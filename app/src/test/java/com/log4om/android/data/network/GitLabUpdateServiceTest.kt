package com.log4om.android.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitLabUpdateServiceTest {

    @Test
    fun manifestUrlIsThePublicGitLabPackage() {
        val url = GitLabUpdateService.DEFAULT_MANIFEST_URL
        assertEquals(
            "https://git.lets-host.de/api/v4/projects/df3mt%2FLog4OM2-Android/" +
                "packages/generic/log4om-android/latest/release.json",
            url
        )
        assertFalse(url.contains("github.com"))
        assertFalse(url.contains("private_token"))
        assertFalse(url.contains("JOB-TOKEN"))
    }

    @Test
    fun buildTagMapsToVersionCode() {
        assertEquals(10, versionCodeFromRelease("build-10", "Log4OM Android 1.0.10"))
        assertEquals("1.0.10", versionNameFromRelease("build-10", "Log4OM Android 1.0.10"))
        assertEquals(11, versionCodeFromRelease("build-11", "unused"))
        assertEquals("1.0.11", versionNameFromRelease("build-11", "unused"))
    }

    @Test
    fun manifestPrefersLatestApkAndKeepsBuildNumber() {
        val info = parseReleaseManifest(
            """
            {
              "tag_name": "build-11",
              "name": "Log4OM Android 1.0.11",
              "body": "from GitLab",
              "html_url": "https://git.lets-host.de/df3mt/Log4OM2-Android/-/releases/build-11",
              "assets": [
                {
                  "name": "Log4OM-Android-1.0.11.apk",
                  "browser_download_url": "https://git.lets-host.de/versioned.apk"
                },
                {
                  "name": "Log4OM-Android-latest.apk",
                  "browser_download_url": "https://git.lets-host.de/latest.apk"
                }
              ]
            }
            """.trimIndent()
        )
        assertEquals("build-11", info.tag)
        assertEquals(11, info.versionCode)
        assertEquals("1.0.11", info.versionName)
        assertEquals("from GitLab", info.releaseNotes)
        assertEquals("https://git.lets-host.de/latest.apk", info.apkDownloadUrl)
        assertTrue(info.htmlUrl.contains("git.lets-host.de"))
    }
}
