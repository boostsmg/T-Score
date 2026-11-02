package tscore

import (
	"errors"
	"strings"
	"time"
)

type DealReview struct {
	ID         string    `json:"id"`
	DealID     string    `json:"deal_id"`
	ReviewerID string    `json:"reviewer_id"`
	RevieweeID string    `json:"reviewee_id"`
	Rating     int       `json:"rating"`
	Comment    string    `json:"comment"`
	CreatedAt  time.Time `json:"created_at"`
}

type DealDispute struct {
	ID        string     `json:"id"`
	DealID    string     `json:"deal_id"`
	OpenedBy  string     `json:"opened_by"`
	Reason    string     `json:"reason"`
	Details   string     `json:"details"`
	Status    string     `json:"status"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt time.Time  `json:"updated_at"`
	ResolvedAt *time.Time `json:"resolved_at,omitempty"`
}

var ErrReviewExists = errors.New("review already submitted")
var ErrReviewNotAllowed = errors.New("review not allowed")
var ErrDisputeExists = errors.New("dispute already open")
var ErrDisputeNotAllowed = errors.New("dispute not allowed")

func ValidateReviewInput(rating int, comment string) (string, error) {
	comment = strings.TrimSpace(comment)
	if rating < 1 || rating > 5 {
		return comment, errors.New("rating must be between 1 and 5")
	}
	if len(comment) > 1000 {
		return comment, errors.New("review is too long")
	}
	return comment, nil
}

func ValidateDisputeInput(reason, details string) (string, string, error) {
	reason = strings.ToUpper(strings.TrimSpace(reason))
	details = strings.TrimSpace(details)
	switch reason {
	case "PAYMENT", "DELIVERY", "NOT_AS_DESCRIBED", "SCAM", "OTHER":
	default:
		return reason, details, errors.New("invalid dispute reason")
	}
	if len(details) < 10 || len(details) > 4000 {
		return reason, details, errors.New("dispute details must be 10-4000 characters")
	}
	return reason, details, nil
}

func RevieweeFor(d Deal, reviewer string) string {
	reviewer = normalizeUserID(reviewer)
	if reviewer == d.BuyerID { return d.SellerID }
	if reviewer == d.SellerID { return d.BuyerID }
	return ""
}
