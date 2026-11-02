package tscore

import (
    "context"
    "errors"

    "github.com/jackc/pgx/v5"
)

func (s *VerificationStore) GetRequest(ctx context.Context,id string)(VerificationRequest,error){
    var out VerificationRequest
    err:=s.pool.QueryRow(ctx,`SELECT id::text,user_id,type,status,evidence_ref,note,created_at,updated_at,reviewed_at FROM tscore_verification_requests WHERE id::text=$1`,id).
        Scan(&out.ID,&out.UserID,&out.Type,&out.Status,&out.EvidenceRef,&out.Note,&out.CreatedAt,&out.UpdatedAt,&out.ReviewedAt)
    if errors.Is(err,pgx.ErrNoRows){return VerificationRequest{},ErrVerificationNotFound}
    return out,err
}

func (s *VerificationStore) ListPending(ctx context.Context)([]VerificationRequest,error){
    rows,err:=s.pool.Query(ctx,`SELECT id::text,user_id,type,status,evidence_ref,note,created_at,updated_at,reviewed_at FROM tscore_verification_requests WHERE status='PENDING' ORDER BY created_at ASC,id ASC`)
    if err!=nil{return nil,err};defer rows.Close()
    out:=[]VerificationRequest{}
    for rows.Next(){var r VerificationRequest;if err:=rows.Scan(&r.ID,&r.UserID,&r.Type,&r.Status,&r.EvidenceRef,&r.Note,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt);err!=nil{return nil,err};out=append(out,r)}
    return out,rows.Err()
}
