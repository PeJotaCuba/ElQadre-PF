package com.example.licensing

import android.content.Context
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.ConfiguracionNegocio
import java.util.Locale

object BusinessCodeHelper {

    /**
     * Resolves the 3-digit business code (e.g., "001", "002") consistently
     * across all scenarios:
     * 1. Super Admin testing mode (ENTRAR A ELQADRE)
     * 2. Active 7-day trial authorization
     * 3. Active commercial license
     * 4. Local database configuration
     */
    fun resolveBusinessCode(
        context: Context? = null,
        configNegocio: ConfiguracionNegocio? = null,
        configGeneral: ConfiguracionGeneral? = null,
        licenseInfo: CommercialLicenseInfo? = null
    ): String {
        // 1. Super Admin testing business context
        if (context != null) {
            val testingBiz = SuperAdminBusinessManager.getTestingBusiness(context)
            if (testingBiz != null && testingBiz.code.isNotBlank()) {
                return formatCode(testingBiz.code)
            }
        }

        // 2. Active license or trial info
        if (licenseInfo != null && licenseInfo.businessCode.isNotBlank()) {
            return formatCode(licenseInfo.businessCode)
        }

        // 3. Fallback: check stored license in prefs
        if (context != null) {
            val storedCode = CommercialLicenseManager.getInstance(context).licenseInfo.value.businessCode
            if (storedCode.isNotBlank()) {
                return formatCode(storedCode)
            }
        }

        // 4. Check URL from general config (regex match on Q_XXX)
        val candidateUrls = listOfNotNull(
            configGeneral?.urlUsuariosJson,
            configGeneral?.urlCatalogoJson,
            configGeneral?.urlQDuenoJson
        )
        val regex = Regex("Q_(\\d{3})", RegexOption.IGNORE_CASE)
        for (url in candidateUrls) {
            val match = regex.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }

        // 5. Check business config code
        if (configNegocio != null && configNegocio.codigoNegocio.isNotBlank()) {
            return formatCode(configNegocio.codigoNegocio)
        }

        return "001"
    }

    fun formatCode(rawCode: String): String {
        val digits = rawCode.filter { it.isDigit() }
        val num = digits.toIntOrNull() ?: 1
        return String.format(Locale.US, "%03d", num)
    }

    fun matches(codeA: String, codeB: String): Boolean {
        if (codeA.isBlank() || codeB.isBlank()) return false
        return formatCode(codeA) == formatCode(codeB)
    }
}
