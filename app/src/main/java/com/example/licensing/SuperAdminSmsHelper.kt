package com.example.licensing

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class SuperAdminSmsRequest(
    val smsId: String,
    val uniqueKey: String,
    val senderAddress: String,
    val tipo: String, // "AUTORIZACION", "ACTIVACION", or "NUEVO_USUARIO"
    val dvc: String,
    val nombreNegocio: String,
    val numeroMovil: String,
    val numeroMovilAlt: String = "",
    val timestamp: Long,
    val rawBody: String,
    val dueno: String = "",
    val ciudad: String = "",
    val solicitante: String = "",
    val nuevoNombre: String = "",
    val nuevoUsername: String = "",
    val nuevoRol: String = "",
    val nuevoTelefono: String = "",
    val montoPorProducto: Double = 0.0
)

data class TrialSmsRequest(
    val smsId: String,
    val uniqueKey: String,
    val senderAddress: String,
    val nombreNegocio: String,
    val dueno: String,
    val movilPrincipal: String,
    val movilAlternativo: String,
    val ciudad: String,
    val dvc: String = "",
    val timestamp: Long,
    val rawBody: String,
    val isIncomplete: Boolean = false,
    val incompleteReason: String = "",
    val businessCode: String = "",
    val token: String = "",
    val ownerUsername: String = "",
    val ownerPassword: String = ""
)

data class PagoConfirmadoClientInfo(
    val negocio: String,
    val dueno: String = "",
    val movilPrincipal: String = "",
    val movilAlt: String = "",
    val ciudad: String = "",
    val sender: String = "",
    val timestamp: Long = 0L,
    val rawBody: String = ""
)

object SuperAdminSmsHelper {

    const val SUPER_ADMIN_PHONE = "54413935"
    private const val PREFS_NAME = "elqadre_superadmin_sms_prefs"
    private const val KEY_PROCESSED_SMS = "processed_superadmin_sms_keys"
    private const val KEY_DELETED_SMS = "deleted_sms_requests"

    /**
     * Builds fixed structured SMS for AUTORIZACIÓN (Solicitud de Prueba)
     * Format:
     * SOLICITUD PRUEBA ELQADRE (nombre de negocio)
     * Dueño: (nombre y apellidos del dueño)
     * MP: (móvil principal)
     * MA: (móvil alternativo)
     * Ciudad: (ciudad)
     */
    fun buildAutorizacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        dvc: String = "",
        ciudad: String = ""
    ): String {
        val cleanNegocio = negocio.trim().removeSurrounding("(", ")")
        val cleanDvc = if (dvc.isBlank()) "" else if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.trim().uppercase() else "DVC-${dvc.trim().uppercase()}"
        val builder = StringBuilder()
        builder.append("SOLICITUD PRUEBA ELQADRE ($cleanNegocio)\n")
        builder.append("Dueño: ${dueno.trim()}\n")
        builder.append("MP: ${movilPrincipal.trim()}\n")
        builder.append("MA: ${movilAlt.trim()}\n")
        if (cleanDvc.isNotBlank()) {
            builder.append("DVC: $cleanDvc\n")
        }
        builder.append("Ciudad: ${ciudad.trim()}")
        return builder.toString().trim()
    }

    /**
     * Overload for backwards compatibility
     */
    fun buildAutorizacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildAutorizacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            dvc = dvc,
            ciudad = ""
        )
    }

    /**
     * Builds fixed structured SMS for ACTIVACIÓN
     * Format:
     * SOLICITUD DE ACTIVACIÓN (nombre de negocio)
     * Dueño: (nombre y apellidos del dueño)
     * MP: (móvil principal)
     * MA: (móvil alternativo)
     * Ciudad: (ciudad)
     */
    fun buildActivacionSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String,
        movilAlt: String = "",
        dvc: String = "",
        ciudad: String = ""
    ): String {
        val cleanNegocio = negocio.trim().removeSurrounding("(", ")")
        val builder = StringBuilder()
        builder.append("SOLICITUD DE ACTIVACIÓN ($cleanNegocio)\n")
        builder.append("Dueño: ${dueno.trim()}\n")
        builder.append("MP: ${movilPrincipal.trim()}\n")
        builder.append("MA: ${movilAlt.trim()}\n")
        builder.append("Ciudad: ${ciudad.trim()}")
        return builder.toString().trim()
    }

    /**
     * Overload for backwards compatibility
     */
    fun buildActivacionSms(dvc: String, negocio: String, movil: String, movilAlt: String = ""): String {
        return buildActivacionSms(
            negocio = negocio,
            dueno = "",
            movilPrincipal = movil,
            movilAlt = movilAlt,
            dvc = dvc,
            ciudad = ""
        )
    }

    /**
     * Builds fixed structured SMS for PAGO RECIBIDO confirmation sent by ElQadre
     * Format:
     * PAGO RECIBIDO (nombre de negocio)
     * Dueño: (nombre y apellidos del dueño)
     * MP: (móvil principal)
     * MA: (móvil alternativo)
     * Ciudad: (ciudad)
     */
    fun buildPagoRecibidoSms(
        negocio: String,
        dueno: String = "",
        movilPrincipal: String = "",
        movilAlt: String = "",
        ciudad: String = ""
    ): String {
        val cleanNegocio = negocio.trim().removeSurrounding("(", ")")
        val builder = StringBuilder()
        builder.append("PAGO RECIBIDO ($cleanNegocio)\n")
        builder.append("Dueño: ${dueno.trim()}\n")
        builder.append("MP: ${movilPrincipal.trim()}\n")
        builder.append("MA: ${movilAlt.trim()}\n")
        builder.append("Ciudad: ${ciudad.trim()}")
        return builder.toString().trim()
    }

    /**
     * Builds standard SMS notification sent by Super Admin to business owner informing trial is active
     */
    fun buildPruebaHabilitadaSms(negocio: String, dvc: String, fechaFin: String): String {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        return "ELQADRE: Su prueba de 7 dias para el negocio ${negocio.trim()} ($cleanDvc) ha sido habilitada exitosamente hasta el $fechaFin. Ya puede ingresar a la aplicacion."
    }

    /**
     * Builds standard SMS notification sent by Super Admin to business owner informing commercial license is active
     */
    fun buildLicenciaActivadaSms(negocio: String, dvc: String, tipoLicencia: String, fechaFin: String): String {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val vigencia = if (tipoLicencia.equals("PERMANENTE", ignoreCase = true)) "PERMANENTE" else "hasta el $fechaFin"
        return "ELQADRE: Su licencia comercial $tipoLicencia para el negocio ${negocio.trim()} ($cleanDvc) ha sido activada exitosamente ($vigencia). Ya puede ingresar a la aplicacion."
    }

    /**
     * Builds standard structured SMS for SOLICITUD DE NUEVO USUARIO (Fase 3)
     */
    fun buildNuevoUsuarioSms(
        dvc: String,
        negocio: String,
        solicitante: String,
        nombre: String,
        username: String,
        rol: String,
        movil: String = "",
        montoPorProducto: Double = 0.0
    ): String {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val builder = StringBuilder()
        builder.append("ELQADRE NUEVO_USUARIO\n")
        builder.append("DVC: $cleanDvc\n")
        builder.append("NEGOCIO: ${negocio.trim()}\n")
        builder.append("SOLICITANTE: ${solicitante.trim()}\n")
        builder.append("NOMBRE: ${nombre.trim()}\n")
        builder.append("USUARIO: ${username.trim().lowercase()}\n")
        builder.append("ROL: ${rol.trim().uppercase()}\n")
        if (movil.isNotBlank()) {
            builder.append("MOVIL: ${movil.trim()}\n")
        }
        if (montoPorProducto > 0) {
            builder.append("MONTO: $montoPorProducto")
        }
        return builder.toString().trim()
    }

    /**
     * Builds standard structured SMS for REVOCACIÓN DE LICENCIA (Fase 5)
     * Sent to the client's registered mobile number when Super Admin revokes license.
     */
    fun buildRevocacionSms(
        negocio: String,
        dvc: String,
        motivo: String,
        fechaRevocacion: String,
        fechaFinAnterior: String = ""
    ): String {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val builder = StringBuilder()
        builder.append("ELQADRE REVOCACION_LICENCIA\n")
        builder.append("NEGOCIO: ${negocio.trim()}\n")
        builder.append("DVC: $cleanDvc\n")
        builder.append("ESTADO: REVOCADA\n")
        builder.append("FECHA_REVOCACION: ${fechaRevocacion.trim()}\n")
        if (fechaFinAnterior.isNotBlank()) {
            builder.append("VENCIMIENTO_PREVIO: ${fechaFinAnterior.trim()}\n")
        }
        builder.append("MOTIVO: ${motivo.trim()}\n")
        builder.append("AVISO: Acceso comercial revocado por administracion.")
        return builder.toString().trim()
    }

    /**
     * Builds standard structured SMS for ADVERTENCIA DE VENCIMIENTO (Fase 5)
     * Automatically sent by client app to Super Admin (54413935) when trial or license is close to expiring.
     */
    fun buildAdvertenciaVencimientoSms(
        negocio: String,
        dvc: String,
        movil: String,
        tipoAcceso: String,
        fechaVencimiento: String,
        diasRestantes: Long
    ): String {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val builder = StringBuilder()
        builder.append("ELQADRE ADVERTENCIA_VENCIMIENTO\n")
        builder.append("NEGOCIO: ${negocio.trim()}\n")
        builder.append("DVC: $cleanDvc\n")
        builder.append("MOVIL: ${movil.trim()}\n")
        builder.append("TIPO_ACCESO: ${tipoAcceso.trim().uppercase()}\n")
        builder.append("VENCE: ${fechaVencimiento.trim()}\n")
        builder.append("DIAS_RESTANTES: $diasRestantes")
        return builder.toString().trim()
    }

    /**
     * Parses an incoming or inbox SMS body to check if it matches official ElQadre format
     */
    fun parseSuperAdminSms(smsId: String, sender: String, body: String, timestamp: Long): SuperAdminSmsRequest? {
        val trimmed = body.trim()
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val firstLine = lines[0].uppercase()
        val tipo = when {
            firstLine.contains("NUEVO_USUARIO") || firstLine.contains("NUEVO USUARIO") -> "NUEVO_USUARIO"
            firstLine.contains("ACTIVACION") || firstLine.contains("ACTIVACIÓN") -> "ACTIVACION"
            firstLine.contains("AUTORIZACION") || firstLine.contains("AUTORIZACIÓN") || firstLine.contains("SOLICITUD PRUEBA") -> "AUTORIZACION"
            else -> return null
        }

        var dvc = ""
        var nombreNegocio = ""
        var dueno = ""
        var ciudad = ""
        var numeroMovil = ""
        var numeroMovilAlt = ""
        var solicitante = ""
        var nuevoNombre = ""
        var nuevoUsername = ""
        var nuevoRol = ""
        var nuevoTelefono = ""
        var montoPorProducto = 0.0

        // Check if business name is in the first line for SOLICITUD DE ACTIVACIÓN (nombre de negocio)
        if (firstLine.contains("ACTIVACION") || firstLine.contains("ACTIVACIÓN")) {
            val idx = lines[0].indexOf("ACTIVACIÓN", ignoreCase = true).let {
                if (it >= 0) it + "ACTIVACIÓN".length else lines[0].indexOf("ACTIVACION", ignoreCase = true) + "ACTIVACION".length
            }
            if (idx >= 0 && idx < lines[0].length) {
                val extracted = lines[0].substring(idx).trim()
                if (extracted.isNotBlank()) {
                    nombreNegocio = extracted
                        .removeSurrounding("(", ")")
                        .removeSurrounding("<", ">")
                        .removeSurrounding("[", "]")
                        .trim()
                }
            }
        } else if (firstLine.contains("SOLICITUD PRUEBA ELQADRE") || firstLine.contains("SOLICITUD PRUEBA")) {
            val idx = lines[0].indexOf("ELQADRE", ignoreCase = true).let {
                if (it >= 0) it + "ELQADRE".length else lines[0].indexOf("PRUEBA", ignoreCase = true) + "PRUEBA".length
            }
            if (idx >= 0 && idx < lines[0].length) {
                val extracted = lines[0].substring(idx).trim()
                if (extracted.isNotBlank()) {
                    nombreNegocio = extracted
                        .removeSurrounding("(", ")")
                        .removeSurrounding("<", ">")
                        .removeSurrounding("[", "]")
                        .trim()
                }
            }
        }

        for (i in 0 until lines.size) {
            val line = lines[i]
            val upper = line.uppercase()
            when {
                upper.startsWith("DUEÑO:") || upper.startsWith("DUEÑO :") || upper.startsWith("DUENO:") || upper.startsWith("DUENO :") || upper.startsWith("PROPIETARIO:") -> {
                    dueno = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MP:") || upper.startsWith("MP :") || upper.startsWith("MOVIL PRINCIPAL:") || upper.startsWith("MÓVIL PRINCIPAL:") -> {
                    numeroMovil = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MA:") || upper.startsWith("MA :") || upper.startsWith("MOVIL ALTERNATIVO:") || upper.startsWith("MÓVIL ALTERNATIVO:") || upper.startsWith("MOVIL_ALT:") || upper.startsWith("MÓVIL_ALT:") || upper.startsWith("MOVIL2:") -> {
                    numeroMovilAlt = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("DVC:") || upper.startsWith("DVC :") -> {
                    val rawVal = line.substringAfter(":").trim()
                    dvc = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("CIUDAD:") || upper.startsWith("CIUDAD :") || upper.startsWith("PROVINCIA:") || upper.startsWith("MUNICIPIO:") -> {
                    ciudad = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("DVC:") || upper.startsWith("DVC :") -> {
                    dvc = line.substringAfter(":").trim()
                }
                upper.startsWith("NEGOCIO:") || upper.startsWith("NEGOCIO :") -> {
                    if (nombreNegocio.isBlank()) {
                        nombreNegocio = line.substringAfter(":").trim()
                    }
                }
                upper.startsWith("SOLICITANTE:") || upper.startsWith("SOLICITANTE :") -> {
                    solicitante = line.substringAfter(":").trim()
                }
                upper.startsWith("NOMBRE:") || upper.startsWith("NOMBRE :") -> {
                    nuevoNombre = line.substringAfter(":").trim()
                }
                upper.startsWith("USUARIO:") || upper.startsWith("USUARIO :") -> {
                    nuevoUsername = line.substringAfter(":").trim()
                }
                upper.startsWith("ROL:") || upper.startsWith("ROL :") -> {
                    nuevoRol = line.substringAfter(":").trim()
                }
                upper.startsWith("MOVIL:") || upper.startsWith("MÓVIL:") || upper.startsWith("MOVIL :") || upper.startsWith("MÓVIL :") || upper.startsWith("TELEFONO:") || upper.startsWith("TELÉFONO:") -> {
                    val phoneVal = line.substringAfter(":").trim()
                    if (tipo == "NUEVO_USUARIO") {
                        nuevoTelefono = phoneVal
                        if (numeroMovil.isBlank()) numeroMovil = phoneVal
                    } else if (numeroMovil.isBlank()) {
                        numeroMovil = phoneVal
                    }
                }
                upper.startsWith("MONTO:") || upper.startsWith("MONTO :") -> {
                    montoPorProducto = line.substringAfter(":").trim().toDoubleOrNull() ?: 0.0
                }
            }
        }

        // Fallback for lines without prefixes if lines followed for standard format
        if (dvc.isBlank() && lines.size >= 2 && lines[1].uppercase().startsWith("DVC")) {
            dvc = lines[1].replace("DVC:", "", ignoreCase = true).trim()
        }
        if (nombreNegocio.isBlank() && lines.size >= 3 && lines[2].uppercase().startsWith("NEGOCIO")) {
            nombreNegocio = lines[2].replace("NEGOCIO:", "", ignoreCase = true).trim()
        }
        if (numeroMovil.isBlank() && lines.size >= 4 && tipo != "NUEVO_USUARIO" && lines[3].uppercase().startsWith("MOVIL")) {
            numeroMovil = lines[3].replace("MOVIL:", "", ignoreCase = true).trim()
        }

        if (dvc.isBlank() && tipo != "ACTIVACION" && tipo != "AUTORIZACION") return null

        val normalizedDvc = if (dvc.isNotBlank()) {
            if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        } else {
            "DVC-${nombreNegocio.take(6).uppercase().ifBlank { "PENDIENTE" }}"
        }

        val effectivePhone = if (numeroMovil.isNotBlank()) numeroMovil else sender
        val uniqueKey = when (tipo) {
            "NUEVO_USUARIO" -> "NUEVO_USUARIO_${normalizedDvc}_${nuevoUsername}_$timestamp"
            "ACTIVACION" -> "ACTIVACION_${nombreNegocio.replace("\\s+".toRegex(), "")}_${effectivePhone}_$timestamp"
            else -> "${tipo}_${normalizedDvc}_${effectivePhone}_$timestamp"
        }

        return SuperAdminSmsRequest(
            smsId = smsId.ifBlank { System.currentTimeMillis().toString() },
            uniqueKey = uniqueKey,
            senderAddress = sender,
            tipo = tipo,
            dvc = normalizedDvc,
            nombreNegocio = if (nombreNegocio.isNotBlank()) nombreNegocio else "Sin nombre",
            numeroMovil = effectivePhone,
            numeroMovilAlt = numeroMovilAlt,
            timestamp = timestamp,
            rawBody = trimmed,
            dueno = dueno,
            ciudad = ciudad,
            solicitante = solicitante,
            nuevoNombre = nuevoNombre,
            nuevoUsername = nuevoUsername,
            nuevoRol = nuevoRol,
            nuevoTelefono = nuevoTelefono.ifBlank { effectivePhone },
            montoPorProducto = montoPorProducto
        )
    }

    /**
     * Parses client payment confirmation SMS sent by ElQadre (54413935):
     * Format:
     * PAGO RECIBIDO (nombre de negocio)
     * Dueño: (nombre y apellidos del dueño)
     * MP: (móvil principal)
     * MA: (móvil alternativo)
     * Ciudad: (ciudad)
     */
    fun parsePagoConfirmadoSms(sender: String, body: String, timestamp: Long): PagoConfirmadoClientInfo? {
        val trimmed = body.trim()
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val firstLine = lines[0]
        val upperFirst = firstLine.uppercase()

        val isPagoRecibido = upperFirst.startsWith("PAGO RECIBIDO") ||
                upperFirst.contains("PAGO RECIBIDO") ||
                upperFirst.startsWith("PAGO CONFIRMADO") ||
                upperFirst.contains("PAGO CONFIRMADO")

        if (!isPagoRecibido) return null

        var negocio = ""
        var dueno = ""
        var movilPrincipal = ""
        var movilAlt = ""
        var ciudad = ""
        var dvc = ""

        // Extract business name from first line: PAGO RECIBIDO (nombre de negocio)
        if (upperFirst.contains("PAGO RECIBIDO")) {
            val idx = firstLine.indexOf("PAGO RECIBIDO", ignoreCase = true) + "PAGO RECIBIDO".length
            val extracted = firstLine.substring(idx).trim()
            if (extracted.isNotBlank()) {
                negocio = extracted
                    .removeSurrounding("(", ")")
                    .removeSurrounding("<", ">")
                    .removeSurrounding("[", "]")
                    .trim()
            }
        }

        for (i in 0 until lines.size) {
            val line = lines[i]
            val upper = line.uppercase()
            when {
                upper.startsWith("DUEÑO:") || upper.startsWith("DUEÑO :") || upper.startsWith("DUENO:") || upper.startsWith("DUENO :") -> {
                    dueno = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MP:") || upper.startsWith("MP :") || upper.startsWith("MOVIL PRINCIPAL:") || upper.startsWith("MÓVIL PRINCIPAL:") -> {
                    movilPrincipal = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MA:") || upper.startsWith("MA :") || upper.startsWith("MOVIL ALTERNATIVO:") || upper.startsWith("MÓVIL ALTERNATIVO:") || upper.startsWith("MOVIL_ALT:") -> {
                    movilAlt = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("DVC:") || upper.startsWith("DVC :") -> {
                    val rawVal = line.substringAfter(":").trim()
                    dvc = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("CIUDAD:") || upper.startsWith("CIUDAD :") || upper.startsWith("PROVINCIA:") || upper.startsWith("MUNICIPIO:") -> {
                    ciudad = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("NEGOCIO:") || upper.startsWith("NEGOCIO :") -> {
                    if (negocio.isBlank()) {
                        negocio = line.substringAfter(":").trim().removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                    }
                }
            }
        }

        return PagoConfirmadoClientInfo(
            negocio = negocio,
            dueno = dueno,
            movilPrincipal = movilPrincipal,
            movilAlt = movilAlt,
            ciudad = ciudad,
            sender = sender,
            timestamp = timestamp,
            rawBody = trimmed
        )
    }

    /**
     * Checks if a client has received a payment confirmation SMS from ElQadre (54413935 / 5354413935 / +5354413935).
     */
    fun checkClientPagoConfirmado(context: Context): PagoConfirmadoClientInfo? {
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")
        val validSenders = setOf("54413935", "5354413935", "+5354413935")

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC LIMIT 500")
            cursor?.use { c ->
                val addressCol = c.getColumnIndex("address")
                val bodyCol = c.getColumnIndex("body")
                val dateCol = c.getColumnIndex("date")

                while (c.moveToNext()) {
                    val address = if (addressCol >= 0) c.getString(addressCol) ?: "" else ""
                    val body = if (bodyCol >= 0) c.getString(bodyCol) ?: "" else ""
                    val date = if (dateCol >= 0) c.getLong(dateCol) else 0L

                    val cleanAddress = address.trim().replace(" ", "").replace("-", "")
                    val isSenderMatch = validSenders.contains(cleanAddress) ||
                            cleanAddress.endsWith("54413935") ||
                            cleanAddress.contains("54413935")

                    if (!isSenderMatch) continue

                    val parsed = parsePagoConfirmadoSms(address, body, date)
                    if (parsed != null) {
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SuperAdminSmsHelper", "Error scanning for client payment confirmation", e)
        }

        return null
    }

    /**
     * Opens SMS app to contact ElQadre at 54413935
     */
    fun contactElQadreBySms(
        context: Context,
        customMessage: String = "Hola ElQadre, deseo información sobre el pago para activar la licencia comercial de mi negocio."
    ) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$SUPER_ADMIN_PHONE")
                putExtra("sms_body", customMessage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "No se pudo abrir la app de SMS", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens WhatsApp to contact ElQadre at +5354413935
     */
    fun contactElQadreByWhatsApp(
        context: Context,
        customMessage: String = "Hola ElQadre, deseo información sobre el pago para activar la licencia comercial de mi negocio."
    ) {
        try {
            val encodedMsg = Uri.encode(customMessage)
            val url = "https://api.whatsapp.com/send?phone=5354413935&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to browser link
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5354413935?text=${Uri.encode(customMessage)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                android.widget.Toast.makeText(context, "No se pudo abrir WhatsApp", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens WhatsApp Business to contact ElQadre at +5354413935
     */
    fun contactElQadreByWhatsAppBusiness(
        context: Context,
        customMessage: String = "Hola ElQadre, deseo información sobre el pago para activar la licencia comercial de mi negocio."
    ) {
        try {
            val encodedMsg = Uri.encode(customMessage)
            val url = "https://api.whatsapp.com/send?phone=5354413935&text=$encodedMsg"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage("com.whatsapp.w4b")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to standard WhatsApp or browser
            contactElQadreByWhatsApp(context, customMessage)
        }
    }

    /**
     * Checks if the SMS request has already been processed by Super Admin
     */
    fun isSmsProcessed(context: Context, uniqueKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet()) ?: emptySet()
        return set.contains(uniqueKey)
    }

    /**
     * Marks an SMS request as processed
     */
    fun markSmsAsProcessed(context: Context, uniqueKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(uniqueKey)
        prefs.edit().putStringSet(KEY_PROCESSED_SMS, currentSet).apply()
    }

    /**
     * Checks if the SMS request has been deleted by Super Admin
     */
    fun isSmsDeleted(context: Context, uniqueKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DELETED_SMS, emptySet()) ?: emptySet()
        return set.contains(uniqueKey)
    }

    /**
     * Marks an SMS request as deleted so it is removed from lists
     */
    fun markSmsAsDeleted(context: Context, uniqueKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(KEY_DELETED_SMS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(uniqueKey)
        val processedSet = prefs.getStringSet(KEY_PROCESSED_SMS, emptySet())?.toMutableSet() ?: mutableSetOf()
        processedSet.remove(uniqueKey)
        prefs.edit()
            .putStringSet(KEY_DELETED_SMS, currentSet)
            .putStringSet(KEY_PROCESSED_SMS, processedSet)
            .apply()
    }

    /**
     * Scans SMS inbox for ElQadre Super Admin requests
     */
    fun scanInboxForRequests(context: Context): List<SuperAdminSmsRequest> {
        val results = mutableListOf<SuperAdminSmsRequest>()
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

                    val parsed = parseSuperAdminSms(smsId, address, body, date)
                    if (parsed != null && !isSmsDeleted(context, parsed.uniqueKey)) {
                        results.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SuperAdminSmsHelper", "Error scanning SMS inbox", e)
        }

        return results
    }

    /**
     * Parses an SMS message for Trial Request (Solicitud de Prueba) strictly adhering to:
     * SOLICITUD PRUEBA ELQADRE (nombre de negocio)
     * Dueño: (nombre y apellidos del dueño)
     * MP: (móvil principal)
     * MA: (móvil alternativo)
     * Ciudad: (ciudad)
     *
     * Sender can be ANY number.
     */
    fun parseTrialSms(smsId: String, sender: String, body: String, timestamp: Long): TrialSmsRequest? {
        val trimmed = body.trim()
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val upperFull = trimmed.uppercase()
        val isTrialSms = upperFull.contains("SOLICITUD PRUEBA") ||
                upperFull.contains("SOLICITUD DE PRUEBA") ||
                (upperFull.contains("ELQADRE") && (upperFull.contains("AUTORIZACION") || upperFull.contains("AUTORIZACIÓN") || upperFull.contains("PRUEBA") || upperFull.contains("SOLICITUD")))

        if (!isTrialSms) return null

        var nombreNegocio = ""
        var dueno = ""
        var movilPrincipal = ""
        var movilAlt = ""
        var ciudad = ""
        var dvc = ""

        // Extract business name from lines
        for (line in lines) {
            val upperLine = line.uppercase()
            if (upperLine.contains("SOLICITUD PRUEBA ELQADRE")) {
                val idx = line.indexOf("SOLICITUD PRUEBA ELQADRE", ignoreCase = true) + "SOLICITUD PRUEBA ELQADRE".length
                val extracted = line.substring(idx).trim()
                if (extracted.isNotBlank()) {
                    nombreNegocio = extracted
                        .removeSurrounding("(", ")")
                        .removeSurrounding("<", ">")
                        .removeSurrounding("[", "]")
                        .trim()
                }
            } else if (upperLine.contains("SOLICITUD DE PRUEBA ELQADRE")) {
                val idx = line.indexOf("SOLICITUD DE PRUEBA ELQADRE", ignoreCase = true) + "SOLICITUD DE PRUEBA ELQADRE".length
                val extracted = line.substring(idx).trim()
                if (extracted.isNotBlank()) {
                    nombreNegocio = extracted
                        .removeSurrounding("(", ")")
                        .removeSurrounding("<", ">")
                        .removeSurrounding("[", "]")
                        .trim()
                }
            }
        }

        for (i in 0 until lines.size) {
            val line = lines[i]
            var cleanLine = line.trim()
            while (cleanLine.isNotEmpty() && !cleanLine[0].isLetterOrDigit()) {
                cleanLine = cleanLine.substring(1).trim()
            }
            val upper = cleanLine.uppercase()
            when {
                upper.startsWith("DUEÑO:") || upper.startsWith("DUEÑO :") || upper.startsWith("DUENO:") || upper.startsWith("DUENO :") || upper.startsWith("PROPIETARIO:") || upper.startsWith("PROPIETARIO :") -> {
                    val rawVal = cleanLine.substringAfter(":").trim()
                    dueno = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MP:") || upper.startsWith("MP :") || upper.startsWith("MOVIL PRINCIPAL:") || upper.startsWith("MÓVIL PRINCIPAL:") || upper.startsWith("MOVIL PRINCIPAL :") || upper.startsWith("MÓVIL PRINCIPAL :") -> {
                    val rawVal = cleanLine.substringAfter(":").trim()
                    movilPrincipal = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("MA:") || upper.startsWith("MA :") || upper.startsWith("MOVIL ALTERNATIVO:") || upper.startsWith("MÓVIL ALTERNATIVO:") || upper.startsWith("MOVIL_ALT:") || upper.startsWith("MOVIL2:") || upper.startsWith("MOVIL ALTERNATIVO :") || upper.startsWith("MÓVIL ALTERNATIVO :") -> {
                    val rawVal = cleanLine.substringAfter(":").trim()
                    movilAlt = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("DVC:") || upper.startsWith("DVC :") -> {
                    val rawVal = cleanLine.substringAfter(":").trim()
                    dvc = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("CIUDAD:") || upper.startsWith("CIUDAD :") || upper.startsWith("PROVINCIA:") || upper.startsWith("PROVINCIA :") || upper.startsWith("MUNICIPIO:") || upper.startsWith("MUNICIPIO :") -> {
                    val rawVal = cleanLine.substringAfter(":").trim()
                    ciudad = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                }
                upper.startsWith("NEGOCIO:") || upper.startsWith("NEGOCIO :") -> {
                    if (nombreNegocio.isBlank()) {
                        val rawVal = cleanLine.substringAfter(":").trim()
                        nombreNegocio = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                    }
                }
                upper.startsWith("MOVIL:") || upper.startsWith("MÓVIL:") || upper.startsWith("TELEFONO:") || upper.startsWith("TELÉFONO:") || upper.startsWith("MOVIL :") || upper.startsWith("MÓVIL :") || upper.startsWith("TELEFONO :") || upper.startsWith("TELÉFONO :") -> {
                    if (movilPrincipal.isBlank()) {
                        val rawVal = cleanLine.substringAfter(":").trim()
                        movilPrincipal = rawVal.removeSurrounding("(", ")").removeSurrounding("<", ">").trim()
                    }
                }
            }
        }

        if (nombreNegocio.isBlank()) {
            nombreNegocio = "Negocio Solicitante"
        }
        if (movilPrincipal.isBlank()) {
            movilPrincipal = sender
        }

        val cleanKeyName = nombreNegocio.trim().lowercase().replace("\\s+".toRegex(), "_")
        val uniqueKey = "TRIAL_SMS_${cleanKeyName}_${movilPrincipal.trim()}_$timestamp"

        return TrialSmsRequest(
            smsId = smsId.ifBlank { timestamp.toString() },
            uniqueKey = uniqueKey,
            senderAddress = sender,
            nombreNegocio = nombreNegocio,
            dueno = dueno,
            movilPrincipal = movilPrincipal,
            movilAlternativo = movilAlt,
            ciudad = ciudad,
            dvc = dvc,
            timestamp = timestamp,
            rawBody = trimmed
        )
    }

    /**
     * Scans SMS inbox specifically for 7-day Trial requests (Solicitud de Prueba)
     * Sender can be ANY number.
     */
    fun scanInboxForTrialRequests(context: Context): List<TrialSmsRequest> {
        val results = mutableListOf<TrialSmsRequest>()
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

                    val parsed = parseTrialSms(smsId, address, body, date)
                    if (parsed != null && !isSmsDeleted(context, parsed.uniqueKey)) {
                        results.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SuperAdminSmsHelper", "Error scanning trial SMS inbox", e)
        }

        return results
    }

    /**
     * Sends SMS to any destination phone number using SmsManager or launches SMS intent if direct sending fails
     */
    fun sendSmsDirectOrIntentToPhone(context: Context, destinationPhone: String, messageText: String): Result<String> {
        val targetPhone = destinationPhone.trim()
        if (targetPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Número de teléfono de destino no válido"))
        }

        try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, messageText, null, null)
            }
            return Result.success("SMS enviado exitosamente al número $targetPhone")
        } catch (e: SecurityException) {
            // Permission not granted -> Fallback to SMS App Intent
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$targetPhone")
                    putExtra("sms_body", messageText)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                return Result.success("Abriendo aplicación de mensajes para enviar al $targetPhone")
            } catch (ex: Exception) {
                return Result.failure(ex)
            }
        } catch (e: Exception) {
            // Other error -> Attempt intent fallback
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$targetPhone")
                    putExtra("sms_body", messageText)
                    if (context !is Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
                return Result.success("Abriendo aplicación de mensajes para enviar al $targetPhone")
            } catch (ex: Exception) {
                return Result.failure(e)
            }
        }
    }

    /**
     * Sends SMS using SmsManager to official Super Admin or launches SMS intent if direct sending fails
     */
    fun sendSmsDirectOrIntent(context: Context, messageText: String): Result<String> {
        return sendSmsDirectOrIntentToPhone(context, SUPER_ADMIN_PHONE, messageText)
    }

    /**
     * Sends SMS strictly using SmsManager in background without launching external apps or intent fallback.
     */
    fun sendSmsDirectOnlyToPhone(context: Context, destinationPhone: String, messageText: String): Result<String> {
        val targetPhone = destinationPhone.trim()
        if (targetPhone.isBlank()) {
            return Result.failure(IllegalArgumentException("Número de teléfono de destino no válido"))
        }

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(targetPhone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(targetPhone, null, messageText, null, null)
            }
            Result.success("SMS enviado exitosamente a ElQadre ($targetPhone)")
        } catch (e: SecurityException) {
            Result.failure(Exception("Permiso de SMS denegado por el sistema"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendSmsDirectOnly(context: Context, messageText: String): Result<String> {
        return sendSmsDirectOnlyToPhone(context, SUPER_ADMIN_PHONE, messageText)
    }

    fun formatDateToDdMmYyyyHhMm(dateStr: String): String {
        if (dateStr.isBlank()) return ""
        val patterns = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "yyyy-MM-dd")
        for (pattern in patterns) {
            try {
                val sdfIn = SimpleDateFormat(pattern, Locale.getDefault())
                val date = sdfIn.parse(dateStr)
                if (date != null) {
                    val sdfOut = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    return sdfOut.format(date)
                }
            } catch (_: Exception) {}
        }
        return dateStr
    }

    fun generateConfirmationSmsText(business: BusinessRecord): String {
        val inicio = formatDateToDdMmYyyyHhMm(business.startDate)
        val vencimiento = formatDateToDdMmYyyyHhMm(business.endDate)
        return "CONFIRMACIÓN PRUEBA ELQADRE ${business.name}\n" +
                "Dueño: ${business.dueno}\n" +
                "MP: ${business.phone}\n" +
                "MA: ${business.phoneAlt}\n" +
                "DVC: ${business.dvc}\n" +
                "Negocio: ${business.code}\n" +
                "Token: ${business.token}\n" +
                "Usuario: ${business.ownerUsername}\n" +
                "Contraseña: ${business.ownerPassword}\n" +
                "Inicio: $inicio\n" +
                "Vencimiento: $vencimiento"
    }

    /**
     * Fetches current remote JSON or reads local copy to preserve existing records
     */
    suspend fun fetchExistingJson(targetUrl: String, localFile: File): String = withContext(Dispatchers.IO) {
        if (localFile.exists()) {
            val localContent = localFile.readText()
            if (localContent.isNotBlank()) return@withContext localContent
        }
        return@withContext ""
    }

    /**
     * Generates or updates Q_preuba.json for 7-day trial approval
     */
    suspend fun generateOrUpdatePruebasJson(
        context: Context,
        dvc: String,
        nombreNegocio: String,
        numeroMovil: String,
        codigoNegocio: String = "",
        urlUsuarios: String = "",
        numeroMovilAlt: String = "",
        dueno: String = "",
        ciudad: String = "",
        token: String = "",
        ownerUsername: String = "",
        ownerPassword: String = "",
        estado: String = "ACTIVO",
        customStartDate: String = "",
        customEndDate: String = ""
    ): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else if (dvc.isNotBlank()) "DVC-${dvc.uppercase()}" else ""
        val localFile = File(context.filesDir, "Q_preuba.json")
        val existingContent = fetchExistingJson(CommercialLicenseManager.URL_PRUEBA, localFile)

        val code = if (codigoNegocio.isNotBlank()) codigoNegocio else {
            if (cleanDvc.isNotBlank()) {
                SuperAdminBusinessManager.getBusinessByDvc(context, cleanDvc)?.code ?: SuperAdminBusinessManager.getNextBusinessCode(context)
            } else {
                SuperAdminBusinessManager.getNextBusinessCode(context)
            }
        }
        val targetUrlUsuarios = if (urlUsuarios.isNotBlank()) urlUsuarios else {
            "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_${code}usuarios.json"
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        val fechaInicio = if (customStartDate.isNotBlank()) customStartDate else if (estado == "ACTIVO") sdf.format(cal.time) else ""
        val fechaFin = if (customEndDate.isNotBlank()) customEndDate else if (estado == "ACTIVO") {
            cal.add(Calendar.DAY_OF_YEAR, 7)
            sdf.format(cal.time)
        } else ""

        val rootObj: JSONObject = if (existingContent.isNotBlank()) {
            try {
                JSONObject(existingContent)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        val pruebasArray = if (rootObj.has("pruebas")) {
            rootObj.getJSONArray("pruebas")
        } else {
            JSONArray().also { rootObj.put("pruebas", it) }
        }

        var replaced = false
        for (i in 0 until pruebasArray.length()) {
            val item = pruebasArray.optJSONObject(i) ?: continue
            val itemCode = item.optString("codigo", "")
            val itemDvc = item.optString("dvc", "")
            val normItemDvc = if (itemDvc.startsWith("DVC-", ignoreCase = true)) itemDvc.uppercase() else if (itemDvc.isNotBlank()) "DVC-${itemDvc.uppercase()}" else ""
            val matchesCode = itemCode.isNotBlank() && itemCode.equals(code, ignoreCase = true)
            val matchesDvc = cleanDvc.isNotBlank() && normItemDvc.equals(cleanDvc, ignoreCase = true)
            val matchesName = item.optString("nombreNegocio", "").equals(nombreNegocio, ignoreCase = true)

            if (matchesCode || matchesDvc || matchesName) {
                item.put("codigo", code)
                if (cleanDvc.isNotBlank()) item.put("dvc", cleanDvc)
                item.put("nombreNegocio", nombreNegocio)
                if (dueno.isNotBlank()) item.put("dueno", dueno)
                if (ciudad.isNotBlank()) item.put("ciudad", ciudad)
                item.put("numeroMovil", numeroMovil)
                if (numeroMovilAlt.isNotBlank()) {
                    item.put("numeroMovilAlt", numeroMovilAlt)
                }
                if (token.isNotBlank()) item.put("token", token)
                if (ownerUsername.isNotBlank()) item.put("usuarioDueño", ownerUsername)
                if (ownerPassword.isNotBlank()) item.put("claveDueño", ownerPassword)
                item.put("fechaInicio", fechaInicio)
                item.put("fechaFin", fechaFin)
                item.put("estado", estado)
                item.put("urlUsuarios", targetUrlUsuarios)
                replaced = true
                break
            }
        }

        if (!replaced) {
            val newRecord = JSONObject().apply {
                put("codigo", code)
                if (cleanDvc.isNotBlank()) put("dvc", cleanDvc)
                put("nombreNegocio", nombreNegocio)
                if (dueno.isNotBlank()) put("dueno", dueno)
                if (ciudad.isNotBlank()) put("ciudad", ciudad)
                put("numeroMovil", numeroMovil)
                if (numeroMovilAlt.isNotBlank()) {
                    put("numeroMovilAlt", numeroMovilAlt)
                }
                if (token.isNotBlank()) put("token", token)
                if (ownerUsername.isNotBlank()) put("usuarioDueño", ownerUsername)
                if (ownerPassword.isNotBlank()) put("claveDueño", ownerPassword)
                put("fechaInicio", fechaInicio)
                put("fechaFin", fechaFin)
                put("estado", estado)
                put("urlUsuarios", targetUrlUsuarios)
            }
            pruebasArray.put(newRecord)
        }

        val jsonString = rootObj.toString(2)
        localFile.writeText(jsonString)
        return@withContext localFile
    }

    /**
     * Processes incoming SMS trial request automatically:
     * - Validates mandatory fields and checks DVC duplication.
     * - If valid, registers business in SOLICITUD DE PRUEBA PENDIENTE, assigns code, token, owner credentials.
     * - Prepares provisional Q_preuba.json.
     */
    suspend fun processIncomingTrialSms(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ): TrialSmsRequest = withContext(Dispatchers.IO) {
        val parsed = parseTrialSms(timestamp.toString(), sender, body, timestamp)
            ?: TrialSmsRequest(
                smsId = timestamp.toString(),
                uniqueKey = "TRIAL_SMS_RAW_$timestamp",
                senderAddress = sender,
                nombreNegocio = "",
                dueno = "",
                movilPrincipal = sender,
                movilAlternativo = "",
                ciudad = "",
                dvc = "",
                timestamp = timestamp,
                rawBody = body,
                isIncomplete = true,
                incompleteReason = "El mensaje SMS no cumple con el formato 'SOLICITUD PRUEBA ELQADRE'."
            )

        val cleanDvc = if (parsed.dvc.isNotBlank()) {
            if (parsed.dvc.startsWith("DVC-", ignoreCase = true)) parsed.dvc.uppercase() else "DVC-${parsed.dvc.uppercase()}"
        } else ""

        val isNameValid = parsed.nombreNegocio.isNotBlank() && !parsed.nombreNegocio.equals("Negocio Solicitante", ignoreCase = true)
        val isDuenoValid = parsed.dueno.isNotBlank()
        val isMpValid = parsed.movilPrincipal.isNotBlank()
        val isDvcValid = cleanDvc.isNotBlank()
        val isCiudadValid = parsed.ciudad.isNotBlank()

        val existingBusinessWithDvc = if (isDvcValid) SuperAdminBusinessManager.getBusinessByDvc(context, cleanDvc) else null

        if (!isNameValid || !isDuenoValid || !isMpValid || !isDvcValid || !isCiudadValid) {
            val incompleteReason = buildString {
                append("Faltan datos obligatorios:")
                if (!isNameValid) append(" [Nombre de Negocio]")
                if (!isDuenoValid) append(" [Dueño]")
                if (!isMpValid) append(" [Móvil Principal]")
                if (!isDvcValid) append(" [DVC]")
                if (!isCiudadValid) append(" [Ciudad]")
            }
            val incompleteReq = parsed.copy(
                dvc = cleanDvc,
                isIncomplete = true,
                incompleteReason = incompleteReason
            )
            return@withContext incompleteReq
        } else if (existingBusinessWithDvc != null) {
            val incompleteReq = parsed.copy(
                dvc = cleanDvc,
                isIncomplete = true,
                incompleteReason = "El DVC $cleanDvc ya pertenece al negocio '${existingBusinessWithDvc.name}' (${existingBusinessWithDvc.code}). Solicitud duplicada."
            )
            return@withContext incompleteReq
        } else {
            val nextCode = SuperAdminBusinessManager.getNextBusinessCode(context)
            val token = SuperAdminBusinessManager.generateUniqueToken(context)
            val (ownerUsername, ownerPassword) = SuperAdminBusinessManager.generateOwnerCredentials(parsed.dueno, nextCode)

            // Do NOT start trial or save active business on SMS detection. The request remains pending until authorization.
            val validReq = parsed.copy(
                dvc = cleanDvc,
                businessCode = nextCode,
                token = token,
                ownerUsername = ownerUsername,
                ownerPassword = ownerPassword,
                isIncomplete = false
            )
            com.example.util.SuperAdminSmsBus.postTrialRequest(validReq)
            return@withContext validReq
        }
    }

    /**
     * Checks if an incoming or scanned trial SMS request matches an existing business in PRUEBA or LICENCIA.
     * Prevents duplicate imports, duplicate businesses, and duplicate authorizations.
     */
    fun findExistingBusinessForTrialRequest(context: Context, req: TrialSmsRequest): BusinessRecord? {
        val businesses = SuperAdminBusinessManager.getBusinesses(context)
        val reqDvcClean = if (req.dvc.startsWith("DVC-", ignoreCase = true)) req.dvc.uppercase() else if (req.dvc.isNotBlank()) "DVC-${req.dvc.uppercase()}" else ""
        val reqName = req.nombreNegocio.trim()
        val reqCode = req.businessCode.trim()
        val reqToken = req.token.trim()

        return businesses.firstOrNull { b ->
            val bDvcClean = if (b.dvc.startsWith("DVC-", ignoreCase = true)) b.dvc.uppercase() else if (b.dvc.isNotBlank()) "DVC-${b.dvc.uppercase()}" else ""
            (reqDvcClean.isNotBlank() && bDvcClean.isNotBlank() && bDvcClean.equals(reqDvcClean, ignoreCase = true)) ||
                    (reqCode.isNotBlank() && b.code.equals(reqCode, ignoreCase = true)) ||
                    (reqName.isNotBlank() && b.name.equals(reqName, ignoreCase = true)) ||
                    (reqToken.isNotBlank() && b.token.isNotBlank() && b.token.equals(reqToken, ignoreCase = true))
        }
    }

    /**
     * Checks if an incoming or scanned activation SMS request matches an existing business.
     */
    fun findExistingBusinessForActivationRequest(context: Context, req: SuperAdminSmsRequest): BusinessRecord? {
        val businesses = SuperAdminBusinessManager.getBusinesses(context)
        val reqDvcClean = if (req.dvc.startsWith("DVC-", ignoreCase = true)) req.dvc.uppercase() else if (req.dvc.isNotBlank()) "DVC-${req.dvc.uppercase()}" else ""
        val reqName = req.nombreNegocio.trim()
        val reqPhone = req.numeroMovil.trim()

        return businesses.firstOrNull { b ->
            val bDvcClean = if (b.dvc.startsWith("DVC-", ignoreCase = true)) b.dvc.uppercase() else if (b.dvc.isNotBlank()) "DVC-${b.dvc.uppercase()}" else ""
            (reqDvcClean.isNotBlank() && bDvcClean.isNotBlank() && bDvcClean.equals(reqDvcClean, ignoreCase = true)) ||
                    (reqName.isNotBlank() && b.name.equals(reqName, ignoreCase = true)) ||
                    (reqPhone.isNotBlank() && b.phone.isNotBlank() && b.phone.equals(reqPhone, ignoreCase = true))
        }
    }

    /**
     * Scans inbox for trial SMS requests and returns explicit status (Success, PermissionDenied, Error).
     */
    sealed class ScanStatus<out T> {
        data class Success<T>(val items: List<T>) : ScanStatus<T>()
        data class PermissionDenied(val message: String) : ScanStatus<Nothing>()
        data class Error(val message: String) : ScanStatus<Nothing>()
    }

    fun scanInboxForTrialRequestsWithStatus(context: Context): ScanStatus<TrialSmsRequest> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ScanStatus.PermissionDenied("Permiso de lectura de SMS no concedido. Se requiere permiso para escanear la bandeja.")
        }

        return try {
            val list = scanInboxForTrialRequests(context)
            ScanStatus.Success(list)
        } catch (e: SecurityException) {
            ScanStatus.PermissionDenied("Permiso de SMS denegado por el sistema: ${e.message}")
        } catch (e: Exception) {
            ScanStatus.Error("Error al leer la bandeja de mensajes: ${e.message ?: "Fallo desconocido"}")
        }
    }

    fun scanInboxForActivationRequestsWithStatus(context: Context): ScanStatus<SuperAdminSmsRequest> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return ScanStatus.PermissionDenied("Permiso de lectura de SMS no concedido. Se requiere permiso para escanear la bandeja.")
        }

        return try {
            val list = scanInboxForRequests(context).filter { it.tipo == "ACTIVACION" }
            ScanStatus.Success(list)
        } catch (e: SecurityException) {
            ScanStatus.PermissionDenied("Permiso de SMS denegado por el sistema: ${e.message}")
        } catch (e: Exception) {
            ScanStatus.Error("Error al leer la bandeja de mensajes: ${e.message ?: "Fallo desconocido"}")
        }
    }

    /**
     * Processes a valid trial SMS request when Super Admin confirms / authorizes:
     * 1. If business already has an active trial, does NOT reset dates.
     * 2. Otherwise records exact start time (now) and end time (now + 7 days).
     * 3. CRITICAL: Sends confirmation SMS FIRST. If sending fails, trial DOES NOT start.
     * 4. Updates status to "ACTIVO" and sets trialSmsSentCount = 1 only upon successful send.
     * 5. Updates Q_preuba.json with real data and marks SMS as processed.
     */
    suspend fun processTrialRequest(
        context: Context,
        request: TrialSmsRequest,
        customName: String = "",
        customDueno: String = "",
        customMp: String = "",
        customMa: String = "",
        customCiudad: String = "",
        customDvc: String = ""
    ): Pair<BusinessRecord, File> = withContext(Dispatchers.IO) {
        val targetName = customName.ifBlank { request.nombreNegocio.ifBlank { "Negocio Solicitante" } }.trim()
        val targetDueno = customDueno.ifBlank { request.dueno }.trim()
        val targetMp = customMp.ifBlank { request.movilPrincipal.ifBlank { request.senderAddress } }.trim()
        val targetMa = customMa.ifBlank { request.movilAlternativo }.trim()
        val targetCiudad = customCiudad.ifBlank { request.ciudad }.trim()
        val targetDvc = customDvc.ifBlank { request.dvc }.trim()

        val cleanDvc = if (targetDvc.startsWith("DVC-", ignoreCase = true)) targetDvc.uppercase() else if (targetDvc.isNotBlank()) "DVC-${targetDvc.uppercase()}" else ""

        val existingList = SuperAdminBusinessManager.getBusinesses(context)
        val existing = existingList.firstOrNull { b ->
            (request.businessCode.isNotBlank() && b.code.equals(request.businessCode, ignoreCase = true)) ||
                    b.name.equals(targetName, ignoreCase = true) ||
                    (cleanDvc.isNotBlank() && b.dvc.equals(cleanDvc, ignoreCase = true))
        }

        // Rule: Trial begins EXCLUSIVELY when confirmation SMS is successfully sent.
        // If already in an active trial, do not reset the 7-day counter.
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val cal = Calendar.getInstance()
        val startDate: String
        val endDate: String
        if (existing != null && existing.status == "ACTIVO" && existing.startDate.isNotBlank() && existing.endDate.isNotBlank()) {
            startDate = existing.startDate
            endDate = existing.endDate
        } else {
            startDate = sdf.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 7)
            endDate = sdf.format(cal.time)
        }

        val code = existing?.code ?: if (request.businessCode.isNotBlank()) request.businessCode else SuperAdminBusinessManager.getNextBusinessCode(context)
        val token = existing?.token?.ifBlank { null } ?: request.token.ifBlank { SuperAdminBusinessManager.generateUniqueToken(context) }
        val (genUser, genPass) = SuperAdminBusinessManager.generateOwnerCredentials(targetDueno.ifBlank { existing?.dueno ?: "" }, code)
        val ownerUsername = existing?.ownerUsername?.ifBlank { null } ?: request.ownerUsername.ifBlank { genUser }
        val ownerPassword = existing?.ownerPassword?.ifBlank { null } ?: request.ownerPassword.ifBlank { genPass }

        val candidateBusiness = BusinessRecord(
            code = code,
            name = targetName,
            dueno = if (targetDueno.isNotBlank()) targetDueno else (existing?.dueno ?: ""),
            phone = targetMp,
            phoneAlt = if (targetMa.isNotBlank()) targetMa else (existing?.phoneAlt ?: ""),
            ciudad = if (targetCiudad.isNotBlank()) targetCiudad else (existing?.ciudad ?: ""),
            dvc = if (cleanDvc.isNotBlank()) cleanDvc else (existing?.dvc ?: ""),
            licenseType = "PRUEBA",
            startDate = startDate,
            endDate = endDate,
            status = "ACTIVO",
            token = token,
            ownerUsername = ownerUsername,
            ownerPassword = ownerPassword,
            trialSmsSentCount = if (existing != null && existing.trialSmsSentCount > 0) existing.trialSmsSentCount else 1,
            users = SuperAdminBusinessManager.getDefaultUsersForBusiness(
                businessName = targetName,
                phone = targetMp,
                ownerName = if (targetDueno.isNotBlank()) targetDueno else (existing?.dueno ?: ""),
                ownerUsername = ownerUsername,
                ownerPassword = ownerPassword
            )
        )

        // Mandatory rule: send confirmation SMS. Trial starts ONLY upon successful send!
        val smsText = generateConfirmationSmsText(candidateBusiness)
        val sendResult = sendSmsDirectOrIntentToPhone(context, candidateBusiness.phone, smsText)

        if (sendResult.isFailure) {
            val errorMsg = sendResult.exceptionOrNull()?.message ?: "Error desconocido al enviar SMS"
            throw IllegalStateException("No se pudo enviar el SMS de confirmación al ${candidateBusiness.phone}: $errorMsg. La prueba NO ha iniciado.")
        }

        // Save business as ACTIVO with exact start/end dates
        SuperAdminBusinessManager.saveBusiness(context, candidateBusiness)

        // Update Q_preuba.json with real dynamic data
        val generatedFile = SuperAdminBusinessManager.saveDynamicQPreubaFile(context)

        // Mark SMS as processed
        markSmsAsProcessed(context, request.uniqueKey)

        Pair(candidateBusiness, generatedFile)
    }

    /**
     * Generates or updates Q_licencias.json for commercial license approval
     */
    suspend fun generateOrUpdateLicenciasJson(
        context: Context,
        dvc: String,
        nombreNegocio: String,
        numeroMovil: String,
        tipoLicencia: String,
        durationDays: Int, // e.g. 365 for 1 year, or -1 for permanent
        codigoNegocio: String = "",
        urlUsuarios: String = "",
        estado: String = "ACTIVO",
        motivoRevocacion: String = "",
        fechaRevocacion: String = "",
        customStartDate: String = "",
        customEndDate: String = "",
        numeroMovilAlt: String = ""
    ): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val localFile = File(context.filesDir, "Q_licencias.json")
        val existingContent = fetchExistingJson(CommercialLicenseManager.URL_LICENCIAS, localFile)

        val code = if (codigoNegocio.isNotBlank()) codigoNegocio else {
            SuperAdminBusinessManager.getBusinessByDvc(context, cleanDvc)?.code ?: SuperAdminBusinessManager.getNextBusinessCode(context)
        }
        val targetUrlUsuarios = if (urlUsuarios.isNotBlank()) urlUsuarios else {
            "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_${code}usuarios.json"
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val fechaInicio = if (customStartDate.isNotBlank()) customStartDate.trim() else sdf.format(cal.time)
        val fechaFin = if (customEndDate.isNotBlank()) {
            customEndDate.trim()
        } else if (durationDays > 0) {
            cal.add(Calendar.DAY_OF_YEAR, durationDays)
            sdf.format(cal.time)
        } else {
            "PERMANENTE"
        }

        val rootObj: JSONObject = if (existingContent.isNotBlank()) {
            try {
                JSONObject(existingContent)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        val licenciasArray = if (rootObj.has("licencias")) {
            rootObj.getJSONArray("licencias")
        } else {
            JSONArray().also { rootObj.put("licencias", it) }
        }

        var replaced = false
        for (i in 0 until licenciasArray.length()) {
            val item = licenciasArray.optJSONObject(i) ?: continue
            val itemDvc = item.optString("dvc", "")
            val normItemDvc = if (itemDvc.startsWith("DVC-", ignoreCase = true)) itemDvc.uppercase() else "DVC-${itemDvc.uppercase()}"
            if (normItemDvc.equals(cleanDvc, ignoreCase = true)) {
                item.put("codigo", code)
                item.put("nombreNegocio", nombreNegocio)
                item.put("numeroMovil", numeroMovil)
                if (numeroMovilAlt.isNotBlank()) {
                    item.put("numeroMovilAlt", numeroMovilAlt)
                }
                item.put("tipoLicencia", tipoLicencia)
                item.put("fechaInicio", fechaInicio)
                item.put("fechaFin", fechaFin)
                item.put("estado", estado.trim().uppercase())
                item.put("urlUsuarios", targetUrlUsuarios)
                if (motivoRevocacion.isNotBlank()) {
                    item.put("motivoRevocacion", motivoRevocacion.trim())
                } else {
                    item.remove("motivoRevocacion")
                }
                if (fechaRevocacion.isNotBlank()) {
                    item.put("fechaRevocacion", fechaRevocacion.trim())
                } else {
                    item.remove("fechaRevocacion")
                }
                replaced = true
                break
            }
        }

        if (!replaced) {
            val newRecord = JSONObject().apply {
                put("codigo", code)
                put("dvc", cleanDvc)
                put("nombreNegocio", nombreNegocio)
                put("numeroMovil", numeroMovil)
                if (numeroMovilAlt.isNotBlank()) {
                    put("numeroMovilAlt", numeroMovilAlt)
                }
                put("tipoLicencia", tipoLicencia)
                put("fechaInicio", fechaInicio)
                put("fechaFin", fechaFin)
                put("estado", estado.trim().uppercase())
                put("urlUsuarios", targetUrlUsuarios)
                if (motivoRevocacion.isNotBlank()) {
                    put("motivoRevocacion", motivoRevocacion.trim())
                }
                if (fechaRevocacion.isNotBlank()) {
                    put("fechaRevocacion", fechaRevocacion.trim())
                }
            }
            licenciasArray.put(newRecord)
        }

        val jsonString = rootObj.toString(2)
        localFile.writeText(jsonString)
        return@withContext localFile
    }

    /**
     * Revokes a business license in Q_licencias.json (Fase 5)
     */
    suspend fun revokeLicenciaJson(
        context: Context,
        dvc: String,
        motivo: String,
        fechaRevocacion: String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    ): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val localFile = File(context.filesDir, "Q_licencias.json")
        val existingContent = fetchExistingJson(CommercialLicenseManager.URL_LICENCIAS, localFile)

        val rootObj: JSONObject = if (existingContent.isNotBlank()) {
            try {
                JSONObject(existingContent)
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        val licenciasArray = if (rootObj.has("licencias")) {
            rootObj.getJSONArray("licencias")
        } else {
            JSONArray().also { rootObj.put("licencias", it) }
        }

        var found = false
        for (i in 0 until licenciasArray.length()) {
            val item = licenciasArray.optJSONObject(i) ?: continue
            val itemDvc = item.optString("dvc", "")
            val normItemDvc = if (itemDvc.startsWith("DVC-", ignoreCase = true)) itemDvc.uppercase() else "DVC-${itemDvc.uppercase()}"
            if (normItemDvc.equals(cleanDvc, ignoreCase = true)) {
                item.put("estado", "REVOCADA")
                item.put("motivoRevocacion", motivo.trim())
                item.put("fechaRevocacion", fechaRevocacion.trim())
                found = true
                break
            }
        }

        if (!found) {
            val biz = SuperAdminBusinessManager.getBusinessByDvc(context, cleanDvc)
            val newRecord = JSONObject().apply {
                put("codigo", biz?.code ?: "001")
                put("dvc", cleanDvc)
                put("nombreNegocio", biz?.name ?: "Negocio")
                put("numeroMovil", biz?.phone ?: "")
                put("tipoLicencia", biz?.licenseType ?: "MENSUAL")
                put("fechaInicio", biz?.startDate ?: fechaRevocacion)
                put("fechaFin", biz?.endDate ?: fechaRevocacion)
                put("estado", "REVOCADA")
                put("motivoRevocacion", motivo.trim())
                put("fechaRevocacion", fechaRevocacion.trim())
                put("urlUsuarios", biz?.urlUsuarios ?: "")
            }
            licenciasArray.put(newRecord)
        }

        val jsonString = rootObj.toString(2)
        localFile.writeText(jsonString)
        return@withContext localFile
    }

    /**
     * Renews a business license in Q_licencias.json (Fase 5)
     */
    suspend fun renewLicenciaJson(
        context: Context,
        dvc: String,
        durationDays: Int, // -1 for permanente, > 0 for days
        customEndDate: String = ""
    ): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val biz = SuperAdminBusinessManager.getBusinessByDvc(context, cleanDvc)
        val nombre = biz?.name ?: "Negocio"
        val phone = biz?.phone ?: ""
        val code = biz?.code ?: "001"
        val urlUsr = biz?.urlUsuarios ?: ""
        val tipoLic = when {
            durationDays < 0 || customEndDate.equals("PERMANENTE", ignoreCase = true) -> "PERMANENTE"
            durationDays >= 360 -> "ANUAL"
            else -> "MENSUAL"
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val fechaInicio = sdf.format(cal.time)
        val fechaFin = if (customEndDate.isNotBlank()) {
            customEndDate.trim()
        } else if (durationDays > 0) {
            cal.add(Calendar.DAY_OF_YEAR, durationDays)
            sdf.format(cal.time)
        } else {
            "PERMANENTE"
        }

        return@withContext generateOrUpdateLicenciasJson(
            context = context,
            dvc = cleanDvc,
            nombreNegocio = nombre,
            numeroMovil = phone,
            tipoLicencia = tipoLic,
            durationDays = durationDays,
            codigoNegocio = code,
            urlUsuarios = urlUsr,
            estado = "ACTIVO",
            motivoRevocacion = "",
            fechaRevocacion = "",
            customStartDate = fechaInicio,
            customEndDate = fechaFin
        )
    }

    /**
     * Shares a generated JSON file using Android's native share sheet
     */
    fun shareJsonFile(context: Context, file: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "Archivo de licencias ElQadre: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, title).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    /**
     * Reads all items from local Q_preuba.json
     */
    fun readPruebasJson(context: Context): List<PruebaJsonItem> {
        val file = File(context.filesDir, "Q_preuba.json")
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("pruebas") ?: return emptyList()
            val list = mutableListOf<PruebaJsonItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    PruebaJsonItem(
                        codigo = obj.optString("codigo", ""),
                        dvc = obj.optString("dvc", ""),
                        nombreNegocio = obj.optString("nombreNegocio", ""),
                        numeroMovil = obj.optString("numeroMovil", ""),
                        numeroMovilAlt = obj.optString("numeroMovilAlt", ""),
                        fechaInicio = obj.optString("fechaInicio", ""),
                        fechaFin = obj.optString("fechaFin", ""),
                        estado = obj.optString("estado", "ACTIVO"),
                        urlUsuarios = obj.optString("urlUsuarios", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Reads all items from local Q_licencias.json
     */
    fun readLicenciasJson(context: Context): List<LicenciaJsonItem> {
        val file = File(context.filesDir, "Q_licencias.json")
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val array = root.optJSONArray("licencias") ?: return emptyList()
            val list = mutableListOf<LicenciaJsonItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    LicenciaJsonItem(
                        codigo = obj.optString("codigo", ""),
                        dvc = obj.optString("dvc", ""),
                        nombreNegocio = obj.optString("nombreNegocio", ""),
                        numeroMovil = obj.optString("numeroMovil", ""),
                        numeroMovilAlt = obj.optString("numeroMovilAlt", ""),
                        tipoLicencia = obj.optString("tipoLicencia", "PERMANENTE"),
                        fechaInicio = obj.optString("fechaInicio", ""),
                        fechaFin = obj.optString("fechaFin", ""),
                        estado = obj.optString("estado", "ACTIVO"),
                        urlUsuarios = obj.optString("urlUsuarios", ""),
                        motivoRevocacion = obj.optString("motivoRevocacion", ""),
                        fechaRevocacion = obj.optString("fechaRevocacion", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Extends a trial by 7 days in Q_preuba.json
     */
    suspend fun extendPruebaDays(context: Context, dvc: String, extraDays: Int = 7): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
        val localFile = File(context.filesDir, "Q_preuba.json")
        val content = if (localFile.exists()) localFile.readText() else ""
        if (content.isBlank()) return@withContext localFile

        val rootObj = try { JSONObject(content) } catch (_: Exception) { JSONObject() }
        val array = rootObj.optJSONArray("pruebas") ?: return@withContext localFile

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val itemDvc = item.optString("dvc", "")
            val normDvc = if (itemDvc.startsWith("DVC-", ignoreCase = true)) itemDvc.uppercase() else "DVC-${itemDvc.uppercase()}"
            if (normDvc.equals(cleanDvc, ignoreCase = true)) {
                val currentFin = item.optString("fechaFin", "")
                val cal = Calendar.getInstance()
                try {
                    val parsed = sdf.parse(currentFin)
                    if (parsed != null && parsed.after(cal.time)) {
                        cal.time = parsed
                    }
                } catch (_: Exception) {}
                cal.add(Calendar.DAY_OF_YEAR, extraDays)
                item.put("fechaFin", sdf.format(cal.time))
                item.put("estado", "ACTIVO")
                break
            }
        }
        localFile.writeText(rootObj.toString(2))
        return@withContext localFile
    }

    /**
     * Removes a business trial record from local Q_preuba.json
     */
    suspend fun removeBusinessFromPruebasJson(context: Context, code: String, dvc: String = ""): File = withContext(Dispatchers.IO) {
        val cleanDvc = if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else if (dvc.isNotBlank()) "DVC-${dvc.uppercase()}" else ""
        val formattedCode = BusinessCodeHelper.formatCode(code)
        val localFile = File(context.filesDir, "Q_preuba.json")
        val content = if (localFile.exists()) localFile.readText() else ""
        if (content.isBlank()) return@withContext localFile

        val rootObj = try { JSONObject(content) } catch (_: Exception) { JSONObject() }
        val array = rootObj.optJSONArray("pruebas") ?: return@withContext localFile
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val itemCode = item.optString("codigo", "")
            val itemDvc = item.optString("dvc", "")
            val normItemDvc = if (itemDvc.startsWith("DVC-", ignoreCase = true)) itemDvc.uppercase() else if (itemDvc.isNotBlank()) "DVC-${itemDvc.uppercase()}" else ""
            val matchesCode = itemCode.isNotBlank() && (itemCode.equals(code, ignoreCase = true) || BusinessCodeHelper.formatCode(itemCode).equals(formattedCode, ignoreCase = true))
            val matchesDvc = cleanDvc.isNotBlank() && normItemDvc.equals(cleanDvc, ignoreCase = true)

            if (!matchesCode && !matchesDvc) {
                newArray.put(item)
            }
        }
        rootObj.put("pruebas", newArray)
        localFile.writeText(rootObj.toString(2))
        return@withContext localFile
    }
}

data class PruebaJsonItem(
    val codigo: String,
    val dvc: String,
    val nombreNegocio: String,
    val numeroMovil: String,
    val numeroMovilAlt: String = "",
    val fechaInicio: String,
    val fechaFin: String,
    val estado: String,
    val urlUsuarios: String
)

data class LicenciaJsonItem(
    val codigo: String,
    val dvc: String,
    val nombreNegocio: String,
    val numeroMovil: String,
    val numeroMovilAlt: String = "",
    val tipoLicencia: String,
    val fechaInicio: String,
    val fechaFin: String,
    val estado: String,
    val urlUsuarios: String,
    val motivoRevocacion: String = "",
    val fechaRevocacion: String = ""
)
