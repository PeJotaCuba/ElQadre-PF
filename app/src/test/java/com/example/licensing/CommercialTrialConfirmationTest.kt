package com.example.licensing

import org.junit.Assert.*
import org.junit.Test

class CommercialTrialConfirmationTest {

    @Test
    fun testParseConfirmationSms_validFormat() {
        val sms = """
            CONFIRMACIÓN PRUEBA ELQADRE Mi Cafeteria
            Dueño: Juan Perez
            MP: 5351234567
            MA: 5357654321
            DVC: DVC-ABC12345
            Negocio: 001
            Token: 6G2K9P
            Usuario: admin
            Contraseña: secret123
            Inicio: 16/09/2026 10:00
            Vencimiento: 23/09/2026 23:59
        """.trimIndent()

        val parsed = CommercialLicenseManager.parseConfirmationSms(sms)
        assertNotNull(parsed)
        assertEquals("Mi Cafeteria", parsed?.businessName)
        assertEquals("Juan Perez", parsed?.dueno)
        assertEquals("5351234567", parsed?.mp)
        assertEquals("5357654321", parsed?.ma)
        assertEquals("DVC-ABC12345", parsed?.dvc)
        assertEquals("001", parsed?.businessCode)
        assertEquals("6G2K9P", parsed?.token)
        assertEquals("admin", parsed?.ownerUsername)
        assertEquals("secret123", parsed?.ownerPassword)
        assertEquals("16/09/2026 10:00", parsed?.startDateStr)
        assertEquals("23/09/2026 23:59", parsed?.endDateStr)
    }

    @Test
    fun testParseConfirmationSms_licenseFormatWithoutAccents() {
        val sms = """
            CONFIRMACION LICENCIA ELQADRE Restaurante La Habana
            Dueno: Carlos Gomez
            MP: 5358889999
            MA: 5351112222
            DVC: DVC-XYZ99999
            Codigo Negocio: 005
            Token: AB12CD
            Usuario: carlitos
            Contrasena: claveSegura2026
            Inicio: 01/01/2026 08:00
            Vencimiento: 31/12/2026 23:59
        """.trimIndent()

        val parsed = CommercialLicenseManager.parseConfirmationSms(sms)
        assertNotNull(parsed)
        assertEquals("Restaurante La Habana", parsed?.businessName)
        assertEquals("Carlos Gomez", parsed?.dueno)
        assertEquals("5358889999", parsed?.mp)
        assertEquals("5351112222", parsed?.ma)
        assertEquals("DVC-XYZ99999", parsed?.dvc)
        assertEquals("005", parsed?.businessCode)
        assertEquals("AB12CD", parsed?.token)
        assertEquals("carlitos", parsed?.ownerUsername)
        assertEquals("claveSegura2026", parsed?.ownerPassword)
        assertEquals("01/01/2026 08:00", parsed?.startDateStr)
        assertEquals("31/12/2026 23:59", parsed?.endDateStr)
    }

    @Test
    fun testParseConfirmationSms_missingRequiredFields_returnsNull() {
        val invalidSms = """
            CONFIRMACIÓN PRUEBA ELQADRE Mi Cafeteria
            Dueño: Juan Perez
            MP: 5351234567
            DVC: DVC-ABC12345
        """.trimIndent()

        val parsed = CommercialLicenseManager.parseConfirmationSms(invalidSms)
        assertNull(parsed)
    }

    @Test
    fun testCommercialEvaluation_activeTrial() {
        val json = org.json.JSONObject().apply {
            put("dvc", "DVC-TEST1234")
            put("estado", "ACTIVO")
            put("nombreNegocio", "Café Central")
            put("numeroMovil", "5351234567")
            put("fechaInicio", "16/09/2026")
            put("fechaFin", "23/09/2026 23:59:59")
            put("codigo", "001")
        }

        val currentInfo = CommercialLicenseInfo(
            status = CommercialStatus.SIN_AUTORIZACION,
            token = "ABC123"
        )

        val evalTime = CommercialLicenseManager.parseDateToTimestamp("18/09/2026 12:00:00", isEndOfDay = false)
        val result = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = json,
            isTrial = true,
            currentInfo = currentInfo,
            now = evalTime
        )

        assertEquals(CommercialStatus.PRUEBA_ACTIVA, result.status)
        assertEquals("Café Central", result.businessName)
        assertEquals("001", result.businessCode)
        assertEquals(evalTime, result.lastCheckedTimestamp)
    }

    @Test
    fun testCommercialEvaluation_expiredTrial() {
        val json = org.json.JSONObject().apply {
            put("dvc", "DVC-TEST1234")
            put("estado", "ACTIVO")
            put("nombreNegocio", "Café Central")
            put("fechaFin", "23/09/2026 23:59:59")
        }

        val currentInfo = CommercialLicenseInfo(status = CommercialStatus.PRUEBA_ACTIVA)
        val afterExpiryTime = CommercialLicenseManager.parseDateToTimestamp("25/09/2026 10:00:00", isEndOfDay = false)

        val result = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = json,
            isTrial = true,
            currentInfo = currentInfo,
            now = afterExpiryTime
        )

        assertEquals(CommercialStatus.PRUEBA_VENCIDA, result.status)
    }

    @Test
    fun testCommercialEvaluation_activeLicense() {
        val json = org.json.JSONObject().apply {
            put("dvc", "DVC-TEST1234")
            put("estado", "ACTIVO")
            put("nombreNegocio", "Restaurante Gourmet")
            put("tipoLicencia", "ANUAL")
            put("fechaFin", "31/12/2026 23:59:59")
            put("codigo", "002")
        }

        val currentInfo = CommercialLicenseInfo(status = CommercialStatus.PRUEBA_VENCIDA)
        val evalTime = CommercialLicenseManager.parseDateToTimestamp("01/10/2026 12:00:00", isEndOfDay = false)

        val result = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = json,
            isTrial = false,
            currentInfo = currentInfo,
            now = evalTime
        )

        assertEquals(CommercialStatus.LICENCIA_ACTIVA, result.status)
        assertEquals("ANUAL", result.licenseType)
    }

    @Test
    fun testCommercialEvaluation_expiredLicense() {
        val json = org.json.JSONObject().apply {
            put("dvc", "DVC-TEST1234")
            put("estado", "ACTIVO")
            put("tipoLicencia", "MENSUAL")
            put("fechaFin", "01/09/2026 23:59:59")
        }

        val currentInfo = CommercialLicenseInfo(status = CommercialStatus.LICENCIA_ACTIVA)
        val afterExpiryTime = CommercialLicenseManager.parseDateToTimestamp("05/09/2026 10:00:00", isEndOfDay = false)

        val result = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = json,
            isTrial = false,
            currentInfo = currentInfo,
            now = afterExpiryTime
        )

        assertEquals(CommercialStatus.LICENCIA_VENCIDA, result.status)
    }

    @Test
    fun testCommercialEvaluation_revokedLicense() {
        val json = org.json.JSONObject().apply {
            put("dvc", "DVC-TEST1234")
            put("estado", "REVOCADA")
            put("motivoRevocacion", "Incumplimiento de términos contractuales")
            put("fechaRevocacion", "17/09/2026")
        }

        val currentInfo = CommercialLicenseInfo(status = CommercialStatus.LICENCIA_ACTIVA)
        val result = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = json,
            isTrial = false,
            currentInfo = currentInfo
        )

        assertEquals(CommercialStatus.LICENCIA_REVOCADA, result.status)
        assertEquals("Incumplimiento de términos contractuales", result.motivoRevocacion)
    }

    @Test
    fun testCommercialEvaluation_notFound() {
        val currentTrialInfo = CommercialLicenseInfo(status = CommercialStatus.PRUEBA_ACTIVA)
        val trialResult = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = null,
            isTrial = true,
            currentInfo = currentTrialInfo
        )
        assertEquals(CommercialStatus.PRUEBA_VENCIDA, trialResult.status)

        val currentLicenseInfo = CommercialLicenseInfo(status = CommercialStatus.LICENCIA_ACTIVA)
        val licenseResult = CommercialLicenseManager.evaluateCommercialRecord(
            matchedObject = null,
            isTrial = false,
            currentInfo = currentLicenseInfo
        )
        assertEquals(CommercialStatus.LICENCIA_VENCIDA, licenseResult.status)
    }

    @Test
    fun testOfflineGracePeriod_calculation() {
        val now = 1000000000000L
        val sixDaysAgo = now - (6L * 24L * 60L * 60L * 1000L)
        val eightDaysAgo = now - (8L * 24L * 60L * 60L * 1000L)

        val withinGracePeriod = (now - sixDaysAgo) > CommercialLicenseManager.OFFLINE_GRACE_PERIOD_MILLIS
        val pastGracePeriod = (now - eightDaysAgo) > CommercialLicenseManager.OFFLINE_GRACE_PERIOD_MILLIS

        assertFalse("6 days should be within grace period", withinGracePeriod)
        assertTrue("8 days should exceed grace period", pastGracePeriod)
    }
}
