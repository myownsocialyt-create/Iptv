package com.example.player

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Connecting : PlaybackState
    data object Buffering : PlaybackState
    data object Playing : PlaybackState
    data class Error(val message: String) : PlaybackState
}

data class VideoQualityOption(
    val height: Int,
    val bitrate: Long,
    val label: String
)

enum class AspectRatioMode(val label: String) {
    FIT_16_9("16:9"),
    FILL("Fill"),
    ORIGINAL("Original"),
    ZOOM("Zoom"),
    FOUR_THREE("4:3"),
    FILL_CROP("Crop")
}
