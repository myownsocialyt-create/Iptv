package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CustomPlaylistEntity
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavSection {
    HOME,
    INDIA,
    GLOBAL,
    CATEGORIES,
    FAVORITES,
    PLAYLISTS,
    ADD_PLAYLIST,
    XTREAM,
    SETTINGS
}

data class IptvUiState(
    val currentSection: NavSection = NavSection.HOME,
    val selectedPlaylist: PlaylistSource = PresetPlaylists.ALL,
    val channelSelectedPlaylist: PlaylistSource = PresetPlaylists.ALL,
    val customPlaylists: List<CustomPlaylistEntity> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val channelChannels: List<Channel> = emptyList(),
    val allChannels: List<Channel> = emptyList(),
    val categories: List<String> = emptyList(),
    val channelCategories: List<String> = emptyList(),
    val selectedCategory: String? = null,
    val searchQuery: String = "",
    val activeChannel: Channel? = null,
    val isFullscreen: Boolean = false,
    val isSplitViewMode: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val currentLanguage: AppLanguage = AppLanguage.ENGLISH,
    val isFirstTimeLaunch: Boolean = false,
    val currentThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val hasAcceptedMediaDisclaimer: Boolean = false,
    val pendingPlayChannel: Channel? = null,
    val autoReconnectEnabled: Boolean = true,
    val hasSelectedFirstPlaylist: Boolean = true,
    val deletedPresetIds: Set<String> = emptySet()
)

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaylistRepository.getInstance(application)
    private val xtreamRepository = XtreamRepository(com.example.data.local.IptvDatabase.getInstance(application).channelDao())
    val playerManager = IptvPlayerManager(application)
    private val prefs = application.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)

    private val _currentLanguage = MutableStateFlow(
        AppLanguage.fromCode(prefs.getString("app_language_code", "en") ?: "en")
    )
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    private val _currentThemeMode = MutableStateFlow(
        AppThemeMode.fromKey(prefs.getString("app_theme_mode", "system") ?: "system")
    )
    val currentThemeMode: StateFlow<AppThemeMode> = _currentThemeMode.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(
        prefs.getBoolean("auto_reconnect_enabled", true)
    )
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _hasSelectedFirstPlaylist = MutableStateFlow(
        prefs.getBoolean("has_selected_first_playlist", true)
    )
    val hasSelectedFirstPlaylist: StateFlow<Boolean> = _hasSelectedFirstPlaylist.asStateFlow()

    private val _hasAcceptedMediaDisclaimer = MutableStateFlow(
        prefs.getBoolean("has_accepted_media_disclaimer", true)
    )
    val hasAcceptedMediaDisclaimer: StateFlow<Boolean> = _hasAcceptedMediaDisclaimer.asStateFlow()

    private val _pendingPlayChannel = MutableStateFlow<Channel?>(null)
    val pendingPlayChannel: StateFlow<Channel?> = _pendingPlayChannel.asStateFlow()

    private val _deletedPresetIds = MutableStateFlow<Set<String>>(
        prefs.getStringSet("deleted_preset_ids", emptySet()) ?: emptySet()
    )
    val deletedPresetIds: StateFlow<Set<String>> = _deletedPresetIds.asStateFlow()

    private val _isFirstTimeLaunch = MutableStateFlow(
        !prefs.getBoolean("has_completed_language_setup", true)
    )
    val isFirstTimeLaunch: StateFlow<Boolean> = _isFirstTimeLaunch.asStateFlow()

    private val _currentSection = MutableStateFlow(NavSection.HOME)
    val currentSection: StateFlow<NavSection> = _currentSection.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow(loadSavedPlaylist())
    val selectedPlaylist: StateFlow<PlaylistSource> = _selectedPlaylist.asStateFlow()

    private val _channelSelectedPlaylist = MutableStateFlow(loadSavedChannelPlaylist())
    val channelSelectedPlaylist: StateFlow<PlaylistSource> = _channelSelectedPlaylist.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val _isSplitViewMode = MutableStateFlow(false)
    val isSplitViewMode: StateFlow<Boolean> = _isSplitViewMode.asStateFlow()

    private val _addPlaylistTab = MutableStateFlow(0)
    val addPlaylistTab: StateFlow<Int> = _addPlaylistTab.asStateFlow()

    val favorites: StateFlow<List<Channel>> = repository.observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recents: StateFlow<List<Channel>> = repository.observeRecents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customPlaylists: StateFlow<List<CustomPlaylistEntity>> = repository.observeCustomPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val xtreamAccounts: StateFlow<List<XtreamAccountEntity>> = xtreamRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeXtreamAccount: StateFlow<XtreamAccountEntity?> = xtreamRepository.observeActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _xtreamChannels = MutableStateFlow<List<Channel>>(emptyList())
    val xtreamChannels: StateFlow<List<Channel>> = _xtreamChannels.asStateFlow()

    private val _xtreamVodChannels = MutableStateFlow<List<Channel>>(emptyList())
    val xtreamVodChannels: StateFlow<List<Channel>> = _xtreamVodChannels.asStateFlow()

    private val _xtreamAuthError = MutableStateFlow<String?>(null)
    val xtreamAuthError: StateFlow<String?> = _xtreamAuthError.asStateFlow()

    fun clearXtreamAuthError() {
        _xtreamAuthError.value = null
    }

    private val _isXtreamLoading = MutableStateFlow(false)
    val isXtreamLoading: StateFlow<Boolean> = _isXtreamLoading.asStateFlow()

    private val _indiaChannels = MutableStateFlow<List<Channel>>(PresetPlaylists.FALLBACK_INDIA_CHANNELS)
    private val _globalChannels = MutableStateFlow<List<Channel>>(PresetPlaylists.FALLBACK_GLOBAL_CHANNELS)
    private val _customPlaylistsChannels = MutableStateFlow<Map<Long, List<Channel>>>(emptyMap())

    val allM3uChannels: StateFlow<List<Channel>> = combine(
        _indiaChannels,
        _globalChannels,
        _customPlaylistsChannels,
        customPlaylists,
        _deletedPresetIds
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val india = args[0] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val global = args[1] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val customMap = args[2] as Map<Long, List<Channel>>
        @Suppress("UNCHECKED_CAST")
        val cLists = args[3] as List<CustomPlaylistEntity>
        @Suppress("UNCHECKED_CAST")
        val deletedPresets = args[4] as Set<String>

        val playlistTitleMap = cLists.associate { it.id to it.title }
        val result = mutableListOf<Channel>()

        if (!deletedPresets.contains(PresetPlaylists.INDIA.id)) {
            val list = if (india.isNotEmpty()) india else PresetPlaylists.FALLBACK_INDIA_CHANNELS
            result.addAll(list.map { it.copy(playlistName = "India Channels") })
        }

        if (!deletedPresets.contains(PresetPlaylists.GLOBAL.id)) {
            val list = if (global.isNotEmpty()) global else PresetPlaylists.FALLBACK_GLOBAL_CHANNELS
            result.addAll(list.map { it.copy(playlistName = "Global Channels") })
        }

        customMap.forEach { (plId, chs) ->
            val title = playlistTitleMap[plId] ?: "Custom Playlist"
            if (chs.isNotEmpty()) {
                result.addAll(chs.map { it.copy(playlistName = title) })
            }
        }

        result.distinctBy { it.streamUrl }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allSearchableChannels: StateFlow<List<Channel>> = allM3uChannels

    private val _isPlaylistLoading = MutableStateFlow(false)
    private val _playlistStatusMessage = MutableStateFlow<String?>(null)
    private val _refreshTrigger = MutableStateFlow(0L)
    private val _channelRefreshTrigger = MutableStateFlow(0L)

    val playlistChannels: StateFlow<List<Channel>> = combine(_selectedPlaylist, _refreshTrigger, _hasSelectedFirstPlaylist) { source, _, hasSelected -> Pair(source, hasSelected) }
        .flatMapLatest { (source, hasSelected) ->
            if (!hasSelected || source.id == "none") {
                flowOf(emptyList())
            } else if (source.id == PresetPlaylists.ALL.id) {
                allM3uChannels
            } else {
                repository.observeChannelsWithFavorites(source)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val channelScreenChannels: StateFlow<List<Channel>> = combine(_channelSelectedPlaylist, _channelRefreshTrigger, _hasSelectedFirstPlaylist) { source, _, hasSelected -> Pair(source, hasSelected) }
        .flatMapLatest { (source, hasSelected) ->
            if (!hasSelected || source.id == "none") {
                flowOf(emptyList())
            } else if (source.id == PresetPlaylists.ALL.id) {
                allM3uChannels
            } else {
                repository.observeChannelsWithFavorites(source)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val uiState: StateFlow<IptvUiState> = combine(
        combine(_currentSection, _selectedPlaylist, _channelSelectedPlaylist, _activeChannel) { sec, pl, cpl, act -> listOf(sec, pl, cpl, act) },
        combine(_isFullscreen, _isSplitViewMode, _currentLanguage, _currentThemeMode) { f, s, l, t -> listOf(f, s, l, t) },
        combine(_isPlaylistLoading, _playlistStatusMessage, _selectedCategory, _searchQuery) { l, m, c, q -> listOf(l, m, c, q) },
        combine(playlistChannels, channelScreenChannels, allSearchableChannels, favorites) { pCh, cCh, aCh, fav -> listOf(pCh, cCh, aCh, fav) },
        combine(customPlaylists, _deletedPresetIds, _hasSelectedFirstPlaylist) { cp, del, hasSel -> listOf(cp, del, hasSel) }
    ) { p1, p2, p3, p4, p5 ->
        val sec = p1[0] as NavSection
        val pl = p1[1] as PlaylistSource
        val cpl = p1[2] as PlaylistSource
        val act = p1[3] as Channel?

        val f = p2[0] as Boolean
        val s = p2[1] as Boolean
        val l = p2[2] as AppLanguage
        val t = p2[3] as AppThemeMode

        val loading = p3[0] as Boolean
        val msg = p3[1] as String?
        val cat = p3[2] as String?
        val query = p3[3] as String

        @Suppress("UNCHECKED_CAST")
        val pCh = p4[0] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val cCh = p4[1] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val aCh = p4[2] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val fav = p4[3] as List<Channel>

        @Suppress("UNCHECKED_CAST")
        val cp = p5[0] as List<CustomPlaylistEntity>
        @Suppress("UNCHECKED_CAST")
        val del = p5[1] as Set<String>
        val hasSel = p5[2] as Boolean

        val cats = pCh.map { it.category }.distinct().sorted()
        val cCats = cCh.map { it.category }.distinct().sorted()

        IptvUiState(
            currentSection = sec,
            selectedPlaylist = pl,
            channelSelectedPlaylist = cpl,
            customPlaylists = cp,
            channels = if (hasSel) pCh else emptyList(),
            channelChannels = if (hasSel) cCh else emptyList(),
            allChannels = if (hasSel) aCh else emptyList(),
            categories = if (hasSel) cats else emptyList(),
            channelCategories = if (hasSel) cCats else emptyList(),
            selectedCategory = cat,
            searchQuery = query,
            activeChannel = act,
            isFullscreen = f,
            isSplitViewMode = s,
            isLoading = loading,
            statusMessage = msg,
            currentLanguage = l,
            currentThemeMode = t,
            hasAcceptedMediaDisclaimer = _hasAcceptedMediaDisclaimer.value,
            pendingPlayChannel = _pendingPlayChannel.value,
            autoReconnectEnabled = _autoReconnectEnabled.value,
            hasSelectedFirstPlaylist = hasSel,
            deletedPresetIds = del
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IptvUiState())

    init {
        viewModelScope.launch {
            activeXtreamAccount.collect { account ->
                if (account != null) {
                    loadXtreamChannels(account)
                } else {
                    _xtreamChannels.value = emptyList()
                    _xtreamVodChannels.value = emptyList()
                }
            }
        }
    }

    private fun loadSavedPlaylist(): PlaylistSource {
        val id = prefs.getString("selected_playlist_id", PresetPlaylists.ALL.id) ?: PresetPlaylists.ALL.id
        val title = prefs.getString("selected_playlist_title", PresetPlaylists.ALL.title) ?: PresetPlaylists.ALL.title
        val url = prefs.getString("selected_playlist_url", PresetPlaylists.ALL.url) ?: PresetPlaylists.ALL.url
        val isPreset = prefs.getBoolean("selected_playlist_is_preset", true)
        return PlaylistSource(id = id, title = title, url = url, isPreset = isPreset)
    }

    private fun loadSavedChannelPlaylist(): PlaylistSource {
        val id = prefs.getString("channel_selected_playlist_id", PresetPlaylists.ALL.id) ?: PresetPlaylists.ALL.id
        val title = prefs.getString("channel_selected_playlist_title", PresetPlaylists.ALL.title) ?: PresetPlaylists.ALL.title
        val url = prefs.getString("channel_selected_playlist_url", PresetPlaylists.ALL.url) ?: PresetPlaylists.ALL.url
        val isPreset = prefs.getBoolean("channel_selected_playlist_is_preset", true)
        return PlaylistSource(id = id, title = title, url = url, isPreset = isPreset)
    }

    fun setSection(section: NavSection) {
        _currentSection.value = section
        _searchQuery.value = ""
        _selectedCategory.value = null
    }

    fun selectPlaylist(source: PlaylistSource) {
        selectHomePlaylist(source)
        selectChannelPlaylist(source)
    }

    fun selectHomePlaylist(source: PlaylistSource) {
        prefs.edit()
            .putString("selected_playlist_id", source.id)
            .putString("selected_playlist_title", source.title)
            .putString("selected_playlist_url", source.url)
            .putBoolean("selected_playlist_is_preset", source.isPreset)
            .apply()
        _selectedPlaylist.value = source
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun selectChannelPlaylist(source: PlaylistSource) {
        prefs.edit()
            .putString("channel_selected_playlist_id", source.id)
            .putString("channel_selected_playlist_title", source.title)
            .putString("channel_selected_playlist_url", source.url)
            .putBoolean("channel_selected_playlist_is_preset", source.isPreset)
            .apply()
        _channelSelectedPlaylist.value = source
        _channelRefreshTrigger.value = System.currentTimeMillis()
    }

    fun deletePresetPlaylist(presetId: String) {
        val set = _deletedPresetIds.value.toMutableSet()
        set.add(presetId)
        _deletedPresetIds.value = set
        prefs.edit().putStringSet("deleted_preset_ids", set).apply()
    }

    fun playChannel(channel: Channel) {
        val isFav = favorites.value.any { it.streamUrl == channel.streamUrl } || channel.isFavorite
        val channelWithFav = channel.copy(isFavorite = isFav)
        _activeChannel.value = channelWithFav
        _isSplitViewMode.value = true
        playerManager.playChannel(channelWithFav)
        viewModelScope.launch {
            repository.recordRecent(channelWithFav)
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun toggleFullscreen() {
        _isFullscreen.value = !_isFullscreen.value
    }

    fun setSplitView(enabled: Boolean) {
        _isSplitViewMode.value = enabled
    }

    fun closePlayer() {
        playerManager.stop()
        _activeChannel.value = null
        _isFullscreen.value = false
        _isSplitViewMode.value = false
    }

    fun openAddPlaylist(tab: Int = 0) {
        _addPlaylistTab.value = tab
        setSection(NavSection.ADD_PLAYLIST)
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language
        prefs.edit().putString("app_language_code", language.code).apply()
    }

    fun setThemeMode(themeMode: AppThemeMode) {
        _currentThemeMode.value = themeMode
        prefs.edit().putString("app_theme_mode", themeMode.key).apply()
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _autoReconnectEnabled.value = enabled
        prefs.edit().putBoolean("auto_reconnect_enabled", enabled).apply()
        playerManager.setAutoReconnectEnabled(enabled)
    }

    fun addAndSelectCustomPlaylist(title: String, url: String) {
        viewModelScope.launch {
            val id = repository.addCustomPlaylist(title, url)
            val source = PlaylistSource(id = "custom_$id", title = title.ifBlank { "My Playlist" }, url = url, isPreset = false)
            selectPlaylist(source)
            setSection(NavSection.HOME)
        }
    }

    fun deleteCustomPlaylist(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomPlaylist(id)
        }
    }

    fun refreshPlaylistChannels() {
        repository.clearCache(_selectedPlaylist.value.url)
        repository.clearCache(_channelSelectedPlaylist.value.url)
        _isPlaylistLoading.value = true
        _playlistStatusMessage.value = "Refreshing channels..."
        _refreshTrigger.value = System.currentTimeMillis()
        _channelRefreshTrigger.value = System.currentTimeMillis()
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            _isPlaylistLoading.value = false
            _playlistStatusMessage.value = null
        }
    }

    private fun loadXtreamChannels(account: XtreamAccountEntity) {
        _isXtreamLoading.value = true
        viewModelScope.launch {
            val live = xtreamRepository.fetchLiveStreams(account)
            val vod = xtreamRepository.fetchVodStreams(account)
            _xtreamChannels.value = live
            _xtreamVodChannels.value = vod
            _isXtreamLoading.value = false
        }
    }

    fun refreshXtreamChannels() {
        val account = activeXtreamAccount.value ?: return
        loadXtreamChannels(account)
    }

    fun addXtreamAccount(name: String, url: String, user: String, pass: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = xtreamRepository.addAccount(name, url, user, pass)
            if (result.isSuccess) {
                _xtreamAuthError.value = null
                onComplete(true)
            } else {
                _xtreamAuthError.value = result.exceptionOrNull()?.message ?: "Login failed"
                onComplete(false)
            }
        }
    }

    fun setActiveXtreamAccount(id: Long) {
        viewModelScope.launch {
            xtreamRepository.setActiveAccount(id)
        }
    }

    fun deleteXtreamAccount(id: Long) {
        viewModelScope.launch {
            xtreamRepository.deleteAccount(id)
        }
    }
}
