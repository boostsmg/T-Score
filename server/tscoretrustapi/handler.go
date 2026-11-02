package tscoretrustapi

import (
    "context"
    "encoding/json"
    "net/http"
    "strings"

    "github.com/tinode/chat/tscore"
)

type VerificationSource interface {
    Summary(context.Context, string) (tscore.VerificationSummary, error)
}

type ReputationSource interface {
    ReputationSummaryForUser(context.Context, string) (tscore.ReputationSummary, error)
}

type StandingSource interface {
    Get(context.Context, string) (tscore.AccountStandingRecord, error)
}

type Handler struct {
    verifications VerificationSource
    reputation    ReputationSource
    standing      StandingSource
}

type Breakdown struct {
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

type Trust struct {
    Score     int       `json:"score"`
    Badge     string    `json:"badge"`
    Breakdown Breakdown `json:"breakdown"`
}

type PublicSnapshot struct {
    UserID   string                `json:"user_id"`
    Trust    Trust                 `json:"trust"`
    Verified map[string]bool       `json:"verified"`
    Activity tscore.ReputationSummary `json:"activity"`
    Standing string                `json:"standing"`
}

func New(v VerificationSource, r ReputationSource, s StandingSource) http.Handler {
    h := &Handler{verifications: v, reputation: r, standing: s}
    mux := http.NewServeMux()
    mux.HandleFunc("GET /v1/trust/users/{id}", h.get)
    return mux
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
    userID := strings.TrimSpace(r.PathValue("id"))
    if !strings.HasPrefix(userID, "usr") {
        writeJSON(w, http.StatusBadRequest, map[string]string{"error":"invalid user id"})
        return
    }
    verified, err := h.verifications.Summary(r.Context(), userID)
    if err != nil {
        writeJSON(w, http.StatusInternalServerError, map[string]string{"error":"verification summary unavailable"})
        return
    }
    activity, err := h.reputation.ReputationSummaryForUser(r.Context(), userID)
    if err != nil {
        writeJSON(w, http.StatusInternalServerError, map[string]string{"error":"reputation summary unavailable"})
        return
    }
    standing, err := h.standing.Get(r.Context(), userID)
    if err != nil {
        writeJSON(w, http.StatusInternalServerError, map[string]string{"error":"standing unavailable"})
        return
    }

    b := Breakdown{}
    if verified.ProfilePhoto { b.ProfilePhoto = tscore.WeightProfilePhoto }
    if verified.Email { b.Email = tscore.WeightEmail }
    if verified.Phone { b.Phone = tscore.WeightPhone }
    if verified.Identity { b.Identity = tscore.WeightIdentity }
    if verified.Address { b.Address = tscore.WeightAddress }
    if verified.Liveness { b.Liveness = tscore.WeightLiveness }

    // Deal/review points grow gradually and are capped at the original trust weights.
    b.SuccessfulDeals = activity.CompletedDeals * 3
    if b.SuccessfulDeals > tscore.WeightSuccessfulDeals { b.SuccessfulDeals = tscore.WeightSuccessfulDeals }
    b.Reviews = activity.ReviewCount * 2
    if b.Reviews > tscore.WeightReviews { b.Reviews = tscore.WeightReviews }
    if tscore.StandingIsGood(standing.Standing) { b.AccountStanding = tscore.WeightAccountStanding }

    score := b.ProfilePhoto+b.Email+b.Phone+b.Identity+b.Address+b.Liveness+b.AccountAge+b.SuccessfulDeals+b.Reviews+b.AccountStanding
    badge := "New"
    switch {
    case score >= 85: badge = "Highly Trusted"
    case score >= 65: badge = "Trusted"
    case score >= 40: badge = "Basic Verified"
    case score >= 15: badge = "Building Trust"
    }

    writeJSON(w, http.StatusOK, PublicSnapshot{
        UserID:userID,
        Trust:Trust{Score:score,Badge:badge,Breakdown:b},
        Verified:map[string]bool{
            "profile_photo":verified.ProfilePhoto,
            "email":verified.Email,
            "phone":verified.Phone,
            "identity":verified.Identity,
            "address":verified.Address,
            "liveness":verified.Liveness,
        },
        Activity:activity,
        Standing:string(standing.Standing),
    })
}

func writeJSON(w http.ResponseWriter, status int, v any) {
    w.Header().Set("Content-Type", "application/json; charset=utf-8")
    w.Header().Set("Cache-Control", "no-store")
    w.WriteHeader(status)
    _ = json.NewEncoder(w).Encode(v)
}
