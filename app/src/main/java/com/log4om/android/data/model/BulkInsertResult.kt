package com.log4om.android.data.model

data class BulkInsertResult(val inserted: Int, val skipped: Int)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
    val userId: String = "",
    val tenantId: String = "",
    val expiresInSeconds: Long = 0
)
