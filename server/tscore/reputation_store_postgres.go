package tscore

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

func (s *DealStore) EnsureReputationSchema(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS tscore_deal_reviews (
    id BIGSERIAL PRIMARY KEY,
    deal_id TEXT NOT NULL REFERENCES tscore_deals(id) ON DELETE CASCADE,
    reviewer_id TEXT NOT NULL,
    reviewee_id TEXT NOT NULL,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (deal_id, reviewer_id)
);
CREATE INDEX IF NOT EXISTS idx_tscore_deal_reviews_reviewee_created ON tscore_deal_reviews (reviewee_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tscore_deal_disputes (
    id BIGSERIAL PRIMARY KEY,
    deal_id TEXT NOT NULL REFERENCES tscore_deals(id) ON DELETE CASCADE,
    opened_by TEXT NOT NULL,
    reason TEXT NOT NULL,
    details TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tscore_deal_disputes_open ON tscore_deal_disputes (deal_id) WHERE status='OPEN';
CREATE INDEX IF NOT EXISTS idx_tscore_deal_disputes_deal_created ON tscore_deal_disputes (deal_id, created_at DESC);
`)
	return err
}

func (s *DealStore) SubmitReview(ctx context.Context, dealID, reviewerID string, rating int, comment string) (DealReview, error) {
	comment, err := ValidateReviewInput(rating, comment)
	if err != nil { return DealReview{}, err }
	d, err := s.Get(ctx, dealID)
	if err != nil { return DealReview{}, err }
	if d.Status != DealCompleted || !IsDealParticipant(d, reviewerID) {
		return DealReview{}, ErrReviewNotAllowed
	}
	reviewee := RevieweeFor(d, reviewerID)
	if reviewee == "" { return DealReview{}, ErrReviewNotAllowed }
	var out DealReview
	err = s.pool.QueryRow(ctx, `
INSERT INTO tscore_deal_reviews (deal_id,reviewer_id,reviewee_id,rating,comment)
VALUES ($1,$2,$3,$4,$5)
RETURNING id::text,deal_id,reviewer_id,reviewee_id,rating,comment,created_at`,
		dealID,reviewerID,reviewee,rating,comment,
	).Scan(&out.ID,&out.DealID,&out.ReviewerID,&out.RevieweeID,&out.Rating,&out.Comment,&out.CreatedAt)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err, &pgErr) && pgErr.Code == "23505" { return DealReview{}, ErrReviewExists }
		return DealReview{}, err
	}
	_, _ = s.pool.Exec(ctx, `INSERT INTO tscore_deal_events (deal_id,actor_id,event_type) VALUES ($1,$2,'REVIEW_SUBMITTED')`, dealID, reviewerID)
	return out,nil
}

func (s *DealStore) ListDealReviews(ctx context.Context, dealID string) ([]DealReview, error) {
	rows, err := s.pool.Query(ctx, `SELECT id::text,deal_id,reviewer_id,reviewee_id,rating,comment,created_at FROM tscore_deal_reviews WHERE deal_id=$1 ORDER BY created_at ASC,id ASC`, dealID)
	if err != nil { return nil,err }
	defer rows.Close()
	out := []DealReview{}
	for rows.Next() {
		var r DealReview
		if err := rows.Scan(&r.ID,&r.DealID,&r.ReviewerID,&r.RevieweeID,&r.Rating,&r.Comment,&r.CreatedAt); err != nil { return nil,err }
		out = append(out,r)
	}
	return out,rows.Err()
}

func (s *DealStore) OpenDispute(ctx context.Context, dealID, actor, reason, details string) (DealDispute, Deal, error) {
	reason,details,err := ValidateDisputeInput(reason,details)
	if err != nil { return DealDispute{},Deal{},err }
	d,err := s.Get(ctx,dealID)
	if err != nil { return DealDispute{},Deal{},err }
	if !IsDealParticipant(d,actor) || !CanUserTransitionDeal(d,actor,DealDisputed) {
		return DealDispute{},Deal{},ErrDisputeNotAllowed
	}
	tx,err := s.pool.Begin(ctx)
	if err != nil { return DealDispute{},Deal{},err }
	defer tx.Rollback(ctx)

	var dispute DealDispute
	err = tx.QueryRow(ctx, `
INSERT INTO tscore_deal_disputes (deal_id,opened_by,reason,details)
VALUES ($1,$2,$3,$4)
RETURNING id::text,deal_id,opened_by,reason,details,status,created_at,updated_at,resolved_at`,
		dealID,actor,reason,details,
	).Scan(&dispute.ID,&dispute.DealID,&dispute.OpenedBy,&dispute.Reason,&dispute.Details,&dispute.Status,&dispute.CreatedAt,&dispute.UpdatedAt,&dispute.ResolvedAt)
	if err != nil {
		var pgErr *pgconn.PgError
		if errors.As(err,&pgErr) && pgErr.Code=="23505" { return DealDispute{},Deal{},ErrDisputeExists }
		return DealDispute{},Deal{},err
	}

	from := d.Status
	_,err = tx.Exec(ctx, `UPDATE tscore_deals SET status=$2,updated_at=NOW() WHERE id=$1`,dealID,DealDisputed)
	if err != nil { return DealDispute{},Deal{},err }
	_,err = tx.Exec(ctx, `INSERT INTO tscore_deal_events (deal_id,actor_id,event_type,from_status,to_status) VALUES ($1,$2,'DISPUTE_OPENED',$3,$4)`,dealID,actor,from,DealDisputed)
	if err != nil { return DealDispute{},Deal{},err }
	if err:=tx.Commit(ctx);err!=nil{return DealDispute{},Deal{},err}
	d.Status=DealDisputed
	return dispute,d,nil
}

func (s *DealStore) GetOpenDispute(ctx context.Context, dealID string) (DealDispute,error) {
	var d DealDispute
	err:=s.pool.QueryRow(ctx, `SELECT id::text,deal_id,opened_by,reason,details,status,created_at,updated_at,resolved_at FROM tscore_deal_disputes WHERE deal_id=$1 AND status='OPEN' ORDER BY created_at DESC LIMIT 1`,dealID).
		Scan(&d.ID,&d.DealID,&d.OpenedBy,&d.Reason,&d.Details,&d.Status,&d.CreatedAt,&d.UpdatedAt,&d.ResolvedAt)
	if errors.Is(err,pgx.ErrNoRows){return DealDispute{},ErrDealNotFound}
	return d,err
}
