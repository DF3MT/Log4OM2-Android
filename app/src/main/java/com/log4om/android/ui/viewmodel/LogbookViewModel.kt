package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.model.LogbookConfig
import com.log4om.android.data.model.LogbookDraft
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.ui.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LogbookUiState(
    val books: List<LogbookConfig> = emptyList(),
    val draft: LogbookDraft = LogbookDraft(),
    val passwordSet: Boolean = false,
    val qrzKeySet: Boolean = false,
    val eqslPasswordSet: Boolean = false,
    val active: Boolean = false,
    val busy: Boolean = false,
    val message: UiText? = null,
    val exportAdif: String? = null
)

class LogbookViewModel(private val repository: LogRepository) : ViewModel() {
    private val _state = MutableStateFlow(LogbookUiState())
    val state: StateFlow<LogbookUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            repository.listLogbooks().fold(
                onSuccess = { books ->
                    val current = _state.value.draft.id
                    val selected = books.find { it.id == current }
                        ?: books.find { it.active }
                        ?: books.firstOrNull()
                    _state.update { it.copy(books = books) }
                    if (selected != null && current.isBlank()) select(selected)
                    else if (selected != null) {
                        _state.update {
                            it.copy(
                                passwordSet = selected.passwordSet,
                                qrzKeySet = selected.qrzLogbookKeySet,
                                eqslPasswordSet = selected.eqslPasswordSet,
                                active = selected.active
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(message = UiText.Raw(e.message ?: "Logbooks")) }
                }
            )
        }
    }

    fun select(book: LogbookConfig) {
        _state.update {
            it.copy(
                draft = LogbookDraft(
                    id = book.id,
                    displayName = book.displayName,
                    stationCallsign = book.stationCallsign,
                    host = book.host,
                    port = book.port,
                    database = book.database,
                    username = book.username,
                    sslEnabled = book.sslEnabled,
                    eqslUser = book.eqslUser
                ),
                passwordSet = book.passwordSet,
                qrzKeySet = book.qrzLogbookKeySet,
                eqslPasswordSet = book.eqslPasswordSet,
                active = book.active
            )
        }
    }

    fun startNew() {
        _state.update {
            it.copy(
                draft = LogbookDraft(),
                passwordSet = false,
                qrzKeySet = false,
                eqslPasswordSet = false,
                active = false
            )
        }
    }

    fun updateDraft(block: (LogbookDraft) -> LogbookDraft) {
        _state.update { it.copy(draft = block(it.draft)) }
    }

    fun save() = runAction {
        val saved = repository.saveLogbook(_state.value.draft).getOrThrow()
        _state.update { it.copy(message = UiText.Resource(R.string.logbook_saved)) }
        refreshAfter(saved.id)
    }

    fun testConnection() = runAction {
        val probe = repository.testLogbook(_state.value.draft).getOrThrow()
        _state.update {
            it.copy(
                message = if (probe.ok) UiText.Resource(R.string.db_connected)
                else UiText.Raw(probe.message.ifBlank { "Connection failed" })
            )
        }
    }

    fun activate() = runAction {
        val id = _state.value.draft.id
        if (id.isBlank()) return@runAction
        repository.activateLogbook(id).getOrThrow()
        _state.update { it.copy(message = UiText.Resource(R.string.logbook_activated)) }
        refreshAfter(id)
    }

    fun delete() = runAction {
        val id = _state.value.draft.id
        if (id.isBlank()) return@runAction
        repository.deleteLogbook(id).getOrThrow()
        _state.update {
            it.copy(
                draft = LogbookDraft(),
                message = UiText.Resource(R.string.logbook_deleted)
            )
        }
        refresh()
    }

    fun export() = runAction {
        val id = _state.value.draft.id
        if (id.isBlank()) return@runAction
        val adif = repository.exportLogbook(id).getOrThrow()
        _state.update { it.copy(exportAdif = adif, busy = false) }
    }

    fun sync(service: String) = runAction {
        val id = _state.value.draft.id
        if (id.isBlank()) return@runAction
        val result = repository.syncLogbook(id, service).getOrThrow()
        val text = if (!result.ok && result.code == "MISSING_CREDENTIALS") {
            UiText.Resource(R.string.sync_missing_credentials, result.message)
        } else if (result.ok) {
            UiText.Resource(R.string.sync_done, result.uploaded, result.downloaded, result.skipped)
        } else {
            UiText.Raw(result.message.ifBlank { result.code })
        }
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearExport() = _state.update { it.copy(exportAdif = null) }

    private fun refreshAfter(id: String) {
        viewModelScope.launch {
            repository.listLogbooks().onSuccess { books ->
                _state.update { it.copy(books = books) }
                books.find { it.id == id }?.let { select(it) }
            }
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: Exception) {
                _state.update { it.copy(message = UiText.Raw(e.message ?: "Error")) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}
