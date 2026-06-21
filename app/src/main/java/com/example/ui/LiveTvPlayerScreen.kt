package com.example.ui

import android.util.Log
import android.widget.Toast
import kotlin.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Channel
import com.example.model.PlaylistSource

// Theme Color Accents - Super simple, clean, neutral dark theme
private val DeepSlateCharcoal = Color(0xFF0C0C0E) // Pure deep charcoal background
private val DarkSlateCard = Color(0xFF1E1E22) // Flat solid dark gray card container
private val GoldenAmberAccent = Color(0xFFE1E1E6) // Neutral soft white/grey text/icon accent
private val LightSlateGlow = Color(0xFF8E8E93) // System standard neutral grey text
private val BrightCyanHot = Color(0xFFFFFFFF) // High contrast pure white text highlight

// Simple flat accent structures
private val DarkPurpleAccent = Color(0xFF2C2C2E) // Dark slate tertiary
private val DeepPurpleOnAccent = Color(0xFF3A3A3C) // Medium slate gray
private val FrostedBorderColor = Color(0x1BFFFFFF) // Subtle thin standard border line

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvPlayerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // State bindings
    val activeChannels by viewModel.filteredChannels.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentPlaying by viewModel.currentPlayingChannel.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val playlists by viewModel.playlistSources.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()

    var showPlaylistManager by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistUrl by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSlateCharcoal)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "📺",
                                    fontSize = 20.sp,
                                    color = GoldenAmberAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Live Stream",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    letterSpacing = (-0.5).sp
                                )
                                Text(
                                    text = activePlaylist?.url?.take(36)?.let { "$it..." } ?: "Famelack India Live TV",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        // One-click Default Loader Button
                        IconButton(
                            onClick = {
                                viewModel.loadPlaylistByUrl(
                                    "Famelack India Live TV",
                                    viewModel.defaultPlaylistUrl
                                )
                                Toast.makeText(context, "Requesting M3U channels...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("reload_default_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Famelack channels",
                                tint = GoldenAmberAccent
                            )
                        }

                        // Playlist source list toggler
                        IconButton(
                            onClick = { showPlaylistManager = !showPlaylistManager },
                            modifier = Modifier.testTag("toggle_playlist_manager_button")
                        ) {
                            Icon(
                                imageVector = if (showPlaylistManager) Icons.Default.List else Icons.Default.Add,
                                contentDescription = "Manage M3u Sources",
                                tint = if (showPlaylistManager) GoldenAmberAccent else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            // Error notice banner
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF421515)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notice",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = error,
                                color = Color(0xFFFFCDD2),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Playlist URL management input & history panel
            AnimatedVisibility(
                visible = showPlaylistManager,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSlateCard),
                    shape = RoundedCornerShape(24.dp), // rounded-3xl
                    border = BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(listOf(GoldenAmberAccent, BrightCyanHot))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "M3U PLAYLIST SOURCES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = BrightCyanHot,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Custom M3U inputs
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Playlist Label (e.g. Famelack India)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.LightGray,
                                focusedBorderColor = GoldenAmberAccent,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedLabelColor = GoldenAmberAccent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_playlist_name_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newPlaylistUrl,
                                onValueChange = { newPlaylistUrl = it },
                                label = { Text("M3U Playlist URL Link") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.LightGray,
                                    focusedBorderColor = BrightCyanHot,
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedLabelColor = BrightCyanHot
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("custom_playlist_url_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newPlaylistUrl.isNotEmpty()) {
                                        viewModel.addNewPlaylistSource(newPlaylistName, newPlaylistUrl)
                                        newPlaylistName = ""
                                        newPlaylistUrl = ""
                                        showPlaylistManager = false
                                        keyboardController?.hide()
                                    }
                                },
                                contentPadding = PaddingValues(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrightCyanHot),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("add_custom_playlist_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add source", tint = Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color.DarkGray, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "PRESETS & RECENT PLAYLISTS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightSlateGlow
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Render Playlist selection sources
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(playlists) { pm ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (activePlaylist?.url == pm.url) Color(0xFF282C3D) else Color.Transparent)
                                        .clickable {
                                            viewModel.loadPlaylistByUrl(pm.name, pm.url)
                                            showPlaylistManager = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (pm.url == viewModel.defaultPlaylistUrl) Icons.Default.PlayArrow else Icons.Default.List,
                                        contentDescription = "M3u doc",
                                        tint = if (activePlaylist?.url == pm.url) GoldenAmberAccent else LightSlateGlow,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pm.name,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (activePlaylist?.url == pm.url) Color.White else Color.LightGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = pm.url,
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (pm.isEditable) {
                                        IconButton(
                                            onClick = { viewModel.removePlaylistSource(pm.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete list",
                                                tint = Color.Red.copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Minimalist Billboard / Hero Banner
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlateCard),
                border = BorderStroke(1.dp, FrostedBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(115.dp)
                        .padding(18.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Text(
                            text = "Premium Live Media",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap on any channel name/card below to automatically stream immersive, latency-free television in full screen.",
                            color = LightSlateGlow,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                    }
                    Text(
                        text = "✨",
                        fontSize = 32.sp,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }

            // Playlist Sync Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSlateCard),
                border = BorderStroke(1.dp, FrostedBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80)) // green-400
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isLoading) "PLAYLIST SYNCING" else "PLAYLIST SYNCED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0), // slate-200
                            letterSpacing = 1.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(GoldenAmberAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${activeChannels.size} Channels",
                            color = GoldenAmberAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Search query textfield
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search channel name or category...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = LightSlateGlow) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = LightSlateGlow)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = DarkSlateCard,
                    unfocusedContainerColor = DarkSlateCard,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_channels_field")
            )

            // Category list selector
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) GoldenAmberAccent else DarkSlateCard)
                            .border(
                                0.8.dp,
                                if (isSelected) Color.Transparent else FrostedBorderColor,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectCategory(cat) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.Black else LightSlateGlow,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Main Channel display list container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (isLoading) {
                    // Glowing loading animation state selector
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = GoldenAmberAccent)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Streaming live playlist catalogs...",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (activeChannels.isEmpty()) {
                    // Empty list state illustration
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Empty",
                            tint = LightSlateGlow.copy(alpha = 0.3f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No channels match your selection.",
                            color = Color.LightGray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Check search query or try refreshing from the default M3u sources",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    // Channels Grid layout
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp)
                            .testTag("channels_grid_list")
                    ) {
                        items(activeChannels) { ch ->
                            ChannelCard(
                                channel = ch,
                                isPlaying = currentPlaying?.streamUrl == ch.streamUrl,
                                onSelect = { viewModel.selectChannelForPlayback(ch) },
                                onToggleFavorite = { viewModel.toggleFavorite(ch) }
                            )
                        }
                    }
                }
            }
        }
 
        // Immersive Fullscreen Video Player Overlay (launched automatically when a channel is clicked)
        AnimatedVisibility(
            visible = currentPlaying != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            if (currentPlaying != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable(enabled = false) {} // capture click events
                ) {
                    VideoPlayer(
                        url = currentPlaying!!.streamUrl,
                        modifier = Modifier.fillMaxSize()
                    )
 
                    // Cinematic Gradient and Overlay Controls header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.selectChannelForPlayback(null) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Close Fullscreen Player",
                                tint = Color.White
                            )
                        }
 
                        // Glass pill showing channel info
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xE013111C))
                                .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(24.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentPlaying!!.logoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(currentPlaying!!.logoUrl)
                                        .crossfade(true)
                                        .build(),
                                    placeholder = null,
                                    contentDescription = "Logo",
                                    fallback = null,
                                    error = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.15f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = currentPlaying!!.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentPlaying!!.groupTitle,
                                    color = GoldenAmberAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
 
                        IconButton(
                            onClick = { viewModel.toggleFavorite(currentPlaying!!) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (currentPlaying!!.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite Toggle",
                                tint = if (currentPlaying!!.isFavorite) Color.Red else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Grid Card Component representing an individual streaming Channel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    channel: Channel,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) Color(0xFF2C2C2E) else if (isFocused) Color(0xFF3A3A3C) else Color(0xFF1E1E22)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelect() }
            .testTag("channel_card_${channel.id}"),
        border = if (isPlaying) {
            BorderStroke(
                1.5.dp,
                BrightCyanHot
            )
        } else if (isFocused) {
            BorderStroke(
                1.5.dp,
                GoldenAmberAccent
            )
        } else {
            BorderStroke(
                0.8.dp,
                FrostedBorderColor
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // TV Logo loading of streams
                if (channel.logoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Channel Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Letter logo placeholder if missing or failed
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2D2D30)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(1).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }

                // Top right bookmark heart
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (channel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Bookmark stream",
                        tint = if (channel.isFavorite) Color.Red else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Play status indication
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Active Playing",
                            tint = GoldenAmberAccent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = channel.name,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = channel.groupTitle.uppercase(),
                fontWeight = FontWeight.Normal,
                fontSize = 8.sp,
                color = LightSlateGlow,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * ExoPlayer Wrapper rendering standard PlayerView directly within Jetpack Compose canvas.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlayerLoading by remember { mutableStateOf(true) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Recreate/prepare dynamic ExoPlayer instance whenever media URL is mutated
    val exoPlayer = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                // Configure HLS support cleanly for .m3u8 urls
                .setMimeType(
                    if (url.contains(".m3u8", ignoreCase = true)) {
                        MimeTypes.APPLICATION_M3U8
                    } else {
                        MimeTypes.APPLICATION_MP4
                    }
                )
                .build()

            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isPlayerLoading = (state == Player.STATE_BUFFERING)
                    if (state == Player.STATE_READY) {
                        playbackError = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VideoPlayer", "Playback error from stream URL ($url)", error)
                    playbackError = "Unable to connect to live channel stream"
                    isPlayerLoading = false
                }
            })
        }
    }

    // Automatic resource clean-ups on Composable disposals
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay status screens (Is Buffering or Stream unreachable)
        if (isPlayerLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GoldenAmberAccent)
            }
        } else if (playbackError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Offline indicator",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Live Stream Offline / Unreachable",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "This free streaming URL is temporarily offline, or requires custom network clearances.",
                        textAlign = TextAlign.Center,
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
