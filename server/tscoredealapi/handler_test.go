package tscoredealapi

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/tinode/chat/tscore"
)

type memoryStore struct { deals map[string]tscore.Deal }

func (m *memoryStore) Create(_ context.Context, in tscore.NewDeal) (tscore.Deal,error) {
	valid,err := tscore.ValidateNewDeal(in); if err != nil { return tscore.Deal{},err }
	d := tscore.Deal{ID:"deal_test",BuyerUsername:valid.BuyerUsername,SellerUsername:valid.SellerUsername,Title:valid.Title,Terms:valid.Terms,AmountMinor:valid.AmountMinor,Currency:valid.Currency,Status:tscore.DealProposed}
	m.deals[d.ID]=d; return d,nil
}
func (m *memoryStore) Get(_ context.Context,id string)(tscore.Deal,error){ d,ok:=m.deals[id]; if !ok{return tscore.Deal{},tscore.ErrDealNotFound}; return d,nil }
func (m *memoryStore) ListForUser(_ context.Context,u string,_ int)([]tscore.Deal,error){ out:=[]tscore.Deal{}; for _,d:=range m.deals{if d.BuyerUsername==u||d.SellerUsername==u{out=append(out,d)}}; return out,nil }
func (m *memoryStore) Transition(_ context.Context,id string,to tscore.DealStatus)(tscore.Deal,error){ d,ok:=m.deals[id]; if !ok{return tscore.Deal{},tscore.ErrDealNotFound}; if err:=tscore.ValidateDealTransition(d.Status,to);err!=nil{return tscore.Deal{},err}; d.Status=to;m.deals[id]=d;return d,nil }

func TestCreateRequiresAdminKey(t *testing.T){
	h:=New(&memoryStore{deals:map[string]tscore.Deal{}},"secret")
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals",bytes.NewBufferString(`{"buyer_username":"alice","seller_username":"bob","title":"Test deal","amount_minor":100,"currency":"USD"}`))
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusUnauthorized{t.Fatalf("status=%d",w.Code)}
}

func TestCreateAndTransition(t *testing.T){
	store:=&memoryStore{deals:map[string]tscore.Deal{}}
	h:=New(store,"secret")
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals",bytes.NewBufferString(`{"buyer_username":"alice","seller_username":"bob","title":"Laptop deal","terms":"sealed","amount_minor":50000,"currency":"PKR"}`)); r.Header.Set("X-TScore-Admin-Key","secret")
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusCreated{t.Fatalf("create status=%d body=%s",w.Code,w.Body.String())}
	r=httptest.NewRequest(http.MethodPost,"/internal/v1/deals/deal_test/transition",bytes.NewBufferString(`{"status":"ACCEPTED"}`)); r.Header.Set("X-TScore-Admin-Key","secret")
	w=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusOK{t.Fatalf("transition status=%d body=%s",w.Code,w.Body.String())}
}

func TestInvalidTransitionRejected(t *testing.T){
	store:=&memoryStore{deals:map[string]tscore.Deal{"d":{ID:"d",BuyerUsername:"alice",SellerUsername:"bob",Status:tscore.DealProposed}}}
	h:=New(store,"secret")
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals/d/transition",bytes.NewBufferString(`{"status":"COMPLETED"}`)); r.Header.Set("X-TScore-Admin-Key","secret")
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusConflict{t.Fatalf("status=%d body=%s",w.Code,w.Body.String())}
}
