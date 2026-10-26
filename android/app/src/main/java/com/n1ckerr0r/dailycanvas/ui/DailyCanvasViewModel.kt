package com.n1ckerr0r.dailycanvas.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.n1ckerr0r.dailycanvas.data.DailyCanvasRepository
import com.n1ckerr0r.dailycanvas.data.remote.NetworkModule
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkCard
import com.n1ckerr0r.dailycanvas.ui.model.CalendarArtwork
import com.n1ckerr0r.dailycanvas.ui.model.HomePayload
import com.n1ckerr0r.dailycanvas.ui.model.SettingsPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class DailyCanvasViewModel(
    private val repository: DailyCanvasRepository = DailyCanvasRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(DailyCanvasUiState(isAuthenticated = NetworkModule.session.hasSession()))
    val state: StateFlow<DailyCanvasUiState> = _state.asStateFlow()

    init {
        if (NetworkModule.session.hasSession()) refresh() else _state.value = DailyCanvasUiState(isLoading = false)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.loadHome() }
                .onSuccess { payload ->
                    _state.value = DailyCanvasUiState(
                        isLoading = false,
                        isAuthenticated = true,
                        payload = payload,
                        selectedArtwork = payload.artworkOfDay,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isAuthenticated = NetworkModule.session.hasSession(),
                        error = error.message ?: "Не удалось загрузить данные",
                    )
                }
        }
    }

    fun authenticate(email: String, password: String, name: String?, register: Boolean, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            runCatching { repository.authenticate(email, password, name, register) }
                .onSuccess { authenticatedEmail ->
                    _state.value = _state.value.copy(isAuthenticated = true)
                    refresh()
                    onSuccess(authenticatedEmail)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(isLoading = false, error = error.message ?: "Не удалось авторизоваться")
                }
        }
    }

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.logout()
            _state.value = DailyCanvasUiState(isLoading = false)
            onComplete()
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteAccount() }
                .onSuccess { _state.value = DailyCanvasUiState(isLoading = false); onComplete() }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message ?: "Не удалось удалить аккаунт") }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            runCatching { repository.changePassword(currentPassword, newPassword) }
                .onSuccess { _state.value = DailyCanvasUiState(isLoading = false); onComplete() }
                .onFailure { error -> _state.value = _state.value.copy(error = error.message ?: "Не удалось сменить пароль") }
        }
    }

    fun openArtwork(artworkId: String) {
        viewModelScope.launch {
            val payload = _state.value.payload ?: return@launch
            val cached = (payload.gallery + payload.favorites + payload.artworkOfDay)
                .firstOrNull { it.id == artworkId }
            _state.value = _state.value.copy(selectedArtwork = cached ?: payload.artworkOfDay)
            if (artworkId == payload.artworkOfDay.id) {
                runCatching { repository.markTodayViewed(payload.date, artworkId) }
                    .onFailure { error ->
                        _state.value = _state.value.copy(error = error.message ?: "Не удалось сохранить просмотр")
                    }
            }
            runCatching { repository.loadArtwork(artworkId) }
                .onSuccess { loaded -> _state.value = _state.value.copy(selectedArtwork = loaded) }
        }
    }

    fun loadCalendar(from: String, to: String) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val requestedFrom = LocalDate.parse(from)
            if (requestedFrom > today) return@launch
            val safeTo = minOf(LocalDate.parse(to), today)
            _state.value = _state.value.copy(
                isCalendarLoading = true,
                calendarMonth = YearMonth.from(requestedFrom).toString(),
            )
            runCatching { repository.loadCalendar(from, safeTo.toString()) }
                .onSuccess { items ->
                    _state.value = _state.value.copy(calendar = items, isCalendarLoading = false)
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isCalendarLoading = false,
                        error = error.message ?: "Не удалось загрузить календарь",
                    )
                }
        }
    }

    fun initializeCalendar() {
        if (_state.value.calendarMonth != null || _state.value.isCalendarLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCalendarLoading = true)
            val today = LocalDate.now()
            runCatching { repository.loadCalendar(today.minusYears(5).toString(), today.toString()) }
                .onSuccess { allItems ->
                    val latestMonth = allItems.maxOfOrNull { it.date }
                        ?.let { YearMonth.from(LocalDate.parse(it)) }
                        ?: YearMonth.now()
                    _state.value = _state.value.copy(
                        calendar = allItems.filter {
                            YearMonth.from(LocalDate.parse(it.date)) == latestMonth
                        },
                        calendarMonth = latestMonth.toString(),
                        isCalendarLoading = false,
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        calendarMonth = YearMonth.now().toString(),
                        isCalendarLoading = false,
                        error = error.message ?: "Не удалось загрузить календарь",
                    )
                }
        }
    }

    fun toggleFavorite(artwork: ArtworkCard) {
        if (artwork.id in _state.value.pendingFavoriteIds) return
        val previous = _state.value
        _state.value = previous.withFavorite(artwork.id, !artwork.isFavorite).copy(
            pendingFavoriteIds = previous.pendingFavoriteIds + artwork.id,
            error = null,
        )
        viewModelScope.launch {
            runCatching { repository.toggleFavorite(artwork.id, artwork.isFavorite) }
                .onSuccess {
                    _state.value = _state.value.copy(pendingFavoriteIds = _state.value.pendingFavoriteIds - artwork.id)
                }
                .onFailure { error ->
                    _state.value = previous.copy(error = error.message ?: "Не удалось изменить избранное")
                }
        }
    }

    fun updateNotifications(enabled: Boolean, time: String) {
        viewModelScope.launch {
            runCatching { repository.updateNotifications(enabled, time) }
                .onSuccess { settings ->
                    val payload = _state.value.payload ?: return@onSuccess
                    _state.value = _state.value.copy(
                        payload = payload.copy(settings = settings),
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(error = error.message ?: "Не удалось сохранить напоминания")
                }
        }
    }

    fun updateCollections(collections: List<String>) {
        viewModelScope.launch {
            runCatching { repository.updateCollections(collections) }
                .onSuccess { settings ->
                    val payload = _state.value.payload ?: return@onSuccess
                    _state.value = _state.value.copy(payload = payload.copy(settings = settings))
                    // Collection choice changes the server-side gallery scope.
                    // Reload all dependent screens immediately instead of keeping
                    // stale cards until the next app launch.
                    refresh()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(error = error.message ?: "Не удалось сохранить коллекции")
                }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }
}

private fun DailyCanvasUiState.withFavorite(artworkId: String, favorite: Boolean): DailyCanvasUiState {
    fun ArtworkCard.updated() = if (id == artworkId) copy(isFavorite = favorite) else this
    val currentPayload = payload ?: return this
    val allKnown = (currentPayload.gallery + currentPayload.favorites + currentPayload.artworkOfDay)
        .distinctBy { it.id }
        .map { it.updated() }
    val updatedGallery = currentPayload.gallery.map { it.updated() }
    val updatedToday = currentPayload.artworkOfDay.updated()
    val updatedFavorites = allKnown.filter { it.isFavorite }
    return copy(
        payload = currentPayload.copy(
            artworkOfDay = updatedToday,
            gallery = updatedGallery,
            favorites = updatedFavorites,
        ),
        selectedArtwork = selectedArtwork?.updated(),
    )
}

data class DailyCanvasUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val payload: HomePayload? = null,
    val selectedArtwork: ArtworkCard? = null,
    val calendar: List<CalendarArtwork> = emptyList(),
    val calendarMonth: String? = null,
    val isCalendarLoading: Boolean = false,
    val pendingFavoriteIds: Set<String> = emptySet(),
    val error: String? = null,
)
