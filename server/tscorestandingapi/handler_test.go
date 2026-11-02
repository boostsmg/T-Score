package tscorestandingapi

import (
    "context"
    "net/http"
    "net/http/httptest"
    "strings"
    "testing"

    "github.com/tinode/chat/tscore"
)

type memStore struct{ rec tscore.AccountStandingRecord }
func (m *memStore) Get(_ context.Context, id string)(tscore.AccountStandingRecord,error){if m.rec.UserID==""{return tscore.AccountStandingRecord{UserID:id,Standing:tscore.StandingGood},nil};return m.rec,nil}
func (m *memStore) Set(_ context.Context,id string,s tscore.AccountStanding,r string)(tscore.AccountStandingRecord,error){if err:=tscore.ValidateStandingInput(id,s,r);err!=nil{return tscore.AccountStandingRecord{},err};m.rec=tscore.AccountStandingRecord{UserID:id,Standing:s,Reason:r};return m.rec,nil}

func TestPublicStandingDefaultsGood(t *testing.T){h:=New(&memStore{},"secret");req:=httptest.NewRequest(http.MethodGet,"/v1/standing/usr123",nil);rr:=httptest.NewRecorder();h.ServeHTTP(rr,req);if rr.Code!=200{t.Fatalf("got %d",rr.Code)};if !strings.Contains(rr.Body.String(),`"standing":"GOOD"`){t.Fatal(rr.Body.String())};if strings.Contains(rr.Body.String(),`"reason"`){t.Fatal("public response leaked reason")}}
func TestAdminStandingRequiresKey(t *testing.T){h:=New(&memStore{},"secret");req:=httptest.NewRequest(http.MethodPut,"/internal/v1/standing/usr123",strings.NewReader(`{"standing":"RESTRICTED","reason":"confirmed abuse"}`));rr:=httptest.NewRecorder();h.ServeHTTP(rr,req);if rr.Code!=401{t.Fatalf("got %d",rr.Code)}}
func TestAdminStandingUpdate(t *testing.T){h:=New(&memStore{},"secret");req:=httptest.NewRequest(http.MethodPut,"/internal/v1/standing/usr123",strings.NewReader(`{"standing":"RESTRICTED","reason":"confirmed abuse"}`));req.Header.Set("X-TScore-Admin-Key","secret");rr:=httptest.NewRecorder();h.ServeHTTP(rr,req);if rr.Code!=200{t.Fatalf("got %d: %s",rr.Code,rr.Body.String())};if !strings.Contains(rr.Body.String(),`"standing":"RESTRICTED"`){t.Fatal(rr.Body.String())}}
