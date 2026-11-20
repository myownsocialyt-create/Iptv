package com.example.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class LiveEpgManagerTest {

    @Test
    fun parsesXtreamLiveUrlCredentialsAndSkipsVodMovieUrls() {
        val live = LiveEpgManager.parseXtreamCredentialsFromUrl(
            "http://iptv.example.com:8080/live/john/secret123/4567.ts"
        )
        assertNotNull(live)
        assertEquals("http://iptv.example.com:8080", live!!.serverBaseUrl)
        assertEquals("john", live.username)
        assertEquals("secret123", live.password)
        assertEquals("4567", live.streamId)

        val shortLive = LiveEpgManager.parseXtreamCredentialsFromUrl(
            "https://tv.host.net/alice/pass99/8901.m3u8"
        )
        assertNotNull(shortLive)
        assertEquals("8901", shortLive!!.streamId)

        val movieUrl = LiveEpgManager.parseXtreamCredentialsFromUrl(
            "http://iptv.example.com:8080/movie/john/secret123/4567.mp4"
        )
        assertNull(movieUrl)
    }

    @Test
    fun parsesXtreamShortEpgJsonWithBase64TitlesAndSplitsNowAndUpcoming() {
        val nowSec = 1_760_000_000L
        val nowMs = nowSec * 1000L
        val curTitleB64 = Base64.getEncoder().encodeToString("Evening News Live".toByteArray())
        val nextTitleB64 = Base64.getEncoder().encodeToString("Cricket Highlights".toByteArray())
        val nextDescB64 = Base64.getEncoder().encodeToString("Best moments from today's match".toByteArray())

        val json = """
            {
              "epg_listings": [
                {
                  "title": "${Base64.getEncoder().encodeToString("Past Show".toByteArray())}",
                  "description": "",
                  "start_timestamp": "${nowSec - 7200}",
                  "stop_timestamp": "${nowSec - 3600}"
                },
                {
                  "title": "$curTitleB64",
                  "description": "",
                  "start_timestamp": "${nowSec - 900}",
                  "stop_timestamp": "${nowSec + 900}"
                },
                {
                  "title": "$nextTitleB64",
                  "description": "$nextDescB64",
                  "start_timestamp": "${nowSec + 900}",
                  "stop_timestamp": "${nowSec + 4500}"
                }
              ]
            }
        """.trimIndent()

        val result = LiveEpgManager.parseXtreamEpgJson(json, nowMs)
        assertNotNull(result)
        assertEquals("Evening News Live", result!!.current?.title)
        assertEquals(1, result.upcoming.size)
        assertEquals("Cricket Highlights", result.upcoming[0].title)
        assertEquals("Best moments from today's match", result.upcoming[0].description)
    }

    @Test
    fun extractsM3uHeaderEpgUrlAndMatchesXmltvSchedule() {
        val m3uHeader = """
            #EXTM3U x-tvg-url="https://epg.example.org/guide.xml.gz"
            #EXTINF:-1 tvg-id="StarSports1.in",Star Sports 1 HD (1080p)
        """.trimIndent()
        assertEquals(
            "https://epg.example.org/guide.xml.gz",
            LiveEpgManager.extractEpgUrlFromM3uHeader(m3uHeader)
        )

        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="StarSports1.in">
                <display-name>Star Sports 1 HD</display-name>
              </channel>
              <programme start="20261119120000 +0000" stop="20261119130000 +0000" channel="StarSports1.in">
                <title>Live Match Pre-Show</title>
                <desc>Pitch report and team news</desc>
              </programme>
              <programme start="20261119130000 +0000" stop="20261119160000 +0000" channel="StarSports1.in">
                <title>India vs Australia Live</title>
                <desc>1st ODI</desc>
              </programme>
            </tv>
        """.trimIndent()

        val nowMs = LiveEpgManager.parseXmltvTimestamp("20261119123000 +0000")
        assertTrue(nowMs > 0L)
        val index = LiveEpgManager.parseXmltvStream(ByteArrayInputStream(xml.toByteArray()), nowMs)

        val matched = LiveEpgManager.lookupXmltvChannel(
            index = index,
            channelId = "custom_101",
            channelName = "Star Sports 1 (1080p)",
            nowMs = nowMs
        )
        assertNotNull(matched)
        assertEquals("Live Match Pre-Show", matched!!.current?.title)
        assertEquals(1, matched.upcoming.size)
        assertEquals("India vs Australia Live", matched.upcoming[0].title)
    }
}
