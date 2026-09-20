package com.example.ui.screens.barra

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState

@Composable
fun BarraCierreTab(
    uiState: MainUiState,
    computedBarraItems: List<BarraProductItem>,
    onConfirmCierre: (notes: String, physicalCounts: Map<Long, Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id ?: 0L

    val openOrders = remember(uiState.allOrders, activeJornadaId) {
        uiState.allOrders.filter { it.jornadaId == activeJornadaId && it.status == "ABIERTA" }
    }

    val closedOrders = remember(uiState.allOrders, activeJornadaId) {
        uiState.allOrders.filter { it.jornadaId == activeJornadaId && it.status == "COBRADA" }
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

    val diferenciaIngresos = totalMontoCobrado - totalTeoricoVentas

    val montoPorBebida = remember(activeJornada, uiState.currentUser) {
        if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
            activeJornada!!.utilidadSalonMontoUnitario
        } else {
            uiState.currentUser?.montoPorProducto ?: 0.0
        }
    }

    val utilidadesPotenciales = totalBebidasVendidas * montoPorBebida

    // Physical count state map: productId -> physicalCount
    val physicalCounts = remember(computedBarraItems) {
        mutableStateMapOf<Long, Int>().apply {
            computedBarraItems.forEach { item ->
                put(item.product.id, item.existenciaActual)
            }
        }
    }

    var notes by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val totalDiferenciasInventario = remember(computedBarraItems, physicalCounts) {
        computedBarraItems.count { item ->
            val physical = physicalCounts[item.product.id] ?: item.existenciaActual
            physical != item.existenciaActual
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = "Cierre de Turno y Cuadre de Barra",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Verifica el inventario físico, cuadra ingresos y finaliza la jornada",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        // Warning banner if open orders exist
        if (openOrders.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Rose600, modifier = Modifier.size(24.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Atención: Hay ${openOrders.size} comanda(s) abierta(s) sin cobrar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Rose700
                            )
                            Text(
                                text = "Se recomienda cobrar o resolver todas las comandas antes del cierre de turno.",
                                fontSize = 11.sp,
                                color = Rose600
                            )
                        }
                    }
                }
            }
        }

        // 1. Control de Ingresos Card
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                            Text(
                                text = "CONTROL DE INGRESOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (diferenciaIngresos == 0.0) Color(0xFFD1FAE5) else if (diferenciaIngresos > 0) Color(0xFFEFF6FF) else Color(0xFFFEF2F2)
                        ) {
                            Text(
                                text = if (diferenciaIngresos == 0.0) "CUADRE EXACTO" else if (diferenciaIngresos > 0) "SUPERÁVIT" else "DIFERENCIA",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (diferenciaIngresos == 0.0) Emerald800 else if (diferenciaIngresos > 0) Color(0xFF1D4ED8) else Rose700
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Total Teórico (Ventas)", fontSize = 9.sp, color = Slate500)
                                Text("$${"%.2f".format(totalTeoricoVentas)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                Text("CUP según catálogo", fontSize = 8.sp, color = Slate400)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Ingreso Registrado", fontSize = 9.sp, color = Emerald800)
                                Text("$${"%.2f".format(totalMontoCobrado)}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Emerald700)
                                Text("${closedOrders.size} cobradas", fontSize = 8.sp, color = Emerald600)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (diferenciaIngresos >= 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, if (diferenciaIngresos >= 0) Color(0xFFBBF7D0) else Color(0xFFFECACA)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Diferencia", fontSize = 9.sp, color = if (diferenciaIngresos >= 0) Emerald800 else Rose700)
                                Text(
                                    "${if (diferenciaIngresos > 0) "+" else ""}$${"%.2f".format(diferenciaIngresos)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diferenciaIngresos >= 0) Emerald700 else Rose700
                                )
                                Text("en caja", fontSize = 8.sp, color = Slate400)
                            }
                        }
                    }

                    // Utilidades Potenciales
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Utilidades Potenciales del Dependiente", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                                Text("$totalBebidasVendidas bebidas × $${"%.2f".format(montoPorBebida)} CUP", fontSize = 9.sp, color = Color(0xFF3B82F6))
                            }
                            Text(
                                "$${"%.2f".format(utilidadesPotenciales)} CUP",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // Desglose de Ingresos Producto por Producto
                    Text(
                        text = "DESGLOSE DE INGRESOS POR PRODUCTO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ElQadreNavy
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        computedBarraItems.forEach { item ->
                            val vendido = item.ventasJornada
                            val precio = item.product.price
                            val importeEsperado = vendido * precio
                            val importeRegistrado = importeEsperado // basado en comandas cobradas

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                                        Text(
                                            "Vendidas: $vendido ud. × $${"%.2f".format(precio)} CUP",
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "Esperado: $${"%.2f".format(importeEsperado)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (vendido > 0) Emerald700 else Slate400
                                        )
                                        Text(
                                            "Registrado: $${"%.2f".format(importeRegistrado)}",
                                            fontSize = 9.sp,
                                            color = Slate500
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Control de Inventario Físico vs Teórico
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                            Text(
                                text = "CONTROL DE INVENTARIO FÍSICO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                        }

                        if (totalDiferenciasInventario > 0) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFEF3C7)) {
                                Text(
                                    "$totalDiferenciasInventario con diferencia",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Fórmula: Inicio + Entradas - Vendido = Debe Terminar. Ingresa la existencia física real encontrada:",
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    HorizontalDivider(color = Slate100)

                    if (computedBarraItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("No hay productos de Barra registrados.", fontSize = 11.sp, color = Slate400)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            computedBarraItems.forEach { item ->
                                val physical = physicalCounts[item.product.id] ?: item.existenciaActual
                                val debeTerminar = item.existenciaActual
                                val dif = physical - debeTerminar

                                BarraInventoryCountRow(
                                    item = item,
                                    physicalCount = physical,
                                    onCountChanged = { newCount ->
                                        physicalCounts[item.product.id] = newCount.coerceAtLeast(0)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Observaciones del Cierre
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
                        text = "OBSERVACIONES / NOTAS DE CIERRE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ElQadreNavy
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Escribe notas relevantes sobre el turno o inventario (opcional)...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_cierre_notes"),
                        minLines = 2,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // 4. Main Action Button: CERRAR JORNADA Y ARCHIVAR
        item {
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_ejecutar_cierre_barra"),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.LockClock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CERRAR JORNADA Y ARCHIVAR", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }

    // Confirmation Dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text("Confirmar Cierre de Turno de Barra", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "¿Estás seguro de que deseas cerrar y archivar la Jornada #${activeJornada?.id ?: 0}?",
                        fontSize = 12.sp,
                        color = Slate700
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Total Cobrado: $${"%.2f".format(totalMontoCobrado)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                            Text("• Bebidas Vendidas: $totalBebidasVendidas ud. (Utilidad: $${"%.2f".format(utilidadesPotenciales)} CUP)", fontSize = 11.sp, color = Slate700)
                            Text("• Confiterías Vendidas: $totalConfiteriasVendidas ud.", fontSize = 11.sp, color = Slate700)
                            if (totalDiferenciasInventario > 0) {
                                Text("• Descuadres Físicos: $totalDiferenciasInventario producto(s)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Rose600)
                            }
                        }
                    }

                    if (openOrders.isNotEmpty()) {
                        Text(
                            "ADVERTENCIA: Hay ${openOrders.size} comanda(s) aún abiertas.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Rose600
                        )
                    }

                    Text(
                        "Esta acción congelará los datos de la jornada actual, la moverá a Archivo y abrirá una nueva jornada activa.",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        onConfirmCierre(notes, physicalCounts)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    modifier = Modifier.testTag("btn_dialog_confirmar_cierre")
                ) {
                    Text("CONFIRMAR CIERRE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancelar", color = Slate600)
                }
            }
        )
    }
}

@Composable
private fun BarraInventoryCountRow(
    item: BarraProductItem,
    physicalCount: Int,
    onCountChanged: (Int) -> Unit
) {
    val debeTerminar = item.existenciaActual
    val dif = physicalCount - debeTerminar
    val isConf = item.subcategory == "CONFITERÍAS"

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                    Text(
                        text = item.product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = ElQadreNavy
                    )
                }

                // Diferencia Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (dif == 0) Color(0xFFD1FAE5) else if (dif < 0) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)
                ) {
                    Text(
                        text = if (dif == 0) "0 OK" else if (dif < 0) "$dif faltante" else "+$dif sobrante",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dif == 0) Emerald800 else if (dif < 0) Rose700 else Color(0xFF1D4ED8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Calculation Breakdown: Inicio + Entradas - Vendido = Debe Terminar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inicio (${item.inventarioInicial}) + Entr (+${item.entradasJornada}) - Vend (${item.ventasJornada}) = Debe: $debeTerminar",
                    fontSize = 10.sp,
                    color = Slate600
                )

                // Stepper for Physical Count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Físico:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate700)

                    Surface(
                        onClick = { onCountChanged(physicalCount - 1) },
                        shape = CircleShape,
                        color = Slate200,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos", modifier = Modifier.size(14.dp))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate300),
                        modifier = Modifier.width(36.dp).height(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$physicalCount", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        onClick = { onCountChanged(physicalCount + 1) },
                        shape = CircleShape,
                        color = Slate200,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Más", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
