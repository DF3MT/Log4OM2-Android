package com.log4om.android.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "log4om_settings")

class AppPrefs(private val context: Context) {

    companion object {
        val QRZ_USER     = stringPreferencesKey("qrz_user")
        val QRZ_PASSWORD = stringPreferencesKey("qrz_password")

        val HAMQTH_USER     = stringPreferencesKey("hamqth_user")
        val HAMQTH_PASSWORD = stringPreferencesKey("hamqth_password")
        val CLUBLOG_API_KEY = stringPreferencesKey("clublog_api_key")

        val MY_CALLSIGN   = stringPreferencesKey("my_callsign")
        val MY_GRIDSQUARE = stringPreferencesKey("my_gridsquare")
        val MY_NAME       = stringPreferencesKey("my_name")
        val MY_RIG        = stringPreferencesKey("my_rig")
        val MY_DXCC       = stringPreferencesKey("my_dxcc")

        val DEFAULT_RST_SENT = stringPreferencesKey("default_rst_sent")
        val DEFAULT_RST_RCVD = stringPreferencesKey("default_rst_rcvd")
        val DEFAULT_BAND     = stringPreferencesKey("default_band")
        val DEFAULT_MODE     = stringPreferencesKey("default_mode")
        val DEFAULT_TXPWR    = stringPreferencesKey("default_txpwr")

        val RADIUS_SOTA_M = intPreferencesKey("radius_sota_m")
        val RADIUS_POTA_M = intPreferencesKey("radius_pota_m")
        val RADIUS_WWFF_M = intPreferencesKey("radius_wwff_m")
        val RADIUS_COTA_M = intPreferencesKey("radius_cota_m")
        val RADIUS_IOTA_M = intPreferencesKey("radius_iota_m")
        val REFS_LAST_SYNC_MS = longPreferencesKey("refs_last_sync_ms")
        val REFS_LAST_SYNC_NOTE = stringPreferencesKey("refs_last_sync_note")
    }

    val qrzUser:     Flow<String> = context.dataStore.data.map { it[QRZ_USER]     ?: "" }
    val qrzPassword: Flow<String> = context.dataStore.data.map { it[QRZ_PASSWORD] ?: "" }

    val hamqthUser:     Flow<String> = context.dataStore.data.map { it[HAMQTH_USER]     ?: "" }
    val hamqthPassword: Flow<String> = context.dataStore.data.map { it[HAMQTH_PASSWORD] ?: "" }
    val clublogApiKey:  Flow<String> = context.dataStore.data.map { it[CLUBLOG_API_KEY] ?: "" }

    val myCallsign:   Flow<String> = context.dataStore.data.map { it[MY_CALLSIGN]   ?: "" }
    val myGridsquare: Flow<String> = context.dataStore.data.map { it[MY_GRIDSQUARE] ?: "" }
    val myName:       Flow<String> = context.dataStore.data.map { it[MY_NAME]       ?: "" }
    val myRig:        Flow<String> = context.dataStore.data.map { it[MY_RIG]        ?: "" }
    val myDxcc:       Flow<String> = context.dataStore.data.map { it[MY_DXCC]       ?: "" }

    val defaultRstSent: Flow<String> = context.dataStore.data.map { it[DEFAULT_RST_SENT] ?: "59" }
    val defaultRstRcvd: Flow<String> = context.dataStore.data.map { it[DEFAULT_RST_RCVD] ?: "59" }
    val defaultBand:    Flow<String> = context.dataStore.data.map { it[DEFAULT_BAND]     ?: "20m" }
    val defaultMode:    Flow<String> = context.dataStore.data.map { it[DEFAULT_MODE]     ?: "SSB" }
    val defaultTxpwr:   Flow<String> = context.dataStore.data.map { it[DEFAULT_TXPWR]   ?: "" }

    val radiusSotaM: Flow<Int> = context.dataStore.data.map { it[RADIUS_SOTA_M] ?: 200 }
    val radiusPotaM: Flow<Int> = context.dataStore.data.map { it[RADIUS_POTA_M] ?: 800 }
    val radiusWwffM: Flow<Int> = context.dataStore.data.map { it[RADIUS_WWFF_M] ?: 500 }
    val radiusCotaM: Flow<Int> = context.dataStore.data.map { it[RADIUS_COTA_M] ?: 1000 }
    val radiusIotaM: Flow<Int> = context.dataStore.data.map { it[RADIUS_IOTA_M] ?: 5000 }
    val refsLastSyncMs: Flow<Long> = context.dataStore.data.map { it[REFS_LAST_SYNC_MS] ?: 0L }
    val refsLastSyncNote: Flow<String> = context.dataStore.data.map { it[REFS_LAST_SYNC_NOTE] ?: "" }

    suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }
}
