package com.log4om.android.data.refs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Syncs Europe activity catalogues from official public feeds into [ReferenceCatalog].
 * COTA / WWFF / IOTA: no redistributable public coordinate feed wired yet — empty with note.
 */
class ReferenceSyncService(
    private val catalog: ReferenceCatalog,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
) {

    suspend fun syncAll(
        onProgress: (SyncProgress) -> Unit = {}
    ): Result<SyncReport> = withContext(Dispatchers.IO) {
        runCatching {
            val counts = linkedMapOf<ActivityProgram, Int>()
            val notes = mutableListOf<String>()

            onProgress(SyncProgress(ActivityProgram.SOTA, "SOTA CSV…"))
            val sota = syncSota()
            counts[ActivityProgram.SOTA] = sota
            onProgress(SyncProgress(ActivityProgram.SOTA, "SOTA: $sota", sota, done = true))

            onProgress(SyncProgress(ActivityProgram.POTA, "POTA parks…"))
            val pota = syncPota { msg, n ->
                onProgress(SyncProgress(ActivityProgram.POTA, msg, n))
            }
            counts[ActivityProgram.POTA] = pota
            onProgress(SyncProgress(ActivityProgram.POTA, "POTA: $pota", pota, done = true))

            // Honest stubs until official redistributable feeds are available
            catalog.replace(ActivityProgram.IOTA, emptySequence())
            catalog.replace(ActivityProgram.WWFF, emptySequence())
            catalog.replace(ActivityProgram.COTA, emptySequence())
            counts[ActivityProgram.IOTA] = 0
            counts[ActivityProgram.WWFF] = 0
            counts[ActivityProgram.COTA] = 0
            notes += "IOTA/WWFF/COTA: no public coordinate feed yet (catalogue left empty)."
            onProgress(SyncProgress(ActivityProgram.IOTA, "IOTA: 0 (kein Feed)", 0, done = true))
            onProgress(SyncProgress(ActivityProgram.WWFF, "WWFF: 0 (kein Feed)", 0, done = true))
            onProgress(SyncProgress(ActivityProgram.COTA, "COTA: 0 (kein Feed)", 0, done = true))

            SyncReport(counts, notes)
        }
    }

    private fun syncSota(): Int {
        val req = Request.Builder()
            .url(SOTA_CSV)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/csv,*/*")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("SOTA HTTP ${resp.code}")
            val body = resp.body ?: error("SOTA leere Antwort")
            val kept = ArrayList<ActivityRef>(20_000)
            BufferedReader(InputStreamReader(body.byteStream(), StandardCharsets.UTF_8)).use { reader ->
                // Line 1: title, line 2: header
                reader.readLine()
                val header = reader.readLine() ?: error("SOTA CSV ohne Header")
                val cols = CsvLineParser.split(header)
                val iCode = cols.indexOf("SummitCode").takeIf { it >= 0 } ?: 0
                val iName = cols.indexOf("SummitName").takeIf { it >= 0 } ?: 3
                val iLon = cols.indexOf("Longitude").takeIf { it >= 0 } ?: 8
                val iLat = cols.indexOf("Latitude").takeIf { it >= 0 } ?: 9
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val f = CsvLineParser.split(line)
                    if (f.size <= maxOf(iCode, iName, iLon, iLat)) continue
                    val code = f[iCode].trim()
                    if (!EuropeScope.isEuropeanSota(code)) continue
                    val lat = f[iLat].trim().toDoubleOrNull() ?: continue
                    val lon = f[iLon].trim().toDoubleOrNull() ?: continue
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                    kept += ActivityRef(
                        program = ActivityProgram.SOTA,
                        reference = code.uppercase(),
                        name = f[iName].trim(),
                        lat = lat,
                        lon = lon,
                        country = EuropeScope.sotaAssociationOf(code)
                    )
                }
            }
            catalog.replace(ActivityProgram.SOTA, kept.asSequence())
            return kept.size
        }
    }

    private fun syncPota(onProg: (String, Int) -> Unit): Int {
        val programsReq = Request.Builder()
            .url(POTA_PROGRAMS)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val prefixes = client.newCall(programsReq).execute().use { resp ->
            if (!resp.isSuccessful) error("POTA programs HTTP ${resp.code}")
            val arr = JSONArray(resp.body?.string().orEmpty())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val prefix = o.optString("programPrefix")
                    val iso = o.optString("isocc")
                    if (prefix in EuropeScope.POTA_ISO || iso in EuropeScope.POTA_ISO) {
                        add(prefix)
                    }
                }
            }.distinct()
        }

        val byRef = LinkedHashMap<String, ActivityRef>()
        prefixes.forEachIndexed { idx, prefix ->
            onProg("POTA $prefix (${idx + 1}/${prefixes.size})", byRef.size)
            val parksReq = Request.Builder()
                .url("$POTA_PROGRAM_PARKS/$prefix")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()
            runCatching {
                client.newCall(parksReq).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val text = resp.body?.string().orEmpty()
                    if (text.isBlank() || text == "[]") return@use
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val ref = o.optString("reference").uppercase()
                        if (ref.isBlank()) continue
                        val lat = o.optDouble("latitude", Double.NaN)
                        val lon = o.optDouble("longitude", Double.NaN)
                        if (lat.isNaN() || lon.isNaN()) continue
                        byRef.putIfAbsent(
                            ref,
                            ActivityRef(
                                program = ActivityProgram.POTA,
                                reference = ref,
                                name = o.optString("name", ""),
                                lat = lat,
                                lon = lon,
                                country = prefix
                            )
                        )
                    }
                }
            }
        }
        catalog.replace(ActivityProgram.POTA, byRef.values.asSequence())
        return byRef.size
    }

    companion object {
        private const val USER_AGENT = "Log4OM-Android/1.0"
        private const val SOTA_CSV = "https://storage.sota.org.uk/summitslist.csv"
        private const val POTA_PROGRAMS = "https://api.pota.app/programs"
        private const val POTA_PROGRAM_PARKS = "https://api.pota.app/program/parks"
    }
}
