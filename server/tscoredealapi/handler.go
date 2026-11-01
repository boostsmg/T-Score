package tscoredealapi

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/tinode/chat/tscore"
)

type Store interface {
	Create(context.Context, tscore.NewDeal) (tscore.Deal, error)
	Get(context.Context, string) (tscore.Deal, error)
	ListForUser(context.Context, string, int) ([]tscore.Deal, error)
	Transition(context.Context, string, tscore.DealStatus) (tscore.Deal, error)
}

type TokenVerifier interface {
	VerifyBase64(string) (string, error)
}

type Handler struct {
	store    Store
	adminKey string
	verifier TokenVerifier
}

func New(store Store, adminKey string) http.Handler {
	return newHandler(store, adminKey, nil)
}

func NewAuthenticated(store Store, adminKey string, verifier TokenVerifier) http.Handler {
	return newHandler(store, adminKey, verifier)
}

func newHandler(store Store, adminKey string, verifier TokenVerifier) http.Handler {
	h := &Handler{store:store, adminKey:adminKey, verifier:verifier}
	mux := http.NewServeMux()

	// Internal/admin API.
	mux.HandleFunc("POST /internal/v1/deals", h.createInternal)
	mux.HandleFunc("GET /internal/v1/deals/{id}", h.getInternal)
	mux.HandleFunc("POST /internal/v1/deals/{id}/transition", h.transitionInternal)
	mux.HandleFunc("GET /internal/v1/users/{userID}/deals", h.listInternal)

	// User API is only registered when a Tinode token verifier is configured.
	if verifier != nil {
		mux.HandleFunc("POST /v1/deals", h.createAuthenticated)
		mux.HandleFunc("GET /v1/deals", h.listAuthenticated)
		mux.HandleFunc("GET /v1/deals/{id}", h.getAuthenticated)
		mux.HandleFunc("POST /v1/deals/{id}/transition", h.transitionAuthenticated)
	}
	return mux
}

func (h *Handler) authorizeAdmin(w http.ResponseWriter, r *http.Request) bool {
	got := r.Header.Get("X-TScore-Admin-Key")
	if h.adminKey == "" || len(got) != len(h.adminKey) || subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey)) != 1 {
		writeError(w,http.StatusUnauthorized,"unauthorized")
		return false
	}
	return true
}

func (h *Handler) actorFromRequest(w http.ResponseWriter, r *http.Request) (string, bool) {
	if h.verifier == nil {
		writeError(w,http.StatusServiceUnavailable,"user authentication unavailable")
		return "", false
	}
	authz := strings.TrimSpace(r.Header.Get("Authorization"))
	if !strings.HasPrefix(strings.ToLower(authz), "bearer ") {
		writeError(w,http.StatusUnauthorized,"missing bearer token")
		return "", false
	}
	token := strings.TrimSpace(authz[len("Bearer "):])
	actor,err := h.verifier.VerifyBase64(token)
	if err != nil {
		writeError(w,http.StatusUnauthorized,"invalid or expired session")
		return "", false
	}
	return actor,true
}

func (h *Handler) createInternal(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(w,r) { return }
	var in tscore.NewDeal
	dec := json.NewDecoder(http.MaxBytesReader(w,r.Body,64<<10)); dec.DisallowUnknownFields()
	if err := dec.Decode(&in); err != nil { writeError(w,400,"invalid deal"); return }
	d,err := h.store.Create(r.Context(),in)
	if err != nil { writeError(w,400,err.Error()); return }
	writeJSON(w,http.StatusCreated,d)
}

func (h *Handler) getInternal(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(w,r) { return }
	d,err := h.store.Get(r.Context(),strings.TrimSpace(r.PathValue("id")))
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,500,"deal unavailable"); return }
	writeJSON(w,200,d)
}

func (h *Handler) listInternal(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(w,r) { return }
	deals,err := h.store.ListForUser(r.Context(),r.PathValue("userID"),queryLimit(r))
	if err != nil { writeError(w,400,"deals unavailable"); return }
	writeJSON(w,200,map[string]any{"deals":deals})
}

func (h *Handler) transitionInternal(w http.ResponseWriter, r *http.Request) {
	if !h.authorizeAdmin(w,r) { return }
	to,ok := decodeTransition(w,r); if !ok { return }
	d,err := h.store.Transition(r.Context(),r.PathValue("id"),to)
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,409,err.Error()); return }
	writeJSON(w,200,d)
}

type userCreateRequest struct {
	CounterpartID string `json:"counterpart_id"`
	Role          string `json:"role"`
	Title         string `json:"title"`
	Terms         string `json:"terms"`
	AmountMinor   int64  `json:"amount_minor"`
	Currency      string `json:"currency"`
}

func (h *Handler) createAuthenticated(w http.ResponseWriter, r *http.Request) {
	actor,ok := h.actorFromRequest(w,r); if !ok { return }
	var req userCreateRequest
	dec := json.NewDecoder(http.MaxBytesReader(w,r.Body,64<<10)); dec.DisallowUnknownFields()
	if err := dec.Decode(&req); err != nil { writeError(w,400,"invalid deal"); return }
	role := strings.ToUpper(strings.TrimSpace(req.Role))
	in := tscore.NewDeal{CreatedBy:actor,Title:req.Title,Terms:req.Terms,AmountMinor:req.AmountMinor,Currency:req.Currency}
	switch role {
	case "BUYER":
		in.BuyerID=actor; in.SellerID=req.CounterpartID
	case "SELLER":
		in.BuyerID=req.CounterpartID; in.SellerID=actor
	default:
		writeError(w,400,"role must be BUYER or SELLER"); return
	}
	d,err := h.store.Create(r.Context(),in)
	if err != nil { writeError(w,400,err.Error()); return }
	writeJSON(w,http.StatusCreated,d)
}

func (h *Handler) listAuthenticated(w http.ResponseWriter, r *http.Request) {
	actor,ok := h.actorFromRequest(w,r); if !ok { return }
	deals,err := h.store.ListForUser(r.Context(),actor,queryLimit(r))
	if err != nil { writeError(w,500,"deals unavailable"); return }
	writeJSON(w,200,map[string]any{"deals":deals})
}

func (h *Handler) getAuthenticated(w http.ResponseWriter, r *http.Request) {
	actor,ok := h.actorFromRequest(w,r); if !ok { return }
	d,err := h.store.Get(r.Context(),strings.TrimSpace(r.PathValue("id")))
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,500,"deal unavailable"); return }
	if !tscore.IsDealParticipant(d,actor) { writeError(w,403,"not a deal participant"); return }
	writeJSON(w,200,d)
}

func (h *Handler) transitionAuthenticated(w http.ResponseWriter, r *http.Request) {
	actor,ok := h.actorFromRequest(w,r); if !ok { return }
	to,ok := decodeTransition(w,r); if !ok { return }
	d,err := h.store.Get(r.Context(),strings.TrimSpace(r.PathValue("id")))
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,500,"deal unavailable"); return }
	if !tscore.CanUserTransitionDeal(d,actor,to) { writeError(w,403,"deal action not permitted"); return }
	d,err = h.store.Transition(r.Context(),d.ID,to)
	if err != nil { writeError(w,409,err.Error()); return }
	writeJSON(w,200,d)
}

func queryLimit(r *http.Request) int {
	limit:=100
	if raw:=r.URL.Query().Get("limit"); raw!="" { if n,err:=strconv.Atoi(raw); err==nil { limit=n } }
	return limit
}

func decodeTransition(w http.ResponseWriter, r *http.Request) (tscore.DealStatus,bool) {
	var body struct { Status tscore.DealStatus `json:"status"` }
	dec:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10)); dec.DisallowUnknownFields()
	if err:=dec.Decode(&body); err!=nil || body.Status=="" { writeError(w,400,"invalid status"); return "",false }
	return body.Status,true
}

func writeError(w http.ResponseWriter, status int, msg string) { writeJSON(w,status,map[string]string{"error":msg}) }
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type","application/json; charset=utf-8")
	w.Header().Set("Cache-Control","no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
