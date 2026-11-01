package tscore

import (
	"testing"

	"github.com/tinode/chat/server/store/types"
)

func dealUsers() (string,string) { return types.Uid(101).UserId(), types.Uid(202).UserId() }

func TestValidateNewDeal(t *testing.T) {
	buyer,seller := dealUsers()
	in, err := ValidateNewDeal(NewDeal{
		BuyerID: buyer, SellerID: seller, CreatedBy: buyer, Title: "Phone purchase", Terms: "New sealed phone", AmountMinor: 125000, Currency: "pkr",
	})
	if err != nil { t.Fatal(err) }
	if in.BuyerID != buyer || in.SellerID != seller || in.CreatedBy != buyer || in.Currency != "PKR" { t.Fatalf("normalized=%+v", in) }
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

func TestParticipantPermissions(t *testing.T) {
	buyer,seller := dealUsers()
	d := Deal{BuyerID:buyer,SellerID:seller,CreatedBy:buyer,Status:DealProposed}
	if CanUserTransitionDeal(d,buyer,DealAccepted) { t.Fatal("creator must not accept own proposal") }
	if !CanUserTransitionDeal(d,seller,DealAccepted) { t.Fatal("counterparty should accept proposal") }
	d.Status=DealAccepted
	if !CanUserTransitionDeal(d,seller,DealInProgress) { t.Fatal("seller should start deal") }
	if CanUserTransitionDeal(d,buyer,DealDelivered) { t.Fatal("buyer must not mark delivered") }
	d.Status=DealDelivered
	if !CanUserTransitionDeal(d,buyer,DealCompleted) { t.Fatal("buyer should complete delivered deal") }
	if CanUserTransitionDeal(d,seller,DealCompleted) { t.Fatal("seller must not complete own delivery") }
}

func TestCannotDealWithSelf(t *testing.T) {
	uid := types.Uid(303).UserId()
	_, err := ValidateNewDeal(NewDeal{BuyerID:uid,SellerID:uid,CreatedBy:uid,Title:"Test deal",Terms:"terms",AmountMinor:1,Currency:"USD"})
	if err == nil { t.Fatal("expected validation error") }
}

func TestRejectsNonTinodeIDs(t *testing.T) {
	_, err := ValidateNewDeal(NewDeal{BuyerID:"alice",SellerID:"bob",CreatedBy:"alice",Title:"Test deal",Terms:"terms",AmountMinor:1,Currency:"USD"})
	if err == nil { t.Fatal("expected invalid user ID error") }
}
