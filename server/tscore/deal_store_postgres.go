package tscore

import (
	"context"
	"errors"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type DealStore struct {
	pool *pgxpool.Pool
}

func NewDealStore(pool *pgxpool.Pool) *DealStore { return &DealStore{pool: pool} }

func (s *DealStore) EnsureSchema(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS tscore_deals (
    id TEXT PRIMARY KEY,
    buyer_id TEXT NOT NULL,
    seller_id TEXT NOT NULL,
    created_by TEXT NOT NULL,
    title TEXT NOT NULL,
    terms TEXT NOT NULL DEFAULT '',
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at TIMESTAMPTZ NULL,
    delivered_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_tscore_deals_buyer_created ON tscore_deals (buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tscore_deals_seller_created ON tscore_deals (seller_id, created_at DESC);

CREATE TABLE IF NOT EXISTS tscore_deal_events (
    id BIGSERIAL PRIMARY KEY,
    deal_id TEXT NOT NULL REFERENCES tscore_deals(id) ON DELETE CASCADE,
    actor_id TEXT NULL,
    event_type TEXT NOT NULL,
    from_status TEXT NULL,
    to_status TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tscore_deal_events_deal_created ON tscore_deal_events (deal_id, created_at ASC, id ASC);
`)
	return err
}

func (s *DealStore) Create(ctx context.Context, input NewDeal) (Deal, error) {
	input, err := ValidateNewDeal(input)
	if err != nil { return Deal{}, err }
	id, err := NewDealID()
	if err != nil { return Deal{}, err }

	tx, err := s.pool.Begin(ctx)
	if err != nil { return Deal{}, err }
	defer tx.Rollback(ctx)

	var d Deal
	err = tx.QueryRow(ctx, `
INSERT INTO tscore_deals (id,buyer_id,seller_id,created_by,title,terms,amount_minor,currency,status)
VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
RETURNING id,buyer_id,seller_id,created_by,title,terms,amount_minor,currency,status,created_at,updated_at,accepted_at,delivered_at,completed_at`,
		id,input.BuyerID,input.SellerID,input.CreatedBy,input.Title,input.Terms,input.AmountMinor,input.Currency,DealProposed,
	).Scan(&d.ID,&d.BuyerID,&d.SellerID,&d.CreatedBy,&d.Title,&d.Terms,&d.AmountMinor,&d.Currency,&d.Status,&d.CreatedAt,&d.UpdatedAt,&d.AcceptedAt,&d.DeliveredAt,&d.CompletedAt)
	if err != nil { return Deal{}, err }

	if _, err = tx.Exec(ctx, `INSERT INTO tscore_deal_events (deal_id,actor_id,event_type,to_status) VALUES ($1,$2,'CREATED',$3)`, id, input.CreatedBy, DealProposed); err != nil {
		return Deal{}, err
	}
	if err := tx.Commit(ctx); err != nil { return Deal{}, err }
	return d, nil
}

func (s *DealStore) Get(ctx context.Context, id string) (Deal, error) {
	var d Deal
	err := s.pool.QueryRow(ctx, `
SELECT id,buyer_id,seller_id,created_by,title,terms,amount_minor,currency,status,created_at,updated_at,accepted_at,delivered_at,completed_at
FROM tscore_deals WHERE id=$1`, id).Scan(&d.ID,&d.BuyerID,&d.SellerID,&d.CreatedBy,&d.Title,&d.Terms,&d.AmountMinor,&d.Currency,&d.Status,&d.CreatedAt,&d.UpdatedAt,&d.AcceptedAt,&d.DeliveredAt,&d.CompletedAt)
	if errors.Is(err, pgx.ErrNoRows) { return Deal{}, ErrDealNotFound }
	return d, err
}

func (s *DealStore) ListForUser(ctx context.Context, userID string, limit int) ([]Deal, error) {
	userID = normalizeUserID(userID)
	if userID == "" { return nil, errors.New("valid user ID required") }
	if limit <= 0 || limit > 200 { limit = 100 }
	rows, err := s.pool.Query(ctx, `
SELECT id,buyer_id,seller_id,created_by,title,terms,amount_minor,currency,status,created_at,updated_at,accepted_at,delivered_at,completed_at
FROM tscore_deals WHERE buyer_id=$1 OR seller_id=$1 ORDER BY created_at DESC LIMIT $2`, userID, limit)
	if err != nil { return nil, err }
	defer rows.Close()
	out := make([]Deal,0)
	for rows.Next() {
		var d Deal
		if err := rows.Scan(&d.ID,&d.BuyerID,&d.SellerID,&d.CreatedBy,&d.Title,&d.Terms,&d.AmountMinor,&d.Currency,&d.Status,&d.CreatedAt,&d.UpdatedAt,&d.AcceptedAt,&d.DeliveredAt,&d.CompletedAt); err != nil { return nil, err }
		out = append(out,d)
	}
	return out, rows.Err()
}

func (s *DealStore) Transition(ctx context.Context, id string, to DealStatus, actor string) (Deal, error) {
	tx, err := s.pool.Begin(ctx)
	if err != nil { return Deal{}, err }
	defer tx.Rollback(ctx)
	var from DealStatus
	if err := tx.QueryRow(ctx, `SELECT status FROM tscore_deals WHERE id=$1 FOR UPDATE`, id).Scan(&from); err != nil {
		if errors.Is(err, pgx.ErrNoRows) { return Deal{}, ErrDealNotFound }
		return Deal{}, err
	}
	if err := ValidateDealTransition(from,to); err != nil { return Deal{}, err }
	now := time.Now().UTC()
	acceptedAt, deliveredAt, completedAt := (*time.Time)(nil), (*time.Time)(nil), (*time.Time)(nil)
	if to == DealAccepted { acceptedAt = &now }
	if to == DealDelivered { deliveredAt = &now }
	if to == DealCompleted { completedAt = &now }
	_, err = tx.Exec(ctx, `UPDATE tscore_deals SET status=$2, updated_at=NOW(),
accepted_at=COALESCE($3,accepted_at), delivered_at=COALESCE($4,delivered_at), completed_at=COALESCE($5,completed_at)
WHERE id=$1`, id,to,acceptedAt,deliveredAt,completedAt)
	if err != nil { return Deal{}, err }
	if _, err = tx.Exec(ctx, `INSERT INTO tscore_deal_events (deal_id,actor_id,event_type,from_status,to_status) VALUES ($1,NULLIF($2,''),'STATUS_CHANGED',$3,$4)`, id, actor, from, to); err != nil {
		return Deal{}, err
	}
	if err := tx.Commit(ctx); err != nil { return Deal{}, err }
	return s.Get(ctx,id)
}