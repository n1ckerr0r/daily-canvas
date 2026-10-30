package http

import (
	"strings"

	openapi "github.com/n1ckerr0r/dailycanvas/backend/internal/adapters/http/openapi"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/artwork"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
)

func artworkToDetail(item artwork.Artwork) openapi.ArtworkDetail {
	return openapi.ArtworkDetail{
		Id:          item.ID,
		Title:       item.Title,
		Artist:      artistToAPI(item.Artist),
		Country:     item.Country,
		Year:        item.YearValue,
		ImageUrl:    imageURL(item.ImagePath),
		FocusPoint:  focusPointToAPI(item),
		IsFavorite:  boolPtr(item.IsFavorite),
		Tags:        tagsToAPI(item.Tags),
		Description: item.Description,
		Facts:       item.Facts,
		Collection:  collectionToAPI(item.Collection),
	}
}

func artworksToSummaries(items []artwork.Artwork) []openapi.ArtworkSummary {
	result := make([]openapi.ArtworkSummary, 0, len(items))
	for _, item := range items {
		result = append(result, openapi.ArtworkSummary{
			Id:         item.ID,
			Title:      item.Title,
			Artist:     artistToAPI(item.Artist),
			Country:    item.Country,
			Year:       item.YearValue,
			ImageUrl:   imageURL(item.ImagePath),
			FocusPoint: focusPointToAPI(item),
			IsFavorite: boolPtr(item.IsFavorite),
			Tags:       tagsToAPI(item.Tags),
		})
	}
	return result
}

func focusPointToAPI(item artwork.Artwork) openapi.ArtworkFocusPoint {
	return openapi.ArtworkFocusPoint{X: item.FocusX, Y: item.FocusY}
}

func artistToAPI(item artwork.Artist) openapi.Artist {
	return openapi.Artist{Id: item.ID, Name: item.Name}
}

func tagsToAPI(items []artwork.Tag) *[]openapi.Tag {
	result := make([]openapi.Tag, 0, len(items))
	for _, item := range items {
		result = append(result, openapi.Tag{
			Id:   item.ID,
			Name: item.Name,
			Type: openapi.TagType(item.Type),
		})
	}
	return &result
}

func derefTags(value *[]openapi.Tag) []openapi.Tag {
	if value == nil {
		return nil
	}
	return *value
}

func settingsToAPI(value settings.Settings) openapi.Settings {
	return openapi.Settings{
		SelectedCollections: stringCollectionsToAPI(value.SelectedCollections),
		Notifications: openapi.NotificationSettings{
			Enabled:  value.Notifications.Enabled,
			Time:     value.Notifications.Time,
			TimeZone: value.Notifications.TimeZone,
		},
	}
}

func collectionsToAPI(items []artwork.Collection) []openapi.Collection {
	result := make([]openapi.Collection, 0, len(items))
	for _, item := range items {
		result = append(result, collectionToAPI(item))
	}
	return result
}

func stringCollectionsToAPI(items []string) []openapi.Collection {
	result := make([]openapi.Collection, 0, len(items))
	for _, item := range items {
		result = append(result, openapi.Collection(item))
	}
	return result
}

func collectionToAPI(value artwork.Collection) openapi.Collection {
	return openapi.Collection(value)
}

func collectionFromAPI(value openapi.Collection) artwork.Collection {
	return artwork.Collection(value)
}

func matchFieldsToAPI(items []artwork.SearchMatchField) []openapi.SearchMatchField {
	result := make([]openapi.SearchMatchField, 0, len(items))
	for _, item := range items {
		result = append(result, openapi.SearchMatchField(item))
	}
	return result
}

func boolPtr(value bool) *bool {
	return &value
}

func imageURL(path string) string {
	path = strings.TrimPrefix(path, "/")
	return "/images/" + path
}
