package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/tinode/chat/tscore"
	"github.com/tinode/chat/tscoreapi"
	"github.com/tinode/chat/tscoreauth"
	"github.com/tinode/chat/tscoredealapi"
	"github.com/tinode/chat/tscoremoderationapi"
	"github.com/tinode/chat/tscorereputationapi"
	"github.com/tinode/chat/tscorestandingapi"
	"github.com/tinode/chat/tscoreverificationapi"
)

func main() {
	dsn := os.Getenv("TSCORE_TRUST_DATABASE_URL")
	if dsn == "" { log.Fatal("TSCORE_TRUST_DATABASE_URL is required") }
	adminKey := os.Getenv("TSCORE_TRUST_ADMIN_KEY")
	if adminKey == "" { log.Fatal("TSCORE_TRUST_ADMIN_KEY is required") }
	addr := os.Getenv("TSCORE_TRUST_ADDR")
	if addr == "" { addr = "127.0.0.1:6061" }

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil { log.Fatalf("database config: %v", err) }
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil { log.Fatalf("database ping: %v", err) }

	trustStore := tscore.NewPostgresStore(pool)
	if err := trustStore.EnsureSchema(ctx); err != nil { log.Fatalf("trust schema: %v", err) }
	dealStore := tscore.NewDealStore(pool)
	if err := dealStore.EnsureSchema(ctx); err != nil { log.Fatalf("deal schema: %v", err) }
	if err := dealStore.EnsureReputationSchema(ctx); err != nil { log.Fatalf("reputation schema: %v", err) }
	verificationStore := tscore.NewVerificationStore(pool)
	if err := verificationStore.EnsureSchema(ctx); err != nil { log.Fatalf("verification schema: %v", err) }
	moderationStore := tscore.NewModerationStore(pool)
	if err := moderationStore.EnsureSchema(ctx); err != nil { log.Fatalf("moderation schema: %v", err) }
	standingStore := tscore.NewAccountStandingStore(pool)
	if err := standingStore.EnsureSchema(ctx); err != nil { log.Fatalf("account standing schema: %v", err) }

	var evidenceStore *tscore.EvidenceStore
	if key := os.Getenv("TSCORE_VERIFICATION_EVIDENCE_KEY"); key != "" {
		dir := os.Getenv("TSCORE_VERIFICATION_EVIDENCE_DIR")
		if dir == "" { dir = "/var/lib/t-score/private-evidence" }
		evidenceStore, err = tscore.NewEvidenceStore(dir, key)
		if err != nil { log.Fatalf("verification evidence storage: %v", err) }
		log.Print("Encrypted private verification evidence storage enabled")
	} else {
		log.Print("Verification evidence uploads disabled: encryption key not configured")
	}

	trustHandler := tscoreapi.New(trustStore, adminKey)
	standingHandler := tscorestandingapi.New(standingStore, adminKey)
	var dealHandler http.Handler = tscoredealapi.New(dealStore, adminKey)
	var reputationHandler http.Handler = http.NotFoundHandler()
	var verificationHandler http.Handler = http.NotFoundHandler()
	var moderationHandler http.Handler = http.NotFoundHandler()

	if tokenKey := os.Getenv("TSCORE_TINODE_TOKEN_KEY"); tokenKey != "" {
		serial, err := strconv.Atoi(os.Getenv("TSCORE_TINODE_TOKEN_SERIAL"))
		if err != nil { log.Fatal("TSCORE_TINODE_TOKEN_SERIAL must be an integer when token auth is enabled") }
		verifier, err := tscoreauth.NewTokenVerifierFromBase64(tokenKey, serial)
		if err != nil { log.Fatalf("Tinode token verifier config: %v", err) }
		dealHandler = tscoredealapi.NewAuthenticated(dealStore, adminKey, verifier)
		reputationHandler = tscorereputationapi.New(dealStore, verifier)
		if evidenceStore != nil {
			verificationHandler = tscoreverificationapi.New(verificationStore, verifier, adminKey, evidenceStore)
		} else {
			verificationHandler = tscoreverificationapi.New(verificationStore, verifier, adminKey)
		}
		moderationHandler = tscoremoderationapi.New(moderationStore, verifier, adminKey)
		log.Print("Authenticated Deals + Reputation + Verification + Moderation API enabled")
	} else {
		log.Print("Authenticated user APIs disabled: token verifier not configured")
	}

	mux := http.NewServeMux()
	mux.Handle("/v1/deals", dealHandler)
	mux.Handle("/v1/deals/", dealHandler)
	mux.Handle("/v1/reputation/", reputationHandler)
	mux.Handle("/v1/verifications", verificationHandler)
	mux.Handle("/v1/verifications/", verificationHandler)
	mux.Handle("/v1/reports", moderationHandler)
	mux.Handle("/v1/reports/", moderationHandler)
	mux.Handle("/v1/standing/", standingHandler)
	mux.Handle("/internal/moderation", moderationHandler)
	mux.Handle("/internal/v1/reports", moderationHandler)
	mux.Handle("/internal/v1/reports/", moderationHandler)
	mux.Handle("/internal/v1/standing/", standingHandler)
	mux.Handle("/internal/v1/verifications/", verificationHandler)
	mux.Handle("/internal/v1/deals", dealHandler)
	mux.Handle("/internal/v1/deals/", dealHandler)
	mux.Handle("/internal/v1/users/", dealHandler)
	mux.Handle("/", trustHandler)

	srv := &http.Server{Addr:addr,Handler:mux,ReadHeaderTimeout:5*time.Second,ReadTimeout:15*time.Second,WriteTimeout:15*time.Second,IdleTimeout:60*time.Second}
	log.Printf("T-Score trust/deals/reputation/verification/moderation/standing API listening on %s", addr)
	log.Fatal(srv.ListenAndServe())
}
