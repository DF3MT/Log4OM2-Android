package com.log4om.android.data.network

import com.log4om.android.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GitHubUpdateService(
    private val owner: String = "DF3MT",
    private val repo: String = "Log4OM2-Android",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchLatestRelease(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$owner/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Log4OM-Android")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("GitHub HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseRelease(body)
            }
        }
    }

    suspend fun downloadApk(url: String, target: java.io.File): Result<java.io.File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Log4OM-Android")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Download HTTP ${response.code}")
                    val bytes = response.body?.bytes() ?: error("Empty APK body")
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                    target
                }
            }
        }

    private fun parseRelease(json: String): AppUpdateInfo {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        val name = root.optString("name").ifBlank { tag }
        val notes = root.optString("body").orEmpty().trim()
        val htmlUrl = root.optString("html_url")
        val versionCode = parseVersionCode(tag, name)
            ?: error("Cannot parse version from tag='$tag' name='$name'")
        val versionName = parseVersionName(tag, name) ?: "1.0.$versionCode"

        val assets = root.optJSONArray("assets") ?: error("No release assets")
        var latestUrl: String? = null
        var versionedUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val assetName = asset.optString("name")
            val url = asset.optString("browser_download_url")
            when {
                assetName.equals("Log4OM-Android-latest.apk", ignoreCase = true) -> latestUrl = url
                assetName.endsWith(".apk", ignoreCase = true) && versionedUrl == null -> versionedUrl = url
            }
        }
        val apkUrl = latestUrl ?: versionedUrl ?: error("No APK asset in latest release")
        return AppUpdateInfo(
            tag = tag,
            versionName = versionName,
            versionCode = versionCode,
            releaseNotes = notes,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl
        )
    }

    private fun parseVersionCode(tag: String, name: String): Int? {
        Regex("""build-(\d+)""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
            ?.let { return it }
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(name)?.groupValues?.get(3)?.toIntOrNull()
            ?.let { return it }
        Regex("""(\d+)\.(\d+)\.(\d+)""").find(tag)?.groupValues?.get(3)?.toIntOrNull()
            ?.let { return it }
        return Regex("""(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseVersionName(tag: String, name: String): String? {
        Regex("""(\d+\.\d+\.\d+)""").find(name)?.groupValues?.get(1)?.let { return it }
        Regex("""(\d+\.\d+\.\d+)""").find(tag)?.groupValues?.get(1)?.let { return it }
        val code = parseVersionCode(tag, name) ?: return null
        return "1.0.$code"
    }
}
