package com.log4om.android.data.model

data class LogbookConfig(
    val id: String = "",
    val displayName: String = "",
    val stationCallsign: String = "",
    val host: String = "",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "",
    val sslEnabled: Boolean = false,
    val passwordSet: Boolean = false,
    val qrzLogbookKeySet: Boolean = false,
    val eqslUser: String = "",
    val eqslPasswordSet: Boolean = false,
    val active: Boolean = false
)

data class LogbookDraft(
    val id: String = "",
    val displayName: String = "",
    val stationCallsign: String = "",
    val host: String = "",
    val port: Int = 3306,
    val database: String = "",
    val username: String = "",
    val password: String = "",
    val sslEnabled: Boolean = false,
    val qrzLogbookKey: String = "",
    val eqslUser: String = "",
    val eqslPassword: String = ""
)

data class RemoteSyncResult(
    val ok: Boolean = false,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val skipped: Int = 0,
    val code: String = "",
    val message: String = ""
)

data class RemoteProbe(
    val ok: Boolean = false,
    val message: String = ""
)
