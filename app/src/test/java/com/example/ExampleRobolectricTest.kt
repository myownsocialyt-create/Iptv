package com.example

import com.example.data.repository.M3uParser
import com.example.data.repository.PresetPlaylists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExampleRobolectricTest {

    @Test
    fun m3uParserCorrectlyParsesExtinfLines() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="aajtak" tvg-name="Aaj Tak" tvg-logo="https://example.com/logo.png" group-title="News",Aaj Tak Live HD
            https://example.com/live.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(ByteArrayInputStream(sampleM3u.toByteArray()))
        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("Aaj Tak", channel.name)
        assertEquals("News", channel.category)
        assertEquals("https://example.com/live.m3u8", channel.streamUrl)
        assertEquals("https://example.com/logo.png", channel.logoUrl)
    }

    @Test
    fun presetPlaylistsHaveVerifiedFallbackChannels() {
        assertTrue(PresetPlaylists.FALLBACK_INDIA_CHANNELS.isNotEmpty())
        assertTrue(PresetPlaylists.FALLBACK_GLOBAL_CHANNELS.isNotEmpty())

        val sampleIndia = PresetPlaylists.FALLBACK_INDIA_CHANNELS.first()
        assertNotNull(sampleIndia.streamUrl)
        assertTrue(sampleIndia.streamUrl.startsWith("http"))
        assertEquals("Movies", sampleIndia.category)
    }
}
