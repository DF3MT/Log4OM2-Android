package com.log4om.android.data.model

data class AppUpdateInfo(
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String
)
