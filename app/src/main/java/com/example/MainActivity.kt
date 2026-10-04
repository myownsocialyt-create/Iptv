package com.example

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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

    private val isPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Mobile Ads SDK and back-button protection
        com.example.ad.AdManager.initialize(application)

        // Configure Coil globally with redirect handling and IPTV User-Agent
        try {
            val imageLoader = coil.ImageLoader.Builder(applicationContext)
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .addInterceptor { chain ->
                            val original = chain.request()
                            val request = original.newBuilder()
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 Hypnotix/1.0")
                                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                                .build()
                            chain.proceed(request)
                        }
                        .build()
                }
                .crossfade(true)
                .respectCacheHeaders(false)
                .build()
            coil.Coil.setImageLoader(imageLoader)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to configure custom Coil ImageLoader", e)
        }

        setContent {
            val context = LocalContext.current
            val viewModel: IptvViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
            val onboardingPrefs = remember { OnboardingPreferences(context) }
            var isDarkTheme by remember { mutableStateOf(true) }

            var flowState by remember {
                mutableStateOf(if (isPipModeState.value) AppFlowState.MAIN else AppFlowState.SPLASH)
            }

            // Keep PiP auto-enter capability strictly synced with active stream playback state
            val canEnterPip = uiState.activeChannel != null && isPlaying
            LaunchedEffect(canEnterPip) {
                updatePipParams(canEnterPip)
            }

            LaunchedEffect(isPipModeState.value) {
                if (isPipModeState.value) {
                    flowState = AppFlowState.MAIN
                }
            }

            // Sync status bar & navigation bar seamlessly with system themes and flow state
            LaunchedEffect(flowState, isDarkTheme) {
                val currentWindow = window ?: return@LaunchedEffect
                val insetsController = WindowCompat.getInsetsController(currentWindow, currentWindow.decorView)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    currentWindow.isNavigationBarContrastEnforced = false
                }
                if (flowState == AppFlowState.SPLASH) {
                    currentWindow.statusBarColor = android.graphics.Color.parseColor("#07090E")
                    currentWindow.navigationBarColor = android.graphics.Color.parseColor("#07090E")
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                } else if (isDarkTheme) {
                    currentWindow.statusBarColor = android.graphics.Color.BLACK
                    currentWindow.navigationBarColor = android.graphics.Color.BLACK
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                } else {
                    currentWindow.statusBarColor = android.graphics.Color.WHITE
                    currentWindow.navigationBarColor = android.graphics.Color.WHITE
                    insetsController.isAppearanceLightStatusBars = true
                    insetsController.isAppearanceLightNavigationBars = true
                }
            }

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
                                    com.example.ad.AdManager.showAppOpenAdIfEligible(this@MainActivity) {
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeState.value = isInPictureInPictureMode
    }

    private fun updatePipParams(canEnter: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setAutoEnterEnabled(canEnter)
                }

                setPictureInPictureParams(paramsBuilder.build())
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to update PiP parameters", e)
            }
        }
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Failed to manually enter PiP mode", e)
            }
        }
    }
}
