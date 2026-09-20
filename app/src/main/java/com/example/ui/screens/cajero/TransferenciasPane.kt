package com.example.ui.screens.cajero

import android.app.DatePickerDialog
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Transferencia
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ParsedTransferSms
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransferenciasPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTransferDateFilter by remember { mutableStateOf("TODAS") } // "TODAS" or "dd/MM/yyyy"
    var transferFilterStatus by remember { mutableStateOf("TODAS") }
    var showInformeDialog by remember { mutableStateOf(false) }
    var showAgregarExternaDialog by remember { mutableStateOf(false) }
    var showConfirmBorrarTodoDialog by remember { mutableStateOf(false) }
    var transferToAssociate by remember { mutableStateOf<Transferencia?>(null) }

    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val calendar = remember { Calendar.getInstance() }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val cal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedTransferDateFilter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val todayDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    val filteredTransfers = remember(uiState.allTransferencias, selectedTransferDateFilter, transferFilterStatus) {
        uiState.allTransferencias.filter { tx ->
            val matchesDate = if (selectedTransferDateFilter == "TODAS") {
                true
            } else {
                val txRecDate = dateOnlyFormatter.format(Date(tx.receivedAt))
                val normalizedSmsDate = tx.smsDate.replace(Regex("^(\\d)/"), "0$1/").replace(Regex("/(\\d)/"), "/0$1/")
                txRecDate == selectedTransferDateFilter ||
                        tx.smsDate == selectedTransferDateFilter ||
                        normalizedSmsDate == selectedTransferDateFilter
            }
            val matchesStatus = when (transferFilterStatus) {
                "NO_ASOCIADA" -> tx.status == "NO ASOCIADA"
                "ASOCIADA" -> tx.status == "ASOCIADA" || tx.status == "PARCIAL"
                else -> true
            }
            matchesDate && matchesStatus
        }
    }

    val totalTransferAmount = remember(filteredTransfers) { filteredTransfers.sumOf { it.amount } }
    val countAsociadas = remember(filteredTransfers) { filteredTransfers.count { it.status == "ASOCIADA" } }
    val countNoAsociadas = remember(filteredTransfers) { filteredTransfers.count { it.status == "NO ASOCIADA" } }
    val countParciales = remember(filteredTransfers) { filteredTransfers.count { it.status == "PARCIAL" } }

    val isLandscapeMode = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Stats Card & Top Actions
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF0F9FF),
            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLandscapeMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedTransferDateFilter == "TODAS") "TRANSFERENCIAS RECIBIDAS (TODAS)" else "TRANSFERENCIAS: $selectedTransferDateFilter",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "$${"%.2f".format(totalTransferAmount)} CUP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7)
                            )
                            Text("Total: ${filteredTransfers.size} | Asoc: $countAsociadas | No Asoc: $countNoAsociadas", fontSize = 10.sp, color = Slate600, fontWeight = FontWeight.Medium)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = { showAgregarExternaDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_agregar_transferencia_externa")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AGREGAR EXTERNA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showInformeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_generar_informe_transferencias")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GENERAR INFORME", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showConfirmBorrarTodoDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("btn_borrar_todas_transferencias")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BORRAR TODO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (selectedTransferDateFilter == "TODAS") "TRANSFERENCIAS RECIBIDAS (TODAS)" else "TRANSFERENCIAS: $selectedTransferDateFilter",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0369A1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$${"%.2f".format(totalTransferAmount)} CUP",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0284C7)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showAgregarExternaDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_agregar_transferencia_externa")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("AGREGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { showInformeDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_generar_informe_transferencias")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("INFORME", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { showConfirmBorrarTodoDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_borrar_todas_transferencias")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("BORRAR TODO", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total", fontSize = 9.sp, color = Slate500)
                                Text("${filteredTransfers.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElQadreNavy)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Asociadas", fontSize = 9.sp, color = Emerald600)
                                Text("$countAsociadas", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Emerald600)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No Asoc.", fontSize = 9.sp, color = Amber600)
                                Text("$countNoAsociadas", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Amber600)
                            }
                        }
                        if (countParciales > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Parciales", fontSize = 9.sp, color = Color(0xFF0284C7))
                                    Text("$countParciales", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                                }
                            }
                        }
                    }
                }
            }
        }

        // SELECTOR DE FECHA and Status Filters
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isLandscapeMode) 2.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Option "TODAS"
                FilterChip(
                    selected = selectedTransferDateFilter == "TODAS",
                    onClick = { selectedTransferDateFilter = "TODAS" },
                    label = { Text("Todas las Fechas", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("chip_fecha_todas")
                )

                // Option "HOY"
                FilterChip(
                    selected = selectedTransferDateFilter == todayDateStr,
                    onClick = { selectedTransferDateFilter = todayDateStr },
                    label = { Text("Hoy", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("chip_fecha_hoy")
                )

                // Specific Date Selector Button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedTransferDateFilter != "TODAS" && selectedTransferDateFilter != todayDateStr) ElQadreGold.copy(alpha = 0.15f) else Color.White,
                    border = BorderStroke(1.dp, if (selectedTransferDateFilter != "TODAS" && selectedTransferDateFilter != todayDateStr) ElQadreGold else Slate300),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable { datePickerDialog.show() }
                        .testTag("btn_selector_fecha_transferencias")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElQadreNavy)
                            Text(
                                text = if (selectedTransferDateFilter == "TODAS") "Elegir Fecha..." else selectedTransferDateFilter,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                        if (selectedTransferDateFilter != "TODAS") {
                            IconButton(
                                onClick = { selectedTransferDateFilter = "TODAS" },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar fecha", modifier = Modifier.size(12.dp), tint = Slate500)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Estado:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                FilterChip(
                    selected = transferFilterStatus == "TODAS",
                    onClick = { transferFilterStatus = "TODAS" },
                    label = { Text("Todas", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = transferFilterStatus == "NO_ASOCIADA",
                    onClick = { transferFilterStatus = "NO_ASOCIADA" },
                    label = { Text("Sin Asociar", fontSize = 10.sp) }
                )
                FilterChip(
                    selected = transferFilterStatus == "ASOCIADA",
                    onClick = { transferFilterStatus = "ASOCIADA" },
                    label = { Text("Asociadas", fontSize = 10.sp) }
                )
            }
        }

        if (filteredTransfers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = Slate300, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.allTransferencias.isEmpty()) "No se han recibido transferencias aún." else "No se encontraron transferencias para la fecha o filtro seleccionado.",
                        color = Slate500,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 4.dp,
                    end = 0.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransfers) { tx ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_transferencia_${tx.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = tx.transactionNumber,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = when (tx.status) {
                                            "ASOCIADA" -> Color(0xFFECFDF5)
                                            "PARCIAL" -> Color(0xFFE0F2FE)
                                            else -> Color(0xFFFEF3C7)
                                        }
                                    ) {
                                        Text(
                                            text = tx.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (tx.status) {
                                                "ASOCIADA" -> Emerald600
                                                "PARCIAL" -> Color(0xFF0284C7)
                                                else -> Color(0xFFB45309)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    if (tx.isManual) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFF3E8FF)
                                        ) {
                                            Text(
                                                text = "MANUAL",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7E22CE),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE0F2FE)
                                        ) {
                                            Text(
                                                text = "SMS",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0284C7)
                                )
                            }

                            HorizontalDivider(color = Slate100)

                            // Titular Info
                            val titularDisplay = if (tx.titularName.isNotBlank()) tx.titularName else "No especificado"
                            val phoneDisplay = if (tx.phoneNumber.isNotBlank()) tx.phoneNumber else "No informado"
                            val ciDisplay = if (tx.titularCi.isNotBlank()) " | CI: ${tx.titularCi}" else ""

                            Text(
                                text = "Titular: $titularDisplay$ciDisplay",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Text(
                                text = "Teléfono: $phoneDisplay | Cuenta: ${tx.recipientAccount}",
                                fontSize = 10.sp,
                                color = Slate500
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateStr = if (tx.smsDate.isNotBlank()) tx.smsDate else dateOnlyFormatter.format(Date(tx.receivedAt))
                                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tx.receivedAt))
                                Text(
                                    text = "Fecha: $dateStr | Hora: $timeStr | Cajero: @${tx.cajeroUsername}",
                                    fontSize = 10.sp,
                                    color = Slate400
                                )

                                if (tx.comandaNumber != null && tx.comandaNumber > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Slate100
                                    ) {
                                        Text(
                                            text = "Comanda #${tx.comandaNumber}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreNavy,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (tx.status == "NO ASOCIADA") {
                                Button(
                                    onClick = { transferToAssociate = tx },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("btn_asociar_transfer_${tx.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ASOCIAR COMANDA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInformeDialog) {
        val filteredForReport = remember(uiState.allTransferencias, selectedTransferDateFilter, transferFilterStatus) {
            uiState.allTransferencias.filter { tx ->
                val matchesDate = if (selectedTransferDateFilter == "TODAS") {
                    true
                } else {
                    val txRecDate = dateOnlyFormatter.format(Date(tx.receivedAt))
                    val normalizedSmsDate = tx.smsDate.replace(Regex("^(\\d)/"), "0$1/").replace(Regex("/(\\d)/"), "/0$1/")
                    txRecDate == selectedTransferDateFilter ||
                            tx.smsDate == selectedTransferDateFilter ||
                            normalizedSmsDate == selectedTransferDateFilter
                }
                val matchesStatus = when (transferFilterStatus) {
                    "NO_ASOCIADA" -> tx.status == "NO ASOCIADA"
                    "ASOCIADA" -> tx.status == "ASOCIADA" || tx.status == "PARCIAL"
                    else -> true
                }
                matchesDate && matchesStatus
            }
        }

        InformeTransferenciasDialog(
            filterLabel = if (selectedTransferDateFilter == "TODAS") "TODAS LAS FECHAS" else selectedTransferDateFilter,
            transfers = filteredForReport,
            cajeroUsername = uiState.currentUser?.username ?: "cajero",
            ownerPhone = uiState.generalConfig?.telefonoDueno ?: "",
            onDismiss = { showInformeDialog = false }
        )
    }

    if (showAgregarExternaDialog) {
        AgregarTransferenciaExternaDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showAgregarExternaDialog = false }
        )
    }

    if (showConfirmBorrarTodoDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmBorrarTodoDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Borrar Todas las Transferencias",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas eliminar todas las transferencias registradas? Esta acción limpiará todo el historial de transferencias y no se puede deshacer.",
                    fontSize = 13.sp,
                    color = Slate600
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.borrarTodasLasTransferencias {
                            showConfirmBorrarTodoDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BORRAR TODO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBorrarTodoDialog = false }) {
                    Text("Cancelar", color = Slate600, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    if (transferToAssociate != null) {
        val tx = transferToAssociate!!
        AsociarTransferenciaExistenteDialog(
            transferencia = tx,
            openOrders = uiState.openOrders,
            onConfirm = { orderId ->
                val parsed = ParsedTransferSms(
                    rawText = tx.rawSmsBody,
                    amount = tx.amount,
                    currency = tx.currency,
                    phoneNumber = tx.phoneNumber,
                    recipientAccount = tx.recipientAccount,
                    transactionNumber = tx.transactionNumber,
                    dateStr = tx.smsDate,
                    hasPhone = tx.phoneNumber.isNotBlank(),
                    timestampMillis = tx.receivedAt
                )
                viewModel.asociarTransferenciaAComanda(
                    parsed = parsed,
                    titularName = tx.titularName,
                    titularCi = tx.titularCi,
                    phoneNumber = tx.phoneNumber,
                    orderId = orderId
                )
                transferToAssociate = null
            },
            onDismiss = { transferToAssociate = null }
        )
    }
}
