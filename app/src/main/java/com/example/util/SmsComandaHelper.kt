package com.example.util

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.data.local.model.Product
import com.example.data.local.model.User
import com.example.ui.components.parseProductAgregados
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

data class ComandaItemForSms(
    val productCode: String,
    val quantity: Int,
    val agregadosIds: List<String> = emptyList()
)

data class ParsedComandaItemToken(
    val productCode: String,
    val quantity: Int,
    val agregadosCodes: List<String>,
    val rawToken: String
)

data class ParsedComandaSms(
    val comandaNumber: Int,
    val itemTokens: List<ParsedComandaItemToken>,
    val rawText: String
)

data class SmsSendResult(
    val success: Boolean,
    val errorReason: String? = null
)

object SmsComandaHelper {


    /**
     * Normalizes phone number strings by stripping country prefixes (+53, 53), leading zeros, and non-digits.
     * E.g. "+53 51234567" -> "51234567", "5351234567" -> "51234567", "051234567" -> "51234567".
     */
    fun normalizePhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val digits = phone.filter { it.isDigit() }
        if (digits.length >= 10 && digits.startsWith("53")) {
            return digits.substring(2)
        }
        if (digits.length == 9 && digits.startsWith("0")) {
            return digits.substring(1)
        }
        return digits.trim()
    }

    /**
     * Finds an active user in the qusuarios.json / DB user list whose telephone matches [senderPhone].
     * Matches either normalized equality or equality of the last 8 digits.
     */
    fun findAuthorizedUser(senderPhone: String, users: List<User>): User? {
        val normSender = normalizePhoneNumber(senderPhone)
        if (normSender.isBlank()) return null

        return users.firstOrNull { user ->
            val normUser = normalizePhoneNumber(user.telefono)
            val matchesPhone = if (normUser.isNotBlank()) {
                normUser == normSender || 
                (normSender.length >= 8 && normUser.length >= 8 && normSender.takeLast(8) == normUser.takeLast(8))
            } else false

            user.isActive && 
            (user.role == com.example.data.local.model.UserRole.SALON || 
             user.role == com.example.data.local.model.UserRole.BARRA || 
             user.role == com.example.data.local.model.UserRole.DUENO) && 
            matchesPhone
        }
    }

    /**
     * Encodes a comanda into the required SMS string format:
     * "Comanda # 1; PF-C-0001-01; PF-C-0004-02; PF-B-0002-05"
     * "Comanda # 1; PF-C-0003-02-02-05; PF-B-0001-01"
     */
    fun encodeComandaSms(comandaNumber: Int, items: List<ComandaItemForSms>): String {
        val sb = StringBuilder()
        sb.append("Comanda # ").append(comandaNumber)

        for (item in items) {
            sb.append("; ")
            // Ensure product code is valid or clean (e.g. PF-C-0001)
            val code = item.productCode.trim().uppercase()
            val qtyStr = String.format("%02d", item.quantity)
            sb.append(code).append("-").append(qtyStr)

            for (agregadoId in item.agregadosIds) {
                val cleanId = agregadoId.filter { it.isDigit() }
                val formattedId = if (cleanId.isNotBlank()) {
                    String.format("%02d", cleanId.toIntOrNull() ?: 1)
                } else {
                    "01"
                }
                sb.append("-").append(formattedId)
            }
        }

        return sb.toString()
    }

    /**
     * Parses an incoming SMS text to check if it represents a valid SMS comanda.
     * Returns null if the text is not a valid comanda SMS.
     */
    fun parseComandaSms(smsText: String): ParsedComandaSms? {
        val text = smsText.trim()
        if (!text.startsWith("Comanda #", ignoreCase = true) && !text.contains("PF-", ignoreCase = true)) {
            return null
        }

        val parts = text.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        val firstPart = parts[0]
        val comandaNumberRegex = Regex("""^Comanda\s*#\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = comandaNumberRegex.find(firstPart) ?: return null
        val comandaNumber = match.groupValues[1].toIntOrNull() ?: return null

        val itemTokens = mutableListOf<ParsedComandaItemToken>()
        for (i in 1 until parts.size) {
            val token = parts[i]
            val itemToken = parseItemToken(token)
            if (itemToken != null) {
                itemTokens.add(itemToken)
            }
        }

        if (itemTokens.isEmpty()) return null

        return ParsedComandaSms(
            comandaNumber = comandaNumber,
            itemTokens = itemTokens,
            rawText = smsText
        )
    }

    /**
     * Parses an individual product item token e.g. "PF-C-0003-02-02-05"
     */
    private fun parseItemToken(token: String): ParsedComandaItemToken? {
        val cleanToken = token.trim().uppercase()
        // Format: PF-C-0003-02-02-05 or PF-B-0001-01
        // Group 1: PF-C-0003 or PF-B-0001
        // Group 2: 02 (quantity)
        // Group 3: optional agregados suffix "-02-05"
        val regex = Regex("""^(PF-[CB]-\d+)-(\d+)((?:-\d+)*)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(cleanToken) ?: return null

        val productCode = match.groupValues[1]
        val quantity = match.groupValues[2].toIntOrNull() ?: 1
        val agregadosSuffix = match.groupValues[3]

        val agregadosCodes = if (agregadosSuffix.isNotBlank()) {
            agregadosSuffix.split("-").filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        return ParsedComandaItemToken(
            productCode = productCode,
            quantity = quantity,
            agregadosCodes = agregadosCodes,
            rawToken = token
        )
    }

    /**
     * Sends an SMS using SmsManager with SENT PendingIntent,
     * awaiting real confirmation of modem dispatch from the network.
     */
     suspend fun sendComandaSms(
         context: Context,
         targetPhone: String,
         smsBody: String
     ): SmsSendResult = withContext(Dispatchers.IO) {
         val phone = targetPhone.trim()
         if (phone.isBlank()) {
             return@withContext SmsSendResult(false, "El teléfono de Caja no está configurado.")
         }

         if (ContextCompat.checkSelfPermission(
                 context,
                 android.Manifest.permission.SEND_SMS
             ) != android.content.pm.PackageManager.PERMISSION_GRANTED
         ) {
             return@withContext SmsSendResult(false, "Permiso SEND_SMS no concedido en este dispositivo.")
         }

         val cleanPhone = if (phone.startsWith("+")) {
             "+" + phone.substring(1).filter { it.isDigit() }
         } else {
             phone.filter { it.isDigit() }
         }
         if (cleanPhone.isBlank()) {
             return@withContext SmsSendResult(false, "Número de teléfono de Caja no válido ($phone).")
         }

         val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             context.getSystemService(SmsManager::class.java)
         } else {
             @Suppress("DEPRECATION")
             SmsManager.getDefault()
         }

         val parts = smsManager.divideMessage(smsBody)
         val uniqueId = System.currentTimeMillis().toString() + "_" + (1000..9999).random()
         val sentAction = "com.example.SMS_SENT_$uniqueId"

         val sentDeferred = CompletableDeferred<SmsSendResult>()
         val totalParts = parts.size
         val partsSuccessCount = java.util.concurrent.atomic.AtomicInteger(0)
         val partFailed = java.util.concurrent.atomic.AtomicBoolean(false)

         val sentReceiver = object : BroadcastReceiver() {
             override fun onReceive(recvContext: Context?, intent: Intent?) {
                 val code = resultCode
                 if (code == Activity.RESULT_OK) {
                     val completed = partsSuccessCount.incrementAndGet()
                     if (completed >= totalParts && !partFailed.get()) {
                         sentDeferred.complete(SmsSendResult(true))
                     }
                 } else {
                     if (partFailed.compareAndSet(false, true)) {
                         val errorMsg = when (code) {
                             SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Falla genérica de red móvil al enviar SMS"
                             SmsManager.RESULT_ERROR_NO_SERVICE -> "Sin cobertura de red móvil"
                             SmsManager.RESULT_ERROR_NULL_PDU -> "Error de PDU en el operador"
                             SmsManager.RESULT_ERROR_RADIO_OFF -> "Módem celular desactivado (Modo Avión)"
                             else -> "Código de error SMS: $code"
                         }
                         sentDeferred.complete(SmsSendResult(false, errorMsg))
                     }
                 }
             }
         }

         val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
         } else {
             PendingIntent.FLAG_UPDATE_CURRENT
         }

         val intentFilter = IntentFilter(sentAction)
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             context.registerReceiver(sentReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
         } else {
             context.registerReceiver(sentReceiver, intentFilter)
         }

         try {
             if (parts.size > 1) {
                 val sentIntents = ArrayList<PendingIntent>()
                 for (i in parts.indices) {
                     val sIntent = PendingIntent.getBroadcast(
                         context,
                         i,
                         Intent(sentAction).setPackage(context.packageName),
                         flags
                     )
                     sentIntents.add(sIntent)
                 }
                 smsManager.sendMultipartTextMessage(cleanPhone, null, parts, sentIntents, null)
             } else {
                 val sentIntent = PendingIntent.getBroadcast(
                     context,
                     0,
                     Intent(sentAction).setPackage(context.packageName),
                     flags
                 )
                 smsManager.sendTextMessage(cleanPhone, null, smsBody, sentIntent, null)
             }

             withTimeoutOrNull(10_000L) {
                 sentDeferred.await()
             } ?: SmsSendResult(false, "Tiempo de confirmación SMS expirado (sin respuesta del módem).")
         } catch (e: Exception) {
             SmsSendResult(false, "Error al enviar SMS: ${e.localizedMessage ?: "Excepción desconocida"}")
         } finally {
             try {
                 context.unregisterReceiver(sentReceiver)
             } catch (ignored: Exception) {}
         }
     }

    /**
     * Safely opens the device's native SMS application with the comanda text and recipient pre-filled.
     * Uses ACTION_SENDTO with "smsto:" URI to target the default SMS application directly.
     */
    fun openSmsApp(context: Context, targetPhone: String, smsBody: String): Boolean {
        return try {
            val cleanPhone = targetPhone.trim()
            val uri = if (cleanPhone.isNotBlank()) {
                android.net.Uri.parse("smsto:${android.net.Uri.encode(cleanPhone)}")
            } else {
                android.net.Uri.parse("smsto:")
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", smsBody)
                putExtra(android.content.Intent.EXTRA_TEXT, smsBody)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

