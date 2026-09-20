package com.example.util

import java.text.SimpleDateFormat
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
    val timestampMillis: Long = 0L
)

object SmsTransferParser {

    private val PHONE_REGEX = Regex(
        """(?:titular\s+del\s+tel[eé]fono|tel[eé]fono|tel\.)\s*:?\s*(\d{10})""",
        RegexOption.IGNORE_CASE
    )

    private val ACCOUNT_REGEX = Regex(
        """(?:cuenta|a\s+la\s+cuenta)\s*:?\s*(\d{12,19})""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT_CURRENCY_REGEX = Regex(
        """(?:de|monto|importe|por)?\s*([\d]+(?:[.,]\d{1,2})?)\s*(CUP|USD|EUR|MLC)""",
        RegexOption.IGNORE_CASE
    )

    private val TRANSACTION_REGEX = Regex(
        """(?:Nro\.?\s*Transacci[oó]n|Transacci[oó]n|Transaccion|Nro\s*Trans)\s*:?\s*([A-Za-z0-9]+)""",
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

    /**
     * Checks if the given text matches the structural pattern of a bank transfer SMS.
     * Validates existence of fundamental elements:
     * - transferencia
     * - cuenta
     * - importe & moneda
     * - Nro. Transaccion
     * - Fecha
     */
    fun isValidTransferSms(text: String): Boolean {
        if (text.isBlank()) return false
        val containsTransfer = text.contains("transferencia", ignoreCase = true) || text.contains("transfer", ignoreCase = true)
        val containsCuenta = text.contains("cuenta", ignoreCase = true)
        val containsTx = text.contains("transacci", ignoreCase = true) || text.contains("transaccion", ignoreCase = true)
        val containsFecha = text.contains("fecha", ignoreCase = true) || text.contains("date", ignoreCase = true)

        if (!containsTransfer || !containsCuenta || !containsTx || !containsFecha) {
            return false
        }

        val hasAmount = AMOUNT_CURRENCY_REGEX.containsMatchIn(text)
        val hasTxNumber = TRANSACTION_REGEX.containsMatchIn(text)
        val hasDate = DATE_REGEX.containsMatchIn(text)

        return hasAmount && hasTxNumber && hasDate
    }

    /**
     * Parses the SMS text extracting:
     * - phoneNumber (10 digits if format A with titular phone, empty if format B)
     * - recipientAccount (e.g. 9224069993889860)
     * - amount (e.g. 5000.00)
     * - currency (e.g. "CUP")
     * - transactionNumber (e.g. "KW601MRAWO999")
     * - dateStr (e.g. "27/8/2026")
     */
    fun parseTransferSms(text: String): ParsedTransferSms? {
        if (!isValidTransferSms(text)) return null

        val phoneMatch = PHONE_REGEX.find(text)
        val phoneNumber = phoneMatch?.groupValues?.get(1)?.trim() ?: ""

        val accountMatch = ACCOUNT_REGEX.find(text)
        val recipientAccount = accountMatch?.groupValues?.get(1)?.trim() ?: ""

        val amountMatch = AMOUNT_CURRENCY_REGEX.find(text)
        val amountStr = amountMatch?.groupValues?.get(1)?.replace(',', '.') ?: "0.0"
        val currencyStr = amountMatch?.groupValues?.get(2)?.uppercase() ?: "CUP"
        val amount = amountStr.toDoubleOrNull() ?: 0.0

        val txMatch = TRANSACTION_REGEX.find(text)
        val transactionNumber = txMatch?.groupValues?.get(1)?.trim() ?: ""

        val dateWithTimeMatch = DATE_WITH_TIME_REGEX.find(text)
        val dateStr = dateWithTimeMatch?.groupValues?.get(1)?.trim()
            ?: DATE_REGEX.find(text)?.groupValues?.get(1)?.trim()
            ?: ""
        var timeStr = dateWithTimeMatch?.groupValues?.get(2)?.trim() ?: ""
        if (timeStr.isBlank()) {
            val separateTimeMatch = SEPARATE_TIME_REGEX.find(text)
            timeStr = separateTimeMatch?.groupValues?.get(1)?.trim() ?: ""
        }

        if (transactionNumber.isBlank() || amount <= 0.0) {
            return null
        }

        val calculatedMillis = tryParseDateTimeMillis(dateStr, timeStr)

        return ParsedTransferSms(
            transactionNumber = transactionNumber,
            amount = amount,
            currency = currencyStr,
            recipientAccount = recipientAccount,
            phoneNumber = phoneNumber,
            dateStr = dateStr,
            rawText = text.trim(),
            hasPhone = phoneNumber.isNotBlank(),
            timestampMillis = calculatedMillis
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
