package com.example

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ad.AdManager
import com.example.data.local.OnboardingPreferences
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HypnotixTheme
import com.example.ui.viewmodel.IptvViewModel

enum class AppFlowState {
    SPLASH,
    ONBOARDING,
    MAIN
}

class MainActivity : ComponentActivity() {

    private val viewModel: IptvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads SDK and back-button protection
        AdManager.initialize(application)

        setContent {
            val context = LocalContext.current
            val onboardingPrefs = remember { OnboardingPreferences(context) }

            var flowState by remember {
                mutableStateOf(if (onboardingPrefs.hasCompletedOnboarding) AppFlowState.SPLASH else AppFlowState.ONBOARDING)
            }
            var isDarkTheme by remember { mutableStateOf(false) }

            HypnotixTheme(darkTheme = isDarkTheme) {
                Crossfade(
                    targetState = flowState,
                    animationSpec = tween(400),
                    label = "app_flow_crossfade"
                ) { state ->
                    when (state) {
                        AppFlowState.SPLASH -> {
                            SplashScreen(
                                onSplashFinished = {
                                    val proceedToNext = {
                                        if (!onboardingPrefs.hasCompletedOnboarding) {
                                            flowState = AppFlowState.ONBOARDING
                                        } else {
                                            flowState = AppFlowState.MAIN
                                        }
                                    }
                                    AdManager.showAppOpenAdIfEligible(this@MainActivity) {
                                        proceedToNext()
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        AppFlowState.ONBOARDING -> {
                            OnboardingScreen(
                                onFinishOnboarding = {
                                    onboardingPrefs.hasCompletedOnboarding = true
                                    flowState = AppFlowState.MAIN
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        AppFlowState.MAIN -> {
                            MainAppScreen(
                                viewModel = viewModel,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = { isDarkTheme = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (viewModel.uiState.value.activeChannel != null && viewModel.playerManager.isPlaying.value) {
            enterPictureInPicture()
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }
}
