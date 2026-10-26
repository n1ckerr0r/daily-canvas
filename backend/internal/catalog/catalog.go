package catalog

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/lib/pq"
)

type Catalog struct {
	Version  int       `json:"version"`
	Artworks []Artwork `json:"artworks"`
}

type Artwork struct {
	ID          string   `json:"id"`
	Title       string   `json:"title"`
	ArtistID    string   `json:"artistId"`
	ArtistName  string   `json:"artistName"`
	Country     string   `json:"country"`
	Collection  string   `json:"collection"`
	Year        int      `json:"year"`
	YearLabel   string   `json:"yearLabel"`
	Image       string   `json:"image"`
	Description string   `json:"description"`
	Facts       []string `json:"facts"`
	Tags        []Tag    `json:"tags"`
}

type Tag struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	Type string `json:"type"`
}

// Sync makes the ignored local catalog the only source of artwork metadata.
// Missing catalog items are deactivated rather than deleted so existing user
// history and favorites always remain referentially valid.
func Sync(ctx context.Context, db *sql.DB, catalogPath, imagesDir string) error {
	data, err := os.ReadFile(catalogPath)
	if err != nil {
		return fmt.Errorf("read artwork catalog: %w", err)
	}

	var value Catalog
	if err := json.Unmarshal(data, &value); err != nil {
		return fmt.Errorf("decode artwork catalog: %w", err)
	}
	if value.Version != 1 || len(value.Artworks) == 0 {
		return fmt.Errorf("artwork catalog must use version 1 and contain at least one artwork")
	}

	seen := make(map[string]struct{}, len(value.Artworks))
	for i, item := range value.Artworks {
		if err := validateArtwork(item, imagesDir); err != nil {
			return fmt.Errorf("validate artwork %d: %w", i+1, err)
		}
		if _, exists := seen[item.ID]; exists {
			return fmt.Errorf("duplicate artwork id %q", item.ID)
		}
		seen[item.ID] = struct{}{}
	}

	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if _, err := tx.ExecContext(ctx, `UPDATE artworks SET active = FALSE`); err != nil {
		return err
	}

	for _, item := range value.Artworks {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO artworks (
				id, title, artist_id, artist_name, country, collection,
				year_value, year_label, image_path, description, facts, published_on, active
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, CURRENT_DATE, TRUE)
			ON CONFLICT (id) DO UPDATE SET
				title = EXCLUDED.title,
				artist_id = EXCLUDED.artist_id,
				artist_name = EXCLUDED.artist_name,
				country = EXCLUDED.country,
				collection = EXCLUDED.collection,
				year_value = EXCLUDED.year_value,
				year_label = EXCLUDED.year_label,
				image_path = EXCLUDED.image_path,
				description = EXCLUDED.description,
				facts = EXCLUDED.facts,
				active = TRUE
		`, item.ID, item.Title, item.ArtistID, item.ArtistName, item.Country, item.Collection,
			item.Year, item.YearLabel, item.Image, item.Description, pq.Array(item.Facts))
		if err != nil {
			return fmt.Errorf("upsert artwork %q: %w", item.ID, err)
		}

		if _, err := tx.ExecContext(ctx, `DELETE FROM artwork_tags WHERE artwork_id = $1`, item.ID); err != nil {
			return err
		}
		for _, tag := range item.Tags {
			if _, err := tx.ExecContext(ctx, `
				INSERT INTO artwork_tags(artwork_id, tag_id, tag_name, tag_type)
				VALUES ($1, $2, $3, $4)
			`, item.ID, tag.ID, tag.Name, tag.Type); err != nil {
				return fmt.Errorf("insert tag %q for artwork %q: %w", tag.ID, item.ID, err)
			}
		}
	}

	return tx.Commit()
}

func validateArtwork(item Artwork, imagesDir string) error {
	required := map[string]string{
		"id": item.ID, "title": item.Title, "artistId": item.ArtistID,
		"artistName": item.ArtistName, "country": item.Country,
		"collection": item.Collection, "yearLabel": item.YearLabel,
		"image": item.Image, "description": item.Description,
	}
	for name, value := range required {
		if strings.TrimSpace(value) == "" {
			return fmt.Errorf("%s is required", name)
		}
	}
	if item.Collection != "russian" && item.Collection != "world" {
		return fmt.Errorf("artwork %q has unsupported collection %q", item.ID, item.Collection)
	}
	if filepath.Base(item.Image) != item.Image {
		return fmt.Errorf("artwork %q image must be a file name", item.ID)
	}
	info, err := os.Stat(filepath.Join(imagesDir, item.Image))
	if err != nil {
		return fmt.Errorf("artwork %q image: %w", item.ID, err)
	}
	if !info.Mode().IsRegular() {
		return fmt.Errorf("artwork %q image is not a regular file", item.ID)
	}
	for _, tag := range item.Tags {
		if strings.TrimSpace(tag.ID) == "" || strings.TrimSpace(tag.Name) == "" || strings.TrimSpace(tag.Type) == "" {
			return fmt.Errorf("artwork %q contains an incomplete tag", item.ID)
		}
	}
	return nil
}
