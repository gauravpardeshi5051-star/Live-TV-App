package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChannelConfig
import com.example.model.LiveChannel

// Define a premium dark palette
val DeepBackgroundMobile = Color(0xFF0F0F1A)
val CardBackgroundMobile = Color(0xFF16172B)
val PremiumPurpleMobile = Color(0xFF6F3FF5)
val AccentRedMobile = Color(0xFFFF2D55)
val TextSilverMobile = Color(0xFFE2E2E2)
val TextGrayMutedMobile = Color(0xFF8E8E9F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileChannelListScreen(
    onChannelClick: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Live update source channels state
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
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1530), // Deep purple-blue tint at the top
                        DeepBackgroundMobile    // Rich pure dark at the bottom
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Elegant top app bar
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Live Stream TV",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("app_title_text")
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("menu_add_channel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Channel",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Section Header
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Channels",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("section_title_text")
                )
                
                Text(
                    text = "${channelsList.size} Available",
                    color = PremiumPurpleMobile,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Vertical list of channel cards
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 96.dp), // Extra space to make sure FAB doesn't overlay bottom card
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(channelsList, key = { it.number.toString() + "_" + it.name }) { channel ->
                    MobileChannelCard(
                        channel = channel,
                        onClick = { onChannelClick(channel) }
                    )
                }
            }
        }

        // Float Action Button to easily add new streams as specified by user
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = PremiumPurpleMobile,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("mobile_add_fab"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Custom Channel")
        }

        // Dynamic Add Channel Dialog overlay
        if (showAddDialog) {
            AddChannelDialog(
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

@Composable
fun MobileChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .testTag("channel_item_card_${channel.number}")
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackgroundMobile)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Channel Number (Left side)
        Text(
            text = channel.number.toString(),
            color = TextGrayMutedMobile,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(28.dp)
                .testTag("channel_num_${channel.number}")
        )

        // 2. Logo Image (Loaded dynamically using Coil library)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF22243C))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = "${channel.name} Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("channel_logo_${channel.number}"),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // 3. Channel Info (Title & Program)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channel_name_${channel.number}")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Now: ${channel.currentProgram}",
                color = TextGrayMutedMobile,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("channel_prog_${channel.number}")
            )
        }

        // 4. "LIVE" Indicator Badge (Red Pill)
        Box(
            modifier = Modifier
                .align(Alignment.Top)
                .testTag("live_badge_${channel.number}")
                .clip(RoundedCornerShape(4.dp))
                .background(AccentRedMobile)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Mini pulse dot
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Aesthetic Material 3 Dialog to support stream additions on mobile devices
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChannelDialog(
    onDismiss: () -> Unit,
    onAddChannel: (name: String, streamUrl: String, logoUrl: String, programName: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var programName by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Custom Live Channel",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.trim().isNotEmpty()) nameError = false
                    },
                    label = { Text("Channel Name") },
                    isError = nameError,
                    placeholder = { Text("e.g. My Action TV") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumPurpleMobile,
                        focusedLabelColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_dialog_name_field"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (nameError) {
                    Text("Channel name cannot be empty", color = AccentRedMobile, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = {
                        streamUrl = it
                        if (it.trim().isNotEmpty()) urlError = false
                    },
                    label = { Text("Stream URL / IPTV Link") },
                    isError = urlError,
                    placeholder = { Text("https://example.com/live.m3u8") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumPurpleMobile,
                        focusedLabelColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_dialog_url_field"),
                    shape = RoundedCornerShape(12.dp)
                )
                if (urlError) {
                    Text("Stream link cannot be empty", color = AccentRedMobile, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Logo Image URL (Optional)") },
                    placeholder = { Text("https://image.com/logo.png") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumPurpleMobile,
                        focusedLabelColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = programName,
                    onValueChange = { programName = it },
                    label = { Text("Active Program Name (Optional)") },
                    placeholder = { Text("e.g. Premium Live Broadcasting") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PremiumPurpleMobile,
                        focusedLabelColor = Color.White,
                        focusedContainerColor = Color(0xFF1E1F35),
                        unfocusedContainerColor = Color(0xFF1E1F35)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
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
                colors = ButtonDefaults.buttonColors(containerColor = PremiumPurpleMobile),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("add_dialog_confirm_btn")
            ) {
                Text("Add Channel", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.LightGray)
            }
        },
        containerColor = CardBackgroundMobile,
        shape = RoundedCornerShape(20.dp)
    )
}
