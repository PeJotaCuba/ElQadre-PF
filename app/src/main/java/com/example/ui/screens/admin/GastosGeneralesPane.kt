package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.GastoGeneral
import com.example.data.local.model.Inversion
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CostCalculationHelper

@Composable
fun GastosGeneralesPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onAddGastoClick: () -> Unit,
    onEditGastoClick: (GastoGeneral) -> Unit,
    onToggleGastoActive: (GastoGeneral) -> Unit
) {
    var currentSubTab by remember { mutableStateOf(0) } // 0 = Gastos Generales, 1 = Inversiones
    var showAddInversionDialog by remember { mutableStateOf(false) }
    var inversionToEdit by remember { mutableStateOf<Inversion?>(null) }
    var gastoToDelete by remember { mutableStateOf<GastoGeneral?>(null) }
    var inversionToDelete by remember { mutableStateOf<Inversion?>(null) }

    val totalDaily = remember(uiState.gastosGenerales) {
        CostCalculationHelper.calculateTotalDailyOverheads(uiState.gastosGenerales)
    }
    val totalMonthly = totalDaily * 30.0
    val activeCount = uiState.gastosGenerales.count { it.isActive && it.scope == "PRODUCCION" }
    val totalCount = uiState.gastosGenerales.count { it.scope == "PRODUCCION" }

    val totalInversionAmount = remember(uiState.inversiones) {
        uiState.inversiones.sumOf { it.amount }
    }
    val totalInversionMonthlyDep = remember(uiState.inversiones) {
        uiState.inversiones.sumOf { it.monthlyDepreciation() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // HEADER ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = "Egresos",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 15.sp
                )
                Text(
                    text = "Costos indirectos y activos con depreciación para la Ficha de Costo",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp
                )
            }
            Button(
                onClick = {
                    if (currentSubTab == 0) {
                        onAddGastoClick()
                    } else {
                        inversionToEdit = null
                        showAddInversionDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag(if (currentSubTab == 0) "add_gasto_general_button" else "add_inversion_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (currentSubTab == 0) "Nuevo Gasto" else "Inversión",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }

        // SUB-TABS SELECTOR FOR GASTOS VS INVERSIONES
        TabRow(
            selectedTabIndex = currentSubTab,
            containerColor = Color.Transparent,
            contentColor = ElQadreNavy,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[currentSubTab]),
                    color = ElQadreGold
                )
            }
        ) {
            Tab(
                selected = currentSubTab == 0,
                onClick = { currentSubTab = 0 },
                text = { Text("Gastos Generales", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
            Tab(
                selected = currentSubTab == 1,
                onClick = { currentSubTab = 1 },
                text = { Text("Inversiones (Activos)", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            )
        }

        // METRICS DASHBOARD
        if (currentSubTab == 0) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ElQadreBorderLight),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("TOTAL GASTOS DIARIOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "$${"%.2f".format(totalDaily)} CUP",
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy,
                            fontSize = 16.sp
                        )
                        Text("Base de prorrateo cocina", fontSize = 10.sp, color = Slate400)
                    }

                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("EQUIVALENTE MENSUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "$${"%.2f".format(totalMonthly)} CUP",
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGoldDark,
                            fontSize = 15.sp
                        )
                        Text("Proyección a 30 días", fontSize = 10.sp, color = Slate400)
                    }

                    Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                        Text("GASTOS ACTIVOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "$activeCount de $totalCount",
                            fontWeight = FontWeight.Bold,
                            color = if (activeCount > 0) Emerald600 else Slate500,
                            fontSize = 15.sp
                        )
                        Text("En cálculo", fontSize = 10.sp, color = Slate400)
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, ElQadreBorderLight),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("TOTAL ACTIVOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "$${"%.2f".format(totalInversionAmount)} CUP",
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy,
                            fontSize = 16.sp
                        )
                        Text("Valor de adquisición", fontSize = 10.sp, color = Slate400)
                    }

                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("DEPRECIACIÓN MENSUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "$${"%.2f".format(totalInversionMonthlyDep)} CUP",
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGoldDark,
                            fontSize = 15.sp
                        )
                        Text("Como gasto indirecto", fontSize = 10.sp, color = Slate400)
                    }

                    Column(modifier = Modifier.weight(0.9f), horizontalAlignment = Alignment.End) {
                        Text("REGISTROS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            "${uiState.inversiones.size}",
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            fontSize = 15.sp
                        )
                        Text("Inversiones activas", fontSize = 10.sp, color = Slate400)
                    }
                }
            }
        }

        // CONTENT SECTION based on current sub-tab
        if (currentSubTab == 0) {
            // EXPENSE LIST
            if (uiState.gastosGenerales.none { it.scope == "PRODUCCION" }) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Slate300
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay gastos generales registrados.",
                            color = Slate600,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Agregue gastos como electricidad, salarios, transporte o limpieza para calcular el costo real de producción.",
                            color = Slate400,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.gastosGenerales.filter { it.scope == "PRODUCCION" }) { gasto ->
                        val daily = gasto.dailyCost()
                        val iconVector = when {
                            gasto.inversionId != null -> Icons.Default.TrendingUp
                            gasto.category.uppercase() in listOf("ELECTRICIDAD", "ENERGÍA") -> Icons.Default.Bolt
                            gasto.category.uppercase() == "TRANSPORTE" -> Icons.Default.LocalShipping
                            gasto.category.uppercase() == "PERSONAL" -> Icons.Default.People
                            gasto.category.uppercase() == "LIMPIEZA" -> Icons.Default.CleaningServices
                            gasto.category.uppercase() == "AGUA" -> Icons.Default.WaterDrop
                            gasto.category.uppercase() == "SEGURIDAD" -> Icons.Default.Security
                            gasto.category.uppercase() == "MANTENIMIENTO" -> Icons.Default.Build
                            else -> Icons.Default.ReceiptLong
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (gasto.isActive) Color.White else Slate50
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (gasto.isActive) ElQadreGoldSoft else Slate200),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = iconVector,
                                                contentDescription = null,
                                                tint = if (gasto.isActive) ElQadreGoldDark else Slate400,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = gasto.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = if (gasto.isActive) ElQadreNavy else Slate500
                                            )
                                            Text(
                                                text = if (gasto.inversionId != null) "Inversión • Depreciación" else "${gasto.category} • ${gasto.period} (${gasto.periodDays}d)",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Switch(
                                        checked = gasto.isActive,
                                        onCheckedChange = { onToggleGastoActive(gasto) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = ElQadreGold,
                                            checkedTrackColor = ElQadreNavy
                                        ),
                                        modifier = Modifier.scale(0.85f).testTag("toggle_gasto_${gasto.id}")
                                    )
                                }

                                if (gasto.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = gasto.description,
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = ElQadreBorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("IMPORTE REGISTRADO", fontSize = 10.sp, color = Slate500)
                                        Text(
                                            text = "$${"%.2f".format(gasto.amount)} CUP",
                                            fontWeight = FontWeight.Bold,
                                            color = if (gasto.isActive) ElQadreNavy else Slate400,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("COSTO DIARIO CALCULADO", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "$${"%.2f".format(daily)} CUP / día",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (gasto.isActive) ElQadreGoldDark else Slate400,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onEditGastoClick(gasto) },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, ElQadreNavy),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { gastoToDelete = gasto },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Rose600),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // INVERSIONES LIST
            if (uiState.inversiones.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Slate300
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No hay inversiones registradas.",
                            color = Slate600,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Registre inversiones para calcular y prorratear su depreciación automáticamente en los productos.",
                            color = Slate400,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.inversiones) { inv ->
                        val monthlyDep = inv.monthlyDepreciation()
                        val dailyDep = inv.dailyDepreciation()

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(ElQadreGoldSoft),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = ElQadreGoldDark,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = inv.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "${inv.category} • Vida útil: ${inv.usefulLife} ${inv.usefulLifeUnit.lowercase()}",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }

                                if (inv.observation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = inv.observation,
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = ElQadreBorderLight)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("VALOR ADQUISICIÓN", fontSize = 10.sp, color = Slate500)
                                        Text(
                                            text = "$${"%.2f".format(inv.amount)} CUP",
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreNavy,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("DEPRECIACIÓN MENSUAL", fontSize = 10.sp, color = Slate500)
                                        Text(
                                            text = "$${"%.2f".format(monthlyDep)} CUP/mes",
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreGoldDark,
                                            fontSize = 13.sp
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("COSTO DIARIO", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "$${"%.2f".format(dailyDep)} CUP/día",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ElQadreGoldDark,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            inversionToEdit = inv
                                            showAddInversionDialog = true
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, ElQadreNavy),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { inversionToDelete = inv },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Rose600),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Add/Edit Inversion Dialog Overlay
    if (showAddInversionDialog) {
        AddEditInversionDialog(
            inversion = inversionToEdit,
            products = uiState.products,
            onDismiss = {
                showAddInversionDialog = false
                inversionToEdit = null
            },
            onConfirm = { inv ->
                if (inv.id == 0L) {
                    viewModel.insertInversion(inv)
                } else {
                    viewModel.updateInversion(inv)
                }
                showAddInversionDialog = false
                inversionToEdit = null
            }
        )
    }

    gastoToDelete?.let { gasto ->
        AlertDialog(
            onDismissRequest = { gastoToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(36.dp)) },
            title = { Text("Confirmar Eliminación", fontWeight = FontWeight.Bold, color = Slate900) },
            text = { Text("¿Está seguro de que desea eliminar el gasto \"${gasto.name}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGastoGeneral(gasto)
                        gastoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("SÍ, ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { gastoToDelete = null }) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    inversionToDelete?.let { inv ->
        AlertDialog(
            onDismissRequest = { inversionToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(36.dp)) },
            title = { Text("Confirmar Eliminación", fontWeight = FontWeight.Bold, color = Slate900) },
            text = { Text("¿Está seguro de que desea eliminar la inversión \"${inv.name}\"? Esta acción eliminará la inversión y sus depreciaciones asociadas.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInversion(inv)
                        inversionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("SÍ, ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { inversionToDelete = null }) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
