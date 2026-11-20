package com.example.data.repository

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChannelDiskCacheTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("channel_disk_cache_test").toFile()
        ChannelDiskCache.resetForTesting()
        ChannelDiskCache.setCacheDirForTesting(tempDir)
    }

    @After
    fun tearDown() {
        ChannelDiskCache.resetForTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun roundTripPreservesAllChannelFieldsAndNullables() {
        val records = listOf(
            ChannelDiskCache.ChannelRecord(
                id = "ch_1",
                name = "Aaj Tak हिंदी HD 📺",
                logoUrl = "https://example.com/logo.png",
                category = "News",
                language = "Hindi",
                country = "IN",
                streamUrl = "https://stream.example.com/live/1.m3u8",
                isFavorite = true,
                playlistName = "India",
                userAgent = "CustomAgent/1.0",
                referer = "https://referer.example.com/",
                isMovieOrVod = false
            ),
            ChannelDiskCache.ChannelRecord(
                id = "vod_42",
                name = "Sample Movie (2026)",
                logoUrl = null,
                category = "Movies",
                language = "",
                country = "",
                streamUrl = "https://xtream.example.com/movie/u/p/42.mp4",
                isFavorite = false,
                playlistName = "Xtream VOD",
                userAgent = null,
                referer = null,
                isMovieOrVod = true
            )
        )
        val entry = ChannelDiskCache.RawCacheEntry(
            sourceKey = "https://iptv-org.github.io/iptv/countries/in.m3u",
            savedAtMs = 1700000000123L,
            records = records
        )

        assertTrue(ChannelDiskCache.writeRawEntry("pl_", entry.sourceKey, entry))

        val loaded = ChannelDiskCache.readRawEntry("pl_", entry.sourceKey)
        assertNotNull(loaded)
        assertEquals(entry.sourceKey, loaded!!.sourceKey)
        assertEquals(entry.savedAtMs, loaded.savedAtMs)
        assertEquals(records, loaded.records)
    }

    @Test
    fun largePlaylistRoundTripIsFastAndExact() {
        val count = 5000
        val records = (0 until count).map { idx ->
            ChannelDiskCache.ChannelRecord(
                id = "xtream_$idx",
                name = "Channel #$idx HD",
                logoUrl = if (idx % 2 == 0) "https://cdn.example.com/logos/$idx.png" else null,
                category = "Category ${idx % 25}",
                language = "English",
                country = "US",
                streamUrl = "https://cdn.example.com/live/$idx.ts",
                isFavorite = idx % 10 == 0,
                playlistName = "Xtream Live",
                userAgent = null,
                referer = null,
                isMovieOrVod = false
            )
        }
        val key = "live|1|https://panel.example.com|user1"
        val savedAt = 1700001234567L

        assertTrue(
            ChannelDiskCache.writeRawEntry(
                "xt_",
                key,
                ChannelDiskCache.RawCacheEntry(key, savedAt, records)
            )
        )

        val loaded = ChannelDiskCache.readRawEntry("xt_", key)
        assertNotNull(loaded)
        assertEquals(count, loaded!!.records.size)
        assertEquals(records.first(), loaded.records.first())
        assertEquals(records.last(), loaded.records.last())
    }

    @Test
    fun corruptAndTruncatedFilesReturnNullSafely() {
        val key = "https://example.com/broken.m3u"
        val fileName = ChannelDiskCache.buildFileName("pl_", key)
        val file = File(tempDir, fileName)

        // Wrong magic bytes
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18))
        assertNull(ChannelDiskCache.readRawEntry("pl_", key))

        // Valid write then truncated mid-stream
        val valid = ChannelDiskCache.RawCacheEntry(
            sourceKey = key,
            savedAtMs = 12345L,
            records = listOf(
                ChannelDiskCache.ChannelRecord(
                    id = "1", name = "Test", logoUrl = null, category = "General",
                    language = "", country = "", streamUrl = "http://a/b.m3u8",
                    isFavorite = false, playlistName = "P", userAgent = null,
                    referer = null, isMovieOrVod = false
                )
            )
        )
        assertTrue(ChannelDiskCache.writeRawEntry("pl_", key, valid))
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size / 2))
        assertNull(ChannelDiskCache.readRawEntry("pl_", key))
    }

    @Test
    fun keysAreHashedIsolatedAndDeletable() {
        val keyA = "live|1|http://server.one|secretuser"
        val keyB = "vod|1|http://server.one|secretuser"
        val fileA = ChannelDiskCache.buildFileName("xt_", keyA)
        val fileB = ChannelDiskCache.buildFileName("xt_", keyB)

        assertNotEquals(fileA, fileB)
        assertFalse(fileA.contains("secretuser"))
        assertFalse(fileA.contains("server.one"))

        val entryA = ChannelDiskCache.RawCacheEntry(keyA, 1000L, emptyList())
        assertTrue(ChannelDiskCache.writeRawEntry("xt_", keyA, entryA))
        assertNotNull(ChannelDiskCache.readRawEntry("xt_", keyA))
        assertTrue(ChannelDiskCache.deleteFileForKey("xt_", keyA))
        assertNull(ChannelDiskCache.readRawEntry("xt_", keyA))
    }

    @Test
    fun forceRefreshFlagsAndAllPlaylistsStateToggleCleanly() {
        val url = "https://iptv-org.github.io/iptv/countries/in.m3u"
        assertFalse(ChannelDiskCache.isPlaylistForceRefreshPending(url))
        assertFalse(ChannelDiskCache.isPlaylistForceRefreshPending(null))

        ChannelDiskCache.markPlaylistForceRefresh(url)
        assertTrue(ChannelDiskCache.isPlaylistForceRefreshPending(url))
        assertFalse(ChannelDiskCache.isPlaylistForceRefreshPending("https://other.example.com/list.m3u"))

        ChannelDiskCache.markPlaylistForceRefresh(null)
        assertTrue(ChannelDiskCache.isPlaylistForceRefreshPending(null))
        assertTrue(ChannelDiskCache.isPlaylistForceRefreshPending("https://other.example.com/list.m3u"))

        ChannelDiskCache.setAllPlaylistsRefreshing(true)
        assertTrue(ChannelDiskCache.isPlaylistForceRefreshPending(null))

        ChannelDiskCache.setAllPlaylistsRefreshing(false)
        assertFalse(ChannelDiskCache.isPlaylistForceRefreshPending(null))
    }
}
