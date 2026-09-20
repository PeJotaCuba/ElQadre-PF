package com.example.ui.screens.barra

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Jornada
import com.example.data.local.model.OrderItem
import com.example.data.local.model.TableOrder
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BarraHistorialTab(
    uiState: MainUiState,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf("ACTIVA") }
    var selectedArchivedJornada by remember { mutableStateOf<Jornada?>(null) }

    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id ?: 0L

    // Current dependiente filter
    val currentUsername = uiState.currentUser?.username ?: "barra1"

    // Comandas of the active jornada
    val activeOrders = remember(uiState.allOrders, activeJornadaId, currentUsername) {
        uiState.allOrders.filter { order ->
            order.jornadaId == activeJornadaId && (
                order.waiterUsername.equals(currentUsername, ignoreCase = true) ||
                order.customerName.contains("Barra", ignoreCase = true) ||
                order.totalBarra > 0
            )
        }.sortedBy { it.comandaNumber }
    }

    val cobradaOrders = remember(activeOrders) {
        activeOrders.filter { it.status == "COBRADA" }
    }

    val cobradaOrderIds = remember(cobradaOrders) { cobradaOrders.map { it.id }.toSet() }
    val cobradaItems = remember(uiState.allOrderItems, cobradaOrderIds) {
        uiState.allOrderItems.filter { cobradaOrderIds.contains(it.orderId) && it.destination == "BARRA" }
    }

    val totalMontoCobrado = remember(cobradaOrders) { cobradaOrders.sumOf { it.totalAmount } }
    val totalCantidadProductos = remember(cobradaItems) { cobradaItems.sumOf { it.quantity } }

    val totalBebidasVendidas = remember(cobradaItems) {
        cobradaItems.filter {
            !it.notes.contains("CONFITER", ignoreCase = true)
        }.sumOf { it.quantity }
    }

    val totalConfiteriasVendidas = remember(cobradaItems) {
        cobradaItems.filter {
            it.notes.contains("CONFITER", ignoreCase = true)
        }.sumOf { it.quantity }
    }

    val montoPorBebida = remember(activeJornada, uiState.currentUser) {
        if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
            activeJornada!!.utilidadSalonMontoUnitario
        } else {
            uiState.currentUser?.montoPorProducto ?: 0.0
        }
    }

    val utilidadesPotenciales = totalBebidasVendidas * montoPorBebida

    val archivedJornadas = remember(uiState.allJornadas) {
        uiState.allJornadas.filter { !it.isOpen }.sortedByDescending { it.id }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "Historial y Archivo de Barra",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Consulta el registro cronológico de comandas y jornadas archivadas",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        // Sub-tabs Selector: JORNADA ACTIVA vs ARCHIVO
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate100, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { selectedSubTab = "ACTIVA" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSubTab == "ACTIVA") ElQadreNavy else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtab_historial_activa")
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "JORNADA ACTIVA (${activeOrders.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selectedSubTab == "ACTIVA") Color.White else Slate700
                        )
                    }
                }

                Surface(
                    onClick = { selectedSubTab = "ARCHIVO" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedSubTab == "ARCHIVO") ElQadreNavy else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("subtab_historial_archivo")
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "ARCHIVO DE JORNADAS (${archivedJornadas.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (selectedSubTab == "ARCHIVO") Color.White else Slate700
                        )
                    }
                }
            }
        }

        if (selectedSubTab == "ACTIVA") {
            // Contadores de la Jornada Activa
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CONTADORES DE LA JORNADA ACTIVA",
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Total Cobrado Registrado", fontSize = 10.sp, color = Slate500)
                                Text(
                                    "$${"%.2f".format(totalMontoCobrado)} CUP",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Emerald700
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Comandas Registradas", fontSize = 10.sp, color = Slate500)
                                Text(
                                    "${cobradaOrders.size} cobradas / ${activeOrders.size} total",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = ElQadreNavy
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("• Bebidas Vendidas: $totalBebidasVendidas ud.", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate700)
                            Text("• Confiterías: $totalConfiteriasVendidas ud.", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Slate700)
                        }

                        HorizontalDivider(color = Slate100)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("• Utilidades Potenciales (Bebidas):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text(
                                "$${"%.2f".format(utilidadesPotenciales)} CUP ($totalBebidasVendidas ud. × $${"%.2f".format(montoPorBebida)})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald700
                            )
                        }
                    }
                }
            }

            // Products sold summary
            if (cobradaItems.isNotEmpty()) {
                item {
                    val groupedItems = remember(cobradaItems) {
                        cobradaItems.groupBy { it.productName }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Resumen de Productos Vendidos:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            HorizontalDivider(color = Slate100)

                            groupedItems.forEach { (prodName, items) ->
                                val qty = items.sumOf { it.quantity }
                                val sum = items.sumOf { it.unitPrice * it.quantity }
                                val isConf = items.firstOrNull()?.notes?.contains("CONFITER", ignoreCase = true) == true

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
                                        Text(prodName, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Text("$qty ud. | $${"%.2f".format(sum)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                            }
                        }
                    }
                }
            }

            // Title
            item {
                Text(
                    text = "Registro Consecutivo de Comandas (${activeOrders.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ElQadreNavy
                )
            }

            if (activeOrders.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Aún no se han registrado comandas en esta jornada.", fontSize = 12.sp, color = Slate400)
                        }
                    }
                }
            } else {
                items(activeOrders, key = { it.id }) { order ->
                    val orderItems = remember(uiState.allOrderItems, order.id) {
                        uiState.allOrderItems.filter { it.orderId == order.id }
                    }
                    BarraHistorialOrderCard(order = order, items = orderItems)
                }
            }
        } else {
            // ARCHIVO DE JORNADAS CERRADAS
            if (archivedJornadas.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Archive, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                                Text("No hay jornadas archivadas anteriores.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate600)
                                Text("Al realizar el Cierre de Turno de Barra, la jornada activa se congelará y guardará automáticamente en el Archivo.", fontSize = 11.sp, color = Slate400, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            } else {
                items(archivedJornadas, key = { it.id }) { jornada ->
                    BarraArchivedJornadaCard(
                        jornada = jornada,
                        uiState = uiState,
                        onViewDetail = { selectedArchivedJornada = jornada }
                    )
                }
            }
        }
    }

    // Modal to view detailed archived jornada
    selectedArchivedJornada?.let { jornada ->
        BarraArchivedJornadaDetailDialog(
            jornada = jornada,
            uiState = uiState,
            onDismiss = { selectedArchivedJornada = null }
        )
    }
}

@Composable
private fun BarraHistorialOrderCard(
    order: TableOrder,
    items: List<OrderItem>
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val isCobrada = order.status == "COBRADA"
    val isDirecta = order.customerName.contains("Barra", ignoreCase = true) || order.tableNumber == null || order.tableNumber == 0
    val origenStr = if (order.customerName.contains("Llevar", ignoreCase = true)) {
        "Para Llevar"
    } else if (isDirecta) {
        "Barra directa"
    } else {
        "Salón (Mesa ${order.tableNumber})"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isCobrada) Slate200 else Color(0xFFFDE68A)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("comanda_historial_${order.comandaNumber}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreNavy
                    ) {
                        Text(
                            text = "Comanda #${order.comandaNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDirecta) Color(0xFFEFF6FF) else Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = origenStr,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isDirecta) Color(0xFF1D4ED8) else Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCobrada) Color(0xFFD1FAE5) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = if (isCobrada) "COBRADA" else "ABIERTA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = if (isCobrada) Emerald800 else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Timestamp
            Text(
                text = "Fecha/Hora: ${dateFormatter.format(Date(order.closedAt ?: order.createdAt))}",
                fontSize = 10.sp,
                color = Slate500
            )

            HorizontalDivider(color = Slate100)

            // Items List
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.quantity}x ${item.productName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate800,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$${"%.2f".format(item.unitPrice)} = $${"%.2f".format(item.unitPrice * item.quantity)} CUP",
                            fontSize = 10.sp,
                            color = Slate600
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Footer Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCobrada) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Pago: ${order.paymentMethod}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        if (order.cashReceived > 0) {
                            Text(
                                text = "(Rec: $${"%.0f".format(order.cashReceived)} / Camb: $${"%.0f".format(order.changeGiven)})",
                                fontSize = 9.sp,
                                color = Slate500
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Pendiente de cobro",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB45309)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Total:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    Text(
                        "$${"%.2f".format(order.totalAmount)} CUP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isCobrada) Emerald700 else Color(0xFFB45309)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarraArchivedJornadaCard(
    jornada: Jornada,
    uiState: MainUiState,
    onViewDetail: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val archivedOrders = remember(uiState.allOrders, jornada.id) {
        uiState.allOrders.filter { it.jornadaId == jornada.id && it.status == "COBRADA" }
    }
    val archivedOrderIds = remember(archivedOrders) { archivedOrders.map { it.id }.toSet() }
    val archivedItems = remember(uiState.allOrderItems, archivedOrderIds) {
        uiState.allOrderItems.filter { archivedOrderIds.contains(it.orderId) && it.destination == "BARRA" }
    }

    val totalMonto = archivedOrders.sumOf { it.totalAmount }
    val totalBebidas = archivedItems.filter { !it.notes.contains("CONFITER", ignoreCase = true) }.sumOf { it.quantity }
    val totalConfiterias = archivedItems.filter { it.notes.contains("CONFITER", ignoreCase = true) }.sumOf { it.quantity }

    val rate = jornada.utilidadSalonMontoUnitario
    val utilidadesPotenciales = totalBebidas * rate

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
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
                Text("Jornada Archivada #${jornada.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                    Text("CERRADA", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
            }

            Text(
                "Apertura: ${dateFormatter.format(Date(jornada.openedAt))} • Cierre: ${if (jornada.closedAt != null) dateFormatter.format(Date(jornada.closedAt)) else '-'}",
                fontSize = 10.sp,
                color = Slate500
            )
            Text(
                "Dependiente / Cerró: ${(jornada.closedBy ?: "").ifBlank { jornada.openedBy }}",
                fontSize = 10.sp,
                color = Slate500
            )

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Comandas Cobradas", fontSize = 10.sp, color = Slate500)
                    Text("${archivedOrders.size}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Productos Vendidos", fontSize = 10.sp, color = Slate500)
                    Text("$totalBebidas Beb. / $totalConfiterias Conf.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1E3A8A))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Cobrado", fontSize = 10.sp, color = Slate500)
                    Text("$${"%.2f".format(totalMonto)} CUP", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Emerald700)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Utilidades Potenciales:", fontSize = 10.sp, color = Slate500)
                Text("$${"%.2f".format(utilidadesPotenciales)} CUP ($totalBebidas ud. × $${"%.2f".format(rate)})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            }

            OutlinedButton(
                onClick = onViewDetail,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VER DETALLE DE COMANDAS ARCHIVADAS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BarraArchivedJornadaDetailDialog(
    jornada: Jornada,
    uiState: MainUiState,
    onDismiss: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    val archivedOrders = remember(uiState.allOrders, jornada.id) {
        uiState.allOrders.filter { it.jornadaId == jornada.id }
    }
    val cobradaOrders = archivedOrders.filter { it.status == "COBRADA" }
    val cobradaOrderIds = cobradaOrders.map { it.id }.toSet()
    val archivedItems = remember(uiState.allOrderItems, cobradaOrderIds) {
        uiState.allOrderItems.filter { cobradaOrderIds.contains(it.orderId) && it.destination == "BARRA" }
    }

    val totalMonto = cobradaOrders.sumOf { it.totalAmount }
    val totalBebidas = archivedItems.filter { !it.notes.contains("CONFITER", ignoreCase = true) }.sumOf { it.quantity }
    val totalConfiterias = archivedItems.filter { it.notes.contains("CONFITER", ignoreCase = true) }.sumOf { it.quantity }
    val rate = jornada.utilidadSalonMontoUnitario
    val utilidadesPotenciales = totalBebidas * rate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Jornada #${jornada.id} - Archivo de Barra", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                Surface(shape = RoundedCornerShape(6.dp), color = Slate100) {
                    Text("CERRADA", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = Slate50, border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dependiente: ${(jornada.closedBy ?: "").ifBlank { jornada.openedBy }}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Apertura: ${dateFormatter.format(Date(jornada.openedAt))}", fontSize = 10.sp, color = Slate600)
                        Text("Cierre: ${if (jornada.closedAt != null) dateFormatter.format(Date(jornada.closedAt)) else '-'}", fontSize = 10.sp, color = Slate600)
                        HorizontalDivider(color = Slate200)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Comandas: ${cobradaOrders.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Ventas: $totalBebidas B / $totalConfiterias C", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                            Text("Total: $${"%.2f".format(totalMonto)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Emerald700)
                        }

                        HorizontalDivider(color = Slate200)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Utilidades Potenciales:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                            Text("$${"%.2f".format(utilidadesPotenciales)} CUP ($totalBebidas ud. × $${"%.2f".format(rate)})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                    }
                }

                Text("Detalle de Comandas Cobradas (${cobradaOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(cobradaOrders) { order ->
                        val items = archivedItems.filter { it.orderId == order.id }
                        val isDirecta = order.customerName.contains("Barra", ignoreCase = true) || order.tableNumber == null || order.tableNumber == 0
                        val origen = if (order.customerName.contains("Llevar", ignoreCase = true)) {
                            "Para Llevar"
                        } else if (isDirecta) {
                            "Barra directa"
                        } else {
                            "Salón (Mesa ${order.tableNumber})"
                        }

                        Surface(shape = RoundedCornerShape(6.dp), color = Slate50, border = BorderStroke(1.dp, Slate200), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Comanda #${order.comandaNumber} • $origen", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("$${"%.2f".format(order.totalAmount)} CUP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Emerald700)
                                }
                                items.forEach { item ->
                                    Text("  • ${item.quantity}x ${item.productName} ($${"%.2f".format(item.unitPrice)})", fontSize = 10.sp, color = Slate600)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)) {
                Text("CERRAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}
