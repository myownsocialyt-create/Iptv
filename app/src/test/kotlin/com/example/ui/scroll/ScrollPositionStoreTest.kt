package com.example.ui.scroll

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScrollPositionStoreTest {

    @Before
    fun setUp() {
        ScrollPositionStore.clearAll()
    }

    @After
    fun tearDown() {
        ScrollPositionStore.clearAll()
    }

    @Test
    fun get_returnsZeroPositionWhenUnseen() {
        val pos = ScrollPositionStore.get("home_screen")
        assertEquals(SavedScrollPosition(0, 0), pos)
    }

    @Test
    fun save_andGet_persistsPositionPerScreenIndependently() {
        ScrollPositionStore.save("home", index = 4, offset = 180, totalItemsCount = 12)
        ScrollPositionStore.save("channels", index = 342, offset = 64, totalItemsCount = 1200)
        ScrollPositionStore.save("categories", index = 9, offset = 40, totalItemsCount = 30)

        assertEquals(SavedScrollPosition(4, 180), ScrollPositionStore.get("home"))
        assertEquals(SavedScrollPosition(342, 64), ScrollPositionStore.get("channels"))
        assertEquals(SavedScrollPosition(9, 40), ScrollPositionStore.get("categories"))
    }

    @Test
    fun save_ignoresTransientEmptyListSoSavedPositionIsPreserved() {
        ScrollPositionStore.save("channels", index = 85, offset = 210, totalItemsCount = 500)

        // Simulates a transient loading frame where list is momentarily empty
        ScrollPositionStore.save("channels", index = 0, offset = 0, totalItemsCount = 0)

        assertEquals(SavedScrollPosition(85, 210), ScrollPositionStore.get("channels"))
    }

    @Test
    fun save_updatesToTopWhenUserActuallyScrollsBackToZeroWithItemsLoaded() {
        ScrollPositionStore.save("home", index = 6, offset = 140, totalItemsCount = 15)

        // User intentionally scrolled back to top while items are loaded
        ScrollPositionStore.save("home", index = 0, offset = 0, totalItemsCount = 15)

        assertEquals(SavedScrollPosition(0, 0), ScrollPositionStore.get("home"))
    }

    @Test
    fun save_coercesNegativeValuesToZero() {
        ScrollPositionStore.save("xtream", index = -3, offset = -50, totalItemsCount = 20)

        assertEquals(SavedScrollPosition(0, 0), ScrollPositionStore.get("xtream"))
    }

    @Test
    fun clampForItemCount_keepsValidPositionUnchanged() {
        val clamped = ScrollPositionStore.clampForItemCount(
            saved = SavedScrollPosition(index = 10, offset = 145),
            totalItemsCount = 50
        )
        assertEquals(SavedScrollPosition(10, 145), clamped)
    }

    @Test
    fun clampForItemCount_clampsIndexAndResetsOffsetWhenListShrinks() {
        val clamped = ScrollPositionStore.clampForItemCount(
            saved = SavedScrollPosition(index = 40, offset = 220),
            totalItemsCount = 12
        )
        assertEquals(SavedScrollPosition(11, 0), clamped)
    }

    @Test
    fun clampForItemCount_returnsZeroForEmptyList() {
        val clamped = ScrollPositionStore.clampForItemCount(
            saved = SavedScrollPosition(index = 10, offset = 145),
            totalItemsCount = 0
        )
        assertEquals(SavedScrollPosition(0, 0), clamped)
    }

    @Test
    fun clear_removesOnlyTargetedKey() {
        ScrollPositionStore.save("a", index = 3, offset = 50, totalItemsCount = 10)
        ScrollPositionStore.save("b", index = 7, offset = 90, totalItemsCount = 10)

        ScrollPositionStore.clear("a")

        assertEquals(SavedScrollPosition(0, 0), ScrollPositionStore.get("a"))
        assertEquals(SavedScrollPosition(7, 90), ScrollPositionStore.get("b"))
    }
}
