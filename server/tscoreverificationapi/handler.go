package tscoreverificationapi

import (
    "context"
    "crypto/subtle"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "net/http"
    "strconv"
    "strings"

    "github.com/tinode/chat/tscore"
)

type Store interface {
    Create(context.Context,string,tscore.VerificationType,string)(tscore.VerificationRequest,error)
    ListForUser(context.Context,string)([]tscore.VerificationRequest,error)
    Summary(context.Context,string)(tscore.VerificationSummary,error)
    AttachEvidence(context.Context,string,string)(tscore.VerificationRequest,error)
    Decide(context.Context,string,tscore.VerificationStatus,string)(tscore.VerificationRequest,error)
    GetRequest(context.Context,string)(tscore.VerificationRequest,error)
    ListPending(context.Context)([]tscore.VerificationRequest,error)
}
type EvidenceStore interface {
    Put(string,io.Reader,int64)(tscore.EvidenceMeta,error)
    Get(string)([]byte,string,error)
    Delete(string) error
}
type TokenVerifier interface{VerifyBase64(string)(string,error)}
type Handler struct{store Store; verifier TokenVerifier; adminKey string; evidence EvidenceStore}
func New(store Store,verifier TokenVerifier,adminKey string,evidence ...EvidenceStore) http.Handler{
    h:=&Handler{store:store,verifier:verifier,adminKey:adminKey};if len(evidence)>0{h.evidence=evidence[0]};m:=http.NewServeMux()
    m.HandleFunc("GET /v1/verifications",h.listMine)
    m.HandleFunc("POST /v1/verifications",h.createMine)
    m.HandleFunc("PUT /v1/verifications/{id}/evidence",h.uploadEvidence)
    m.HandleFunc("GET /v1/verifications/users/{userID}/summary",h.publicSummary)
    m.HandleFunc("GET /internal/v1/verifications/pending",h.listPending)
    m.HandleFunc("GET /internal/v1/verifications/{id}/evidence",h.getEvidence)
    m.HandleFunc("POST /internal/v1/verifications/{id}/evidence",h.attachEvidence)
    m.HandleFunc("POST /internal/v1/verifications/{id}/decision",h.decide)
    return m
}
func (h *Handler) actor(w http.ResponseWriter,r *http.Request)(string,bool){
    if h.verifier==nil{writeErr(w,503,"authentication unavailable");return "",false}
    a:=strings.TrimSpace(r.Header.Get("Authorization"));if !strings.HasPrefix(strings.ToLower(a),"bearer "){writeErr(w,401,"missing bearer token");return "",false}
    id,err:=h.verifier.VerifyBase64(strings.TrimSpace(a[len("Bearer "):]))
    if err!=nil{writeErr(w,401,"invalid or expired session");return "",false};return id,true
}
func (h *Handler) admin(w http.ResponseWriter,r *http.Request)bool{got:=r.Header.Get("X-TScore-Admin-Key");if h.adminKey==""||len(got)!=len(h.adminKey)||subtle.ConstantTimeCompare([]byte(got),[]byte(h.adminKey))!=1{writeErr(w,401,"unauthorized");return false};return true}
func (h *Handler) listMine(w http.ResponseWriter,r *http.Request){u,ok:=h.actor(w,r);if !ok{return};items,err:=h.store.ListForUser(r.Context(),u);if err!=nil{writeErr(w,500,"verifications unavailable");return};writeJSON(w,200,map[string]any{"verifications":items})}
func (h *Handler) createMine(w http.ResponseWriter,r *http.Request){u,ok:=h.actor(w,r);if !ok{return};var b struct{Type tscore.VerificationType `json:"type"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid verification request");return};v,err:=h.store.Create(r.Context(),u,b.Type,"");switch{case errors.Is(err,tscore.ErrVerificationExists):writeErr(w,409,"verification already pending");case errors.Is(err,tscore.ErrVerificationInvalid):writeErr(w,400,"invalid verification type");case err!=nil:writeErr(w,500,"verification unavailable");default:writeJSON(w,201,map[string]any{"verification":v,"evidence_required":tscore.IsDocumentVerification(v.Type)})}}
func (h *Handler) publicSummary(w http.ResponseWriter,r *http.Request){s,err:=h.store.Summary(r.Context(),r.PathValue("userID"));if err!=nil{writeErr(w,500,"verification summary unavailable");return};writeJSON(w,200,s)}

func (h *Handler) uploadEvidence(w http.ResponseWriter,r *http.Request){
    actor,ok:=h.actor(w,r);if !ok{return};if h.evidence==nil{writeErr(w,503,"private evidence storage unavailable");return}
    id:=strings.TrimSpace(r.PathValue("id"));v,err:=h.store.GetRequest(r.Context(),id)
    if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,500,"verification unavailable");return}
    if v.UserID!=actor{writeErr(w,403,"not your verification request");return}
    if v.Status!=tscore.VerificationPending{writeErr(w,409,"verification is no longer pending");return}
    if !tscore.IsDocumentVerification(v.Type){writeErr(w,409,"this verification does not accept document evidence");return}
    if v.EvidenceRef!=""{writeErr(w,409,"evidence already uploaded");return}
    size,err:=strconv.ParseInt(strings.TrimSpace(r.Header.Get("Content-Length")),10,64);if err!=nil||size<=0||size>tscore.MaxEvidenceBytes{writeErr(w,413,"evidence must be between 1 byte and 8 MB");return}
    contentType:=strings.TrimSpace(strings.Split(r.Header.Get("Content-Type"),";")[0])
    meta,err:=h.evidence.Put(contentType,http.MaxBytesReader(w,r.Body,tscore.MaxEvidenceBytes),size)
    if err!=nil{writeErr(w,400,"unsupported or invalid evidence file");return}
    out,err:=h.store.AttachEvidence(r.Context(),id,meta.Ref);if err!=nil{_ = h.evidence.Delete(meta.Ref);writeErr(w,409,"could not attach evidence");return}
    writeJSON(w,200,map[string]any{"verification":out,"evidence_uploaded":true,"content_type":meta.ContentType,"size":meta.Size})
}
func (h *Handler) listPending(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};items,err:=h.store.ListPending(r.Context());if err!=nil{writeErr(w,500,"pending verifications unavailable");return};writeJSON(w,200,map[string]any{"verifications":items})}
func (h *Handler) getEvidence(w http.ResponseWriter,r *http.Request){
    if !h.admin(w,r){return};if h.evidence==nil{writeErr(w,503,"private evidence storage unavailable");return}
    v,err:=h.store.GetRequest(r.Context(),r.PathValue("id"));if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,500,"verification unavailable");return}
    if v.EvidenceRef==""{writeErr(w,404,"evidence not uploaded");return}
    data,contentType,err:=h.evidence.Get(v.EvidenceRef);if err!=nil{writeErr(w,500,"evidence unavailable");return}
    w.Header().Set("Content-Type",contentType);w.Header().Set("Cache-Control","no-store, private");w.Header().Set("Content-Disposition",fmt.Sprintf("inline; filename=verification-%s",v.ID));w.Header().Set("Content-Length",strconv.Itoa(len(data)));w.WriteHeader(200);_,_=w.Write(data)
}
func (h *Handler) attachEvidence(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};var b struct{EvidenceRef string `json:"evidence_ref"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid evidence");return};v,err:=h.store.AttachEvidence(r.Context(),r.PathValue("id"),b.EvidenceRef);if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,400,err.Error());return};writeJSON(w,200,v)}
func (h *Handler) decide(w http.ResponseWriter,r *http.Request){if !h.admin(w,r){return};var b struct{Status tscore.VerificationStatus `json:"status"`;Note string `json:"note"`};d:=json.NewDecoder(http.MaxBytesReader(w,r.Body,8<<10));d.DisallowUnknownFields();if d.Decode(&b)!=nil{writeErr(w,400,"invalid decision");return};v,err:=h.store.Decide(r.Context(),r.PathValue("id"),b.Status,b.Note);if errors.Is(err,tscore.ErrVerificationNotFound){writeErr(w,404,"verification not found");return};if err!=nil{writeErr(w,409,err.Error());return};writeJSON(w,200,v)}
func writeErr(w http.ResponseWriter,s int,m string){writeJSON(w,s,map[string]string{"error":m})}
func writeJSON(w http.ResponseWriter,s int,v any){w.Header().Set("Content-Type","application/json; charset=utf-8");w.Header().Set("Cache-Control","no-store");w.WriteHeader(s);_ = json.NewEncoder(w).Encode(v)}
