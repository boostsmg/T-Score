package tscoreverificationapi

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
    Create(context.Context,string,tscore.VerificationType,string)(tscore.VerificationRequest,error)
    ListForUser(context.Context,string)([]tscore.VerificationRequest,error)
    Summary(context.Context,string)(tscore.VerificationSummary,error)
    AttachEvidence(context.Context,string,string)(tscore.VerificationRequest,error)
    Decide(context.Context,string,tscore.VerificationStatus,string)(tscore.VerificationRequest,error)
}
type TokenVerifier interface{VerifyBase64(string)(string,error)}
type Handler struct{store Store; verifier TokenVerifier; adminKey string}
func New(store Store,verifier TokenVerifier,adminKey string) http.Handler{
    h:=&Handler{store:store,verifier:verifier,adminKey:adminKey};m:=http.NewServeMux()
    m.HandleFunc("GET /v1/verifications",h.listMine)
    m.HandleFunc("POST /v1/verifications",h.createMine)
    m.HandleFunc("GET /v1/verifications/users/{userID}/summary",h.publicSummary)
    m.HandleFunc("POST /internal/v1/verifications/{id}/evidence",h.attachEvidence)
    m.HandleFunc("POST /internal/v1/verifications/{id}/decision",h.decide)
    return m
}
func (h *Handler) actor(w http.ResponseWriter,r *http.Request)(string,bool){
    a:=strings.TrimSpace(r.Header.Get("Authorization"));if !strings.HasPrefix(strings.ToLower(a),"bearer "){writeErr(w,401,"missing bearer token");return "",false}
    id,err:=h.verifier.VerifyBase64(strings.TrimSpace(a[len("Bearer "):]))
    if err!=nil{writeErr(w,401,"invalid or expired session");return "",false};return id,true
}
func (h *Handler) admin(w http.ResponseWriter,r *http.Request)bool{got:=r.Header.Get("X-TScore-Admin-Key");if h.adminKey==""||len(got)!=len(h.adminKey)||subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey))!=1{writeErr(w,401,"unauthorized");return false};return true}
func (h *Handler) listMine(w http.ResponseWriter,r *http.Request){u,ok:=h.actor(w,r);if !ok{return};items,err:=h.store.ListForUser(r.Context(),u);if err!=nil{writeErr(w,500,"verifications unavailable");return};writeJSON(w,200,map[string]any{"verifications":items})}
func (h *Handler) createMine(w http.ResponseWriter,r *http.Request){u,ok:=h.actor(w,r);if !ok{return};var b struct{Type tscore.VerificationType `json:"type"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid verification request");return};v,err:=h.store.Create(r.Context(),u,b.Type,"");switch{case errors.Is(err,tscore.ErrVerificationExists):writeErr(w,409,"verification already pending");case errors.Is(err,tscore.ErrVerificationInvalid):writeErr(w,400,"invalid verification type");case err!=nil:writeErr(w,500,"verification unavailable");default:writeJSON(w,201,map[string]any{"verification":v,"evidence_required":tscore.IsDocumentVerification(v.Type)})}}
func (h *Handler) publicSummary(w http.ResponseWriter,r *http.Request){s,err:=h.store.Summary(r.Context(),r.PathValue("userID"));if err!=nil{writeErr(w,500,"verification summary unavailable");return};writeJSON(w,200,s)}
func (h *Handler) attachEvidence(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};var b struct{EvidenceRef string `json:"evidence_ref"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid evidence");return};v,err:=h.store.AttachEvidence(r.Context(),r.PathValue("id"),b.EvidenceRef);if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,400,err.Error());return};writeJSON(w,200,v)}
func (h *Handler) decide(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};var b struct{Status tscore.VerificationStatus `json:"status"`;Note string `json:"note"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid decision");return};v,err:=h.store.Decide(r.Context(),r.PathValue("id"),b.Status,b.Note);if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,409,err.Error());return};writeJSON(w,200,v)}
func writeErr(w http.ResponseWriter,s int,m string){writeJSON(w,s,map[string]string{"error":m})}
func writeJSON(w http.ResponseWriter,s int,v any){w.Header().Set("Content-Type","application/json; charset=utf-8");w.Header().Set("Cache-Control","no-store");w.WriteHeader(s);_ = json.NewEncoder(w).Encode(v)}
