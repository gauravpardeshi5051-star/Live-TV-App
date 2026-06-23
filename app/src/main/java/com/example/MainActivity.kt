package com.example

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveChannel
import com.example.ui.MobileChannelListScreen
import com.example.ui.TvChannelListScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTv = isRunningOnTv(this)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                var showSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(3000) // Beautiful 3-second startup screen
                    showSplash = false
                }

                if (showSplash) {
                    SplashScreen()
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        if (isTv) {
                            TvChannelListScreen(
                                onChannelSelect = { channel ->
                                    playChannel(channel)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            MobileChannelListScreen(
                                onChannelClick = { channel ->
                                    playChannel(channel)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun isRunningOnTv(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun playChannel(channel: LiveChannel) {
        val intent = Intent(this, LivePlayerActivity::class.java).apply {
            putExtra("channel_name", channel.name)
            putExtra("channel_program", channel.currentProgram)
            putExtra("stream_url", channel.streamUrl)
        }
        startActivity(intent)
    }
}

@Composable
fun SplashScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0E1C), // Deep premium dark cyber atmosphere
                        Color(0xFF14152D),
                        Color(0xFF1B1A3A)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant Animated TV Streaming App Logo
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8A2387),
                                Color(0xFFE94057),
                                Color(0xFFF27121)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = "Live IPTV App Icon",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Application Broadcast Name
            Text(
                text = "LIVE IPTV PLAYER",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Premium Television Network & Streaming",
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 1.sp
                ),
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Elegant separation divider line
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            Spacer(modifier = Modifier.height(20.dp))

            // "Developed by" label
            Text(
                text = "Developed by",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 3.sp
                ),
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Primary signature authorship
            Text(
                text = "GAURAV PARDESHI",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 4.sp
                ),
                color = Color(0xFF00D2FF), // Glowing futuristic cyan
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Dynamic loading spinner indication
            CircularProgressIndicator(
                color = Color(0xFF00D2FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
