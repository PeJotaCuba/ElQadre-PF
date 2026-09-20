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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.GastoGeneral
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GastosCorrientesWorkspaceDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("TODOS") }
    var showAddDialog by remember { mutableStateOf(false) }
    var gastoToEdit by remember { mutableStateOf<GastoGeneral?>(null) }
    var gastoToDelete by remember { mutableStateOf<GastoGeneral?>(null) }

    // Overheads only (exclude investment depreciation linked expenses)
    val gastosCorrientes = remember(uiState.gastosGenerales) {
        uiState.gastosGenerales.filter { it.inversionId == null }
    }

    val totalDaily = remember(gastosCorrientes) {
        gastosCorrientes.filter { it.isActive }.sumOf { it.dailyCost() }
    }
    val totalMonthly = totalDaily * 30.0
    val activeCount = remember(gastosCorrientes) { gastosCorrientes.count { it.isActive } }

    val filteredList = remember(gastosCorrientes, searchQuery, selectedCategoryFilter) {
        gastosCorrientes.filter { g ->
            val matchesSearch = g.name.contains(searchQuery, ignoreCase = true) ||
                    g.description.contains(searchQuery, ignoreCase = true) ||
                    g.category.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "TODOS" || g.category == selectedCategoryFilter
            matchesSearch && matchesCategory
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ElQadreBackground
        ) {
            Scaffold(
                containerColor = ElQadreBackground,
                topBar = {
                    Surface(
                        color = ElQadreNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Gastos Corrientes",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Costos operativos prorrateados diariamente",
                                        fontSize = 11.sp,
                                        color = Slate300
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    gastoToEdit = null
                                    showAddDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(38.dp).testTag("btn_add_gasto_corriente")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nuevo Gasto", color = ElQadreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary KPIs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Daily
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("GASTO DIARIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${"%.2f".format(totalDaily)} CUP",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreNavy
                                )
                                Text("Prorrateo / día", fontSize = 10.sp, color = Slate400)
                            }
                        }

                        // Total Monthly
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("PROYECCIÓN MENSUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${"%.2f".format(totalMonthly)} CUP",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreGoldDark
                                )
                                Text("Base 30 días", fontSize = 10.sp, color = Slate400)
                            }
                        }

                        // Active items
                        Surface(
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ACTIVOS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$activeCount / ${gastosCorrientes.size}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate700
                                )
                                Text("En cálculo", fontSize = 10.sp, color = Slate400)
                            }
                        }
                    }

                    // Search and Filter Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar gasto por nombre o categoría...", color = Slate500, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate600) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    // List of Expenses
                    if (filteredList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = Slate400, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No hay gastos corrientes registrados", fontWeight = FontWeight.Bold, color = Slate700, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Usa el botón '+ Nuevo Gasto' para registrar gastos diarios, semanales, mensuales o anuales.", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList, key = { it.id }) { gasto ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 1.5.dp,
                                    border = BorderStroke(1.dp, if (gasto.isActive) ElQadreBorderLight else Slate200)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = gasto.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = if (gasto.isActive) ElQadreNavy else Slate500
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (gasto.isActive) ElQadreGoldSoft else Slate200
                                                ) {
                                                    Text(
                                                        text = gasto.category,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (gasto.isActive) ElQadreGoldDark else Slate600,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    text = "Monto: $${"%.2f".format(gasto.amount)} CUP / ${gasto.period.lowercase()}",
                                                    fontSize = 12.sp,
                                                    color = Slate600
                                                )
                                                Text(
                                                    text = "• Diario: $${"%.2f".format(gasto.dailyCost())} CUP/día",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (gasto.isActive) ElQadreNavy else Slate400
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    gastoToEdit = gasto
                                                    showAddDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = { gastoToDelete = gasto },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(18.dp))
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            Switch(
                                                checked = gasto.isActive,
                                                onCheckedChange = { viewModel.updateGastoGeneral(gasto.copy(isActive = !gasto.isActive)) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = ElQadreGold,
                                                    checkedTrackColor = ElQadreNavy
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog) {
        AddEditGastoGeneralDialog(
            gasto = gastoToEdit,
            products = uiState.products,
            onDismiss = {
                showAddDialog = false
                gastoToEdit = null
            },
            onConfirm = { savedGasto ->
                if (gastoToEdit == null) {
                    viewModel.insertGastoGeneral(savedGasto)
                } else {
                    viewModel.updateGastoGeneral(savedGasto)
                }
                showAddDialog = false
                gastoToEdit = null
            }
        )
    }

    // Delete Confirmation
    gastoToDelete?.let { gasto ->
        AlertDialog(
            onDismissRequest = { gastoToDelete = null },
            title = { Text("Eliminar Gasto Corriente", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = { Text("¿Deseas eliminar '${gasto.name}'? Dejará de formar parte del prorrateo diario.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGastoGeneral(gasto)
                        gastoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { gastoToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
