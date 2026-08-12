package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.log4om.android.Log4OMApp

class AppViewModelFactory(private val app: Log4OMApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(LogViewModel::class.java) ->
            LogViewModel(app.repository) as T
        modelClass.isAssignableFrom(NewQsoViewModel::class.java) ->
            NewQsoViewModel(app.repository, app.prefs, app.locationHelper) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(app.prefs, app.repository) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}
