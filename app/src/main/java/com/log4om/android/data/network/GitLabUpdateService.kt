package com.log4om.android.data.network

import com.log4om.android.data.model.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * In-app updates come from the GitLab generic package registry on git.lets-host.de.
 *
 * The project is private, so the Releases API is not reachable from a phone without a
 * token. CI publishes `release.json` (and the APK) as package `log4om-android` / `latest`
 * and turns on anonymous package pull. No credential is embedded in the app.
 *
 * Tags stay `build-N` → versionCode N, versionName `1.0.N`.
 */
class GitLabUpdateService(
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchLatestRelease(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "Log4OM-Android")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("GitLab HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parseReleaseManifest(body)
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

    companion object {
        const val PROJECT_PATH = "df3mt/Log4OM2-Android"
        const val HOST = "https://git.lets-host.de"
        const val PACKAGE_NAME = "log4om-android"

        val DEFAULT_MANIFEST_URL: String =
            "$HOST/api/v4/projects/${PROJECT_PATH.replace("/", "%2F")}" +
                "/packages/generic/$PACKAGE_NAME/latest/release.json"
    }
}

internal fun parseReleaseManifest(json: String): AppUpdateInfo {
    val root = JSONObject(json)
    val tag = root.optString("tag_name")
    val name = root.optString("name").ifBlank { tag }
    val notes = root.optString("body").orEmpty().trim()
    val htmlUrl = root.optString("html_url")
    val versionCode = versionCodeFromRelease(tag, name)
        ?: error("Cannot parse version from tag='$tag' name='$name'")
    val versionName = versionNameFromRelease(tag, name) ?: "1.0.$versionCode"

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

internal fun versionCodeFromRelease(tag: String, name: String): Int? {
    Regex("""build-(\d+)""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)?.toIntOrNull()
        ?.let { return it }
    Regex("""(\d+)\.(\d+)\.(\d+)""").find(name)?.groupValues?.get(3)?.toIntOrNull()
        ?.let { return it }
    Regex("""(\d+)\.(\d+)\.(\d+)""").find(tag)?.groupValues?.get(3)?.toIntOrNull()
        ?.let { return it }
    return Regex("""(\d+)""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
}

internal fun versionNameFromRelease(tag: String, name: String): String? {
    Regex("""(\d+\.\d+\.\d+)""").find(name)?.groupValues?.get(1)?.let { return it }
    Regex("""(\d+\.\d+\.\d+)""").find(tag)?.groupValues?.get(1)?.let { return it }
    val code = versionCodeFromRelease(tag, name) ?: return null
    return "1.0.$code"
}
