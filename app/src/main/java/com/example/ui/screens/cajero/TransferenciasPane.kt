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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
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
    modifier: Modifier = Modifier,
    triggerScanSignal: Int = 0
) {
    val context = LocalContext.current
    var selectedTransferDateFilter by remember { mutableStateOf("TODAS") } // "TODAS" or "dd/MM/yyyy"
    var showInformeDialog by remember { mutableStateOf(false) }
    var showAgregarExternaDialog by remember { mutableStateOf(false) }
    var showConfirmBorrarTodoDialog by remember { mutableStateOf(false) }
    var pendingNewTransfersToConfirm by remember { mutableStateOf<List<com.example.util.SearchedPagoXMovilSms>>(emptyList()) }
    var selectedTransferForDetail by remember { mutableStateOf<Transferencia?>(null) }

    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    fun autoSearchJornadaTransfers(showNoNewToast: Boolean = false) {
        val cal = Calendar.getInstance()
        if (uiState.activeJornada != null && uiState.activeJornada.openedAt > 0) {
            cal.timeInMillis = uiState.activeJornada.openedAt
        }
        val existingTxs = uiState.allTransferencias.map { it.transactionNumber.trim() }.toSet()
        val list = com.example.util.SmsSearchHelper.searchPagoXMovilByDate(context, cal, existingTxs)
        val seenTxs = mutableSetOf<String>()
        val newOnly = list.filter { !it.isAlreadyRegistered && it.parsed.transactionNumber.isNotBlank() && seenTxs.add(it.parsed.transactionNumber) }
        if (newOnly.isNotEmpty()) {
            pendingNewTransfersToConfirm = newOnly
        } else if (showNoNewToast) {
            android.widget.Toast.makeText(
                context,
                "Escaneo completado. No se encontraron nuevas transferencias.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val smsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            autoSearchJornadaTransfers(showNoNewToast = triggerScanSignal > 0)
        }
    }

    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            autoSearchJornadaTransfers()
        } else {
            smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS)
        }
    }

    LaunchedEffect(triggerScanSignal) {
        if (triggerScanSignal > 0) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                autoSearchJornadaTransfers(showNoNewToast = true)
            } else {
                smsPermissionLauncher.launch(android.Manifest.permission.READ_SMS)
            }
        }
    }

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

    val filteredTransfers = remember(uiState.allTransferencias, selectedTransferDateFilter) {
        uiState.allTransferencias.filter { tx ->
            if (selectedTransferDateFilter == "TODAS") {
                true
            } else {
                val txRecDate = dateOnlyFormatter.format(Date(tx.receivedAt))
                val normalizedSmsDate = tx.smsDate.replace(Regex("^(\\d)/"), "0$1/").replace(Regex("/(\\d)/"), "/0$1/")
                txRecDate == selectedTransferDateFilter ||
                        tx.smsDate == selectedTransferDateFilter ||
                        normalizedSmsDate == selectedTransferDateFilter
            }
        }
    }

    val totalTransferAmount = remember(filteredTransfers) { filteredTransfers.sumOf { it.amount } }

    val isLandscapeMode = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Botones de acción principales (AGREGAR | INFORME | BORRAR TODO)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showAgregarExternaDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_agregar_transferencia_externa")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("AGREGAR", fontSize = 12.5.sp, fontWeight = FontWeight.Black)
            }

            Button(
                onClick = { showInformeDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_generar_informe_transferencias")
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("INFORME", fontSize = 12.5.sp, fontWeight = FontWeight.Black)
            }

            Button(
                onClick = { showConfirmBorrarTodoDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1.18f)
                    .height(46.dp)
                    .testTag("btn_borrar_todas_transferencias")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BORRAR TODO", fontSize = 11.5.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }

        // SELECTOR DE FECHA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Option "TODAS"
            FilterChip(
                selected = selectedTransferDateFilter == "TODAS",
                onClick = { selectedTransferDateFilter = "TODAS" },
                label = { Text("Todas las Fechas", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.height(38.dp).testTag("chip_fecha_todas")
            )

            // Option "HOY"
            FilterChip(
                selected = selectedTransferDateFilter == todayDateStr,
                onClick = { selectedTransferDateFilter = todayDateStr },
                label = { Text("Hoy", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.height(38.dp).testTag("chip_fecha_hoy")
            )

            // Specific Date Selector Button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selectedTransferDateFilter != "TODAS" && selectedTransferDateFilter != todayDateStr) ElQadreGold.copy(alpha = 0.15f) else Color.White,
                border = BorderStroke(1.dp, if (selectedTransferDateFilter != "TODAS" && selectedTransferDateFilter != todayDateStr) ElQadreGold else Slate300),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
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
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp), tint = ElQadreNavy)
                        Text(
                            text = if (selectedTransferDateFilter == "TODAS") "Elegir Fecha..." else selectedTransferDateFilter,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }
                    if (selectedTransferDateFilter != "TODAS") {
                        IconButton(
                            onClick = { selectedTransferDateFilter = "TODAS" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar fecha", modifier = Modifier.size(14.dp), tint = Slate500)
                        }
                    }
                }
            }
        }

        if (filteredTransfers.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = Slate300, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.allTransferencias.isEmpty()) "No se han recibido transferencias aún." else "No se encontraron transferencias para la fecha seleccionada.",
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
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransfers) { tx ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTransferForDetail = tx }
                            .testTag("card_transferencia_${tx.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = tx.transactionNumber,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.5.sp,
                                        color = ElQadreNavy
                                    )

                                    if (tx.isManual) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFF3E8FF)
                                        ) {
                                            Text(
                                                text = "MANUAL",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7E22CE),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE0F2FE)
                                        ) {
                                            Text(
                                                text = "SMS",
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0369A1),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate700
                            )
                            Text(
                                text = "Teléfono: $phoneDisplay | Cuenta: ${tx.recipientAccount.ifBlank { "No especificada" }}",
                                fontSize = 10.5.sp,
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
                                    text = "Fecha: $dateStr | Hora: $timeStr",
                                    fontSize = 10.5.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Ver detalle",
                                    tint = Slate400,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInformeDialog) {
        val filteredForReport = remember(uiState.allTransferencias, selectedTransferDateFilter) {
            uiState.allTransferencias.filter { tx ->
                if (selectedTransferDateFilter == "TODAS") {
                    true
                } else {
                    val txRecDate = dateOnlyFormatter.format(Date(tx.receivedAt))
                    val normalizedSmsDate = tx.smsDate.replace(Regex("^(\\d)/"), "0$1/").replace(Regex("/(\\d)/"), "/0$1/")
                    txRecDate == selectedTransferDateFilter ||
                            tx.smsDate == selectedTransferDateFilter ||
                            normalizedSmsDate == selectedTransferDateFilter
                }
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

    if (pendingNewTransfersToConfirm.isNotEmpty()) {
        val totalNewAmount = remember(pendingNewTransfersToConfirm) {
            pendingNewTransfersToConfirm.sumOf { it.parsed.amount }
        }

        AlertDialog(
            onDismissRequest = { pendingNewTransfersToConfirm = emptyList() },
            icon = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Icon(
                        Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint = Color(0xFF15803D),
                        modifier = Modifier.padding(10.dp).size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "SE ENCONTRARON ${pendingNewTransfersToConfirm.size} TRANSFERENCIAS NUEVAS",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MONTO TOTAL DETECTADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "$${"%.2f".format(totalNewAmount)} CUP",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald600
                            )
                        }
                    }

                    Text(
                        text = "Transferencias de la jornada actual detectadas en mensajes SMS no registradas previamente. ¿Desea registrarlas?",
                        fontSize = 12.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pendingNewTransfersToConfirm) { item ->
                            val tx = item.parsed
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = tx.transactionNumber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = if (tx.phoneNumber.isNotBlank()) "Tel: ${tx.phoneNumber}" else tx.dateStr,
                                            fontSize = 9.5.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text(
                                        text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = Emerald600
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRegister = pendingNewTransfersToConfirm
                        pendingNewTransfersToConfirm = emptyList()
                        viewModel.registrarNuevasTransferenciasDetectadas(toRegister)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REGISTRAR (${pendingNewTransfersToConfirm.size})",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingNewTransfersToConfirm = emptyList() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancelar / Más tarde", color = Slate600, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        )
    }

    if (selectedTransferForDetail != null) {
        DetalleTransferenciaDialog(
            transferencia = selectedTransferForDetail!!,
            onSave = { name, ci, phone ->
                val id = selectedTransferForDetail!!.id
                viewModel.updateTransferenciaSenderData(
                    transferenciaId = id,
                    titularName = name,
                    titularCi = ci,
                    phoneNumber = phone,
                    onSuccess = {
                        selectedTransferForDetail = null
                    }
                )
            },
            onDismiss = { selectedTransferForDetail = null }
        )
    }
}
