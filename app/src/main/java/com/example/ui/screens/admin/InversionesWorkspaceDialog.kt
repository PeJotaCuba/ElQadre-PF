package com.example.ui.screens.admin

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Inversion
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.AdminJsonExportHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InversionesWorkspaceDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var inversionToEdit by remember { mutableStateOf<Inversion?>(null) }
    var inversionToDelete by remember { mutableStateOf<Inversion?>(null) }

    val totalInvested = remember(uiState.inversiones) {
        uiState.inversiones.sumOf { it.amount }
    }
    val totalDailyRecovery = remember(uiState.inversiones) {
        uiState.inversiones.sumOf { it.dailyDepreciation() }
    }
    val totalMonthlyRecovery = totalDailyRecovery * 30.0

    val filteredList = remember(uiState.inversiones, searchQuery) {
        uiState.inversiones.filter { inv ->
            inv.name.contains(searchQuery, ignoreCase = true) ||
                    inv.category.contains(searchQuery, ignoreCase = true) ||
                    inv.observation.contains(searchQuery, ignoreCase = true)
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = AdminJsonExportHelper.buildQInversionesJson(uiState)
            val success = AdminJsonExportHelper.writeJsonToUri(context, uri, json)
            if (success) {
                Toast.makeText(context, "Q_inversiones.json exportado correctamente", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Error al escribir archivo", Toast.LENGTH_SHORT).show()
            }
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
                                        text = "Inversiones",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Activos, maquinaria y recuperación de capital",
                                        fontSize = 11.sp,
                                        color = Slate300
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        inversionToEdit = null
                                        showAddDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(38.dp).testTag("btn_add_inversion")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nueva Inversión", color = ElQadreNavy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
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
                        // Total Invested
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("TOTAL INVERTIDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${"%.2f".format(totalInvested)} CUP",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreNavy
                                )
                                Text("${uiState.inversiones.size} activos registrados", fontSize = 10.sp, color = Slate400)
                            }
                        }

                        // Daily Recovery
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("RECUPERACIÓN DIARIA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${"%.2f".format(totalDailyRecovery)} CUP",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreGoldDark
                                )
                                Text("Prorrateo / día", fontSize = 10.sp, color = Slate400)
                            }
                        }

                        // Monthly Recovery
                        Surface(
                            modifier = Modifier.weight(0.9f),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("RECUP. MENSUAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${"%.2f".format(totalMonthlyRecovery)} CUP",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate700
                                )
                                Text("Base 30 días", fontSize = 10.sp, color = Slate400)
                            }
                        }
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar inversión por nombre o categoría...", color = Slate500, fontSize = 13.sp) },
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

                    // List of Investments
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
                                Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Slate400, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No hay inversiones registradas", fontWeight = FontWeight.Bold, color = Slate700, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Usa el botón '+ Nueva Inversión' para registrar maquinaria, equipos y activos.", fontSize = 12.sp, color = Slate500)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredList, key = { it.id }) { inv ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White,
                                    shadowElevation = 1.5.dp,
                                    border = BorderStroke(1.dp, ElQadreBorderLight)
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
                                                    text = inv.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = ElQadreNavy
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = ElQadreGoldSoft
                                                ) {
                                                    Text(
                                                        text = inv.category,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = ElQadreGoldDark,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(
                                                    text = "Inversión: $${"%.2f".format(inv.amount)} CUP",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Slate700
                                                )
                                                Text(
                                                    text = "• Plazo: ${inv.usefulLife} ${inv.usefulLifeUnit.lowercase()}",
                                                    fontSize = 12.sp,
                                                    color = Slate600
                                                )
                                                Text(
                                                    text = "• Recuperación: $${"%.2f".format(inv.dailyDepreciation())} CUP/día",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElQadreNavy
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    inversionToEdit = inv
                                                    showAddDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                            }

                                            IconButton(
                                                onClick = { inversionToDelete = inv },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(18.dp))
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
    }

    // Add / Edit Dialog
    if (showAddDialog) {
        AddEditInversionDialog(
            inversion = inversionToEdit,
            products = uiState.products,
            generalConfig = uiState.generalConfig,
            onDismiss = {
                showAddDialog = false
                inversionToEdit = null
            },
            onConfirm = { savedInversion ->
                if (inversionToEdit == null) {
                    viewModel.insertInversion(savedInversion)
                } else {
                    viewModel.updateInversion(savedInversion)
                }
                showAddDialog = false
                inversionToEdit = null
            }
        )
    }

    // Delete Confirmation
    inversionToDelete?.let { inv ->
        AlertDialog(
            onDismissRequest = { inversionToDelete = null },
            title = { Text("Eliminar Inversión", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = { Text("¿Deseas eliminar '${inv.name}'? Se eliminará del registro y del prorrateo diario de inversiones.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInversion(inv)
                        inversionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("Eliminar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { inversionToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
