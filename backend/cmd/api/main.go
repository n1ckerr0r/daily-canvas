package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"os/signal"
	"syscall"
	"time"

	httpadapter "github.com/n1ckerr0r/dailycanvas/backend/internal/adapters/http"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/adapters/postgres"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/artwork"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/auth"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/catalog"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/favorite"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/platform/config"
	pgplatform "github.com/n1ckerr0r/dailycanvas/backend/internal/platform/postgres"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/user"
)

func main() {
	cfg := config.Load()

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	db, err := pgplatform.Open(ctx, cfg.Database)
	if err != nil {
		log.Fatalf("open postgres: %v", err)
	}
	defer db.Close()

	if err := pgplatform.Migrate(ctx, db, cfg.MigrationsDir); err != nil {
		log.Fatalf("migrate postgres: %v", err)
	}
	if err := catalog.Sync(ctx, db, cfg.CatalogPath, cfg.ImagesDir); err != nil {
		log.Fatalf("sync artwork catalog: %v", err)
	}

	router := buildRouter(cfg, db)
	server := &http.Server{
		Addr:              ":" + cfg.AppPort,
		Handler:           router,
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdownCtx)
	}()

	log.Printf("dailycanvas backend listening on http://localhost:%s", cfg.AppPort)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("serve http: %v", err)
	}
}

func buildRouter(cfg config.Config, db *sql.DB) http.Handler {
	store := postgres.NewStore(db)
	userService := user.NewService(store)
	settingsService := settings.NewService(store)
	artworkService := artwork.NewService(store, settingsService)
	favoriteService := favorite.NewService(store)
	authService := auth.NewService(cfg.Auth, store)

	return httpadapter.NewRouter(
		cfg,
		httpadapter.Dependencies{
			ArtworkService:  artworkService,
			AuthService:     authService,
			FavoriteService: favoriteService,
			SettingsService: settingsService,
			UserService:     userService,
		},
	)
}
