package com.example.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap

/**
 * Immutable snapshot of a list/grid scroll position.
 */
data class SavedScrollPosition(
    val index: Int = 0,
    val offset: Int = 0
)

private data class ScrollSnapshot(
    val index: Int,
    val offset: Int,
    val totalItems: Int,
    val isScrolling: Boolean
)

/**
 * Process-scoped in-memory scroll position registry that preserves vertical scroll
 * positions across screen switches, channel opens, and Back navigation.
 */
object ScrollPositionStore {
    private val positions = ConcurrentHashMap<String, SavedScrollPosition>()

    fun get(key: String): SavedScrollPosition {
        return positions[key] ?: SavedScrollPosition()
    }

    /**
     * Persists the latest scroll position whenever items are actually present.
     * Transient empty frames (totalItemsCount <= 0) never wipe a previously saved position.
     */
    fun save(key: String, index: Int, offset: Int, totalItemsCount: Int) {
        if (totalItemsCount <= 0) return
        positions[key] = SavedScrollPosition(
            index = index.coerceAtLeast(0),
            offset = offset.coerceAtLeast(0)
        )
    }

    /**
     * Safely clamps a saved position to the currently available item count so
     * scroll restoration can never go out of bounds.
     */
    fun clampForItemCount(saved: SavedScrollPosition, totalItemsCount: Int): SavedScrollPosition {
        if (totalItemsCount <= 0) return SavedScrollPosition(0, 0)
        val clampedIndex = saved.index.coerceIn(0, totalItemsCount - 1)
        val clampedOffset = if (clampedIndex == saved.index) saved.offset.coerceAtLeast(0) else 0
        return SavedScrollPosition(clampedIndex, clampedOffset)
    }

    fun clear(key: String) {
        positions.remove(key)
    }

    fun clearAll() {
        positions.clear()
    }
}

/**
 * Creates and remembers a [LazyListState] initialized to the last saved scroll position
 * for the current composable call site, continuously saving updates and safely restoring
 * even when list items populate asynchronously after initial composition.
 */
@Composable
fun rememberPersistentLazyListState(
    key: String = "lazy_col_${currentCompositeKeyHash}"
): LazyListState {
    val savedOnMount = remember(key) { ScrollPositionStore.get(key) }
    val pendingRestoreFlag = remember(key) {
        booleanArrayOf(savedOnMount.index > 0 || savedOnMount.offset > 0)
    }
    val state = remember(key) {
        LazyListState(
            firstVisibleItemIndex = savedOnMount.index.coerceAtLeast(0),
            firstVisibleItemScrollOffset = savedOnMount.offset.coerceAtLeast(0)
        )
    }

    LaunchedEffect(key, state) {
        snapshotFlow {
            ScrollSnapshot(
                index = state.firstVisibleItemIndex,
                offset = state.firstVisibleItemScrollOffset,
                totalItems = state.layoutInfo.totalItemsCount,
                isScrolling = state.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collect { snap ->
                if (snap.isScrolling) {
                    pendingRestoreFlag[0] = false
                    ScrollPositionStore.save(key, snap.index, snap.offset, snap.totalItems)
                } else if (pendingRestoreFlag[0]) {
                    if (snap.totalItems > savedOnMount.index) {
                        pendingRestoreFlag[0] = false
                        if (snap.index != savedOnMount.index || snap.offset != savedOnMount.offset) {
                            val target = ScrollPositionStore.clampForItemCount(savedOnMount, snap.totalItems)
                            runCatching { state.scrollToItem(target.index, target.offset) }
                        }
                        ScrollPositionStore.save(
                            key = key,
                            index = state.firstVisibleItemIndex,
                            offset = state.firstVisibleItemScrollOffset,
                            totalItemsCount = snap.totalItems
                        )
                    }
                } else {
                    ScrollPositionStore.save(key, snap.index, snap.offset, snap.totalItems)
                }
            }
    }

    DisposableEffect(key, state) {
        onDispose {
            val totalItems = state.layoutInfo.totalItemsCount
            val currentIndex = state.firstVisibleItemIndex
            val currentOffset = state.firstVisibleItemScrollOffset
            if (!pendingRestoreFlag[0] || currentIndex > 0 || currentOffset > 0) {
                ScrollPositionStore.save(key, currentIndex, currentOffset, totalItems)
            }
        }
    }

    return state
}

/**
 * Creates and remembers a [LazyGridState] initialized to the last saved scroll position
 * for the current composable call site, with safe async restoration and disposal saving.
 */
@Composable
fun rememberPersistentLazyGridState(
    key: String = "lazy_grid_${currentCompositeKeyHash}"
): LazyGridState {
    val savedOnMount = remember(key) { ScrollPositionStore.get(key) }
    val pendingRestoreFlag = remember(key) {
        booleanArrayOf(savedOnMount.index > 0 || savedOnMount.offset > 0)
    }
    val state = remember(key) {
        LazyGridState(
            firstVisibleItemIndex = savedOnMount.index.coerceAtLeast(0),
            firstVisibleItemScrollOffset = savedOnMount.offset.coerceAtLeast(0)
        )
    }

    LaunchedEffect(key, state) {
        snapshotFlow {
            ScrollSnapshot(
                index = state.firstVisibleItemIndex,
                offset = state.firstVisibleItemScrollOffset,
                totalItems = state.layoutInfo.totalItemsCount,
                isScrolling = state.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collect { snap ->
                if (snap.isScrolling) {
                    pendingRestoreFlag[0] = false
                    ScrollPositionStore.save(key, snap.index, snap.offset, snap.totalItems)
                } else if (pendingRestoreFlag[0]) {
                    if (snap.totalItems > savedOnMount.index) {
                        pendingRestoreFlag[0] = false
                        if (snap.index != savedOnMount.index || snap.offset != savedOnMount.offset) {
                            val target = ScrollPositionStore.clampForItemCount(savedOnMount, snap.totalItems)
                            runCatching { state.scrollToItem(target.index, target.offset) }
                        }
                        ScrollPositionStore.save(
                            key = key,
                            index = state.firstVisibleItemIndex,
                            offset = state.firstVisibleItemScrollOffset,
                            totalItemsCount = snap.totalItems
                        )
                    }
                } else {
                    ScrollPositionStore.save(key, snap.index, snap.offset, snap.totalItems)
                }
            }
    }

    DisposableEffect(key, state) {
        onDispose {
            val totalItems = state.layoutInfo.totalItemsCount
            val currentIndex = state.firstVisibleItemIndex
            val currentOffset = state.firstVisibleItemScrollOffset
            if (!pendingRestoreFlag[0] || currentIndex > 0 || currentOffset > 0) {
                ScrollPositionStore.save(key, currentIndex, currentOffset, totalItems)
            }
        }
    }

    return state
}
