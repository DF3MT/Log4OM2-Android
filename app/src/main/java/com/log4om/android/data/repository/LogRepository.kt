package com.log4om.android.data.repository

import com.log4om.android.data.adif.AdifMapper
import com.log4om.android.data.adif.AdifParser
import com.log4om.android.data.adif.AdifWriter
import com.log4om.android.data.db.BulkInsertResult
import com.log4om.android.data.db.DatabaseHelper
import com.log4om.android.data.db.QsoDao
import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.Qso
import com.log4om.android.data.model.QrzCallsignData
import com.log4om.android.data.network.ClubLogApiService
import com.log4om.android.data.network.HamQthApiService
import com.log4om.android.data.network.QrzApiService
import com.log4om.android.data.prefs.AppPrefs
import kotlinx.coroutines.flow.first
import java.io.InputStream

class LogRepository(
    val prefs: AppPrefs,
    private val dbHelper: DatabaseHelper,
    private val qsoDao: QsoDao,
    private val qrzApiService: QrzApiService,
    private val hamQthApiService: HamQthApiService,
    private val clubLogApiService: ClubLogApiService
) {
    private suspend fun ensureDbConfigured() {
        dbHelper.configure(
            host     = prefs.dbHost.first(),
            port     = prefs.dbPort.first(),
            database = prefs.dbName.first(),
            user     = prefs.dbUser.first(),
            password = prefs.dbPassword.first()
        )
    }

    private suspend fun ensureQrzConfigured() {
        qrzApiService.configure(
            user     = prefs.qrzUser.first(),
            password = prefs.qrzPassword.first()
        )
    }

    private suspend fun ensureLookupConfigured() {
        ensureQrzConfigured()
        hamQthApiService.configure(
            user     = prefs.hamqthUser.first(),
            password = prefs.hamqthPassword.first()
        )
        clubLogApiService.configure(prefs.clublogApiKey.first())
    }

    suspend fun testDbConnection(): Result<Unit> {
        ensureDbConfigured()
        return dbHelper.testConnection()
    }

    suspend fun getRecentQsos(limit: Int = 100, offset: Int = 0): Result<List<Qso>> {
        ensureDbConfigured()
        return qsoDao.getRecentQsos(limit, offset)
    }

    suspend fun searchQsos(query: String, limit: Int = 100, offset: Int = 0): Result<List<Qso>> {
        ensureDbConfigured()
        return qsoDao.searchQsos(query, limit, offset)
    }

    suspend fun queryQsos(filter: LogFilter, limit: Int = 100, offset: Int = 0): Result<List<Qso>> {
        ensureDbConfigured()
        return qsoDao.queryFiltered(filter, limit, offset)
    }

    suspend fun countQsos(filter: LogFilter): Result<Int> {
        ensureDbConfigured()
        return qsoDao.countFiltered(filter)
    }

    suspend fun getFilteredQsoIds(filter: LogFilter): Result<List<Long>> {
        ensureDbConfigured()
        return qsoDao.getFilteredIds(filter)
    }

    suspend fun getQsosByIds(ids: List<Long>): Result<List<Qso>> {
        ensureDbConfigured()
        return qsoDao.getQsosByIds(ids)
    }

    suspend fun exportAdif(ids: List<Long>): Result<String> {
        ensureDbConfigured()
        return qsoDao.getQsosByIds(ids).map { AdifWriter.toAdif(it) }
    }

    suspend fun insertQso(qso: Qso): Result<Boolean> {
        ensureDbConfigured()
        return qsoDao.insertQso(qso)
    }

    suspend fun updateQso(qso: Qso): Result<Boolean> {
        ensureDbConfigured()
        return qsoDao.updateQso(qso)
    }

    suspend fun deleteQso(qsoid: Long): Result<Boolean> {
        ensureDbConfigured()
        return qsoDao.deleteQso(qsoid)
    }

    suspend fun getQsoCount(): Result<Int> {
        ensureDbConfigured()
        return qsoDao.getQsoCount()
    }

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
                    // Don't replace a successful callbook result with Club Log noise
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

    suspend fun getWorkedDxccIds(): Result<Set<Int>> {
        ensureDbConfigured()
        return qsoDao.getWorkedDxccIds()
    }

    suspend fun getWorkedDxccBands(): Result<Set<Pair<Int, String>>> {
        ensureDbConfigured()
        return qsoDao.getWorkedDxccBands()
    }

    suspend fun getQsosByCallsign(callsign: String, limit: Int = 15): Result<List<Qso>> {
        ensureDbConfigured()
        return qsoDao.getQsosByCallsign(callsign, limit)
    }

    /**
     * Parse ADIF stream and insert in chunks. Reports parsed-record count via [onProgress].
     * Existing rows (matched by composite PK mode+qsodate+band+callsign) are silently skipped.
     */
    suspend fun importAdif(
        input: InputStream,
        onProgress: (parsed: Int) -> Unit = {}
    ): Result<BulkInsertResult> {
        ensureDbConfigured()
        var totalInserted = 0
        var totalSkipped = 0
        var totalInvalid = 0
        var parsed = 0
        val chunk = ArrayList<Qso>(500)
        val baseId = System.currentTimeMillis()
        return runCatching {
            input.use { stream ->
                AdifParser.parse(stream).forEach { rec ->
                    val qso = AdifMapper.toQso(rec, baseId + parsed)
                    if (qso != null) chunk.add(qso) else totalInvalid++
                    parsed++
                    if (chunk.size >= 500) {
                        val r = qsoDao.bulkInsert(chunk).getOrThrow()
                        totalInserted += r.inserted
                        totalSkipped += r.skipped
                        chunk.clear()
                        onProgress(parsed)
                    }
                }
                if (chunk.isNotEmpty()) {
                    val r = qsoDao.bulkInsert(chunk).getOrThrow()
                    totalInserted += r.inserted
                    totalSkipped += r.skipped
                    onProgress(parsed)
                }
            }
            BulkInsertResult(inserted = totalInserted, skipped = totalSkipped + totalInvalid)
        }
    }
}
