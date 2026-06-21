package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.model.ChannelConfig
import com.example.model.LiveChannel

// Theme Colors matching premium dark aesthetics
val TVDeepBack = Color(0xFF0A0A14)
val TVCardBack = Color(0xFF131427)
val TVAccentPurple = Color(0xFF7B4DFF)
val TVLiveRed = Color(0xFFFF2B55)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelListScreen(
    onChannelSelect: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var channelsList by remember { mutableStateOf(emptyList<LiveChannel>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Load initial channels on launch
    LaunchedEffect(Unit) {
        channelsList = ChannelConfig.loadChannels(context)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1F1D36), // Cozy vibrant core
                        TVDeepBack        // Rich dark border
                    )
                )
            )
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Elegant Row containing Header and a focusable top button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live IPTV Player",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.testTag("tv_header_title")
                    )
                    Text(
                        text = "Choose a live channel using your remote D-pad controls",
                        color = Color(0xFF9E9EB8),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("tv_header_desc")
                    )
                }

                // Focusable TV Add Icon Button (D-Pad navigates here flawlessly!)
                TvAddButton(
                    onClick = { showAddDialog = true }
                )
            }

            // D-Pad focus-optimized scrolling list
            TvLazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tv_channel_list")
            ) {
                items(channelsList, key = { it.number.toString() + "_" + it.name }) { channel ->
                    TvChannelRowItem(
                        channel = channel,
                        onSelect = { onChannelSelect(channel) },
                        onDelete = {
                            channelsList = ChannelConfig.deleteChannel(context, channel.streamUrl)
                        }
                    )
                }
            }
        }

        // TV-Style Custom Add Dialog
        if (showAddDialog) {
            TvAddChannelDialog(
                onDismiss = { showAddDialog = false },
                onAddChannel = { name, sourceUrl, iconUrl, currentProg ->
                    channelsList = ChannelConfig.addChannel(
                        context = context,
                        name = name,
                        streamUrl = sourceUrl,
                        logoUrl = iconUrl,
                        currentProgram = currentProg
                    )
                    showAddDialog = false
                }
            )
        }
    }
}

/**
 * Custom focus-optimized header Add Button for Smart TVs
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAddButton(
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f)

    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(50.dp)
            .width(220.dp)
            .graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0xFF33355A),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("tv_header_add_btn"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF1D1B2D),
            focusedContainerColor = TVAccentPurple
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Custom Channel",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Custom Stream",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvChannelRowItem(
    channel: LiveChannel,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Focusable Channel Card
        var isCardFocused by remember { mutableStateOf(false) }
        val cardScale by animateFloatAsState(targetValue = if (isCardFocused) 1.03f else 1.00f)
        val cardBorderColor = if (isCardFocused) Color.White else Color.Transparent

        Surface(
            onClick = onSelect,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .onFocusChanged { isCardFocused = it.isFocused }
                .border(
                    width = if (isCardFocused) 3.dp else 1.dp,
                    color = if (isCardFocused) Color.White else Color(0xFF2E2E4D),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("tv_channel_card_${channel.number}"),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TVCardBack,
                focusedContainerColor = TVCardBack.copy(alpha = 0.85f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Large Channel Number indicator
                Text(
                    text = String.format("%02d", channel.number),
                    color = if (isCardFocused) TVAccentPurple else Color(0xFF636383),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .width(48.dp)
                        .testTag("tv_channel_num_${channel.number}")
                )

                // 2. Beautiful Square/Rect Icon Box
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF22243C))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // 3. Information details
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("tv_channel_name_${channel.number}")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Small "Now Playing" pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF2E2E4D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PROGRAM",
                                color = Color(0xFFA6A6C7),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = channel.currentProgram,
                            color = Color(0xFF9191B0),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("tv_channel_prog_${channel.number}")
                        )
                    }
                }

                // 4. "LIVE" Indicator Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(TVLiveRed)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        )
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // TV-Style Focusable Trash-bin Button
        var isDeleteFocused by remember { mutableStateOf(false) }
        val deleteScale by animateFloatAsState(targetValue = if (isDeleteFocused) 1.08f else 1.00f)

        Surface(
            onClick = onDelete,
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier
                .size(108.dp)
                .graphicsLayer {
                    scaleX = deleteScale
                    scaleY = deleteScale
                }
                .onFocusChanged { isDeleteFocused = it.isFocused }
                .border(
                    width = if (isDeleteFocused) 3.dp else 1.dp,
                    color = if (isDeleteFocused) Color.White else Color(0xFF3E2235),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("tv_channel_delete_${channel.number}"),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF2C1E27),
                focusedContainerColor = Color(0xFF4A1A24)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Channel",
                    tint = if (isDeleteFocused) Color.White else Color(0xFFFF5252),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * TV Focusable custom Dialog overlay
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvAddChannelDialog(
    onDismiss: () -> Unit,
    onAddChannel: (name: String, streamUrl: String, logoUrl: String, programName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var programName by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Channel",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.trim().isNotEmpty()) nameError = false
                    },
                    label = { androidx.compose.material3.Text("Channel Name", color = Color.LightGray) },
                    isError = nameError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TVAccentPurple,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (nameError) {
                    androidx.compose.material3.Text("Channel name cannot be empty", color = TVLiveRed, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        if (it.trim().isNotEmpty()) urlError = false
                    },
                    label = { androidx.compose.material3.Text("Stream URL / IPTV m3u8 Link", color = Color.LightGray) },
                    isError = urlError,
                    placeholder = { androidx.compose.material3.Text("http://...", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TVAccentPurple,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                if (urlError) {
                    androidx.compose.material3.Text("Stream link cannot be empty", color = TVLiveRed, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { androidx.compose.material3.Text("Logo Image URL (Optional)", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TVAccentPurple,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = programName,
                    onValueChange = { programName = it },
                    label = { androidx.compose.material3.Text("Active Program Name (Optional)", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TVAccentPurple,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            // TV surface click trigger
            var btnFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = {
                    if (name.trim().isEmpty()) {
                        nameError = true
                    }
                    if (streamUrl.trim().isEmpty()) {
                        urlError = true
                    }
                    if (name.trim().isNotEmpty() && streamUrl.trim().isNotEmpty()) {
                        onAddChannel(name, streamUrl, logoUrl, programName)
                    }
                },
                modifier = Modifier
                    .onFocusChanged { btnFocused = it.isFocused }
                    .border(
                        width = if (btnFocused) 2.dp else 0.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = TVAccentPurple,
                    focusedContainerColor = TVAccentPurple
                )
            ) {
                Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Add Channel",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            var cancelFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .onFocusChanged { cancelFocused = it.isFocused }
                    .border(
                        width = if (cancelFocused) 2.dp else 0.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .height(44.dp)
                    .padding(horizontal = 16.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    focusedContainerColor = Color(0xFF2B2B47)
                )
            ) {
                Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        containerColor = TVCardBack,
        shape = RoundedCornerShape(20.dp)
    )
}
