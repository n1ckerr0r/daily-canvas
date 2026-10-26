package config

import (
	"fmt"
	"os"
)

type Config struct {
	AppPort       string
	WebDir        string
	ImagesDir     string
	CatalogPath   string
	MigrationsDir string
	Public        Public
	Auth          Auth
	Database      Database
}

type Database struct {
	Host     string
	Port     string
	User     string
	Password string
	Name     string
	SSLMode  string
}

type Public struct {
	BaseURL string
}

type Auth struct {
	JWTSecret              string
	TokenType              string
	AccessTokenTTLSeconds  int
	RefreshTokenTTLSeconds int
}

func Load() Config {
	return Config{
		AppPort:       env("APP_PORT", "37117"),
		WebDir:        env("WEB_DIR", "../frontend"),
		ImagesDir:     env("IMAGES_DIR", "../local-data/artworks/images"),
		CatalogPath:   env("ARTWORK_CATALOG_PATH", "../local-data/artworks/catalog.json"),
		MigrationsDir: env("MIGRATIONS_DIR", "./migrations"),
		Public: Public{
			BaseURL: env("PUBLIC_BASE_URL", "http://localhost:37117"),
		},
		Auth: Auth{
			JWTSecret:              env("AUTH_JWT_SECRET", "change-this-local-development-secret-before-production"),
			TokenType:              env("AUTH_TOKEN_TYPE", "Bearer"),
			AccessTokenTTLSeconds:  envInt("AUTH_ACCESS_TOKEN_TTL_SECONDS", 3600),
			RefreshTokenTTLSeconds: envInt("AUTH_REFRESH_TOKEN_TTL_SECONDS", 2592000),
		},
		Database: Database{
			Host:     env("POSTGRES_HOST", "127.0.0.1"),
			Port:     env("POSTGRES_PORT", "37432"),
			User:     env("POSTGRES_USER", "dailycanvas"),
			Password: env("POSTGRES_PASSWORD", "dailycanvas"),
			Name:     env("POSTGRES_DB", "dailycanvas"),
			SSLMode:  env("POSTGRES_SSLMODE", "disable"),
		},
	}
}

func env(key string, fallback string) string {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}
	return value
}

func envInt(key string, fallback int) int {
	value := os.Getenv(key)
	if value == "" {
		return fallback
	}

	var parsed int
	_, err := fmt.Sscanf(value, "%d", &parsed)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}
