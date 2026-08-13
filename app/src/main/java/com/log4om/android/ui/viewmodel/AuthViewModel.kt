package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.data.auth.AuthTokenStore
import com.log4om.android.data.network.Log4omApiService
import com.log4om.android.ui.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val ready: Boolean = false,
    val loggedIn: Boolean = false,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val apiUrl: String = AuthTokenStore.DEFAULT_API_URL,
    val registerMode: Boolean = false,
    val busy: Boolean = false,
    val error: UiText? = null
)

class AuthViewModel(
    private val authStore: AuthTokenStore,
    private val api: Log4omApiService
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                ready = true,
                loggedIn = authStore.isLoggedIn,
                email = authStore.email.orEmpty(),
                apiUrl = authStore.apiBaseUrl
            )
        }
    }

    fun updateEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun updatePassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun updateDisplayName(v: String) = _state.update { it.copy(displayName = v) }
    fun updateApiUrl(v: String) = _state.update { it.copy(apiUrl = v, error = null) }
    fun setRegisterMode(v: Boolean) = _state.update { it.copy(registerMode = v, error = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun refreshSessionFlag() {
        _state.update {
            it.copy(
                loggedIn = authStore.isLoggedIn,
                email = authStore.email.orEmpty(),
                apiUrl = authStore.apiBaseUrl
            )
        }
    }

    fun submit() {
        val s = _state.value
        if (s.email.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(error = UiText.Raw("Email / password required")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val result = if (s.registerMode) {
                api.register(s.email, s.password, s.displayName.ifBlank { null }, s.apiUrl)
            } else {
                api.login(s.email, s.password, s.apiUrl)
            }
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            busy = false,
                            loggedIn = true,
                            password = "",
                            email = authStore.email.orEmpty()
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            busy = false,
                            error = UiText.Raw(e.message ?: "Auth failed")
                        )
                    }
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            api.logout()
            _state.update {
                it.copy(
                    loggedIn = false,
                    password = "",
                    email = "",
                    registerMode = false
                )
            }
        }
    }
}
