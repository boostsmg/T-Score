package tscoreverificationapi

import (
    "bytes"
    "context"
    "errors"
    "net/http"
    "net/http/httptest"
    "testing"

    "github.com/tinode/chat/tscore"
)

type memStore struct{items map[string]tscore.VerificationRequest; next int}
func (m *memStore) Create(_ context.Context,u string,t tscore.VerificationType,e string)(tscore.VerificationRequest,error){for _,v:=range m.items{if v.UserID==u&&v.Type==t&&v.Status==tscore.VerificationPending{return tscore.VerificationRequest{},tscore.ErrVerificationExists}};m.next++;id:=string(rune('0'+m.next));v:=tscore.VerificationRequest{ID:id,UserID:u,Type:t,Status:tscore.VerificationPending,EvidenceRef:e};m.items[id]=v;return v,nil}
func (m *memStore) ListForUser(_ context.Context,u string)([]tscore.VerificationRequest,error){out:=[]tscore.VerificationRequest{};for _,v:=range m.items{if v.UserID==u{out=append(out,v)}};return out,nil}
func (m *memStore) Summary(_ context.Context,u string)(tscore.VerificationSummary,error){s:=tscore.VerificationSummary{UserID:u};for _,v:=range m.items{if v.UserID!=u||v.Status!=tscore.VerificationApproved{continue};switch v.Type{case tscore.VerificationEmail:s.Email=true;case tscore.VerificationPhone:s.Phone=true;case tscore.VerificationIdentity:s.Identity=true;case tscore.VerificationAddress:s.Address=true;case tscore.VerificationLiveness:s.Liveness=true;case tscore.VerificationPhoto:s.ProfilePhoto=true}};return s,nil}
func (m *memStore) AttachEvidence(_ context.Context,id,e string)(tscore.VerificationRequest,error){v,ok:=m.items[id];if !ok{return v,tscore.ErrVerificationNotFound};v.EvidenceRef=e;m.items[id]=v;return v,nil}
func (m *memStore) Decide(_ context.Context,id string,s tscore.VerificationStatus,n string)(tscore.VerificationRequest,error){v,ok:=m.items[id];if !ok{return v,tscore.ErrVerificationNotFound};if s==tscore.VerificationApproved&&tscore.IsDocumentVerification(v.Type)&&v.EvidenceRef==""{return v,errors.New("private evidence required before approval")};v.Status=s;v.Note=n;m.items[id]=v;return v,nil}
type verifier map[string]string
func (v verifier) VerifyBase64(t string)(string,error){if u,ok:=v[t];ok{return u,nil};return "",errors.New("bad token")}

func TestDocumentApprovalRequiresEvidence(t *testing.T){m:=&memStore{items:map[string]tscore.VerificationRequest{"1":{ID:"1",UserID:"usrA",Type:tscore.VerificationIdentity,Status:tscore.VerificationPending}}};h:=New(m,verifier{"x":"usrA"},"admin");r:=httptest.NewRequest(http.MethodPost,"/internal/v1/verifications/1/decision",bytes.NewBufferString(`{"status":"APPROVED","note":"ok"}`));r.Header.Set("X-TScore-Admin-Key","admin");w:=httptest.NewRecorder();h.ServeHTTP(w,r);if w.Code!=http.StatusConflict{t.Fatalf("status=%d body=%s",w.Code,w.Body.String())}}
func TestDuplicatePendingRejected(t *testing.T){m:=&memStore{items:map[string]tscore.VerificationRequest{}};h:=New(m,verifier{"x":"usrA"},"admin");for i:=0;i<2;i++{r:=httptest.NewRequest(http.MethodPost,"/v1/verifications",bytes.NewBufferString(`{"type":"EMAIL"}`));r.Header.Set("Authorization","Bearer x");w:=httptest.NewRecorder();h.ServeHTTP(w,r);if i==0&&w.Code!=201{t.Fatalf("first=%d",w.Code)};if i==1&&w.Code!=409{t.Fatalf("second=%d body=%s",w.Code,w.Body.String())}}}
func TestPublicSummaryOnlyShowsApprovedFlags(t *testing.T){m:=&memStore{items:map[string]tscore.VerificationRequest{"1":{ID:"1",UserID:"usrA",Type:tscore.VerificationEmail,Status:tscore.VerificationApproved,EvidenceRef:"secret"},"2":{ID:"2",UserID:"usrA",Type:tscore.VerificationIdentity,Status:tscore.VerificationPending,EvidenceRef:"private-doc"}}};h:=New(m,verifier{},"admin");r:=httptest.NewRequest(http.MethodGet,"/v1/verifications/users/usrA/summary",nil);w:=httptest.NewRecorder();h.ServeHTTP(w,r);body:=w.Body.String();if w.Code!=200||!bytes.Contains([]byte(body),[]byte(`"email_verified":true`))||bytes.Contains([]byte(body),[]byte("secret"))||bytes.Contains([]byte(body),[]byte("private-doc")){t.Fatalf("status=%d body=%s",w.Code,body)}}
