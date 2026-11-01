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

	store := tscore.NewPostgresStore(pool)
	if err := store.EnsureSchema(ctx); err != nil {
		log.Fatalf("trust schema: %v", err)
	}

	srv := &http.Server{
		Addr:              addr,
		Handler:           tscoreapi.New(store, adminKey),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	log.Printf("T-Score trust API listening on %s", addr)
	log.Fatal(srv.ListenAndServe())
}
