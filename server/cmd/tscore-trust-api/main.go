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
)

func main() {
	dsn := os.Getenv("TSCORE_TRUST_DATABASE_URL")
	if dsn == "" {
		log.Fatal("TSCORE_TRUST_DATABASE_URL is required")
	}
	adminKey := os.Getenv("TSCORE_TRUST_ADMIN_KEY")
	if adminKey == "" {
		log.Fatal("TSCORE_TRUST_ADMIN_KEY is required")
	}
	addr := os.Getenv("TSCORE_TRUST_ADDR")
	if addr == "" {
		addr = "127.0.0.1:6061"
	}

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	pool, err := pgxpool.New(ctx, dsn)
	if err != nil {
		log.Fatalf("database config: %v", err)
	}
	defer pool.Close()
	if err := pool.Ping(ctx); err != nil {
		log.Fatalf("database ping: %v", err)
	}

	trustStore := tscore.NewPostgresStore(pool)
	if err := trustStore.EnsureSchema(ctx); err != nil {
		log.Fatalf("trust schema: %v", err)
	}
	dealStore := tscore.NewDealStore(pool)
	if err := dealStore.EnsureSchema(ctx); err != nil {
		log.Fatalf("deal schema: %v", err)
	}

	trustHandler := tscoreapi.New(trustStore, adminKey)
	var dealHandler http.Handler = tscoredealapi.New(dealStore, adminKey)

	// User-facing Deals API is enabled only when the Tinode token signing config is
	// provided. This avoids exposing unauthenticated write endpoints if deployment
	// has not yet wired the messaging server's token verifier configuration.
	if tokenKey := os.Getenv("TSCORE_TINODE_TOKEN_KEY"); tokenKey != "" {
		serial, err := strconv.Atoi(os.Getenv("TSCORE_TINODE_TOKEN_SERIAL"))
		if err != nil {
			log.Fatal("TSCORE_TINODE_TOKEN_SERIAL must be an integer when token auth is enabled")
		}
		verifier, err := tscoreauth.NewTokenVerifierFromBase64(tokenKey, serial)
		if err != nil {
			log.Fatalf("Tinode token verifier config: %v", err)
		}
		dealHandler = tscoredealapi.NewAuthenticated(dealStore, adminKey, verifier)
		log.Print("Authenticated Deals API enabled")
	} else {
		log.Print("Authenticated Deals API disabled: token verifier not configured")
	}

	mux := http.NewServeMux()
	mux.Handle("/v1/deals", dealHandler)
	mux.Handle("/v1/deals/", dealHandler)
	mux.Handle("/internal/v1/deals", dealHandler)
	mux.Handle("/internal/v1/deals/", dealHandler)
	mux.Handle("/internal/v1/users/", dealHandler)
	mux.Handle("/", trustHandler)

	srv := &http.Server{
		Addr:              addr,
		Handler:           mux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	log.Printf("T-Score trust/deals API listening on %s", addr)
	log.Fatal(srv.ListenAndServe())
}
