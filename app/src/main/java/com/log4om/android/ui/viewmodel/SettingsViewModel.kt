package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.auth.AuthTokenStore
import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.data.refs.ActivityProgram
import com.log4om.android.data.refs.ReferenceCatalog
import com.log4om.android.data.refs.ReferenceSyncService
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.ui.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SettingsState(
    val apiUrl: String = AuthTokenStore.DEFAULT_API_URL,
    val accountEmail: String = "",
    val qrzUser: String = "",
    val qrzPassword: String = "",
    val hamqthUser: String = "",
    val hamqthPassword: String = "",
    val clublogApiKey: String = "",
    val myCallsign: String = "",
    val myGridsquare: String = "",
    val myName: String = "",
    val myRig: String = "",
    val myDxcc: String = "",
    val defaultRstSent: String = "59",
    val defaultRstRcvd: String = "59",
    val defaultBand: String = "20m",
    val defaultMode: String = "SSB",
    val defaultTxpwr: String = "",
    val radiusSotaM: String = "200",
    val radiusPotaM: String = "800",
    val radiusWwffM: String = "500",
    val radiusCotaM: String = "1000",
    val radiusIotaM: String = "5000",
    val refsCountSota: Int = 0,
    val refsCountPota: Int = 0,
    val refsCountWwff: Int = 0,
    val refsCountCota: Int = 0,
    val refsCountIota: Int = 0,
    val refsLastSyncLabel: String = "",
    val refsSyncNote: String = "",
    val isSyncingRefs: Boolean = false,
    val refsSyncProgress: String = "",
    val isTestingDb: Boolean = false,
    val dbTestResult: UiText? = null,
    val dbTestSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importMessage: UiText? = null
)

class SettingsViewModel(
    private val prefs: AppPrefs,
    private val repository: LogRepository,
    private val catalog: ReferenceCatalog,
    private val syncService: ReferenceSyncService,
    private val authStore: AuthTokenStore
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val syncFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        viewModelScope.launch {
            _state.value = SettingsState(
                apiUrl = authStore.apiBaseUrl,
                accountEmail = authStore.email.orEmpty(),
                qrzUser = prefs.qrzUser.first(),
                qrzPassword = prefs.qrzPassword.first(),
                hamqthUser = prefs.hamqthUser.first(),
                hamqthPassword = prefs.hamqthPassword.first(),
                clublogApiKey = prefs.clublogApiKey.first(),
                myCallsign = prefs.myCallsign.first(),
                myGridsquare = prefs.myGridsquare.first(),
                myName = prefs.myName.first(),
                myRig = prefs.myRig.first(),
                myDxcc = prefs.myDxcc.first(),
                defaultRstSent = prefs.defaultRstSent.first(),
                defaultRstRcvd = prefs.defaultRstRcvd.first(),
                defaultBand = prefs.defaultBand.first(),
                defaultMode = prefs.defaultMode.first(),
                defaultTxpwr = prefs.defaultTxpwr.first(),
                radiusSotaM = prefs.radiusSotaM.first().toString(),
                radiusPotaM = prefs.radiusPotaM.first().toString(),
                radiusWwffM = prefs.radiusWwffM.first().toString(),
                radiusCotaM = prefs.radiusCotaM.first().toString(),
                radiusIotaM = prefs.radiusIotaM.first().toString(),
                refsSyncNote = prefs.refsLastSyncNote.first()
            )
            refreshCatalogStats()
        }
    }

    private fun refreshCatalogStats() {
        _state.update {
            it.copy(
                refsCountSota = catalog.count(ActivityProgram.SOTA),
                refsCountPota = catalog.count(ActivityProgram.POTA),
                refsCountWwff = catalog.count(ActivityProgram.WWFF),
                refsCountCota = catalog.count(ActivityProgram.COTA),
                refsCountIota = catalog.count(ActivityProgram.IOTA)
            )
        }
        viewModelScope.launch {
            val ms = prefs.refsLastSyncMs.first()
            val label = if (ms > 0L) syncFmt.format(Instant.ofEpochMilli(ms)) else ""
            _state.update {
                it.copy(
                    refsLastSyncLabel = label,
                    refsSyncNote = prefs.refsLastSyncNote.first()
                )
            }
        }
    }

    fun updateApiUrl(v: String) = _state.update { it.copy(apiUrl = v) }
    fun updateQrzUser(v: String) = _state.update { it.copy(qrzUser = v) }
    fun updateQrzPassword(v: String) = _state.update { it.copy(qrzPassword = v) }
    fun updateHamqthUser(v: String) = _state.update { it.copy(hamqthUser = v) }
    fun updateHamqthPassword(v: String) = _state.update { it.copy(hamqthPassword = v) }
    fun updateClublogApiKey(v: String) = _state.update { it.copy(clublogApiKey = v) }
    fun updateMyCallsign(v: String) = _state.update { it.copy(myCallsign = v.uppercase()) }
    fun updateMyGridsquare(v: String) = _state.update { it.copy(myGridsquare = v.uppercase()) }
    fun updateMyName(v: String) = _state.update { it.copy(myName = v) }
    fun updateMyRig(v: String) = _state.update { it.copy(myRig = v) }
    fun updateMyDxcc(v: String) = _state.update { it.copy(myDxcc = v.filter(Char::isDigit).take(4)) }
    fun updateDefaultRstSent(v: String) = _state.update { it.copy(defaultRstSent = v) }
    fun updateDefaultRstRcvd(v: String) = _state.update { it.copy(defaultRstRcvd = v) }
    fun updateDefaultBand(v: String) = _state.update { it.copy(defaultBand = v) }
    fun updateDefaultMode(v: String) = _state.update { it.copy(defaultMode = v) }
    fun updateDefaultTxpwr(v: String) = _state.update { it.copy(defaultTxpwr = v) }
    fun updateRadiusSota(v: String) = _state.update { it.copy(radiusSotaM = v.filter(Char::isDigit).take(5)) }
    fun updateRadiusPota(v: String) = _state.update { it.copy(radiusPotaM = v.filter(Char::isDigit).take(5)) }
    fun updateRadiusWwff(v: String) = _state.update { it.copy(radiusWwffM = v.filter(Char::isDigit).take(5)) }
    fun updateRadiusCota(v: String) = _state.update { it.copy(radiusCotaM = v.filter(Char::isDigit).take(5)) }
    fun updateRadiusIota(v: String) = _state.update { it.copy(radiusIotaM = v.filter(Char::isDigit).take(5)) }

    fun syncActivityRefs() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(isSyncingRefs = true, refsSyncProgress = "", importMessage = null)
            }
            syncService.syncAll { prog ->
                _state.update {
                    it.copy(refsSyncProgress = "${prog.message} (${prog.count})")
                }
            }.fold(
                onSuccess = { report ->
                    val note = report.notes.joinToString(" ")
                    val now = System.currentTimeMillis()
                    prefs.update {
                        it[AppPrefs.REFS_LAST_SYNC_MS] = now
                        it[AppPrefs.REFS_LAST_SYNC_NOTE] = note
                    }
                    _state.update {
                        it.copy(
                            isSyncingRefs = false,
                            refsSyncProgress = "",
                            refsCountSota = report.counts[ActivityProgram.SOTA] ?: 0,
                            refsCountPota = report.counts[ActivityProgram.POTA] ?: 0,
                            refsCountWwff = report.counts[ActivityProgram.WWFF] ?: 0,
                            refsCountCota = report.counts[ActivityProgram.COTA] ?: 0,
                            refsCountIota = report.counts[ActivityProgram.IOTA] ?: 0,
                            refsLastSyncLabel = syncFmt.format(Instant.ofEpochMilli(now)),
                            refsSyncNote = note,
                            importMessage = UiText.Resource(
                                R.string.refs_sync_done,
                                report.counts.values.sum()
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isSyncingRefs = false,
                            refsSyncProgress = "",
                            importMessage = UiText.Resource(
                                R.string.refs_sync_failed,
                                e.localizedMessage ?: e::class.simpleName.orEmpty()
                            )
                        )
                    }
                }
            )
        }
    }

    fun testDbConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isTestingDb = true, dbTestResult = null) }
            authStore.apiBaseUrl = _state.value.apiUrl
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
            authStore.apiBaseUrl = s.apiUrl
            prefs.update {
                it[AppPrefs.QRZ_USER] = s.qrzUser
                it[AppPrefs.QRZ_PASSWORD] = s.qrzPassword
                it[AppPrefs.HAMQTH_USER] = s.hamqthUser
                it[AppPrefs.HAMQTH_PASSWORD] = s.hamqthPassword
                it[AppPrefs.CLUBLOG_API_KEY] = s.clublogApiKey
                it[AppPrefs.MY_CALLSIGN] = s.myCallsign
                it[AppPrefs.MY_GRIDSQUARE] = s.myGridsquare
                it[AppPrefs.MY_NAME] = s.myName
                it[AppPrefs.MY_RIG] = s.myRig
                it[AppPrefs.MY_DXCC] = s.myDxcc
                it[AppPrefs.DEFAULT_RST_SENT] = s.defaultRstSent
                it[AppPrefs.DEFAULT_RST_RCVD] = s.defaultRstRcvd
                it[AppPrefs.DEFAULT_BAND] = s.defaultBand
                it[AppPrefs.DEFAULT_MODE] = s.defaultMode
                it[AppPrefs.DEFAULT_TXPWR] = s.defaultTxpwr
                it[AppPrefs.RADIUS_SOTA_M] = s.radiusSotaM.toIntOrNull()?.coerceAtLeast(1) ?: 200
                it[AppPrefs.RADIUS_POTA_M] = s.radiusPotaM.toIntOrNull()?.coerceAtLeast(1) ?: 800
                it[AppPrefs.RADIUS_WWFF_M] = s.radiusWwffM.toIntOrNull()?.coerceAtLeast(1) ?: 500
                it[AppPrefs.RADIUS_COTA_M] = s.radiusCotaM.toIntOrNull()?.coerceAtLeast(1) ?: 1000
                it[AppPrefs.RADIUS_IOTA_M] = s.radiusIotaM.toIntOrNull()?.coerceAtLeast(1) ?: 5000
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
