package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.ui.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream

data class SettingsState(
    val dbHost:     String = "",
    val dbPort:     String = "3306",
    val dbName:     String = "",
    val dbUser:     String = "",
    val dbPassword: String = "",
    val qrzUser:     String = "",
    val qrzPassword: String = "",
    val myCallsign:   String = "",
    val myGridsquare: String = "",
    val myName:       String = "",
    val myRig:        String = "",
    val myDxcc:       String = "",
    val defaultRstSent: String = "59",
    val defaultRstRcvd: String = "59",
    val defaultBand:    String = "20m",
    val defaultMode:    String = "SSB",
    val defaultTxpwr:   String = "",
    val isTestingDb:    Boolean = false,
    val dbTestResult:   UiText? = null,
    val dbTestSuccess:  Boolean = false,
    val isSaving:       Boolean = false,
    val saveSuccess:    Boolean = false,
    val isImporting:    Boolean = false,
    val importProgress: Int = 0,
    val importMessage:  UiText? = null
)

class SettingsViewModel(
    private val prefs: AppPrefs,
    private val repository: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = SettingsState(
                dbHost         = prefs.dbHost.first(),
                dbPort         = prefs.dbPort.first().toString(),
                dbName         = prefs.dbName.first(),
                dbUser         = prefs.dbUser.first(),
                dbPassword     = prefs.dbPassword.first(),
                qrzUser        = prefs.qrzUser.first(),
                qrzPassword    = prefs.qrzPassword.first(),
                myCallsign     = prefs.myCallsign.first(),
                myGridsquare   = prefs.myGridsquare.first(),
                myName         = prefs.myName.first(),
                myRig          = prefs.myRig.first(),
                myDxcc         = prefs.myDxcc.first(),
                defaultRstSent = prefs.defaultRstSent.first(),
                defaultRstRcvd = prefs.defaultRstRcvd.first(),
                defaultBand    = prefs.defaultBand.first(),
                defaultMode    = prefs.defaultMode.first(),
                defaultTxpwr   = prefs.defaultTxpwr.first()
            )
        }
    }

    fun updateDbHost(v: String)         = _state.update { it.copy(dbHost = v) }
    fun updateDbPort(v: String)         = _state.update { it.copy(dbPort = v) }
    fun updateDbName(v: String)         = _state.update { it.copy(dbName = v) }
    fun updateDbUser(v: String)         = _state.update { it.copy(dbUser = v) }
    fun updateDbPassword(v: String)     = _state.update { it.copy(dbPassword = v) }
    fun updateQrzUser(v: String)        = _state.update { it.copy(qrzUser = v) }
    fun updateQrzPassword(v: String)    = _state.update { it.copy(qrzPassword = v) }
    fun updateMyCallsign(v: String)     = _state.update { it.copy(myCallsign = v.uppercase()) }
    fun updateMyGridsquare(v: String)   = _state.update { it.copy(myGridsquare = v.uppercase()) }
    fun updateMyName(v: String)         = _state.update { it.copy(myName = v) }
    fun updateMyRig(v: String)          = _state.update { it.copy(myRig = v) }
    fun updateMyDxcc(v: String)         = _state.update { it.copy(myDxcc = v.filter(Char::isDigit).take(4)) }
    fun updateDefaultRstSent(v: String) = _state.update { it.copy(defaultRstSent = v) }
    fun updateDefaultRstRcvd(v: String) = _state.update { it.copy(defaultRstRcvd = v) }
    fun updateDefaultBand(v: String)    = _state.update { it.copy(defaultBand = v) }
    fun updateDefaultMode(v: String)    = _state.update { it.copy(defaultMode = v) }
    fun updateDefaultTxpwr(v: String)   = _state.update { it.copy(defaultTxpwr = v) }

    fun testDbConnection() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isTestingDb = true, dbTestResult = null) }
            prefs.update {
                it[AppPrefs.DB_HOST]     = s.dbHost
                it[AppPrefs.DB_PORT]     = s.dbPort.toIntOrNull() ?: 3306
                it[AppPrefs.DB_NAME]     = s.dbName
                it[AppPrefs.DB_USER]     = s.dbUser
                it[AppPrefs.DB_PASSWORD] = s.dbPassword
            }
            repository.testDbConnection().fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            isTestingDb = false,
                            dbTestResult = UiText.Resource(R.string.db_connected),
                            dbTestSuccess = true
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isTestingDb = false,
                            dbTestResult = e.localizedMessage?.let { msg ->
                                UiText.Resource(R.string.db_error_detail, msg)
                            } ?: UiText.Resource(R.string.db_error),
                            dbTestSuccess = false
                        )
                    }
                }
            )
        }
    }

    fun saveSettings() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            prefs.update {
                it[AppPrefs.DB_HOST]          = s.dbHost
                it[AppPrefs.DB_PORT]          = s.dbPort.toIntOrNull() ?: 3306
                it[AppPrefs.DB_NAME]          = s.dbName
                it[AppPrefs.DB_USER]          = s.dbUser
                it[AppPrefs.DB_PASSWORD]      = s.dbPassword
                it[AppPrefs.QRZ_USER]         = s.qrzUser
                it[AppPrefs.QRZ_PASSWORD]     = s.qrzPassword
                it[AppPrefs.MY_CALLSIGN]      = s.myCallsign
                it[AppPrefs.MY_GRIDSQUARE]    = s.myGridsquare
                it[AppPrefs.MY_NAME]          = s.myName
                it[AppPrefs.MY_RIG]           = s.myRig
                it[AppPrefs.MY_DXCC]          = s.myDxcc
                it[AppPrefs.DEFAULT_RST_SENT] = s.defaultRstSent
                it[AppPrefs.DEFAULT_RST_RCVD] = s.defaultRstRcvd
                it[AppPrefs.DEFAULT_BAND]     = s.defaultBand
                it[AppPrefs.DEFAULT_MODE]     = s.defaultMode
                it[AppPrefs.DEFAULT_TXPWR]    = s.defaultTxpwr
            }
            _state.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun clearSaveSuccess() = _state.update { it.copy(saveSuccess = false) }
    fun clearDbTestResult() = _state.update { it.copy(dbTestResult = null) }
    fun clearImportMessage() = _state.update { it.copy(importMessage = null) }

    fun importAdif(input: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isImporting = true, importProgress = 0, importMessage = null) }
            repository.importAdif(input) { parsed ->
                _state.update { it.copy(importProgress = parsed) }
            }.fold(
                onSuccess = { res ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            importMessage = UiText.Resource(
                                R.string.adif_import_done,
                                res.inserted,
                                res.skipped
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isImporting = false,
                            importMessage = UiText.Resource(
                                R.string.adif_import_error,
                                e.localizedMessage ?: e::class.simpleName.orEmpty()
                            )
                        )
                    }
                }
            )
        }
    }
}
