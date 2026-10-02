package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomPlaylistEntity
import com.example.data.local.IptvDatabase
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
import com.example.data.repository.PlaylistRepository
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.data.repository.XtreamRepository
import com.example.player.IptvPlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IptvUiState(
    val channels: List<Channel> = emptyList(),
    val filteredChannels: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val activeChannel: Channel? = null,
    val activePlaylistSource: PlaylistSource = PresetPlaylists.INDIA,
    val isLoading: Boolean = false,
    val isSplitViewMode: Boolean = false,
    val isFullscreen: Boolean = false
)

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val db = IptvDatabase.getDatabase(application)
    val playlistRepository = PlaylistRepository(application, db.channelDao())
    val xtreamRepository = XtreamRepository(db.channelDao())
    val playerManager = IptvPlayerManager(application)

    private val _uiState = MutableStateFlow(IptvUiState())
    val uiState: StateFlow<IptvUiState> = _uiState.asStateFlow()

    val favorites: StateFlow<List<Channel>> = playlistRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<Channel>> = playlistRepository.getRecentChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPlaylists: StateFlow<List<CustomPlaylistEntity>> = playlistRepository.getCustomPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val xtreamAccounts: StateFlow<List<XtreamAccountEntity>> = xtreamRepository.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadPlaylist(PresetPlaylists.INDIA)
    }

    fun loadPlaylist(source: PlaylistSource) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, activePlaylistSource = source)
            val channels = playlistRepository.getChannelsForSource(source)
            val categories = listOf("All") + channels.map { it.category }.distinct().sorted()

            _uiState.value = _uiState.value.copy(
                channels = channels,
                filteredChannels = channels,
                categories = categories,
                selectedCategory = "All",
                searchQuery = "",
                isLoading = false
            )
        }
    }

    fun playChannel(channel: Channel) {
        _uiState.value = _uiState.value.copy(
            activeChannel = channel,
            isSplitViewMode = true
        )
        playerManager.playChannel(channel)
        viewModelScope.launch {
            playlistRepository.recordRecent(channel)
        }
    }

    fun playNextChannel() {
        val channels = _uiState.value.filteredChannels
        val current = _uiState.value.activeChannel ?: return
        val currentIndex = channels.indexOfFirst { it.streamUrl == current.streamUrl }
        if (currentIndex != -1 && currentIndex < channels.size - 1) {
            playChannel(channels[currentIndex + 1])
        }
    }

    fun playPreviousChannel() {
        val channels = _uiState.value.filteredChannels
        val current = _uiState.value.activeChannel ?: return
        val currentIndex = channels.indexOfFirst { it.streamUrl == current.streamUrl }
        if (currentIndex > 0) {
            playChannel(channels[currentIndex - 1])
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            playlistRepository.toggleFavorite(channel)
        }
    }

    fun selectCategory(category: String) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        filterChannels()
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        filterChannels()
    }

    private fun filterChannels() {
        val state = _uiState.value
        var list = state.channels

        if (state.selectedCategory != "All") {
            list = list.filter { it.category.equals(state.selectedCategory, ignoreCase = true) }
        }

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.trim().lowercase()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                        it.category.lowercase().contains(q) ||
                        it.language?.lowercase()?.contains(q) == true
            }
        }

        _uiState.value = state.copy(filteredChannels = list)
    }

    fun setFullscreen(fullscreen: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = fullscreen)
    }

    fun toggleFullscreen() {
        setFullscreen(!_uiState.value.isFullscreen)
    }

    fun closePlayer() {
        _uiState.value = _uiState.value.copy(activeChannel = null, isSplitViewMode = false, isFullscreen = false)
        playerManager.release()
        com.example.ad.InStreamAdManager.onPlayerClosed()
    }

    fun addCustomPlaylist(entity: CustomPlaylistEntity) {
        viewModelScope.launch {
            playlistRepository.addCustomPlaylist(entity)
        }
    }

    fun deleteCustomPlaylist(id: String) {
        viewModelScope.launch {
            playlistRepository.deleteCustomPlaylist(id)
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            playlistRepository.clearRecents()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
