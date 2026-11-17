package com.tristan.spotifyfast.ui.search

import com.tristan.spotifyfast.data.local.SearchHistoryEntity
import com.tristan.spotifyfast.data.local.SpotifyDao
import com.tristan.spotifyfast.data.remote.SearchResponseDto
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SearchCategory {
    ALL,
    TRACKS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
    PODCASTS,
    AUDIOBOOKS,
}

data class SearchUiState(
    val query: String = "",
    val selectedCategory: SearchCategory = SearchCategory.ALL,
    val isLoading: Boolean = false,
    val results: SearchResponseDto? = null,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val dao: SpotifyDao,
    private val syncEngine: OfflineFirstSyncEngine,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val queryFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch {
            queryFlow
                .debounce(250L)
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.isBlank()) {
                        _uiState.value = _uiState.value.copy(isLoading = false, results = null, error = null)
                        return@collectLatest
                    }
                    _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                    dao.upsertSearchHistory(SearchHistoryEntity(q.trim()))
                    syncEngine.search(q.trim(), limit = 10)
                        .onSuccess { res ->
                            _uiState.value = _uiState.value.copy(isLoading = false, results = res)
                        }
                        .onFailure { err ->
                            _uiState.value = _uiState.value.copy(isLoading = false, error = err.message)
                        }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(query = newQuery)
        queryFlow.value = newQuery
    }

    fun setCategory(category: SearchCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun removeHistoryItem(query: String) {
        scope.launch { dao.deleteSearchHistory(query) }
    }
}
