package com.example.ui.screens.superadmin
import com.example.util.ContactHelper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.licensing.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tarjeta exclusiva para la gestión de SMS de Pago en Super Admin.
 * Contiene:
 * - ESCANEAR
 * - PENDIENTES
 * - CONFIRMADAS
 * - AJUSTES
 *
 * Al pulsar cualquiera de estas opciones, muestra la información en un cuadro superpuesto amplio y legible.
 */
@Composable
fun SuperAdminPaymentSmsCard(
    modifier: Modifier = Modifier,
    onPaymentConfirmed: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var paymentRecords by remember { mutableStateOf<List<PaymentSmsRecord>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Dialog Overlays (Cuadros superpuestos amplios y legibles)
    var showScanOverlay by rememberSaveable { mutableStateOf(false) }
    var showPendientesOverlay by rememberSaveable { mutableStateOf(false) }
    var showConfirmadasOverlay by rememberSaveable { mutableStateOf(false) }
    var showAjustesOverlay by rememberSaveable { mutableStateOf(false) }

    // Confirm Payment Dialog
    var paymentToConfirm by remember { mutableStateOf<PaymentSmsRecord?>(null) }
    var duplicateConfirmPayment by remember { mutableStateOf<PaymentSmsRecord?>(null) }
    var successConfirmedRecord by remember { mutableStateOf<PaymentSmsRecord?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            paymentRecords = SuperAdminPaymentHelper.scanInboxForPayments(context)
        }
    }

    fun doScan() {
        isScanning = true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            paymentRecords = SuperAdminPaymentHelper.scanInboxForPayments(context)
            isScanning = false
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
                )
            )
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        doScan()
    }

    val pendingList = remember(paymentRecords) {
        paymentRecords.filter { !SuperAdminPaymentHelper.isPaymentConfirmed(context, it.uniqueKey) }
    }

    val confirmedList = remember(paymentRecords) {
        paymentRecords.filter { SuperAdminPaymentHelper.isPaymentConfirmed(context, it.uniqueKey) }
    }

    // MAIN CARD: ESCANEAR, PENDIENTES, CONFIRMADAS, AJUSTES
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Emerald50,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = null,
                            tint = Emerald700,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMS DE PAGO (SUPER ADMIN)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Detección y validación de transferencias bancarias de negocios en prueba",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // FOUR EXCLUSIVE OPTIONS: ESCANEAR, PENDIENTES, CONFIRMADAS, AJUSTES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // OPTION 1: ESCANEAR
                Button(
                    onClick = {
                        doScan()
                        showScanOverlay = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "ESCANEAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // OPTION 2: PENDIENTES
                Button(
                    onClick = {
                        doScan()
                        showPendientesOverlay = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pendingList.isNotEmpty()) Amber600 else Slate700,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (pendingList.isNotEmpty()) {
                                    Badge(
                                        containerColor = Rose600,
                                        contentColor = Color.White
                                    ) {
                                        Text("${pendingList.size}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = "Pendientes",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "PENDIENTES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // OPTION 3: CONFIRMADAS
                Button(
                    onClick = {
                        doScan()
                        showConfirmadasOverlay = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald700,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (confirmedList.isNotEmpty()) {
                                    Badge(
                                        containerColor = Emerald800,
                                        contentColor = Color.White
                                    ) {
                                        Text("${confirmedList.size}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Confirmadas",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "CONFIRMADAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // OPTION 4: AJUSTES
                Button(
                    onClick = {
                        showAjustesOverlay = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Slate800,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Ajustes",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "AJUSTES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Status summary
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Estado: ${pendingList.size} pendientes • ${confirmedList.size} confirmadas",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                    Text(
                        text = "Solo negocios en PRUEBA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }
            }
        }
    }

    // ==========================================
    // OVERLAY 1: ESCANEAR DIALOG
    // ==========================================
    if (showScanOverlay) {
        Dialog(
            onDismissRequest = { showScanOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp
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
                                shape = CircleShape,
                                color = ElQadreNavySoft,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = ElQadreNavy
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Escanear SMS de Pago",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "Bandeja SMS • ${paymentRecords.size} pagos detectados",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }
                        }

                        IconButton(onClick = { showScanOverlay = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Slate600
                            )
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // Results Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Amber50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Amber200),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${pendingList.size}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Amber800
                                )
                                Text(
                                    text = "Pendientes",
                                    fontSize = 10.sp,
                                    color = Amber800
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Emerald50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Emerald200),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${confirmedList.size}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald700
                                )
                                Text(
                                    text = "Confirmadas",
                                    fontSize = 10.sp,
                                    color = Emerald800
                                )
                            }
                        }
                    }

                    // Scanned SMS Payment List or Empty
                    if (paymentRecords.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Paid,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No se encontraron SMS de pago en la bandeja de entrada según los patrones configurados.",
                                    fontSize = 13.sp,
                                    color = Slate600,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Mensajes de Pago Encontrados para Revisión:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(paymentRecords, key = { it.uniqueKey }) { payment ->
                                val isConfirmed = SuperAdminPaymentHelper.isPaymentConfirmed(context, payment.uniqueKey)

                                PaymentItemCard(
                                    payment = payment,
                                    isConfirmed = isConfirmed,
                                    onConfirmClick = {
                                        if (isConfirmed) {
                                            duplicateConfirmPayment = payment
                                        } else {
                                            paymentToConfirm = payment
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { doScan() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-escanear", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showScanOverlay = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cerrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // DUPLICATE PAYMENT CONFIRMATION DIALOG
    duplicateConfirmPayment?.let { payment ->
        AlertDialog(
            onDismissRequest = { duplicateConfirmPayment = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber600,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Pago Ya Confirmado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = "El pago con referencia de transacción '${payment.transactionId}' ya fue verificado y confirmado anteriormente.\n\n¿Desea revisar y procesarlo de todos modos?",
                    fontSize = 13.sp,
                    color = Slate700,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedPayment = payment
                        duplicateConfirmPayment = null
                        paymentToConfirm = selectedPayment
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Procesar de todos modos", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { duplicateConfirmPayment = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ==========================================
    // OVERLAY 2: PENDIENTES DIALOG
    // ==========================================
    if (showPendientesOverlay) {
        Dialog(
            onDismissRequest = { showPendientesOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                shape = CircleShape,
                                color = Amber50,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = Amber800
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Pagos Pendientes (${pendingList.size})",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "SMS de transferencias de negocios en prueba pendientes de confirmar",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }
                        }

                        IconButton(onClick = { showPendientesOverlay = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Slate600
                            )
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    if (pendingList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald600,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = "No hay pagos pendientes",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                                Text(
                                    text = "Todos los pagos recibidos han sido confirmados o no hay nuevos SMS.",
                                    fontSize = 12.sp,
                                    color = Slate500,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pendingList, key = { it.uniqueKey }) { payment ->
                                PaymentItemCard(
                                    payment = payment,
                                    isConfirmed = false,
                                    onConfirmClick = {
                                        paymentToConfirm = payment
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { doScan() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Actualizar")
                        }

                        Button(
                            onClick = { showPendientesOverlay = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY 3: CONFIRMADAS DIALOG
    // ==========================================
    if (showConfirmadasOverlay) {
        Dialog(
            onDismissRequest = { showConfirmadasOverlay = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                shape = CircleShape,
                                color = Emerald50,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Emerald700
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Pagos Confirmados (${confirmedList.size})",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "Remitentes habilitados para activación comercial",
                                    fontSize = 12.sp,
                                    color = Slate600
                                )
                            }
                        }

                        IconButton(onClick = { showConfirmadasOverlay = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Slate600
                            )
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    if (confirmedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Paid,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(54.dp)
                                )
                                Text(
                                    text = "No hay pagos confirmados aún",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                                Text(
                                    text = "Los pagos confirmados desde la sección PENDIENTES aparecerán aquí.",
                                    fontSize = 12.sp,
                                    color = Slate500,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(confirmedList, key = { it.uniqueKey }) { payment ->
                                PaymentItemCard(
                                    payment = payment,
                                    isConfirmed = true,
                                    onConfirmClick = {}
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showConfirmadasOverlay = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OVERLAY 4: AJUSTES DIALOG
    // ==========================================
    if (showAjustesOverlay) {
        SuperAdminPaymentAjustesDialog(
            onDismiss = {
                showAjustesOverlay = false
                doScan()
            }
        )
    }

    // ==========================================
    // CONFIRM PAYMENT MODAL DIALOG
    // ==========================================
    paymentToConfirm?.let { payment ->
        Dialog(
            onDismissRequest = { paymentToConfirm = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Emerald50,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald700
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Confirmar Pago Recibido",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Habilitar remitente para activación",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Negocio: [${payment.businessCode}] ${payment.businessName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Teléfono: ${ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, payment.phoneNumber)} (${if (payment.phoneTypeMatched == "MP") "Móvil Principal" else "Móvil Alternativo"})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                            Text(
                                text = "Cuenta de Destino: ${payment.accountNumber}",
                                fontSize = 13.sp,
                                color = Slate800,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Nro. Transacción: ${payment.transactionId}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Fecha: ${payment.paymentDate}",
                                fontSize = 13.sp,
                                color = Slate700
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElQadreNavySoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Acción al Confirmar:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "• Habilita este número telefónico como remitente válido para la tarjeta ACTIVACIÓN.\n• NO se activa todavía la licencia comercial.\n• NO se modifica Q_licencias.json.",
                                fontSize = 11.sp,
                                color = Slate700,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { paymentToConfirm = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                SuperAdminPaymentHelper.confirmPayment(context, payment)
                                Toast.makeText(context, "Pago confirmado. Remitente habilitado para activación.", Toast.LENGTH_LONG).show()
                                successConfirmedRecord = payment
                                paymentToConfirm = null
                                doScan()
                                onPaymentConfirmed()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Emerald700,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirmar Pago", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual Payment SMS Item card for Pendientes and Confirmadas
 */
@Composable
private fun PaymentItemCard(
    payment: PaymentSmsRecord,
    isConfirmed: Boolean,
    onConfirmClick: () -> Unit
) {
    var expandedRaw by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConfirmed) Emerald200 else Amber200
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isConfirmed) Emerald50 else Amber50
                ) {
                    Text(
                        text = if (isConfirmed) "PAGO CONFIRMADO" else "PAGO PENDIENTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConfirmed) Emerald800 else Amber800,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "Fecha: ${payment.paymentDate}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate600
                )
            }

            // Business info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "[${payment.businessCode}] ${payment.businessName}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                    if (payment.businessDueno.isNotBlank()) {
                        Text(
                            text = "Dueño: ${payment.businessDueno}",
                            fontSize = 12.sp,
                            color = Slate600
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            // Extracted 4 Data Points Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "1. Teléfono:",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Text(
                        text = "${ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, payment.phoneNumber)} (${if (payment.phoneTypeMatched == "MP") "MP" else "MA"})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "2. Cuenta:",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Text(
                        text = payment.accountNumber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "3. Transacción:",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Text(
                        text = payment.transactionId,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald700,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "4. Fecha:",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    Text(
                        text = payment.paymentDate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                }
            }

            // Expandable raw text
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedRaw = !expandedRaw },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedRaw) "Ocultar SMS original" else "Ver SMS original...",
                    fontSize = 11.sp,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = if (expandedRaw) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expandedRaw) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = payment.rawBody,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Slate800,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Bottom action
            if (!isConfirmed) {
                Button(
                    onClick = onConfirmClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald700,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirmar Pago", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Emerald50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Verified, contentDescription = null, tint = Emerald700, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Remitente habilitado para ACTIVACIÓN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald700
                        )
                    }
                }
            }
        }
    }
}

/**
 * AJUSTES Dialog: Allows Super Admin to configure different senders and formats, and test with live text.
 */
@Composable
private fun SuperAdminPaymentAjustesDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var configs by remember { mutableStateOf(SuperAdminPaymentHelper.getPaymentConfigs(context)) }
    var editingConfig by remember { mutableStateOf<PaymentSenderConfig?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // Simulator / Live Tester
    var testSmsText by remember {
        mutableStateOf("El titular del telefono 5354205031 le ha realizado una transferencia a la cuenta 9224069993889860 de 1500.00 CUP. Nro. Transaccion KW601Q3PKK999. Fecha: 10/9/2026.")
    }
    var selectedTestConfig by remember { mutableStateOf(configs.firstOrNull()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 16.dp
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
                            shape = CircleShape,
                            color = Slate100,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Slate800
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Ajustes de SMS de Pago",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Configuración de remitentes y reglas de extracción",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate600
                        )
                    }
                }

                HorizontalDivider(color = Slate200)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Action Bar: Add Format & Restore Defaults
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Remitentes Configurados (${configs.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    configs = SuperAdminPaymentHelper.getDefaultConfigs()
                                    SuperAdminPaymentHelper.savePaymentConfigs(context, configs)
                                    selectedTestConfig = configs.firstOrNull()
                                    Toast.makeText(context, "Ajustes restablecidos por defecto", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Por Defecto", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    editingConfig = PaymentSenderConfig(
                                        id = UUID.randomUUID().toString(),
                                        senderName = ""
                                    )
                                    isAddingNew = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElQadreNavy,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Añadir Remitente", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // List of Configs
                    configs.forEach { cfg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (cfg.isEnabled) Slate50 else Slate100,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (cfg.isEnabled) Slate300 else Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                                            shape = RoundedCornerShape(4.dp),
                                            color = ElQadreNavy
                                        ) {
                                            Text(
                                                text = cfg.senderName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (!cfg.isEnabled) {
                                            Text(
                                                text = "(Desactivado)",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingConfig = cfg
                                                isAddingNew = false
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = ElQadreNavy,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val updated = configs.filter { it.id != cfg.id }
                                                configs = updated
                                                SuperAdminPaymentHelper.savePaymentConfigs(context, updated)
                                                if (selectedTestConfig?.id == cfg.id) {
                                                    selectedTestConfig = updated.firstOrNull()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Eliminar",
                                                tint = Rose600,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "1. Teléfono: palabra '${cfg.phonePrefixWord}' → ${cfg.phoneDigitCount} dígitos",
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                                Text(
                                    text = "2. Cuenta: palabra '${cfg.accountPrefixWord}' → ${cfg.accountDigitCount} dígitos",
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                                Text(
                                    text = "3. Transacción: palabra '${cfg.transactionPrefixWord}' → (hasta '.')",
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                                Text(
                                    text = "4. Fecha: palabra '${cfg.datePrefixWord}' → (hasta '.')",
                                    fontSize = 12.sp,
                                    color = Slate700
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Slate200)

                    // LIVE SIMULATOR / TESTER BOX
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ElQadreNavySoft.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Science, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Probador en Vivo de Formato",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }

                            Text(
                                text = "Pegue un SMS de ejemplo para verificar la extracción simultánea de los 4 datos:",
                                fontSize = 11.sp,
                                color = Slate700
                            )

                            OutlinedTextField(
                                value = testSmsText,
                                onValueChange = { testSmsText = it },
                                placeholder = { Text("Pegue el texto del SMS aquí...") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            // Result of Test
                            val activeCfg = selectedTestConfig ?: configs.firstOrNull()
                            if (activeCfg != null) {
                                val testResult = remember(testSmsText, activeCfg) {
                                    SuperAdminPaymentHelper.testExtraction(testSmsText, activeCfg)
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "Extracción con regla [${activeCfg.senderName}]:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "• Teléfono: ${testResult["phone"] ?: "❌ NO DETECTADO"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (testResult["phone"] != null) Emerald700 else Rose600,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "• Cuenta: ${testResult["account"] ?: "❌ NO DETECTADO"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (testResult["account"] != null) Emerald700 else Rose600,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "• Transacción: ${testResult["transaction"] ?: "❌ NO DETECTADO"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (testResult["transaction"] != null) Emerald700 else Rose600,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "• Fecha: ${testResult["date"] ?: "❌ NO DETECTADO"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (testResult["date"] != null) Emerald700 else Rose600,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Guardar y Salir", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ==========================================
    // EDIT / ADD SENDER FORMAT MODAL
    // ==========================================
    editingConfig?.let { cfgToEdit ->
        var senderName by remember { mutableStateOf(cfgToEdit.senderName) }
        var phoneWord by remember { mutableStateOf(cfgToEdit.phonePrefixWord) }
        var phoneDigits by remember { mutableStateOf(cfgToEdit.phoneDigitCount.toString()) }
        var accountWord by remember { mutableStateOf(cfgToEdit.accountPrefixWord) }
        var accountDigits by remember { mutableStateOf(cfgToEdit.accountDigitCount.toString()) }
        var txWord by remember { mutableStateOf(cfgToEdit.transactionPrefixWord) }
        var dateWord by remember { mutableStateOf(cfgToEdit.datePrefixWord) }
        var isEnabled by remember { mutableStateOf(cfgToEdit.isEnabled) }

        Dialog(
            onDismissRequest = { editingConfig = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .padding(vertical = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isAddingNew) "Nuevo Remitente de Pago" else "Editar Formato de Remitente",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )

                    HorizontalDivider(color = Slate200)

                    OutlinedTextField(
                        value = senderName,
                        onValueChange = { senderName = it },
                        label = { Text("Nombre del Remitente (ej: TRANSFERMOVIL, ENZONA, * / TODOS)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // 1. Phone
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phoneWord,
                            onValueChange = { phoneWord = it },
                            label = { Text("Palabra antes de Teléfono") },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = phoneDigits,
                            onValueChange = { phoneDigits = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Dígitos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // 2. Account
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = accountWord,
                            onValueChange = { accountWord = it },
                            label = { Text("Palabra antes de Cuenta") },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        OutlinedTextField(
                            value = accountDigits,
                            onValueChange = { accountDigits = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Dígitos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // 3. Transaction
                    OutlinedTextField(
                        value = txWord,
                        onValueChange = { txWord = it },
                        label = { Text("Palabra antes de Transacción (toma hasta '.')") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    // 4. Date
                    OutlinedTextField(
                        value = dateWord,
                        onValueChange = { dateWord = it },
                        label = { Text("Palabra antes de Fecha (toma hasta '.')") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Regla activa", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingConfig = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                if (senderName.isBlank()) {
                                    Toast.makeText(context, "Ingrese el nombre del remitente", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val pDigits = phoneDigits.toIntOrNull() ?: 10
                                val aDigits = accountDigits.toIntOrNull() ?: 16

                                val updatedConfig = cfgToEdit.copy(
                                    senderName = senderName.trim(),
                                    phonePrefixWord = phoneWord.trim(),
                                    phoneDigitCount = pDigits,
                                    accountPrefixWord = accountWord.trim(),
                                    accountDigitCount = aDigits,
                                    transactionPrefixWord = txWord.trim(),
                                    datePrefixWord = dateWord.trim(),
                                    isEnabled = isEnabled
                                )

                                val newConfigs = if (isAddingNew) {
                                    configs + updatedConfig
                                } else {
                                    configs.map { if (it.id == cfgToEdit.id) updatedConfig else it }
                                }

                                configs = newConfigs
                                SuperAdminPaymentHelper.savePaymentConfigs(context, newConfigs)
                                editingConfig = null
                                Toast.makeText(context, "Ajustes guardados correctamente", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
