package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransferValidationTest {

    @Test
    fun testFormato1_TransfermovilConTelefono() {
        val sms = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."

        assertTrue("Debe validar formato 1 como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertFalse("No debe validar formato 1 como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Debe ser válido en general", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Debe parsear correctamente formato 1", parsed)
        assertEquals("5351110746", parsed!!.phoneNumber)
        assertEquals("9212069990303927", parsed.recipientAccount)
        assertEquals(5000.00, parsed.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("KW601MRAWO999", parsed.transactionNumber)
        assertEquals("27/8/2026", parsed.dateStr)
        assertEquals("Transfermóvil", parsed.gateway)
        assertTrue(parsed.hasPhone)
    }

    @Test
    fun testFormato2_TransfermovilSinTelefono() {
        val sms = "Se ha realizado una transferencia a la cuenta 9212069990303927 de 10.00 CUP. Nro. Transaccion KW601NFJBT999. Fecha: 29/8/2026."

        assertTrue("Debe validar formato 2 como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertFalse("No debe validar formato 2 como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Debe ser válido en general", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "PAGOxMOVIL")
        assertNotNull("Debe parsear correctamente formato 2", parsed)
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
    fun testFormato3_EnzonaTransferenciaRecibida() {
        val sms = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"

        assertFalse("No debe validar formato 3 como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertTrue("Debe validar formato 3 como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Debe ser válido en general", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull("Debe parsear correctamente formato 3", parsed)
        assertEquals(3325.00, parsed!!.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("Qrr6FuhO4Yiu", parsed.transactionNumber)
        assertEquals("ENZONA", parsed.gateway)
    }

    @Test
    fun testFormato4_EnzonaPagoRecibido() {
        val sms = "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn"

        assertFalse("No debe validar formato 4 como Transfermóvil", SmsTransferParser.isTransfermovilTransferSms(sms))
        assertTrue("Debe validar formato 4 como ENZONA", SmsTransferParser.isEnzonaTransferSms(sms))
        assertTrue("Debe ser válido en general", SmsTransferParser.isValidTransferSms(sms))

        val parsed = SmsTransferParser.parseTransferSms(sms, sender = "ENZONA")
        assertNotNull("Debe parsear correctamente formato 4", parsed)
        assertEquals(1550.00, parsed!!.amount, 0.001)
        assertEquals("CUP", parsed.currency)
        assertEquals("lwoTTFnYIwvn", parsed.transactionNumber)
        assertEquals("ENZONA", parsed.gateway)
    }

    @Test
    fun testRemitentesAutorizados() {
        // Transfermóvil senders
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("PAGOxMOVIL"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("PAGO POR MOVIL"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("TRANSFERMOVIL"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("PAGOMOVIL"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("8888"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("5000"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("BANDEC"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("BANMET"))
        assertTrue(SmsTransferParser.isAuthorizedTransfermovilSender("BPA"))

        // ENZONA sender
        assertTrue(SmsTransferParser.isAuthorizedEnzonaSender("ENZONA"))
        assertTrue(SmsTransferParser.isAuthorizedEnzonaSender("enzona"))
        assertFalse(SmsTransferParser.isAuthorizedEnzonaSender("PAGOxMOVIL"))
        assertFalse(SmsTransferParser.isAuthorizedEnzonaSender("+5351234567"))

        // Unauthorized senders
        assertFalse(SmsTransferParser.isAuthorizedSender("+5351234567"))
        assertFalse(SmsTransferParser.isAuthorizedSender("BANCO"))
        assertFalse(SmsTransferParser.isAuthorizedSender("ETECSA"))
        assertFalse(SmsTransferParser.isAuthorizedSender(""))
        assertFalse(SmsTransferParser.isAuthorizedSender(null))
    }

    @Test
    fun testMensajesQueDebenSerRechazados() {
        val nonTransferMessages = listOf(
            "Usted ha pagado 150.00 CUP de su factura eléctrica con éxito.",
            "Recarga recibida de 500 CUP a su línea móvil.",
            "Su saldo disponible en la cuenta es de 2500.00 CUP.",
            "Usted ha realizado una transferencia a la cuenta 9212069990303927 de 50.00 CUP.",
            "ENZONA: Su código de verificación es 654321.",
            "ENZONA pago emitido Importe: 200.00 CUP No.: ABC123XYZ",
            "ENZONA compra realizada Importe: 120.00 CUP",
            "Operación fallida. Fondos insuficientes para completar la transacción.",
            "Aviso de mantenimiento del sistema bancario nacional.",
            "Factura pagada correctamente. Ref: 83921938.",
            "Pago de agua e hidrología realizado de 40.00 CUP.",
            "Transfermóvil informa que los servicios se restablecerán a las 18:00.",
            "Estimado cliente, su débito automático de 300.00 CUP fue procesado."
        )

        for (msg in nonTransferMessages) {
            assertFalse("El mensaje '$msg' debe ser rechazado", SmsTransferParser.isValidTransferSms(msg))
            assertNull("parseTransferSms debe retornar null para '$msg'", SmsTransferParser.parseTransferSms(msg))
            assertNull("parseTransferSms con sender PAGOxMOVIL debe retornar null para '$msg'", SmsTransferParser.parseTransferSms(msg, sender = "PAGOxMOVIL"))
            assertNull("parseTransferSms con sender ENZONA debe retornar null para '$msg'", SmsTransferParser.parseTransferSms(msg, sender = "ENZONA"))
        }
    }

    @Test
    fun testRechazoPorRemitenteInvalido() {
        val validFormato1 = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026."
        val validFormato3 = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu"

        // Mensaje con formato válido pero enviado por un remitente no autorizado (ej: número particular)
        assertNull("Debe rechazar si el remitente es un teléfono particular", SmsTransferParser.parseTransferSms(validFormato1, sender = "+5351234567"))
        assertNull("Debe rechazar si el remitente es un teléfono particular para ENZONA", SmsTransferParser.parseTransferSms(validFormato3, sender = "+5351234567"))

        // Mensaje de ENZONA enviado desde PAGOxMOVIL (canal cruzado no autorizado)
        assertNull("Debe rechazar si formato ENZONA viene con remitente PAGOxMOVIL", SmsTransferParser.parseTransferSms(validFormato3, sender = "PAGOxMOVIL"))
    }

    @Test
    fun testResolveTransferenciaOrigen() {
        val txEnzona1 = com.example.data.local.model.Transferencia(
            transactionNumber = "Qrr6FuhO4Yiu",
            amount = 3325.0,
            rawSmsBody = "ENZONA transferencia recibida Importe: 3325.00 CUP No.: Qrr6FuhO4Yiu",
            source = "ENZONA_SMS"
        )
        val txEnzona2 = com.example.data.local.model.Transferencia(
            transactionNumber = "lwoTTFnYIwvn",
            amount = 1550.0,
            rawSmsBody = "ENZONA pago recibido, Importe: 1550.00 CUP No.: lwoTTFnYIwvn",
            source = "ENZONA_SMS"
        )
        val txTm1 = com.example.data.local.model.Transferencia(
            transactionNumber = "KW601MRAWO999",
            amount = 5000.0,
            rawSmsBody = "El titular del telefono 5351110746 le ha realizado una transferencia a la cuenta 9212069990303927 de 5000.00 CUP. Nro. Transaccion KW601MRAWO999. Fecha: 27/8/2026.",
            source = "TRANSFERMOVIL_SMS"
        )
        val txTm2 = com.example.data.local.model.Transferencia(
            transactionNumber = "KW601NFJBT999",
            amount = 10.0,
            rawSmsBody = "Se ha realizado una transferencia a la cuenta 9212069990303927 de 10.00 CUP. Nro. Transaccion KW601NFJBT999. Fecha: 29/8/2026.",
            source = "TRANSFERMOVIL_SMS"
        )

        assertEquals("ENZONA", com.example.ui.screens.cajero.resolveTransferenciaOrigen(txEnzona1))
        assertEquals("ENZONA", com.example.ui.screens.cajero.resolveTransferenciaOrigen(txEnzona2))
        assertEquals("Transfermóvil", com.example.ui.screens.cajero.resolveTransferenciaOrigen(txTm1))
        assertEquals("Transfermóvil", com.example.ui.screens.cajero.resolveTransferenciaOrigen(txTm2))

        val all = listOf(txEnzona1, txEnzona2, txTm1, txTm2)
        val totalAmount = all.sumOf { it.amount }
        val enzonaCount = all.count { com.example.ui.screens.cajero.resolveTransferenciaOrigen(it) == "ENZONA" }
        val tmCount = all.count { com.example.ui.screens.cajero.resolveTransferenciaOrigen(it) == "Transfermóvil" }

        assertEquals(9885.0, totalAmount, 0.001)
        assertEquals(2, enzonaCount)
        assertEquals(2, tmCount)
    }
}
