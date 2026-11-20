package com.example.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VodPlaybackSpeedControllerTest {
    @Test
    fun speedOptionsAndFormattingMatchSpec() {
        assertEquals(listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f), VodPlaybackSpeedController.SPEED_OPTIONS)
        assertEquals("0.5x", VodPlaybackSpeedController.formatSpeedLabel(0.5f))
        assertEquals("1x", VodPlaybackSpeedController.formatSpeedLabel(1.0f))
        assertEquals("1.25x", VodPlaybackSpeedController.formatSpeedLabel(1.25f))
        assertEquals("1.5x", VodPlaybackSpeedController.formatSpeedLabel(1.5f))
        assertEquals("2x", VodPlaybackSpeedController.formatSpeedLabel(2.0f))
    }

    @Test
    fun sanitizeSpeedClampsToNearestAllowedOption() {
        assertEquals(0.5f, VodPlaybackSpeedController.sanitizeSpeed(0.1f))
        assertEquals(1.0f, VodPlaybackSpeedController.sanitizeSpeed(1.0f))
        assertEquals(1.25f, VodPlaybackSpeedController.sanitizeSpeed(1.2f))
        assertEquals(1.5f, VodPlaybackSpeedController.sanitizeSpeed(1.6f))
        assertEquals(2.0f, VodPlaybackSpeedController.sanitizeSpeed(5.0f))
    }

    @Test
    fun formatPlayerDurationHandlesHoursMinutesSeconds() {
        assertEquals("00:00", VodPlaybackSpeedController.formatPlayerDuration(0L))
        assertEquals("00:00", VodPlaybackSpeedController.formatPlayerDuration(-5000L))
        assertEquals("01:05", VodPlaybackSpeedController.formatPlayerDuration(65_000L))
        assertEquals("1:02:03", VodPlaybackSpeedController.formatPlayerDuration(3_723_000L))
    }

    @Test
    fun formatLiveBehindLabelFormatsLiveEdgeSecondsMinutesAndHours() {
        assertEquals("0:00 (LIVE)", VodPlaybackSpeedController.formatLiveBehindLabel(0))
        assertEquals("0:00 (LIVE)", VodPlaybackSpeedController.formatLiveBehindLabel(-3))
        assertEquals("-0:10 (-10s)", VodPlaybackSpeedController.formatLiveBehindLabel(10))
        assertEquals("-0:59 (-59s)", VodPlaybackSpeedController.formatLiveBehindLabel(59))
        assertEquals("-1:00", VodPlaybackSpeedController.formatLiveBehindLabel(60))
        assertEquals("-2:15", VodPlaybackSpeedController.formatLiveBehindLabel(135))
        assertEquals("-1:02:05", VodPlaybackSpeedController.formatLiveBehindLabel(3725))
    }

    @Test
    fun liveTimelineSyncsRedBarWithRealDvrWindowAndLiveEdge() {
        // Within live edge tolerance -> 0s behind and full red line (1.0f)
        val atEdge = VodPlaybackSpeedController.computeLiveTimelineState(
            isScrubbing = false,
            currentPositionMs = 298_500L,
            durationMs = 300_000L,
            manualFraction = 0.5f
        )
        assertEquals(0, atEdge.behindSeconds)
        assertEquals(1.0f, atEdge.progress, 0.0001f)
        assertEquals("0:00 (LIVE)", atEdge.label)

        // 90 seconds behind a 300s DVR window -> progress = 0.70f, label = "-1:30"
        val behindDvr = VodPlaybackSpeedController.computeLiveTimelineState(
            isScrubbing = false,
            currentPositionMs = 210_000L,
            durationMs = 300_000L,
            manualFraction = 1.0f
        )
        assertEquals(90, behindDvr.behindSeconds)
        assertEquals(0.70f, behindDvr.progress, 0.0001f)
        assertEquals("-1:30", behindDvr.label)

        // Fallback 30s live window (no DVR duration) synced with manual/seekBy fraction
        val behindManual = VodPlaybackSpeedController.computeLiveTimelineState(
            isScrubbing = false,
            currentPositionMs = 0L,
            durationMs = 0L,
            manualFraction = 2f / 3f
        )
        assertEquals(10, behindManual.behindSeconds)
        assertEquals(2f / 3f, behindManual.progress, 0.001f)
        assertEquals("-0:10 (-10s)", behindManual.label)

        // Scrubbing near right edge snaps to 0:00 (LIVE) and full red line
        val scrubbedLive = VodPlaybackSpeedController.computeLiveTimelineState(
            isScrubbing = true,
            currentPositionMs = 100_000L,
            durationMs = 300_000L,
            manualFraction = 0.98f
        )
        assertEquals(0, scrubbedLive.behindSeconds)
        assertEquals(1.0f, scrubbedLive.progress, 0.0001f)
        assertEquals("0:00 (LIVE)", scrubbedLive.label)
    }
}
