package com.log4om.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.log4om.android.R
import com.log4om.android.data.adif.CallsignCountry
import com.log4om.android.data.model.Qso
import com.log4om.android.data.model.QrzCallsignData
import com.log4om.android.data.prefs.AppPrefs
import com.log4om.android.data.repository.LogRepository
import com.log4om.android.ui.util.UiText
import com.log4om.android.util.AmateurRadio
import com.log4om.android.util.GridLocator
import com.log4om.android.util.LocationHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class DxccNeededStatus {
    UNKNOWN,
    NEW_DXCC,
    NEW_ON_BAND,
    WORKED
}

data class QsoFormState(
    val qsoid: Long = System.currentTimeMillis(),
    val callsign: String = "",
    val band: String = "20m",
    val mode: String = "SSB",
    val qsodate: LocalDateTime = LocalDateTime.now(),
    val freq: String = "14.225",
    val rstsent: String = "59",
    val rstrcvd: String = "59",
    val name: String = "",
    val address: String = "",
    val qth: String = "",
    val country: String = "",
    val dxcc: String = "",
    val cqzone: String = "",
    val ituzone: String = "",
    val gridsquare: String = "",
    val cont: String = "",
    val comment: String = "",
    val notes: String = "",
    val txpwr: String = "",
    val propmode: String = "",
    val contestid: String = "",
    val satmode: String = "",
    val satname: String = "",
    val sotaRef: String = "",
    val iota: String = "",
    val potaRef: String = "",
    val wwffRef: String = "",
    val contactLat: Double? = null,
    val contactLon: Double? = null,
    val stationLat: Double? = null,
    val stationLon: Double? = null,
    val distanceKm: Double? = null,
    val bearingDeg: Double? = null,
    val dxccNeeded: DxccNeededStatus = DxccNeededStatus.UNKNOWN,
    val lookupSource: String = "",
    val qrzData: QrzCallsignData? = null,
    val qrzLoading: Boolean = false,
    val qrzError: UiText? = null,
    val isSaving: Boolean = false,
    val saveError: UiText? = null,
    val saveSuccess: Boolean = false,
    val isEditMode: Boolean = false
)

class NewQsoViewModel(
    private val repository: LogRepository,
    private val prefs: AppPrefs,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _form = MutableStateFlow(QsoFormState())
    val form: StateFlow<QsoFormState> = _form.asStateFlow()

    private val _pastQsos = MutableStateFlow<List<Qso>>(emptyList())
    val pastQsos: StateFlow<List<Qso>> = _pastQsos.asStateFlow()

    private var workedDxcc: Set<Int> = emptySet()
    private var workedDxccBands: Set<Pair<Int, String>> = emptySet()
    private var myGridCached: String = ""

    @OptIn(FlowPreview::class)
    private fun observeCallsignForHistory() {
        viewModelScope.launch {
            _form.map { it.callsign.trim() }
                .distinctUntilChanged()
                .debounce(350)
                .collectLatest { call ->
                    if (call.length < 2) {
                        _pastQsos.value = emptyList()
                        return@collectLatest
                    }
                    repository.getQsosByCallsign(call, limit = 15).fold(
                        onSuccess = { list ->
                            val currentId = _form.value.qsoid
                            _pastQsos.value = list.filter { it.qsoid != currentId }
                        },
                        onFailure = { _pastQsos.value = emptyList() }
                    )
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeCallsignForQrz() {
        viewModelScope.launch {
            _form.map { it.callsign.trim() }
                .distinctUntilChanged()
                .debounce(700)
                .collectLatest { call ->
                    if (call.length < 3 || _form.value.isEditMode) return@collectLatest
                    lookupCallsign(silent = true)
                }
        }
    }

    init {
        observeCallsignForHistory()
        observeCallsignForQrz()
        viewModelScope.launch {
            val rstSent = prefs.defaultRstSent.first()
            val rstRcvd = prefs.defaultRstRcvd.first()
            val band    = prefs.defaultBand.first()
            val mode    = prefs.defaultMode.first()
            val txpwr   = prefs.defaultTxpwr.first()
            myGridCached = prefs.myGridsquare.first()
            _form.update {
                it.copy(
                    rstsent = rstSent,
                    rstrcvd = rstRcvd,
                    band    = band,
                    mode    = mode,
                    txpwr   = txpwr,
                    freq    = AmateurRadio.freqForBand(band).ifBlank { it.freq }
                )
            }
            refreshDxccCache()
        }
    }

    private suspend fun refreshDxccCache() {
        repository.getWorkedDxccIds().onSuccess { workedDxcc = it }
        repository.getWorkedDxccBands().onSuccess { workedDxccBands = it }
        refreshDxccNeeded()
    }

    private fun refreshDxccNeeded() {
        val dxcc = _form.value.dxcc.toIntOrNull() ?: 0
        val band = _form.value.band
        val status = when {
            dxcc <= 0 -> DxccNeededStatus.UNKNOWN
            dxcc !in workedDxcc -> DxccNeededStatus.NEW_DXCC
            (dxcc to band) !in workedDxccBands -> DxccNeededStatus.NEW_ON_BAND
            else -> DxccNeededStatus.WORKED
        }
        _form.update { it.copy(dxccNeeded = status) }
    }

    private fun refreshPathInfo(
        grid: String = _form.value.gridsquare,
        lat: Double? = _form.value.contactLat,
        lon: Double? = _form.value.contactLon
    ) {
        val my = myGridCached.ifBlank { null }
        val theirPoint = when {
            lat != null && lon != null -> GridLocator.LatLon(lat, lon)
            else -> GridLocator.parse(grid)
        }
        val myPoint = my?.let { GridLocator.parse(it) }
        if (myPoint != null && theirPoint != null) {
            val path = GridLocator.path(myPoint, theirPoint)
            _form.update {
                it.copy(
                    contactLat = theirPoint.lat,
                    contactLon = theirPoint.lon,
                    stationLat = myPoint.lat,
                    stationLon = myPoint.lon,
                    distanceKm = path.distanceKm,
                    bearingDeg = path.bearingDeg
                )
            }
        } else if (theirPoint != null) {
            _form.update {
                it.copy(
                    contactLat = theirPoint.lat,
                    contactLon = theirPoint.lon,
                    stationLat = myPoint?.lat,
                    stationLon = myPoint?.lon,
                    distanceKm = null,
                    bearingDeg = null
                )
            }
        } else {
            _form.update {
                it.copy(
                    contactLat = lat,
                    contactLon = lon,
                    stationLat = myPoint?.lat,
                    stationLon = myPoint?.lon,
                    distanceKm = null,
                    bearingDeg = null
                )
            }
        }
    }

    fun loadForEdit(qso: Qso) {
        _form.value = QsoFormState(
            qsoid      = qso.qsoid,
            callsign   = qso.callsign,
            band       = qso.band,
            mode       = qso.mode,
            qsodate    = qso.qsodate,
            freq       = if (qso.freq > 0) qso.freq.toString() else "",
            rstsent    = qso.rstsent,
            rstrcvd    = qso.rstrcvd,
            name       = qso.name,
            address    = qso.address,
            qth        = qso.qth,
            country    = qso.country,
            dxcc       = if (qso.dxcc > 0) qso.dxcc.toString() else "",
            cqzone     = qso.cqzone?.toString() ?: "",
            ituzone    = qso.ituzone?.toString() ?: "",
            gridsquare = qso.gridsquare,
            cont       = qso.cont,
            comment    = qso.comment,
            notes      = qso.notes,
            txpwr      = qso.txpwr?.toString() ?: "",
            propmode   = qso.propmode,
            contestid  = qso.contestid,
            satmode    = qso.satmode,
            satname    = qso.satname,
            sotaRef    = qso.sotaRef,
            iota       = qso.iota,
            potaRef    = qso.potaRef,
            wwffRef    = qso.wwffRef,
            contactLat = qso.lat,
            contactLon = qso.lon,
            distanceKm = qso.distance,
            isEditMode = true
        )
        refreshPathInfo(qso.gridsquare, qso.lat, qso.lon)
        refreshDxccNeeded()
    }

    fun updateCallsign(v: String) = _form.update {
        it.copy(callsign = v.uppercase().take(50), qrzData = null, qrzError = null, lookupSource = "")
    }

    fun updateBand(v: String) {
        _form.update {
            it.copy(band = v, freq = AmateurRadio.freqForBand(v).ifBlank { it.freq })
        }
        refreshDxccNeeded()
    }

    fun updateMode(v: String) = _form.update {
        val rst = AmateurRadio.defaultRstForMode(v)
        it.copy(mode = v, rstsent = rst, rstrcvd = rst)
    }

    fun updateDate(v: LocalDateTime)  = _form.update { it.copy(qsodate = v) }
    fun updateFreq(v: String)         = _form.update { it.copy(freq = v) }
    fun updateRstSent(v: String)      = _form.update { it.copy(rstsent = v.take(10)) }
    fun updateRstRcvd(v: String)      = _form.update { it.copy(rstrcvd = v.take(10)) }
    fun updateName(v: String)         = _form.update { it.copy(name = v) }
    fun updateAddress(v: String)      = _form.update { it.copy(address = v) }
    fun updateQth(v: String)          = _form.update { it.copy(qth = v) }
    fun updateCountry(v: String)      = _form.update { it.copy(country = v) }
    fun updateDxcc(v: String) {
        _form.update { it.copy(dxcc = v) }
        refreshDxccNeeded()
    }
    fun updateCqzone(v: String)       = _form.update { it.copy(cqzone = v) }
    fun updateItuzone(v: String)      = _form.update { it.copy(ituzone = v) }
    fun updateGridsquare(v: String) {
        _form.update { it.copy(gridsquare = v.uppercase().take(10)) }
        refreshPathInfo(grid = v.uppercase().take(10))
    }
    fun updateCont(v: String)         = _form.update { it.copy(cont = v) }
    fun updateComment(v: String)      = _form.update { it.copy(comment = v) }
    fun updateNotes(v: String)        = _form.update { it.copy(notes = v) }
    fun updateTxpwr(v: String)        = _form.update { it.copy(txpwr = v) }
    fun updatePropmode(v: String)     = _form.update { it.copy(propmode = v) }
    fun updateContestid(v: String)    = _form.update { it.copy(contestid = v) }
    fun updateSatmode(v: String)      = _form.update { it.copy(satmode = v) }
    fun updateSatname(v: String)      = _form.update { it.copy(satname = v) }
    fun updateSotaRef(v: String)      = _form.update { it.copy(sotaRef = v.uppercase().take(20)) }
    fun updateIota(v: String)         = _form.update { it.copy(iota = v.uppercase().take(20)) }
    fun updatePotaRef(v: String)      = _form.update { it.copy(potaRef = v.uppercase().take(20)) }
    fun updateWwffRef(v: String)      = _form.update { it.copy(wwffRef = v.uppercase().take(20)) }
    fun dismissQrzError()             = _form.update { it.copy(qrzError = null) }
    fun dismissSaveError()            = _form.update { it.copy(saveError = null) }

    fun lookupCallsign(silent: Boolean = false) {
        val call = _form.value.callsign.trim()
        if (call.isBlank()) return
        viewModelScope.launch {
            _form.update { it.copy(qrzLoading = true, qrzError = null) }
            repository.lookupCallsign(call).fold(
                onSuccess = { data ->
                    val apiError = data.error
                    if (apiError != null && !data.hasUsefulData) {
                        _form.update {
                            it.copy(
                                qrzLoading = false,
                                qrzError = if (silent) null else UiText.Raw(apiError)
                            )
                        }
                    } else {
                        val lat = data.lat.toDoubleOrNull()
                        val lon = data.lon.toDoubleOrNull()
                        _form.update { s ->
                            s.copy(
                                qrzLoading = false,
                                qrzData    = data,
                                lookupSource = data.source,
                                name       = data.name.ifBlank { s.name },
                                address    = data.addr1.ifBlank { s.address },
                                qth        = data.addr2.ifBlank { s.qth },
                                country    = data.country.ifBlank { s.country },
                                gridsquare = data.grid.ifBlank { s.gridsquare },
                                dxcc       = data.dxcc.ifBlank { s.dxcc },
                                cqzone     = data.cqzone.ifBlank { s.cqzone },
                                ituzone    = data.ituzone.ifBlank { s.ituzone },
                                cont       = data.continent.ifBlank { s.cont },
                                contactLat = lat ?: s.contactLat,
                                contactLon = lon ?: s.contactLon
                            )
                        }
                        refreshPathInfo(
                            grid = _form.value.gridsquare,
                            lat = _form.value.contactLat,
                            lon = _form.value.contactLon
                        )
                        refreshDxccNeeded()
                    }
                },
                onFailure = { e ->
                    _form.update {
                        it.copy(
                            qrzLoading = false,
                            qrzError = if (silent) null else {
                                e.localizedMessage?.let { msg ->
                                    UiText.Resource(R.string.error_qrz_detail, msg)
                                } ?: UiText.Resource(R.string.error_qrz)
                            }
                        )
                    }
                }
            )
        }
    }

    fun saveQso() {
        val s = _form.value
        if (s.callsign.isBlank()) {
            _form.update { it.copy(saveError = UiText.Resource(R.string.error_callsign_required)) }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(isSaving = true, saveError = null) }
            val myCallsign   = prefs.myCallsign.first()
            val myGridsquare = prefs.myGridsquare.first()
            val myName       = prefs.myName.first()
            val myRig        = prefs.myRig.first()
            val myDxcc       = prefs.myDxcc.first().toIntOrNull()
            val myCountry    = CallsignCountry.fromCallsign(myCallsign).orEmpty()
            val location     = locationHelper.currentLocation()
            val freqKhz      = (s.freq.toDoubleOrNull() ?: 0.0) * 1000.0

            val qso = Qso(
                qsoid           = s.qsoid,
                callsign        = s.callsign.uppercase().trim(),
                band            = s.band,
                bandrx          = s.band,
                mode            = s.mode,
                qsodate         = s.qsodate,
                freq            = freqKhz,
                freqrx          = freqKhz,
                rstsent         = s.rstsent,
                rstrcvd         = s.rstrcvd,
                name            = s.name,
                address         = s.address,
                qth             = s.qth,
                country         = s.country,
                dxcc            = s.dxcc.toIntOrNull() ?: 0,
                cqzone          = s.cqzone.toIntOrNull(),
                ituzone         = s.ituzone.toIntOrNull(),
                gridsquare      = s.gridsquare,
                lat             = s.contactLat,
                lon             = s.contactLon,
                distance        = s.distanceKm,
                cont            = s.cont,
                comment         = s.comment,
                notes           = s.notes,
                stationcallsign = myCallsign,
                mygridsquare    = myGridsquare,
                myname          = myName,
                myrig           = myRig,
                mycountry       = myCountry,
                mydxcc          = myDxcc,
                mylat           = location?.latitude,
                mylon           = location?.longitude,
                txpwr           = s.txpwr.toDoubleOrNull(),
                propmode        = s.propmode,
                contestid       = s.contestid,
                satmode         = s.satmode,
                satname         = s.satname,
                satelliteqso    = if (s.satname.isNotBlank()) 1 else 0,
                sotaRef         = s.sotaRef,
                iota            = s.iota,
                potaRef         = s.potaRef,
                wwffRef         = s.wwffRef,
                programid       = "Log4OM Android",
                programversion  = "1.0"
            )

            val result = if (s.isEditMode) repository.updateQso(qso) else repository.insertQso(qso)
            result.fold(
                onSuccess = {
                    val dxcc = qso.dxcc
                    if (dxcc > 0) {
                        workedDxcc = workedDxcc + dxcc
                        workedDxccBands = workedDxccBands + (dxcc to qso.band)
                    }
                    _form.update { it.copy(isSaving = false, saveSuccess = true) }
                },
                onFailure = { e ->
                    _form.update {
                        it.copy(
                            isSaving = false,
                            saveError = e.localizedMessage?.let { msg ->
                                UiText.Resource(R.string.error_save_failed_detail, msg)
                            } ?: UiText.Resource(R.string.error_save_failed)
                        )
                    }
                }
            )
        }
    }

    fun resetForm() {
        viewModelScope.launch {
            val rstSent = prefs.defaultRstSent.first()
            val rstRcvd = prefs.defaultRstRcvd.first()
            val band    = prefs.defaultBand.first()
            val mode    = prefs.defaultMode.first()
            val txpwr   = prefs.defaultTxpwr.first()
            myGridCached = prefs.myGridsquare.first()
            _form.value = QsoFormState(
                qsoid   = System.currentTimeMillis(),
                rstsent = rstSent,
                rstrcvd = rstRcvd,
                band    = band,
                mode    = mode,
                txpwr   = txpwr,
                freq    = AmateurRadio.freqForBand(band),
                qsodate = LocalDateTime.now()
            )
            _pastQsos.value = emptyList()
        }
    }

    fun clearSaveSuccess() = _form.update { it.copy(saveSuccess = false) }
}
