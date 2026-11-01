package tscore

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"
)

type DealStatus string

const (
	DealProposed   DealStatus = "PROPOSED"
	DealAccepted   DealStatus = "ACCEPTED"
	DealInProgress DealStatus = "IN_PROGRESS"
	DealDelivered  DealStatus = "DELIVERED"
	DealCompleted  DealStatus = "COMPLETED"
	DealCancelled  DealStatus = "CANCELLED"
	DealDisputed   DealStatus = "DISPUTED"
)

type Deal struct {
	ID             string     `json:"id"`
	BuyerUsername  string     `json:"buyer_username"`
	SellerUsername string     `json:"seller_username"`
	Title          string     `json:"title"`
	Terms          string     `json:"terms"`
	AmountMinor    int64      `json:"amount_minor"`
	Currency       string     `json:"currency"`
	Status         DealStatus `json:"status"`
	CreatedAt      time.Time  `json:"created_at"`
	UpdatedAt      time.Time  `json:"updated_at"`
	AcceptedAt     *time.Time `json:"accepted_at,omitempty"`
	DeliveredAt    *time.Time `json:"delivered_at,omitempty"`
	CompletedAt    *time.Time `json:"completed_at,omitempty"`
}

type NewDeal struct {
	BuyerUsername  string `json:"buyer_username"`
	SellerUsername string `json:"seller_username"`
	Title          string `json:"title"`
	Terms          string `json:"terms"`
	AmountMinor    int64  `json:"amount_minor"`
	Currency       string `json:"currency"`
}

var ErrDealNotFound = errors.New("deal not found")

func ValidateNewDeal(in NewDeal) (NewDeal, error) {
	in.BuyerUsername = normalizeUsername(in.BuyerUsername)
	in.SellerUsername = normalizeUsername(in.SellerUsername)
	in.Title = strings.TrimSpace(in.Title)
	in.Terms = strings.TrimSpace(in.Terms)
	in.Currency = strings.ToUpper(strings.TrimSpace(in.Currency))

	if in.BuyerUsername == "" || in.SellerUsername == "" {
		return in, errors.New("buyer and seller are required")
	}
	if in.BuyerUsername == in.SellerUsername {
		return in, errors.New("buyer and seller must be different users")
	}
	if len(in.Title) < 3 || len(in.Title) > 120 {
		return in, errors.New("title must be 3-120 characters")
	}
	if len(in.Terms) > 4000 {
		return in, errors.New("terms are too long")
	}
	if in.AmountMinor <= 0 {
		return in, errors.New("amount must be positive")
	}
	if len(in.Currency) != 3 {
		return in, errors.New("currency must be a 3-letter code")
	}
	return in, nil
}

func NewDealID() (string, error) {
	var b [12]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return "deal_" + hex.EncodeToString(b[:]), nil
}

func CanTransitionDeal(from, to DealStatus) bool {
	if from == to {
		return true
	}
	switch from {
	case DealProposed:
		return to == DealAccepted || to == DealCancelled || to == DealDisputed
	case DealAccepted:
		return to == DealInProgress || to == DealCancelled || to == DealDisputed
	case DealInProgress:
		return to == DealDelivered || to == DealCancelled || to == DealDisputed
	case DealDelivered:
		return to == DealCompleted || to == DealDisputed
	case DealDisputed:
		return to == DealCompleted || to == DealCancelled
	case DealCompleted, DealCancelled:
		return false
	default:
		return false
	}
}

func ValidateDealTransition(from, to DealStatus) error {
	if !CanTransitionDeal(from, to) {
		return fmt.Errorf("invalid deal transition %s -> %s", from, to)
	}
	return nil
}
