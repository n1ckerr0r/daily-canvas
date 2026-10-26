package http

import "context"

type userIDContextKey struct{}

func withUserID(ctx context.Context, userID string) context.Context {
	return context.WithValue(ctx, userIDContextKey{}, userID)
}

func userIDFromContext(ctx context.Context) (string, bool) {
	value, ok := ctx.Value(userIDContextKey{}).(string)
	return value, ok && value != ""
}
