package tscore

import (
	"testing"

	"github.com/tinode/chat/server/store/types"
)

func TestValidateNewDeal(t *testing.T) {
	buyer := types.Uid(101).UserId()
	seller := types.Uid(202).UserId()
	in, err := ValidateNewDeal(NewDeal{
		BuyerID: buyer, SellerID: seller, Title: "Phone purchase", Terms: "New sealed phone", AmountMinor: 125000, Currency: "pkr",
	})
	if err != nil { t.Fatal(err) }
	if in.BuyerID != buyer || in.SellerID != seller || in.Currency != "PKR" { t.Fatalf("normalized=%+v", in) }
}

func TestDealLifecycle(t *testing.T) {
	valid := [][2]DealStatus{
		{DealProposed,DealAccepted}, {DealAccepted,DealInProgress}, {DealInProgress,DealDelivered}, {DealDelivered,DealCompleted},
		{DealProposed,DealCancelled}, {DealAccepted,DealDisputed}, {DealDisputed,DealCompleted},
	}
	for _, pair := range valid { if !CanTransitionDeal(pair[0],pair[1]) { t.Fatalf("expected %s -> %s",pair[0],pair[1]) } }
	invalid := [][2]DealStatus{{DealCompleted,DealInProgress},{DealCancelled,DealAccepted},{DealProposed,DealCompleted},{DealDelivered,DealInProgress}}
	for _, pair := range invalid { if CanTransitionDeal(pair[0],pair[1]) { t.Fatalf("unexpected %s -> %s",pair[0],pair[1]) } }
}

func TestCannotDealWithSelf(t *testing.T) {
	uid := types.Uid(303).UserId()
	_, err := ValidateNewDeal(NewDeal{BuyerID:uid,SellerID:uid,Title:"Test deal",AmountMinor:1,Currency:"USD"})
	if err == nil { t.Fatal("expected validation error") }
}

func TestRejectsNonTinodeIDs(t *testing.T) {
	_, err := ValidateNewDeal(NewDeal{BuyerID:"alice",SellerID:"bob",Title:"Test deal",AmountMinor:1,Currency:"USD"})
	if err == nil { t.Fatal("expected invalid user ID error") }
}
