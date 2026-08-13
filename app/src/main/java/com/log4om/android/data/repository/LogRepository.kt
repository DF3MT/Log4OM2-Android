package com.log4om.android.data.repository

import com.log4om.android.data.adif.AdifWriter
import com.log4om.android.data.model.BulkInsertResult
import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.Qso
import com.log4om.android.data.model.QrzCallsignData
import com.log4om.android.data.network.ClubLogApiService
import com.log4om.android.data.network.HamQthApiService
import com.log4om.android.data.network.Log4omApiService
import com.log4om.android.data.network.QrzApiService
import com.log4om.android.data.prefs.AppPrefs
import kotlinx.coroutines.flow.first
import java.io.InputStream

class LogRepository(
    val prefs: AppPrefs,
    private val api: Log4omApiService,
    private val qrzApiService: QrzApiService,
    private val hamQthApiService: HamQthApiService,
    private val clubLogApiService: ClubLogApiService
) {
    private suspend fun ensureQrzConfigured() {
        qrzApiService.configure(
            user = prefs.qrzUser.first(),
            password = prefs.qrzPassword.first()
        )
    }

    private suspend fun ensureLookupConfigured() {
        ensureQrzConfigured()
        hamQthApiService.configure(
            user = prefs.hamqthUser.first(),
            password = prefs.hamqthPassword.first()
        )
        clubLogApiService.configure(prefs.clublogApiKey.first())
    }

    suspend fun testDbConnection(): Result<Unit> = api.testTenantDb()

    suspend fun getRecentQsos(limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        api.listQsos(LogFilter(), limit, offset)

    suspend fun searchQsos(query: String, limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        api.listQsos(LogFilter(callsign = query), limit, offset)

    suspend fun queryQsos(filter: LogFilter, limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        api.listQsos(filter, limit, offset)

    suspend fun countQsos(filter: LogFilter): Result<Int> = api.countQsos(filter)

    suspend fun getFilteredQsoIds(filter: LogFilter): Result<List<Long>> = api.filteredIds(filter)

    suspend fun getQsosByIds(ids: List<Long>): Result<List<Qso>> = api.qsosByIds(ids)

    suspend fun exportAdif(ids: List<Long>): Result<String> {
        val remote = api.exportAdif(ids)
        if (remote.isSuccess) return remote
        // Fallback: client-side ADIF from fetched rows
        return api.qsosByIds(ids).map { AdifWriter.toAdif(it) }
    }

    suspend fun insertQso(qso: Qso): Result<Boolean> =
        api.createQso(qso).map { true }

    suspend fun updateQso(qso: Qso): Result<Boolean> =
        api.updateQso(qso.qsoid, qso).map { true }

    suspend fun deleteQso(qsoid: Long): Result<Boolean> =
        api.deleteQso(qsoid).map { true }

    suspend fun getQsoCount(): Result<Int> = api.countQsos(LogFilter())

    /**
     * Multi-source lookup: QRZ → HamQTH → Club Log (DXCC-only fill).
     * Returns the richest successful payload; last error if all fail.
     */
    suspend fun lookupCallsign(callsign: String): Result<QrzCallsignData> {
        ensureLookupConfigured()
        val call = callsign.trim()
        if (call.isBlank()) return Result.failure(IllegalArgumentException("Empty callsign"))

        var best: QrzCallsignData? = null
        var lastError: String? = null

        if (qrzApiService.isConfigured) {
            qrzApiService.lookupCallsign(call).fold(
                onSuccess = { data ->
                    if (data.hasUsefulData) best = data
                    else lastError = data.error ?: lastError
                },
                onFailure = { lastError = it.message ?: lastError }
            )
        }

        if (best == null || !best!!.hasUsefulData) {
            if (hamQthApiService.isConfigured) {
                hamQthApiService.lookupCallsign(call).fold(
                    onSuccess = { data ->
                        if (data.hasUsefulData) {
                            best = mergeLookup(best, data)
                        } else lastError = data.error ?: lastError
                    },
                    onFailure = { lastError = it.message ?: lastError }
                )
            }
        } else if (hamQthApiService.isConfigured &&
            (best!!.name.isBlank() || best!!.grid.isBlank() || best!!.dxcc.isBlank())
        ) {
            hamQthApiService.lookupCallsign(call).onSuccess { data ->
                if (data.hasUsefulData) best = mergeLookup(best, data)
            }
        }

        val current = best
        if (clubLogApiService.isConfigured &&
            (current == null || current.dxcc.isBlank() || current.country.isBlank())
        ) {
            clubLogApiService.lookupDxcc(call).fold(
                onSuccess = { data -> best = mergeLookup(best, data) },
                onFailure = { err ->
                    if (best == null) lastError = err.message ?: lastError
                }
            )
        }

        val result = best
        return when {
            result != null && result.hasUsefulData -> Result.success(result)
            result != null && result.error != null -> Result.success(result)
            else -> Result.failure(Exception(lastError ?: "Callsign lookup failed"))
        }
    }

    private fun mergeLookup(primary: QrzCallsignData?, secondary: QrzCallsignData): QrzCallsignData {
        if (primary == null) return secondary
        fun pick(a: String, b: String) = a.ifBlank { b }
        return primary.copy(
            call = pick(primary.call, secondary.call),
            fname = pick(primary.fname, secondary.fname),
            name = pick(primary.name, secondary.name),
            addr1 = pick(primary.addr1, secondary.addr1),
            addr2 = pick(primary.addr2, secondary.addr2),
            state = pick(primary.state, secondary.state),
            country = pick(primary.country, secondary.country),
            cqzone = pick(primary.cqzone, secondary.cqzone),
            ituzone = pick(primary.ituzone, secondary.ituzone),
            lat = pick(primary.lat, secondary.lat),
            lon = pick(primary.lon, secondary.lon),
            grid = pick(primary.grid, secondary.grid),
            email = pick(primary.email, secondary.email),
            dxcc = pick(primary.dxcc, secondary.dxcc),
            continent = pick(primary.continent, secondary.continent),
            pfx = pick(primary.pfx, secondary.pfx),
            bio = pick(primary.bio, secondary.bio),
            image = pick(primary.image, secondary.image),
            error = null,
            source = listOf(primary.source, secondary.source)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("+")
        )
    }

    suspend fun getWorkedDxccIds(): Result<Set<Int>> = api.workedDxccIds()

    suspend fun getWorkedDxccBands(): Result<Set<Pair<Int, String>>> = api.workedDxccBands()

    suspend fun getQsosByCallsign(callsign: String, limit: Int = 15): Result<List<Qso>> =
        api.byCallsign(callsign, limit)

    /**
     * Upload ADIF stream to the API. [onProgress] is best-effort (bytes read proxy).
     */
    suspend fun importAdif(
        input: InputStream,
        onProgress: (parsed: Int) -> Unit = {}
    ): Result<BulkInsertResult> = runCatching {
        val bytes = input.use { it.readBytes() }
        onProgress(1)
        api.importAdif(bytes).getOrThrow().also { onProgress(2) }
    }
}
