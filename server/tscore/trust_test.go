package tscore

import "testing"

func TestEmptyTrustScore(t *testing.T) {
	got := CalculateTrustScore(VerificationState{})
	if got.Score != 0 || got.Badge != "New" {
		t.Fatalf("empty score = %d %q, want 0 New", got.Score, got.Badge)
	}
}

func TestFullTrustScore(t *testing.T) {
	got := CalculateTrustScore(VerificationState{
		ProfilePhotoVerified: true,
		EmailVerified: true,
		PhoneVerified: true,
		IdentityVerified: true,
		AddressVerified: true,
		LivenessVerified: true,
		AccountAgeEligible: true,
		SuccessfulDealsMaxed: true,
		ReviewsEligible: true,
		AccountStandingGood: true,
	})
	if got.Score != 100 || got.Badge != "Highly Trusted" {
		t.Fatalf("full score = %d %q, want 100 Highly Trusted", got.Score, got.Badge)
	}
}

func TestBadgeThresholds(t *testing.T) {
	cases := []struct {
		score int
		want string
	}{
		{0, "New"},
		{15, "Building Trust"},
		{40, "Basic Verified"},
		{65, "Trusted"},
		{85, "Highly Trusted"},
	}
	for _, tc := range cases {
		if got := badgeFor(tc.score); got != tc.want {
			t.Fatalf("badgeFor(%d) = %q, want %q", tc.score, got, tc.want)
		}
	}
}
