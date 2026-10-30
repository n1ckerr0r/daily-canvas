package http

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"time"

	"github.com/lib/pq"
	openapi "github.com/n1ckerr0r/dailycanvas/backend/internal/adapters/http/openapi"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/artwork"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/auth"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/favorite"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/settings"
	"github.com/n1ckerr0r/dailycanvas/backend/internal/user"
	openapiTypes "github.com/oapi-codegen/runtime/types"
)

type Handler struct {
	artworks  artwork.Service
	auth      auth.Service
	favorites favorite.Service
	settings  settings.Service
	users     user.Service
}

func (h Handler) GetMain(w http.ResponseWriter, r *http.Request) {
	ctx := r.Context()
	currentUser, err := h.currentUser(ctx)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	daily, cfg, viewed, err := h.artworks.Main(ctx, currentUser.ID)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	message := "Сегодня вас ждет новая картина дня."
	if viewed {
		message = "Картина дня уже просмотрена."
	} else if daily.Exhausted {
		message = "Вы уже открыли всю выбранную коллекцию. Новая картина появится после пополнения каталога."
	}

	writeJSON(w, http.StatusOK, openapi.MainResponse{
		Date:                apiDate(daily.Date),
		SelectedCollections: stringCollectionsToAPI(cfg.SelectedCollections),
		TodayArtwork:        artworkToDetail(daily.Artwork),
		TodayStatus: openapi.TodayStatus{
			HasViewedToday: viewed,
			ShouldNotify:   cfg.Notifications.Enabled,
			Message:        message,
		},
	})
}

func (h Handler) GetGalleryArtworks(w http.ResponseWriter, r *http.Request, params openapi.GetGalleryArtworksParams) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	filter := artwork.Filter{
		Page:  valueOr(params.Page, 1),
		Limit: valueOr(params.Limit, 20),
		TagID: valueOrString(params.TagId),
	}
	if params.Collection != nil {
		collection := collectionFromAPI(*params.Collection)
		filter.Collection = &collection
	}

	items, err := h.artworks.Gallery(r.Context(), currentUser.ID, filter)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.GalleryResponse{
		Items:      artworksToSummaries(items),
		Pagination: openapi.Pagination{Page: filter.Page, Limit: filter.Limit, Total: len(items)},
	})
}

func (h Handler) SearchArtworks(w http.ResponseWriter, r *http.Request, params openapi.SearchArtworksParams) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	page := valueOr(params.Page, 1)
	limit := valueOr(params.Limit, 20)
	items, err := h.artworks.Search(r.Context(), currentUser.ID, params.Q, page, limit)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	responseItems := make([]openapi.SearchArtworkItem, 0, len(items))
	for _, item := range items {
		responseItems = append(responseItems, openapi.SearchArtworkItem{
			Id:         item.Artwork.ID,
			Title:      item.Artwork.Title,
			Artist:     artistToAPI(item.Artwork.Artist),
			Year:       item.Artwork.YearValue,
			ImageUrl:   imageURL(item.Artwork.ImagePath),
			FocusPoint: focusPointToAPI(item.Artwork),
			IsFavorite: boolPtr(item.Artwork.IsFavorite),
			Tags:       tagsToAPI(item.Artwork.Tags),
			MatchedBy:  matchFieldsToAPI(item.MatchedBy),
		})
	}

	writeJSON(w, http.StatusOK, openapi.SearchResponse{
		Items:      responseItems,
		Pagination: openapi.Pagination{Page: page, Limit: limit, Total: len(responseItems)},
	})
}

func (h Handler) GetArtworkById(w http.ResponseWriter, r *http.Request, artworkID openapi.ArtworkId) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	item, err := h.artworks.GetByID(r.Context(), currentUser.ID, artworkID)
	if err != nil {
		if errors.Is(err, artwork.ErrNotFound) {
			writeError(w, http.StatusNotFound, "not_found", "Artwork not found")
			return
		}
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, artworkToDetail(item))
}

func (h Handler) LoginUser(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[struct {
		Email    string `json:"email"`
		Password string `json:"password"`
	}](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid login request")
		return
	}
	if !auth.IsValidEmail(body.Email) {
		writeError(w, http.StatusBadRequest, "invalid_email", "Provide a valid email address")
		return
	}
	currentUser, hash, err := h.users.Credentials(r.Context(), body.Email)
	if err != nil || h.auth.CheckPassword(hash, body.Password) != nil {
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "Invalid email or password")
		return
	}
	h.writeAuthResponse(w, r.Context(), currentUser)
}

func (h Handler) LogoutUser(w http.ResponseWriter, r *http.Request) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Authentication is required")
		return
	}
	if err := h.auth.Logout(r.Context(), currentUser.ID); err != nil {
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, openapi.SuccessResponse{Success: true})
}

func (h Handler) RegisterUser(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[struct {
		Email    string  `json:"email"`
		Password string  `json:"password"`
		Name     *string `json:"name"`
	}](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid registration request")
		return
	}
	if !auth.IsValidEmail(body.Email) {
		writeError(w, http.StatusBadRequest, "invalid_email", "Provide a valid email address")
		return
	}
	if len(body.Password) < 8 {
		writeError(w, http.StatusBadRequest, "bad_request", "Password must contain at least 8 characters")
		return
	}
	hash, err := h.auth.HashPassword(body.Password)
	if err != nil {
		writeInternalError(w, err)
		return
	}
	name := ""
	if body.Name != nil {
		name = *body.Name
	}
	currentUser, err := h.users.Register(r.Context(), body.Email, name, hash)
	if err != nil {
		var pgErr *pq.Error
		if errors.As(err, &pgErr) && pgErr.Code == "23505" {
			writeError(w, http.StatusConflict, "email_taken", "This email is already registered")
			return
		}
		writeInternalError(w, err)
		return
	}
	h.writeAuthResponse(w, r.Context(), currentUser)
}

func (h Handler) RefreshAccessToken(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[openapi.RefreshTokenRequest](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", "invalid refresh request")
		return
	}
	tokens, err := h.auth.Refresh(r.Context(), body.RefreshToken, h.users.Get)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "invalid_token", "Refresh token is invalid or expired")
		return
	}

	writeJSON(w, http.StatusOK, openapi.RefreshTokenResponse{
		AccessToken:           tokens.AccessToken,
		RefreshToken:          tokens.RefreshToken,
		TokenType:             tokens.TokenType,
		AccessTokenExpiresIn:  tokens.AccessTokenExpiresIn,
		RefreshTokenExpiresIn: tokens.RefreshTokenExpiresIn,
	})
}

func (h Handler) GetCalendar(w http.ResponseWriter, r *http.Request, params openapi.GetCalendarParams) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	from := time.Now().AddDate(0, 0, -14)
	to := time.Now()
	if params.From != nil {
		from = params.From.Time
	}
	if params.To != nil {
		to = params.To.Time
	}

	items, err := h.artworks.Calendar(r.Context(), currentUser.ID, from, to)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	responseItems := make([]openapi.CalendarItem, 0, len(items))
	for _, item := range items {
		responseItems = append(responseItems, openapi.CalendarItem{
			Date:       apiDate(item.Date),
			ArtworkId:  item.ArtworkID,
			ImageUrl:   imageURL(item.ImagePath),
			FocusPoint: openapi.ArtworkFocusPoint{X: item.FocusX, Y: item.FocusY},
		})
	}

	writeJSON(w, http.StatusOK, openapi.CalendarResponse{
		From:  apiDate(from),
		To:    apiDate(to),
		Items: responseItems,
	})
}

func (h Handler) GetFavorites(w http.ResponseWriter, r *http.Request, params openapi.GetFavoritesParams) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	items, err := h.favorites.List(r.Context(), currentUser.ID, favorite.Sort(valueOrSort(params.Sort)))
	if err != nil {
		writeInternalError(w, err)
		return
	}

	responseItems := make([]openapi.FavoriteArtworkItem, 0, len(items))
	for _, item := range items {
		responseItems = append(responseItems, openapi.FavoriteArtworkItem{
			Id:         item.Artwork.ID,
			Title:      item.Artwork.Title,
			Artist:     artistToAPI(item.Artwork.Artist),
			Country:    item.Artwork.Country,
			Year:       item.Artwork.YearValue,
			ImageUrl:   imageURL(item.Artwork.ImagePath),
			FocusPoint: focusPointToAPI(item.Artwork),
			IsFavorite: boolPtr(item.Artwork.IsFavorite),
			Tags:       tagsToAPI(item.Artwork.Tags),
			AddedAt:    item.AddedAt,
		})
	}

	writeJSON(w, http.StatusOK, openapi.FavoritesResponse{
		Items:      responseItems,
		Pagination: openapi.Pagination{Page: valueOr(params.Page, 1), Limit: valueOr(params.Limit, 20), Total: len(responseItems)},
	})
}

func (h Handler) AddFavorite(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[openapi.FavoriteMutationRequest](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}

	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	if err := h.favorites.Add(r.Context(), currentUser.ID, body.ArtworkId); err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusCreated, openapi.FavoriteAddedResponse{Success: true, ArtworkId: body.ArtworkId, IsFavorite: true})
}

func (h Handler) RemoveFavorite(w http.ResponseWriter, r *http.Request, artworkID openapi.ArtworkId) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	if err := h.favorites.Remove(r.Context(), currentUser.ID, artworkID); err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.FavoriteRemovedResponse{Success: true, ArtworkId: artworkID, IsFavorite: false})
}

func (h Handler) MarkTodayArtworkViewed(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[openapi.MainViewRequest](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}

	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	if err := h.artworks.MarkViewed(r.Context(), currentUser.ID, body.ArtworkId, body.Date.Time); err != nil {
		if errors.Is(err, artwork.ErrNotAssigned) {
			writeError(w, http.StatusNotFound, "not_assigned", "Artwork is not assigned to this user for this date")
			return
		}
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.MainViewResponse{Success: true, HasViewedToday: true})
}

func (h Handler) GetTodayNotificationStatus(w http.ResponseWriter, r *http.Request) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	viewed, shouldNotify, message, err := h.artworks.TodayStatus(r.Context(), currentUser.ID)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.TodayStatus{
		HasViewedToday: viewed,
		ShouldNotify:   shouldNotify,
		Message:        message,
	})
}

func (h Handler) GetSettings(w http.ResponseWriter, r *http.Request) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	value, err := h.settings.Get(r.Context(), currentUser.ID)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, settingsToAPI(value))
}

func (h Handler) UpdateSettings(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[openapi.SettingsPatch](r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}

	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	patch := settings.Patch{}
	if body.SelectedCollections != nil {
		items := make([]string, 0, len(*body.SelectedCollections))
		for _, item := range *body.SelectedCollections {
			items = append(items, string(item))
		}
		patch.SelectedCollections = &items
	}
	if body.Notifications != nil {
		patch.Notifications = &settings.NotificationPatch{
			Enabled:  body.Notifications.Enabled,
			Time:     body.Notifications.Time,
			TimeZone: body.Notifications.TimeZone,
		}
	}

	value, err := h.settings.Update(r.Context(), currentUser.ID, patch)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, settingsToAPI(value))
}

func (h Handler) GetTags(w http.ResponseWriter, r *http.Request) {
	items, err := h.artworks.Tags(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.TagsResponse{Items: derefTags(tagsToAPI(items))})
}

func (h Handler) GetCurrentUser(w http.ResponseWriter, r *http.Request) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.UserProfile{
		Id:        currentUser.ID,
		Email:     openapiTypes.Email(currentUser.Email),
		CreatedAt: &currentUser.CreatedAt,
		Name:      stringPtr(currentUser.Name),
	})
}

func (h Handler) ChangeCurrentUserPassword(w http.ResponseWriter, r *http.Request) {
	body, err := decodeJSON[openapi.ChangePasswordRequest](r)
	if err != nil || len(body.NewPassword) < 8 {
		writeError(w, http.StatusBadRequest, "bad_request", "New password must contain at least 8 characters")
		return
	}
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Authentication is required")
		return
	}
	_, hash, err := h.users.Credentials(r.Context(), currentUser.Email)
	if err != nil || h.auth.CheckPassword(hash, body.CurrentPassword) != nil {
		writeError(w, http.StatusUnauthorized, "invalid_credentials", "Current password is incorrect")
		return
	}
	newHash, err := h.auth.HashPassword(body.NewPassword)
	if err != nil {
		writeInternalError(w, err)
		return
	}
	if err := h.users.UpdatePassword(r.Context(), currentUser.ID, newHash); err != nil {
		writeInternalError(w, err)
		return
	}
	if err := h.auth.Logout(r.Context(), currentUser.ID); err != nil {
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, openapi.SuccessResponse{Success: true})
}

func (h Handler) DeleteCurrentUser(w http.ResponseWriter, r *http.Request) {
	currentUser, err := h.currentUser(r.Context())
	if err != nil {
		writeError(w, http.StatusUnauthorized, "unauthorized", "Authentication is required")
		return
	}
	if err := h.auth.Logout(r.Context(), currentUser.ID); err != nil {
		writeInternalError(w, err)
		return
	}
	if err := h.users.Delete(r.Context(), currentUser.ID); err != nil {
		writeInternalError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, openapi.SuccessResponse{Success: true})
}

func (h Handler) currentUser(ctx context.Context) (user.User, error) {
	id, ok := userIDFromContext(ctx)
	if !ok {
		return user.User{}, sql.ErrNoRows
	}
	return h.users.Get(ctx, id)
}

func (h Handler) writeAuthResponse(w http.ResponseWriter, ctx context.Context, currentUser user.User) {
	tokens, err := h.auth.Issue(ctx, currentUser)
	if err != nil {
		writeInternalError(w, err)
		return
	}

	writeJSON(w, http.StatusOK, openapi.AuthResponse{
		AccessToken:           tokens.AccessToken,
		RefreshToken:          tokens.RefreshToken,
		TokenType:             tokens.TokenType,
		AccessTokenExpiresIn:  tokens.AccessTokenExpiresIn,
		RefreshTokenExpiresIn: tokens.RefreshTokenExpiresIn,
		User: openapi.UserProfile{
			Id:        currentUser.ID,
			Email:     openapiTypes.Email(currentUser.Email),
			CreatedAt: &currentUser.CreatedAt,
			Name:      stringPtr(currentUser.Name),
		},
	})
}

func stringPtr(value string) *string {
	if value == "" {
		return nil
	}
	return &value
}

func decodeJSON[T any](r *http.Request) (T, error) {
	var value T
	if r.Body == nil {
		return value, errors.New("request body is required")
	}
	defer r.Body.Close()
	err := json.NewDecoder(r.Body).Decode(&value)
	return value, err
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, code string, message string) {
	writeJSON(w, status, openapi.ErrorResponse{Code: code, Message: message})
}

func writeInternalError(w http.ResponseWriter, err error) {
	log.Printf("internal API error: %v", err)
	writeError(w, http.StatusInternalServerError, "internal_error", "The server could not complete the request")
}

func apiDate(value time.Time) openapiTypes.Date {
	return openapiTypes.Date{Time: time.Date(value.Year(), value.Month(), value.Day(), 0, 0, 0, 0, time.UTC)}
}

func valueOr[T ~int](value *T, fallback T) T {
	if value == nil {
		return fallback
	}
	return *value
}

func valueOrString(value *string) string {
	if value == nil {
		return ""
	}
	return *value
}

func valueOrSort(value *openapi.FavoritesSort) string {
	if value == nil {
		return string(favorite.SortRecentlyAdded)
	}
	return string(*value)
}
