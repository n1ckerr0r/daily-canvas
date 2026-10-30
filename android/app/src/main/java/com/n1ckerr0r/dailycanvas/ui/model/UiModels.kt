package com.n1ckerr0r.dailycanvas.ui.model

data class ArtworkCard(
    val id: String,
    val title: String,
    val artistId: String,
    val artist: String,
    val country: String,
    val year: String,
    val imageUrl: String,
    val focusX: Float,
    val focusY: Float,
    val isFavorite: Boolean,
    val description: String,
    val facts: List<String>,
    val tags: List<ArtworkTag>,
)

data class ArtworkTag(
    val id: String,
    val name: String,
    val type: String,
)

data class CalendarArtwork(
    val date: String,
    val artworkId: String,
    val imageUrl: String,
    val focusX: Float,
    val focusY: Float,
)

data class SettingsPayload(
    val selectedCollections: List<String>,
    val notificationsEnabled: Boolean,
    val notificationTime: String,
)

data class HomePayload(
    val greeting: String,
    val date: String,
    val artworkOfDay: ArtworkCard,
    val gallery: List<ArtworkCard>,
    val favorites: List<ArtworkCard>,
    val settings: SettingsPayload,
    val notificationMessage: String,
)
