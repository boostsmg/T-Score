package tscore

// VerificationState contains only trust inputs. Sensitive document values
// (CNIC/National ID numbers, addresses, images) must be stored separately.
type VerificationState struct {
	ProfilePhotoVerified bool
	EmailVerified        bool
	PhoneVerified        bool
	IdentityVerified     bool
	AddressVerified      bool
	LivenessVerified     bool
	AccountAgeEligible   bool
	SuccessfulDealsMaxed bool
	ReviewsEligible      bool
	AccountStandingGood  bool
}

type ScoreBreakdown struct {
	ProfilePhoto    int `json:"profile_photo"`
	Email           int `json:"email"`
	Phone           int `json:"phone"`
	Identity        int `json:"identity"`
	Address         int `json:"address"`
	Liveness        int `json:"liveness"`
	AccountAge      int `json:"account_age"`
	SuccessfulDeals int `json:"successful_deals"`
	Reviews         int `json:"reviews"`
	AccountStanding int `json:"account_standing"`
}

type TrustResult struct {
	Score     int            `json:"score"`
	Badge     string         `json:"badge"`
	Breakdown ScoreBreakdown `json:"breakdown"`
}

const (
	WeightProfilePhoto    = 5
	WeightEmail           = 5
	WeightPhone           = 10
	WeightIdentity        = 20
	WeightAddress         = 15
	WeightLiveness        = 10
	WeightAccountAge      = 5
	WeightSuccessfulDeals = 15
	WeightReviews         = 10
	WeightAccountStanding = 5
)

// CalculateTrustScore is deterministic and server-owned. Mobile clients may
// display this result but must never be authoritative for score calculation.
func CalculateTrustScore(v VerificationState) TrustResult {
	b := ScoreBreakdown{}
	if v.ProfilePhotoVerified { b.ProfilePhoto = WeightProfilePhoto }
	if v.EmailVerified { b.Email = WeightEmail }
	if v.PhoneVerified { b.Phone = WeightPhone }
	if v.IdentityVerified { b.Identity = WeightIdentity }
	if v.AddressVerified { b.Address = WeightAddress }
	if v.LivenessVerified { b.Liveness = WeightLiveness }
	if v.AccountAgeEligible { b.AccountAge = WeightAccountAge }
	if v.SuccessfulDealsMaxed { b.SuccessfulDeals = WeightSuccessfulDeals }
	if v.ReviewsEligible { b.Reviews = WeightReviews }
	if v.AccountStandingGood { b.AccountStanding = WeightAccountStanding }

	score := b.ProfilePhoto + b.Email + b.Phone + b.Identity + b.Address +
		b.Liveness + b.AccountAge + b.SuccessfulDeals + b.Reviews + b.AccountStanding

	return TrustResult{Score: score, Badge: badgeFor(score), Breakdown: b}
}

func badgeFor(score int) string {
	switch {
	case score >= 85:
		return "Highly Trusted"
	case score >= 65:
		return "Trusted"
	case score >= 40:
		return "Basic Verified"
	case score >= 15:
		return "Building Trust"
	default:
		return "New"
	}
}
