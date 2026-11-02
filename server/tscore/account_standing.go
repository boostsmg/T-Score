package tscore

import (
    "errors"
    "strings"
    "time"
)

type AccountStanding string

const (
    StandingGood       AccountStanding = "GOOD"
    StandingRestricted AccountStanding = "RESTRICTED"
    StandingSuspended  AccountStanding = "SUSPENDED"
)

var ErrStandingInvalid = errors.New("invalid account standing")

type AccountStandingRecord struct {
    UserID    string          `json:"user_id"`
    Standing  AccountStanding `json:"standing"`
    Reason    string          `json:"reason,omitempty"`
    UpdatedAt time.Time       `json:"updated_at"`
}

func ValidAccountStanding(s AccountStanding) bool {
    return s == StandingGood || s == StandingRestricted || s == StandingSuspended
}

func ValidateStandingInput(userID string, standing AccountStanding, reason string) error {
    if !strings.HasPrefix(strings.TrimSpace(userID), "usr") || !ValidAccountStanding(standing) {
        return ErrStandingInvalid
    }
    if len([]rune(strings.TrimSpace(reason))) > 1000 {
        return ErrStandingInvalid
    }
    return nil
}

func StandingIsGood(s AccountStanding) bool { return s == StandingGood }
