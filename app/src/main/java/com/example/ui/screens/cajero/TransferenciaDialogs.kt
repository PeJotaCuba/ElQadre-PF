package com.example.ui.screens.cajero

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.model.TableOrder
import com.example.data.local.model.Transferencia
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.ParsedTransferSms
import com.example.util.TransferenciasPdfExporter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TransferenciaAlertDialog(
    parsed: ParsedTransferSms,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var titularName by remember { mutableStateOf("") }
    var titularCi by remember { mutableStateOf("") }
    var titularPhone by remember(parsed.phoneNumber) { mutableStateOf(parsed.phoneNumber) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp)
                .testTag("dialog_transferencia_recibida")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalance,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TRANSFERENCIA RECIBIDA",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Detección automática por SMS (Transfermóvil / ENZONA)",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Transfer Summary Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MONTO RECIBIDO:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "$${"%.2f".format(parsed.amount)} ${parsed.currency}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Emerald600,
                                modifier = Modifier.testTag("transfer_alert_amount")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nro. Transacción:", fontSize = 11.sp, color = Slate600)
                            Text(
                                text = parsed.transactionNumber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                modifier = Modifier.testTag("transfer_alert_tx")
                            )
                        }
                        if (parsed.recipientAccount.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cuenta Receptora:", fontSize = 11.sp, color = Slate600)
                                Text(parsed.recipientAccount, fontSize = 11.sp, color = Slate700)
                            }
                        }
                        val displayTimestamp = if (parsed.timestampMillis > 0L) parsed.timestampMillis else System.currentTimeMillis()
                        val dateFormatted = if (parsed.dateStr.isNotBlank()) parsed.dateStr else java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(displayTimestamp))
                        val timeFormatted = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(displayTimestamp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fecha y Hora SMS:", fontSize = 11.sp, color = Slate600)
                            Text("$dateFormatted • $timeFormatted", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                        }
                    }
                }

                // Manual Extra Data Input (Titular, CI, Phone)
                    Text(
                        text = "DATOS DEL TITULAR (OPCIONAL)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Slate500
                    )

                    OutlinedTextField(
                        value = titularName,
                        onValueChange = { titularName = it },
                        label = { Text("Nombre del Titular") },
                        placeholder = { Text("ej. Juan Pérez") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_transfer_titular_name")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = titularCi,
                            onValueChange = { titularCi = it },
                            label = { Text("CI Titular") },
                            placeholder = { Text("11 dígitos") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_transfer_titular_ci")
                        )

                        OutlinedTextField(
                            value = titularPhone,
                            onValueChange = { titularPhone = it },
                            label = { Text("Teléfono (10 dígitos)") },
                            placeholder = { Text("535XXXXXXX") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_transfer_titular_phone")
                        )
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }

                    // Action Button: Save Transfer
                    Button(
                        onClick = {
                            viewModel.saveTransferenciaSinAsociar(
                                parsed = parsed,
                                titularName = titularName,
                                titularCi = titularCi,
                                phoneNumber = titularPhone
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_guardar_transferencia"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "GUARDAR TRANSFERENCIA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
            }
        }
    }
}

@Composable
fun AgregarTransferenciaExternaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDateCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var foundSmsList by remember { mutableStateOf<List<com.example.util.SearchedPagoXMovilSms>?>(null) }
    var selectedSms by remember { mutableStateOf<Set<String>>(emptySet()) }
    val dateDisplayFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    val isSelectedDateToday = remember(selectedDateCalendar) {
        val today = Calendar.getInstance()
        today.get(Calendar.YEAR) == selectedDateCalendar.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == selectedDateCalendar.get(Calendar.DAY_OF_YEAR)
    }

    fun performSearch(targetCal: Calendar) {
        val existingTxs = uiState.allTransferencias.map { it.transactionNumber }.toSet()
        val list = com.example.util.SmsSearchHelper.searchPagoXMovilByDate(context, targetCal, existingTxs)
        foundSmsList = list
        selectedSms = emptySet()
        if (list.isEmpty()) {
            errorMessage = "No se encontraron pagos por SMS de Transfermóvil o ENZONA para el ${dateDisplayFormat.format(targetCal.time)}."
        } else {
            errorMessage = null
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            performSearch(selectedDateCalendar)
        } else {
            errorMessage = "Permiso de lectura de SMS no concedido."
        }
    }

    val datePickerDialog = remember(selectedDateCalendar) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                selectedDateCalendar = newCal
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    performSearch(newCal)
                } else {
                    launcher.launch(android.Manifest.permission.READ_SMS)
                }
            },
            selectedDateCalendar.get(Calendar.YEAR),
            selectedDateCalendar.get(Calendar.MONTH),
            selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .padding(vertical = 12.dp)
                .testTag("dialog_agregar_transferencia_externa")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BUSCAR PAGOS POR SMS",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Consulta automática de mensajes (Transfermóvil / ENZONA)",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Date filter selector bar (FECHA A CONSULTAR y CAMBIAR FECHA)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE0F2FE)
                            ) {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "FECHA A CONSULTAR",
                                    fontSize = 10.5.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = dateDisplayFormat.format(selectedDateCalendar.time),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = ElQadreNavy
                                    )
                                    if (isSelectedDateToday) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Emerald100
                                        ) {
                                            Text(
                                                text = "HOY",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Emerald700,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = { datePickerDialog.show() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CAMBIAR FECHA", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        }
                    }
                }

                // Botón principal grande y claramente identificable: BUSCAR PAGO
                Button(
                    onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            performSearch(selectedDateCalendar)
                        } else {
                            launcher.launch(android.Manifest.permission.READ_SMS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_buscar_pago_sms"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BUSCAR PAGO", fontWeight = FontWeight.Black, fontSize = 14.5.sp)
                }

                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (foundSmsList != null && foundSmsList!!.isNotEmpty()) {
                    val availableCount = foundSmsList!!.count { !it.isAlreadyRegistered }
                    val registeredCount = foundSmsList!!.count { it.isAlreadyRegistered }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PAGOS ENCONTRADOS (${foundSmsList!!.size})",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "$availableCount nuevos • $registeredCount ya registrados",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }

                        if (availableCount > 0) {
                            TextButton(
                                onClick = {
                                    val availableTransactions = foundSmsList!!.filter { !it.isAlreadyRegistered }.map { it.parsed.transactionNumber }.toSet()
                                    if (selectedSms.size == availableTransactions.size) {
                                        selectedSms = emptySet()
                                    } else {
                                        selectedSms = availableTransactions
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = ElQadreNavy),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("btn_select_all_transferencias")
                            ) {
                                val allSelected = selectedSms.size == availableCount && availableCount > 0
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (allSelected) "DESMARCAR TODO" else "SELECCIONAR TODO",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(foundSmsList!!) { item ->
                            val tx = item.parsed
                            val isRegistered = item.isAlreadyRegistered
                            val isSelected = selectedSms.contains(tx.transactionNumber)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = when {
                                    isRegistered -> Slate100
                                    isSelected -> Color(0xFFECFDF5)
                                    else -> Color.White
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isRegistered -> Slate200
                                        isSelected -> Emerald600
                                        else -> Slate200
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isRegistered) {
                                        selectedSms = if (isSelected) {
                                            selectedSms - tx.transactionNumber
                                        } else {
                                            selectedSms + tx.transactionNumber
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isRegistered) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate200,
                                            modifier = Modifier.padding(end = 4.dp)
                                        ) {
                                            Text(
                                                text = "REGISTRADA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Slate600,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        androidx.compose.material3.Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = {
                                                selectedSms = if (it) {
                                                    selectedSms + tx.transactionNumber
                                                } else {
                                                    selectedSms - tx.transactionNumber
                                                }
                                            }
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Trans: ${tx.transactionNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isRegistered) Slate600 else ElQadreNavy
                                        )
                                        val displayDate = if (tx.dateStr.isNotBlank()) tx.dateStr else dateDisplayFormat.format(java.util.Date(item.smsDateMillis))
                                        val displayTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(item.smsDateMillis))
                                        Text(
                                            text = "Fecha: $displayDate | Hora: $displayTime",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate600
                                        )
                                        if (tx.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = "Tel: ${tx.phoneNumber}",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = if (isRegistered) Slate500 else Emerald600
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSms.isNotEmpty()) {
                        Button(
                            onClick = {
                                val toRegister = foundSmsList!!
                                    .filter { !it.isAlreadyRegistered && selectedSms.contains(it.parsed.transactionNumber) }
                                var count = 0
                                toRegister.forEach { item ->
                                    val parsed = item.parsed
                                    val finalDateStr = if (parsed.dateStr.isNotBlank()) parsed.dateStr else dateDisplayFormat.format(java.util.Date(item.smsDateMillis))
                                    viewModel.registrarTransferenciaManual(
                                        parsed = parsed.copy(dateStr = finalDateStr, timestampMillis = item.smsDateMillis),
                                        smsTimestampMillis = item.smsDateMillis,
                                        onSuccess = {
                                            count++
                                            if (count == toRegister.size) {
                                                Toast.makeText(context, "$count transferencias registradas.", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        },
                                        onError = { err -> errorMessage = err }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_registrar_transferencias_seleccionadas"),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REGISTRAR SELECCIONADOS (${selectedSms.size})",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun InformeTransferenciasDialog(
    filterLabel: String,
    transfers: List<Transferencia>,
    cajeroUsername: String,
    ownerPhone: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalAmount = remember(transfers) { transfers.sumOf { it.amount } }
    val countTotal = remember(transfers) { transfers.size }
    val countSms = remember(transfers) { transfers.count { !it.isManual } }
    val countManual = remember(transfers) { transfers.count { it.isManual } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .fillMaxHeight(0.82f)
                .testTag("dialog_informe_transferencias")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF0F9FF)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Analytics,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "INFORME DE TRANSFERENCIAS",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Filtro: $filterLabel",
                                fontSize = 11.5.sp,
                                color = Color(0xFF0369A1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Contenido central adaptable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Resumen Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("MONTO TOTAL RECIBIDO", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Slate500)
                                    Text(
                                        text = "$${"%.2f".format(totalAmount)} CUP",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200)
                                ) {
                                    Text(
                                        text = "$countTotal transferencias",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Detectadas por SMS", fontSize = 9.5.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                                        Text("$countSms", fontWeight = FontWeight.Black, fontSize = 12.5.sp, color = Color(0xFF0284C7))
                                    }
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.weight(1f)) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Entrada Manual/Ext.", fontSize = 9.5.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.SemiBold)
                                        Text("$countManual", fontWeight = FontWeight.Black, fontSize = 12.5.sp, color = Color(0xFF7C3AED))
                                    }
                                }
                            }
                        }
                    }

                    // List preview
                    Text(
                        text = "RESUMEN DE TRANSFERENCIAS (${transfers.size})",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    if (transfers.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate50,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No hay transferencias registradas para el filtro '$filterLabel'.",
                                    fontSize = 12.5.sp,
                                    color = Slate500,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(transfers) { tx ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = tx.transactionNumber,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.5.sp,
                                                    color = ElQadreNavy
                                                )
                                                val tagBg = if (tx.isManual) Color(0xFFF3E8FF) else Color(0xFFE0F2FE)
                                                val tagColor = if (tx.isManual) Color(0xFF7C3AED) else Color(0xFF0284C7)
                                                Surface(shape = RoundedCornerShape(4.dp), color = tagBg) {
                                                    Text(
                                                        text = if (tx.isManual) "MANUAL" else "SMS",
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = tagColor,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            val dateStr = if (tx.smsDate.isNotBlank()) tx.smsDate else SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(tx.receivedAt))
                                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(tx.receivedAt))
                                            Text(
                                                text = "Fecha: $dateStr | Hora: $timeStr",
                                                fontSize = 10.5.sp,
                                                color = Slate500
                                            )
                                        }
                                        Text(
                                            text = "$${"%.2f".format(tx.amount)} CUP",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.5.sp,
                                            color = ElQadreNavy
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // Botón "ENVIAR INFORME" - Completamente visible y accesible
                Button(
                    onClick = {
                        val cleanPhone = ownerPhone.replace("+", "").replace(" ", "").replace("-", "").trim()
                        if (cleanPhone.isBlank()) {
                            Toast.makeText(
                                context,
                                "No existe destinatario configurado. El Administrador debe registrar el número de teléfono del dueño en Ajustes.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }

                        val file = TransferenciasPdfExporter.exportTransferenciasReport(
                            context = context,
                            transferencias = transfers,
                            filterLabel = filterLabel,
                            generatedBy = cajeroUsername
                        )
                        if (file != null) {
                            TransferenciasPdfExporter.shareTransferenciasReport(
                                context = context,
                                file = file,
                                filterLabel = filterLabel,
                                ownerPhone = ownerPhone
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_enviar_informe_transferencias"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENVIAR INFORME",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
fun DetalleTransferenciaDialog(
    transferencia: Transferencia,
    onSave: (nombreRemitente: String, ciRemitente: String, telefonoRemitente: String) -> Unit,
    onDismiss: () -> Unit
) {
    val dateOnlyFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateStr = if (transferencia.smsDate.isNotBlank()) transferencia.smsDate else dateOnlyFormatter.format(Date(transferencia.receivedAt))
    val timeStr = timeFormatter.format(Date(transferencia.receivedAt))

    var nombreRemitente by remember(transferencia.id) { mutableStateOf(transferencia.titularName) }
    var ciRemitente by remember(transferencia.id) { mutableStateOf(transferencia.titularCi) }
    var telefonoRemitente by remember(transferencia.id) { mutableStateOf(transferencia.phoneNumber) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .testTag("dialog_detalle_transferencia")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE0F2FE)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "DETALLE DE TRANSFERENCIA",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Consulta y edición de datos del remitente",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Scrollable content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Amount Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.5.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "MONTO DE LA OPERACIÓN",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = "$${"%.2f".format(transferencia.amount)} ${transferencia.currency}",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Emerald600
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (transferencia.isManual) Color(0xFFF3E8FF) else Color(0xFFE0F2FE)
                                    ) {
                                        Text(
                                            text = if (transferencia.isManual) "MANUAL / EXTERNA" else "SMS AUTOMÁTICO",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (transferencia.isManual) Color(0xFF7E22CE) else Color(0xFF0369A1),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (transferencia.status.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Slate100
                                        ) {
                                            Text(
                                                text = transferencia.status,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate600,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Datos Principales (No editables)
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DATOS DE LA TRANSACCIÓN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Nro. Transacción:", fontSize = 12.sp, color = Slate600)
                                    Text(
                                        text = transferencia.transactionNumber,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ElQadreNavy
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Fecha:", fontSize = 12.sp, color = Slate600)
                                    Text(dateStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hora:", fontSize = 12.sp, color = Slate600)
                                    Text(timeStr, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                }

                                if (transferencia.recipientAccount.isNotBlank()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Cuenta Receptora:", fontSize = 12.sp, color = Slate600)
                                        Text(
                                            text = transferencia.recipientAccount,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate800
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Datos del Remitente (Editables / Completables)
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "DATOS DEL REMITENTE (EDITABLE)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                }

                                OutlinedTextField(
                                    value = nombreRemitente,
                                    onValueChange = { nombreRemitente = it },
                                    label = { Text("Nombre del Remitente", fontSize = 12.sp) },
                                    placeholder = { Text("Ej: Juan Pérez González", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_nombre_remitente")
                                )

                                OutlinedTextField(
                                    value = ciRemitente,
                                    onValueChange = { ciRemitente = it },
                                    label = { Text("Carné de Identidad (CI)", fontSize = 12.sp) },
                                    placeholder = { Text("Ej: 95081234567", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_ci_remitente")
                                )

                                OutlinedTextField(
                                    value = telefonoRemitente,
                                    onValueChange = { telefonoRemitente = it },
                                    label = { Text("Número de Teléfono", fontSize = 12.sp) },
                                    placeholder = { Text("Ej: 5351234567", fontSize = 12.sp) },
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_telefono_remitente")
                                )
                            }
                        }
                    }

                    // Mensaje SMS original (si existe)
                    if (transferencia.rawSmsBody.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "TEXTO ORIGINAL DEL SMS",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate500
                                    )
                                    Text(
                                        text = transferencia.rawSmsBody,
                                        fontSize = 11.5.sp,
                                        color = Slate700,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // Botones de acción inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancelar_detalle_transferencia"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Text(
                            text = "CERRAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Slate700
                        )
                    }

                    Button(
                        onClick = {
                            onSave(nombreRemitente, ciRemitente, telefonoRemitente)
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_guardar_detalle_transferencia"),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GUARDAR",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmarScanDialog(
    onScan: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("dialog_confirmar_scan_transferencias")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Icon Header
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFE0F2FE),
                    border = BorderStroke(1.5.dp, Color(0xFFBAE6FD))
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier
                            .padding(16.dp)
                            .size(42.dp)
                    )
                }

                // Title
                Text(
                    text = "RECOMENDACIÓN DE ESCANEO",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                // Description Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Comprobación previa al cierre",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color(0xFF0369A1)
                        )
                        Text(
                            text = "Se recomienda realizar un NUEVO ESCANEO para comprobar que no existen transferencias nuevas pendientes de registrar.",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Button ESCANEAR
                    Button(
                        onClick = onScan,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("btn_escanear_confirmacion")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ESCANEAR",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.5.sp,
                            color = Color.White
                        )
                    }

                    // Button CONTINUAR
                    Button(
                        onClick = onContinue,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("btn_continuar_confirmacion")
                    ) {
                        Text(
                            text = "CONTINUAR",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = ElQadreGold
                        )
                    }
                }
            }
        }
    }
}

data class MercaderiaProductOption(
    val mercaderiaId: Long,
    val productId: Long,
    val productName: String,
    val precioVenta: Double,
    val unitOfMeasure: String
)

data class MercaderiaTransferenciaSeleccionada(
    val mercaderiaId: Long,
    val productId: Long,
    val productName: String,
    val precioVenta: Double,
    val cantidad: Int
) {
    val total: Double get() = precioVenta * cantidad
}

@Composable
fun ClasificacionTransferenciasDialog(
    totalTransferenciasConfirmadas: Double,
    availableMercaderias: List<MercaderiaProductOption>,
    initialProduccionMonto: Double = totalTransferenciasConfirmadas,
    initialMercaderiasMonto: Double = 0.0,
    onAceptar: (transferenciasProduccion: Double, transferenciasMercaderias: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var esTodasProduccion by remember { mutableStateOf(initialMercaderiasMonto <= 0.0) }

    val selectedMercaderiasItems = remember { mutableStateListOf<MercaderiaTransferenciaSeleccionada>() }

    var selectedProductToAdding by remember(availableMercaderias) { mutableStateOf(availableMercaderias.firstOrNull()) }
    var quantityToAddStr by remember { mutableStateOf("1") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val totalMercaderias = if (esTodasProduccion) 0.0 else selectedMercaderiasItems.sumOf { it.total }
    val totalProduccion = if (esTodasProduccion) totalTransferenciasConfirmadas else (totalTransferenciasConfirmadas - totalMercaderias).coerceAtLeast(0.0)
    val exceedsLimit = !esTodasProduccion && (totalMercaderias > totalTransferenciasConfirmadas + 0.01)

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("dialog_clasificacion_transferencias")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CLASIFICACIÓN DE TRANSFERENCIAS",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Asignación entre Producción y Mercaderías",
                                fontSize = 11.5.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Total Confirmed Transfers Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.5.dp, Color(0xFFBAE6FD)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL CONFIRMADO DE TRANSFERENCIAS:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0369A1)
                        )
                        Text(
                            text = "$${"%.2f".format(totalTransferenciasConfirmadas)} CUP",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFF0284C7)
                        )
                    }
                }

                // Scrollable Content Form
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // QUESTION CARD
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "¿TODAS LAS TRANSFERENCIAS CORRESPONDEN A PRODUCCIÓN?",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp,
                                    color = ElQadreNavy
                                )

                                // Option 1: SÍ, TODAS SON DE PRODUCCIÓN
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (esTodasProduccion) Color(0xFFF0FDF4) else Color.White,
                                    border = BorderStroke(
                                        if (esTodasProduccion) 2.dp else 1.dp,
                                        if (esTodasProduccion) Emerald600 else Slate200
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { esTodasProduccion = true }
                                        .testTag("option_todas_produccion")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = esTodasProduccion,
                                            onClick = { esTodasProduccion = true },
                                            colors = RadioButtonDefaults.colors(selectedColor = Emerald600)
                                        )
                                        Column {
                                            Text(
                                                text = "SÍ, TODAS SON DE PRODUCCIÓN",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = if (esTodasProduccion) Color(0xFF166534) else Slate800
                                            )
                                            Text(
                                                text = "El 100% del importe ($${"%.2f".format(totalTransferenciasConfirmadas)} CUP) se asignará a Producción.",
                                                fontSize = 11.5.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }

                                // Option 2: NO, HAY TRANSFERENCIAS DE MERCADERÍAS
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (!esTodasProduccion) Color(0xFFEFF6FF) else Color.White,
                                    border = BorderStroke(
                                        if (!esTodasProduccion) 2.dp else 1.dp,
                                        if (!esTodasProduccion) Color(0xFF0284C7) else Slate200
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { esTodasProduccion = false }
                                        .testTag("option_hay_mercaderias")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RadioButton(
                                            selected = !esTodasProduccion,
                                            onClick = { esTodasProduccion = false },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF0284C7))
                                        )
                                        Column {
                                            Text(
                                                text = "NO, HAY TRANSFERENCIAS DE MERCADERÍAS",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 14.sp,
                                                color = if (!esTodasProduccion) Color(0xFF0369A1) else Slate800
                                            )
                                            Text(
                                                text = "Identificar qué productos de Mercaderías fueron pagados por transferencia.",
                                                fontSize = 11.5.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // MERCADERÍAS PRODUCTS SELECTION PANEL
                    if (!esTodasProduccion) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "SELECCIONAR PRODUCTOS DE MERCADERÍAS",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )

                                    if (availableMercaderias.isEmpty()) {
                                        Text(
                                            text = "No existen productos de Mercaderías activos configurados en el sistema.",
                                            fontSize = 12.sp,
                                            color = Slate500,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    } else {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { dropdownExpanded = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, Slate300)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = selectedProductToAdding?.let { "${it.productName} ($${"%.2f".format(it.precioVenta)} CUP)" }
                                                            ?: "Seleccionar producto...",
                                                        fontSize = 13.sp,
                                                        color = if (selectedProductToAdding != null) Slate800 else Slate400,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = dropdownExpanded,
                                                onDismissRequest = { dropdownExpanded = false },
                                                modifier = Modifier.fillMaxWidth(0.85f)
                                            ) {
                                                availableMercaderias.forEach { opt ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = "${opt.productName} — $${"%.2f".format(opt.precioVenta)} CUP / ${opt.unitOfMeasure}",
                                                                fontSize = 13.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        },
                                                        onClick = {
                                                            selectedProductToAdding = opt
                                                            dropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Cant:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                                IconButton(
                                                    onClick = {
                                                        val q = (quantityToAddStr.toIntOrNull() ?: 1) - 1
                                                        if (q >= 1) quantityToAddStr = q.toString()
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Restar", tint = Slate600)
                                                }
                                                OutlinedTextField(
                                                    value = quantityToAddStr,
                                                    onValueChange = { quantityToAddStr = it.filter { c -> c.isDigit() } },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier
                                                        .width(55.dp)
                                                        .height(48.dp),
                                                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        val q = (quantityToAddStr.toIntOrNull() ?: 0) + 1
                                                        quantityToAddStr = q.toString()
                                                    },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Sumar", tint = Slate600)
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val prod = selectedProductToAdding
                                                    val q = quantityToAddStr.toIntOrNull() ?: 0
                                                    if (prod != null && q > 0) {
                                                        val existingIndex = selectedMercaderiasItems.indexOfFirst { it.mercaderiaId == prod.mercaderiaId }
                                                        if (existingIndex >= 0) {
                                                            val curr = selectedMercaderiasItems[existingIndex]
                                                            selectedMercaderiasItems[existingIndex] = curr.copy(cantidad = curr.cantidad + q)
                                                        } else {
                                                            selectedMercaderiasItems.add(
                                                                MercaderiaTransferenciaSeleccionada(
                                                                    mercaderiaId = prod.mercaderiaId,
                                                                    productId = prod.productId,
                                                                    productName = prod.productName,
                                                                    precioVenta = prod.precioVenta,
                                                                    cantidad = q
                                                                )
                                                            )
                                                        }
                                                        quantityToAddStr = "1"
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("AGREGAR", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                            }
                                        }
                                    }

                                    if (selectedMercaderiasItems.isNotEmpty()) {
                                        HorizontalDivider(color = Slate200)
                                        Text(
                                            text = "PRODUCTOS DE MERCADERÍAS AGREGADOS:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate600
                                        )

                                        selectedMercaderiasItems.forEachIndexed { idx, item ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFFF8FAFC),
                                                border = BorderStroke(1.dp, Slate200),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.productName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = ElQadreNavy
                                                        )
                                                        Text(
                                                            text = "${item.cantidad} u × $${"%.2f".format(item.precioVenta)} = $${"%.2f".format(item.total)} CUP",
                                                            fontSize = 11.5.sp,
                                                            color = Slate600
                                                        )
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = {
                                                                if (item.cantidad > 1) {
                                                                    selectedMercaderiasItems[idx] = item.copy(cantidad = item.cantidad - 1)
                                                                } else {
                                                                    selectedMercaderiasItems.removeAt(idx)
                                                                }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Slate600, modifier = Modifier.size(16.dp))
                                                        }

                                                        Text(
                                                            text = "${item.cantidad}",
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.padding(horizontal = 6.dp)
                                                        )

                                                        IconButton(
                                                            onClick = {
                                                                selectedMercaderiasItems[idx] = item.copy(cantidad = item.cantidad + 1)
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Add, contentDescription = "Más", tint = Slate600, modifier = Modifier.size(16.dp))
                                                        }

                                                        IconButton(
                                                            onClick = { selectedMercaderiasItems.removeAt(idx) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
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

                    // TOTALS SUMMARY CARD
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, if (exceedsLimit) Color(0xFFFCA5A5) else Slate300),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DESGLOSE DE TOTALES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TRANSFERENCIAS PRODUCCIÓN:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                    Text("$${"%.2f".format(totalProduccion)} CUP", fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TRANSFERENCIAS MERCADERÍAS:", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0369A1))
                                    Text("$${"%.2f".format(totalMercaderias)} CUP", fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = Color(0xFF0369A1))
                                }

                                HorizontalDivider(color = Slate200)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("TOTAL CONFIRMADO:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Slate700)
                                    Text("$${"%.2f".format(totalTransferenciasConfirmadas)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Slate800)
                                }

                                if (exceedsLimit) {
                                    Text(
                                        text = "⚠️ El total de Mercaderías ($${"%.2f".format(totalMercaderias)} CUP) excede el total confirmado de transferencias ($${"%.2f".format(totalTransferenciasConfirmadas)} CUP).",
                                        color = Color(0xFFDC2626),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_cancelar_clasificacion_transferencias"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Slate300)
                    ) {
                        Text(
                            text = "CANCELAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Slate700
                        )
                    }

                    Button(
                        onClick = {
                            if (!exceedsLimit) {
                                onAceptar(totalProduccion, totalMercaderias)
                            }
                        },
                        enabled = !exceedsLimit,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(52.dp)
                            .testTag("btn_aceptar_clasificacion_transferencias"),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACEPTAR",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoNuevasTransferenciasDialog(
    onAceptar: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("dialog_no_nuevas_transferencias")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.5.dp, Color(0xFFBBF7D0))
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier
                            .padding(16.dp)
                            .size(44.dp)
                    )
                }

                Text(
                    text = "NO SE ENCONTRARON NUEVAS TRANSFERENCIAS",
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "El escaneo en Transfermóvil y ENZONA finalizó correctamente. No existen transferencias nuevas pendientes de registrar para la jornada actual.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }

                Button(
                    onClick = onAceptar,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("btn_aceptar_no_nuevas_transf")
                ) {
                    Text(
                        text = "ACEPTAR",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.5.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun NuevasTransferenciasEncontradasDialog(
    nuevasCount: Int,
    totalMontoNuevas: Double,
    onAceptar: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
                .testTag("dialog_nuevas_transferencias_encontradas")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.5.dp, Color(0xFFBFDBFE))
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = Color(0xFF0284C7),
                        modifier = Modifier
                            .padding(16.dp)
                            .size(44.dp)
                    )
                }

                Text(
                    text = "¡NUEVAS TRANSFERENCIAS ENCONTRADAS!",
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "NUEVAS DETECTADAS: $nuevasCount",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "IMPORTE TOTAL: $${"%.2f".format(totalMontoNuevas)} CUP",
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = Emerald600
                        )
                        Text(
                            text = "Al pulsar ACEPTAR se registrarán automáticamente en la jornada actual y pasarás al formulario de clasificación.",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = onAceptar,
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .testTag("btn_aceptar_nuevas_transf")
                ) {
                    Text(
                        text = "ACEPTAR",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.5.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
