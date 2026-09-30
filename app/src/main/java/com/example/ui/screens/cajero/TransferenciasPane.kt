package com.example.ui.screens.cajero

import android.app.DatePickerDialog
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCode2
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

fun resolveTransferenciaOrigen(tx: Transferencia): String {
    val raw = tx.rawSmsBody.uppercase()
    val src = tx.source.uppercase()
    if (raw.contains("ENZONA") || src.contains("ENZONA")) {
        return "ENZONA"
    }
    if (com.example.util.SmsTransferParser.isEnzonaTransferSms(tx.rawSmsBody)) {
        return "ENZONA"
    }
    return "Transfermóvil"
}

@Composable
fun TransferenciasPane(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    triggerScanSignal: Int = 0
) {
    val context = LocalContext.current
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val todayDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }
    val initialDateStr = remember(uiState.activeJornada) {
        val activeJ = uiState.activeJornada
        if (activeJ != null && activeJ.openedAt > 0) {
            dateOnlyFormatter.format(Date(activeJ.openedAt))
        } else {
            todayDateStr
        }
    }
    var selectedTransferDateFilter by remember { mutableStateOf(initialDateStr) }
    var showInformeDialog by remember { mutableStateOf(false) }
    var showAgregarExternaDialog by remember { mutableStateOf(false) }
    var showConfirmBorrarTodoDialog by remember { mutableStateOf(false) }
    var pendingNewTransfersToConfirm by remember { mutableStateOf<List<com.example.util.SearchedPagoXMovilSms>>(emptyList()) }
    var selectedTransferForDetail by remember { mutableStateOf<Transferencia?>(null) }

    // El escaneo para confirmar busca transferencias pendientes no registradas correspondientes al día de la jornada activa / fecha seleccionada
    fun autoSearchJornadaTransfers(showNoNewToast: Boolean = false) {
        val existingTxs = uiState.allTransferencias.map { it.transactionNumber.trim() }.toSet()
        val targetCal = Calendar.getInstance().apply {
            try {
                val d = dateOnlyFormatter.parse(selectedTransferDateFilter)
                if (d != null) time = d
            } catch (_: Exception) {
                val activeJ = uiState.activeJornada
                if (activeJ != null && activeJ.openedAt > 0L) {
                    timeInMillis = activeJ.openedAt
                }
            }
        }
        val allFound = com.example.util.SmsSearchHelper.searchPagoXMovilByDate(context, targetCal, existingTxs)

        val newOnly = allFound.filter { !it.isAlreadyRegistered && it.parsed.transactionNumber.isNotBlank() }
        if (newOnly.isNotEmpty()) {
            pendingNewTransfersToConfirm = newOnly
        } else if (showNoNewToast) {
            android.widget.Toast.makeText(
                context,
                "Escaneo completado. No se encontraron nuevas transferencias pendientes.",
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

    fun normalizeDate(str: String): String {
        if (str.isBlank()) return ""
        val clean = str.trim().replace('-', '/').replace('.', '/')
        val parts = clean.split('/')
        if (parts.size >= 3) {
            val d = parts[0].trim().toIntOrNull() ?: 0
            val m = parts[1].trim().toIntOrNull() ?: 0
            var y = parts[2].trim().toIntOrNull() ?: 0
            if (y < 100) y += 2000
            return "%02d/%02d/%04d".format(d, m, y)
        }
        return str.trim()
    }

    // Filtrado de transferencias que alimenta la pantalla inicial:
    // Muestra ÚNICAMENTE las transferencias cuya fecha real del SMS coincida con la fecha seleccionada
    val filteredTransfers = remember(uiState.allTransferencias, selectedTransferDateFilter) {
        val normTarget = normalizeDate(selectedTransferDateFilter)

        uiState.allTransferencias.filter { tx ->
            val normSmsDate = normalizeDate(tx.smsDate)
            val normRecDate = if (tx.receivedAt > 0L) normalizeDate(dateOnlyFormatter.format(Date(tx.receivedAt))) else ""

            if (normSmsDate.isNotBlank()) {
                normSmsDate == normTarget
            } else {
                normRecDate == normTarget
            }
        }
    }

    val totalTransferAmount = remember(filteredTransfers) { filteredTransfers.sumOf { it.amount } }
    val transfermovilCount = remember(filteredTransfers) { filteredTransfers.count { resolveTransferenciaOrigen(it) == "Transfermóvil" } }
    val enzonaCount = remember(filteredTransfers) { filteredTransfers.count { resolveTransferenciaOrigen(it) == "ENZONA" } }

    val isDateToday = selectedTransferDateFilter == todayDateStr
    val headerDateTitle = if (isDateToday) "TRANSFERENCIAS — HOY" else "TRANSFERENCIAS — $selectedTransferDateFilter"

    Column(
        modifier = modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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

        // SELECTOR DE FECHA (Exclusivamente HOY y ELEGIR FECHA)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Opción "HOY"
            FilterChip(
                selected = isDateToday,
                onClick = { selectedTransferDateFilter = todayDateStr },
                leadingIcon = {
                    if (isDateToday) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                label = {
                    Text(
                        text = "HOY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElQadreNavy,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = ElQadreGold
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isDateToday,
                    borderColor = if (isDateToday) ElQadreNavy else Slate300
                ),
                modifier = Modifier
                    .height(40.dp)
                    .testTag("chip_fecha_hoy")
            )

            // Opción "ELEGIR FECHA"
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (!isDateToday) Color(0xFFEFF6FF) else Color.White,
                border = BorderStroke(1.5.dp, if (!isDateToday) Color(0xFF3B82F6) else Slate300),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { datePickerDialog.show() }
                    .testTag("btn_selector_fecha_transferencias")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (!isDateToday) Color(0xFF1D4ED8) else ElQadreNavy
                        )
                        Text(
                            text = if (isDateToday) "ELEGIR FECHA" else selectedTransferDateFilter,
                            fontSize = 12.sp,
                            fontWeight = if (!isDateToday) FontWeight.ExtraBold else FontWeight.Bold,
                            color = if (!isDateToday) Color(0xFF1D4ED8) else ElQadreNavy
                        )
                    }

                    if (!isDateToday) {
                        IconButton(
                            onClick = { selectedTransferDateFilter = todayDateStr },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Volver a hoy",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF1D4ED8)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // BLOQUE DE INFORMACIÓN RESALTADA (Monto Total, Transfermóvil, ENZONA)
        // =========================================================================
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.5.dp, ElQadreNavy.copy(alpha = 0.25f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("resumen_transferencias_fecha_seleccionada")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Título de la fecha mostrada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElQadreNavy
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(16.dp)
                            )
                        }
                        Text(
                            text = headerDateTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Text(
                            text = "${filteredTransfers.size} total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                HorizontalDivider(color = Slate200)

                // Monto total resaltado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monto total:",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Text(
                        text = "$${"%,.2f".format(totalTransferAmount)} CUP",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0284C7)
                    )
                }

                // Desglose por origen real: Transfermóvil y ENZONA
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Transfermóvil Counter Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE0F2FE),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Transfermóvil:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1)
                                )
                            }
                            Text(
                                text = "$transfermovilCount",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0369A1)
                            )
                        }
                    }

                    // ENZONA Counter Card
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEDE9FE),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCode2,
                                    contentDescription = null,
                                    tint = Color(0xFF6D28D9),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ENZONA:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6D28D9)
                                )
                            }
                            Text(
                                text = "$enzonaCount",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF6D28D9)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // LISTADO DE TRANSFERENCIAS DE LA FECHA SELECCIONADA
        // =========================================================================
        if (filteredTransfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AccountBalance, null, tint = Slate300, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (uiState.allTransferencias.isEmpty()) "No se han recibido transferencias aún." else "No se encontraron transferencias para la fecha seleccionada ($selectedTransferDateFilter).",
                        color = Slate500,
                        fontSize = 12.5.sp,
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
                    top = 2.dp,
                    end = 0.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransfers) { tx ->
                    val origenReal = resolveTransferenciaOrigen(tx)
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

                                    // Identificación Real del Origen: Transfermóvil o ENZONA
                                    if (origenReal == "ENZONA") {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEDE9FE),
                                            border = BorderStroke(0.5.dp, Color(0xFFDDD6FE))
                                        ) {
                                            Text(
                                                text = "ENZONA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF6D28D9),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE0F2FE),
                                            border = BorderStroke(0.5.dp, Color(0xFFBAE6FD))
                                        ) {
                                            Text(
                                                text = "Transfermóvil",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF0369A1),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
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
                            if (origenReal == "ENZONA") {
                                Text(
                                    text = "Canal: ENZONA | Transacción: ${tx.transactionNumber}",
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF6D28D9),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Teléfono: $phoneDisplay | Cuenta: ${tx.recipientAccount.ifBlank { "No especificada" }}",
                                    fontSize = 10.5.sp,
                                    color = Slate500
                                )
                            }

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
                val txRecDate = dateOnlyFormatter.format(Date(tx.receivedAt))
                val normalizedSmsDate = tx.smsDate.replace(Regex("^(\\d)/"), "0$1/").replace(Regex("/(\\d)/"), "/0$1/")
                txRecDate == selectedTransferDateFilter ||
                        tx.smsDate == selectedTransferDateFilter ||
                        normalizedSmsDate == selectedTransferDateFilter
            }
        }

        InformeTransferenciasDialog(
            filterLabel = if (selectedTransferDateFilter == todayDateStr) "HOY ($todayDateStr)" else selectedTransferDateFilter,
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
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(pendingNewTransfersToConfirm) { item ->
                            val tx = item.parsed
                            val isEnzonaItem = tx.gateway.equals("ENZONA", ignoreCase = true) || tx.rawText.contains("ENZONA", ignoreCase = true)
                            val origenName = if (isEnzonaItem) "ENZONA" else "Transfermóvil"
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = tx.transactionNumber,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = ElQadreNavy
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (isEnzonaItem) Color(0xFFEDE9FE) else Color(0xFFE0F2FE),
                                                border = BorderStroke(0.5.dp, if (isEnzonaItem) Color(0xFFDDD6FE) else Color(0xFFBAE6FD))
                                            ) {
                                                Text(
                                                    text = origenName,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isEnzonaItem) Color(0xFF6D28D9) else Color(0xFF0369A1),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (tx.phoneNumber.isNotBlank()) "Tel: ${tx.phoneNumber}" else "Fecha: ${tx.dateStr}",
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text(
                                        text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
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
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_confirmar_nuevas_transferencias")
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
                    modifier = Modifier.fillMaxWidth().testTag("btn_posponer_nuevas_transferencias")
                ) {
                    Text("DEJAR PARA MÁS TARDE", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
