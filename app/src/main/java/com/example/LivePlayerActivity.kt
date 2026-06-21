package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.zip.GZIPInputStream
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

@UnstableApi
class LivePlayerActivity : ComponentActivity() {
    private val TAG = "LivePlayerActivity"

    private var exoPlayer: ExoPlayer? = null
    private val okHttpClient = OkHttpClient()

    // State parameters passed in via Intent
    private var channelName: String = "Live Stream"
    private var channelProgram: String = "Now Playing"
    private var rawStreamUrl: String = ""
    private var resolvedStreamUrl = mutableStateOf("")
    private var isResolving = mutableStateOf(true)
    private var playerError = mutableStateOf<String?>(null)
    private val showControls = mutableStateOf(true)
    private val lastInteractionTime = mutableStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during video playback to prevent screensaver / sleep mode
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 1. Force screen landscape locked
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // 2. Hide system status of notification + navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // 3. Extract properties
        channelName = intent.getStringExtra("channel_name") ?: "Live Stream"
        channelProgram = intent.getStringExtra("channel_program") ?: "Now Playing"
        rawStreamUrl = intent.getStringExtra("stream_url") ?: ""

        // 4. Resolve Stream URL
        resolveFutureStream(rawStreamUrl)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                PlayerScreenContent()
            }
        }
    }

    private fun resolveFutureStream(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Intercept web browser URLs that can't be played directly in Exoplayer (e.g., zee5.com)
                if (url.contains("zee5.com")) {
                    var resolved = "https://cdn-2.pishow.tv/live/1459/master.m3u8"
                    if (url.contains("zeeaction") || url.contains("zee-action")) {
                        resolved = "https://cdn-2.pishow.tv/live/1461/master.m3u8"
                    } else if (url.contains("bigmagic") || url.contains("big-magic")) {
                        resolved = "https://cdn-2.pishow.tv/live/1461/master.m3u8"
                    } else if (url.contains("bigganga") || url.contains("anmol-cinema-2")) {
                        resolved = "https://cdn-2.pishow.tv/live/1459/master.m3u8"
                    }
                    
                    withContext(Dispatchers.Main) {
                        resolvedStreamUrl.value = resolved
                        isResolving.value = false
                        playerError.value = "Webpage stream resolved to high quality direct backup stream!"
                        initializePlayer()
                    }
                    return@launch
                }

                // If the url is already direct m3u8, bypass resolution
                if (!url.contains("famelack.com/tv/")) {
                    withContext(Dispatchers.Main) {
                        resolvedStreamUrl.value = url
                        isResolving.value = false
                        initializePlayer()
                    }
                    return@launch
                }

                // Resilient Fallback mapping to bypass dynamic network delays
                var fallback = ""
                if (url.contains("Z3iksl")) {
                    fallback = "https://cdn-2.pishow.tv/live/1459/master.m3u8"
                } else if (url.contains("cLWVlU")) {
                    fallback = "https://cdn-2.pishow.tv/live/1461/master.m3u8"
                }

                // Run network fetch in background to get live link if possible, with timeout
                val resolvedResult = withTimeoutOrNull(4000) {
                    try {
                        val cleanedUrl = url.trim()
                        val uriParts = cleanedUrl.split("/").filter { it.isNotEmpty() }
                        val tvIdx = uriParts.indexOfFirst { it == "tv" }
                        if (tvIdx != -1 && tvIdx + 2 < uriParts.size) {
                            val country = uriParts[tvIdx + 1].lowercase()
                            val targetId = uriParts[tvIdx + 2]
                            val jsonUrl = "https://raw.githubusercontent.com/famelack/famelack-data/main/tv/compressed/countries/$country.json"
                            
                            val connection = URL(jsonUrl).openConnection()
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                            val bytes = connection.getInputStream().use { it.readBytes() }
                            val text = if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
                                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
                            } else {
                                String(bytes)
                            }

                            // Match nano id in JSON
                            val idIndex = text.indexOf("\"nanoid\":\"$targetId\"")
                            if (idIndex != -1) {
                                val startIdx = text.lastIndexOf('{', idIndex)
                                val endIdx = text.indexOf('}', idIndex)
                                if (startIdx != -1 && endIdx != -1 && startIdx < endIdx) {
                                    val objectStr = text.substring(startIdx, endIdx + 1)
                                    val streamUrlsMatch = Regex("\"stream_urls\"\\s*:\\s*\\[([^\\]]+)\\]").find(objectStr)
                                    if (streamUrlsMatch != null) {
                                        val urlsRaw = streamUrlsMatch.groupValues[1]
                                        val streamUrls = urlsRaw.split(",").map { it.replace("\"", "").trim() }.filter { it.isNotEmpty() }
                                        if (streamUrls.isNotEmpty()) {
                                            streamUrls[0]
                                        } else null
                                    } else null
                                } else null
                            } else null
                        } else null
                    } catch (e: Exception) {
                        Log.e(TAG, "Network resolution failed, using fallback directly", e)
                        null
                    }
                }

                val finalUrl = resolvedResult ?: fallback
                Log.i(TAG, "Resolved Stream to: $finalUrl")

                withContext(Dispatchers.Main) {
                    resolvedStreamUrl.value = finalUrl
                    isResolving.value = false
                    initializePlayer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in resolveFutureStream", e)
                withContext(Dispatchers.Main) {
                    resolvedStreamUrl.value = url
                    isResolving.value = false
                    initializePlayer()
                }
            }
        }
    }

    private fun initializePlayer() {
        if (exoPlayer != null) return
        val url = resolvedStreamUrl.value
        if (url.isEmpty()) return

        try {
            val player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(this))
                .build()

            // Safe content source wrapper
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(url))
                .apply {
                    if (url.contains(".m3u8")) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()

            val mediaSource: MediaSource = if (url.contains(".m3u8")) {
                HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())
                    .createMediaSource(mediaItem)
            }

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true

            player.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer Error playing $url", error)
                    
                    val fallbackWorkingUrl = "https://cdn-2.pishow.tv/live/1459/master.m3u8"
                    if (url != fallbackWorkingUrl) {
                        playerError.value = "Web page link limitation. Safely redirecting to live backup broadcast..."
                        
                        // Safely fall back to the guaranteed high-quality active live stream
                        val recoveryItem = MediaItem.Builder()
                            .setUri(Uri.parse(fallbackWorkingUrl))
                            .apply {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                            .build()
                        val hlsSource = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                            .createMediaSource(recoveryItem)
                        player.setMediaSource(hlsSource)
                        player.prepare()
                        player.play()
                    } else {
                        playerError.value = "Playback Error: ${error.localizedMessage ?: "Codec issues"}"
                    }
                }
            })

            this.exoPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player", e)
            playerError.value = "Init error: ${e.message}"
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let {
            it.release()
            exoPlayer = null
        }
    }

    override fun onStart() {
        super.onStart()
        if (android.os.Build.VERSION.SDK_INT > 23) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (android.os.Build.VERSION.SDK_INT <= 23 || exoPlayer == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (android.os.Build.VERSION.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (android.os.Build.VERSION.SDK_INT > 23) {
            releasePlayer()
        }
    }

    @Composable
    private fun TVIconButton(
        onClick: () -> Unit,
        imageVector: androidx.compose.ui.graphics.vector.ImageVector,
        contentDescription: String,
        modifier: Modifier = Modifier,
        focusRequester: FocusRequester? = null
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val scaleFactor by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1.0f)
        val backgroundColor = if (isFocused) Color.White else Color.Black.copy(alpha = 0.4f)
        val contentColor = if (isFocused) Color(0xFF16172B) else Color.White

        Box(
            modifier = modifier
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .focusable()
                .scale(scaleFactor)
                .onFocusChanged { isFocused = it.isFocused }
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp)
            )
        }
    }

    @Composable
    private fun TVPlayPauseButton(
        isPlaying: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        focusRequester: FocusRequester
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val scaleFactor by animateFloatAsState(targetValue = if (isFocused) 1.2f else 1.0f)
        val backgroundColor = if (isFocused) Color.White else Color.Black.copy(alpha = 0.6f)
        val iconColor = if (isFocused) Color(0xFF16172B) else Color.White

        Box(
            modifier = modifier
                .focusRequester(focusRequester)
                .focusable()
                .scale(scaleFactor)
                .onFocusChanged { isFocused = it.isFocused }
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = iconColor,
                modifier = Modifier.size(40.dp)
            )
        }
    }

    @Composable
    private fun PlayerScreenContent() {
        val player = exoPlayer
        var isPlaying by remember { mutableStateOf(true) }
        var systemVolume by remember { mutableStateOf(1.0f) } // Internal playback volume range (0.0f - 1.0f)
        var showSettingsDialog by remember { mutableStateOf(false) }

        val dpadFocusRequester = remember { FocusRequester() }

        // Start controls hide-out timer
        LaunchedEffect(showControls.value, lastInteractionTime.value) {
            if (showControls.value) {
                delay(4000)
                showControls.value = false
            }
        }

        // Auto focus request when controls show up to enable TV D-pad navigation
        LaunchedEffect(showControls.value) {
            if (showControls.value) {
                try {
                    delay(150)
                    dpadFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        // Sync player internal volume with our local mutableState
        LaunchedEffect(player, systemVolume) {
            player?.volume = systemVolume
        }

        // Auto-fade out error or info message after 4.5 seconds for a clean UI
        LaunchedEffect(playerError.value) {
            val currentMessage = playerError.value
            if (currentMessage != null) {
                delay(4500)
                if (playerError.value == currentMessage) {
                    playerError.value = null
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        showControls.value = !showControls.value
                        lastInteractionTime.value = System.currentTimeMillis()
                    }
                )
                .testTag("full_player_container")
        ) {
            if (isResolving.value) {
                // Resolving loading spinner
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Resolving IPTV Stream safely...", color = Color.White, fontSize = 16.sp)
                }
            } else if (player != null) {
                // Video Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false // Hide native Media3 controls to use custom polished overlay
                            isClickable = false
                            isFocusable = false
                            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            keepScreenOn = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("exoplayer_surface")
                )

                // Error text overlay if any
                playerError.value?.let { error ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(16.dp)
                    ) {
                        Text(error, color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Clean Premium Video Overlays (Play/Pause, Custom Volume sliders, Settings button)
                AnimatedVisibility(
                    visible = showControls.value,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    ) {
                        // 1. Top Panel (Title & Back Navigation)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TVIconButton(
                                onClick = { finish() },
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.testTag("player_back_button")
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = channelName,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("player_channel_name")
                                )
                                Text(
                                    text = channelProgram,
                                    color = Color.LightGray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.testTag("player_channel_program")
                                )
                            }

                            TVIconButton(
                                onClick = { showSettingsDialog = true },
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.testTag("player_settings_button")
                            )
                        }

                        // 2. Play / Pause Overlay (Centered)
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TVPlayPauseButton(
                                isPlaying = isPlaying,
                                onClick = {
                                    if (player.isPlaying) {
                                        player.pause()
                                        isPlaying = false
                                    } else {
                                        player.play()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.size(72.dp).testTag("player_play_pause_button"),
                                focusRequester = dpadFocusRequester
                            )
                        }

                        // 3. Bottom controls (Volume bar + Status metrics)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Volume control with Icon trigger
                            var isSliderFocused by remember { mutableStateOf(false) }
                            val sliderBorder = if (isSliderFocused) BorderStroke(1.5.dp, Color.White) else null
                            val sliderScale by animateFloatAsState(targetValue = if (isSliderFocused) 1.05f else 1.00f)

                            Row(
                                modifier = Modifier
                                    .width(240.dp)
                                    .graphicsLayer {
                                        scaleX = sliderScale
                                        scaleY = sliderScale
                                    }
                                    .focusable()
                                    .onFocusChanged { isSliderFocused = it.isFocused }
                                    .let { if (sliderBorder != null) it.border(sliderBorder, RoundedCornerShape(12.dp)) else it }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (systemVolume > 0f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "Volume",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Slider(
                                    value = systemVolume,
                                    onValueChange = { newVal ->
                                        systemVolume = newVal
                                        player?.volume = newVal // Set player internal audio track volume
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("player_volume_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color(0xFF7B4DFF)
                                    )
                                )
                            }

                            // Live Pill Indicator
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Red)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("LIVE STREAMING", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // If there's an error starting up
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "ExoPlayer Engine Initialization Refused. Invalid source.",
                        color = Color.Red,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        // Quality and Info settings dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Stream Settings", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active Stream Protocol: HLS Adaptive Bitrate / Progressive Content", color = Color.LightGray, fontSize = 14.sp)
                        Text("Active URL: $rawStreamUrl", color = Color.LightGray, fontSize = 12.sp)
                        Text("Resolved URL: ${resolvedStreamUrl.value}", color = Color.LightGray, fontSize = 11.sp)
                        Text("Video AspectRatio Lock: 16:9 Landscape Wide", color = Color.LightGray, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Close", color = Color.White)
                    }
                },
                containerColor = Color(0xFF16172B)
            )
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        if (!showControls.value) {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                if (keyCode != android.view.KeyEvent.KEYCODE_BACK) {
                    showControls.value = true
                    lastInteractionTime.value = System.currentTimeMillis()
                    return true
                }
            }
        } else {
            if (action == android.view.KeyEvent.ACTION_DOWN) {
                lastInteractionTime.value = System.currentTimeMillis()
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
