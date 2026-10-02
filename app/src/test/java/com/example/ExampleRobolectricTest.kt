package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FavoriteChannelEntity
import com.example.data.local.IptvDatabase
import com.example.data.repository.M3uParser
import com.example.data.repository.PresetPlaylists
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class ExampleRobolectricTest {

    private lateinit var db: IptvDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, IptvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

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
        assertEquals("Aaj Tak Live HD", channel.name)
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
        assertEquals("News", sampleIndia.category)
    }

    @Test
    fun roomDatabaseInsertsAndQueriesFavorites() = runBlocking {
        val dao = db.channelDao()
        val fav = FavoriteChannelEntity(
            streamUrl = "https://example.com/dd_news.m3u8",
            channelId = "dd_news",
            name = "DD News",
            logoUrl = null,
            category = "News"
        )
        dao.insertFavorite(fav)

        val favorites = dao.getAllFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("DD News", favorites[0].name)
        assertTrue(dao.isFavorite("https://example.com/dd_news.m3u8").first())

        dao.deleteFavorite("https://example.com/dd_news.m3u8")
        assertFalse(dao.isFavorite("https://example.com/dd_news.m3u8").first())
    }
}
