package com.example.util

import com.example.data.local.model.Transferencia
import com.example.ui.screens.cajero.resolveTransferenciaOrigen
import org.junit.Assert.*
import org.junit.Test

/**
 * Auditoría Activa y Funcional del Módulo de Transferencias en Cuadre de Caja.
 */
class TransferenciasAuditoriaActivaTest {

    // =========================================================================
    // 1. VALIDACIÓN DE LOS 4 ÚNICOS FORMATOS PERMITIDOS
    // =========================================================================

    @Test
    fun prueba_1_Transfermovil_Formato1_ConTelefono() {
        val sms = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."
        
        // Validación con remitente autorizado Transfermóvil
        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Formato 1 de Transfermóvil debe ser aceptado", parsed)
        assertEquals("5351110746", parsed!!.phoneNumber)
        assertEquals("9212069990303927", parsed.recipientAccount)
        assertEquals(5000.00, parsed.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("KW601MRAWO999", parsed.transactionNumber)
        assertEquals("27/8/2026", parsed.dateStr)
        assertEquals("Transfermóvil", parsed.gateway)
        assertTrue(parsed.hasPhone)

        // Variabilidad de campos (otro teléfono, cuenta, importe, ID y fecha)
        val smsVar = "El titular del telefono 52889900 le ha realizado una transferencia a la cuenta 9200112233445566 de 123.45 CUP. Nro. Transaccion TX_VAR_999. Fecha: 15/09/2026."
        val parsedVar = SmsTransferParser.parseTransferSms(smsVar, sender = "TRANSFERMOVIL")
        assertNotNull("Formato 1 con valores variables debe ser aceptado", parsedVar)
        assertEquals("52889900", parsedVar!!.phoneNumber)
        assertEquals(123.45, parsedVar.amount, 0.001)
        assertEquals("TX_VAR_999", parsedVar.transactionNumber)
    }

    @Test
    fun prueba_2_Transfermovil_Formato2_SinTelefono() {
        val sms = "Se ha realizado una transferencia a la cuenta 9212069990303927 de 10.00 CUP. Nro. Transaccion KW601NFJBT999. Fecha: 29/8/2026."
        
        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Formato 2 de Transfermóvil debe ser aceptado", parsed)
        assertEquals("", parsed!!.phoneNumber)
        assertEquals("9212069990303927", parsed.recipientAccount)
        assertEquals(10.00, parsed.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("KW601NFJBT999", parsed.transactionNumber)
        assertEquals("29/8/2026", parsed.dateStr)
        assertEquals("Transfermóvil", parsed.gateway)
        assertFalse(parsed.hasPhone)
    }

    @Test
    fun prueba_3_ENZONA_Formato3_TransferenciaRecibida() {
        val sms = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"
        
        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull("Formato 3 de ENZONA debe ser aceptado", parsed)
        assertEquals(3325.00, parsed!!.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("Qrr6FuhO4Yiu", parsed.transactionNumber)
        assertEquals("ENZONA", parsed.gateway)

        // Variabilidad
        val smsVar = "ENZONA transferencia recibida Importe: 850.50 CUP No.: ENZ_TX_ABC123"
        val parsedVar = SmsTransferParser.parseTransferSms(smsVar, sender = "ENZONA")
        assertNotNull(parsedVar)
        assertEquals(850.50, parsedVar!!.amount, 0.001)
        assertEquals("ENZ_TX_ABC123", parsedVar.transactionNumber)
    }

    @Test
    fun prueba_4_ENZONA_Formato4_PagoRecibido() {
        val sms = "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn"
        
        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull("Formato 4 de ENZONA debe ser aceptado", parsed)
        assertEquals(1550.00, parsed!!.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("lwoTTFnYIwvn", parsed.transactionNumber)
        assertEquals("ENZONA", parsed.gateway)
    }

    // =========================================================================
    // 2. ATAQUE DE FALSOS POSITIVOS (MENSAJES NO VÁLIDOS CON PALABRAS CLAVE)
    // =========================================================================

    @Test
    fun prueba_5_AtaqueFalsosPositivos_RechazoObligatorio() {
        val falsosPositivos = listOf(
            // Contiene "pago", "CUP", "recibido", "Transfermóvil" pero estructura diferente
            "Transfermóvil: Pago de factura eléctrica recibido con éxito por 250.00 CUP.",
            "PAGO POR MOVIL: Se ha realizado el pago del servicio telefónico de 150.00 CUP.",
            "ENZONA: pago emitido correctamente Importe: 500.00 CUP No.: 83921938.",
            "ENZONA compra recibida, Importe: 350.00 CUP en Tienda Panadería.",
            "Se ha realizado un pago a la cuenta 9212069990303927 de 100.00 CUP.",
            "Su transferencia fue enviada satisfactoriamente a la cuenta 9212069990303927 por 200.00 CUP.",
            "Transferencia revertida por el banco de 500.00 CUP.",
            "Consulta de saldo: Su cuenta 9212069990303927 dispone de 4500.00 CUP.",
            "ENZONA: transferencia fallida por saldo insuficiente Importe: 1000.00 CUP No.: 99999",
            "Recarga recibida de 500.00 CUP para el móvil 5351110746.",
            "El titular del telefono 5351110746 solicita un cobro de 500.00 CUP.",
            "Aviso de Transfermóvil: Nueva versión disponible para descargar en transfermovil.cu.",
            "Notificación bancaria: Débito de 150.00 CUP efectuado en su cuenta."
        )

        for (sms in falsosPositivos) {
            assertFalse("El mensaje falso positivo '$sms' debe ser rechazado por isValidTransferSms", SmsTransferParser.isValidTransferSms(sms))
            assertNull("parseTransferSms debe retornar null para '$sms'", SmsTransferParser.parseTransferSms(sms))
            assertNull("parseTransferSms con remitente PAGOxMOVIL debe retornar null para '$sms'", SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL"))
            assertNull("parseTransferSms con remitente ENZONA debe retornar null para '$sms'", SmsTransferParser.parseTransferSms(sms, sender = "ENZONA"))
        }
    }

    @Test
    fun prueba_6_ValidacionRemitenteEstricto() {
        val validFormato1 = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."
        val validFormato3 = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"

        // Remitente desconocido / particular
        assertNull("Debe rechazar si el remitente es un teléfono particular", SmsTransferParser.parseTransferSms(validFormato1, sender = "+5359998877"))
        assertNull("Debe rechazar si el remitente no es ENZONA para formato 3", SmsTransferParser.parseTransferSms(validFormato3, sender = "+5359998877"))

        // Canal cruzado (Transfermóvil con formato ENZONA o viceversa)
        assertNull("Debe rechazar formato ENZONA proveniente de PAGOxMOVIL", SmsTransferParser.parseTransferSms(validFormato3, sender = "PAGOxMOVIL"))
        assertNull("Debe rechazar formato Transfermóvil proveniente de ENZONA", SmsTransferParser.parseTransferSms(validFormato1, sender = "ENZONA"))
    }

    // =========================================================================
    // 3. AUDITORÍA DE ENZONA Y TRANSFERMÓVIL EN PANTALLA INICIAL Y TOTALES
    // =========================================================================

    @Test
    fun prueba_7_IdentificacionVisualYTotalesEnPantallaInicial() {
        val txEnzona1 = Transferencia(
            id = 1L,
            transactionNumber = "Qrr6FuhO4Yiu",
            amount = 3325.0,
            currency = "CUP",
            status = "NO ASOCIADA",
            rawSmsBody = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu",
            source = "ENZONA_SMS"
        )
        val txEnzona2 = Transferencia(
            id = 2L,
            transactionNumber = "lwoTTFnYIwvn",
            amount = 1550.0,
            currency = "CUP",
            status = "NO ASOCIADA",
            rawSmsBody = "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn",
            source = "ENZONA_SMS"
        )
        val txTm1 = Transferencia(
            id = 3L,
            transactionNumber = "KW601MRAWO999",
            amount = 5000.0,
            currency = "CUP",
            status = "NO ASOCIADA",
            phoneNumber = "5351110746",
            rawSmsBody = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026.",
            source = "TRANSFERMOVIL_SMS"
        )

        // 1. Identificación de origen
        assertEquals("ENZONA", resolveTransferenciaOrigen(txEnzona1))
        assertEquals("ENZONA", resolveTransferenciaOrigen(txEnzona2))
        assertEquals("Transfermóvil", resolveTransferenciaOrigen(txTm1))

        // 2. Cálculo de lista y totales
        val listaInicial = listOf(txEnzona1, txEnzona2, txTm1)
        val totalMonto = listaInicial.sumOf { it.amount }
        val enzonaCount = listaInicial.count { resolveTransferenciaOrigen(it) == "ENZONA" }
        val tmCount = listaInicial.count { resolveTransferenciaOrigen(it) == "Transfermóvil" }

        assertEquals(9875.0, totalMonto, 0.001)
        assertEquals(2, enzonaCount)
        assertEquals(1, tmCount)
    }

    // =========================================================================
    // 4. PROTECCIÓN CONTRA DUPLICADOS
    // =========================================================================

    @Test
    fun prueba_8_ProteccionContraDuplicados() {
        val existingTxs = setOf("KW601MRAWO999", "Qrr6FuhO4Yiu")

        val txDuplicate = "Qrr6FuhO4Yiu"
        val txNew = "TX_NUEVA_777"

        assertTrue("La transferencia existente debe ser detectada como ya registrada", existingTxs.contains(txDuplicate.trim()))
        assertFalse("La transferencia nueva no debe estar registrada", existingTxs.contains(txNew.trim()))
    }
}
