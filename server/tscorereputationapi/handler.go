package tscorereputationapi

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/tinode/chat/tscore"
)

type Store interface {
	Get(context.Context, string) (tscore.Deal, error)
	SubmitReview(context.Context, string, string, int, string) (tscore.DealReview, error)
	ListDealReviews(context.Context, string) ([]tscore.DealReview, error)
	OpenDispute(context.Context, string, string, string, string) (tscore.DealDispute, tscore.Deal, error)
	GetOpenDispute(context.Context, string) (tscore.DealDispute, error)
}

type TokenVerifier interface { VerifyBase64(string) (string, error) }

type Handler struct { store Store; verifier TokenVerifier }

func New(store Store, verifier TokenVerifier) http.Handler {
	h := &Handler{store:store, verifier:verifier}
	mux := http.NewServeMux()
	mux.HandleFunc("POST /v1/reputation/reviews", h.submitReview)
	mux.HandleFunc("GET /v1/reputation/deals/{id}/reviews", h.listReviews)
	mux.HandleFunc("POST /v1/reputation/disputes", h.openDispute)
	mux.HandleFunc("GET /v1/reputation/deals/{id}/dispute", h.getDispute)
	return mux
}

func (h *Handler) actor(w http.ResponseWriter, r *http.Request) (string,bool) {
	if h.verifier == nil { writeError(w,503,"authentication unavailable"); return "",false }
	a := strings.TrimSpace(r.Header.Get("Authorization"))
	if !strings.HasPrefix(strings.ToLower(a),"bearer ") { writeError(w,401,"missing bearer token"); return "",false }
	id,err := h.verifier.VerifyBase64(strings.TrimSpace(a[len("Bearer "):]))
	if err != nil { writeError(w,401,"invalid or expired session"); return "",false }
	return id,true
}

func (h *Handler) participantDeal(w http.ResponseWriter, r *http.Request, actor, dealID string) (tscore.Deal,bool) {
	d,err:=h.store.Get(r.Context(),dealID)
	if errors.Is(err,tscore.ErrDealNotFound){writeError(w,404,"deal not found");return tscore.Deal{},false}
	if err!=nil{writeError(w,500,"deal unavailable");return tscore.Deal{},false}
	if !tscore.IsDealParticipant(d,actor){writeError(w,403,"not a deal participant");return tscore.Deal{},false}
	return d,true
}

func (h *Handler) submitReview(w http.ResponseWriter, r *http.Request) {
	actor,ok:=h.actor(w,r); if !ok{return}
	var body struct { DealID string `json:"deal_id"`; Rating int `json:"rating"`; Comment string `json:"comment"` }
	dec:=json.NewDecoder(http.MaxBytesReader(w,r.Body,16<<10)); dec.DisallowUnknownFields()
	if err:=dec.Decode(&body);err!=nil{writeError(w,400,"invalid review");return}
	if _,ok:=h.participantDeal(w,r,actor,strings.TrimSpace(body.DealID));!ok{return}
	review,err:=h.store.SubmitReview(r.Context(),strings.TrimSpace(body.DealID),actor,body.Rating,body.Comment)
	switch {
	case errors.Is(err,tscore.ErrReviewExists): writeError(w,409,"review already submitted")
	case errors.Is(err,tscore.ErrReviewNotAllowed): writeError(w,409,"review allowed only after completed deal")
	case err!=nil: writeError(w,400,err.Error())
	default: writeJSON(w,201,review)
	}
}

func (h *Handler) listReviews(w http.ResponseWriter, r *http.Request) {
	actor,ok:=h.actor(w,r); if !ok{return}
	id:=strings.TrimSpace(r.PathValue("id"))
	if _,ok:=h.participantDeal(w,r,actor,id);!ok{return}
	reviews,err:=h.store.ListDealReviews(r.Context(),id)
	if err!=nil{writeError(w,500,"reviews unavailable");return}
	writeJSON(w,200,map[string]any{"reviews":reviews})
}

func (h *Handler) openDispute(w http.ResponseWriter, r *http.Request) {
	actor,ok:=h.actor(w,r); if !ok{return}
	var body struct { DealID string `json:"deal_id"`; Reason string `json:"reason"`; Details string `json:"details"` }
	dec:=json.NewDecoder(http.MaxBytesReader(w,r.Body,16<<10)); dec.DisallowUnknownFields()
	if err:=dec.Decode(&body);err!=nil{writeError(w,400,"invalid dispute");return}
	if _,ok:=h.participantDeal(w,r,actor,strings.TrimSpace(body.DealID));!ok{return}
	dispute,deal,err:=h.store.OpenDispute(r.Context(),strings.TrimSpace(body.DealID),actor,body.Reason,body.Details)
	switch {
	case errors.Is(err,tscore.ErrDisputeExists): writeError(w,409,"dispute already open")
	case errors.Is(err,tscore.ErrDisputeNotAllowed): writeError(w,409,"dispute not allowed for this deal")
	case err!=nil: writeError(w,400,err.Error())
	default: writeJSON(w,201,map[string]any{"dispute":dispute,"deal":deal})
	}
}

func (h *Handler) getDispute(w http.ResponseWriter, r *http.Request) {
	actor,ok:=h.actor(w,r); if !ok{return}
	id:=strings.TrimSpace(r.PathValue("id"))
	if _,ok:=h.participantDeal(w,r,actor,id);!ok{return}
	d,err:=h.store.GetOpenDispute(r.Context(),id)
	if errors.Is(err,tscore.ErrDealNotFound){writeError(w,404,"no open dispute");return}
	if err!=nil{writeError(w,500,"dispute unavailable");return}
	writeJSON(w,200,d)
}

func writeError(w http.ResponseWriter,status int,msg string){writeJSON(w,status,map[string]string{"error":msg})}
func writeJSON(w http.ResponseWriter,status int,v any){w.Header().Set("Content-Type","application/json; charset=utf-8");w.Header().Set("Cache-Control","no-store");w.WriteHeader(status);_ = json.NewEncoder(w).Encode(v)}
