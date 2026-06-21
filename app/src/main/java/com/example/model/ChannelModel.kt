package com.example.model

import android.content.Context
import android.content.SharedPreferences

/**
 * Data class representing a Live TV Channel.
 */
data class LiveChannel(
    val number: Int,
    val name: String,
    val currentProgram: String,
    val logoUrl: String,
    val streamUrl: String
)

/**
 * Configuration object containing the application's default live channels
 * and SharedPreferences dynamic offline persistence utilities.
 */
object ChannelConfig {
    private const val PREFS_NAME = "live_tv_channels_prefs"
    private const val KEY_CUSTOM_CHANNELS = "custom_channels_list_v1"
    private const val KEY_DELETED_CHANNELS = "deleted_channels_set_v1"

    // Default pre-populated high-quality TV channels
    val defaultList = listOf(
        LiveChannel(
            number = 1,
            name = "Goldmines",
            currentProgram = "Live Stream 1",
            logoUrl = "https://wikimedia.org",
            streamUrl = "https://famelack.com/tv/in/Z3ikslJzUV8EMT"
        ),
        LiveChannel(
            number = 2,
            name = "Goldmines Movies",
            currentProgram = "Live Stream 2",
            logoUrl = "https://wikimedia.org",
            streamUrl = "https://famelack.com/tv/in/cLWVlUzDk2Xpem"
        )
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Load the merged list of default + user-created custom channels, filtering out deleted ones.
     */
    fun loadChannels(context: Context): List<LiveChannel> {
        val prefs = getPrefs(context)
        val deletedUrls = prefs.getStringSet(KEY_DELETED_CHANNELS, emptySet()) ?: emptySet()
        val savedStr = prefs.getString(KEY_CUSTOM_CHANNELS, null)
        
        val customChannels = if (savedStr != null) {
            try {
                savedStr.split("##")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { deserializeChannel(it) }
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
        
        val combinedList = mutableListOf<LiveChannel>()
        var nextNum = 1
        
        // Add active default channels
        defaultList.forEach { defaultChan ->
            if (!deletedUrls.contains(defaultChan.streamUrl)) {
                combinedList.add(defaultChan.copy(number = nextNum++))
            }
        }
        
        // Add active custom channels
        customChannels.forEach { customChan ->
            if (!deletedUrls.contains(customChan.streamUrl)) {
                combinedList.add(customChan.copy(number = nextNum++))
            }
        }
        
        return combinedList
    }

    /**
     * Add and persist a new custom channel into local storage.
     */
    fun addChannel(context: Context, name: String, streamUrl: String, logoUrl: String, currentProgram: String): List<LiveChannel> {
        val prefs = getPrefs(context)
        val currentSaved = prefs.getString(KEY_CUSTOM_CHANNELS, "") ?: ""
        
        val newChannel = LiveChannel(
            number = 0, // Assigned sequentially on load
            name = name.trim().ifEmpty { "Custom Channel" },
            currentProgram = currentProgram.trim().ifEmpty { "Live Stream" },
            logoUrl = logoUrl.trim().ifEmpty { "https://upload.wikimedia.org/wikipedia/commons/e/e0/Zee_Action_logo.png" },
            streamUrl = streamUrl.trim()
        )
        
        val serialized = serializeChannel(newChannel)
        val updatedSaved = if (currentSaved.isEmpty()) serialized else "$currentSaved##$serialized"
        
        // In case this URL was previously deleted, remove it from the deleted set
        val deletedUrls = prefs.getStringSet(KEY_DELETED_CHANNELS, emptySet()) ?: emptySet()
        if (deletedUrls.contains(streamUrl.trim())) {
            val updatedDeleted = deletedUrls.toMutableSet()
            updatedDeleted.remove(streamUrl.trim())
            prefs.edit().putStringSet(KEY_DELETED_CHANNELS, updatedDeleted).apply()
        }
        
        prefs.edit().putString(KEY_CUSTOM_CHANNELS, updatedSaved).apply()
        return loadChannels(context)
    }

    /**
     * Mark a channel as deleted, persisting state.
     */
    fun deleteChannel(context: Context, streamUrl: String): List<LiveChannel> {
        val prefs = getPrefs(context)
        val deletedUrls = prefs.getStringSet(KEY_DELETED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
        deletedUrls.add(streamUrl)
        
        // Also physically prune from custom channels list to save storage spaces
        val savedStr = prefs.getString(KEY_CUSTOM_CHANNELS, null)
        if (savedStr != null) {
            try {
                val customChannels = savedStr.split("##")
                    .filter { it.isNotEmpty() }
                    .mapNotNull { deserializeChannel(it) }
                val updatedCustomList = customChannels.filter { it.streamUrl != streamUrl }
                val newSavedStr = updatedCustomList.joinToString("##") { serializeChannel(it) }
                prefs.edit().putString(KEY_CUSTOM_CHANNELS, newSavedStr).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        prefs.edit().putStringSet(KEY_DELETED_CHANNELS, deletedUrls).apply()
        return loadChannels(context)
    }

    /**
     * Lightweight custom delimiter-based serialization.
     * Keeps the app footprint tiny, avoiding runtime reflection or class mismatches.
     */
    private fun serializeChannel(channel: LiveChannel): String {
        return "${channel.name.replace("|", "")}|${channel.currentProgram.replace("|", "")}|${channel.logoUrl.replace("|", "")}|${channel.streamUrl.replace("|", "")}"
    }

    private fun deserializeChannel(str: String): LiveChannel? {
        val parts = str.split("|")
        if (parts.size < 4) return null
        return LiveChannel(
            number = 0, // Placeholder
            name = parts[0],
            currentProgram = parts[1],
            logoUrl = parts[2],
            streamUrl = parts[3]
        )
    }
}
