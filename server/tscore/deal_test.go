package tscore

import "testing"

func TestValidateNewDeal(t *testing.T) {
	in, err := ValidateNewDeal(NewDeal{
		BuyerUsername: " Alice ", SellerUsername: "BOB", Title: "Phone purchase", Terms: "New sealed phone", AmountMinor: 125000, Currency: "pkr",
	})
	if err != nil { t.Fatal(err) }
	if in.BuyerUsername != "alice" || in.SellerUsername != "bob" || in.Currency != "PKR" { t.Fatalf("normalized=%+v", in) }
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
	_, err := ValidateNewDeal(NewDeal{BuyerUsername:"same",SellerUsername:"same",Title:"Test deal",AmountMinor:1,Currency:"USD"})
	if err == nil { t.Fatal("expected validation error") }
}
