package artwork

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
)

var (
	ErrNotFound    = errors.New("artwork not found")
	ErrNotAssigned = errors.New("artwork is not assigned to the user for this date")
)

type Collection string

const (
	CollectionRussian Collection = "russian"
	CollectionWorld   Collection = "world"
)

type TagType string

const (
	TagTypeGenre      TagType = "genre"
	TagTypeStyle      TagType = "style"
	TagTypePeriod     TagType = "period"
	TagTypeCollection TagType = "collection"
)

type SearchMatchField string

const (
	SearchMatchTitle       SearchMatchField = "title"
	SearchMatchArtist      SearchMatchField = "artist"
	SearchMatchDescription SearchMatchField = "description"
	SearchMatchTags        SearchMatchField = "tags"
)

type Artist struct {
	ID   string
	Name string
}

type Tag struct {
	ID   string
	Name string
	Type TagType
}

type Artwork struct {
	ID          string
	Title       string
	Artist      Artist
	Country     string
	Collection  Collection
	YearValue   int
	YearLabel   string
	ImagePath   string
	Description string
	Facts       []string
	Tags        []Tag
	IsFavorite  bool
	PublishedOn time.Time
}

type Filter struct {
	Page       int
	Limit      int
	Collection *Collection
	TagID      string
	Query      string
}

type SearchItem struct {
	Artwork   Artwork
	MatchedBy []SearchMatchField
}

type CalendarItem struct {
	Date      time.Time
	ArtworkID string
	ImagePath string
}

type DailyArtwork struct {
	Artwork   Artwork
	Date      time.Time
	Exhausted bool
}

type Repository interface {
	GetDaily(ctx context.Context, userID string, collections []Collection, day time.Time) (DailyArtwork, error)
	ListGallery(ctx context.Context, userID string, filter Filter, collections []Collection) ([]Artwork, error)
	GetByID(ctx context.Context, userID string, artworkID string) (Artwork, error)
	GetCalendar(ctx context.Context, userID string, from time.Time, to time.Time) ([]CalendarItem, error)
	ListTags(ctx context.Context) ([]Tag, error)
	HasViewedOn(ctx context.Context, userID string, artworkID string, day time.Time) (bool, error)
	MarkViewedOn(ctx context.Context, userID string, artworkID string, day time.Time) error
}

type SettingsReader interface {
	Get(ctx context.Context, userID string) (settings.Settings, error)
}

type Service struct {
	repo         Repository
	settingsRepo SettingsReader
}

func NewService(repo Repository, settingsRepo SettingsReader) Service {
	return Service{repo: repo, settingsRepo: settingsRepo}
}

func (s Service) Main(ctx context.Context, userID string) (DailyArtwork, settings.Settings, bool, error) {
	cfg, err := s.settingsRepo.Get(ctx, userID)
	if err != nil {
		return DailyArtwork{}, settings.Settings{}, false, err
	}

	day := localDay(time.Now(), cfg.Notifications.TimeZone)
	daily, err := s.repo.GetDaily(ctx, userID, stringsToCollections(cfg.SelectedCollections), day)
	if err != nil {
		return DailyArtwork{}, settings.Settings{}, false, err
	}

	viewed, err := s.repo.HasViewedOn(ctx, userID, daily.Artwork.ID, daily.Date)
	if err != nil {
		return DailyArtwork{}, settings.Settings{}, false, err
	}

	return daily, cfg, viewed, nil
}

func (s Service) Gallery(ctx context.Context, userID string, filter Filter) ([]Artwork, error) {
	cfg, err := s.settingsRepo.Get(ctx, userID)
	if err != nil {
		return nil, err
	}
	return s.repo.ListGallery(ctx, userID, normalizeFilter(filter), stringsToCollections(cfg.SelectedCollections))
}

func stringsToCollections(items []string) []Collection {
	result := make([]Collection, 0, len(items))
	for _, item := range items {
		result = append(result, Collection(item))
	}
	return result
}

func (s Service) Search(ctx context.Context, userID string, query string, page int, limit int) ([]SearchItem, error) {
	items, err := s.Gallery(ctx, userID, Filter{Page: page, Limit: limit, Query: query})
	if err != nil {
		return nil, err
	}

	searchItems := make([]SearchItem, 0, len(items))
	for _, item := range items {
		searchItems = append(searchItems, SearchItem{
			Artwork:   item,
			MatchedBy: matchedFields(item, query),
		})
	}

	return searchItems, nil
}

func (s Service) GetByID(ctx context.Context, userID string, artworkID string) (Artwork, error) {
	return s.repo.GetByID(ctx, userID, artworkID)
}

func (s Service) Calendar(ctx context.Context, userID string, from time.Time, to time.Time) ([]CalendarItem, error) {
	return s.repo.GetCalendar(ctx, userID, from, to)
}

func (s Service) Tags(ctx context.Context) ([]Tag, error) {
	return s.repo.ListTags(ctx)
}

func (s Service) TodayStatus(ctx context.Context, userID string) (bool, bool, string, error) {
	daily, cfg, viewed, err := s.Main(ctx, userID)
	if err != nil {
		return false, false, "", err
	}

	message := "Ваша картина дня уже ждет вас!"
	if viewed {
		message = "Картина дня уже просмотрена, но вы можете вернуться к ней в галерее."
	}
	if daily.Exhausted {
		message = "Вы уже открыли всю выбранную коллекцию. Новая картина появится после пополнения каталога."
	} else if daily.Artwork.Title != "" && !viewed {
		message = "Сегодняшняя картина дня: " + daily.Artwork.Title
	}

	return viewed, cfg.Notifications.Enabled, message, nil
}

func localDay(now time.Time, zone string) time.Time {
	location, err := time.LoadLocation(strings.TrimSpace(zone))
	if err != nil {
		location = time.UTC
	}
	local := now.In(location)
	return time.Date(local.Year(), local.Month(), local.Day(), 0, 0, 0, 0, time.UTC)
}

func (s Service) MarkViewed(ctx context.Context, userID string, artworkID string, day time.Time) error {
	return s.repo.MarkViewedOn(ctx, userID, artworkID, day)
}

func normalizeFilter(filter Filter) Filter {
	if filter.Page <= 0 {
		filter.Page = 1
	}
	if filter.Limit <= 0 {
		filter.Limit = 20
	}
	return filter
}

func matchedFields(item Artwork, query string) []SearchMatchField {
	query = strings.ToLower(strings.TrimSpace(query))
	if query == "" {
		return nil
	}

	fields := make([]SearchMatchField, 0, 4)
	if strings.Contains(strings.ToLower(item.Title), query) {
		fields = append(fields, SearchMatchTitle)
	}
	if strings.Contains(strings.ToLower(item.Artist.Name), query) {
		fields = append(fields, SearchMatchArtist)
	}
	if strings.Contains(strings.ToLower(item.Description), query) {
		fields = append(fields, SearchMatchDescription)
	}
	for _, tag := range item.Tags {
		if strings.Contains(strings.ToLower(tag.Name), query) {
			fields = append(fields, SearchMatchTags)
			break
		}
	}
	return fields
}
