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
        val DB_HOST     = stringPreferencesKey("db_host")
        val DB_PORT     = intPreferencesKey("db_port")
        val DB_NAME     = stringPreferencesKey("db_name")
        val DB_USER     = stringPreferencesKey("db_user")
        val DB_PASSWORD = stringPreferencesKey("db_password")

        val QRZ_USER     = stringPreferencesKey("qrz_user")
        val QRZ_PASSWORD = stringPreferencesKey("qrz_password")

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
    }

    val dbHost:     Flow<String> = context.dataStore.data.map { it[DB_HOST]     ?: "" }
    val dbPort:     Flow<Int>    = context.dataStore.data.map { it[DB_PORT]     ?: 3306 }
    val dbName:     Flow<String> = context.dataStore.data.map { it[DB_NAME]     ?: "" }
    val dbUser:     Flow<String> = context.dataStore.data.map { it[DB_USER]     ?: "" }
    val dbPassword: Flow<String> = context.dataStore.data.map { it[DB_PASSWORD] ?: "" }

    val qrzUser:     Flow<String> = context.dataStore.data.map { it[QRZ_USER]     ?: "" }
    val qrzPassword: Flow<String> = context.dataStore.data.map { it[QRZ_PASSWORD] ?: "" }

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

    suspend fun update(block: suspend (MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }
}
