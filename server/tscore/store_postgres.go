package tscore

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var ErrTrustProfileNotFound = errors.New("trust profile not found")

type TrustProfile struct {
	Username  string            `json:"username"`
	State     VerificationState `json:"-"`
	UpdatedAt time.Time         `json:"updated_at"`
}

type PostgresStore struct {
	pool *pgxpool.Pool
}

func NewPostgresStore(pool *pgxpool.Pool) *PostgresStore {
	return &PostgresStore{pool: pool}
}

func normalizeUsername(username string) string {
	return strings.ToLower(strings.TrimSpace(username))
}

func (s *PostgresStore) EnsureSchema(ctx context.Context) error {
	_, err := s.pool.Exec(ctx, `
CREATE TABLE IF NOT EXISTS tscore_trust_profiles (
    username TEXT PRIMARY KEY,
    profile_photo_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    phone_verified BOOLEAN NOT NULL DEFAULT FALSE,
    identity_verified BOOLEAN NOT NULL DEFAULT FALSE,
    address_verified BOOLEAN NOT NULL DEFAULT FALSE,
    liveness_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_age_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    successful_deals_maxed BOOLEAN NOT NULL DEFAULT FALSE,
    reviews_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    account_standing_good BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
)`)
	return err
}

func (s *PostgresStore) Get(ctx context.Context, username string) (TrustProfile, error) {
	username = normalizeUsername(username)
	var p TrustProfile
	p.Username = username
	err := s.pool.QueryRow(ctx, `
SELECT profile_photo_verified, email_verified, phone_verified,
       identity_verified, address_verified, liveness_verified,
       account_age_eligible, successful_deals_maxed, reviews_eligible,
       account_standing_good, updated_at
FROM tscore_trust_profiles
WHERE username=$1`, username).Scan(
		&p.State.ProfilePhotoVerified,
		&p.State.EmailVerified,
		&p.State.PhoneVerified,
		&p.State.IdentityVerified,
		&p.State.AddressVerified,
		&p.State.LivenessVerified,
		&p.State.AccountAgeEligible,
		&p.State.SuccessfulDealsMaxed,
		&p.State.ReviewsEligible,
		&p.State.AccountStandingGood,
		&p.UpdatedAt,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return TrustProfile{}, ErrTrustProfileNotFound
	}
	return p, err
}

func (s *PostgresStore) Upsert(ctx context.Context, username string, state VerificationState) (TrustProfile, error) {
	username = normalizeUsername(username)
	if username == "" {
		return TrustProfile{}, errors.New("username is required")
	}
	var updatedAt time.Time
	err := s.pool.QueryRow(ctx, `
INSERT INTO tscore_trust_profiles (
    username, profile_photo_verified, email_verified, phone_verified,
    identity_verified, address_verified, liveness_verified,
    account_age_eligible, successful_deals_maxed, reviews_eligible,
    account_standing_good
) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
ON CONFLICT (username) DO UPDATE SET
    profile_photo_verified=EXCLUDED.profile_photo_verified,
    email_verified=EXCLUDED.email_verified,
    phone_verified=EXCLUDED.phone_verified,
    identity_verified=EXCLUDED.identity_verified,
    address_verified=EXCLUDED.address_verified,
    liveness_verified=EXCLUDED.liveness_verified,
    account_age_eligible=EXCLUDED.account_age_eligible,
    successful_deals_maxed=EXCLUDED.successful_deals_maxed,
    reviews_eligible=EXCLUDED.reviews_eligible,
    account_standing_good=EXCLUDED.account_standing_good,
    updated_at=NOW()
RETURNING updated_at`,
		username,
		state.ProfilePhotoVerified,
		state.EmailVerified,
		state.PhoneVerified,
		state.IdentityVerified,
		state.AddressVerified,
		state.LivenessVerified,
		state.AccountAgeEligible,
		state.SuccessfulDealsMaxed,
		state.ReviewsEligible,
		state.AccountStandingGood,
	).Scan(&updatedAt)
	if err != nil {
		return TrustProfile{}, err
	}
	return TrustProfile{Username: username, State: state, UpdatedAt: updatedAt}, nil
}
