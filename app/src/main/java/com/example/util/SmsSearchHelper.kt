package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class SearchedPagoXMovilSms(
    val parsed: ParsedTransferSms,
    val smsDateMillis: Long,
    val isAlreadyRegistered: Boolean
)

object SmsSearchHelper {

    fun searchPagoXMovilByDate(
        context: Context,
        targetDateCalendar: Calendar,
        existingTransactions: Set<String>
    ): List<SearchedPagoXMovilSms> {
        val results = mutableListOf<SearchedPagoXMovilSms>()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return results
        }

        // Calculate start and end of target day in local timezone
        val startCal = (targetDateCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = (targetDateCalendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val startMillis = startCal.timeInMillis
        val endMillis = endCal.timeInMillis

        val targetDay = targetDateCalendar.get(Calendar.DAY_OF_MONTH)
        val targetMonth = targetDateCalendar.get(Calendar.MONTH) + 1 // 1-12
        val targetYear = targetDateCalendar.get(Calendar.YEAR)

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.DATE_SENT
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                Telephony.Sms.DATE + " DESC LIMIT 1000"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val dateSentIdx = it.getColumnIndex(Telephony.Sms.DATE_SENT)

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val dateMillis = it.getLong(dateIdx)
                    val dateSentMillis = if (dateSentIdx >= 0) it.getLong(dateSentIdx) else 0L

                    val isPagoXMovilOrEnzona = address.contains("PAGOxMOVIL", ignoreCase = true) ||
                            address.contains("ENZONA", ignoreCase = true) ||
                            body.contains("PAGOxMOVIL", ignoreCase = true) ||
                            body.contains("ENZONA", ignoreCase = true) ||
                            SmsTransferParser.isValidTransferSms(body)

                    if (isPagoXMovilOrEnzona) {
                        val effectiveSmsMillis = when {
                            dateMillis > 0L -> dateMillis
                            dateSentMillis > 0L -> dateSentMillis
                            else -> startMillis
                        }
                        val parsed = SmsTransferParser.parseTransferSms(body, effectiveSmsMillis)
                        if (parsed != null) {
                            val finalEffectiveMillis = when {
                                dateMillis > 0L -> dateMillis
                                dateSentMillis > 0L -> dateSentMillis
                                parsed.timestampMillis > 0L -> parsed.timestampMillis
                                else -> startMillis
                            }

                            // Check date match
                            val matchesEpoch = (dateMillis in startMillis..endMillis) ||
                                    (dateSentMillis in startMillis..endMillis) ||
                                    (parsed.timestampMillis in startMillis..endMillis) ||
                                    (finalEffectiveMillis in startMillis..endMillis)
                            val matchesTextDate = isDateMatchingText(parsed.dateStr, targetDay, targetMonth, targetYear)

                            if (matchesEpoch || matchesTextDate) {
                                val isRegistered = existingTransactions.contains(parsed.transactionNumber)
                                val finalDateStr = if (parsed.dateStr.isNotBlank()) {
                                    parsed.dateStr
                                } else {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(finalEffectiveMillis))
                                }

                                results.add(
                                    SearchedPagoXMovilSms(
                                        parsed = parsed.copy(
                                            dateStr = finalDateStr,
                                            timestampMillis = finalEffectiveMillis
                                        ),
                                        smsDateMillis = finalEffectiveMillis,
                                        isAlreadyRegistered = isRegistered
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return results.distinctBy { it.parsed.transactionNumber }
    }

    private fun isDateMatchingText(dateStr: String, targetDay: Int, targetMonth: Int, targetYear: Int): Boolean {
        if (dateStr.isBlank()) return false
        val parts = dateStr.split('/', '-')
        if (parts.size >= 3) {
            val d = parts[0].toIntOrNull() ?: return false
            val m = parts[1].toIntOrNull() ?: return false
            var y = parts[2].toIntOrNull() ?: return false
            if (y < 100) y += 2000
            return d == targetDay && m == targetMonth && y == targetYear
        }
        return false
    }

    fun findUnregisteredPagoXMovil(context: Context, existingTransactions: Set<String>): List<ParsedTransferSms> {
        val todayCal = Calendar.getInstance()
        val all = searchPagoXMovilByDate(context, todayCal, existingTransactions)
        return all.filter { !it.isAlreadyRegistered }.map { it.parsed }
    }
}
