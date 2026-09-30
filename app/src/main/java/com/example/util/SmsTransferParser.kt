package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedTransferSms(
    val transactionNumber: String,
    val amount: Double,
    val currency: String = "CUP",
    val recipientAccount: String = "",
    val phoneNumber: String = "", // 10 digits if available, else empty
    val dateStr: String = "",
    val rawText: String = "",
    val hasPhone: Boolean = phoneNumber.isNotBlank(),
    val timestampMillis: Long = 0L,
    val gateway: String = "Transfermóvil" // "Transfermóvil" or "ENZONA"
)

/**
 * Validador y analizador de SMS de transferencias bancarias (Transfermóvil y ENZONA).
 *
 * REGLA ESTRICTA:
 * REMITENTE AUTORIZADO + FORMATO VÁLIDO EXACTO = TRANSFERENCIA
 *
 * Formatos válidos admitidos:
 *
 * 1. TRANSFERMÓVIL - FORMATO 1:
 *    "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."
 *
 * 2. TRANSFERMÓVIL - FORMATO 2:
 *    "Se ha realizado una transferencia a la cuenta 9212069990303927 de 10.00 CUP. Nro. Transaccion KW601NFJBT999. Fecha: 29/8/2026."
 *
 * 3. ENZONA - FORMATO 3:
 *    "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"
 *
 * 4. ENZONA - FORMATO 4:
 *    "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn"
 *
 * Cualquier otro formato o mensaje (pagos emitidos, cobros, consultas de saldo, facturas,
 * recargas, compras, débitos, avisos, notificaciones del sistema o mensajes con estructuras distintas)
 * ES RECHAZADO Y NO SE CONSIDERA TRANSFERENCIA.
 */
object SmsTransferParser {

    // FORMATO 1 (Transfermóvil con teléfono remitente)
    private val FORMAT_1_REGEX = Regex(
        """^El\s+titular\s+del\s+tel[eé]fono\s+(\d{8,12})\s+le\s+ha\s+realizado\s+una\s+transferencia\s+a\s+la\s+cuenta\s+([0-9*xX]{8,24})\s+de\s+(\d+(?:[.,]\d{1,2})?)(?:\s*(CUP|USD|EUR|MLC))?\.?\s+Nro\.?\s*Transacci[oó]n\s+([A-Za-z0-9_-]+)\.?\s+Fecha:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4}(?:\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?)\.?$""",
        RegexOption.IGNORE_CASE
    )

    // FORMATO 2 (Transfermóvil sin teléfono)
    private val FORMAT_2_REGEX = Regex(
        """^Se\s+ha\s+realizado\s+una\s+transferencia\s+a\s+la\s+cuenta\s+([0-9*xX]{8,24})\s+de\s+(\d+(?:[.,]\d{1,2})?)(?:\s*(CUP|USD|EUR|MLC))?\.?\s+Nro\.?\s*Transacci[oó]n\s+([A-Za-z0-9_-]+)\.?\s+Fecha:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4}(?:\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?)\.?$""",
        RegexOption.IGNORE_CASE
    )

    // FORMATO 3 (ENZONA transferencia recibida)
    private val FORMAT_3_REGEX = Regex(
        """^EN\s*ZONA\s+transferencia\s+recibida\s+Importe:?\s*(\d+(?:[.,]\d{1,2})?)(?:\s*(CUP|USD|EUR|MLC))?\.?\s+(?:No\.?:?|Nro\.?:?)\s*([A-Za-z0-9_-]+)(?:\.?\s+Fecha:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4}(?:\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?))?\.?$""",
        RegexOption.IGNORE_CASE
    )

    // FORMATO 4 (ENZONA pago recibido con o sin coma)
    private val FORMAT_4_REGEX = Regex(
        """^EN\s*ZONA\s+pago\s+recibido,?\s+Importe:?\s*(\d+(?:[.,]\d{1,2})?)(?:\s*(CUP|USD|EUR|MLC))?\.?\s+(?:No\.?:?|Nro\.?:?)\s*([A-Za-z0-9_-]+)(?:\.?\s+Fecha:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4}(?:\s+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)?))?\.?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Normaliza saltos de línea y espacios en blanco del texto SMS.
     */
    fun normalizeSmsText(text: String): String {
        return text.replace('\u00A0', ' ')
            .replace('\u200B', ' ')
            .replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    /**
     * Valida si el remitente corresponde a los remitentes autorizados de Transfermóvil.
     */
    fun isAuthorizedTransfermovilSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim()
        return clean.contains("PAGOxMOVIL", ignoreCase = true) ||
                clean.contains("PAGO POR MOVIL", ignoreCase = true) ||
                clean.contains("TRANSFERMOVIL", ignoreCase = true) ||
                clean.contains("PAGOMOVIL", ignoreCase = true) ||
                clean == "8888" ||
                clean == "5000" ||
                clean == "4000" ||
                clean.contains("BANDEC", ignoreCase = true) ||
                clean.contains("BANMET", ignoreCase = true) ||
                clean.contains("BPA", ignoreCase = true)
    }

    /**
     * Valida si el remitente corresponde exactamente a "ENZONA".
     */
    fun isAuthorizedEnzonaSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val clean = sender.trim()
        return clean.equals("ENZONA", ignoreCase = true) || clean.contains("ENZONA", ignoreCase = true)
    }

    /**
     * Valida si el remitente corresponde a un remitente autorizado (Transfermóvil o ENZONA).
     */
    fun isAuthorizedSender(sender: String?): Boolean {
        return isAuthorizedTransfermovilSender(sender) || isAuthorizedEnzonaSender(sender)
    }

    /**
     * Valida si el texto corresponde estrictamente a uno de los dos formatos válidos de Transfermóvil (FORMATO 1 o FORMATO 2).
     */
    fun isTransfermovilTransferSms(text: String): Boolean {
        if (text.isBlank()) return false
        val clean = normalizeSmsText(text)
        return FORMAT_1_REGEX.matches(clean) || FORMAT_2_REGEX.matches(clean)
    }

    /**
     * Valida si el texto corresponde estrictamente a uno de los dos formatos válidos de ENZONA (FORMATO 3 o FORMATO 4).
     */
    fun isEnzonaTransferSms(text: String): Boolean {
        if (text.isBlank()) return false
        val clean = normalizeSmsText(text)
        return FORMAT_3_REGEX.matches(clean) || FORMAT_4_REGEX.matches(clean)
    }

    /**
     * Valida si el texto coincide con la estructura de cualquiera de los cuatro formatos válidos.
     */
    fun isValidTransferSms(text: String): Boolean {
        return isTransfermovilTransferSms(text) || isEnzonaTransferSms(text)
    }

    /**
     * Valida si el texto y el remitente corresponden a una transferencia autorizada.
     * Si el remitente es proporcionado, debe coincidir exactamente con el canal del formato.
     */
    fun isValidTransferSmsWithSender(text: String, sender: String?): Boolean {
        if (sender != null && sender.isNotBlank()) {
            if (isAuthorizedEnzonaSender(sender)) {
                return isEnzonaTransferSms(text)
            }
            if (isAuthorizedTransfermovilSender(sender)) {
                return isTransfermovilTransferSms(text)
            }
            return false
        }
        return isValidTransferSms(text)
    }

    /**
     * Parsea un SMS de transferencia extrayendo sus datos.
     * Solo retorna un objeto ParsedTransferSms si el mensaje coincide estrictamente
     * con uno de los 4 formatos válidos y el remitente (si se proporciona) está autorizado.
     */
    fun parseTransferSms(
        text: String,
        fallbackTimestampMillis: Long = 0L,
        sender: String? = null
    ): ParsedTransferSms? {
        if (text.isBlank()) return null

        // Si se provee remitente, validar que esté autorizado para el tipo de mensaje
        if (sender != null && sender.isNotBlank()) {
            val isTm = isAuthorizedTransfermovilSender(sender)
            val isEz = isAuthorizedEnzonaSender(sender)
            if (!isTm && !isEz) {
                return null
            }
            if (isTm && !isTransfermovilTransferSms(text)) {
                return null
            }
            if (isEz && !isEnzonaTransferSms(text)) {
                return null
            }
        }

        val clean = normalizeSmsText(text)

        // 1. Probar Transfermóvil FORMATO 1
        val m1 = FORMAT_1_REGEX.matchEntire(clean)
        if (m1 != null) {
            val phone = m1.groupValues[1].trim()
            val account = m1.groupValues[2].trim()
            val amountStr = m1.groupValues[3].replace(',', '.')
            val currencyStr = m1.groupValues[4].ifBlank { "CUP" }.uppercase()
            val txNumber = m1.groupValues[5].trim()
            val dateRaw = m1.groupValues[6].trim()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (txNumber.isNotBlank() && amount > 0.0) {
                val parsedMillis = parseDateToMillis(dateRaw)
                val effectiveMillis = when {
                    parsedMillis > 0L -> parsedMillis
                    fallbackTimestampMillis > 0L -> fallbackTimestampMillis
                    else -> System.currentTimeMillis()
                }
                val dateFormatted = if (dateRaw.isNotBlank()) dateRaw else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))
                return ParsedTransferSms(
                    transactionNumber = txNumber,
                    amount = amount,
                    currency = currencyStr,
                    recipientAccount = account,
                    phoneNumber = phone,
                    dateStr = dateFormatted,
                    rawText = text.trim(),
                    hasPhone = phone.isNotBlank(),
                    timestampMillis = effectiveMillis,
                    gateway = "Transfermóvil"
                )
            }
        }

        // 2. Probar Transfermóvil FORMATO 2
        val m2 = FORMAT_2_REGEX.matchEntire(clean)
        if (m2 != null) {
            val account = m2.groupValues[1].trim()
            val amountStr = m2.groupValues[2].replace(',', '.')
            val currencyStr = m2.groupValues[3].ifBlank { "CUP" }.uppercase()
            val txNumber = m2.groupValues[4].trim()
            val dateRaw = m2.groupValues[5].trim()
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (txNumber.isNotBlank() && amount > 0.0) {
                val parsedMillis = parseDateToMillis(dateRaw)
                val effectiveMillis = when {
                    parsedMillis > 0L -> parsedMillis
                    fallbackTimestampMillis > 0L -> fallbackTimestampMillis
                    else -> System.currentTimeMillis()
                }
                val dateFormatted = if (dateRaw.isNotBlank()) dateRaw else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))
                return ParsedTransferSms(
                    transactionNumber = txNumber,
                    amount = amount,
                    currency = currencyStr,
                    recipientAccount = account,
                    phoneNumber = "",
                    dateStr = dateFormatted,
                    rawText = text.trim(),
                    hasPhone = false,
                    timestampMillis = effectiveMillis,
                    gateway = "Transfermóvil"
                )
            }
        }

        // 3. Probar ENZONA FORMATO 3
        val m3 = FORMAT_3_REGEX.matchEntire(clean)
        if (m3 != null) {
            val amountStr = m3.groupValues[1].replace(',', '.')
            val currencyStr = m3.groupValues[2].ifBlank { "CUP" }.uppercase()
            val txNumber = m3.groupValues[3].trim()
            val dateRaw = if (m3.groupValues.size > 4) m3.groupValues[4].trim() else ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (txNumber.isNotBlank() && amount > 0.0) {
                val parsedMillis = if (dateRaw.isNotBlank()) parseDateToMillis(dateRaw) else 0L
                val effectiveMillis = when {
                    parsedMillis > 0L -> parsedMillis
                    fallbackTimestampMillis > 0L -> fallbackTimestampMillis
                    else -> System.currentTimeMillis()
                }
                val dateFormatted = if (dateRaw.isNotBlank()) dateRaw else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))
                return ParsedTransferSms(
                    transactionNumber = txNumber,
                    amount = amount,
                    currency = currencyStr,
                    recipientAccount = "",
                    phoneNumber = "",
                    dateStr = dateFormatted,
                    rawText = text.trim(),
                    hasPhone = false,
                    timestampMillis = effectiveMillis,
                    gateway = "ENZONA"
                )
            }
        }

        // 4. Probar ENZONA FORMATO 4
        val m4 = FORMAT_4_REGEX.matchEntire(clean)
        if (m4 != null) {
            val amountStr = m4.groupValues[1].replace(',', '.')
            val currencyStr = m4.groupValues[2].ifBlank { "CUP" }.uppercase()
            val txNumber = m4.groupValues[3].trim()
            val dateRaw = if (m4.groupValues.size > 4) m4.groupValues[4].trim() else ""
            val amount = amountStr.toDoubleOrNull() ?: 0.0

            if (txNumber.isNotBlank() && amount > 0.0) {
                val parsedMillis = if (dateRaw.isNotBlank()) parseDateToMillis(dateRaw) else 0L
                val effectiveMillis = when {
                    parsedMillis > 0L -> parsedMillis
                    fallbackTimestampMillis > 0L -> fallbackTimestampMillis
                    else -> System.currentTimeMillis()
                }
                val dateFormatted = if (dateRaw.isNotBlank()) dateRaw else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))
                return ParsedTransferSms(
                    transactionNumber = txNumber,
                    amount = amount,
                    currency = currencyStr,
                    recipientAccount = "",
                    phoneNumber = "",
                    dateStr = dateFormatted,
                    rawText = text.trim(),
                    hasPhone = false,
                    timestampMillis = effectiveMillis,
                    gateway = "ENZONA"
                )
            }
        }

        // Ningún formato válido coincidió
        return null
    }

    /**
     * Intenta parsear fechas de diferentes formatos cubanos comunes (dd/MM/yyyy, d/M/yyyy, etc.).
     */
    fun parseDateToMillis(dateStr: String): Long {
        val clean = dateStr.trim()
        if (clean.isBlank()) return 0L
        val formats = listOf(
            "dd/MM/yyyy HH:mm:ss",
            "d/M/yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm:ss",
            "d-M-yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "d/M/yyyy HH:mm",
            "dd-MM-yyyy HH:mm",
            "d-M-yyyy HH:mm",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "dd-MM-yyyy",
            "d-M-yyyy",
            "dd/MM/yy HH:mm:ss",
            "dd/MM/yy HH:mm",
            "dd/MM/yy",
            "d/M/yy"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val parsed = sdf.parse(clean)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return 0L
    }
}
