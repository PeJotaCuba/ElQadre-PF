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

object SmsTransferParser {

    private val PHONE_REGEX = Regex(
        """(?:titular\s+del\s+tel[eé]fono|tel[eé]fono|tel\.|m[oó]vil|celular)\s*:?\s*(\d{8,12})""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_REGEX = Regex(
        """(?:cuenta|a\s+la\s+cuenta|cta\.?)\s*:?\s*([0-9*xX]{8,24})""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_CURRENCY_REGEX = Regex(
        """(?:de|monto|importe|por)?\s*([\d]+(?:[.,]\d{1,2})?)\s*(CUP|USD|EUR|MLC)""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_GENERIC_REGEX = Regex(
        """(?:de|monto|importe|por)\s*:?\s*([\d]+(?:[.,]\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val TRANSACTION_REGEX = Regex(
        """(?:Nro\.?\s*Transacci[oó]n|Transacci[oó]n|Transaccion|Nro\.?\s*Trans|No\.?\s*Trans|Trans\.|Tx\.?|Ref\.?|No\.|No:)\s*:?\s*([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_WITH_TIME_REGEX = Regex(
        """(?:Fecha|Date)\s*:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})(?:\s+([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?))?""",
        RegexOption.IGNORE_CASE
    )

    private val SEPARATE_TIME_REGEX = Regex(
        """(?:hora|time|a\s+las)\s*:?\s*([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_REGEX = Regex(
        """(?:Fecha|Date)\s*:?\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{2,4})""",
        RegexOption.IGNORE_CASE
    )

    // Flexible regex for ENZONA formats:
    // Format 1: ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu
    // Format 2: ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn
    // Format 3: ENZONA pago recibido Importe: 500 CUP No: 12345
    private val ENZONA_UNIFIED_REGEX = Regex(
        """(?:ENZONA|EN\s*ZONA)\s*[\s:,.-]*(?:transferencia\s+recibida|pago\s+recibido|transf\.?\s+recibida|cobro\s+recibido)?\s*[\s:,.-]*(?:Importe|Monto|Total)?\s*[:=\s]*([\d]+(?:[.,]\d{1,2})?)\s*(CUP|USD|EUR|MLC)?\s*[\s,;.-]*(?:No\.?|Nro\.?|Transacci[oó]n|Id|Ref\.?|No)\s*[:=\s]*([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Checks if the message is a valid ENZONA transfer/payment SMS matching the specified formats.
     */
    fun isEnzonaTransferSms(text: String): Boolean {
        if (text.isBlank()) return false
        val clean = text.trim()
        if (clean.contains("ENZONA", ignoreCase = true) || clean.contains("EN ZONA", ignoreCase = true)) {
            return ENZONA_UNIFIED_REGEX.containsMatchIn(clean) ||
                    (clean.contains("recibido", ignoreCase = true) && clean.contains("Importe", ignoreCase = true)) ||
                    (clean.contains("transferencia", ignoreCase = true) && clean.contains("Importe", ignoreCase = true))
        }
        return false
    }

    /**
     * Checks if the given text matches the structural pattern of a Transfermóvil bank transfer SMS.
     */
    fun isTransfermovilTransferSms(text: String): Boolean {
        if (text.isBlank()) return false
        val clean = text.trim()
        val containsTransfer = clean.contains("transferencia", ignoreCase = true) ||
                clean.contains("transfer", ignoreCase = true) ||
                clean.contains("pagoxmovil", ignoreCase = true) ||
                clean.contains("pago por movil", ignoreCase = true) ||
                clean.contains("pago recibido", ignoreCase = true)
        val containsCuenta = clean.contains("cuenta", ignoreCase = true) || clean.contains("cta", ignoreCase = true)
        val containsTx = clean.contains("transacci", ignoreCase = true) ||
                clean.contains("transaccion", ignoreCase = true) ||
                clean.contains("nro", ignoreCase = true) ||
                clean.contains("no.", ignoreCase = true)
        val containsFecha = clean.contains("fecha", ignoreCase = true) ||
                clean.contains("date", ignoreCase = true) ||
                DATE_REGEX.containsMatchIn(clean)

        if (!containsTransfer) {
            return false
        }

        val hasAmount = AMOUNT_CURRENCY_REGEX.containsMatchIn(clean) || AMOUNT_GENERIC_REGEX.containsMatchIn(clean)
        val hasTxNumber = TRANSACTION_REGEX.containsMatchIn(clean)

        return hasAmount && hasTxNumber
    }

    /**
     * Checks if the given text matches the structural pattern of any valid bank transfer SMS (Transfermóvil or ENZONA).
     */
    fun isValidTransferSms(text: String): Boolean {
        return isEnzonaTransferSms(text) || isTransfermovilTransferSms(text)
    }

    /**
     * Parses the SMS text extracting transfer details.
     * Supports both Transfermóvil (PAGOxMOVIL) and ENZONA formats.
     */
    fun parseTransferSms(text: String, fallbackTimestampMillis: Long = 0L): ParsedTransferSms? {
        if (text.isBlank()) return null
        val cleanText = text.trim()

        // 1. Check ENZONA formats
        if (isEnzonaTransferSms(cleanText)) {
            val enzonaMatch = ENZONA_UNIFIED_REGEX.find(cleanText)
            if (enzonaMatch != null) {
                val amountStr = enzonaMatch.groupValues[1].replace(',', '.')
                val currencyStr = enzonaMatch.groupValues[2].ifBlank { "CUP" }.uppercase()
                val txNumber = enzonaMatch.groupValues[3].trim()
                val amount = amountStr.toDoubleOrNull() ?: 0.0

                if (txNumber.isNotBlank() && amount > 0.0) {
                    val dateWithTimeMatch = DATE_WITH_TIME_REGEX.find(cleanText)
                    val dateStr = dateWithTimeMatch?.groupValues?.get(1)?.trim()
                        ?: DATE_REGEX.find(cleanText)?.groupValues?.get(1)?.trim()
                        ?: ""
                    var timeStr = dateWithTimeMatch?.groupValues?.get(2)?.trim() ?: ""
                    if (timeStr.isBlank()) {
                        val separateTimeMatch = SEPARATE_TIME_REGEX.find(cleanText)
                        timeStr = separateTimeMatch?.groupValues?.get(1)?.trim() ?: ""
                    }

                    val parsedMillis = tryParseDateTimeMillis(dateStr, timeStr)
                    val effectiveMillis = when {
                        fallbackTimestampMillis > 0L -> fallbackTimestampMillis
                        parsedMillis > 0L -> parsedMillis
                        else -> System.currentTimeMillis()
                    }
                    val dateFormatted = if (dateStr.isNotBlank()) dateStr else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))

                    return ParsedTransferSms(
                        transactionNumber = txNumber,
                        amount = amount,
                        currency = currencyStr,
                        recipientAccount = "",
                        phoneNumber = "",
                        dateStr = dateFormatted,
                        rawText = cleanText,
                        hasPhone = false,
                        timestampMillis = effectiveMillis,
                        gateway = "ENZONA"
                    )
                }
            } else {
                // Fallback for ENZONA variations
                val amountMatch = AMOUNT_CURRENCY_REGEX.find(cleanText) ?: AMOUNT_GENERIC_REGEX.find(cleanText)
                val amountStr = amountMatch?.groupValues?.get(1)?.replace(',', '.') ?: "0.0"
                val currencyStr = if (amountMatch?.groupValues?.size ?: 0 > 2 && amountMatch?.groupValues?.get(2)?.isNotBlank() == true) amountMatch.groupValues[2].uppercase() else "CUP"
                val amount = amountStr.toDoubleOrNull() ?: 0.0

                val txMatch = TRANSACTION_REGEX.find(cleanText)
                val txNumber = txMatch?.groupValues?.get(1)?.trim() ?: ""

                if (txNumber.isNotBlank() && amount > 0.0) {
                    val effectiveMillis = if (fallbackTimestampMillis > 0L) fallbackTimestampMillis else System.currentTimeMillis()
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))
                    return ParsedTransferSms(
                        transactionNumber = txNumber,
                        amount = amount,
                        currency = currencyStr,
                        recipientAccount = "",
                        phoneNumber = "",
                        dateStr = dateFormatted,
                        rawText = cleanText,
                        hasPhone = false,
                        timestampMillis = effectiveMillis,
                        gateway = "ENZONA"
                    )
                }
            }
        }

        // 2. Check Transfermóvil format
        if (!isTransfermovilTransferSms(cleanText)) return null

        val phoneMatch = PHONE_REGEX.find(cleanText)
        val phoneNumber = phoneMatch?.groupValues?.get(1)?.trim() ?: ""

        val accountMatch = ACCOUNT_REGEX.find(cleanText)
        val recipientAccount = accountMatch?.groupValues?.get(1)?.trim() ?: ""

        val amountMatch = AMOUNT_CURRENCY_REGEX.find(cleanText) ?: AMOUNT_GENERIC_REGEX.find(cleanText)
        val amountStr = amountMatch?.groupValues?.get(1)?.replace(',', '.') ?: "0.0"
        val currencyStr = if (amountMatch?.groupValues?.size ?: 0 > 2 && amountMatch?.groupValues?.get(2)?.isNotBlank() == true) amountMatch.groupValues[2].uppercase() else "CUP"
        val amount = amountStr.toDoubleOrNull() ?: 0.0

        val txMatch = TRANSACTION_REGEX.find(cleanText)
        val transactionNumber = txMatch?.groupValues?.get(1)?.trim() ?: ""

        val dateWithTimeMatch = DATE_WITH_TIME_REGEX.find(cleanText)
        val dateStr = dateWithTimeMatch?.groupValues?.get(1)?.trim()
            ?: DATE_REGEX.find(cleanText)?.groupValues?.get(1)?.trim()
            ?: ""
        var timeStr = dateWithTimeMatch?.groupValues?.get(2)?.trim() ?: ""
        if (timeStr.isBlank()) {
            val separateTimeMatch = SEPARATE_TIME_REGEX.find(cleanText)
            timeStr = separateTimeMatch?.groupValues?.get(1)?.trim() ?: ""
        }

        if (transactionNumber.isBlank() || amount <= 0.0) {
            return null
        }

        val parsedMillis = tryParseDateTimeMillis(dateStr, timeStr)
        val effectiveMillis = when {
            fallbackTimestampMillis > 0L -> fallbackTimestampMillis
            parsedMillis > 0L -> parsedMillis
            else -> System.currentTimeMillis()
        }

        val finalDateStr = if (dateStr.isNotBlank()) dateStr else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(effectiveMillis))

        return ParsedTransferSms(
            transactionNumber = transactionNumber,
            amount = amount,
            currency = currencyStr,
            recipientAccount = recipientAccount,
            phoneNumber = phoneNumber,
            dateStr = finalDateStr,
            rawText = cleanText,
            hasPhone = phoneNumber.isNotBlank(),
            timestampMillis = effectiveMillis,
            gateway = "Transfermóvil"
        )
    }

    private fun tryParseDateTimeMillis(dateStr: String, timeStr: String): Long {
        val cleanDate = dateStr.trim()
        val cleanTime = timeStr.trim()
        if (cleanDate.isBlank()) return 0L

        val formats = if (cleanTime.isNotBlank()) {
            listOf(
                "dd/MM/yyyy HH:mm:ss",
                "d/M/yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm:ss",
                "d-M-yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "d/M/yyyy HH:mm",
                "dd-MM-yyyy HH:mm",
                "d-M-yyyy HH:mm",
                "dd/MM/yy HH:mm:ss",
                "dd/MM/yy HH:mm"
            )
        } else {
            listOf(
                "dd/MM/yyyy",
                "d/M/yyyy",
                "dd-MM-yyyy",
                "d-M-yyyy",
                "dd/MM/yy"
            )
        }

        val stringToParse = if (cleanTime.isNotBlank()) "$cleanDate $cleanTime" else cleanDate
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.getDefault())
                sdf.isLenient = false
                val parsed = sdf.parse(stringToParse)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {}
        }
        return 0L
    }
}
