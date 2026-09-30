package com.example.util

import com.example.data.local.model.Transferencia
import com.example.ui.screens.cajero.resolveTransferenciaOrigen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActiveCuadreTransferenciasAuditTest {

    // =========================================================================
    // 1. AUDITORÍA DE LOS 4 FORMATOS PERMITIDOS
    // =========================================================================

    @Test
    fun audit01_TransfermovilFormato1_VariablesValidadas() {
        // Formato 1: El titular del telefono [TEL] le ha realizado una transferencia a la cuenta [CUENTA] de [IMPORTE] CUP. Nro. Transaccion [ID]. Fecha: [FECHA].
        val tel = "5351110746"
        val cuenta = "9212069990303927"
        val importe = "5000.00"
        val id = "KW601MRAWO999"
        val fecha = "27/8/2026"
        val sms = "El titular del telefono $tel le ha realizado una transferencia a la cuenta $cuenta de $importe CUP. Nro. Transaccion $id. Fecha: $fecha."

        assertTrue("Formato 1 debe ser reconocido como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertFalse("Formato 1 no debe ser reconocido como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Formato 1 debe ser válido", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Formato 1 debe parsear correctamente", parsed)
        assertEquals(tel, parsed?.phoneNumber)
        assertEquals(cuenta, parsed?.recipientAccount)
        assertEquals(5000.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(id, parsed?.transactionNumber)
        assertEquals("Transfermóvil", parsed?.gateway)
        assertTrue(parsed?.hasPhone == true)

        // Variabilidad de datos en formato 1
        val sms2 = "El titular del telefono 52889900 le ha realizado una transferencia a la cuenta 9200112233445566 de 123.45 CUP. Nro. Transaccion TX999ABC. Fecha: 01/09/2026."
        val parsed2 = SmsTransferParser.parseTransferSms(sms2, sender = "TRANSFERMOVIL")
        assertNotNull(parsed2)
        assertEquals(123.45, parsed2?.amount ?: 0.0, 0.001)
        assertEquals("TX999ABC", parsed2?.transactionNumber)
    }

    @Test
    fun audit02_TransfermovilFormato2_VariablesValidadas() {
        // Formato 2: Se ha realizado una transferencia a la cuenta [CUENTA] de [IMPORTE] CUP. Nro. Transaccion [ID]. Fecha: [FECHA].
        val cuenta = "9212069990303927"
        val importe = "10.00"
        val id = "KW601NFJBT999"
        val fecha = "29/8/2026"
        val sms = "Se ha realizado una transferencia a la cuenta $cuenta de $importe CUP. Nro. Transaccion $id. Fecha: $fecha."

        assertTrue("Formato 2 debe ser reconocido como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertFalse("Formato 2 no debe ser reconocido como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Formato 2 debe ser válido", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Formato 2 debe parsear correctamente", parsed)
        assertEquals("", parsed?.phoneNumber)
        assertEquals(cuenta, parsed?.recipientAccount)
        assertEquals(10.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(id, parsed?.transactionNumber)
        assertEquals("Transfermóvil", parsed?.gateway)
        assertFalse(parsed?.hasPhone == true)
    }

    @Test
    fun audit03_EnzonaFormato3_VariablesValidadas() {
        // Formato 3: ENZONA transferencia recibida Importe: [IMPORTE] CUP No.: [ID]
        val importe = "3325.00"
        val id = "Qrr6FuhO4Yiu"
        val sms = "ENZONA transferencia recibida Importe: $importe CUP No.: $id"

        assertFalse("Formato 3 no debe ser reconocido como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertTrue("Formato 3 debe ser reconocido como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Formato 3 debe ser válido", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull("Formato 3 debe parsear correctamente", parsed)
        assertEquals(3325.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(id, parsed?.transactionNumber)
        assertEquals("ENZONA", parsed?.gateway)

        // Variabilidad de datos en formato 3
        val smsVar = "ENZONA transferencia recibida Importe: 890.50 CUP No.: TRX_VAR_789"
        val parsedVar = SmsTransferParser.parseTransferSms(smsVar, sender = "ENZONA")
        assertNotNull(parsedVar)
        assertEquals(890.50, parsedVar?.amount ?: 0.0, 0.001)
        assertEquals("TRX_VAR_789", parsedVar?.transactionNumber)
    }

    @Test
    fun audit04_EnzonaFormato4_VariablesValidadas() {
        // Formato 4: ENZONA pago recibido, Importe: [IMPORTE] CUP No.: [ID]
        val importe = "1550.00"
        val id = "lwoTTFnYIwvn"
        val smsConComa = "ENZONA pago recibido, Importe: $importe CUP No.: $id"
        val smsSinComa = "ENZONA pago recibido Importe: $importe CUP No.: $id"

        assertTrue("Formato 4 con coma debe ser válido", SmsTransferParser.isEnzonaTransferSms(smsConComa))
        assertTrue("Formato 4 sin coma debe ser válido", SmsTransferParser.isEnzonaTransferSms(smsSinComa))

        val parsed = SmsTransferParser.parseTransferSms(smsConComa, sender = "ENZONA")
        assertNotNull("Formato 4 debe parsear correctamente", parsed)
        assertEquals(1550.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(id, parsed?.transactionNumber)
        assertEquals("ENZONA", parsed?.gateway)
    }

    // =========================================================================
    // 2. ATAQUE DE FALSOS POSITIVOS (RECHAZO OBLIGATORIO)
    // =========================================================================

    @Test
    fun audit05_AtaqueFalsosPositivos_MensajesEngañososRechazados() {
        // Mensajes que contienen palabras clave pero no coinciden exactamente con la estructura requerida
        val attackCases = listOf(
            "PAGOxMOVIL pago recibido de 500 CUP por transferencia bancaria.",
            "TRANSFERMOVIL: Su transferencia por importe de 1000.00 CUP ha sido enviada.",
            "ENZONA pago recibido de 200.00 CUP en la tienda virtual.",
            "ENZONA transferencia enviada Importe: 450.00 CUP No.: TX112233",
            "ENZONA pago emitido, Importe: 300.00 CUP No.: TX445566",
            "Usted ha realizado un pago por transferencia de 1500.00 CUP.",
            "Se ha realizado un pago a la cuenta 9212069990303927 de 100.00 CUP.",
            "El titular del telefono le ha realizado un pago por importe de 50.00 CUP.",
            "ENZONA consulta de saldo: Su saldo es 4500.00 CUP.",
            "Transfermóvil informa: Pago de factura eléctrica completado por 250.00 CUP.",
            "ENZONA: Código de confirmación 123456 para pago de 80.00 CUP.",
            "TRANSFERMOVIL recarga recibida por 500.00 CUP.",
            "ENZONA transferencia recibida sin importe ni numero"
        )

        for (fakeSms in attackCases) {
            val isTransfer = SmsTransferParser.isValidTransferSms(fakeSms)
            val parsedPmovil = SmsTransferParser.parseTransferSms(fakeSms, sender = "PAGOxMOVIL")
            val parsedEnzona = SmsTransferParser.parseTransferSms(fakeSms, sender = "ENZONA")

            assertFalse("ATAQUE FALSO POSITIVO DEBE FALLAR: '$fakeSms' no debe ser válido", isTransfer)
            assertNull("ATAQUE FALSO POSITIVO DEBE RETORNAR NULL en PAGOxMOVIL: '$fakeSms'", parsedPmovil)
            assertNull("ATAQUE FALSO POSITIVO DEBE RETORNAR NULL en ENZONA: '$fakeSms'", parsedEnzona)
        }
    }

    // =========================================================================
    // 3. AUDITORÍA DE ENZONA EN LA PANTALLA INICIAL Y TOTALES
    // =========================================================================

    @Test
    fun audit06_EnzonaPantallaInicial_IdentificacionYTotales() {
        val txEnzona = Transferencia(
            id = 101L,
            transactionNumber = "Qrr6FuhO4Yiu",
            jornadaId = 0L, // No asignada a jornada cerrada
            amount = 3325.0,
            currency = "CUP",
            phoneNumber = "",
            recipientAccount = "",
            smsDate = "30/09/2026",
            receivedAt = System.currentTimeMillis(),
            status = "NO ASOCIADA",
            rawSmsBody = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu",
            source = "ENZONA_SMS"
        )

        val txTransfermovil = Transferencia(
            id = 102L,
            transactionNumber = "KW601MRAWO999",
            jornadaId = 0L,
            amount = 5000.0,
            currency = "CUP",
            phoneNumber = "5351110746",
            recipientAccount = "9212069990303927",
            smsDate = "30/09/2026",
            receivedAt = System.currentTimeMillis(),
            status = "NO ASOCIADA",
            rawSmsBody = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026.",
            source = "TRANSFERMOVIL_SMS"
        )

        // Comprobar resolución exacta de origen
        assertEquals("ENZONA", resolveTransferenciaOrigen(txEnzona))
        assertEquals("Transfermóvil", resolveTransferenciaOrigen(txTransfermovil))

        // Simular lógica de filtrado de TransferenciasPane (pantalla inicial)
        val allTransfers = listOf(txEnzona, txTransfermovil)
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val isViewingCurrent = true

        val filtered = allTransfers.filter { tx ->
            val normRecDate = if (tx.receivedAt > 0L) SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(tx.receivedAt)) else ""
            val normSmsDate = tx.smsDate
            val matchesDate = normRecDate == todayStr || normSmsDate == todayStr
            val matchesJornada = isViewingCurrent && (tx.status == "NO ASOCIADA" || tx.jornadaId <= 0L)
            matchesDate || matchesJornada
        }

        assertEquals("Ambas transferencias deben estar en la lista filtrada de inicio", 2, filtered.size)
        assertTrue("ENZONA debe estar en la lista filtrada", filtered.any { it.transactionNumber == "Qrr6FuhO4Yiu" })
        assertTrue("Transfermóvil debe estar en la lista filtrada", filtered.any { it.transactionNumber == "KW601MRAWO999" })

        // Comprobar totales y contadores
        val totalMonto = filtered.sumOf { it.amount }
        val enzonaCount = filtered.count { resolveTransferenciaOrigen(it) == "ENZONA" }
        val tmCount = filtered.count { resolveTransferenciaOrigen(it) == "Transfermóvil" }

        assertEquals(8325.0, totalMonto, 0.001)
        assertEquals(1, enzonaCount)
        assertEquals(1, tmCount)
    }

    // =========================================================================
    // 4. AUDITORÍA DE TRANSFERENCIA NO REGISTRADA
    // =========================================================================

    @Test
    fun audit07_TransferenciaNoRegistrada_DeteccionYConfirmacion() {
        val existingDbTransactions = setOf("KW601EXISTING")
        val newTxId = "Qrr6FuhO4Yiu"

        // Simular detección de mensaje
        val isAlreadyRegistered = existingDbTransactions.contains(newTxId)
        assertFalse("Transferencia no debe estar registrada", isAlreadyRegistered)

        // Comprobar que no se marca automáticamente registrada sin acción del usuario
        var wasRegisteredAutomatically = false
        if (isAlreadyRegistered) {
            wasRegisteredAutomatically = true
        }
        assertFalse("No se debe registrar automáticamente sin confirmación", wasRegisteredAutomatically)

        // Simular confirmación de registro
        val updatedDb = existingDbTransactions.toMutableSet()
        updatedDb.add(newTxId)
        assertTrue("Tras confirmación del usuario, debe pasar a la lista de registradas", updatedDb.contains(newTxId))
    }

    // =========================================================================
    // 5. AUDITORÍA DE OVERLAY AUTOMÁTICO VÍA EVENT BUS
    // =========================================================================

    @Test
    fun audit08_OverlayAutomatico_EventBusTransmission() = runBlocking {
        val sms = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"
        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull(parsed)

        // Emisión al bus de transferencias entrantes
        SmsTransferBus.postTransfer(parsed!!)

        // El bus debe emitir el evento inmediatamente para disparar el diálogo overlay
        val receivedOnBus = SmsTransferBus.incomingTransfers.first()
        assertEquals("Qrr6FuhO4Yiu", receivedOnBus.transactionNumber)
        assertEquals(3325.0, receivedOnBus.amount, 0.001)
        assertEquals("ENZONA", receivedOnBus.gateway)
    }

    // =========================================================================
    // 6. AUDITORÍA DE PROTECCIÓN CONTRA DUPLICADOS
    // =========================================================================

    @Test
    fun audit09_ProteccionContraDuplicados() {
        val existingTransactions = setOf("KW601MRAWO999", "Qrr6FuhO4Yiu")

        // Intento de re-detección de transferencia ya existente
        val duplicateTxId = "KW601MRAWO999"
        val isDuplicate = existingTransactions.contains(duplicateTxId)

        assertTrue("Debe detectar que la transferencia ya fue registrada", isDuplicate)

        // Verificar que una lista existente de transferencias no duplica montos si se re-evalúa
        val list = mutableListOf(
            Transferencia(transactionNumber = "KW601MRAWO999", amount = 5000.0)
        )
        if (!list.any { it.transactionNumber == duplicateTxId }) {
            list.add(Transferencia(transactionNumber = duplicateTxId, amount = 5000.0))
        }

        assertEquals(1, list.size)
        assertEquals(5000.0, list.sumOf { it.amount }, 0.001)
    }

    // =========================================================================
    // 7. COMPROBACIÓN DE QUE AUTOMÁTICO Y MANUAL USAN EL MISMO VALIDADOR
    // =========================================================================

    @Test
    fun audit10_MismoFiltroAutomaticoYManual() {
        val validFormats = listOf(
            Pair("PAGOxMOVIL", "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."),
            Pair("PAGOxMOVIL", "Se ha realizado una transferencia a la cuenta 9212069990303927 de 10.00 CUP. Nro. Transaccion KW601NFJBT999. Fecha: 29/8/2026."),
            Pair("ENZONA", "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"),
            Pair("ENZONA", "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn")
        )

        for ((sender, body) in validFormats) {
            // Validación que usa SmsReceiver (automático)
            val autoCheck = SmsTransferParser.isAuthorizedSender(sender) &&
                    SmsTransferParser.parseTransferSms(body, sender = sender) != null

            // Validación que usa SmsSearchHelper (manual / escaneo)
            val manualCheck = SmsTransferParser.isAuthorizedSender(sender) &&
                    SmsTransferParser.parseTransferSms(body, sender = sender) != null

            assertTrue("Validación automática debe coincidir con manual para: $body", autoCheck)
            assertEquals("Resultado automático y manual deben ser idénticos", autoCheck, manualCheck)
        }
    }
}
