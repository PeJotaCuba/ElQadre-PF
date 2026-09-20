package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ApkUpdateManagerTest {

    @Test
    fun testOfficialVersionJsonUrlIsExact() {
        val expected = "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/version.json"
        assertEquals(expected, ApkUpdateManager.OFFICIAL_VERSION_JSON_URL)
    }

    @Test
    fun testParseVersionJson() {
        val sampleJson = """
            {
              "product": "ElQadre",
              "versionCode": 3,
              "versionName": "1.2",
              "apkUrl": "https://github.com/PeJotaCuba/BD-Qadre-PF/raw/refs/heads/main/ElQadre.apk",
              "releaseDate": "2026-09-16T07:37:11Z",
              "notes": "Correcciones"
            }
        """.trimIndent()

        val info = ApkUpdateManager.parseVersionJson(sampleJson)
        assertEquals("ElQadre", info.product)
        assertEquals(3L, info.versionCode)
        assertEquals("1.2", info.versionName)
        assertEquals("https://github.com/PeJotaCuba/BD-Qadre-PF/raw/refs/heads/main/ElQadre.apk", info.apkUrl)
    }

    @Test
    fun testIsUpdateAvailable() {
        // When remote versionCode is strictly greater than local versionCode
        assertTrue(ApkUpdateManager.isUpdateAvailable(localVersionCode = 1, remoteVersionCode = 2))
        assertTrue(ApkUpdateManager.isUpdateAvailable(localVersionCode = 2, remoteVersionCode = 3))

        // When remote versionCode is equal to local versionCode (already up to date)
        assertFalse(ApkUpdateManager.isUpdateAvailable(localVersionCode = 2, remoteVersionCode = 2))

        // When remote versionCode is lower than local versionCode (running newer/dev build)
        assertFalse(ApkUpdateManager.isUpdateAvailable(localVersionCode = 3, remoteVersionCode = 2))
    }

    @Test
    fun testLiveVersionJsonIsAccessibleAndValid() {
        val url = URL(ApkUpdateManager.OFFICIAL_VERSION_JSON_URL)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")

        val responseCode = conn.responseCode
        assertEquals(HttpURLConnection.HTTP_OK, responseCode)

        val text = conn.inputStream.bufferedReader().use { it.readText() }
        assertNotNull(text)
        assertTrue(text.isNotBlank())

        val remoteInfo = ApkUpdateManager.parseVersionJson(text)
        assertEquals("ElQadre", remoteInfo.product)
        assertTrue(remoteInfo.versionCode >= 1L)
        assertTrue(remoteInfo.apkUrl.isNotBlank())

        // Verification required by prompt:
        // 1. An APK with lower versionCode (e.g. 0) correctly detects an update available against remote version.json
        val olderLocalCode = 0L
        assertTrue(ApkUpdateManager.isUpdateAvailable(olderLocalCode, remoteInfo.versionCode))

        // 2. An APK with versionCode equal to or greater than remote correctly detects that it is already up-to-date
        val currentLocalCode = remoteInfo.versionCode
        assertFalse(ApkUpdateManager.isUpdateAvailable(currentLocalCode, remoteInfo.versionCode))

        val newerLocalCode = remoteInfo.versionCode + 1
        assertFalse(ApkUpdateManager.isUpdateAvailable(newerLocalCode, remoteInfo.versionCode))

        // 3. When a new APK is released with versionCode higher than current installed, it will detect the update
        val futureRemoteCode = newerLocalCode + 1
        assertTrue(ApkUpdateManager.isUpdateAvailable(newerLocalCode, futureRemoteCode))
    }
}
