package tscoredealapi

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/tinode/chat/server/store/types"
	"github.com/tinode/chat/tscore"
)

type memoryStore struct { deals map[string]tscore.Deal }

func (m *memoryStore) Create(_ context.Context, in tscore.NewDeal) (tscore.Deal,error) {
	valid,err := tscore.ValidateNewDeal(in); if err != nil { return tscore.Deal{},err }
	d := tscore.Deal{ID:"deal_test",BuyerID:valid.BuyerID,SellerID:valid.SellerID,CreatedBy:valid.CreatedBy,Title:valid.Title,Terms:valid.Terms,AmountMinor:valid.AmountMinor,Currency:valid.Currency,Status:tscore.DealProposed}
	m.deals[d.ID]=d; return d,nil
}
func (m *memoryStore) Get(_ context.Context,id string)(tscore.Deal,error){ d,ok:=m.deals[id]; if !ok{return tscore.Deal{},tscore.ErrDealNotFound}; return d,nil }
func (m *memoryStore) ListForUser(_ context.Context,u string,_ int)([]tscore.Deal,error){ out:=[]tscore.Deal{}; for _,d:=range m.deals{if d.BuyerID==u||d.SellerID==u{out=append(out,d)}}; return out,nil }
func (m *memoryStore) Transition(_ context.Context,id string,to tscore.DealStatus)(tscore.Deal,error){ d,ok:=m.deals[id]; if !ok{return tscore.Deal{},tscore.ErrDealNotFound}; if err:=tscore.ValidateDealTransition(d.Status,to);err!=nil{return tscore.Deal{},err}; d.Status=to;m.deals[id]=d;return d,nil }

func users() (string,string) { return types.Uid(101).UserId(), types.Uid(202).UserId() }

func TestCreateRequiresAdminKey(t *testing.T){
	buyer,seller:=users()
	h:=New(&memoryStore{deals:map[string]tscore.Deal{}},"secret")
	body:=`{"buyer_id":"`+buyer+`","seller_id":"`+seller+`","created_by":"`+buyer+`","title":"Test deal","terms":"agreed terms","amount_minor":100,"currency":"USD"}`
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals",bytes.NewBufferString(body))
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusUnauthorized{t.Fatalf("status=%d",w.Code)}
}

func TestCreateAndTransition(t *testing.T){
	buyer,seller:=users()
	store:=&memoryStore{deals:map[string]tscore.Deal{}}
	h:=New(store,"secret")
	body:=`{"buyer_id":"`+buyer+`","seller_id":"`+seller+`","created_by":"`+buyer+`","title":"Laptop deal","terms":"sealed laptop","amount_minor":50000,"currency":"PKR"}`
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals",bytes.NewBufferString(body)); r.Header.Set("X-TScore-Admin-Key","secret")
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusCreated{t.Fatalf("create status=%d body=%s",w.Code,w.Body.String())}
	r=httptest.NewRequest(http.MethodPost,"/internal/v1/deals/deal_test/transition",bytes.NewBufferString(`{"status":"ACCEPTED"}`)); r.Header.Set("X-TScore-Admin-Key","secret")
	w=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusOK{t.Fatalf("transition status=%d body=%s",w.Code,w.Body.String())}
}

func TestInvalidTransitionRejected(t *testing.T){
	buyer,seller:=users()
	store:=&memoryStore{deals:map[string]tscore.Deal{"d":{ID:"d",BuyerID:buyer,SellerID:seller,CreatedBy:buyer,Status:tscore.DealProposed}}}
	h:=New(store,"secret")
	r:=httptest.NewRequest(http.MethodPost,"/internal/v1/deals/d/transition",bytes.NewBufferString(`{"status":"COMPLETED"}`)); r.Header.Set("X-TScore-Admin-Key","secret")
	w:=httptest.NewRecorder(); h.ServeHTTP(w,r)
	if w.Code!=http.StatusConflict{t.Fatalf("status=%d body=%s",w.Code,w.Body.String())}
}
