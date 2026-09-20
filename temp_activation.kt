package com.example.ui.screens.superadmin

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.licensing.BusinessRecord
import com.example.licensing.SuperAdminBusinessManager
import com.example.licensing.SuperAdminSmsHelper
import com.example.licensing.TrialSmsRequest
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tarjeta exclusiva para la gestión de Solicitudes de Prueba por SMS en Super Admin.
 * Contiene ÚNICAMENTE:
 * - ESCANEAR
 * - PENDIENTES
 * - CONFIRMADAS
 *
 * Al pulsar cualquiera de estas opciones, se despliega un cuadro superpuesto amplio y legible.
 * NO incluye AJUSTES en esta tarjeta.
 */
@Composable
fun SuperAdminTrialSmsCard(
    modifier: Modifier = Modifier,
    onBusinessCreatedOrUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var trialRequests by remember { mutableStateOf<List<TrialSmsRequest>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Dialog Overlays (Cuadros superpuestos amplios y legibles)
    var showScanOverlay by rememberSaveable { mutableStateOf(false) }
    var showPendientesOverlay by rememberSaveable { mutableStateOf(false) }
    var showConfirmadasOverlay by rememberSaveable { mutableStateOf(false) }

    // Review & Process Dialog
    var requestToReview by remember { mutableStateOf<TrialSmsRequest?>(null) }
    var generatedFileToShare by remember { mutableStateOf<File?>(null) }
    var successBusinessRecord by remember { mutableStateOf<BusinessRecord?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            trialRequests = SuperAdminSmsHelper.scanInboxForTrialRequests(context)
        }
    }

    fun doScan() {
        isScanning = true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            trialRequests = SuperAdminSmsHelper.scanInboxForTrialRequests(context)
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

    val pendingList = remember(trialRequests) {
        trialRequests.filter { !SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
    }

    val confirmedList = remember(trialRequests) {
        trialRequests.filter { SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
    }

    // MAIN CARD: SOLO ESCANEAR, PENDIENTES, CONFIRMADAS
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
                    color = ElQadreNavySoft,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SOLICITUD DE PRUEBA (SMS)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Rastreo y procesamiento automático de solicitudes de 7 días",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // THREE EXCLUSIVE OPTIONS: ESCANEAR, PENDIENTES, CONFIRMADAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ESCANEAR",
                            fontSize = 11.sp,
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PENDIENTES",
                            fontSize = 11.sp,
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
                        containerColor = Emerald600,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
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
                                        containerColor = ElQadreNavy,
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "CONFIRMADAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // OVERLAY 1: ESCANEAR DIALOG
    if (showScanOverlay) {
        TrialSmsScanOverlayDialog(
            totalCount = trialRequests.size,
            pendingCount = pendingList.size,
            confirmedCount = confirmedList.size,
            onRescan = { doScan() },
            onOpenPendientes = {
                showScanOverlay = false
                showPendientesOverlay = true
            },
            onDismiss = { showScanOverlay = false }
        )
    }

    // OVERLAY 2: PENDIENTES DIALOG
    if (showPendientesOverlay) {
        TrialSmsPendientesOverlayDialog(
            pendingList = pendingList,
            onProcess = { req ->
                requestToReview = req
            },
            onDismiss = { showPendientesOverlay = false }
        )
    }

    // OVERLAY 3: CONFIRMADAS DIALOG
    if (showConfirmadasOverlay) {
        TrialSmsConfirmadasOverlayDialog(
            confirmedList = confirmedList,
            onDismiss = { showConfirmadasOverlay = false }
        )
    }

    // REVIEW & PROCESS CONFIRMATION DIALOG
    requestToReview?.let { req ->
        TrialSmsReviewConfirmDialog(
            request = req,
            onConfirm = { name, dueno, mp, ma, ciudad, dvc ->
                coroutineScope.launch {
                    val result = SuperAdminSmsHelper.processTrialRequest(
                        context = context,
                        request = req,
                        customName = name,
                        customDueno = dueno,
                        customMp = mp,
                        customMa = ma,
                        customCiudad = ciudad,
                        customDvc = dvc
                    )
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    successBusinessRecord = result.first
                    generatedFileToShare = result.second
                }
            },
            onDismiss = { requestToReview = null }
        )
    }

    // SUCCESS & SHARE OVERLAY
    if (successBusinessRecord != null && generatedFileToShare != null) {
        TrialSmsSuccessOverlayDialog(
            business = successBusinessRecord!!,
            file = generatedFileToShare!!,
            onSendConfirmationSms = {
                val smsText = "ELQADRE: Su solicitud de prueba para '${successBusinessRecord!!.name}' ha sido APROBADA por 7 dias. Codigo de negocio: ${successBusinessRecord!!.code}."
                SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, successBusinessRecord!!.phone, smsText)
            },
            onShareJson = {
                SuperAdminSmsHelper.shareJsonFile(context, generatedFileToShare!!, "Compartir Q_preuba.json")
            },
            onDismiss = {
                successBusinessRecord = null
                generatedFileToShare = null
            }
        )
    }
}

/**
 * Cuadro superpuesto amplio y legible para ESCANEAR
 */
@Composable
private fun TrialSmsScanOverlayDialog(
    totalCount: Int,
    pendingCount: Int,
    confirmedCount: Int,
    onRescan: () -> Unit,
    onOpenPendientes: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
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
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "ESCANEAR SOLICITUDES",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Bandeja de SMS para Solicitudes de Prueba",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Results Summary Cards
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
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$pendingCount",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber800
                            )
                            Text(
                                text = "PENDIENTES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Amber700
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
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$confirmedCount",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                            Text(
                                text = "CONFIRMADAS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald700
                            )
                        }
                    }
                }

                // Format explanation card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Formato Fijo de SMS Rastreado:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "SOLICITUD PRUEBA ELQADRE (nombre de negocio)\nDueño: (nombre y apellidos del dueño)\nMP: (móvil principal)\nMA: (móvil alternativo)\nCiudad: (ciudad)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Slate800,
                                modifier = Modifier.padding(10.dp),
                                lineHeight = 16.sp
                            )
                        }
                        Text(
                            text = "• El remitente puede ser cualquier número telefónico.",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                        Text(
                            text = "• Los datos extraídos crean el registro del negocio y su prueba automáticamente.",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRescan,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-escanear", fontSize = 12.sp)
                    }

                    if (pendingCount > 0) {
                        Button(
                            onClick = onOpenPendientes,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Ver Pendientes ($pendingCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Aceptar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cuadro superpuesto amplio y legible para PENDIENTES
 */
@Composable
private fun TrialSmsPendientesOverlayDialog(
    pendingList: List<TrialSmsRequest>,
    onProcess: (TrialSmsRequest) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = Amber700,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "SOLICITUDES PENDIENTES",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "${pendingList.size} solicitudes detectadas sin procesar",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Slate100)
                Spacer(modifier = Modifier.height(10.dp))

                if (pendingList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircleOutline,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No hay solicitudes de prueba pendientes",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                            Text(
                                text = "Todas las solicitudes detectadas han sido procesadas o confirmadas.",
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
                        items(pendingList, key = { it.uniqueKey }) { item ->
                            PendingTrialRequestCard(
                                request = item,
                                onProcess = { onProcess(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tarjeta individual para solicitud pendiente dentro del cuadro superpuesto
 */
@Composable
private fun PendingTrialRequestCard(
    request: TrialSmsRequest,
    onProcess: () -> Unit
) {
    val dateStr = remember(request.timestamp) {
        if (request.timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(request.timestamp))
        } else "Reciente"
    }

    var expandedRaw by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Amber100
                ) {
                    Text(
                        text = "SOLICITUD DE PRUEBA 7D",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber800,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            Text(
                text = request.nombreNegocio,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )

            // Extracted Details Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👤 Dueño: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                    Text(request.dueno.ifBlank { "No especificado" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📱 MP (Principal): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                    Text(request.movilPrincipal.ifBlank { request.senderAddress }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                if (request.movilAlternativo.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞 MA (Alternativo): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(request.movilAlternativo, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
                    }
                }
                if (request.ciudad.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍 Ciudad: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(request.ciudad, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✉️ Remitente SMS: ", fontSize = 11.sp, color = Slate500)
                    Text(request.senderAddress, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate700)
                }
            }

            // Raw SMS Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedRaw = !expandedRaw },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedRaw) "Ocultar texto SMS" else "Ver texto SMS original",
                    fontSize = 11.sp,
                    color = ElQadreNavy,
                    fontWeight = FontWeight.Medium
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
                    shape = RoundedCornerShape(6.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = request.rawBody,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Slate700,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Process Button
            Button(
                onClick = onProcess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("REVISAR Y CONFIRMAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Cuadro superpuesto amplio y legible para CONFIRMADAS
 */
@Composable
private fun TrialSmsConfirmadasOverlayDialog(
    confirmedList: List<TrialSmsRequest>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allBusinesses = remember { SuperAdminBusinessManager.getBusinesses(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "SOLICITUDES CONFIRMADAS",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "${confirmedList.size} solicitudes procesadas y activadas",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Slate100)
                Spacer(modifier = Modifier.height(10.dp))

                if (confirmedList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassEmpty,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Aún no hay solicitudes de prueba confirmadas",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                            Text(
                                text = "Al procesar una solicitud desde 'PENDIENTES', aparecerá aquí con sus datos y estado.",
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
                        items(confirmedList, key = { it.uniqueKey }) { item ->
                            val matchedBiz = allBusinesses.firstOrNull { b ->
                                b.name.equals(item.nombreNegocio, ignoreCase = true) ||
                                        (item.movilPrincipal.isNotBlank() && b.phone.equals(item.movilPrincipal, ignoreCase = true))
                            }
                            ConfirmedTrialRequestCard(
                                request = item,
                                business = matchedBiz
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Tarjeta individual para solicitud confirmada dentro del cuadro superpuesto
 */
@Composable
private fun ConfirmedTrialRequestCard(
    request: TrialSmsRequest,
    business: BusinessRecord?
) {
    val context = LocalContext.current
    val dateStr = remember(request.timestamp) {
        if (request.timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(request.timestamp))
        } else "Procesada"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Emerald100
                ) {
                    Text(
                        text = "PRUEBA ACTIVA (7 DÍAS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald800,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (business != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreNavy
                    ) {
                        Text(
                            text = business.code,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreGold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = business?.name ?: request.nombreNegocio,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            }

            // Details Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val duenoText = business?.dueno?.ifBlank { request.dueno } ?: request.dueno
                if (duenoText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👤 Dueño: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(duenoText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📱 MP (Principal): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                    Text(business?.phone ?: request.movilPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                val altText = business?.phoneAlt?.ifBlank { request.movilAlternativo } ?: request.movilAlternativo
                if (altText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞 MA (Alternativo): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(altText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
                    }
                }
                val ciudadText = business?.ciudad?.ifBlank { request.ciudad } ?: request.ciudad
                if (ciudadText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍 Ciudad: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(ciudadText, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
                    }
                }
                if (business != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅 Vigencia: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text("${business.startDate} al ${business.endDate}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                    }
                }
            }

            // Action: Resend confirmation SMS
            OutlinedButton(
                onClick = {
                    val phone = business?.phone ?: request.movilPrincipal
                    val name = business?.name ?: request.nombreNegocio
                    val code = business?.code ?: ""
                    val smsText = "ELQADRE: Su solicitud de prueba para '$name' ha sido CONFIRMADA por 7 dias. Codigo de negocio: $code."
                    SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, phone, smsText)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(15.dp), tint = ElQadreNavy)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Enviar SMS de Confirmación", fontSize = 12.sp, color = ElQadreNavy)
            }
        }
    }
}

/**
 * Cuadro superpuesto para revisar y confirmar los datos antes de crear el negocio
 */
@Composable
private fun TrialSmsReviewConfirmDialog(
    request: TrialSmsRequest,
    onConfirm: (name: String, dueno: String, mp: String, ma: String, ciudad: String, dvc: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val nextCode = remember { SuperAdminBusinessManager.getNextBusinessCode(context) }

    var name by remember { mutableStateOf(request.nombreNegocio) }
    var dueno by remember { mutableStateOf(request.dueno) }
    var mp by remember { mutableStateOf(request.movilPrincipal.ifBlank { request.senderAddress }) }
    var ma by remember { mutableStateOf(request.movilAlternativo) }
    var ciudad by remember { mutableStateOf(request.ciudad) }
    var dvc by remember { mutableStateOf(request.dvc) }

    var nameError by remember { mutableStateOf(false) }
    var mpError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "REVISAR Y CONFIRMAR PRUEBA",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Código que se asignará: [$nextCode] • Duración: 7 Días",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Amber700
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                HorizontalDivider(color = Slate100)

                // Editable Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.trim().isBlank()
                    },
                    label = { Text("Nombre del Negocio *") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) { { Text("El nombre es obligatorio", color = Rose600) } } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dueno,
                    onValueChange = { dueno = it },
                    label = { Text("Dueño del Negocio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = mp,
                        onValueChange = {
                            mp = it
                            mpError = it.trim().isBlank()
                        },
                        label = { Text("Móvil Principal (MP) *") },
                        singleLine = true,
                        isError = mpError,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = ma,
                        onValueChange = { ma = it },
                        label = { Text("Móvil Alt. (MA)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = ciudad,
                        onValueChange = { ciudad = it },
                        label = { Text("Ciudad") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dvc,
                        onValueChange = { dvc = it },
                        label = { Text("DVC (Opcional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Emerald50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Acciones al confirmar:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald800
                        )
                        Text("1. Crea el negocio '$name' con código [$nextCode].", fontSize = 11.sp, color = Slate700)
                        Text("2. Conserva ambos teléfonos (MP: $mp, MA: ${ma.ifBlank { "Ninguno" }}).", fontSize = 11.sp, color = Slate700)
                        Text("3. Registra la prueba de 7 días y actualiza Q_preuba.json.", fontSize = 11.sp, color = Slate700)
                        Text("4. Mueve la solicitud de PENDIENTES a CONFIRMADAS.", fontSize = 11.sp, color = Slate700)
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (name.trim().isBlank()) {
                                nameError = true
                                return@Button
                            }
                            if (mp.trim().isBlank()) {
                                mpError = true
                                return@Button
                            }
                            onConfirm(name.trim(), dueno.trim(), mp.trim(), ma.trim(), ciudad.trim(), dvc.trim())
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONFIRMAR PRUEBA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Cuadro superpuesto de éxito tras procesar y crear el negocio
 */
@Composable
private fun TrialSmsSuccessOverlayDialog(
    business: BusinessRecord,
    file: File,
    onSendConfirmationSms: () -> Unit,
    onShareJson: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(48.dp)
                )

                Text(
                    text = "¡Prueba Creada y Confirmada!",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Código Asignado: ", fontSize = 12.sp, color = Slate600)
                            Text(business.code, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Negocio: ", fontSize = 12.sp, color = Slate600)
                            Text(business.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        if (business.dueno.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Dueño: ", fontSize = 12.sp, color = Slate600)
                                Text(business.dueno, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Móvil Principal: ", fontSize = 12.sp, color = Slate600)
                            Text(business.phone, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        if (business.phoneAlt.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Móvil Alternativo: ", fontSize = 12.sp, color = Slate600)
                                Text(business.phoneAlt, fontSize = 12.sp, color = Slate800)
                            }
                        }
                        if (business.ciudad.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Ciudad: ", fontSize = 12.sp, color = Slate600)
                                Text(business.ciudad, fontSize = 12.sp, color = Slate800)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Vigencia (7D): ", fontSize = 12.sp, color = Slate600)
                            Text("${business.startDate} al ${business.endDate}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                    }
                }

                Text(
                    text = "Se ha actualizado '${file.name}' localmente.",
                    fontSize = 11.sp,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )

                // Actions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSendConfirmationSms,
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enviar SMS de Confirmación", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onShareJson,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir Q_preuba.json", fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aceptar y Cerrar", color = Slate600)
                    }
                }
            }
        }
    }
}
