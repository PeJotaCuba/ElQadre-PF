package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.local.AppDatabase
import com.example.data.local.model.SmsComandaQueue
import com.example.util.SmsComandaBus
import com.example.util.SmsComandaHelper
import com.example.util.SmsTransferBus
import com.example.util.SmsTransferParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            if (messages.isEmpty()) return

            val firstMsg = messages.firstOrNull() ?: return
            val sender = firstMsg.originatingAddress ?: firstMsg.displayOriginatingAddress ?: ""
            val senderTrimmed = sender.trim()

            val fullBodyBuilder = StringBuilder()
            for (msg in messages) {
                val body = msg.displayMessageBody ?: msg.messageBody ?: ""
                fullBodyBuilder.append(body)
            }
            val fullText = fullBodyBuilder.toString()
            if (fullText.isBlank()) return

            // 1. Separate check for PAGOxMOVIL Transfer SMS
            val isPagoXMovil = senderTrimmed.equals("PAGOxMOVIL", ignoreCase = true) ||
                    senderTrimmed.contains("PAGOxMOVIL", ignoreCase = true)

            if (isPagoXMovil) {
                val parsed = SmsTransferParser.parseTransferSms(fullText)
                if (parsed != null) {
                    val firstMsg = messages.firstOrNull()
                    val msgTimestamp = firstMsg?.timestampMillis ?: 0L
                    val finalTimestamp = when {
                        msgTimestamp > 0L -> msgTimestamp
                        parsed.timestampMillis > 0L -> parsed.timestampMillis
                        else -> System.currentTimeMillis()
                    }
                    val finalDateStr = if (parsed.dateStr.isNotBlank()) {
                        parsed.dateStr
                    } else {
                        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(finalTimestamp))
                    }
                    SmsTransferBus.postTransfer(
                        parsed.copy(
                            dateStr = finalDateStr,
                            timestampMillis = finalTimestamp
                        )
                    )
                }
                return
            }

            // 2. Separate check for Comanda SMS
            val isComandaPattern = fullText.startsWith("Comanda #", ignoreCase = true) ||
                    Regex("""^Comanda\s*#\s*\d+""", RegexOption.IGNORE_CASE).containsMatchIn(fullText)

            if (isComandaPattern) {
                val pendingResult = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    var savedId = 0L
                    try {
                        val db = com.example.data.local.AppDatabase.getDatabase(context.applicationContext)
                        val activeJornada = db.jornadaDao().getActiveJornadaSync()
                        val jornadaId = if (activeJornada != null && activeJornada.isOpen) activeJornada.id else 0L
                        val parsed = com.example.util.SmsComandaHelper.parseComandaSms(fullText)
                        val comandaNum = parsed?.comandaNumber ?: 0

                        val queueItem = com.example.data.local.model.SmsComandaQueue(
                            senderPhone = senderTrimmed,
                            smsText = fullText,
                            receivedAt = System.currentTimeMillis(),
                            estado = "PENDIENTE",
                            jornadaId = jornadaId,
                            comandaNumber = comandaNum
                        )
                        savedId = db.smsComandaQueueDao().insertSmsComanda(queueItem)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        SmsComandaBus.postComandaSms(senderTrimmed, fullText, savedId)
                        pendingResult.finish()
                    }
                }
                return
            }

            // 3. Separate check for Trial Request SMS ("SOLICITUD PRUEBA ELQADRE")
            val upperFull = fullText.uppercase()
            val isTrialPattern = upperFull.contains("SOLICITUD PRUEBA ELQADRE") ||
                    upperFull.contains("SOLICITUD PRUEBA") ||
                    (upperFull.contains("ELQADRE") && (upperFull.contains("AUTORIZACION") || upperFull.contains("AUTORIZACIÓN") || upperFull.contains("PRUEBA")))

            if (isTrialPattern) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        com.example.licensing.SuperAdminSmsHelper.processIncomingTrialSms(
                            context = context.applicationContext,
                            sender = senderTrimmed,
                            body = fullText,
                            timestamp = System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }

            // 4. Separate check for Confirmation SMS ("CONFIRMACIÓN PRUEBA ELQADRE")
            val isConfirmationPattern = upperFull.contains("CONFIRMACIÓN PRUEBA ELQADRE") ||
                    upperFull.contains("CONFIRMACION PRUEBA ELQADRE")

            if (isConfirmationPattern) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        com.example.licensing.CommercialLicenseManager.getInstance(context.applicationContext)
                            .processIncomingConfirmationSms(context.applicationContext, fullText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }

            // 5. Separate check for License Confirmation SMS ("CONFIRMACIÓN LICENCIA ELQADRE")
            val isLicenseConfirmationPattern = upperFull.contains("CONFIRMACIÓN LICENCIA ELQADRE") ||
                    upperFull.contains("CONFIRMACION LICENCIA ELQADRE")

            if (isLicenseConfirmationPattern) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        com.example.licensing.CommercialLicenseManager.getInstance(context.applicationContext)
                            .processIncomingLicenseConfirmationSms(context.applicationContext, fullText)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }
    }
}


