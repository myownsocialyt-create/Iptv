package com.example.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridCastManagerTest {

    @Test
    fun stripPipeSuffix_removesPipeHeadersAndTrims() {
        val raw = "  https://cdn.example.com/live/ch1.m3u8|User-Agent=VLC/3.0&Referer=https://site.test  "
        assertEquals(
            "https://cdn.example.com/live/ch1.m3u8",
            HybridCastManager.stripPipeSuffix(raw)
        )
        assertEquals(
            "http://example.com/stream.ts",
            HybridCastManager.stripPipeSuffix("http://example.com/stream.ts")
        )
    }

    @Test
    fun maskXtreamCredentials_masksLiveAndMovieUrlsOnly() {
        assertEquals(
            "http://iptv.example.com:8080/live/***/***/12345.m3u8",
            HybridCastManager.maskXtreamCredentials("http://iptv.example.com:8080/live/alice/secret99/12345.m3u8")
        )
        assertEquals(
            "https://iptv.example.com/movie/***/***/67890.mp4",
            HybridCastManager.maskXtreamCredentials("https://iptv.example.com/movie/bob_1/p@ss2/67890.mp4")
        )
        assertEquals(
            "https://cdn.example.com/hls/live/master.m3u8",
            HybridCastManager.maskXtreamCredentials("https://cdn.example.com/hls/live/master.m3u8")
        )
    }

    @Test
    fun webAuthToken_isSixCharsAndValidatedAcrossRoutes() {
        val token1 = HybridCastManager.generateWebAuthToken()
        val token2 = HybridCastManager.generateWebAuthToken()
        assertEquals(6, token1.length)
        assertEquals(6, token2.length)
        assertTrue(token1.all { it.isLetterOrDigit() })
        assertNotEquals(token1, token2)

        val serverUrl = HybridCastManager.buildWebServerUrl("192.168.1.25", 8989, token1)
        assertEquals("http://192.168.1.25:8989/?t=$token1", serverUrl)

        assertTrue(HybridCastManager.isValidWebRequestToken("/?t=$token1", token1))
        assertTrue(HybridCastManager.isValidWebRequestToken("/api/state?t=$token1&_=12345", token1))
        assertTrue(HybridCastManager.isValidWebRequestToken("/proxy?t=$token1&url=https%3A%2F%2Fa.test%2Fs.m3u8", token1))

        assertFalse(HybridCastManager.isValidWebRequestToken("/", token1))
        assertFalse(HybridCastManager.isValidWebRequestToken("/api/state", token1))
        assertFalse(HybridCastManager.isValidWebRequestToken("/proxy?url=https%3A%2F%2Fa.test%2Fs.m3u8", token1))
        assertFalse(HybridCastManager.isValidWebRequestToken("/api/state?t=WRONG1", token1))
    }

    @Test
    fun proxyAllowlist_allowsStreamHostAndRedirectHostOnly() {
        HybridCastManager.resetAllowedProxyHosts("https://origin.stream.test/live/ch.m3u8|User-Agent=Test")

        assertTrue(HybridCastManager.isAllowedProxyUrl("https://origin.stream.test/live/seg01.ts"))
        assertTrue(HybridCastManager.isAllowedProxyUrl("http://ORIGIN.STREAM.TEST:8080/key.bin"))
        assertFalse(HybridCastManager.isAllowedProxyUrl("https://evil.other.test/secret"))
        assertFalse(HybridCastManager.isAllowedProxyUrl("file:///etc/hosts"))
        assertFalse(HybridCastManager.isAllowedProxyUrl("ftp://origin.stream.test/file"))

        HybridCastManager.allowProxyHostFromUrl("https://cdn-edge.stream.test/hls/seg01.ts")
        assertTrue(HybridCastManager.isAllowedProxyUrl("https://cdn-edge.stream.test/hls/seg02.ts"))

        HybridCastManager.resetAllowedProxyHosts("https://new-origin.test/stream.m3u8")
        assertFalse(HybridCastManager.isAllowedProxyUrl("https://origin.stream.test/live/seg01.ts"))
        assertTrue(HybridCastManager.isAllowedProxyUrl("https://new-origin.test/seg01.ts"))
    }

    @Test
    fun selectPreferredLanIp_prefersWlan0AndAp0AndIgnoresRmnet() {
        val withWifiAndCellular = listOf(
            HybridCastManager.NetworkIpCandidate("rmnet_data0", "10.22.33.44"),
            HybridCastManager.NetworkIpCandidate("eth0", "192.168.0.5"),
            HybridCastManager.NetworkIpCandidate("ap0", "192.168.43.1"),
            HybridCastManager.NetworkIpCandidate("wlan0", "192.168.1.42")
        )
        assertEquals("192.168.1.42", HybridCastManager.selectPreferredLanIp(withWifiAndCellular))

        val withHotspotAndCellular = listOf(
            HybridCastManager.NetworkIpCandidate("rmnet0", "10.1.2.3"),
            HybridCastManager.NetworkIpCandidate("ap0", "192.168.43.1")
        )
        assertEquals("192.168.43.1", HybridCastManager.selectPreferredLanIp(withHotspotAndCellular))

        val cellularOnly = listOf(
            HybridCastManager.NetworkIpCandidate("rmnet_data0", "10.10.10.10"),
            HybridCastManager.NetworkIpCandidate("rmnet_data1", "172.16.0.5")
        )
        assertNull(HybridCastManager.selectPreferredLanIp(cellularOnly))
    }
}
