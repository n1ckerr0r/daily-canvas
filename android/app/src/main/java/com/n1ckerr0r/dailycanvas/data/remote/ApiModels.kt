package com.n1ckerr0r.dailycanvas.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MainResponseDto(
    val date: String,
    @SerialName("selectedCollections") val selectedCollections: List<String>,
    @SerialName("todayArtwork") val todayArtwork: ArtworkDetailDto,
    @SerialName("todayStatus") val todayStatus: TodayStatusDto,
)

@Serializable
data class GalleryResponseDto(
    val items: List<ArtworkSummaryDto>,
)

@Serializable
data class FavoritesResponseDto(
    val items: List<FavoriteArtworkDto>,
)

@Serializable
data class SettingsDto(
    @SerialName("selectedCollections") val selectedCollections: List<String>,
    val notifications: NotificationSettingsDto,
)

@Serializable
data class TagsResponseDto(
    val items: List<TagDto>,
)

@Serializable
data class CalendarResponseDto(
    val from: String,
    val to: String,
    val items: List<CalendarItemDto>,
)

@Serializable
data class CalendarItemDto(
    val date: String,
    @SerialName("artworkId") val artworkId: String,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("focusPoint") val focusPoint: ArtworkFocusPointDto,
)

@Serializable
data class ArtworkFocusPointDto(
    val x: Float,
    val y: Float,
)

@Serializable
data class ArtworkDetailDto(
    val id: String,
    val title: String,
    val artist: ArtistDto,
    val country: String,
    val year: Int,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("focusPoint") val focusPoint: ArtworkFocusPointDto,
    @SerialName("isFavorite") val isFavorite: Boolean? = null,
    val tags: List<TagDto> = emptyList(),
    val description: String,
    val facts: List<String>,
    val collection: String,
)

@Serializable
data class ArtworkSummaryDto(
    val id: String,
    val title: String,
    val artist: ArtistDto,
    val country: String,
    val year: Int,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("focusPoint") val focusPoint: ArtworkFocusPointDto,
    @SerialName("isFavorite") val isFavorite: Boolean? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
data class FavoriteArtworkDto(
    val id: String,
    val title: String,
    val artist: ArtistDto,
    val country: String,
    val year: Int,
    @SerialName("imageUrl") val imageUrl: String,
    @SerialName("focusPoint") val focusPoint: ArtworkFocusPointDto,
    @SerialName("isFavorite") val isFavorite: Boolean? = null,
    val tags: List<TagDto> = emptyList(),
)

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
)

@Serializable
data class TagDto(
    val id: String,
    val name: String,
    val type: String,
)

@Serializable
data class TodayStatusDto(
    @SerialName("hasViewedToday") val hasViewedToday: Boolean,
    @SerialName("shouldNotify") val shouldNotify: Boolean,
    val message: String,
)

@Serializable
data class MainViewRequestDto(
    val date: String,
    @SerialName("artworkId") val artworkId: String,
)

@Serializable
data class MainViewResponseDto(
    val success: Boolean,
    @SerialName("hasViewedToday") val hasViewedToday: Boolean,
)

@Serializable
data class NotificationSettingsDto(
    val enabled: Boolean,
    val time: String,
    @SerialName("timeZone") val timeZone: String,
)

@Serializable
data class FavoriteMutationRequestDto(
    @SerialName("artworkId") val artworkId: String,
)

@Serializable
data class FavoriteMutationResponseDto(
    val success: Boolean,
    @SerialName("artworkId") val artworkId: String,
    @SerialName("isFavorite") val isFavorite: Boolean,
)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RegisterRequestDto(val email: String, val password: String, val name: String? = null)

@Serializable
data class ChangePasswordRequestDto(
    @SerialName("currentPassword") val currentPassword: String,
    @SerialName("newPassword") val newPassword: String,
)

@Serializable
data class RefreshTokenRequestDto(@SerialName("refreshToken") val refreshToken: String)

@Serializable
data class RefreshTokenResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("tokenType") val tokenType: String,
    @SerialName("accessTokenExpiresIn") val accessTokenExpiresIn: Int,
    @SerialName("refreshTokenExpiresIn") val refreshTokenExpiresIn: Int,
)

@Serializable
data class UserProfileDto(val id: String, val email: String, val name: String? = null, @SerialName("createdAt") val createdAt: String)

@Serializable
data class AuthResponseDto(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("tokenType") val tokenType: String,
    val user: UserProfileDto,
)

@Serializable
data class SettingsPatchDto(
    @SerialName("selectedCollections") val selectedCollections: List<String>? = null,
    val notifications: NotificationSettingsPatchDto? = null,
)

@Serializable
data class NotificationSettingsPatchDto(
    val enabled: Boolean? = null,
    val time: String? = null,
    @SerialName("timeZone") val timeZone: String? = null,
)
