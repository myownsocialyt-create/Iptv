package com.example.player

sealed class PlayerState {
    data object Idle : PlayerState()
    data object Buffering : PlayerState()
    data object Ready : PlayerState()
    data object Ended : PlayerState()
    data class Error(val message: String, val canRetry: Boolean = true) : PlayerState()
}
