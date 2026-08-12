package com.log4om.android.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager

class DatabaseHelper {

    private var host: String = ""
    private var port: Int = 3306
    private var database: String = ""
    private var user: String = ""
    private var password: String = ""

    fun configure(host: String, port: Int, database: String, user: String, password: String) {
        this.host = host
        this.port = port
        this.database = database
        this.user = user
        this.password = password
    }

    private fun buildUrl(): String =
        "jdbc:mysql://$host:$port/$database" +
        "?useSSL=false" +
        "&useUnicode=true" +
        "&characterEncoding=UTF-8" +
        "&useLegacyDatetimeCode=false" +
        "&serverTimezone=UTC" +
        "&connectTimeout=5000" +
        "&socketTimeout=15000" +
        "&autoReconnect=true"

    suspend fun <T> withConnection(block: suspend (Connection) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                Class.forName("com.mysql.jdbc.Driver")
                val conn = DriverManager.getConnection(buildUrl(), user, password)
                conn.use { block(it) }
            }
        }

    suspend fun testConnection(): Result<Unit> =
        withConnection { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT 1").close()
            }
        }
}
