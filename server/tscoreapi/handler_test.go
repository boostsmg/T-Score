package tscoreapi

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/tinode/chat/tscore"
)

type memoryStore struct {
	profiles map[string]tscore.TrustProfile
}

func (m *memoryStore) Get(_ context.Context, username string) (tscore.TrustProfile, error) {
	p, ok := m.profiles[username]
	if !ok {
		return tscore.TrustProfile{}, tscore.ErrTrustProfileNotFound
	}
	return p, nil
}

func (m *memoryStore) Upsert(_ context.Context, username string, state tscore.VerificationState) (tscore.TrustProfile, error) {
	p := tscore.TrustProfile{Username: username, State: state, UpdatedAt: time.Unix(100, 0).UTC()}
	m.profiles[username] = p
	return p, nil
}

func TestPublicMissingProfileReturnsZeroScore(t *testing.T) {
	store := &memoryStore{profiles: map[string]tscore.TrustProfile{}}
	r := httptest.NewRequest(http.MethodGet, "/v1/trust/alice", nil)
	w := httptest.NewRecorder()
	New(store, "secret").ServeHTTP(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("status=%d want 200", w.Code)
	}
	var body PublicProfile
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatal(err)
	}
	if body.Trust.Score != 0 || body.Trust.Badge != "New" {
		t.Fatalf("trust=%+v want zero/New", body.Trust)
	}
}

func TestInternalWriteRequiresKey(t *testing.T) {
	store := &memoryStore{profiles: map[string]tscore.TrustProfile{}}
	r := httptest.NewRequest(http.MethodPut, "/internal/v1/trust/alice", bytes.NewBufferString(`{"emailVerified":true}`))
	w := httptest.NewRecorder()
	New(store, "secret").ServeHTTP(w, r)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("status=%d want 401", w.Code)
	}
}

func TestInternalWriteCalculatesServerScore(t *testing.T) {
	store := &memoryStore{profiles: map[string]tscore.TrustProfile{}}
	payload := `{
		"ProfilePhotoVerified": true,
		"EmailVerified": true,
		"PhoneVerified": true,
		"IdentityVerified": false,
		"AddressVerified": false,
		"LivenessVerified": false,
		"AccountAgeEligible": false,
		"SuccessfulDealsMaxed": false,
		"ReviewsEligible": false,
		"AccountStandingGood": true
	}`
	r := httptest.NewRequest(http.MethodPut, "/internal/v1/trust/alice", bytes.NewBufferString(payload))
	r.Header.Set("X-TScore-Admin-Key", "secret")
	w := httptest.NewRecorder()
	New(store, "secret").ServeHTTP(w, r)
	if w.Code != http.StatusOK {
		t.Fatalf("status=%d body=%s", w.Code, w.Body.String())
	}
	var body PublicProfile
	if err := json.NewDecoder(w.Body).Decode(&body); err != nil {
		t.Fatal(err)
	}
	// 5 photo + 5 email + 10 phone + 5 good standing.
	if body.Trust.Score != 25 {
		t.Fatalf("score=%d want 25", body.Trust.Score)
	}
}
