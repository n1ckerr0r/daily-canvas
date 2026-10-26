package com.n1ckerr0r.dailycanvas.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DailyCanvasApi {
	@POST("auth/login")
	suspend fun login(@Body request: LoginRequestDto): AuthResponseDto

	@POST("auth/register")
	suspend fun register(@Body request: RegisterRequestDto): AuthResponseDto

	@POST("auth/token/refresh")
	suspend fun refresh(@Body request: RefreshTokenRequestDto): RefreshTokenResponseDto

	@POST("auth/logout")
	suspend fun logout(): SuccessResponseDto

    @DELETE("users/me")
    suspend fun deleteAccount(): SuccessResponseDto

    @PATCH("users/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequestDto): SuccessResponseDto
    @GET("main")
    suspend fun getMain(): MainResponseDto

    @POST("main/view")
    suspend fun markTodayViewed(@Body request: MainViewRequestDto): MainViewResponseDto

    @GET("calendar")
    suspend fun getCalendar(
        @Query("from") from: String,
        @Query("to") to: String,
    ): CalendarResponseDto

    @GET("artworks/gallery")
    suspend fun getGallery(
        @Query("collection") collection: String? = null,
        @Query("tagId") tagId: String? = null,
    ): GalleryResponseDto

    @GET("artworks/{artworkId}")
    suspend fun getArtwork(@Path("artworkId") artworkId: String): ArtworkDetailDto

    @GET("favorites")
    suspend fun getFavorites(
        @Query("sort") sort: String = "recently_added",
    ): FavoritesResponseDto

    @POST("favorites")
    suspend fun addFavorite(@Body request: FavoriteMutationRequestDto): FavoriteMutationResponseDto

    @DELETE("favorites/{artworkId}")
    suspend fun removeFavorite(@Path("artworkId") artworkId: String): FavoriteMutationResponseDto

    @GET("settings")
    suspend fun getSettings(): SettingsDto

    @PATCH("settings")
    suspend fun updateSettings(@Body request: SettingsPatchDto): SettingsDto

    @GET("tags")
    suspend fun getTags(): TagsResponseDto
}

@kotlinx.serialization.Serializable
data class SuccessResponseDto(val success: Boolean)
