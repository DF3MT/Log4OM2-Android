package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.model.Qso
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.ui.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

sealed interface LogUiState {
    data object Loading : LogUiState
    data class Success(
        val qsos: List<Qso>,
        val totalCount: Int,
        val endReached: Boolean,
        val isLoadingMore: Boolean
    ) : LogUiState
    data class Error(val message: UiText) : LogUiState
}

class LogViewModel(private val repository: LogRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<LogUiState>(LogUiState.Loading)
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _deleteMessage = MutableStateFlow<UiText?>(null)
    val deleteMessage: StateFlow<UiText?> = _deleteMessage.asStateFlow()

    init { loadInitial() }

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = LogUiState.Loading
            val query = _searchQuery.value
            val pageResult = fetchPage(query, offset = 0)
            val totalCount = if (query.isBlank()) {
                repository.getQsoCount().getOrDefault(0)
            } else 0  // search has no cheap total — show 0
            pageResult.fold(
                onSuccess = { page ->
                    _uiState.value = LogUiState.Success(
                        qsos = page,
                        totalCount = if (query.isBlank()) totalCount else page.size,
                        endReached = page.size < PAGE_SIZE,
                        isLoadingMore = false
                    )
                },
                onFailure = { e ->
                    _uiState.value = LogUiState.Error(
                        e.localizedMessage?.let { UiText.Raw(it) }
                            ?: UiText.Resource(R.string.error_connection)
                    )
                }
            )
        }
    }

    fun loadMore() {
        val current = _uiState.value as? LogUiState.Success ?: return
        if (current.endReached || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            val query = _searchQuery.value
            fetchPage(query, offset = current.qsos.size).fold(
                onSuccess = { page ->
                    _uiState.update { state ->
                        if (state !is LogUiState.Success) return@update state
                        val merged = state.qsos + page
                        state.copy(
                            qsos = merged,
                            totalCount = if (query.isBlank()) state.totalCount else merged.size,
                            endReached = page.size < PAGE_SIZE,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        if (state is LogUiState.Success) state.copy(isLoadingMore = false) else state
                    }
                }
            )
        }
    }

    private suspend fun fetchPage(query: String, offset: Int): Result<List<Qso>> =
        if (query.isBlank()) {
            repository.getRecentQsos(limit = PAGE_SIZE, offset = offset)
        } else {
            repository.searchQsos(query, limit = PAGE_SIZE, offset = offset)
        }

    fun search(query: String) {
        if (_searchQuery.value == query) return
        _searchQuery.value = query
        loadInitial()
    }

    fun refresh() = loadInitial()

    fun deleteQso(qsoid: Long) {
        viewModelScope.launch {
            repository.deleteQso(qsoid).fold(
                onSuccess = { loadInitial() },
                onFailure = { e ->
                    _deleteMessage.value = UiText.Resource(
                        R.string.error_delete_failed,
                        e.localizedMessage.orEmpty()
                    )
                }
            )
        }
    }

    fun clearDeleteMessage() { _deleteMessage.value = null }
}
