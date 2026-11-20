package com.example.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChannelCardHookTest {

    @Test
    fun xtreamVodMovieFlagShowsMovieBadge() {
        val isVod = HomeChannelCardHook.isMovieOrVodChannel(
            isMovieOrVodFlag = true,
            id = "12345",
            streamUrl = "http://server:8080/movie/user/pass/12345.mp4",
            category = "Action"
        )
        assertTrue(isVod)
        assertEquals("MOVIE", HomeChannelCardHook.resolveBadgeLabel(isVod))
    }

    @Test
    fun savedRecentOrFavoriteVodMovieDetectedEvenWithoutFlag() {
        assertTrue(
            HomeChannelCardHook.isMovieOrVodChannel(
                isMovieOrVodFlag = false,
                id = "vod_991",
                streamUrl = "https://example.com/stream",
                category = "Action"
            )
        )
        assertTrue(
            HomeChannelCardHook.isMovieOrVodChannel(
                isMovieOrVodFlag = false,
                id = "custom_1",
                streamUrl = "http://host/movie/user/pass/777.mkv?token=abc",
                category = "Cinema"
            )
        )
        assertTrue(
            HomeChannelCardHook.isMovieOrVodChannel(
                isMovieOrVodFlag = false,
                id = "spiderman_1",
                streamUrl = "https://cdn.example.com/playlist.m3u8",
                category = "VOD Movies"
            )
        )
    }

    @Test
    fun xtreamLiveChannelAndM3uMoviesLiveChannelShowLiveBadge() {
        val xtreamLive = HomeChannelCardHook.isMovieOrVodChannel(
            isMovieOrVodFlag = false,
            id = "xtream_live_42",
            streamUrl = "http://server:8080/live/user/pass/42.ts",
            category = "Sports"
        )
        assertFalse(xtreamLive)
        assertEquals("• LIVE", HomeChannelCardHook.resolveBadgeLabel(xtreamLive))

        val liveMovieChannel = HomeChannelCardHook.isMovieOrVodChannel(
            isMovieOrVodFlag = false,
            id = "m3u_sony_max",
            streamUrl = "https://stream.example.com/sonymax/index.m3u8",
            category = "Movies"
        )
        assertFalse(liveMovieChannel)
        assertEquals("• LIVE", HomeChannelCardHook.resolveBadgeLabel(liveMovieChannel))
    }
}
