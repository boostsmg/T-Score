package tscoretrustapi

import (
    "context"
    "net/http/httptest"
    "testing"

    "github.com/tinode/chat/tscore"
)

type fakeVerification struct{ s tscore.VerificationSummary }
func (f fakeVerification) Summary(context.Context,string)(tscore.VerificationSummary,error){ return f.s,nil }
type fakeReputation struct{ s tscore.ReputationSummary }
func (f fakeReputation) ReputationSummaryForUser(context.Context,string)(tscore.ReputationSummary,error){ return f.s,nil }
type fakeStanding struct{ s tscore.AccountStandingRecord }
func (f fakeStanding) Get(context.Context,string)(tscore.AccountStandingRecord,error){ return f.s,nil }

func TestCanonicalTrustAggregatesVerifiedActivityAndStanding(t *testing.T) {
    h := New(
        fakeVerification{tscore.VerificationSummary{UserID:"usr123",Email:true,Phone:true,Identity:true,Address:true,Liveness:true,ProfilePhoto:true}},
        fakeReputation{tscore.ReputationSummary{UserID:"usr123",CompletedDeals:9,ReviewCount:8,AverageRating:4.8}},
        fakeStanding{tscore.AccountStandingRecord{UserID:"usr123",Standing:tscore.StandingGood}},
    )
    rr:=httptest.NewRecorder(); req:=httptest.NewRequest("GET","/v1/trust/users/usr123",nil); h.ServeHTTP(rr,req)
    if rr.Code != 200 { t.Fatalf("expected 200, got %d: %s",rr.Code,rr.Body.String()) }
    body:=rr.Body.String()
    for _,want:=range []string{`"score":95`,`"badge":"Highly Trusted"`,`"successful_deals":15`,`"reviews":10`,`"account_standing":5`} {
        if !contains(body,want){t.Fatalf("missing %s in %s",want,body)}
    }
}

func TestRestrictedStandingRemovesStandingPoints(t *testing.T) {
    h:=New(fakeVerification{},fakeReputation{},fakeStanding{tscore.AccountStandingRecord{UserID:"usr123",Standing:tscore.StandingRestricted}})
    rr:=httptest.NewRecorder(); req:=httptest.NewRequest("GET","/v1/trust/users/usr123",nil); h.ServeHTTP(rr,req)
    if rr.Code!=200{t.Fatalf("expected 200, got %d",rr.Code)}
    if !contains(rr.Body.String(),`"account_standing":0`){t.Fatalf("standing points should be zero: %s",rr.Body.String())}
}

func TestInvalidUIDRejected(t *testing.T) {
    h:=New(fakeVerification{},fakeReputation{},fakeStanding{})
    rr:=httptest.NewRecorder(); req:=httptest.NewRequest("GET","/v1/trust/users/not-a-user",nil); h.ServeHTTP(rr,req)
    if rr.Code!=400{t.Fatalf("expected 400, got %d",rr.Code)}
}

func contains(s,sub string) bool {
    if len(sub)==0{return true}
    for i:=0;i+len(sub)<=len(s);i++{if s[i:i+len(sub)]==sub{return true}}
    return false
}
