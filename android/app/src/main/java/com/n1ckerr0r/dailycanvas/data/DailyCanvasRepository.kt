package com.n1ckerr0r.dailycanvas.data

import com.n1ckerr0r.dailycanvas.data.remote.DailyCanvasApi
import com.n1ckerr0r.dailycanvas.data.remote.FavoriteMutationRequestDto
import com.n1ckerr0r.dailycanvas.data.remote.NetworkModule
import com.n1ckerr0r.dailycanvas.data.remote.NotificationSettingsPatchDto
import com.n1ckerr0r.dailycanvas.data.remote.SettingsPatchDto
import com.n1ckerr0r.dailycanvas.data.remote.LoginRequestDto
import com.n1ckerr0r.dailycanvas.data.remote.MainViewRequestDto
import com.n1ckerr0r.dailycanvas.data.remote.RegisterRequestDto
import com.n1ckerr0r.dailycanvas.data.remote.ChangePasswordRequestDto
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkCard
import com.n1ckerr0r.dailycanvas.ui.model.CalendarArtwork
import com.n1ckerr0r.dailycanvas.ui.model.HomePayload
import com.n1ckerr0r.dailycanvas.ui.model.SettingsPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DailyCanvasRepository(
    private val api: DailyCanvasApi = NetworkModule.api,
) {
    suspend fun authenticate(email: String, password: String, name: String?, register: Boolean) = withContext(Dispatchers.IO) {
        val response = if (register) api.register(RegisterRequestDto(email, password, name)) else api.login(LoginRequestDto(email, password))
        NetworkModule.session.save(response)
        response.user.email
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching { api.logout() }
        NetworkModule.session.clear()
    }

    suspend fun deleteAccount() = withContext(Dispatchers.IO) {
        api.deleteAccount()
        NetworkModule.session.clear()
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        api.changePassword(ChangePasswordRequestDto(currentPassword, newPassword))
        NetworkModule.session.clear()
    }
    suspend fun loadHome(): HomePayload = withContext(Dispatchers.IO) {
        val main = api.getMain()
        val gallery = api.getGallery().items.map { it.toCard() }
        val favorites = api.getFavorites().items.map { it.toCard() }
        val settings = api.getSettings().toPayload()

        HomePayload(
            greeting = "Доброе утро",
            date = main.date,
            artworkOfDay = main.todayArtwork.toCard(),
            gallery = gallery,
            favorites = favorites,
            settings = settings,
            notificationMessage = main.todayStatus.message,
        )
    }

    suspend fun loadArtwork(artworkId: String): ArtworkCard = withContext(Dispatchers.IO) {
        api.getArtwork(artworkId).toCard()
    }

    suspend fun markTodayViewed(date: String, artworkId: String) = withContext(Dispatchers.IO) {
        api.markTodayViewed(MainViewRequestDto(date = date, artworkId = artworkId))
    }

    suspend fun loadCalendar(from: String, to: String): List<CalendarArtwork> = withContext(Dispatchers.IO) {
        api.getCalendar(from, to).items.map {
            CalendarArtwork(
                date = it.date,
                artworkId = it.artworkId,
                imageUrl = it.imageUrl.toBackendUrl(),
            )
        }
    }

    suspend fun toggleFavorite(artworkId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        if (isFavorite) {
            api.removeFavorite(artworkId)
        } else {
            api.addFavorite(FavoriteMutationRequestDto(artworkId))
        }
    }

    suspend fun updateNotifications(enabled: Boolean, time: String): SettingsPayload = withContext(Dispatchers.IO) {
        api.updateSettings(
            SettingsPatchDto(
                notifications = NotificationSettingsPatchDto(
                    enabled = enabled,
                    time = time,
                ),
            ),
        ).toPayload()
    }

    suspend fun updateCollections(collections: List<String>): SettingsPayload = withContext(Dispatchers.IO) {
        api.updateSettings(SettingsPatchDto(selectedCollections = collections)).toPayload()
    }
}
