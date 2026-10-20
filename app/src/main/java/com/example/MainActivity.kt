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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.OnboardingPreferences
import com.example.data.model.AppThemeMode
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.ChooseLanguageScreen
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.HypnotixTheme
import com.example.ui.viewmodel.IptvViewModel

enum class AppFlowState {
    SPLASH,
    CHOOSE_LANGUAGE,
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

        // Ensure Coil uses the high-speed disk/memory cached ImageLoader from HypnotixApp
        try {
            (application as? HypnotixApp)?.let { app ->
                coil.Coil.setImageLoader(app)
            }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Failed to configure custom Coil ImageLoader", e)
        }

        setContent {
            val context = LocalContext.current
            val viewModel: IptvViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            val isPlaying by viewModel.playerManager.isPlaying.collectAsState()

            val systemInDark = isSystemInDarkTheme()
            val isDarkTheme = when (uiState.currentThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemInDark
            }

            var flowState by remember {
                mutableStateOf(if (isPipModeState.value) AppFlowState.MAIN else AppFlowState.SPLASH)
            }

            // Keep PiP auto-enter capability strictly synced with active stream playback state
            val canEnterPip = uiState.activeChannel != null && uiState.isSplitViewMode && isPlaying
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
                if (flowState == AppFlowState.SPLASH || flowState == AppFlowState.ONBOARDING) {
                    currentWindow.statusBarColor = android.graphics.Color.BLACK
                    currentWindow.navigationBarColor = android.graphics.Color.BLACK
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                } else if (flowState == AppFlowState.CHOOSE_LANGUAGE) {
                    if (isDarkTheme) {
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
                val appStrings = remember(uiState.currentLanguage) {
                    AppStrings.forLanguage(uiState.currentLanguage)
                }
                CompositionLocalProvider(LocalAppStrings provides appStrings) {
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
                                            if (!OnboardingPreferences.isLanguageSetupCompleted(context)) {
                                                flowState = AppFlowState.CHOOSE_LANGUAGE
                                            } else if (!OnboardingPreferences.isOnboardingCompleted(context)) {
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

                            AppFlowState.CHOOSE_LANGUAGE -> {
                                ChooseLanguageScreen(
                                    currentLanguage = uiState.currentLanguage,
                                    onLanguageSelected = { lang ->
                                        viewModel.setLanguage(lang)
                                        OnboardingPreferences.setLanguageSetupCompleted(context, true)
                                        viewModel.completeFirstTimeSetup()
                                        if (!OnboardingPreferences.isOnboardingCompleted(context)) {
                                            flowState = AppFlowState.ONBOARDING
                                        } else {
                                            flowState = AppFlowState.MAIN
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            AppFlowState.ONBOARDING -> {
                                OnboardingScreen(
                                    onFinishOnboarding = {
                                        OnboardingPreferences.setOnboardingCompleted(context, true)
                                        viewModel.completeFirstTimeSetup()
                                        flowState = AppFlowState.MAIN
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            AppFlowState.MAIN -> {
                                MainAppScreen(
                                    viewModel = viewModel,
                                    isDarkTheme = isDarkTheme,
                                    isPipMode = isPipModeState.value,
                                    onToggleTheme = { isDark ->
                                        viewModel.setThemeMode(if (isDark) AppThemeMode.DARK else AppThemeMode.LIGHT)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
        com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
        isPipModeState.value = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            // When returning from PiP mode, immediately check playback state.
            // If stream is not playing, disable auto-enter so pressing Home doesn't re-trigger PiP.
            val viewModel = runCatching { ViewModelProvider(this)[IptvViewModel::class.java] }.getOrNull()
            val isStreamActive = viewModel != null &&
                viewModel.uiState.value.activeChannel != null &&
                viewModel.playerManager.isPlaying.value
            updatePipParams(isStreamActive)
        }
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        com.example.ad.InStreamAdManager.suppressAdsTemporarily(3000L)
        isPipModeState.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        val viewModel = runCatching { ViewModelProvider(this)[IptvViewModel::class.java] }.getOrNull()
        val isVideoPlaying = viewModel != null &&
            viewModel.uiState.value.activeChannel != null &&
            viewModel.uiState.value.isSplitViewMode &&
            viewModel.playerManager.isPlaying.value
        updatePipParams(isVideoPlaying)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter PiP ONLY when leaving app while a video stream is actively playing!
        // If user presses Home or Navigation button without video playing, NEVER enter PiP mode.
        val viewModel = runCatching { ViewModelProvider(this)[IptvViewModel::class.java] }.getOrNull()
        val isVideoPlaying = viewModel != null &&
            viewModel.uiState.value.activeChannel != null &&
            viewModel.uiState.value.isSplitViewMode &&
            viewModel.playerManager.isPlaying.value
        if (isVideoPlaying) {
            enterPipMode()
        } else {
            updatePipParams(false)
        }
    }

    fun updatePipParams(canEnterPip: Boolean) {
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val aspectRatio = Rational(16, 9)
                val paramsBuilder = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    paramsBuilder.setAutoEnterEnabled(canEnterPip)
                    paramsBuilder.setSeamlessResizeEnabled(canEnterPip)
                }
                setPictureInPictureParams(paramsBuilder.build())
            } catch (t: Throwable) {}
        }
    }

    fun enterPipMode() {
        com.example.ad.InStreamAdManager.suppressAdsTemporarily(5000L)
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return
        }
        val viewModel = runCatching { ViewModelProvider(this)[IptvViewModel::class.java] }.getOrNull()
        val isVideoOpen = viewModel != null &&
            viewModel.uiState.value.activeChannel != null &&
            viewModel.uiState.value.isSplitViewMode
        if (!isVideoOpen) {
            // Strictly prevent entering PiP mode when no video/channel is active
            updatePipParams(false)
            return
        }

        isPipModeState.value = true

        // Ensure Compose has drawn the clean pure video player frame before triggering PiP
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val aspectRatio = Rational(16, 9)
                    val paramsBuilder = PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        paramsBuilder.setAutoEnterEnabled(true)
                        paramsBuilder.setSeamlessResizeEnabled(true)
                    }
                    enterPictureInPictureMode(paramsBuilder.build())
                } catch (t: Throwable) {
                    try {
                        @Suppress("DEPRECATION")
                        enterPictureInPictureMode()
                    } catch (e: Throwable) {}
                }
            }
        }
    }
}

