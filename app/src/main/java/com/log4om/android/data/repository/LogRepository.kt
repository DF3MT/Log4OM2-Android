package com.log4om.android.data.repository

import com.log4om.android.data.adif.AdifMapper
import com.log4om.android.data.adif.AdifParser
import com.log4om.android.data.db.BulkInsertResult
import com.log4om.android.data.db.DatabaseHelper
import com.log4om.android.data.db.QsoDao
import com.log4om.android.data.model.Qso
import com.log4om.android.data.model.QrzCallsignData
import com.log4om.android.data.network.QrzApiService
import com.log4om.android.data.prefs.AppPrefs
import kotlinx.coroutines.flow.first
import java.io.InputStream

class LogRepository(
    val prefs: AppPrefs,
    private val dbHelper: DatabaseHelper,
    private val qsoDao: QsoDao,
    private val qrzApiService: QrzApiService
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

    suspend fun lookupCallsign(callsign: String): Result<QrzCallsignData> {
        ensureQrzConfigured()
        return qrzApiService.lookupCallsign(callsign)
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
