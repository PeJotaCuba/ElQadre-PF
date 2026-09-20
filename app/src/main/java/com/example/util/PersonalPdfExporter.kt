package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.local.model.PaymentProposal
import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PersonalPdfExporter {

    fun exportPersonalContratadoPdf(
        context: Context,
        personalList: List<PersonalContratado>,
        businessName: String = "El Qadre",
        duenoName: String = "Dueño"
    ): File? {
        try {
            val pageWidth = 595
            val pageHeight = 842
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()

            val navyColor = Color.parseColor("#1B2A4A")
            val goldColor = Color.parseColor("#C89D3C")
            val darkSlate = Color.parseColor("#334155")
            val lightBg = Color.parseColor("#F8FAFC")
            val borderColor = Color.parseColor("#CBD5E1")

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

            // Background
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), paint)

            var currentY = 40f

            // Header Banner
            paint.color = navyColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 60f, paint)

            paint.color = goldColor
            canvas.drawRect(30f, currentY + 56f, (pageWidth - 30).toFloat(), currentY + 60f, paint)

            // Title inside Banner
            paint.color = Color.WHITE
            paint.textSize = 17f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("EL QADRE - CONTROL DE PERSONAL CONTRATADO", 45f, currentY + 35f, paint)

            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Negocio: $businessName | Emitido por: $duenoName", 45f, currentY + 50f, paint)

            currentY += 75f

            // Info Card
            paint.color = lightBg
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 45f, paint)

            paint.color = borderColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 45f, paint)

            paint.style = Paint.Style.FILL
            paint.color = darkSlate
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Total de personal registrado: ${personalList.size}", 45f, currentY + 20f, paint)
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Fecha de emisión: ${dateFormatter.format(Date())}", 45f, currentY + 35f, paint)

            currentY += 60f

            // Table Header
            paint.color = navyColor
            paint.style = Paint.Style.FILL
            canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 26f, paint)

            paint.color = Color.WHITE
            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("NOMBRE COMPLETO", 40f, currentY + 17f, paint)
            canvas.drawText("CARNÉ IDENTIDAD (CI)", 210f, currentY + 17f, paint)
            canvas.drawText("MÓVIL", 350f, currentY + 17f, paint)
            canvas.drawText("FORMA DE PAGO", 440f, currentY + 17f, paint)

            currentY += 26f

            if (personalList.isEmpty()) {
                paint.color = lightBg
                paint.style = Paint.Style.FILL
                canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 30f, paint)

                paint.color = darkSlate
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No hay personal contratado registrado actualmente.", 45f, currentY + 20f, paint)
                currentY += 30f
            } else {
                personalList.forEachIndexed { index, p ->
                    val isEven = index % 2 == 0
                    paint.color = if (isEven) Color.WHITE else lightBg
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 26f, paint)

                    paint.color = borderColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.5f
                    canvas.drawRect(30f, currentY, (pageWidth - 30).toFloat(), currentY + 26f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = darkSlate
                    paint.textSize = 9.5f

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    canvas.drawText(p.nombreCompleto.take(25), 40f, currentY + 17f, paint)

                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    canvas.drawText(if (p.carnetIdentidad.isNotBlank()) p.carnetIdentidad else "-", 210f, currentY + 17f, paint)
                    canvas.drawText(if (p.movil.isNotBlank()) p.movil else "-", 350f, currentY + 17f, paint)
                    canvas.drawText(if (p.formaPago.isNotBlank()) p.formaPago.take(18) else "-", 440f, currentY + 17f, paint)

                    currentY += 26f
                }
            }

            pdfDocument.finishPage(page)

            val fileName = "Personal_Contratado_${System.currentTimeMillis()}.pdf"
            val file = File(context.getExternalFilesDir(null), fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Personal Contratado - $businessName")
                putExtra(Intent.EXTRA_TEXT, "Registro de personal contratado generado en ElQadre.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Registro de Personal (PDF)"))

            return file
        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar PDF de personal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    fun exportPersonalConfig(
        context: Context,
        proposals: List<PaymentProposal>,
        users: List<User>
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint()
            val linePaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val dateOnlyFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            // Background
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, 595f, 842f, paint)

            // Header Banner
            paint.color = Color.parseColor("#1E293B") // ElQadreNavy
            canvas.drawRect(30f, 30f, 565f, 100f, paint)

            // Title inside Banner
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("EL QADRE - CONTROL DE PERSONAL", 45f, 62f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("PROPUESTA DE CONFIGURACIÓN DE ESQUEMAS DE PAGO", 45f, 82f, paint)

            // Right side of banner: Date
            paint.textAlign = Paint.Align.RIGHT
            paint.textSize = 9f
            canvas.drawText("Fecha: ${dateOnlyFormatter.format(Date())}", 550f, 62f, paint)
            canvas.drawText("Generado offline", 550f, 82f, paint)
            paint.textAlign = Paint.Align.LEFT // Reset alignment

            var currentY = 125f

            // Summary Section
            paint.color = Color.parseColor("#0F172A")
            paint.textSize = 12f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("RESUMEN DE PROPUESTA", 30f, currentY, paint)
            currentY += 5f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 18f

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.textSize = 10f
            paint.color = Color.parseColor("#475569")
            
            val totalUsers = users.filter { it.role != UserRole.DUENO }.size
            val activeProposals = proposals.filter { it.isActiveProposal }.size
            
            canvas.drawText("Total de trabajadores en sistema (excluyendo Dueño):", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$totalUsers", 340f, currentY, paint)
            
            currentY += 15f
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Configuraciones activas propuestas:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("$activeProposals", 340f, currentY, paint)

            currentY += 15f
            paint.color = Color.parseColor("#475569")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Fecha de emisión:", 30f, currentY, paint)
            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(dateFormatter.format(Date()), 340f, currentY, paint)

            currentY += 30f

            // Roles to list
            val targetRoles = listOf(
                UserRole.CAJERO to "CAJERO",
                UserRole.SALON to "DEPENDIENTE DE SALÓN",
                UserRole.BARRA to "DEPENDIENTE DE BARRA"
            )

            for ((role, label) in targetRoles) {
                // Section Title for Role
                paint.color = Color.parseColor("#1E293B")
                paint.textSize = 11f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawRect(30f, currentY - 12f, 565f, currentY + 4f, Paint().apply { color = Color.parseColor("#F1F5F9") })
                canvas.drawText("[ $label ]", 35f, currentY, paint)
                
                currentY += 18f

                // Table headers
                paint.color = Color.parseColor("#475569")
                paint.textSize = 9f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("Trabajador", 35f, currentY, paint)
                canvas.drawText("Tipo de Pago", 220f, currentY, paint)
                canvas.drawText("Tasa / Porcentaje", 380f, currentY, paint)
                canvas.drawText("Estado", 490f, currentY, paint)

                currentY += 4f
                canvas.drawLine(30f, currentY, 565f, currentY, Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f })
                currentY += 14f

                // Filter users with this role
                val workersOfRole = users.filter { it.role == role }
                if (workersOfRole.isEmpty()) {
                    paint.color = Color.parseColor("#94A3B8")
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                    canvas.drawText("No hay trabajadores registrados en este rol", 35f, currentY, paint)
                    currentY += 18f
                } else {
                    for (worker in workersOfRole) {
                        val prop = proposals.find { it.username == worker.username }
                        
                        // Fallback defaults if no proposal has been saved yet
                        val pAmount = prop?.paymentAmount ?: worker.montoPorProducto
                        val pType = prop?.paymentType ?: when (worker.role) {
                            UserRole.SALON -> "Por plato"
                            UserRole.BARRA -> "Comisión de barra"
                            UserRole.CAJERO -> "Porcentaje general"
                            else -> "Por plato"
                        }
                        val isActive = prop?.isActiveProposal ?: worker.isActive

                        paint.color = Color.parseColor("#0F172A")
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                        paint.textSize = 9.5f
                        
                        // Name & username
                        canvas.drawText("${worker.fullName} (${worker.username})", 35f, currentY, paint)
                        // Type
                        canvas.drawText(pType, 220f, currentY, paint)
                        // Rate/Amount
                        val amountText = if (pType.lowercase().contains("porcentaje") || pType.lowercase().contains("comisión")) {
                            "${"%.1f".format(pAmount)}%"
                        } else {
                            "$${"%.2f".format(pAmount)} CUP"
                        }
                        canvas.drawText(amountText, 380f, currentY, paint)
                        
                        // Status
                        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        if (isActive) {
                            paint.color = Color.parseColor("#059669") // Green
                            canvas.drawText("ACTIVO", 490f, currentY, paint)
                        } else {
                            paint.color = Color.parseColor("#DC2626") // Red
                            canvas.drawText("INACTIVO", 490f, currentY, paint)
                        }

                        currentY += 15f
                    }
                }
                currentY += 15f
            }

            // Footer Disclaimer
            currentY = 800f
            canvas.drawLine(30f, currentY, 565f, currentY, linePaint)
            currentY += 15f
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.parseColor("#64748B")
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("El Qadre - Propuesta offline de personal y nómina gestionada por el Dueño.", 297.5f, currentY, paint)
            canvas.drawText("Este documento se almacena localmente y se comparte sin intervención de servicios en la nube.", 297.5f, currentY + 10f, paint)
            paint.textAlign = Paint.Align.LEFT

            pdfDocument.finishPage(page)

            // Save PDF to downloads or cache
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileName = "ElQadre_Propuesta_Personal_${System.currentTimeMillis()}.pdf"
            var pdfFile = File(downloadsDir, fileName)
            
            try {
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
            } catch (e: Exception) {
                pdfFile = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
                FileOutputStream(pdfFile).use { pdfDocument.writeTo(it) }
            }
            
            pdfDocument.close()
            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun sharePdfFile(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Propuesta de Personal y Pagos - El Qadre")
                putExtra(Intent.EXTRA_TEXT, "Propuesta de configuración de personal y pagos generada por el Dueño.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Enviar Configuración (PDF)"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error al compartir PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportAndShareJson(
        context: Context,
        proposals: List<PaymentProposal>,
        users: List<User>
    ) {
        try {
            val root = org.json.JSONObject()
            root.put("tipo", "PROPUESTA_PERSONAL_PAGOS")
            root.put("version", "1.0.0")
            root.put("fechaHora", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))
            root.put("emisor", "Dueño")

            val array = org.json.JSONArray()
            for (user in users.filter { it.role != UserRole.DUENO }) {
                val prop = proposals.find { it.username == user.username }
                val item = org.json.JSONObject()
                item.put("username", user.username)
                item.put("fullName", user.fullName)
                item.put("role", user.role.name)
                item.put("paymentAmount", prop?.paymentAmount ?: user.montoPorProducto)
                item.put("paymentType", prop?.paymentType ?: when (user.role) {
                    UserRole.SALON -> "Por plato"
                    UserRole.BARRA -> "Comisión de barra"
                    UserRole.CAJERO -> "Porcentaje general"
                    UserRole.ADMIN -> "Esquema fijo"
                    else -> "Por plato"
                })
                item.put("isActiveProposal", prop?.isActiveProposal ?: user.isActive)
                array.put(item)
            }
            root.put("propuestas", array)

            val jsonString = root.toString(4)
            val fileName = "ElQadre_Propuesta_Personal_${System.currentTimeMillis()}.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var jsonFile = File(downloadsDir, fileName)

            try {
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                FileOutputStream(jsonFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
            } catch (e: Exception) {
                jsonFile = File(context.getExternalFilesDir(null) ?: context.cacheDir, fileName)
                FileOutputStream(jsonFile).use { it.write(jsonString.toByteArray(Charsets.UTF_8)) }
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", jsonFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Propuesta Personal y Pagos JSON")
                putExtra(Intent.EXTRA_TEXT, "Archivo JSON con configuración de pagos El Qadre.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Enviar Configuración (JSON)"))

        } catch (e: Exception) {
            Toast.makeText(context, "Error al generar JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
