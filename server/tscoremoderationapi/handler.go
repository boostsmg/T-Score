package tscoremoderationapi

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
    CreateReport(context.Context,string,string,string,tscore.ReportReason,string)(tscore.ModerationReport,error)
    ListMine(context.Context,string)([]tscore.ModerationReport,error)
    ListReports(context.Context,tscore.ReportStatus)([]tscore.ModerationReport,error)
    DecideReport(context.Context,string,tscore.ReportStatus,string)(tscore.ModerationReport,error)
}
type TokenVerifier interface{ VerifyBase64(string)(string,error) }
type Handler struct{ store Store; verifier TokenVerifier; adminKey string }

func New(store Store, verifier TokenVerifier, adminKey string) http.Handler {
    h:=&Handler{store:store,verifier:verifier,adminKey:adminKey}; m:=http.NewServeMux()
    m.HandleFunc("POST /v1/reports",h.create)
    m.HandleFunc("GET /v1/reports/mine",h.mine)
    m.HandleFunc("GET /internal/moderation",h.dashboard)
    m.HandleFunc("GET /internal/v1/reports",h.adminList)
    m.HandleFunc("POST /internal/v1/reports/{id}/decision",h.adminDecision)
    return m
}
func (h *Handler) actor(w http.ResponseWriter,r *http.Request)(string,bool){a:=strings.TrimSpace(r.Header.Get("Authorization"));if h.verifier==nil||!strings.HasPrefix(strings.ToLower(a),"bearer "){writeErr(w,401,"missing bearer token");return "",false};id,err:=h.verifier.VerifyBase64(strings.TrimSpace(a[len("Bearer "): ]));if err!=nil{writeErr(w,401,"invalid or expired session");return "",false};return id,true}
func (h *Handler) admin(w http.ResponseWriter,r *http.Request)bool{got:=r.Header.Get("X-TScore-Admin-Key");if h.adminKey==""||len(got)!=len(h.adminKey)||subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey))!=1{writeErr(w,401,"unauthorized");return false};return true}
func (h *Handler) create(w http.ResponseWriter,r *http.Request){actor,ok:=h.actor(w,r);if !ok{return};var b struct{TargetID string `json:"target_id"`;TopicName string `json:"topic_name"`;Reason tscore.ReportReason `json:"reason"`;Details string `json:"details"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,16<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid report");return};rep,err:=h.store.CreateReport(r.Context(),actor,b.TargetID,b.TopicName,b.Reason,b.Details);if errors.Is(err,tscore.ErrReportInvalid){writeErr(w,400,"invalid report");return};if err!=nil{writeErr(w,500,"report unavailable");return};writeJSON(w,201,rep)}
func (h *Handler) mine(w http.ResponseWriter,r *http.Request){actor,ok:=h.actor(w,r);if !ok{return};items,err:=h.store.ListMine(r.Context(),actor);if err!=nil{writeErr(w,500,"reports unavailable");return};writeJSON(w,200,map[string]any{"reports":items})}
func (h *Handler) adminList(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};status:=tscore.ReportStatus(strings.TrimSpace(r.URL.Query().Get("status")));items,err:=h.store.ListReports(r.Context(),status);if err!=nil{writeErr(w,500,"reports unavailable");return};writeJSON(w,200,map[string]any{"reports":items})}
func (h *Handler) adminDecision(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};var b struct{Status tscore.ReportStatus `json:"status"`;Note string `json:"note"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,16<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid decision");return};rep,err:=h.store.DecideReport(r.Context(),r.PathValue("id"),b.Status,b.Note);if errors.Is(err,tscore.ErrReportInvalid){writeErr(w,400,"invalid decision");return};if errors.Is(err,tscore.ErrReportNotFound){writeErr(w,404,"report not found");return};if err!=nil{writeErr(w,500,"decision unavailable");return};writeJSON(w,200,rep)}
func writeErr(w http.ResponseWriter,s int,m string){writeJSON(w,s,map[string]string{"error":m})}
func writeJSON(w http.ResponseWriter,s int,v any){w.Header().Set("Content-Type","application/json; charset=utf-8");w.Header().Set("Cache-Control","no-store");w.WriteHeader(s);_ = json.NewEncoder(w).Encode(v)}
