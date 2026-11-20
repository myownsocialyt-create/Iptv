package com.example.premium

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreUpdatePromptTest {
    private val dayMs = 24L * 60L * 60L * 1000L
    private val fifteenDaysMs = 15L * dayMs

    @Test
    fun latestVersionNeverPrompts() {
        val now = 1_700_000_000_000L
        assertFalse(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 6L,
                availableVersionCode = 6,
                lastShownAtMs = 0L,
                lastShownVersionCode = 0,
                nowMs = now
            )
        )
        assertFalse(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 7L,
                availableVersionCode = 6,
                lastShownAtMs = 0L,
                lastShownVersionCode = 0,
                nowMs = now
            )
        )
    }

    @Test
    fun firstUpdateAvailablePromptsImmediately() {
        val now = 1_700_000_000_000L
        assertTrue(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 6L,
                availableVersionCode = 7,
                lastShownAtMs = 0L,
                lastShownVersionCode = 0,
                nowMs = now
            )
        )
    }

    @Test
    fun repeatsStrictlyAfter15Days() {
        val t0 = 1_700_000_000_000L
        assertFalse(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 6L,
                availableVersionCode = 7,
                lastShownAtMs = t0,
                lastShownVersionCode = 7,
                nowMs = t0 + 14L * dayMs + (23L * 60L * 60L * 1000L)
            )
        )
        assertTrue(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 6L,
                availableVersionCode = 7,
                lastShownAtMs = t0,
                lastShownVersionCode = 7,
                nowMs = t0 + fifteenDaysMs
            )
        )
    }

    @Test
    fun afterUserUpdatesFutureReleasePromptsImmediately() {
        val t0 = 1_700_000_000_000L
        assertTrue(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 7L,
                availableVersionCode = 8,
                lastShownAtMs = t0,
                lastShownVersionCode = 7,
                nowMs = t0 + 2L * dayMs
            )
        )
    }

    @Test
    fun backwardClockDoesNotSpam() {
        val t0 = 1_700_000_000_000L
        assertFalse(
            PlayStoreUpdatePrompt.shouldShowPromptNow(
                currentVersionCode = 6L,
                availableVersionCode = 7,
                lastShownAtMs = t0,
                lastShownVersionCode = 7,
                nowMs = t0 - dayMs
            )
        )
    }
}
