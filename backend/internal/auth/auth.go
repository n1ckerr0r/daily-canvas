package auth

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"golang.org/x/crypto/bcrypt"

	"github.com/n1ckerr0r/dailycanvas/backend/internal/platform/config"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/user"
)

var (
	ErrInvalidToken = errors.New("invalid or expired token")
	ErrInvalidLogin = errors.New("invalid email or password")
)

type TokenRepository interface {
	SaveRefreshToken(ctx context.Context, userID, tokenID, tokenHash string, expiresAt time.Time) error
	IsRefreshTokenActive(ctx context.Context, userID, tokenID, tokenHash string) (bool, error)
	RevokeRefreshToken(ctx context.Context, tokenID string) error
	RevokeUserRefreshTokens(ctx context.Context, userID string) error
}

type Tokens struct {
	AccessToken, RefreshToken, TokenType        string
	AccessTokenExpiresIn, RefreshTokenExpiresIn int
}
type claims struct {
	Type string `json:"typ"`
	jwt.RegisteredClaims
}
type Service struct {
	cfg    config.Auth
	tokens TokenRepository
}

func NewService(cfg config.Auth, tokens TokenRepository) Service {
	return Service{cfg: cfg, tokens: tokens}
}
func (s Service) HashPassword(password string) (string, error) {
	value, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(value), err
}
func (s Service) CheckPassword(hash, password string) error {
	if hash == "" || bcrypt.CompareHashAndPassword([]byte(hash), []byte(password)) != nil {
		return ErrInvalidLogin
	}
	return nil
}

func (s Service) Issue(ctx context.Context, value user.User) (Tokens, error) {
	now := time.Now().UTC()
	access, _, err := s.sign(value.ID, "access", now.Add(time.Duration(s.cfg.AccessTokenTTLSeconds)*time.Second))
	if err != nil {
		return Tokens{}, err
	}
	refresh, refreshID, err := s.sign(value.ID, "refresh", now.Add(time.Duration(s.cfg.RefreshTokenTTLSeconds)*time.Second))
	if err != nil {
		return Tokens{}, err
	}
	if err := s.tokens.SaveRefreshToken(ctx, value.ID, refreshID, tokenHash(refresh), now.Add(time.Duration(s.cfg.RefreshTokenTTLSeconds)*time.Second)); err != nil {
		return Tokens{}, err
	}
	return Tokens{access, refresh, s.cfg.TokenType, s.cfg.AccessTokenTTLSeconds, s.cfg.RefreshTokenTTLSeconds}, nil
}

func (s Service) Refresh(ctx context.Context, raw string, getUser func(context.Context, string) (user.User, error)) (Tokens, error) {
	c, err := s.parse(raw, "refresh")
	if err != nil {
		return Tokens{}, err
	}
	active, err := s.tokens.IsRefreshTokenActive(ctx, c.Subject, c.ID, tokenHash(raw))
	if err != nil || !active {
		return Tokens{}, ErrInvalidToken
	}
	if err := s.tokens.RevokeRefreshToken(ctx, c.ID); err != nil {
		return Tokens{}, err
	}
	value, err := getUser(ctx, c.Subject)
	if err != nil {
		return Tokens{}, err
	}
	return s.Issue(ctx, value)
}

func (s Service) ValidateAccess(raw string) (string, error) {
	c, err := s.parse(raw, "access")
	if err != nil {
		return "", err
	}
	return c.Subject, nil
}
func (s Service) Logout(ctx context.Context, userID string) error {
	return s.tokens.RevokeUserRefreshTokens(ctx, userID)
}

func (s Service) sign(userID, kind string, expiresAt time.Time) (string, string, error) {
	id := uuid.NewString()
	c := claims{Type: kind, RegisteredClaims: jwt.RegisteredClaims{Subject: userID, ID: id, IssuedAt: jwt.NewNumericDate(time.Now().UTC()), ExpiresAt: jwt.NewNumericDate(expiresAt)}}
	raw, err := jwt.NewWithClaims(jwt.SigningMethodHS256, c).SignedString([]byte(s.cfg.JWTSecret))
	if err != nil {
		return "", "", fmt.Errorf("sign jwt: %w", err)
	}
	return raw, id, nil
}

func (s Service) parse(raw, expectedType string) (claims, error) {
	var result claims
	token, err := jwt.ParseWithClaims(raw, &result, func(t *jwt.Token) (any, error) {
		if t.Method != jwt.SigningMethodHS256 {
			return nil, ErrInvalidToken
		}
		return []byte(s.cfg.JWTSecret), nil
	})
	if err != nil || !token.Valid || result.Type != expectedType || result.Subject == "" || result.ID == "" {
		return claims{}, ErrInvalidToken
	}
	return result, nil
}

func tokenHash(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}
func NormalizeEmail(value string) string { return strings.ToLower(strings.TrimSpace(value)) }
