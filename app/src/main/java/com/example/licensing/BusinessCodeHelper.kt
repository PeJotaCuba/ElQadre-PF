package com.example.licensing

import android.content.Context
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.ConfiguracionNegocio
import java.util.Locale

object BusinessCodeHelper {

    const val GITHUB_BASE_RAW_URL = "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/"

    fun buildUrlUsuarios(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${formatCode(code)}usuarios.json"

    fun buildUrlCatalogo(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${formatCode(code)}catalogo.json"

    fun buildUrlAdmin(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${formatCode(code)}admin.json"

    fun buildUrlDueño(code: String): String =
        "${GITHUB_BASE_RAW_URL}Q_${formatCode(code)}dueño.json"

    /**
     * Resuelve el código de negocio de 3 dígitos (por ejemplo "001", "002")
     * a partir de la configuración general o de negocio local.
     */
    fun resolveBusinessCode(
        context: Context? = null,
        configNegocio: ConfiguracionNegocio? = null,
        configGeneral: ConfiguracionGeneral? = null
    ): String {
        // 1. Verificar URLs en configuración general (búsqueda de Q_XXX)
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

        // 2. Verificar código en configuración del negocio
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
