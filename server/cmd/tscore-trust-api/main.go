package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/tinode/chat/tscore"
	"github.com/tinode/chat/tscoreapi"
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
	dealHandler := tscoredealapi.New(dealStore, adminKey)
	mux := http.NewServeMux()
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
