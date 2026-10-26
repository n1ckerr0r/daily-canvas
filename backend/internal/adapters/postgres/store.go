package postgres

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/lib/pq"

	"github.com/n1ckerr0r/dailycanvas/backend/internal/artwork"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/favorite"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/user"
)

type Store struct {
	db *sql.DB
}

func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) GetUserByID(ctx context.Context, userID string) (user.User, error) {
	var value user.User
	err := s.db.QueryRowContext(ctx, `
		SELECT id, email, display_name, created_at
		FROM users
		WHERE id = $1
	`, userID).Scan(&value.ID, &value.Email, &value.Name, &value.CreatedAt)
	return value, err
}

func (s *Store) Create(ctx context.Context, email, name, passwordHash string) (user.User, error) {
	value := user.User{ID: uuid.NewString(), Email: email, Name: name}
	err := s.db.QueryRowContext(ctx, `
		INSERT INTO users(id, email, display_name, password_hash)
		VALUES ($1, $2, $3, $4)
		RETURNING created_at
	`, value.ID, value.Email, value.Name, passwordHash).Scan(&value.CreatedAt)
	if err != nil {
		return user.User{}, err
	}
	_, err = s.db.ExecContext(ctx, `
		INSERT INTO user_settings(user_id, selected_collections, notifications_enabled, notification_time, notification_time_zone)
		VALUES ($1, ARRAY['russian', 'world'], TRUE, '20:00', 'Europe/Moscow')
	`, value.ID)
	return value, err
}

func (s *Store) FindByEmail(ctx context.Context, email string) (user.User, string, error) {
	var value user.User
	var passwordHash string
	err := s.db.QueryRowContext(ctx, `
		SELECT id, email, display_name, password_hash, created_at
		FROM users WHERE email = $1
	`, email).Scan(&value.ID, &value.Email, &value.Name, &passwordHash, &value.CreatedAt)
	return value, passwordHash, err
}

func (s *Store) Delete(ctx context.Context, userID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM users WHERE id = $1`, userID)
	return err
}

func (s *Store) UpdatePassword(ctx context.Context, userID, passwordHash string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE users SET password_hash = $2 WHERE id = $1`, userID, passwordHash)
	return err
}

func (s *Store) SaveRefreshToken(ctx context.Context, userID, tokenID, tokenHash string, expiresAt time.Time) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO refresh_tokens(id, user_id, token_hash, expires_at)
		VALUES ($1, $2, $3, $4)
	`, tokenID, userID, tokenHash, expiresAt)
	return err
}

func (s *Store) IsRefreshTokenActive(ctx context.Context, userID, tokenID, tokenHash string) (bool, error) {
	var active bool
	err := s.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM refresh_tokens
			WHERE id = $1 AND user_id = $2 AND token_hash = $3 AND expires_at > NOW() AND revoked_at IS NULL
		)
	`, tokenID, userID, tokenHash).Scan(&active)
	return active, err
}

func (s *Store) RevokeRefreshToken(ctx context.Context, tokenID string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = $1`, tokenID)
	return err
}

func (s *Store) RevokeUserRefreshTokens(ctx context.Context, userID string) error {
	_, err := s.db.ExecContext(ctx, `UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = $1 AND revoked_at IS NULL`, userID)
	return err
}

func (s *Store) Get(ctx context.Context, userID string) (settings.Settings, error) {
	var rawCollections []string
	value := settings.Settings{}

	err := s.db.QueryRowContext(ctx, `
		SELECT selected_collections, notifications_enabled, notification_time, notification_time_zone
		FROM user_settings
		WHERE user_id = $1
	`, userID).Scan(pq.Array(&rawCollections), &value.Notifications.Enabled, &value.Notifications.Time, &value.Notifications.TimeZone)
	if err != nil {
		return value, err
	}

	value.SelectedCollections = rawCollections
	return value, nil
}

func (s *Store) Save(ctx context.Context, userID string, value settings.Settings) (settings.Settings, error) {
	_, err := s.db.ExecContext(ctx, `
		UPDATE user_settings
		SET selected_collections = $2,
			notifications_enabled = $3,
			notification_time = $4,
			notification_time_zone = $5
		WHERE user_id = $1
	`, userID, pq.Array(value.SelectedCollections), value.Notifications.Enabled, value.Notifications.Time, value.Notifications.TimeZone)
	if err != nil {
		return settings.Settings{}, err
	}
	return s.Get(ctx, userID)
}

func (s *Store) GetDaily(ctx context.Context, userID string, collections []artwork.Collection, day time.Time) (artwork.DailyArtwork, error) {
	day = dateOnly(day)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return artwork.DailyArtwork{}, err
	}
	defer tx.Rollback()

	// Serialize assignment creation per user so simultaneous devices always see
	// one stable artwork and cannot consume two entries from the sequence.
	if _, err := tx.ExecContext(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, userID); err != nil {
		return artwork.DailyArtwork{}, err
	}

	var artworkID string
	var assignedOn time.Time
	err = tx.QueryRowContext(ctx, `
		SELECT artwork_id, assigned_on
		FROM daily_artwork_assignments
		WHERE user_id = $1 AND assigned_on = $2
	`, userID, day).Scan(&artworkID, &assignedOn)

	exhausted := false
	if errors.Is(err, sql.ErrNoRows) {
		err = tx.QueryRowContext(ctx, `
			SELECT a.id
			FROM artworks a
			WHERE a.active = TRUE
			  AND a.collection = ANY($2)
			  AND NOT EXISTS (
				SELECT 1
				FROM daily_artwork_assignments history
				WHERE history.user_id = $1 AND history.artwork_id = a.id
			  )
			ORDER BY md5($1 || ':' || a.id), a.id
			LIMIT 1
		`, userID, pq.Array(collectionStrings(collections))).Scan(&artworkID)
		if err == nil {
			assignedOn = day
			_, err = tx.ExecContext(ctx, `
				INSERT INTO daily_artwork_assignments(user_id, assigned_on, artwork_id)
				VALUES ($1, $2, $3)
			`, userID, day, artworkID)
		} else if errors.Is(err, sql.ErrNoRows) {
			exhausted = true
			err = tx.QueryRowContext(ctx, `
				SELECT history.artwork_id, history.assigned_on
				FROM daily_artwork_assignments history
				JOIN artworks a ON a.id = history.artwork_id
				WHERE history.user_id = $1 AND a.collection = ANY($2)
				ORDER BY history.assigned_on DESC
				LIMIT 1
			`, userID, pq.Array(collectionStrings(collections))).Scan(&artworkID, &assignedOn)
		}
	}
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return artwork.DailyArtwork{}, artwork.ErrNotFound
		}
		return artwork.DailyArtwork{}, err
	}
	if err := tx.Commit(); err != nil {
		return artwork.DailyArtwork{}, err
	}

	item, err := s.GetByID(ctx, userID, artworkID)
	if err != nil {
		return artwork.DailyArtwork{}, err
	}
	return artwork.DailyArtwork{Artwork: item, Date: dateOnly(assignedOn), Exhausted: exhausted}, nil
}

func (s *Store) ListGallery(ctx context.Context, userID string, filter artwork.Filter, collections []artwork.Collection) ([]artwork.Artwork, error) {
	args := []any{userID}
	where := []string{"a.active = TRUE", "a.collection = ANY($2)"}
	args = append(args, pq.Array(collectionStrings(collections)))

	if filter.Collection != nil {
		args = append(args, string(*filter.Collection))
		where = append(where, fmt.Sprintf("a.collection = $%d", len(args)))
	}
	if filter.TagID != "" {
		args = append(args, filter.TagID)
		where = append(where, fmt.Sprintf("EXISTS (SELECT 1 FROM artwork_tags tag WHERE tag.artwork_id = a.id AND tag.tag_id = $%d)", len(args)))
	}
	if strings.TrimSpace(filter.Query) != "" {
		args = append(args, "%"+strings.TrimSpace(filter.Query)+"%")
		where = append(where, fmt.Sprintf(`(
			a.title ILIKE $%[1]d OR
			a.artist_name ILIKE $%[1]d OR
			a.description ILIKE $%[1]d OR
			EXISTS (SELECT 1 FROM artwork_tags tag WHERE tag.artwork_id = a.id AND tag.tag_name ILIKE $%[1]d)
		)`, len(args)))
	}

	args = append(args, filter.Limit, (filter.Page-1)*filter.Limit)
	query := baseArtworkQuery(`
		WHERE ` + strings.Join(where, " AND ") + `
		ORDER BY a.published_on DESC, a.title ASC
		LIMIT $` + fmt.Sprintf("%d", len(args)-1) + ` OFFSET $` + fmt.Sprintf("%d", len(args)))

	return s.listArtworks(ctx, userID, query, args...)
}

func (s *Store) GetByID(ctx context.Context, userID string, artworkID string) (artwork.Artwork, error) {
	items, err := s.listArtworks(ctx, userID, baseArtworkQuery(`
		WHERE a.id = $2
		LIMIT 1
	`), userID, artworkID)
	if err != nil {
		return artwork.Artwork{}, err
	}
	if len(items) == 0 {
		return artwork.Artwork{}, artwork.ErrNotFound
	}
	return items[0], nil
}

func (s *Store) GetCalendar(ctx context.Context, userID string, from time.Time, to time.Time) ([]artwork.CalendarItem, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT history.assigned_on, a.id, a.image_path
		FROM daily_artwork_assignments history
		JOIN artworks a ON a.id = history.artwork_id
		WHERE history.user_id = $1 AND history.assigned_on BETWEEN $2 AND $3
		ORDER BY history.assigned_on ASC
	`, userID, dateOnly(from), dateOnly(to))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]artwork.CalendarItem, 0)
	for rows.Next() {
		var value artwork.CalendarItem
		if err := rows.Scan(&value.Date, &value.ArtworkID, &value.ImagePath); err != nil {
			return nil, err
		}
		items = append(items, value)
	}
	return items, rows.Err()
}

func (s *Store) ListTags(ctx context.Context) ([]artwork.Tag, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT DISTINCT tags.tag_id, tags.tag_name, tags.tag_type
		FROM artwork_tags tags
		JOIN artworks a ON a.id = tags.artwork_id
		WHERE a.active = TRUE
		ORDER BY tag_name ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]artwork.Tag, 0)
	for rows.Next() {
		var item artwork.Tag
		if err := rows.Scan(&item.ID, &item.Name, &item.Type); err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	return items, rows.Err()
}

func (s *Store) HasViewedOn(ctx context.Context, userID string, artworkID string, day time.Time) (bool, error) {
	var exists bool
	err := s.db.QueryRowContext(ctx, `
		SELECT EXISTS(
			SELECT 1
			FROM artwork_views
			WHERE user_id = $1 AND artwork_id = $2 AND viewed_on = $3
		)
	`, userID, artworkID, dateOnly(day)).Scan(&exists)
	return exists, err
}

func (s *Store) MarkViewedOn(ctx context.Context, userID string, artworkID string, day time.Time) error {
	result, err := s.db.ExecContext(ctx, `
		INSERT INTO artwork_views(user_id, artwork_id, viewed_on)
		SELECT $1, $2, $3
		WHERE EXISTS (
			SELECT 1 FROM daily_artwork_assignments
			WHERE user_id = $1 AND artwork_id = $2 AND assigned_on = $3
		)
		ON CONFLICT (user_id, artwork_id, viewed_on) DO NOTHING
	`, userID, artworkID, dateOnly(day))
	if err != nil {
		return err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if changed == 0 {
		var assigned bool
		err := s.db.QueryRowContext(ctx, `
			SELECT EXISTS(
				SELECT 1 FROM daily_artwork_assignments
				WHERE user_id = $1 AND artwork_id = $2 AND assigned_on = $3
			)
		`, userID, artworkID, dateOnly(day)).Scan(&assigned)
		if err != nil {
			return err
		}
		if !assigned {
			return artwork.ErrNotAssigned
		}
	}
	return nil
}

func (s *Store) Add(ctx context.Context, userID string, artworkID string) error {
	_, err := s.db.ExecContext(ctx, `
		INSERT INTO favorites(user_id, artwork_id, added_at)
		VALUES ($1, $2, NOW())
		ON CONFLICT (user_id, artwork_id) DO UPDATE SET added_at = EXCLUDED.added_at
	`, userID, artworkID)
	return err
}

func (s *Store) Remove(ctx context.Context, userID string, artworkID string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM favorites WHERE user_id = $1 AND artwork_id = $2`, userID, artworkID)
	return err
}

func (s *Store) List(ctx context.Context, userID string, sort favorite.Sort) ([]favorite.Item, error) {
	orderBy := "f.added_at DESC"
	switch sort {
	case favorite.SortArtist:
		orderBy = "a.artist_name ASC, f.added_at DESC"
	case favorite.SortTitle:
		orderBy = "a.title ASC, f.added_at DESC"
	case favorite.SortYear:
		orderBy = "a.year_value ASC, f.added_at DESC"
	}

	query := `
		SELECT
			a.id, a.title, a.artist_id, a.artist_name, a.country, a.collection,
			a.year_value, a.year_label, a.image_path, a.description, a.facts, a.published_on,
			TRUE AS is_favorite, f.added_at
		FROM favorites f
		JOIN artworks a ON a.id = f.artwork_id
		WHERE f.user_id = $1 AND a.active = TRUE
		ORDER BY ` + orderBy

	rows, err := s.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]favorite.Item, 0)
	ids := make([]string, 0)
	for rows.Next() {
		var value favorite.Item
		var rawFacts []string
		if err := rows.Scan(
			&value.Artwork.ID,
			&value.Artwork.Title,
			&value.Artwork.Artist.ID,
			&value.Artwork.Artist.Name,
			&value.Artwork.Country,
			&value.Artwork.Collection,
			&value.Artwork.YearValue,
			&value.Artwork.YearLabel,
			&value.Artwork.ImagePath,
			&value.Artwork.Description,
			pq.Array(&rawFacts),
			&value.Artwork.PublishedOn,
			&value.Artwork.IsFavorite,
			&value.AddedAt,
		); err != nil {
			return nil, err
		}
		value.Artwork.Facts = rawFacts
		items = append(items, value)
		ids = append(ids, value.Artwork.ID)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	tagsByArtwork, err := s.tagsByArtworkIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	for i := range items {
		items[i].Artwork.Tags = tagsByArtwork[items[i].Artwork.ID]
	}
	return items, nil
}

func (s *Store) listArtworks(ctx context.Context, userID string, query string, args ...any) ([]artwork.Artwork, error) {
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]artwork.Artwork, 0)
	ids := make([]string, 0)
	for rows.Next() {
		var item artwork.Artwork
		var rawFacts []string
		if err := rows.Scan(
			&item.ID,
			&item.Title,
			&item.Artist.ID,
			&item.Artist.Name,
			&item.Country,
			&item.Collection,
			&item.YearValue,
			&item.YearLabel,
			&item.ImagePath,
			&item.Description,
			pq.Array(&rawFacts),
			&item.PublishedOn,
			&item.IsFavorite,
		); err != nil {
			return nil, err
		}
		item.Facts = rawFacts
		items = append(items, item)
		ids = append(ids, item.ID)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	tagsByArtwork, err := s.tagsByArtworkIDs(ctx, ids)
	if err != nil {
		return nil, err
	}
	for i := range items {
		items[i].Tags = tagsByArtwork[items[i].ID]
	}

	return items, nil
}

func (s *Store) tagsByArtworkIDs(ctx context.Context, ids []string) (map[string][]artwork.Tag, error) {
	result := make(map[string][]artwork.Tag, len(ids))
	if len(ids) == 0 {
		return result, nil
	}

	rows, err := s.db.QueryContext(ctx, `
		SELECT artwork_id, tag_id, tag_name, tag_type
		FROM artwork_tags
		WHERE artwork_id = ANY($1)
		ORDER BY tag_name ASC
	`, pq.Array(ids))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var artworkID string
		var tag artwork.Tag
		if err := rows.Scan(&artworkID, &tag.ID, &tag.Name, &tag.Type); err != nil {
			return nil, err
		}
		result[artworkID] = append(result[artworkID], tag)
	}
	return result, rows.Err()
}

func baseArtworkQuery(suffix string) string {
	return `
		SELECT
			a.id, a.title, a.artist_id, a.artist_name, a.country, a.collection,
			a.year_value, a.year_label, a.image_path, a.description, a.facts, a.published_on,
			COALESCE(f.artwork_id IS NOT NULL, FALSE) AS is_favorite
		FROM artworks a
		LEFT JOIN favorites f
			ON f.artwork_id = a.id AND f.user_id = $1
	` + suffix
}

func collectionStrings(items []artwork.Collection) []string {
	result := make([]string, 0, len(items))
	for _, item := range items {
		result = append(result, string(item))
	}
	return result
}

func dateOnly(value time.Time) time.Time {
	return time.Date(value.Year(), value.Month(), value.Day(), 0, 0, 0, 0, time.UTC)
}
