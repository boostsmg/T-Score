package tscoreapi

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/tinode/chat/tscore"
)

type Store interface {
	Get(context.Context, string) (tscore.TrustProfile, error)
	Upsert(context.Context, string, tscore.VerificationState) (tscore.TrustProfile, error)
}

type Handler struct {
	store    Store
	adminKey string
}

type PublicProfile struct {
	Username  string                 `json:"username"`
	Trust     tscore.TrustResult     `json:"trust"`
	Verified map[string]bool         `json:"verified"`
	UpdatedAt any                    `json:"updated_at,omitempty"`
}

func New(store Store, adminKey string) http.Handler {
	h := &Handler{store: store, adminKey: adminKey}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", h.health)
	mux.HandleFunc("GET /v1/trust/{username}", h.getPublic)
	mux.HandleFunc("PUT /internal/v1/trust/{username}", h.putInternal)
	return mux
}

func (h *Handler) health(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (h *Handler) getPublic(w http.ResponseWriter, r *http.Request) {
	username := strings.TrimSpace(r.PathValue("username"))
	if username == "" {
		writeError(w, http.StatusBadRequest, "username is required")
		return
	}
	p, err := h.store.Get(r.Context(), username)
	if errors.Is(err, tscore.ErrTrustProfileNotFound) {
		// A valid user with no trust row yet is represented as a clean zero score.
		state := tscore.VerificationState{}
		writeJSON(w, http.StatusOK, publicProfile(username, state, nil))
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, "trust profile unavailable")
		return
	}
	writeJSON(w, http.StatusOK, publicProfile(p.Username, p.State, p.UpdatedAt))
}

func (h *Handler) putInternal(w http.ResponseWriter, r *http.Request) {
	if h.adminKey == "" || !secureEqual(r.Header.Get("X-TScore-Admin-Key"), h.adminKey) {
		writeError(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	username := strings.TrimSpace(r.PathValue("username"))
	if username == "" {
		writeError(w, http.StatusBadRequest, "username is required")
		return
	}
	var state tscore.VerificationState
	dec := json.NewDecoder(http.MaxBytesReader(w, r.Body, 32<<10))
	dec.DisallowUnknownFields()
	if err := dec.Decode(&state); err != nil {
		writeError(w, http.StatusBadRequest, "invalid verification state")
		return
	}
	p, err := h.store.Upsert(r.Context(), username, state)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not save trust profile")
		return
	}
	writeJSON(w, http.StatusOK, publicProfile(p.Username, p.State, p.UpdatedAt))
}

func publicProfile(username string, state tscore.VerificationState, updatedAt any) PublicProfile {
	return PublicProfile{
		Username: username,
		Trust:    tscore.CalculateTrustScore(state),
		Verified: map[string]bool{
			"profile_photo": state.ProfilePhotoVerified,
			"email":         state.EmailVerified,
			"phone":         state.PhoneVerified,
			"identity":      state.IdentityVerified,
			"address":       state.AddressVerified,
			"liveness":      state.LivenessVerified,
		},
		UpdatedAt: updatedAt,
	}
}

func secureEqual(got, want string) bool {
	if len(got) != len(want) {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(got), []byte(want)) == 1
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
