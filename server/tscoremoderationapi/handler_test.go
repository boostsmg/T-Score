package tscoremoderationapi

import (
    "context"
    "net/http"
    "net/http/httptest"
    "strings"
    "testing"

    "github.com/tinode/chat/tscore"
)

type fakeVerifier struct{ id string; err error }
func (f fakeVerifier) VerifyBase64(string)(string,error){ return f.id,f.err }

type fakeStore struct{ reports []tscore.ModerationReport }
func (f *fakeStore) CreateReport(_ context.Context, reporter,target,topic string,reason tscore.ReportReason,details string)(tscore.ModerationReport,error){
    if err:=tscore.ValidateReportInput(reporter,target,reason,details);err!=nil{return tscore.ModerationReport{},err}
    r:=tscore.ModerationReport{ID:"rpt_test",ReporterID:reporter,TargetID:target,TopicName:topic,Reason:reason,Details:details,Status:tscore.ReportOpen}; f.reports=append(f.reports,r); return r,nil
}
func (f *fakeStore) ListMine(_ context.Context,id string)([]tscore.ModerationReport,error){var out []tscore.ModerationReport;for _,r:=range f.reports{if r.ReporterID==id{out=append(out,r)}};return out,nil}
func (f *fakeStore) ListReports(_ context.Context,status tscore.ReportStatus)([]tscore.ModerationReport,error){if status==""{return f.reports,nil};var out []tscore.ModerationReport;for _,r:=range f.reports{if r.Status==status{out=append(out,r)}};return out,nil}
func (f *fakeStore) DecideReport(_ context.Context,id string,status tscore.ReportStatus,note string)(tscore.ModerationReport,error){if !tscore.ValidReportDecision(status){return tscore.ModerationReport{},tscore.ErrReportInvalid};for i,r:=range f.reports{if r.ID==id{r.Status=status;r.AdminNote=note;f.reports[i]=r;return r,nil}};return tscore.ModerationReport{},tscore.ErrReportNotFound}

func TestReportRequiresBearer(t *testing.T){
    h:=New(&fakeStore{},fakeVerifier{id:"usr1"},"secret")
    req:=httptest.NewRequest(http.MethodPost,"/v1/reports",strings.NewReader(`{"target_id":"usr2","reason":"SCAM"}`))
    rr:=httptest.NewRecorder();h.ServeHTTP(rr,req)
    if rr.Code!=http.StatusUnauthorized{t.Fatalf("got %d",rr.Code)}
}

func TestSelfReportRejected(t *testing.T){
    h:=New(&fakeStore{},fakeVerifier{id:"usr1"},"secret")
    req:=httptest.NewRequest(http.MethodPost,"/v1/reports",strings.NewReader(`{"target_id":"usr1","reason":"SCAM"}`));req.Header.Set("Authorization","Bearer token")
    rr:=httptest.NewRecorder();h.ServeHTTP(rr,req)
    if rr.Code!=http.StatusBadRequest{t.Fatalf("got %d body=%s",rr.Code,rr.Body.String())}
}

func TestAdminListRequiresKey(t *testing.T){
    h:=New(&fakeStore{},fakeVerifier{id:"usr1"},"secret")
    rr:=httptest.NewRecorder();h.ServeHTTP(rr,httptest.NewRequest(http.MethodGet,"/internal/v1/reports",nil))
    if rr.Code!=http.StatusUnauthorized{t.Fatalf("got %d",rr.Code)}
}

func TestDashboardDoesNotEmbedAdminKey(t *testing.T){
    h:=New(&fakeStore{},fakeVerifier{id:"usr1"},"super-secret-value")
    rr:=httptest.NewRecorder();h.ServeHTTP(rr,httptest.NewRequest(http.MethodGet,"/internal/moderation",nil))
    if rr.Code!=http.StatusOK{t.Fatalf("got %d",rr.Code)}
    if strings.Contains(rr.Body.String(),"super-secret-value"){t.Fatal("dashboard leaked admin key")}
    if !strings.Contains(rr.Header().Get("Content-Security-Policy"),"frame-ancestors 'none'"){t.Fatal("missing CSP")}
}
