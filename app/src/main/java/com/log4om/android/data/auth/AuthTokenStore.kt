package com.log4om.android.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthTokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "log4om_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Fallback if Keystore is unavailable on a device/emulator
        context.getSharedPreferences("log4om_auth_fallback", Context.MODE_PRIVATE)
    }

    var apiBaseUrl: String
        get() {
            val stored = prefs.getString(KEY_API_URL, null)?.trim()?.trimEnd('/')
            val resolved = if (stored.isNullOrBlank() || stored in LEGACY_API_URLS) {
                DEFAULT_API_URL
            } else {
                stored
            }
            if (stored != resolved) {
                prefs.edit().putString(KEY_API_URL, resolved).apply()
            }
            return resolved
        }
        set(value) = prefs.edit().putString(KEY_API_URL, value.trim().trimEnd('/')).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun saveSession(
        accessToken: String,
        refreshToken: String,
        email: String,
        apiBaseUrl: String? = null
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_EMAIL, email)
            .apply {
                if (!apiBaseUrl.isNullOrBlank()) putString(KEY_API_URL, apiBaseUrl.trim().trimEnd('/'))
            }
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EMAIL)
            .apply()
    }

    companion object {
        /** Public portal. /api is proxied to the Log4OM API. */
        const val DEFAULT_API_URL = "https://log4om.df3mt.de/api"
        private val LEGACY_API_URLS = setOf(
            "http://192.168.96.191:8080",
            "http://10.0.2.2:8080",
            "https://log4om-api.df3mt.de",
            "http://log4om-api.df3mt.de",
        )
        private const val KEY_API_URL = "api_base_url"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EMAIL = "email"
    }
}
