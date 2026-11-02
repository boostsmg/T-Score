package tscore

import (
    "errors"
    "strings"
    "time"
)

type ReportStatus string
type ReportReason string

const (
    ReportOpen      ReportStatus = "OPEN"
    ReportReviewing ReportStatus = "REVIEWING"
    ReportResolved  ReportStatus = "RESOLVED"
    ReportDismissed ReportStatus = "DISMISSED"

    ReportScam          ReportReason = "SCAM"
    ReportHarassment    ReportReason = "HARASSMENT"
    ReportImpersonation ReportReason = "IMPERSONATION"
    ReportSpam          ReportReason = "SPAM"
    ReportOther         ReportReason = "OTHER"
)

var (
    ErrReportInvalid     = errors.New("invalid moderation report")
    ErrReportNotFound    = errors.New("moderation report not found")
    ErrReportRateLimited = errors.New("moderation report rate limited")
)

type ModerationReport struct {
    ID          string       `json:"id"`
    ReporterID  string       `json:"reporter_id"`
    TargetID    string       `json:"target_id"`
    TopicName   string       `json:"topic_name,omitempty"`
    Reason      ReportReason `json:"reason"`
    Details     string       `json:"details,omitempty"`
    Status      ReportStatus `json:"status"`
    AdminNote   string       `json:"admin_note,omitempty"`
    CreatedAt   time.Time    `json:"created_at"`
    UpdatedAt   time.Time    `json:"updated_at"`
    ReviewedAt  *time.Time   `json:"reviewed_at,omitempty"`
}

func ValidReportReason(r ReportReason) bool {
    switch r {
    case ReportScam, ReportHarassment, ReportImpersonation, ReportSpam, ReportOther:
        return true
    default:
        return false
    }
}

func ValidReportDecision(s ReportStatus) bool {
    return s == ReportReviewing || s == ReportResolved || s == ReportDismissed
}

func ValidateReportInput(reporter, target string, reason ReportReason, details string) error {
    reporter = strings.TrimSpace(reporter)
    target = strings.TrimSpace(target)
    if reporter == "" || target == "" || reporter == target || !ValidReportReason(reason) {
        return ErrReportInvalid
    }
    if len([]rune(strings.TrimSpace(details))) > 2000 {
        return ErrReportInvalid
    }
    return nil
}
