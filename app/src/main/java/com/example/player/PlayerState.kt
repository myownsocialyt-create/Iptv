package com.example.player

sealed class PlayerState {
    data object Idle : PlayerState()
    data object Connecting : PlayerState()
    data object Buffering : PlayerState()
    data object Ready : PlayerState()
    data object Ended : PlayerState()
    data class Error(val message: String, val canRetry: Boolean = true) : PlayerState()
}

typealias PlaybackState = PlayerState

enum class AspectRatioMode(val label: String) {
    FIT_16_9("16:9"),
    FILL("Full"),
    ZOOM("Zoom"),
    FOUR_THREE("4:3"),
    ORIGINAL("Fit"),
    FILL_CROP("Crop")
}

data class VideoQuality(
    val id: String,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0
)
