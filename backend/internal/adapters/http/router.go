package http

import (
	"net/http"
	"os"
	"path/filepath"
	"strings"

	"github.com/go-chi/chi/v5"

	openapi "github.com/n1ckerr0r/dailycanvas/backend/internal/adapters/http/openapi"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/artwork"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/auth"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/favorite"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/platform/config"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/user"
)

type Dependencies struct {
	ArtworkService  artwork.Service
	AuthService     auth.Service
	FavoriteService favorite.Service
	SettingsService settings.Service
	UserService     user.Service
}

func NewRouter(cfg config.Config, deps Dependencies) http.Handler {
	router := chi.NewRouter()
	router.Use(cors)
	router.Use(authentication(deps.AuthService))

	handler := Handler{
		artworks:  deps.ArtworkService,
		auth:      deps.AuthService,
		favorites: deps.FavoriteService,
		settings:  deps.SettingsService,
		users:     deps.UserService,
	}

	openapi.HandlerFromMuxWithBaseURL(handler, router, "/api/v1")
	router.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
	})

	images := http.StripPrefix("/images/", http.FileServer(http.Dir(cfg.ImagesDir)))
	router.Handle("/images/*", images)
	if strings.TrimSpace(cfg.WebDir) == "" {
		router.NotFound(func(w http.ResponseWriter, r *http.Request) {
			http.NotFound(w, r)
		})
	} else {
		router.NotFound(staticFallback(cfg.WebDir))
	}

	return router
}

func authentication(service auth.Service) func(http.Handler) http.Handler {
	public := map[string]bool{
		"/api/v1/auth/login":         true,
		"/api/v1/auth/register":      true,
		"/api/v1/auth/token/refresh": true,
	}
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Method == http.MethodOptions || !strings.HasPrefix(r.URL.Path, "/api/") || public[r.URL.Path] {
				next.ServeHTTP(w, r)
				return
			}
			header := strings.TrimSpace(r.Header.Get("Authorization"))
			parts := strings.Fields(header)
			if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
				writeError(w, http.StatusUnauthorized, "unauthorized", "Bearer token is required")
				return
			}
			userID, err := service.ValidateAccess(parts[1])
			if err != nil {
				writeError(w, http.StatusUnauthorized, "unauthorized", "Access token is invalid or expired")
				return
			}
			next.ServeHTTP(w, r.WithContext(withUserID(r.Context(), userID)))
		})
	}
}

func staticFallback(webDir string) http.HandlerFunc {
	files := http.FileServer(http.Dir(webDir))
	return func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/api/") {
			http.NotFound(w, r)
			return
		}

		target := filepath.Join(webDir, filepath.Clean(r.URL.Path))
		if _, err := os.Stat(target); err != nil {
			r.URL.Path = "/"
		}
		files.ServeHTTP(w, r)
	}
}

func cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}
