package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.example.data.local.model.Tanda
import java.util.Locale

sealed class CocinaTandasScanResult {
    data class Success(val newTandas: List<Tanda>, val senderPhone: String, val timestamp: Long) : CocinaTandasScanResult()
    data class AllAlreadyImported(val senderPhone: String, val totalInSms: Int) : CocinaTandasScanResult()
    data class BusinessMismatch(val expected: String, val actual: String, val senderPhone: String) : CocinaTandasScanResult()
    data class InvalidFormat(val reason: String, val senderPhone: String) : CocinaTandasScanResult()
    object NoSmsFound : CocinaTandasScanResult()
}

object SmsTandasHelper {

    const val HEADER_PREFIX = "ELQADRE|TANDAS|V1|"
    const val TANDA_LINE_PREFIX = "TANDA|"

    /**
     * Normaliza números de teléfono quitando prefijos de país (+53, 53), ceros iniciales y espacios/guiones.
     */
    fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val digits = phone.filter { it.isDigit() }
        if (digits.length >= 10 && digits.startsWith("53")) {
            return digits.substring(2)
        }
        if (digits.length == 9 && digits.startsWith("0")) {
            return digits.substring(1)
        }
        return digits
    }

    /**
     * Comprueba si un número de teléfono de remitente coincide con alguno de los números registrados de Cocina.
     */
    fun matchesCocinaPhone(sender: String, cocinaPhoneNumbers: List<String>): Boolean {
        val normalizedSender = normalizePhone(sender)
        if (normalizedSender.isBlank()) return false
        return cocinaPhoneNumbers.any { registered ->
            val normReg = normalizePhone(registered)
            normReg.isNotBlank() && (normalizedSender == normReg || normalizedSender.endsWith(normReg) || normReg.endsWith(normalizedSender))
        }
    }

    /**
     * Genera el SMS estructurado oficial de intercambio COCINA -> DUEÑO.
     * Mismo formato para ambos extremos.
     */
    fun buildTandasSms(
        codigoNegocio: String,
        cocinaIdentifier: String,
        tandas: List<Tanda>,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val safeNeg = codigoNegocio.ifBlank { "NEG-000001" }
        val safeCocina = cocinaIdentifier.ifBlank { "COCINA" }
        val sb = StringBuilder()
        // Header oficial: ELQADRE|TANDAS|V1|NEG:<codigo>|COCINA:<id>|TS:<timestamp>|COUNT:<count>
        sb.append(HEADER_PREFIX)
        sb.append("NEG:").append(safeNeg).append("|")
        sb.append("COCINA:").append(safeCocina).append("|")
        sb.append("TS:").append(timestamp).append("|")
        sb.append("COUNT:").append(tandas.size)

        // Líneas de Tandas
        tandas.forEach { t ->
            sb.append("\n")
            sb.append(TANDA_LINE_PREFIX)
            sb.append(t.uuid.replace("|", "_").replace("#", "_")).append("#")
            sb.append(t.tandaNumber.replace("#", "_")).append("#")
            sb.append(t.productId).append("#")
            sb.append(t.productName.replace("#", "_").replace("|", "_")).append("#")
            sb.append(t.baseMateriaPrimaId).append("#")
            sb.append(t.baseMateriaPrimaName.replace("#", "_").replace("|", "_")).append("#")
            sb.append(t.baseQuantityUsed).append("#")
            sb.append(t.baseQuantityUnit.replace("#", "_")).append("#")
            sb.append(t.productionFactor).append("#")
            sb.append(t.estimatedYield).append("#")
            sb.append(t.actualYield).append("#")
            sb.append(t.productionUnit.replace("#", "_")).append("#")
            sb.append(t.totalBatchCost).append("#")
            sb.append(t.status).append("#")
            sb.append(t.jornadaId).append("#")
            sb.append(t.ingredientsConsumedText.replace("#", ";").replace("|", ";"))
        }

        return sb.toString()
    }

    /**
     * Determina si un texto corresponde al formato de SMS de Tandas de ElQadre.
     */
    fun isTandasSms(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val trimmed = text.trim()
        return trimmed.startsWith(HEADER_PREFIX) || trimmed.contains(HEADER_PREFIX)
    }

    /**
     * Parsea un texto de SMS de Tandas y extrae la lista de objetos Tanda.
     */
    fun parseTandasSms(
        rawText: String,
        expectedCodigoNegocio: String? = null
    ): Pair<ParsedHeader?, List<Tanda>> {
        val trimmed = rawText.trim()
        val headerIdx = trimmed.indexOf(HEADER_PREFIX)
        if (headerIdx < 0) return Pair(null, emptyList())

        val relevantText = trimmed.substring(headerIdx)
        val lines = relevantText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return Pair(null, emptyList())

        val headerLine = lines[0]
        val headerTokens = headerLine.removePrefix(HEADER_PREFIX).split("|")
        var negCode = ""
        var cocinaId = ""
        var timestamp = System.currentTimeMillis()
        var reportedCount = 0

        headerTokens.forEach { token ->
            val parts = token.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().uppercase(Locale.ROOT)
                val value = parts[1].trim()
                when (key) {
                    "NEG", "NEGOCIO" -> negCode = value
                    "COCINA" -> cocinaId = value
                    "TS", "TIME", "TIMESTAMP" -> timestamp = value.toLongOrNull() ?: timestamp
                    "COUNT", "TANDAS" -> reportedCount = value.toIntOrNull() ?: 0
                }
            }
        }

        val header = ParsedHeader(negCode, cocinaId, timestamp, reportedCount)
        val parsedTandas = mutableListOf<Tanda>()

        // Procesar líneas siguientes
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith(TANDA_LINE_PREFIX)) {
                val dataPart = line.removePrefix(TANDA_LINE_PREFIX)
                val fields = dataPart.split("#")
                if (fields.size >= 12) {
                    try {
                        val uuid = fields.getOrNull(0)?.trim() ?: ""
                        val tandaNumber = fields.getOrNull(1)?.trim() ?: "01"
                        val productId = fields.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
                        val productName = fields.getOrNull(3)?.trim() ?: "Producto"
                        val baseMpId = fields.getOrNull(4)?.trim()?.toLongOrNull() ?: 0L
                        val baseMpName = fields.getOrNull(5)?.trim() ?: "Base"
                        val baseQty = fields.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0
                        val baseUnit = fields.getOrNull(7)?.trim() ?: "g"
                        val factor = fields.getOrNull(8)?.trim()?.toDoubleOrNull() ?: 1.0
                        val estYield = fields.getOrNull(9)?.trim()?.toDoubleOrNull() ?: 0.0
                        val actYield = fields.getOrNull(10)?.trim()?.toDoubleOrNull() ?: 0.0
                        val prodUnit = fields.getOrNull(11)?.trim() ?: "unidades"
                        val batchCost = fields.getOrNull(12)?.trim()?.toDoubleOrNull() ?: 0.0
                        val status = fields.getOrNull(13)?.trim() ?: "ACTIVA"
                        val jornadaId = fields.getOrNull(14)?.trim()?.toLongOrNull() ?: 0L
                        val ingConsumed = fields.getOrNull(15)?.trim() ?: ""

                        if (uuid.isNotBlank()) {
                            val tanda = Tanda(
                                uuid = uuid,
                                tandaNumber = tandaNumber,
                                productId = productId,
                                productName = productName,
                                date = timestamp,
                                responsibleUser = "Cocina ($cocinaId)",
                                baseMateriaPrimaId = baseMpId,
                                baseMateriaPrimaName = baseMpName,
                                baseQuantityUsed = baseQty,
                                baseQuantityUnit = baseUnit,
                                productionFactor = factor,
                                estimatedYield = estYield,
                                expectedYield = estYield,
                                actualYield = actYield,
                                productionUnit = prodUnit,
                                ingredientsConsumedText = ingConsumed,
                                status = status,
                                jornada = if (jornadaId > 0) "Jornada #$jornadaId" else "Jornada Importada",
                                jornadaId = jornadaId,
                                totalDirectIngredientsCost = batchCost,
                                totalBatchCost = batchCost,
                                inventoryDeducted = true // Ya gestionado en Cocina, no descontar doble
                            )
                            parsedTandas.add(tanda)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return Pair(header, parsedTandas)
    }

    /**
     * Escanea el buzón de SMS recibidos (content://sms/inbox) buscando exclusivamente
     * mensajes provenientes de los números registrados para Cocina con formato ElQadre Tandas.
     */
    fun scanInboxForCocinaTandas(
        context: Context,
        cocinaPhoneNumbers: List<String>,
        currentCodigoNegocio: String,
        existingTandaUuids: Set<String>
    ): CocinaTandasScanResult {
        if (cocinaPhoneNumbers.isEmpty()) {
            return CocinaTandasScanResult.NoSmsFound
        }

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return CocinaTandasScanResult.NoSmsFound
        }

        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )

            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                Telephony.Sms.DATE + " DESC LIMIT 200"
            )

            cursor?.use {
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val senderAddress = it.getString(addressIdx) ?: ""
                    val body = it.getString(bodyIdx) ?: ""
                    val smsDate = it.getLong(dateIdx)

                    // 1. Validar que el remitente sea exclusivamente un número registrado de Cocina
                    if (matchesCocinaPhone(senderAddress, cocinaPhoneNumbers)) {
                        // 2. Validar que el mensaje tenga el formato oficial de Tandas de ElQadre
                        if (isTandasSms(body)) {
                            val (header, tandas) = parseTandasSms(body, currentCodigoNegocio)
                            if (header == null) {
                                return CocinaTandasScanResult.InvalidFormat(
                                    reason = "Cabecera del mensaje de tandas no válida",
                                    senderPhone = senderAddress
                                )
                            }

                            // 3. Validar código de negocio
                            val cleanExpectedNeg = currentCodigoNegocio.trim()
                            val cleanReceivedNeg = header.codigoNegocio.trim()
                            if (cleanExpectedNeg.isNotBlank() && cleanReceivedNeg.isNotBlank() &&
                                !cleanExpectedNeg.equals(cleanReceivedNeg, ignoreCase = true)
                            ) {
                                return CocinaTandasScanResult.BusinessMismatch(
                                    expected = cleanExpectedNeg,
                                    actual = cleanReceivedNeg,
                                    senderPhone = senderAddress
                                )
                            }

                            if (tandas.isEmpty()) {
                                return CocinaTandasScanResult.InvalidFormat(
                                    reason = "El SMS de Cocina no contiene datos válidos de tandas",
                                    senderPhone = senderAddress
                                )
                            }

                            // 4. Filtrar tandas para evitar duplicados
                            val newTandas = tandas.filter { t -> !existingTandaUuids.contains(t.uuid) }
                            if (newTandas.isEmpty()) {
                                return CocinaTandasScanResult.AllAlreadyImported(
                                    senderPhone = senderAddress,
                                    totalInSms = tandas.size
                                )
                            }

                            return CocinaTandasScanResult.Success(
                                newTandas = newTandas,
                                senderPhone = senderAddress,
                                timestamp = if (smsDate > 0) smsDate else header.timestamp
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return CocinaTandasScanResult.NoSmsFound
    }
}

data class ParsedHeader(
    val codigoNegocio: String,
    val cocinaId: String,
    val timestamp: Long,
    val reportedCount: Int
)
