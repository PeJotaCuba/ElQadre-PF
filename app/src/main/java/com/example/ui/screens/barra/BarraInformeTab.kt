package com.example.ui.screens.barra

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.util.BarraReportPdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BarraInformeTab(
    uiState: MainUiState,
    computedBarraItems: List<BarraProductItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id ?: 0L
    val currentUsername = uiState.currentUser?.fullName?.ifBlank { uiState.currentUser?.username } ?: "Dependiente de Barra"

    val closedOrders = remember(uiState.allOrders, activeJornadaId) {
        uiState.allOrders.filter { it.jornadaId == activeJornadaId && it.status == "COBRADA" }
    }

    val closedOrderIds = remember(closedOrders) { closedOrders.map { it.id }.toSet() }
    val closedOrderItems = remember(uiState.allOrderItems, closedOrderIds) {
        uiState.allOrderItems.filter { closedOrderIds.contains(it.orderId) && it.destination == "BARRA" }
    }

    val totalMontoCobrado = remember(closedOrders) { closedOrders.sumOf { it.totalAmount } }

    val totalBebidasVendidas = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "BEBIDAS" }.sumOf { it.ventasJornada }
    }
    val totalConfiteriasVendidas = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.ventasJornada }
    }

    val totalImporteBebidas = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "BEBIDAS" }.sumOf { it.ventasJornada * it.product.price }
    }
    val totalImporteConfiterias = remember(computedBarraItems) {
        computedBarraItems.filter { it.subcategory == "CONFITERÍAS" }.sumOf { it.ventasJornada * it.product.price }
    }
    val totalTeoricoVentas = totalImporteBebidas + totalImporteConfiterias

    val montoPorBebida = remember(activeJornada, uiState.currentUser) {
        if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
            activeJornada!!.utilidadSalonMontoUnitario
        } else {
            uiState.currentUser?.montoPorProducto ?: 0.0
        }
    }

    val utilidadesPotenciales = totalBebidasVendidas * montoPorBebida

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var exportedPdfFile by remember { mutableStateOf<File?>(null) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    fun generatePdfDocument(): File? {
        if (activeJornada == null) {
            Toast.makeText(context, "No hay jornada activa.", Toast.LENGTH_SHORT).show()
            return null
        }
        val file = BarraReportPdfExporter.exportBarraReport(
            context = context,
            activeJornada = activeJornada,
            closedOrders = closedOrders,
            orderItems = closedOrderItems,
            currentDependiente = currentUsername,
            montoPorBebida = montoPorBebida,
            barraProductItems = computedBarraItems
        )
        exportedPdfFile = file
        return file
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title Header
        item {
            Column {
                Text(
                    text = "Informe Operativo de Barra",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Resumen de ventas, categorías, utilidades potenciales y exportación",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        // Action Buttons: Descargar/Ver PDF y Enviar al Administrador
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isGeneratingPdf = true
                        val file = generatePdfDocument()
                        isGeneratingPdf = false
                        if (file != null) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "PDF guardado en: ${file.name}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(context, "Error al generar PDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_descargar_pdf_barra"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver / Descargar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val file = exportedPdfFile ?: generatePdfDocument()
                        if (file != null) {
                            val adminPhone = uiState.businessConfig?.telefono ?: ""
                            BarraReportPdfExporter.shareBarraReportToAdmin(context, file, adminPhone)
                        } else {
                            Toast.makeText(context, "Error al preparar documento PDF.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_enviar_admin_barra"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar al Administrador", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Preview Card: Datos Generales
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. DATOS DE LA JORNADA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ElQadreNavy
                        )
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFD1FAE5)) {
                            Text(
                                text = "Jornada #${activeJornada?.id ?: 0}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Dependiente:", fontSize = 11.sp, color = Slate600)
                        Text(currentUsername, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fecha de Emisión:", fontSize = 11.sp, color = Slate600)
                        Text(dateOnlyFormatter.format(Date()), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hora Apertura:", fontSize = 11.sp, color = Slate600)
                        Text(
                            if (activeJornada != null) dateFormatter.format(Date(activeJornada.openedAt)) else "-",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estado:", fontSize = 11.sp, color = Slate600)
                        Text("ACTIVA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Comandas Cobradas:", fontSize = 11.sp, color = Slate600)
                        Text("${closedOrders.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }
                }
            }
        }

        // Preview Card: Resumen Financiero y Categorías
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. RESUMEN DE VENTAS Y CATEGORÍAS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElQadreNavy
                    )

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("BEBIDAS ($totalBebidasVendidas ud.)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                Text("$${"%.2f".format(totalImporteBebidas)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Amber50,
                            border = BorderStroke(1.dp, Amber100),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("CONFITERÍAS ($totalConfiteriasVendidas ud.)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber800)
                                Text("$${"%.2f".format(totalImporteConfiterias)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Amber800)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Teórico (Ventas × Precio):", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(totalTeoricoVentas)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Cobrado Registrado:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                                Text("$${"%.2f".format(totalMontoCobrado)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Emerald700)
                            }
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // Utilidades Potenciales Section
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Utilidades Potenciales (Bebidas):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text(
                                "$${"%.2f".format(utilidadesPotenciales)} CUP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald700
                            )
                            Text(
                                "Cálculo: $totalBebidasVendidas bebidas vendidas × $${"%.2f".format(montoPorBebida)} CUP fijados por Administrador",
                                fontSize = 10.sp,
                                color = Emerald800
                            )
                        }
                    }
                }
            }
        }

        // Preview Card: Detalle de Productos
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "3. DESGLOSE DE PRODUCTOS VENDIDOS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElQadreNavy
                    )

                    HorizontalDivider(color = Slate100)

                    val soldItems = computedBarraItems.filter { it.ventasJornada > 0 }
                    if (soldItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text("No se registran ventas en la jornada activa.", fontSize = 11.sp, color = Slate400)
                        }
                    } else {
                        soldItems.forEach { item ->
                            val isConf = item.subcategory == "CONFITERÍAS"
                            val sum = item.ventasJornada * item.product.price

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isConf) Amber50 else Color(0xFFEFF6FF)
                                    ) {
                                        Text(
                                            text = if (isConf) "CONF" else "BEB",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isConf) Amber800 else Color(0xFF1E40AF),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                    Text(item.product.name, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }

                                Text(
                                    "${item.ventasJornada} ud. × $${"%.2f".format(item.product.price)} = $${"%.2f".format(sum)} CUP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
