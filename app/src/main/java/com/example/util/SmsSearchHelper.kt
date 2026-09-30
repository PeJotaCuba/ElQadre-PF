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

    /**
     * Comprueba si una fecha en texto (ej. "27/8/2026" o "30/09/2026") coincide exactamente con el día, mes y año objetivo.
     */
    fun isDateMatchingText(dateStr: String, targetDay: Int, targetMonth: Int, targetYear: Int): Boolean {
        if (dateStr.isBlank()) return false
        val clean = dateStr.trim().replace('-', '/').replace('.', '/')
        val parts = clean.split('/')
        if (parts.size >= 3) {
            val d = parts[0].trim().toIntOrNull() ?: return false
            val m = parts[1].trim().toIntOrNull() ?: return false
            var y = parts[2].trim().toIntOrNull() ?: return false
            if (y < 100) y += 2000
            return d == targetDay && m == targetMonth && y == targetYear
        }
        return false
    }

    /**
     * Comprueba si una transferencia corresponde a la fecha de la jornada/filtro objetivo:
     * - Para Transfermóvil: Se utiliza estrictamente la fecha real extraída del texto del SMS (ej. "Fecha: 27/8/2026.").
     * - Para ENZONA: Se utiliza la fecha real de recepción del SMS en el sistema Android (o la fecha del texto si la incluye).
     */
    fun doesSmsMatchTargetDate(
        parsed: ParsedTransferSms,
        smsReceivedMillis: Long,
        targetDateCalendar: Calendar
    ): Boolean {
        val targetDay = targetDateCalendar.get(Calendar.DAY_OF_MONTH)
        val targetMonth = targetDateCalendar.get(Calendar.MONTH) + 1 // 1-12
        val targetYear = targetDateCalendar.get(Calendar.YEAR)

        val isTransfermovil = parsed.gateway.equals("Transfermóvil", ignoreCase = true) ||
                SmsTransferParser.isTransfermovilTransferSms(parsed.rawText)

        if (isTransfermovil && parsed.dateStr.isNotBlank()) {
            // Transfermóvil: la fecha de referencia es la fecha explícita en el cuerpo del mensaje
            return isDateMatchingText(parsed.dateStr, targetDay, targetMonth, targetYear)
        } else {
            // ENZONA: utilizar la fecha/hora real de recepción en el sistema Android
            val effectiveMillis = if (smsReceivedMillis > 0L) smsReceivedMillis else parsed.timestampMillis
            if (effectiveMillis <= 0L) return false
            val smsCal = Calendar.getInstance().apply { timeInMillis = effectiveMillis }
            val sDay = smsCal.get(Calendar.DAY_OF_MONTH)
            val sMonth = smsCal.get(Calendar.MONTH) + 1
            val sYear = smsCal.get(Calendar.YEAR)
            return sDay == targetDay && sMonth == targetMonth && sYear == targetYear
        }
    }

    /**
     * Busca transferencias por SMS de los remitentes autorizados que correspondan ÚNICAMENTE
     * al día indicado por targetDateCalendar, respetando los 4 formatos válidos.
     */
    fun searchPagoXMovilByDate(
        context: Context,
        targetDateCalendar: Calendar,
        existingTransactions: Set<String>
    ): List<SearchedPagoXMovilSms> {
        val results = mutableListOf<SearchedPagoXMovilSms>()

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return results
        }

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

                    val isAuthorized = SmsTransferParser.isAuthorizedSender(address)

                    if (isAuthorized) {
                        val effectiveSmsMillis = when {
                            dateMillis > 0L -> dateMillis
                            dateSentMillis > 0L -> dateSentMillis
                            else -> 0L
                        }
                        val parsed = SmsTransferParser.parseTransferSms(body, effectiveSmsMillis, address)
                        if (parsed != null && parsed.transactionNumber.isNotBlank() && parsed.amount > 0.0) {
                            if (doesSmsMatchTargetDate(parsed, effectiveSmsMillis, targetDateCalendar)) {
                                val txClean = parsed.transactionNumber.trim()
                                val isRegistered = existingTransactions.contains(txClean)
                                
                                val finalEffectiveMillis = when {
                                    parsed.timestampMillis > 0L -> parsed.timestampMillis
                                    effectiveSmsMillis > 0L -> effectiveSmsMillis
                                    else -> targetDateCalendar.timeInMillis
                                }
                                val finalDateStr = if (parsed.dateStr.isNotBlank()) {
                                    parsed.dateStr
                                } else {
                                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(finalEffectiveMillis))
                                }

                                results.add(
                                    SearchedPagoXMovilSms(
                                        parsed = parsed.copy(
                                            transactionNumber = txClean,
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

        return results.distinctBy { it.parsed.transactionNumber.trim().uppercase() }
    }

    fun findUnregisteredPagoXMovil(
        context: Context,
        existingTransactions: Set<String>,
        targetDateCalendar: Calendar? = null
    ): List<ParsedTransferSms> {
        val all = findAllUnregisteredTransfers(context, existingTransactions, targetDateCalendar)
        return all.map { it.parsed }
    }

    fun findAllUnregisteredTransfers(
        context: Context,
        existingTransactions: Set<String>,
        targetDateCalendar: Calendar? = null
    ): List<SearchedPagoXMovilSms> {
        if (targetDateCalendar != null) {
            return searchPagoXMovilByDate(context, targetDateCalendar, existingTransactions)
                .filter { !it.isAlreadyRegistered }
        }

        // Si no se especifica calendario, se usa el día actual
        return searchPagoXMovilByDate(context, Calendar.getInstance(), existingTransactions)
            .filter { !it.isAlreadyRegistered }
    }
}
