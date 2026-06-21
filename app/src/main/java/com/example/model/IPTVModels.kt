package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represent a parsed Live Channel from an M3U playlist.
 */
data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val groupTitle: String = "General",
    val isFavorite: Boolean = false
)

/**
 * Room Table to persist the user's favorite channels.
 */
@Entity(tableName = "favorites")
data class FavoriteChannel(
    @PrimaryKey val streamUrl: String,
    val name: String,
    val logoUrl: String,
    val groupTitle: String
)

/**
 * Room Table to store configured M3U Playlist sources.
 * Prepopulated with the user's provided M3U link on initialization.
 */
@Entity(tableName = "playlist_sources")
data class PlaylistSource(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val url: String,
    val isEditable: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)
