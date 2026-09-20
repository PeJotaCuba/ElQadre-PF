package com.example.licensing

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Data class representing configuration for parsing payment SMS from a specific sender.
 */
data class PaymentSenderConfig(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String, // e.g. "TRANSFERMOVIL", "ENZONA", "BANDEC", "PAGO", "TODOS"
    val phonePrefixWord: String = "telefono", // Palabra anterior al número de teléfono
    val phoneDigitCount: Int = 10, // Cantidad exacta de dígitos del teléfono
    val accountPrefixWord: String = "cuenta", // Palabra anterior a la cuenta
    val accountDigitCount: Int = 16, // Cantidad exacta de dígitos de la cuenta
    val transactionPrefixWord: String = "Transaccion", // Palabra anterior a la transacción (toma hasta '.')
    val datePrefixWord: String = "Fecha:", // Palabra anterior a la fecha (toma hasta '.')
    val isEnabled: Boolean = true
)

/**
 * Data class representing a parsed payment SMS associated with a business in PRUEBA.
 */
data class PaymentSmsRecord(
    val smsId: String,
    val uniqueKey: String,
    val senderAddress: String,
    val businessCode: String,
    val businessName: String,
    val businessDueno: String = "",
    val phoneNumber: String, // Extracted phone (e.g. 5354205031)
    val phoneTypeMatched: String = "MP", // "MP" (Móvil Principal) or "MA" (Móvil Alternativo)
    val accountNumber: String, // Extracted account (e.g. 9224069993889860)
    val transactionId: String, // Extracted transaction (e.g. KW601Q3PKK999)
    val paymentDate: String, // Extracted date (e.g. 10/9/2026)
    val timestamp: Long,
    val rawBody: String,
    val isConfirmed: Boolean = false,
    val confirmedTimestamp: Long = 0L
)

object SuperAdminPaymentHelper {

    private const val TAG = "SuperAdminPaymentHelper"
    private const val PREFS_NAME = "elqadre_superadmin_payment_prefs"
    private const val KEY_PAYMENT_CONFIGS = "payment_sender_configs_json"
    private const val KEY_CONFIRMED_PAYMENT_KEYS = "confirmed_payment_unique_keys"
    private const val KEY_AUTHORIZED_ACTIVATION_PHONES = "authorized_activation_phones"
    private const val KEY_PAYMENT_RECORDS_CACHE = "cached_payment_records_json"

    /**
     * Returns default sender configurations including Transfermóvil, EnZona, and generic Bank notifications
     */
    fun getDefaultConfigs(): List<PaymentSenderConfig> {
        return listOf(
            PaymentSenderConfig(
                id = "config_transfermovil",
                senderName = "TRANSFERMOVIL",
                phonePrefixWord = "telefono",
                phoneDigitCount = 10,
                accountPrefixWord = "cuenta",
                accountDigitCount = 16,
                transactionPrefixWord = "Transaccion",
                datePrefixWord = "Fecha:",
                isEnabled = true
            ),
            PaymentSenderConfig(
                id = "config_pago_general",
                senderName = "PAGO",
                phonePrefixWord = "telefono",
                phoneDigitCount = 10,
                accountPrefixWord = "cuenta",
                accountDigitCount = 16,
                transactionPrefixWord = "Transaccion",
                datePrefixWord = "Fecha:",
                isEnabled = true
            ),
            PaymentSenderConfig(
                id = "config_enzona",
                senderName = "ENZONA",
                phonePrefixWord = "telefono",
                phoneDigitCount = 10,
                accountPrefixWord = "cuenta",
                accountDigitCount = 16,
                transactionPrefixWord = "Transaccion",
                datePrefixWord = "Fecha:",
                isEnabled = true
            ),
            PaymentSenderConfig(
                id = "config_bandec",
                senderName = "BANDEC",
                phonePrefixWord = "telefono",
                phoneDigitCount = 10,
                accountPrefixWord = "cuenta",
                accountDigitCount = 16,
                transactionPrefixWord = "Transaccion",
                datePrefixWord = "Fecha:",
                isEnabled = true
            ),
            PaymentSenderConfig(
                id = "config_todos",
                senderName = "CUALQUIER_REMITENTE",
                phonePrefixWord = "telefono",
                phoneDigitCount = 10,
                accountPrefixWord = "cuenta",
                accountDigitCount = 16,
                transactionPrefixWord = "Transaccion",
                datePrefixWord = "Fecha:",
                isEnabled = true
            )
        )
    }

    /**
     * Retrieves the stored sender configurations or defaults if not set.
     */
    fun getPaymentConfigs(context: Context): List<PaymentSenderConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PAYMENT_CONFIGS, null) ?: return getDefaultConfigs()
        return try {
            val list = mutableListOf<PaymentSenderConfig>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PaymentSenderConfig(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        senderName = obj.optString("senderName", "TRANSFERMOVIL"),
                        phonePrefixWord = obj.optString("phonePrefixWord", "telefono"),
                        phoneDigitCount = obj.optInt("phoneDigitCount", 10),
                        accountPrefixWord = obj.optString("accountPrefixWord", "cuenta"),
                        accountDigitCount = obj.optInt("accountDigitCount", 16),
                        transactionPrefixWord = obj.optString("transactionPrefixWord", "Transaccion"),
                        datePrefixWord = obj.optString("datePrefixWord", "Fecha:"),
                        isEnabled = obj.optBoolean("isEnabled", true)
                    )
                )
            }
            if (list.isEmpty()) getDefaultConfigs() else list
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing payment configs JSON", e)
            getDefaultConfigs()
        }
    }

    /**
     * Saves sender configurations.
     */
    fun savePaymentConfigs(context: Context, configs: List<PaymentSenderConfig>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (c in configs) {
            val obj = JSONObject().apply {
                put("id", c.id)
                put("senderName", c.senderName)
                put("phonePrefixWord", c.phonePrefixWord)
                put("phoneDigitCount", c.phoneDigitCount)
                put("accountPrefixWord", c.accountPrefixWord)
                put("accountDigitCount", c.accountDigitCount)
                put("transactionPrefixWord", c.transactionPrefixWord)
                put("datePrefixWord", c.datePrefixWord)
                put("isEnabled", c.isEnabled)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PAYMENT_CONFIGS, array.toString()).apply()
    }

    /**
     * Extracts exact phone digits using the configured prefix word and digit count.
     */
    fun extractPhone(body: String, prefixWord: String, digitCount: Int): String? {
        if (prefixWord.isBlank() || digitCount <= 0) return null
        val idx = body.indexOf(prefixWord, ignoreCase = true)
        if (idx < 0) return null
        val afterPrefix = body.substring(idx + prefixWord.length)

        // Try to find exact digit length pattern
        val regex = Regex("\\b(\\d{$digitCount})\\b")
        val match = regex.find(afterPrefix)
        if (match != null) {
            return match.groupValues[1]
        }

        // Fallback: extract consecutive digits skipping common delimiters
        val window = afterPrefix.take(60)
        val nonAlpha = window.replace(Regex("[a-zA-Z:]"), " ")
        val digitsOnlySeq = Regex("\\d+").findAll(nonAlpha).map { it.value }.firstOrNull { it.length == digitCount }
        if (digitsOnlySeq != null) {
            return digitsOnlySeq
        }

        val allDigits = window.filter { it.isDigit() }
        if (allDigits.length >= digitCount) {
            return allDigits.substring(0, digitCount)
        }

        return null
    }

    /**
     * Extracts exact account digits using the configured prefix word and digit count.
     */
    fun extractAccount(body: String, prefixWord: String, digitCount: Int): String? {
        if (prefixWord.isBlank() || digitCount <= 0) return null
        val idx = body.indexOf(prefixWord, ignoreCase = true)
        if (idx < 0) return null
        val afterPrefix = body.substring(idx + prefixWord.length)

        val regex = Regex("\\b(\\d{$digitCount})\\b")
        val match = regex.find(afterPrefix)
        if (match != null) {
            return match.groupValues[1]
        }

        val window = afterPrefix.take(80)
        val nonAlpha = window.replace(Regex("[a-zA-Z:]"), " ")
        val digitsOnlySeq = Regex("\\d+").findAll(nonAlpha).map { it.value }.firstOrNull { it.length == digitCount }
        if (digitsOnlySeq != null) {
            return digitsOnlySeq
        }

        val allDigits = window.filter { it.isDigit() }
        if (allDigits.length >= digitCount) {
            return allDigits.substring(0, digitCount)
        }

        return null
    }

    /**
     * Extracts text from the configured prefix word until the next '.' (period) or newline.
     */
    fun extractUntilPeriod(body: String, prefixWord: String): String? {
        if (prefixWord.isBlank()) return null
        val idx = body.indexOf(prefixWord, ignoreCase = true)
        if (idx < 0) return null
        var afterPrefix = body.substring(idx + prefixWord.length).trim()
        
        // Strip common leading delimiters like ':', ' ', '-', '=', '#', '№', 'N'
        afterPrefix = afterPrefix.trimStart(':', ' ', '-', '=', '#', '№', 'N', 'º', '.')
        if (afterPrefix.isBlank()) return null

        val dotIdx = afterPrefix.indexOf('.')
        val lineIdx = afterPrefix.indexOf('\n')
        val endIdx = when {
            dotIdx >= 0 && lineIdx >= 0 -> minOf(dotIdx, lineIdx)
            dotIdx >= 0 -> dotIdx
            lineIdx >= 0 -> lineIdx
            else -> afterPrefix.length
        }

        val result = afterPrefix.substring(0, endIdx).trim()
        return result.ifBlank { null }
    }

    /**
     * Normalizes phone string to last 8 digits or exact clean digits for comparison.
     */
    fun normalizePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return if (digits.length > 8 && digits.startsWith("53")) {
            digits.substring(2)
        } else if (digits.length >= 8) {
            digits.takeLast(8)
        } else {
            digits
        }
    }

    /**
     * Tests extraction on an arbitrary text using a given configuration.
     */
    fun testExtraction(
        body: String,
        config: PaymentSenderConfig
    ): Map<String, String?> {
        val phone = extractPhone(body, config.phonePrefixWord, config.phoneDigitCount)
        val account = extractAccount(body, config.accountPrefixWord, config.accountDigitCount)
        val transaction = extractUntilPeriod(body, config.transactionPrefixWord)
        val date = extractUntilPeriod(body, config.datePrefixWord)
        return mapOf(
            "phone" to phone,
            "account" to account,
            "transaction" to transaction,
            "date" to date
        )
    }

    /**
     * Parses an SMS message as a payment SMS against all enabled configs and businesses in PRUEBA.
     */
    fun parsePaymentSms(
        smsId: String,
        sender: String,
        body: String,
        timestamp: Long,
        configs: List<PaymentSenderConfig>,
        pruebaBusinesses: List<BusinessRecord>
    ): PaymentSmsRecord? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null

        // Iterate through enabled configs matching sender or wildcard
        for (cfg in configs.filter { it.isEnabled }) {
            val senderMatches = cfg.senderName.equals("CUALQUIER_REMITENTE", ignoreCase = true) ||
                    cfg.senderName.equals("*", ignoreCase = true) ||
                    sender.contains(cfg.senderName, ignoreCase = true) ||
                    cfg.senderName.contains(sender, ignoreCase = true) ||
                    cfg.senderName.equals("TODOS", ignoreCase = true)

            if (!senderMatches) continue

            val extractedPhone = extractPhone(trimmed, cfg.phonePrefixWord, cfg.phoneDigitCount) ?: continue
            val extractedAccount = extractAccount(trimmed, cfg.accountPrefixWord, cfg.accountDigitCount) ?: continue
            val extractedTransaction = extractUntilPeriod(trimmed, cfg.transactionPrefixWord) ?: continue
            val extractedDate = extractUntilPeriod(trimmed, cfg.datePrefixWord) ?: continue

            // Verify if extracted phone belongs to a business currently in PRUEBA
            val normExtractedPhone = normalizePhone(extractedPhone)
            if (normExtractedPhone.isBlank()) continue

            var matchedBusiness: BusinessRecord? = null
            var phoneType = "MP"

            for (biz in pruebaBusinesses) {
                val normMp = normalizePhone(biz.phone)
                val normMa = normalizePhone(biz.phoneAlt)

                if (normMp.isNotBlank() && (normMp == normExtractedPhone || extractedPhone.contains(normMp) || biz.phone.contains(extractedPhone))) {
                    matchedBusiness = biz
                    phoneType = "MP"
                    break
                } else if (normMa.isNotBlank() && (normMa == normExtractedPhone || extractedPhone.contains(normMa) || biz.phoneAlt.contains(extractedPhone))) {
                    matchedBusiness = biz
                    phoneType = "MA"
                    break
                }
            }

            if (matchedBusiness != null) {
                val uniqueKey = "PAYMENT_${matchedBusiness.code}_${extractedTransaction.replace("\\s+".toRegex(), "")}_$timestamp"
                return PaymentSmsRecord(
                    smsId = smsId.ifBlank { timestamp.toString() },
                    uniqueKey = uniqueKey,
                    senderAddress = sender,
                    businessCode = matchedBusiness.code,
                    businessName = matchedBusiness.name,
                    businessDueno = matchedBusiness.dueno,
                    phoneNumber = extractedPhone,
                    phoneTypeMatched = phoneType,
                    accountNumber = extractedAccount,
                    transactionId = extractedTransaction,
                    paymentDate = extractedDate,
                    timestamp = timestamp,
                    rawBody = trimmed
                )
            }
        }

        return null
    }

    private const val KEY_CONFIRMED_PAYMENT_RECORDS_JSON = "confirmed_payment_records_json"

    /**
     * Checks if a payment SMS is confirmed.
     */
    fun isPaymentConfirmed(context: Context, uniqueKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_CONFIRMED_PAYMENT_KEYS, emptySet()) ?: emptySet()
        return set.contains(uniqueKey)
    }

    /**
     * Confirms a payment:
     * - Marks the payment unique key as confirmed.
     * - Enables the phone number as an authorized sender for the ACTIVACIÓN card.
     * - Saves the confirmed payment record for verification during activation.
     * - Does NOT modify Q_licencias.json or activate the license yet.
     */
    fun confirmPayment(context: Context, payment: PaymentSmsRecord) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 1. Mark payment key as confirmed
        val confirmedSet = prefs.getStringSet(KEY_CONFIRMED_PAYMENT_KEYS, emptySet())?.toMutableSet() ?: mutableSetOf()
        confirmedSet.add(payment.uniqueKey)

        // 2. Add phone numbers to authorized activation list
        val authorizedPhones = prefs.getStringSet(KEY_AUTHORIZED_ACTIVATION_PHONES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (payment.phoneNumber.isNotBlank()) {
            authorizedPhones.add(payment.phoneNumber.trim())
            authorizedPhones.add(normalizePhone(payment.phoneNumber))
        }
        if (payment.senderAddress.isNotBlank()) {
            authorizedPhones.add(payment.senderAddress.trim())
            authorizedPhones.add(normalizePhone(payment.senderAddress))
        }

        // 3. Save confirmed payment record in JSON array
        val existingRecords = getConfirmedPaymentRecords(context).toMutableList()
        existingRecords.removeAll { it.uniqueKey == payment.uniqueKey }
        existingRecords.add(payment.copy(isConfirmed = true, confirmedTimestamp = System.currentTimeMillis()))
        val jsonArray = JSONArray()
        for (rec in existingRecords) {
            val obj = JSONObject().apply {
                put("smsId", rec.smsId)
                put("uniqueKey", rec.uniqueKey)
                put("senderAddress", rec.senderAddress)
                put("businessCode", rec.businessCode)
                put("businessName", rec.businessName)
                put("businessDueno", rec.businessDueno)
                put("phoneNumber", rec.phoneNumber)
                put("phoneTypeMatched", rec.phoneTypeMatched)
                put("accountNumber", rec.accountNumber)
                put("transactionId", rec.transactionId)
                put("paymentDate", rec.paymentDate)
                put("timestamp", rec.timestamp)
                put("rawBody", rec.rawBody)
                put("isConfirmed", true)
                put("confirmedTimestamp", rec.confirmedTimestamp)
            }
            jsonArray.put(obj)
        }

        prefs.edit()
            .putStringSet(KEY_CONFIRMED_PAYMENT_KEYS, confirmedSet)
            .putStringSet(KEY_AUTHORIZED_ACTIVATION_PHONES, authorizedPhones)
            .putString(KEY_CONFIRMED_PAYMENT_RECORDS_JSON, jsonArray.toString())
            .apply()
    }

    /**
     * Retrieves all confirmed payment records.
     */
    fun getConfirmedPaymentRecords(context: Context): List<PaymentSmsRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONFIRMED_PAYMENT_RECORDS_JSON, null) ?: return emptyList()
        return try {
            val list = mutableListOf<PaymentSmsRecord>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    PaymentSmsRecord(
                        smsId = obj.optString("smsId", ""),
                        uniqueKey = obj.optString("uniqueKey", ""),
                        senderAddress = obj.optString("senderAddress", ""),
                        businessCode = obj.optString("businessCode", ""),
                        businessName = obj.optString("businessName", ""),
                        businessDueno = obj.optString("businessDueno", ""),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        phoneTypeMatched = obj.optString("phoneTypeMatched", "MP"),
                        accountNumber = obj.optString("accountNumber", ""),
                        transactionId = obj.optString("transactionId", ""),
                        paymentDate = obj.optString("paymentDate", ""),
                        timestamp = obj.optLong("timestamp", 0L),
                        rawBody = obj.optString("rawBody", ""),
                        isConfirmed = true,
                        confirmedTimestamp = obj.optLong("confirmedTimestamp", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error reading confirmed payment records", e)
            emptyList()
        }
    }

    /**
     * Returns confirmed payment info for a business by code, name, or phone.
     */
    fun getConfirmedPaymentForBusiness(
        context: Context,
        businessCode: String = "",
        businessName: String = "",
        phone: String = ""
    ): PaymentSmsRecord? {
        val list = getConfirmedPaymentRecords(context)
        val normPhone = normalizePhone(phone)
        val cleanName = businessName.trim().lowercase()
        return list.firstOrNull { rec ->
            (businessCode.isNotBlank() && rec.businessCode.equals(businessCode, ignoreCase = true)) ||
            (normPhone.isNotBlank() && (normalizePhone(rec.phoneNumber) == normPhone || rec.phoneNumber.contains(normPhone) || normPhone.contains(normalizePhone(rec.phoneNumber)))) ||
            (cleanName.isNotBlank() && rec.businessName.trim().lowercase() == cleanName)
        }
    }

    /**
     * Checks if a business has a confirmed payment.
     */
    fun hasConfirmedPaymentForBusiness(
        context: Context,
        businessCode: String = "",
        businessName: String = "",
        phone: String = ""
    ): Boolean {
        return getConfirmedPaymentForBusiness(context, businessCode, businessName, phone) != null
    }

    /**
     * Checks if a phone number has been authorized by a confirmed payment for ACTIVACIÓN.
     */
    fun isPhoneAuthorizedForActivation(context: Context, phone: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val authorizedPhones = prefs.getStringSet(KEY_AUTHORIZED_ACTIVATION_PHONES, emptySet()) ?: emptySet()
        val norm = normalizePhone(phone)
        return authorizedPhones.contains(phone.trim()) || (norm.isNotBlank() && authorizedPhones.contains(norm))
    }

    /**
     * Returns all phone numbers authorized for activation.
     */
    fun getAuthorizedPhones(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_AUTHORIZED_ACTIVATION_PHONES, emptySet()) ?: emptySet()
    }

    /**
     * Scans SMS inbox for payment SMS according to configured rules and PRUEBA businesses.
     */
    fun scanInboxForPayments(context: Context): List<PaymentSmsRecord> {
        val results = mutableListOf<PaymentSmsRecord>()
        val configs = getPaymentConfigs(context)
        val allBusinesses = SuperAdminBusinessManager.getBusinesses(context)
        val pruebaBusinesses = allBusinesses.filter { 
            it.licenseType.equals("PRUEBA", ignoreCase = true) && 
            !it.status.equals("REVOCADA", ignoreCase = true) 
        }

        if (pruebaBusinesses.isEmpty()) {
            return emptyList()
        }

        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT 500")
            cursor?.use { c ->
                val idCol = c.getColumnIndex("_id")
                val addressCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                val dateCol = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val smsId = if (idCol >= 0) c.getString(idCol) ?: "" else ""
                    val address = if (addressCol >= 0) c.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol >= 0) c.getLong(dateCol) else 0L

                    val parsed = parsePaymentSms(smsId, address, body, date, configs, pruebaBusinesses)
                    if (parsed != null) {
                        val isConfirmed = isPaymentConfirmed(context, parsed.uniqueKey)
                        results.add(parsed.copy(isConfirmed = isConfirmed))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning SMS inbox for payments", e)
        }

        return results
    }
}
