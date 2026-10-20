package com.example

import android.app.Application
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.OnboardingPreferences
import com.example.ui.screens.MainAppScreen
import com.example.ui.theme.HypnotixTheme
import com.example.ui.viewmodel.IptvViewModel

class HypnotixApp : Application()

class MainActivity : ComponentActivity() {

    private val isPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = OnboardingPreferences(this)
        val viewModel = ViewModelProvider(this)[IptvViewModel::class.java]

        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(prefs.isDarkTheme()) }
            val isInPip by isPipModeState

            HypnotixTheme(darkTheme = isDarkTheme) {
                MainAppScreen(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { dark ->
                        isDarkTheme = dark
                        prefs.setDarkTheme(dark)
                    },
                    isInPictureInPictureMode = isInPip,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    /**
     * Enters Android Picture-in-Picture (PiP) mode from either the split-screen player
     * or the YouTube-style bottom mini-player.
     */
    fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val viewModel = runCatching {
            ViewModelProvider(this)[IptvViewModel::class.java]
        }.getOrNull() ?: return

        val activeChannel = viewModel.uiState.value.activeChannel
        if (activeChannel == null) {
            updatePipParams(false)
            return
        }

        // Ensure full video player surface is active when entering PiP from the bottom mini-player
        if (!viewModel.uiState.value.isSplitViewMode) {
            viewModel.restoreFromMiniPlayer()
        }

        runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }
            val entered = enterPictureInPictureMode(builder.build())
            if (entered) {
                isPipModeState.value = true
            }
        }
    }

    fun updatePipParams(isStreamActive: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(isStreamActive)
                builder.setSeamlessResizeEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val viewModel = runCatching {
            ViewModelProvider(this)[IptvViewModel::class.java]
        }.getOrNull()
        val hasActiveStream = viewModel?.uiState?.value?.activeChannel != null &&
            viewModel.playerManager.isPlaying.value
        if (hasActiveStream) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }
}
