package tscore

import (
    "context"
    "errors"

    "github.com/jackc/pgx/v5"
    "github.com/jackc/pgx/v5/pgconn"
    "github.com/jackc/pgx/v5/pgxpool"
)

type VerificationStore struct { pool *pgxpool.Pool }
func NewVerificationStore(pool *pgxpool.Pool) *VerificationStore { return &VerificationStore{pool:pool} }

func (s *VerificationStore) EnsureSchema(ctx context.Context) error {
    _,err:=s.pool.Exec(ctx,`
CREATE TABLE IF NOT EXISTS tscore_verification_requests (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    type TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    evidence_ref TEXT NOT NULL DEFAULT '',
    note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tscore_verification_pending
ON tscore_verification_requests(user_id,type) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS idx_tscore_verification_user_created
ON tscore_verification_requests(user_id,created_at DESC);
`)
    return err
}

func (s *VerificationStore) Create(ctx context.Context,userID string,t VerificationType,evidenceRef string)(VerificationRequest,error){
    userID=normalizeUserID(userID)
    t,err:=NormalizeVerificationType(t)
    if err!=nil||userID=="" { return VerificationRequest{},ErrVerificationInvalid }
    var out VerificationRequest
    err=s.pool.QueryRow(ctx,`
INSERT INTO tscore_verification_requests(user_id,type,evidence_ref)
VALUES($1,$2,$3)
RETURNING id::text,user_id,type,status,evidence_ref,note,created_at,updated_at,reviewed_at`,userID,t,evidenceRef).
        Scan(&out.ID,&out.UserID,&out.Type,&out.Status,&out.EvidenceRef,&out.Note,&out.CreatedAt,&out.UpdatedAt,&out.ReviewedAt)
    if err!=nil {
        var pgErr *pgconn.PgError
        if errors.As(err,&pgErr)&&pgErr.Code=="23505" { return VerificationRequest{},ErrVerificationExists }
        return VerificationRequest{},err
    }
    return out,nil
}

func (s *VerificationStore) ListForUser(ctx context.Context,userID string)([]VerificationRequest,error){
    userID=normalizeUserID(userID)
    rows,err:=s.pool.Query(ctx,`SELECT id::text,user_id,type,status,evidence_ref,note,created_at,updated_at,reviewed_at FROM tscore_verification_requests WHERE user_id=$1 ORDER BY created_at DESC`,userID)
    if err!=nil{return nil,err};defer rows.Close()
    out:=[]VerificationRequest{}
    for rows.Next(){var r VerificationRequest;if err:=rows.Scan(&r.ID,&r.UserID,&r.Type,&r.Status,&r.EvidenceRef,&r.Note,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt);err!=nil{return nil,err};out=append(out,r)}
    return out,rows.Err()
}

func (s *VerificationStore) Decide(ctx context.Context,id string,status VerificationStatus,note string)(VerificationRequest,error){
    if status!=VerificationApproved&&status!=VerificationRejected{return VerificationRequest{},ErrVerificationInvalid}
    var out VerificationRequest
    err:=s.pool.QueryRow(ctx,`
UPDATE tscore_verification_requests SET status=$2,note=$3,updated_at=NOW(),reviewed_at=NOW()
WHERE id::text=$1 AND status='PENDING'
RETURNING id::text,user_id,type,status,evidence_ref,note,created_at,updated_at,reviewed_at`,id,status,note).
        Scan(&out.ID,&out.UserID,&out.Type,&out.Status,&out.EvidenceRef,&out.Note,&out.CreatedAt,&out.UpdatedAt,&out.ReviewedAt)
    if errors.Is(err,pgx.ErrNoRows){return VerificationRequest{},ErrVerificationNotFound}
    return out,err
}

func (s *VerificationStore) Summary(ctx context.Context,userID string)(VerificationSummary,error){
    userID=normalizeUserID(userID);sum:=VerificationSummary{UserID:userID}
    rows,err:=s.pool.Query(ctx,`SELECT DISTINCT type FROM tscore_verification_requests WHERE user_id=$1 AND status='APPROVED'`,userID)
    if err!=nil{return sum,err};defer rows.Close()
    for rows.Next(){var t VerificationType;if err:=rows.Scan(&t);err!=nil{return sum,err};switch t{case VerificationEmail:sum.Email=true;case VerificationPhone:sum.Phone=true;case VerificationIdentity:sum.Identity=true;case VerificationAddress:sum.Address=true;case VerificationLiveness:sum.Liveness=true;case VerificationPhoto:sum.ProfilePhoto=true}}
    return sum,rows.Err()
}
