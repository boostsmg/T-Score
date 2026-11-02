package tscorestandingapi

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
    Get(context.Context,string)(tscore.AccountStandingRecord,error)
    Set(context.Context,string,tscore.AccountStanding,string)(tscore.AccountStandingRecord,error)
}

type Handler struct { store Store; adminKey string }

func New(store Store, adminKey string) http.Handler {
    h := &Handler{store:store, adminKey:adminKey}
    mux := http.NewServeMux()
    mux.HandleFunc("GET /v1/standing/{userID}", h.publicGet)
    mux.HandleFunc("PUT /internal/v1/standing/{userID}", h.adminSet)
    return mux
}

func (h *Handler) publicGet(w http.ResponseWriter, r *http.Request) {
    userID := strings.TrimSpace(r.PathValue("userID"))
    if !strings.HasPrefix(userID, "usr") { writeErr(w,400,"invalid user id"); return }
    rec, err := h.store.Get(r.Context(), userID)
    if err != nil { writeErr(w,500,"standing unavailable"); return }
    writeJSON(w,200,map[string]any{"user_id":rec.UserID,"standing":rec.Standing,"account_standing_good":tscore.StandingIsGood(rec.Standing),"updated_at":rec.UpdatedAt})
}

func (h *Handler) adminSet(w http.ResponseWriter, r *http.Request) {
    if !h.admin(w,r) { return }
    userID := strings.TrimSpace(r.PathValue("userID"))
    var b struct { Standing tscore.AccountStanding `json:"standing"`; Reason string `json:"reason"` }
    d := json.NewDecoder(http.MaxBytesReader(w,r.Body,16<<10)); d.DisallowUnknownFields()
    if d.Decode(&b) != nil { writeErr(w,400,"invalid standing action"); return }
    rec, err := h.store.Set(r.Context(), userID, b.Standing, b.Reason)
    if errors.Is(err, tscore.ErrStandingInvalid) { writeErr(w,400,"invalid standing action"); return }
    if err != nil { writeErr(w,500,"standing update unavailable"); return }
    writeJSON(w,200,rec)
}

func (h *Handler) admin(w http.ResponseWriter,r *http.Request) bool {
    got := r.Header.Get("X-TScore-Admin-Key")
    if h.adminKey=="" || len(got)!=len(h.adminKey) || subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey))!=1 {
        writeErr(w,401,"unauthorized"); return false
    }
    return true
}

func writeErr(w http.ResponseWriter,s int,m string){writeJSON(w,s,map[string]string{"error":m})}
func writeJSON(w http.ResponseWriter,s int,v any){w.Header().Set("Content-Type","application/json; charset=utf-8");w.Header().Set("Cache-Control","no-store");w.WriteHeader(s);_ = json.NewEncoder(w).Encode(v)}
