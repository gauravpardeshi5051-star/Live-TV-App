package com.example

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.model.LiveChannel
import com.example.ui.MobileChannelListScreen
import com.example.ui.TvChannelListScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isTv = isRunningOnTv(this)

        setContent {
            MyApplicationTheme(darkTheme = true) {
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
