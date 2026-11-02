package tscorereputationapi

import (
	"bytes"
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/tinode/chat/server/store/types"
	"github.com/tinode/chat/tscore"
)

type memoryStore struct {
	deals map[string]tscore.Deal
	reviews map[string]map[string]tscore.DealReview
	disputes map[string]tscore.DealDispute
}
func (m *memoryStore) Get(_ context.Context,id string)(tscore.Deal,error){d,ok:=m.deals[id];if !ok{return tscore.Deal{},tscore.ErrDealNotFound};return d,nil}
func (m *memoryStore) SubmitReview(_ context.Context,dealID,reviewer string,rating int,comment string)(tscore.DealReview,error){
	d,ok:=m.deals[dealID];if !ok{return tscore.DealReview{},tscore.ErrDealNotFound}
	if d.Status!=tscore.DealCompleted||!tscore.IsDealParticipant(d,reviewer){return tscore.DealReview{},tscore.ErrReviewNotAllowed}
	if m.reviews[dealID]==nil{m.reviews[dealID]=map[string]tscore.DealReview{}}
	if _,ok:=m.reviews[dealID][reviewer];ok{return tscore.DealReview{},tscore.ErrReviewExists}
	if _,err:=tscore.ValidateReviewInput(rating,comment);err!=nil{return tscore.DealReview{},err}
	r:=tscore.DealReview{ID:"1",DealID:dealID,ReviewerID:reviewer,RevieweeID:tscore.RevieweeFor(d,reviewer),Rating:rating,Comment:comment};m.reviews[dealID][reviewer]=r;return r,nil
}
func (m *memoryStore) ListDealReviews(_ context.Context,dealID string)([]tscore.DealReview,error){out:=[]tscore.DealReview{};for _,r:=range m.reviews[dealID]{out=append(out,r)};return out,nil}
func (m *memoryStore) OpenDispute(_ context.Context,dealID,actor,reason,details string)(tscore.DealDispute,tscore.Deal,error){
	d,ok:=m.deals[dealID];if !ok{return tscore.DealDispute{},tscore.Deal{},tscore.ErrDealNotFound}
	if !tscore.IsDealParticipant(d,actor)||!tscore.CanUserTransitionDeal(d,actor,tscore.DealDisputed){return tscore.DealDispute{},tscore.Deal{},tscore.ErrDisputeNotAllowed}
	if _,ok:=m.disputes[dealID];ok{return tscore.DealDispute{},tscore.Deal{},tscore.ErrDisputeExists}
	if _,_,err:=tscore.ValidateDisputeInput(reason,details);err!=nil{return tscore.DealDispute{},tscore.Deal{},err}
	dp:=tscore.DealDispute{ID:"1",DealID:dealID,OpenedBy:actor,Reason:reason,Details:details,Status:"OPEN"};m.disputes[dealID]=dp;d.Status=tscore.DealDisputed;m.deals[dealID]=d;return dp,d,nil
}
func (m *memoryStore) GetOpenDispute(_ context.Context,dealID string)(tscore.DealDispute,error){d,ok:=m.disputes[dealID];if !ok{return tscore.DealDispute{},tscore.ErrDealNotFound};return d,nil}

type verifier map[string]string
func (v verifier) VerifyBase64(token string)(string,error){if u,ok:=v[token];ok{return u,nil};return "",errors.New("bad")}
func ids()(string,string,string){return types.Uid(101).UserId(),types.Uid(202).UserId(),types.Uid(303).UserId()}

func newStore(status tscore.DealStatus)(*memoryStore,string,string,string){a,b,c:=ids();m:=&memoryStore{deals:map[string]tscore.Deal{"d":{ID:"d",BuyerID:a,SellerID:b,CreatedBy:a,Status:status}},reviews:map[string]map[string]tscore.DealReview{},disputes:map[string]tscore.DealDispute{}};return m,a,b,c}

func TestReviewRequiresCompletedDeal(t *testing.T){m,a,_,_:=newStore(tscore.DealDelivered);h:=New(m,verifier{"a":a});r:=httptest.NewRequest(http.MethodPost,"/v1/reputation/reviews",bytes.NewBufferString(`{"deal_id":"d","rating":5,"comment":"great deal"}`));r.Header.Set("Authorization","Bearer a");w:=httptest.NewRecorder();h.ServeHTTP(w,r);if w.Code!=http.StatusConflict{t.Fatalf("status=%d body=%s",w.Code,w.Body.String())}}
func TestDuplicateReviewRejected(t *testing.T){m,a,_,_:=newStore(tscore.DealCompleted);h:=New(m,verifier{"a":a});body:=[]byte(`{"deal_id":"d","rating":5,"comment":"great deal"}`);for i:=0;i<2;i++{r:=httptest.NewRequest(http.MethodPost,"/v1/reputation/reviews",bytes.NewReader(body));r.Header.Set("Authorization","Bearer a");w:=httptest.NewRecorder();h.ServeHTTP(w,r);if i==0&&w.Code!=201{t.Fatalf("first=%d",w.Code)};if i==1&&w.Code!=409{t.Fatalf("second=%d body=%s",w.Code,w.Body.String())}}}
func TestOutsiderCannotOpenDispute(t *testing.T){m,_,_,c:=newStore(tscore.DealInProgress);h:=New(m,verifier{"c":c});r:=httptest.NewRequest(http.MethodPost,"/v1/reputation/disputes",bytes.NewBufferString(`{"deal_id":"d","reason":"DELIVERY","details":"seller did not deliver as agreed"}`));r.Header.Set("Authorization","Bearer c");w:=httptest.NewRecorder();h.ServeHTTP(w,r);if w.Code!=http.StatusForbidden{t.Fatalf("status=%d body=%s",w.Code,w.Body.String())}}
