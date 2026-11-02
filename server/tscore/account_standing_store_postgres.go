package tscore

import (
    "context"
    "errors"
    "strings"
    "time"

    "github.com/jackc/pgx/v5"
    "github.com/jackc/pgx/v5/pgxpool"
)

type AccountStandingStore struct{ pool *pgxpool.Pool }

func NewAccountStandingStore(pool *pgxpool.Pool) *AccountStandingStore { return &AccountStandingStore{pool: pool} }

func (s *AccountStandingStore) EnsureSchema(ctx context.Context) error {
    _, err := s.pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS tscore_account_standing (
    user_id TEXT PRIMARY KEY,
    standing TEXT NOT NULL DEFAULT 'GOOD',
    reason TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS tscore_account_standing_events (
    id BIGSERIAL PRIMARY KEY,
    user_id TEXT NOT NULL,
    standing TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tscore_account_standing_events_user ON tscore_account_standing_events(user_id, created_at DESC);
`)
    return err
}

func (s *AccountStandingStore) Get(ctx context.Context, userID string) (AccountStandingRecord, error) {
    userID = strings.TrimSpace(userID)
    var r AccountStandingRecord
    err := s.pool.QueryRow(ctx, `SELECT user_id,standing,reason,updated_at FROM tscore_account_standing WHERE user_id=$1`, userID).
        Scan(&r.UserID,&r.Standing,&r.Reason,&r.UpdatedAt)
    if errors.Is(err, pgx.ErrNoRows) {
        return AccountStandingRecord{UserID:userID,Standing:StandingGood,UpdatedAt:time.Time{}}, nil
    }
    return r, err
}

func (s *AccountStandingStore) Set(ctx context.Context, userID string, standing AccountStanding, reason string) (AccountStandingRecord, error) {
    if err := ValidateStandingInput(userID, standing, reason); err != nil { return AccountStandingRecord{}, err }
    userID, reason = strings.TrimSpace(userID), strings.TrimSpace(reason)
    tx, err := s.pool.Begin(ctx); if err != nil { return AccountStandingRecord{}, err }
    defer tx.Rollback(ctx)
    var r AccountStandingRecord
    err = tx.QueryRow(ctx, `INSERT INTO tscore_account_standing(user_id,standing,reason) VALUES($1,$2,$3)
ON CONFLICT(user_id) DO UPDATE SET standing=EXCLUDED.standing,reason=EXCLUDED.reason,updated_at=NOW()
RETURNING user_id,standing,reason,updated_at`, userID, standing, reason).Scan(&r.UserID,&r.Standing,&r.Reason,&r.UpdatedAt)
    if err != nil { return AccountStandingRecord{}, err }
    if _, err = tx.Exec(ctx, `INSERT INTO tscore_account_standing_events(user_id,standing,reason) VALUES($1,$2,$3)`, userID, standing, reason); err != nil { return AccountStandingRecord{}, err }
    if err = tx.Commit(ctx); err != nil { return AccountStandingRecord{}, err }
    return r, nil
}
