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

data class TrialSmsConfirmParams(
    val req: TrialSmsRequest,
    val name: String,
    val dueno: String,
    val mp: String,
    val ma: String,
    val ciudad: String,
    val dvc: String
)

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
    autoOpenPendientes: Boolean = false,
    onPendientesOpened: () -> Unit = {},
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
    var pendingProcessConfirmation by remember { mutableStateOf<TrialSmsConfirmParams?>(null) }
    var duplicateConfirmRequest by remember { mutableStateOf<TrialSmsRequest?>(null) }
    var generatedFileToShare by remember { mutableStateOf<File?>(null) }
    var successBusinessRecord by remember { mutableStateOf<BusinessRecord?>(null) }

    // Informational, Warning and Error Dialogs for SMS Scanning
    var scanInfoDialogMessage by remember { mutableStateOf<String?>(null) }
    var scanDuplicateWarningMessage by remember { mutableStateOf<String?>(null) }
    var scanErrorDialogMessage by remember { mutableStateOf<String?>(null) }

    fun processScanResults(items: List<TrialSmsRequest>, manualClick: Boolean) {
        if (manualClick) {
            showScanOverlay = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            when (val result = SuperAdminSmsHelper.scanInboxForTrialRequestsWithStatus(context)) {
                is SuperAdminSmsHelper.ScanStatus.Success -> {
                    trialRequests = result.items
                    processScanResults(result.items, manualClick = true)
                }
                is SuperAdminSmsHelper.ScanStatus.PermissionDenied -> {
                    scanErrorDialogMessage = result.message
                }
                is SuperAdminSmsHelper.ScanStatus.Error -> {
                    scanErrorDialogMessage = result.message
                }
            }
        } else {
            scanErrorDialogMessage = "Permiso de lectura de SMS denegado. No se pueden escanear los mensajes de la bandeja."
        }
    }

    fun doScan(manualClick: Boolean = false) {
        isScanning = true
        when (val result = SuperAdminSmsHelper.scanInboxForTrialRequestsWithStatus(context)) {
            is SuperAdminSmsHelper.ScanStatus.Success -> {
                trialRequests = result.items
                isScanning = false
                processScanResults(result.items, manualClick)
            }
            is SuperAdminSmsHelper.ScanStatus.PermissionDenied -> {
                isScanning = false
                if (manualClick) {
                    scanErrorDialogMessage = result.message
                }
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.SEND_SMS
                    )
                )
            }
            is SuperAdminSmsHelper.ScanStatus.Error -> {
                isScanning = false
                if (manualClick) {
                    scanErrorDialogMessage = result.message
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        doScan()
    }

    LaunchedEffect(autoOpenPendientes) {
        if (autoOpenPendientes) {
            doScan()
            showPendientesOverlay = true
            onPendientesOpened()
        }
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

    val handleRequestProcess: (TrialSmsRequest) -> Unit = { req ->
        val existing = SuperAdminSmsHelper.findExistingBusinessForTrialRequest(context, req)
        val isProcessed = SuperAdminSmsHelper.isSmsProcessed(context, req.uniqueKey)
        if (existing != null || isProcessed) {
            duplicateConfirmRequest = req
        } else {
            requestToReview = req
        }
    }

    // OVERLAY 1: ESCANEAR DIALOG
    if (showScanOverlay) {
        TrialSmsScanOverlayDialog(
            trialRequests = trialRequests,
            onProcessRequest = handleRequestProcess,
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
            onProcess = handleRequestProcess,
            onDismiss = { showPendientesOverlay = false }
        )
    }

    // OVERLAY 3: CONFIRMADAS DIALOG
    if (showConfirmadasOverlay) {
        TrialSmsConfirmadasOverlayDialog(
            confirmedList = confirmedList,
            onBusinessDeleted = {
                doScan()
                onBusinessCreatedOrUpdated()
            },
            onDismiss = { showConfirmadasOverlay = false }
        )
    }

    // DUPLICATE CONFIRMATION DIALOG BEFORE REVIEWING
    duplicateConfirmRequest?.let { req ->
        val existing = SuperAdminSmsHelper.findExistingBusinessForTrialRequest(context, req)
        AlertDialog(
            onDismissRequest = { duplicateConfirmRequest = null },
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
                    text = "Negocio Ya Registrado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                val detail = if (existing != null) {
                    "La solicitud para '${req.nombreNegocio}' (DVC: ${req.dvc.ifBlank { "N/A" }}) corresponde al negocio registrado [${existing.code}] '${existing.name}' (${existing.status})."
                } else {
                    "La solicitud para '${req.nombreNegocio}' (DVC: ${req.dvc.ifBlank { "N/A" }}) ya fue procesada anteriormente."
                }
                Text(
                    text = "$detail\n\n¿Desea revisar y procesarla de todos modos?\n\n• No se duplicará el registro del negocio.\n• Se mantendrán y actualizarán los datos existentes.",
                    fontSize = 13.sp,
                    color = Slate700,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedReq = req
                        duplicateConfirmRequest = null
                        requestToReview = selectedReq
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Procesar de todos modos", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { duplicateConfirmRequest = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // REVIEW & PROCESS CONFIRMATION DIALOG
    requestToReview?.let { req ->
        TrialSmsReviewConfirmDialog(
            request = req,
            onConfirm = { name, dueno, mp, ma, ciudad, dvc ->
                pendingProcessConfirmation = TrialSmsConfirmParams(
                    req = req,
                    name = name,
                    dueno = dueno,
                    mp = mp,
                    ma = ma,
                    ciudad = ciudad,
                    dvc = dvc
                )
            },
            onDismiss = { requestToReview = null }
        )
    }

    // FINAL DIALOG CONFIRMATION
    pendingProcessConfirmation?.let { params ->
        AlertDialog(
            onDismissRequest = { pendingProcessConfirmation = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirmar Procesamiento",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = "¿Desea procesar estas solicitudes?\n\nAl confirmar, se iniciará el período de prueba de 7 días y se enviará el SMS de confirmación al negocio.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = params
                        pendingProcessConfirmation = null
                        coroutineScope.launch {
                            val result = SuperAdminSmsHelper.processTrialRequest(
                                context = context,
                                request = p.req,
                                customName = p.name,
                                customDueno = p.dueno,
                                customMp = p.mp,
                                customMa = p.ma,
                                customCiudad = p.ciudad,
                                customDvc = p.dvc
                            )
                            requestToReview = null
                            doScan()
                            onBusinessCreatedOrUpdated()
                            successBusinessRecord = result.first
                            generatedFileToShare = result.second
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sí, procesar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingProcessConfirmation = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // SUCCESS & SHARE OVERLAY
    if (successBusinessRecord != null && generatedFileToShare != null) {
        TrialSmsSuccessOverlayDialog(
            business = successBusinessRecord!!,
            file = generatedFileToShare!!,
            onSendConfirmationSms = {
                val smsText = SuperAdminSmsHelper.generateConfirmationSmsText(successBusinessRecord!!)
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

    // Informational Scan Dialog
    scanInfoDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanInfoDialogMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Información de Escaneo SMS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = Slate700,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { scanInfoDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Duplicate Business Warning Dialog
    scanDuplicateWarningMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanDuplicateWarningMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber600,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Negocio Ya Registrado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Amber800
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    color = Slate700,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { scanDuplicateWarningMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Aceptar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Scan Error Dialog
    scanErrorDialogMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { scanErrorDialogMessage = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Rose600,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Error al Escanear SMS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Rose700
                )
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    color = Slate700,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { scanErrorDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Cuadro superpuesto amplio y legible para ESCANEAR
 */
@Composable
private fun TrialSmsScanOverlayDialog(
    trialRequests: List<TrialSmsRequest>,
    onProcessRequest: (TrialSmsRequest) -> Unit,
    onRescan: () -> Unit,
    onOpenPendientes: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalCount = trialRequests.size
    val pendingCount = remember(trialRequests) {
        trialRequests.count { !SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
    }
    val confirmedCount = remember(trialRequests) {
        trialRequests.count { SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
    }

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
                                text = "Bandeja SMS • $totalCount solicitudes detectadas",
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
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$pendingCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Amber800
                            )
                            Text(
                                text = "PENDIENTES",
                                fontSize = 10.sp,
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
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$confirmedCount",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                            Text(
                                text = "CONFIRMADAS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald700
                            )
                        }
                    }
                }

                // Scanned SMS List or Empty Notice
                if (trialRequests.isEmpty()) {
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
                                imageVector = Icons.Default.MarkEmailRead,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No se encontraron mensajes SMS con el formato esperado de solicitud de prueba ('SOLICITUD PRUEBA ELQADRE...').",
                                fontSize = 13.sp,
                                color = Slate600,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Solicitudes SMS Encontradas para Revisión:",
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
                        items(trialRequests) { req ->
                            val existing = SuperAdminSmsHelper.findExistingBusinessForTrialRequest(context, req)
                            val isProcessed = SuperAdminSmsHelper.isSmsProcessed(context, req.uniqueKey)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (existing != null || isProcessed) Amber200 else Emerald200
                                ),
                                shadowElevation = 1.dp,
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
                                        Text(
                                            text = req.nombreNegocio.ifBlank { "Negocio Solicitante" },
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ElQadreNavy,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (existing != null) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Amber100
                                            ) {
                                                Text(
                                                    text = "YA REGISTRADO [${existing.code}]",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Amber800,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else if (isProcessed) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Slate200
                                            ) {
                                                Text(
                                                    text = "PROCESADO",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Slate700,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Emerald100
                                            ) {
                                                Text(
                                                    text = "NUEVA SOLICITUD",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Emerald800,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = Slate100)

                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(text = "• Dueño: ${req.dueno.ifBlank { "N/A" }}", fontSize = 12.sp, color = Slate700)
                                        Text(text = "• DVC: ${req.dvc.ifBlank { "N/A" }}", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                                        Text(text = "• Remitente / MP: ${req.movilPrincipal.ifBlank { req.senderAddress }}", fontSize = 12.sp, color = Slate700)
                                        if (req.ciudad.isNotBlank()) {
                                            Text(text = "• Ciudad: ${req.ciudad}", fontSize = 12.sp, color = Slate700)
                                        }
                                    }

                                    Button(
                                        onClick = { onProcessRequest(req) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (existing != null || isProcessed) Amber700 else ElQadreNavy
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (existing != null || isProcessed) "Revisar / Procesar" else "Procesar Solicitud",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Footer
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

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.movilPrincipal.ifBlank { request.senderAddress }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                if (request.movilAlternativo.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞 MA (Alternativo): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.movilAlternativo), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
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
                    Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.senderAddress), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Slate700)
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
    onBusinessDeleted: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allBusinesses = remember { SuperAdminBusinessManager.getBusinesses(context) }
    var itemToDelete by remember { mutableStateOf<Pair<TrialSmsRequest, BusinessRecord?>?>(null) }

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
                                business = matchedBiz,
                                onDelete = {
                                    itemToDelete = Pair(item, matchedBiz)
                                }
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

    // Modal de confirmación para eliminar negocio confirmado
    if (itemToDelete != null) {
        val (req, biz) = itemToDelete!!
        val bizName = biz?.name ?: req.nombreNegocio
        val bizCode = biz?.code ?: req.businessCode

        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = Rose600,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Eliminar Negocio Confirmado",
                    fontWeight = FontWeight.Bold,
                    color = Rose600,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Está seguro de que desea eliminar definitivamente el negocio '$bizName'${if (bizCode.isNotBlank()) " (Código: $bizCode)" else ""}?",
                        fontSize = 13.sp,
                        color = Slate800
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("• Se eliminará del registro de negocios.", fontSize = 12.sp, color = Slate700)
                            Text("• El número de negocio ($bizCode) quedará LIBRE para ser reutilizado inmediatamente.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                            Text("• Los demás negocios conservarán sus números intactos.", fontSize = 12.sp, color = Slate700)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetCode = biz?.code ?: req.businessCode
                        coroutineScope.launch {
                            if (targetCode.isNotBlank()) {
                                SuperAdminBusinessManager.deleteBusiness(context, targetCode)
                                SuperAdminSmsHelper.removeBusinessFromPruebasJson(context, targetCode, req.dvc)
                            } else {
                                val found = SuperAdminBusinessManager.getBusinesses(context).firstOrNull {
                                    it.name.equals(req.nombreNegocio, ignoreCase = true) ||
                                            (req.movilPrincipal.isNotBlank() && it.phone == req.movilPrincipal) ||
                                            (req.dvc.isNotBlank() && it.dvc.equals(req.dvc, ignoreCase = true))
                                }
                                if (found != null) {
                                    SuperAdminBusinessManager.deleteBusiness(context, found.code)
                                    SuperAdminSmsHelper.removeBusinessFromPruebasJson(context, found.code, req.dvc)
                                }
                            }
                            SuperAdminSmsHelper.markSmsAsDeleted(context, req.uniqueKey)
                            itemToDelete = null
                            onBusinessDeleted()
                            Toast.makeText(context, "Negocio eliminado. Número libre para ser reutilizado.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Eliminar definitivamente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancelar", color = Slate600)
                }
            }
        )
    }
}

/**
 * Tarjeta individual para solicitud confirmada dentro del cuadro superpuesto
 */
@Composable
private fun ConfirmedTrialRequestCard(
    request: TrialSmsRequest,
    business: BusinessRecord?,
    onDelete: () -> Unit = {}
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
                    Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, business?.phone ?: request.movilPrincipal), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                val altText = business?.phoneAlt?.ifBlank { request.movilAlternativo } ?: request.movilAlternativo
                if (altText.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞 MA (Alternativo): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, altText), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
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

            // Actions Row: Resend confirmation SMS & Delete Business
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val phone = business?.phone ?: request.movilPrincipal
                        val name = business?.name ?: request.nombreNegocio
                        val code = business?.code ?: ""
                        val smsText = "ELQADRE: Su solicitud de prueba para '$name' ha sido CONFIRMADA por 7 dias. Codigo de negocio: $code."
                        SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, phone, smsText)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(15.dp), tint = ElQadreNavy)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SMS Confirmación", fontSize = 11.sp, color = ElQadreNavy)
                }

                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose300),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Eliminar", modifier = Modifier.size(15.dp), tint = Rose600)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Eliminar", fontSize = 11.sp, color = Rose600, fontWeight = FontWeight.Bold)
                }
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
    val nextCode = remember { if (request.businessCode.isNotBlank()) request.businessCode else SuperAdminBusinessManager.getNextBusinessCode(context) }

    var name by remember { mutableStateOf(request.nombreNegocio) }
    var dueno by remember { mutableStateOf(request.dueno) }
    var mp by remember { mutableStateOf(request.movilPrincipal.ifBlank { request.senderAddress }) }
    var ma by remember { mutableStateOf(request.movilAlternativo) }
    var ciudad by remember { mutableStateOf(request.ciudad) }
    var dvc by remember { mutableStateOf(request.dvc) }

    val token = remember { request.token.ifBlank { SuperAdminBusinessManager.generateUniqueToken(context) } }
    val (defaultUser, defaultPass) = remember(dueno, nextCode) {
        SuperAdminBusinessManager.generateOwnerCredentials(dueno, nextCode)
    }
    val ownerUser = remember { request.ownerUsername.ifBlank { defaultUser } }
    val ownerPass = remember { request.ownerPassword.ifBlank { defaultPass } }

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
                            text = "SOLICITUD DE PRUEBA GRATIS RECIBIDA",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Número de negocio: [$nextCode] • Duración: 7 Días",
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

                // Warning Banner (Requirement 7)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Amber50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Amber200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Amber700, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Revise todos los datos antes de autorizar el inicio de la prueba.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Amber800
                        )
                    }
                }

                // Incomplete Request Warning if applicable
                if (request.isIncomplete && request.incompleteReason.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Atención: Solicitud incompleta o duplicada", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Rose700)
                            Text(request.incompleteReason, fontSize = 11.sp, color = Rose600)
                        }
                    }
                }

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
                    label = { Text("Dueño del Negocio *") },
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
                        label = { Text("Ciudad *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = dvc,
                        onValueChange = { dvc = it },
                        label = { Text("DVC *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Generated System Credentials Info Box
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Datos generados automáticamente:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Número de negocio:", fontSize = 11.sp, color = Slate600)
                            Text("[$nextCode]", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Token único:", fontSize = 11.sp, color = Slate600)
                            Text(token, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Amber700)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Usuario Dueño:", fontSize = 11.sp, color = Slate600)
                            Text(ownerUser, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Contraseña Inicial:", fontSize = 11.sp, color = Slate600)
                            Text(ownerPass, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
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
                            containerColor = Emerald600,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AUTORIZAR PRUEBA GRATIS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
