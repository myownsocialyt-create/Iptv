package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomPlaylistEntity
import com.example.data.local.LocalChannelStore
import com.example.data.local.OnboardingPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.Channel
import com.example.data.repository.M3uRepository
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.player.IptvPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IptvUiState(
    val channels: List<Channel> = PresetPlaylists.builtInChannels,
    val categories: List<String> = listOf("All", "Movies", "News", "Sports", "Science", "Tech"),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val activeChannel: Channel? = null,
    val isSplitViewMode: Boolean = false,
    val isFullscreen: Boolean = false,
    val isLoading: Boolean = false,
    val activePlaylistSource: PlaylistSource = PresetPlaylists.defaultPresets.first(),
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH
)

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = M3uRepository()
    private val localStore = LocalChannelStore(application)
    private val prefs = OnboardingPreferences(application)

    val playerManager = IptvPlayerManager(application)

    private val _rawChannels = MutableStateFlow(PresetPlaylists.builtInChannels)
    private val _uiState = MutableStateFlow(
        IptvUiState(currentLanguage = prefs.getLanguage())
    )
    val uiState: StateFlow<IptvUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<Channel>> = combine(_rawChannels, localStore.favoriteIds) { list, favIds ->
        list.filter { it.id in favIds }.map { it.copy(isFavorite = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<Channel>> = localStore.recents
    val customPlaylists: StateFlow<List<CustomPlaylistEntity>> = localStore.customPlaylists

    init {
        viewModelScope.launch {
            combine(
                _rawChannels,
                localStore.favoriteIds,
                _uiState
            ) { allChannels, favIds, state ->
                val withFavs = allChannels.map { it.copy(isFavorite = it.id in favIds) }
                val categories = listOf("All") + withFavs.map { it.category }.distinct().sorted()
                val filtered = withFavs.filter { ch ->
                    val matchesCategory = state.selectedCategory == "All" ||
                        ch.category.equals(state.selectedCategory, ignoreCase = true)
                    val matchesSearch = state.searchQuery.isBlank() ||
                        ch.name.contains(state.searchQuery, ignoreCase = true) ||
                        ch.category.contains(state.searchQuery, ignoreCase = true)
                    matchesCategory && matchesSearch
                }
                val updatedActive = state.activeChannel?.let { active ->
                    withFavs.firstOrNull { it.id == active.id } ?: active.copy(isFavorite = active.id in favIds)
                }
                state.copy(
                    channels = filtered,
                    categories = categories,
                    activeChannel = updatedActive
                )
            }.collect { merged ->
                _uiState.value = merged
            }
        }
        loadPlaylist(PresetPlaylists.defaultPresets.first())
    }

    fun loadPlaylist(source: PlaylistSource) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activePlaylistSource = source,
                    selectedCategory = "All",
                    isLoading = true
                )
            }
            val fetched = repository.fetchChannels(source)
            _rawChannels.value = fetched
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun playChannel(channel: Channel, openSplitView: Boolean = true) {
        playerManager.playStream(channel.streamUrl)
        localStore.recordRecent(channel)
        _uiState.update {
            it.copy(
                activeChannel = channel,
                isSplitViewMode = openSplitView
            )
        }
    }

    fun minimizeToMiniPlayer() {
        _uiState.update {
            it.copy(
                isSplitViewMode = false,
                isFullscreen = false
            )
        }
    }

    fun restoreFromMiniPlayer() {
        _uiState.update {
            it.copy(
                isSplitViewMode = true
            )
        }
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isFullscreen = fullscreen) }
    }

    fun closePlayer() {
        playerManager.stop()
        _uiState.update {
            it.copy(
                activeChannel = null,
                isSplitViewMode = false,
                isFullscreen = false
            )
        }
    }

    fun toggleFavorite(channel: Channel) {
        localStore.toggleFavorite(channel.id)
    }

    fun addCustomPlaylist(name: String, url: String) {
        val added = localStore.addCustomPlaylist(name, url)
        loadPlaylist(PlaylistSource.Custom(added.id, added.name, added.url))
    }

    fun deleteCustomPlaylist(id: Long) {
        localStore.deleteCustomPlaylist(id)
    }

    fun setLanguage(language: AppLanguage) {
        prefs.setLanguage(language)
        _uiState.update { it.copy(currentLanguage = language) }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
