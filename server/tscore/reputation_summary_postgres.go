package tscore

import "context"

func (s *DealStore) ReputationSummaryForUser(ctx context.Context, userID string) (ReputationSummary, error) {
	userID = normalizeUserID(userID)
	if userID == "" {
		return ReputationSummary{}, ErrReviewNotAllowed
	}
	var out ReputationSummary
	out.UserID = userID
	err := s.pool.QueryRow(ctx, `
SELECT
  COALESCE((SELECT COUNT(*) FROM tscore_deal_reviews WHERE reviewee_id=$1),0)::int,
  COALESCE((SELECT AVG(rating)::float8 FROM tscore_deal_reviews WHERE reviewee_id=$1),0),
  COALESCE((SELECT COUNT(*) FROM tscore_deals WHERE status='COMPLETED' AND (buyer_id=$1 OR seller_id=$1)),0)::int,
  COALESCE((SELECT COUNT(DISTINCT d.id) FROM tscore_deals d JOIN tscore_deal_disputes x ON x.deal_id=d.id WHERE d.buyer_id=$1 OR d.seller_id=$1),0)::int
`, userID).Scan(&out.ReviewCount,&out.AverageRating,&out.CompletedDeals,&out.DisputedDeals)
	return out,err
}
