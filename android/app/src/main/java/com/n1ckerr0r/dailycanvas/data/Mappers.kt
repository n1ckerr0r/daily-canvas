package com.n1ckerr0r.dailycanvas.data

import com.n1ckerr0r.dailycanvas.BuildConfig
import com.n1ckerr0r.dailycanvas.data.remote.ArtworkDetailDto
import com.n1ckerr0r.dailycanvas.data.remote.ArtworkSummaryDto
import com.n1ckerr0r.dailycanvas.data.remote.FavoriteArtworkDto
import com.n1ckerr0r.dailycanvas.data.remote.SettingsDto
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkCard
import com.n1ckerr0r.dailycanvas.ui.model.ArtworkTag
import com.n1ckerr0r.dailycanvas.ui.model.SettingsPayload

fun ArtworkDetailDto.toCard(): ArtworkCard = ArtworkCard(
    id = id,
    title = title,
    artistId = artist.id,
    artist = artist.name,
    country = country,
    year = year.toString(),
    imageUrl = imageUrl.toBackendUrl(),
    focusX = focusPoint.x,
    focusY = focusPoint.y,
    isFavorite = isFavorite ?: false,
    description = description,
    facts = facts,
    tags = tags.map { ArtworkTag(it.id, it.name, it.type) },
)

fun ArtworkSummaryDto.toCard(): ArtworkCard = ArtworkCard(
    id = id,
    title = title,
    artistId = artist.id,
    artist = artist.name,
    country = country,
    year = year.toString(),
    imageUrl = imageUrl.toBackendUrl(),
    focusX = focusPoint.x,
    focusY = focusPoint.y,
    isFavorite = isFavorite ?: false,
    description = "",
    facts = emptyList(),
    tags = tags.map { ArtworkTag(it.id, it.name, it.type) },
)

fun FavoriteArtworkDto.toCard(): ArtworkCard = ArtworkCard(
    id = id,
    title = title,
    artistId = artist.id,
    artist = artist.name,
    country = country,
    year = year.toString(),
    imageUrl = imageUrl.toBackendUrl(),
    focusX = focusPoint.x,
    focusY = focusPoint.y,
    isFavorite = isFavorite ?: true,
    description = "",
    facts = emptyList(),
    tags = tags.map { ArtworkTag(it.id, it.name, it.type) },
)

fun SettingsDto.toPayload(): SettingsPayload = SettingsPayload(
    selectedCollections = selectedCollections,
    notificationsEnabled = notifications.enabled,
    notificationTime = notifications.time,
)

fun String.toBackendUrl(): String {
    if (startsWith("http://") || startsWith("https://")) return this

    val backendRoot = BuildConfig.API_BASE_URL
        .substringBefore("/api/v1")
        .trimEnd('/')
    return "$backendRoot/${trimStart('/')}"
}
