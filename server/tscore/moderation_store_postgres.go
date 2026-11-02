package tscore

import (
    "context"
    "crypto/rand"
    "encoding/hex"
    "errors"
    "strings"
    "time"

    "github.com/jackc/pgx/v5"
    "github.com/jackc/pgx/v5/pgxpool"
)

type ModerationStore struct{ pool *pgxpool.Pool }

func NewModerationStore(pool *pgxpool.Pool) *ModerationStore { return &ModerationStore{pool: pool} }

func (s *ModerationStore) EnsureSchema(ctx context.Context) error {
    _, err := s.pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS moderation_reports (
    id TEXT PRIMARY KEY,
    reporter_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    topic_name TEXT NOT NULL DEFAULT '',
    reason TEXT NOT NULL,
    details TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'OPEN',
    admin_note TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ NULL
);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_status_created ON moderation_reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_reporter_created ON moderation_reports(reporter_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_reports_target_created ON moderation_reports(target_id, created_at DESC);
`)
    return err
}

func newReportID() (string, error) {
    b := make([]byte, 12)
    if _, err := rand.Read(b); err != nil { return "", err }
    return "rpt_" + hex.EncodeToString(b), nil
}

func (s *ModerationStore) CreateReport(ctx context.Context, reporter, target, topic string, reason ReportReason, details string) (ModerationReport, error) {
    if err := ValidateReportInput(reporter, target, reason, details); err != nil { return ModerationReport{}, err }
    id, err := newReportID(); if err != nil { return ModerationReport{}, err }
    reporter, target, topic, details = strings.TrimSpace(reporter), strings.TrimSpace(target), strings.TrimSpace(topic), strings.TrimSpace(details)
    var r ModerationReport
    err = s.pool.QueryRow(ctx, `INSERT INTO moderation_reports(id,reporter_id,target_id,topic_name,reason,details,status)
VALUES($1,$2,$3,$4,$5,$6,'OPEN')
RETURNING id,reporter_id,target_id,topic_name,reason,details,status,admin_note,created_at,updated_at,reviewed_at`,
        id, reporter, target, topic, reason, details).Scan(&r.ID,&r.ReporterID,&r.TargetID,&r.TopicName,&r.Reason,&r.Details,&r.Status,&r.AdminNote,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt)
    return r, err
}

func (s *ModerationStore) ListMine(ctx context.Context, reporter string) ([]ModerationReport, error) {
    rows, err := s.pool.Query(ctx, `SELECT id,reporter_id,target_id,topic_name,reason,details,status,admin_note,created_at,updated_at,reviewed_at
FROM moderation_reports WHERE reporter_id=$1 ORDER BY created_at DESC LIMIT 100`, strings.TrimSpace(reporter))
    if err != nil { return nil, err }
    defer rows.Close()
    out := []ModerationReport{}
    for rows.Next() { var r ModerationReport; if err := rows.Scan(&r.ID,&r.ReporterID,&r.TargetID,&r.TopicName,&r.Reason,&r.Details,&r.Status,&r.AdminNote,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt); err != nil{return nil,err}; out=append(out,r) }
    return out, rows.Err()
}

func (s *ModerationStore) ListReports(ctx context.Context, status ReportStatus) ([]ModerationReport, error) {
    q := `SELECT id,reporter_id,target_id,topic_name,reason,details,status,admin_note,created_at,updated_at,reviewed_at FROM moderation_reports`
    args := []any{}
    if status != "" { q += ` WHERE status=$1`; args = append(args, status) }
    q += ` ORDER BY created_at DESC LIMIT 200`
    rows, err := s.pool.Query(ctx, q, args...); if err != nil { return nil, err }; defer rows.Close()
    out := []ModerationReport{}
    for rows.Next(){ var r ModerationReport; if err:=rows.Scan(&r.ID,&r.ReporterID,&r.TargetID,&r.TopicName,&r.Reason,&r.Details,&r.Status,&r.AdminNote,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt);err!=nil{return nil,err};out=append(out,r)}
    return out, rows.Err()
}

func (s *ModerationStore) DecideReport(ctx context.Context, id string, status ReportStatus, note string) (ModerationReport, error) {
    if !ValidReportDecision(status) { return ModerationReport{}, ErrReportInvalid }
    note = strings.TrimSpace(note); if len([]rune(note)) > 2000 { return ModerationReport{}, ErrReportInvalid }
    now := time.Now().UTC(); var r ModerationReport
    err := s.pool.QueryRow(ctx, `UPDATE moderation_reports SET status=$2,admin_note=$3,updated_at=$4,reviewed_at=$4 WHERE id=$1
RETURNING id,reporter_id,target_id,topic_name,reason,details,status,admin_note,created_at,updated_at,reviewed_at`, strings.TrimSpace(id),status,note,now).Scan(&r.ID,&r.ReporterID,&r.TargetID,&r.TopicName,&r.Reason,&r.Details,&r.Status,&r.AdminNote,&r.CreatedAt,&r.UpdatedAt,&r.ReviewedAt)
    if errors.Is(err, pgx.ErrNoRows) { return ModerationReport{}, ErrReportNotFound }
    return r, err
}
