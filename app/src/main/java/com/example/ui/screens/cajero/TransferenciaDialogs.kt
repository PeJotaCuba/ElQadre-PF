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
    var isAssociatingWithOrder by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<TableOrder?>(null) }

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
                                text = "Detección automática por SMS (PAGOxMOVIL)",
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

                if (!isAssociatingWithOrder) {
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
                } else {
                    // Select Open Order Step
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SELECCIONAR COMANDA ABIERTA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ElQadreNavy
                        )
                        TextButton(
                            onClick = { isAssociatingWithOrder = false },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Atrás", fontSize = 12.sp)
                        }
                    }

                    if (uiState.openOrders.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate100,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No hay comandas abiertas en este momento.\nPuede guardar la transferencia como NO ASOCIADA.",
                                modifier = Modifier.padding(14.dp),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uiState.openOrders) { order ->
                                val isSelected = selectedOrder?.id == order.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) Color(0xFFE0F2FE) else Slate50,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF0284C7) else Slate200
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedOrder = order }
                                        .testTag("order_item_${order.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            val tableStr = if ((order.tableNumber ?: 0) > 0) "Mesa ${order.tableNumber}" else "PARA LLEVAR"
                                            Text(
                                                text = "Comanda #${order.comandaNumber} ($tableStr)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = ElQadreNavy
                                            )
                                            if (order.customerName.isNotBlank()) {
                                                Text(
                                                    text = "Cliente: ${order.customerName}",
                                                    fontSize = 11.sp,
                                                    color = Slate500
                                                )
                                            }
                                        }
                                        Text(
                                            text = "$${"%.2f".format(order.totalAmount)} CUP",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            color = ElQadreNavy
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedOrder != null) {
                        val order = selectedOrder!!
                        val isFullyCovered = parsed.amount >= order.totalAmount
                        val remaining = if (isFullyCovered) 0.0 else order.totalAmount - parsed.amount

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isFullyCovered) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                            border = BorderStroke(
                                1.dp,
                                if (isFullyCovered) Emerald500 else Amber500
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total Comanda #${order.comandaNumber}:", fontSize = 12.sp, color = Slate700)
                                    Text("$${"%.2f".format(order.totalAmount)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Transferencia Aplicada:", fontSize = 12.sp, color = Slate700)
                                    Text("$${"%.2f".format(parsed.amount)} CUP", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                                }
                                HorizontalDivider(color = Slate300)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isFullyCovered) "Estado al Confirmar:" else "Saldo Pendiente en Efectivo:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isFullyCovered) Emerald600 else Color(0xFFB45309)
                                    )
                                    Text(
                                        text = if (isFullyCovered) "TOTALMENTE PAGADA" else "$${"%.2f".format(remaining)} CUP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = if (isFullyCovered) Emerald600 else Color(0xFFB45309)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.asociarTransferenciaAComanda(
                                    parsed = parsed,
                                    titularName = titularName,
                                    titularCi = titularCi,
                                    phoneNumber = titularPhone,
                                    orderId = order.id
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_confirmar_asociacion_comanda"),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CONFIRMAR ASOCIACIÓN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AsociarTransferenciaExistenteDialog(
    transferencia: Transferencia,
    openOrders: List<TableOrder>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOrder by remember { mutableStateOf<TableOrder?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("dialog_asociar_transferencia_existente")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Asociar a Comanda",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Trans: ${transferencia.transactionNumber} ($${"%.2f".format(transferencia.amount)} ${transferencia.currency})",
                            fontSize = 12.sp,
                            color = Color(0xFF0284C7),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                HorizontalDivider(color = Slate100)

                if (openOrders.isEmpty()) {
                    Text(
                        text = "No hay comandas abiertas para asociar.",
                        color = Slate500,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(openOrders) { order ->
                            val isSelected = selectedOrder?.id == order.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFE0F2FE) else Slate50,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF0284C7) else Slate200
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedOrder = order }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val tableStr = if ((order.tableNumber ?: 0) > 0) "Mesa ${order.tableNumber}" else "PARA LLEVAR"
                                    Column {
                                        Text(
                                            text = "Comanda #${order.comandaNumber} ($tableStr)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        if (order.customerName.isNotBlank()) {
                                            Text(text = "Cliente: ${order.customerName}", fontSize = 11.sp, color = Slate500)
                                        }
                                    }
                                    Text(
                                        text = "$${"%.2f".format(order.totalAmount)} CUP",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedOrder != null) {
                    val isFullyCovered = transferencia.amount >= selectedOrder!!.totalAmount
                    val remaining = if (isFullyCovered) 0.0 else selectedOrder!!.totalAmount - transferencia.amount

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isFullyCovered) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isFullyCovered) "La transferencia cubre el total de la comanda." else "Saldo restante: $${"%.2f".format(remaining)} CUP (Pago parcial).",
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isFullyCovered) Emerald600 else Color(0xFFB45309)
                        )
                    }

                    Button(
                        onClick = { onConfirm(selectedOrder!!.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CONFIRMAR ASOCIACIÓN", fontWeight = FontWeight.Bold)
                    }
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
    var smsPastedText by remember { mutableStateOf("") }
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
            errorMessage = "No se encontraron transferencias PAGOxMOVIL para la fecha ${dateDisplayFormat.format(targetCal.time)}."
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
            errorMessage = "Permiso de SMS denegado."
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            color = Color(0xFFF3E8FF)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCard,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TRANSFERENCIA EXTERNA",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Pegar SMS o Buscar pagos en SMS",
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

                // Date filter selector bar
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "Fecha a consultar:",
                                    fontSize = 10.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = dateDisplayFormat.format(selectedDateCalendar.time),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
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
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = { datePickerDialog.show() },
                            colors = ButtonDefaults.textButtonColors(contentColor = ElQadreNavy)
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cambiar Fecha", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // SMS text field
                OutlinedTextField(
                    value = smsPastedText,
                    onValueChange = { smsPastedText = it },
                    label = { Text("Pegar SMS completo de PAGOxMOVIL") },
                    placeholder = { Text("Ej. El titular del telefono 5351110746 le ha realizado una transferencia...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .testTag("input_manual_sms"),
                    maxLines = 4
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                performSearch(selectedDateCalendar)
                            } else {
                                launcher.launch(android.Manifest.permission.READ_SMS)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BUSCAR PAGO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val parsed = com.example.util.SmsTransferParser.parseTransferSms(smsPastedText)
                            if (parsed == null) {
                                errorMessage = "El texto pegado no es un SMS válido de PAGOxMOVIL."
                                return@Button
                            }
                            val effectiveMillis = if (parsed.timestampMillis > 0L) parsed.timestampMillis else selectedDateCalendar.timeInMillis
                            val finalDateStr = if (parsed.dateStr.isNotBlank()) parsed.dateStr else dateDisplayFormat.format(java.util.Date(effectiveMillis))
                            viewModel.registrarTransferenciaManual(
                                parsed = parsed.copy(dateStr = finalDateStr, timestampMillis = effectiveMillis),
                                smsTimestampMillis = effectiveMillis,
                                onSuccess = {
                                    Toast.makeText(context, "Transferencia registrada correctamente", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REGISTRAR PEGADO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFB45309)
                            )
                            Text(
                                text = "$availableCount nuevos • $registeredCount registrados",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate500
                            )
                        }
                        
                        if (availableCount > 0) {
                            TextButton(
                                onClick = {
                                    val availableTransactions = foundSmsList!!.filter { !it.isAlreadyRegistered }.map { it.parsed.transactionNumber }.toSet()
                                    if (selectedSms.size == availableTransactions.size) {
                                        // Deselect all
                                        selectedSms = emptySet()
                                    } else {
                                        // Select all
                                        selectedSms = availableTransactions
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = ElQadreNavy),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_select_all_transferencias")
                            ) {
                                val allSelected = selectedSms.size == availableCount && availableCount > 0
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (allSelected) "Desmarcar todas" else "Seleccionar todas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        .padding(10.dp),
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
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REGISTRAR SELECCIONADOS (${selectedSms.size})", fontWeight = FontWeight.Bold)
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp)
                .testTag("dialog_informe_transferencias")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                text = "Filtro activo: $filterLabel",
                                fontSize = 11.sp,
                                color = Color(0xFF0369A1),
                                fontWeight = FontWeight.SemiBold
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
                                Text("MONTO TOTAL RECIBIDO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate500)
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
                                    fontSize = 11.sp,
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
                                    Text("Detectadas por SMS", fontSize = 9.sp, color = Color(0xFF0284C7))
                                    Text("$countSms", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                                }
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = Color.White, modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Entrada Manual/Ext.", fontSize = 9.sp, color = Color(0xFF7C3AED))
                                    Text("$countManual", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF7C3AED))
                                }
                            }
                        }
                    }
                }

                // List preview
                Text(
                    text = "RESUMEN DE TRANSFERENCIAS (${transfers.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Slate500
                )

                if (transfers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No hay transferencias registradas para el filtro '$filterLabel'.",
                            fontSize = 12.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(transfers) { tx ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate100),
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
                                                fontSize = 12.sp,
                                                color = ElQadreNavy
                                            )
                                            val tagBg = if (tx.isManual) Color(0xFFF3E8FF) else Color(0xFFE0F2FE)
                                            val tagColor = if (tx.isManual) Color(0xFF7C3AED) else Color(0xFF0284C7)
                                            Surface(shape = RoundedCornerShape(4.dp), color = tagBg) {
                                                Text(
                                                    text = if (tx.isManual) "MANUAL" else "SMS",
                                                    fontSize = 8.sp,
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
                                            fontSize = 10.sp,
                                            color = Slate500
                                        )
                                    }
                                    Text(
                                        text = "$${"%.2f".format(tx.amount)} CUP",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )
                                }
                            }
                        }
                    }
                }

                // Botón "ENVIAR INFORME"
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
                        .height(48.dp)
                        .testTag("btn_enviar_informe_transferencias"),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ENVIAR INFORME",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
