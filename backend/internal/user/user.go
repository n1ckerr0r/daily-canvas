package user

import (
	"context"
	"errors"
	"strings"
	"time"
)

var (
	ErrInvalidCredentials = errors.New("invalid email or password")
	ErrEmailTaken         = errors.New("email is already registered")
)

type User struct {
	ID        string
	Email     string
	Name      string
	CreatedAt time.Time
}

type Repository interface {
	GetUserByID(ctx context.Context, id string) (User, error)
	Create(ctx context.Context, email, name, passwordHash string) (User, error)
	FindByEmail(ctx context.Context, email string) (User, string, error)
	Delete(ctx context.Context, id string) error
	UpdatePassword(ctx context.Context, id, passwordHash string) error
}

type Service struct {
	repo Repository
}

func NewService(repo Repository) Service {
	return Service{repo: repo}
}

func (s Service) Get(ctx context.Context, id string) (User, error) {
	return s.repo.GetUserByID(ctx, id)
}

func (s Service) Register(ctx context.Context, email, name, passwordHash string) (User, error) {
	email = strings.ToLower(strings.TrimSpace(email))
	return s.repo.Create(ctx, email, strings.TrimSpace(name), passwordHash)
}

func (s Service) Credentials(ctx context.Context, email string) (User, string, error) {
	return s.repo.FindByEmail(ctx, strings.ToLower(strings.TrimSpace(email)))
}

func (s Service) Delete(ctx context.Context, id string) error { return s.repo.Delete(ctx, id) }

func (s Service) UpdatePassword(ctx context.Context, id, passwordHash string) error {
	return s.repo.UpdatePassword(ctx, id, passwordHash)
}
