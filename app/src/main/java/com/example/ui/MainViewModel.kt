package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.IPTVRepository
import com.example.model.Channel
import com.example.model.FallbackChannels
import com.example.model.FavoriteChannel
import com.example.model.PlaylistSource
import com.example.parser.M3uParser
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

sealed interface UiState {
    data class Success(
        val channels: List<Channel>,
        val categories: List<String>,
        val selectedCategory: String = "All",
        val searchQuery: String = "",
        val currentPlaylistName: String = ""
    ) : UiState
    object Loading : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(private val repository: IPTVRepository) : ViewModel() {
    private val TAG = "MainViewModel"

    // OkHttpClient instance for fetching M3U playlists
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Default playlist url provided by user
    val defaultPlaylistUrl = "https://famelack.com/tv/in/Z3ikslJzUV8EMT"

    // Holds the raw parsed channels from the active playlist
    private val _rawChannels = MutableStateFlow<List<Channel>>(emptyList())

    // Tracks currently reading playlist source
    private val _activePlaylist = MutableStateFlow<PlaylistSource?>(null)
    val activePlaylist: StateFlow<PlaylistSource?> = _activePlaylist.asStateFlow()

    // Loading & network status state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Search query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected category State
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    // Currently playing stream State
    private val _currentPlayingChannel = MutableStateFlow<Channel?>(null)
    val currentPlayingChannel: StateFlow<Channel?> = _currentPlayingChannel.asStateFlow()

    // List of playlist sources from DB
    val playlistSources: StateFlow<List<PlaylistSource>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of favorite channels from DB
    val favoriteChannels: StateFlow<List<FavoriteChannel>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Combined Flow: Computes active channel list with exact isFavorite flags
    val channels: StateFlow<List<Channel>> = combine(_rawChannels, favoriteChannels) { raw, favorites ->
        raw.map { channel ->
            channel.copy(isFavorite = favorites.any { it.streamUrl == channel.streamUrl })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered channels Flow by Category & Search query
    val filteredChannels: StateFlow<List<Channel>> = combine(channels, _selectedCategory, _searchQuery) { list, category, query ->
        list.filter { channel ->
            val matchesCategory = (category == "All") || (channel.groupTitle.equals(category, ignoreCase = true))
            val matchesQuery = query.isEmpty() || channel.name.contains(query, ignoreCase = true) || channel.groupTitle.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic Categories Flow extracted directly from current playlist channels
    val categories: StateFlow<List<String>> = channels.map { list ->
        val groups = list.map { it.groupTitle }.distinct().filter { it.isNotEmpty() }.sortedBy { it.lowercase() }
        listOf("All") + groups
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    init {
        // Prepare database default source and trigger initial load
        viewModelScope.launch {
            // Check if default playlist is already registered, if not, add it
            repository.playlists.first().let { currentList ->
                val hasDefault = currentList.any { it.url == defaultPlaylistUrl }
                if (!hasDefault) {
                    val defaultSource = PlaylistSource(
                        name = "Famelack India Live TV",
                        url = defaultPlaylistUrl,
                        isEditable = false
                    )
                    repository.addPlaylist(defaultSource)
                }
            }

            // Fetch default playlist source immediately
            loadPlaylistByUrl("Famelack India Live TV", defaultPlaylistUrl)
        }
    }

    /**
     * Downloads and parses an M3U playlist from any URL.
     */
    fun loadPlaylistByUrl(name: String, url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _activePlaylist.value = PlaylistSource(name = name, url = url, isEditable = false)
            _rawChannels.value = emptyList() // clear current
            _currentPlayingChannel.value = null // reset player

            if (url == defaultPlaylistUrl || 
                url == "https://famelack.com/tv/in/Z3ikslJzUV8EMT" || 
                url == "https://famelack.com/tv/in/cLWVlUzDk2Xpem") {
                _rawChannels.value = FallbackChannels.list
                _isLoading.value = false
                _selectedCategory.value = "All"
                return@launch
            }

            try {
                Log.d(TAG, "Fetching playlist: $url")
                val parsedList = M3uParser.fetchFromUrl(okHttpClient, url)
                if (parsedList.isEmpty()) {
                    Log.w(TAG, "M3U was parsed successfully but returned absolutely no channels.")
                    // Fallback to active demo catalog if loaded nothing
                    _rawChannels.value = FallbackChannels.list
                    _errorMessage.value = "The URL loaded successfully but contains no valid streaming channels. Enjoy fallback channels!"
                } else {
                    _rawChannels.value = parsedList
                    Log.d(TAG, "Loaded parsed channels count: ${parsedList.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading playlist from URL", e)
                // When request fails (such as blocked by sandbox Proxy restrictions), we elegant fallback
                _rawChannels.value = FallbackChannels.list
                _activePlaylist.value = PlaylistSource(name = "Fallback Demo Playlist", url = "", isEditable = false)
                _errorMessage.value = "Failed to load channel list from URL (${e.localizedMessage ?: "Connection Timeout"}). Load Fallback Demo Channels!"
            } finally {
                _isLoading.value = false
                _selectedCategory.value = "All"
            }
        }
    }

    /**
     * Toggle bookmarking of a live channel.
     */
    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val isFav = repository.isFavorite(channel.streamUrl)
            if (isFav) {
                repository.removeFavorite(channel.streamUrl)
            } else {
                repository.addFavorite(
                    FavoriteChannel(
                        streamUrl = channel.streamUrl,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        groupTitle = channel.groupTitle
                    )
                )
            }
        }
    }

    /**
     * User actions: Search & Filter Change
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectChannelForPlayback(channel: Channel?) {
        _currentPlayingChannel.value = channel
    }

    /**
     * Add a generic playlist source to history.
     */
    fun addNewPlaylistSource(name: String, url: String) {
        viewModelScope.launch {
            if (url.isEmpty()) return@launch
            val formattedName = if (name.trim().isEmpty()) "Custom M3U" else name.trim()
            repository.addPlaylist(PlaylistSource(name = formattedName, url = url.trim()))
            loadPlaylistByUrl(formattedName, url.trim())
        }
    }

    /**
     * Remove playlist from bookmarks.
     */
    fun removePlaylistSource(id: Int) {
        viewModelScope.launch {
            repository.removePlaylist(id)
        }
    }
}

/**
 * Custom Factory pattern to instantiate MainViewModel with local Repository setup.
 */
class MainViewModelFactory(private val repository: IPTVRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
