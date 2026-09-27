package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class VersionManagementTest {

    @Test
    fun testSameRoleIncrementsPatchOnly() {
        // Initial state: 1.1.0 in DUEÑO
        val (nextVersion, nextCode) = ProjectVersionManager.calculateNextVersion(
            currentMajor = 1,
            currentMinor = 1,
            currentPatch = 0,
            currentCode = 2,
            currentRole = "DUEÑO",
            targetRole = "DUEÑO"
        )
        assertEquals("1.1.1", nextVersion)
        assertEquals(3L, nextCode)

        // Subsequent change in DUEÑO: 1.1.1 -> 1.1.2
        val (nextVersion2, nextCode2) = ProjectVersionManager.calculateNextVersion(
            currentMajor = 1,
            currentMinor = 1,
            currentPatch = 1,
            currentCode = 3,
            currentRole = "DUEÑO",
            targetRole = "DUEÑO"
        )
        assertEquals("1.1.2", nextVersion2)
        assertEquals(4L, nextCode2)
    }

    @Test
    fun testDifferentRoleIncrementsMinorAndResetsPatchToZero() {
        // From DUEÑO 1.1.7 to CAJERO -> should become 1.2.0
        val (nextVersion, nextCode) = ProjectVersionManager.calculateNextVersion(
            currentMajor = 1,
            currentMinor = 1,
            currentPatch = 7,
            currentCode = 9,
            currentRole = "DUEÑO",
            targetRole = "CAJERO"
        )
        assertEquals("1.2.0", nextVersion)
        assertEquals(10L, nextCode)

        // Subsequent change in CAJERO -> 1.2.1
        val (nextVersion2, nextCode2) = ProjectVersionManager.calculateNextVersion(
            currentMajor = 1,
            currentMinor = 2,
            currentPatch = 0,
            currentCode = 10,
            currentRole = "CAJERO",
            targetRole = "CAJERO"
        )
        assertEquals("1.2.1", nextVersion2)
        assertEquals(11L, nextCode2)

        // From CAJERO 1.2.3 to BARRA -> should become 1.3.0
        val (nextVersion3, nextCode3) = ProjectVersionManager.calculateNextVersion(
            currentMajor = 1,
            currentMinor = 2,
            currentPatch = 3,
            currentCode = 13,
            currentRole = "CAJERO",
            targetRole = "BARRA"
        )
        assertEquals("1.3.0", nextVersion3)
        assertEquals(14L, nextCode3)
    }

    @Test
    fun testVersionJsonMatchesSpecification() {
        val jsonStr = ProjectVersionManager.createVersionJson(
            productName = "ElQadrePF",
            versionCode = 2L,
            versionName = "1.1.0",
            apkUrl = "https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v1.1/ElQadrePF.apk",
            notes = "Test release"
        )
        val json = JSONObject(jsonStr)
        assertEquals("ElQadrePF", json.getString("product"))
        assertEquals(2L, json.getLong("versionCode"))
        assertEquals("1.1.0", json.getString("versionName"))
        assertTrue(json.has("releaseDate"))
        assertTrue(json.getString("releaseDate").contains("Z"))
    }
}
