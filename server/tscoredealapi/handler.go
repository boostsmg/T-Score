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

type Handler struct { store Store; adminKey string }

func New(store Store, adminKey string) http.Handler {
	h := &Handler{store:store, adminKey:adminKey}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /internal/v1/deals", h.create)
	mux.HandleFunc("GET /internal/v1/deals/{id}", h.get)
	mux.HandleFunc("POST /internal/v1/deals/{id}/transition", h.transition)
	mux.HandleFunc("GET /internal/v1/users/{username}/deals", h.list)
	return mux
}

func (h *Handler) authorize(w http.ResponseWriter, r *http.Request) bool {
	got := r.Header.Get("X-TScore-Admin-Key")
	if h.adminKey == "" || len(got) != len(h.adminKey) || subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey)) != 1 {
		writeError(w,http.StatusUnauthorized,"unauthorized")
		return false
	}
	return true
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	if !h.authorize(w,r) { return }
	var in tscore.NewDeal
	dec := json.NewDecoder(http.MaxBytesReader(w,r.Body,64<<10)); dec.DisallowUnknownFields()
	if err := dec.Decode(&in); err != nil { writeError(w,400,"invalid deal"); return }
	d,err := h.store.Create(r.Context(),in)
	if err != nil { writeError(w,400,err.Error()); return }
	writeJSON(w,http.StatusCreated,d)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	if !h.authorize(w,r) { return }
	d,err := h.store.Get(r.Context(),strings.TrimSpace(r.PathValue("id")))
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,500,"deal unavailable"); return }
	writeJSON(w,200,d)
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	if !h.authorize(w,r) { return }
	limit := 100
	if raw := r.URL.Query().Get("limit"); raw != "" { if n,err := strconv.Atoi(raw); err == nil { limit=n } }
	deals,err := h.store.ListForUser(r.Context(),r.PathValue("username"),limit)
	if err != nil { writeError(w,500,"deals unavailable"); return }
	writeJSON(w,200,map[string]any{"deals":deals})
}

func (h *Handler) transition(w http.ResponseWriter, r *http.Request) {
	if !h.authorize(w,r) { return }
	var body struct { Status tscore.DealStatus `json:"status"` }
	dec := json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10)); dec.DisallowUnknownFields()
	if err := dec.Decode(&body); err != nil { writeError(w,400,"invalid status"); return }
	d,err := h.store.Transition(r.Context(),r.PathValue("id"),body.Status)
	if errors.Is(err,tscore.ErrDealNotFound) { writeError(w,404,"deal not found"); return }
	if err != nil { writeError(w,409,err.Error()); return }
	writeJSON(w,200,d)
}

func writeError(w http.ResponseWriter, status int, msg string) { writeJSON(w,status,map[string]string{"error":msg}) }
func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type","application/json; charset=utf-8")
	w.Header().Set("Cache-Control","no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}
