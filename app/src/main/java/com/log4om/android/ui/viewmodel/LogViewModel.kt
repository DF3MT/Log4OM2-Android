package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.model.LogFilter
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

    private val _filter = MutableStateFlow(LogFilter())
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val _exportAdif = MutableStateFlow<String?>(null)
    val exportAdif: StateFlow<String?> = _exportAdif.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _deleteMessage = MutableStateFlow<UiText?>(null)
    val deleteMessage: StateFlow<UiText?> = _deleteMessage.asStateFlow()

    init { loadInitial() }

    fun applyFilter(filter: LogFilter) {
        _filter.value = filter
        if (_selectionMode.value) clearSelectionKeepMode()
        loadInitial()
    }

    fun clearFilter() = applyFilter(LogFilter())

    fun loadInitial() {
        viewModelScope.launch {
            _uiState.value = LogUiState.Loading
            val f = _filter.value
            val pageResult = repository.queryQsos(f, limit = PAGE_SIZE, offset = 0)
            val totalCount = repository.countQsos(f).getOrDefault(0)
            pageResult.fold(
                onSuccess = { page ->
                    _uiState.value = LogUiState.Success(
                        qsos = page,
                        totalCount = totalCount,
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
            val f = _filter.value
            repository.queryQsos(f, limit = PAGE_SIZE, offset = current.qsos.size).fold(
                onSuccess = { page ->
                    _uiState.update { state ->
                        if (state !is LogUiState.Success) return@update state
                        state.copy(
                            qsos = state.qsos + page,
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

    fun refresh() = loadInitial()

    fun enterSelectionMode(initialId: Long? = null) {
        _selectionMode.value = true
        _selectedIds.value = if (initialId != null) setOf(initialId) else emptySet()
    }

    fun exitSelectionMode() {
        _selectionMode.value = false
        _selectedIds.value = emptySet()
    }

    private fun clearSelectionKeepMode() {
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(qsoid: Long) {
        if (!_selectionMode.value) return
        _selectedIds.update { set ->
            if (qsoid in set) set - qsoid else set + qsoid
        }
    }

    fun selectAllMatchingFilter() {
        viewModelScope.launch {
            repository.getFilteredQsoIds(_filter.value).fold(
                onSuccess = { ids ->
                    _selectionMode.value = true
                    _selectedIds.value = ids.toSet()
                    _message.value = UiText.Resource(R.string.selection_all_count, ids.size)
                },
                onFailure = { e ->
                    _message.value = UiText.Resource(
                        R.string.error_selection_failed,
                        e.localizedMessage.orEmpty()
                    )
                }
            )
        }
    }

    fun prepareExport() {
        val ids = _selectedIds.value.toList()
        if (ids.isEmpty()) {
            _message.value = UiText.Resource(R.string.export_none_selected)
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            repository.exportAdif(ids).fold(
                onSuccess = { adif ->
                    _isExporting.value = false
                    _exportAdif.value = adif
                },
                onFailure = { e ->
                    _isExporting.value = false
                    _message.value = UiText.Resource(
                        R.string.export_failed,
                        e.localizedMessage.orEmpty()
                    )
                }
            )
        }
    }

    fun clearExportAdif() { _exportAdif.value = null }
    fun clearMessage() { _message.value = null }

    fun deleteQso(qsoid: Long) {
        viewModelScope.launch {
            repository.deleteQso(qsoid).fold(
                onSuccess = {
                    _selectedIds.update { it - qsoid }
                    loadInitial()
                },
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
