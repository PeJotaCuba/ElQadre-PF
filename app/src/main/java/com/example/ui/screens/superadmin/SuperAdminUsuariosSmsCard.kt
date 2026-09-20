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
import com.example.licensing.SuperAdminSmsRequest
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
fun SuperAdminUsuariosSmsCard(
    modifier: Modifier = Modifier,
    onBusinessCreatedOrUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var trialRequests by remember { mutableStateOf<List<SuperAdminSmsRequest>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Dialog Overlays (Cuadros superpuestos amplios y legibles)
    var showScanOverlay by rememberSaveable { mutableStateOf(false) }
    var showPendientesOverlay by rememberSaveable { mutableStateOf(false) }
    var showConfirmadasOverlay by rememberSaveable { mutableStateOf(false) }

    // Review & Process Dialog
    var requestToReview by remember { mutableStateOf<SuperAdminSmsRequest?>(null) }
    var duplicateConfirmRequest by remember { mutableStateOf<SuperAdminSmsRequest?>(null) }
    var generatedFileToShare by remember { mutableStateOf<File?>(null) }
    var successBusinessRecord by remember { mutableStateOf<BusinessRecord?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context).filter { it.tipo == "NUEVO_USUARIO" }
        }
    }

    fun doScan() {
        isScanning = true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            trialRequests = SuperAdminSmsHelper.scanInboxForRequests(context).filter { it.tipo == "NUEVO_USUARIO" }
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
                        text = "SM USUARIOS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Validación de administradores y generación de Q_XXXusuarios.json",
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

    val handleRequestProcess: (SuperAdminSmsRequest) -> Unit = { req ->
        val isProcessed = SuperAdminSmsHelper.isSmsProcessed(context, req.uniqueKey)
        if (isProcessed) {
            duplicateConfirmRequest = req
        } else {
            requestToReview = req
        }
    }

    // OVERLAY 1: ESCANEAR DIALOG
    if (showScanOverlay) {
        TrialSmsScanOverlayDialog(
            userRequests = trialRequests,
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
            onDismiss = { showConfirmadasOverlay = false }
        )
    }

    // DUPLICATE CONFIRMATION DIALOG BEFORE REVIEWING
    duplicateConfirmRequest?.let { req ->
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
                    text = "Solicitud de Usuario Ya Procesada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Text(
                    text = "La solicitud de usuario '${req.nuevoRol.ifBlank { req.solicitante }}' para el negocio '${req.nombreNegocio}' (DVC: ${req.dvc.ifBlank { "N/A" }}) ya fue procesada anteriormente.\n\n¿Desea revisar y procesarla de todos modos?\n\n• No se duplicarán registros.\n• Se regenerará/actualizará el archivo de usuarios del negocio.",
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
        UsuariosProcessDialog(
            request = req,
            onApproved = { file ->
                coroutineScope.launch {
                    SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    generatedFileToShare = file
                }
            },
            onDismiss = { requestToReview = null }
        )
    }

    // SUCCESS & SHARE OVERLAY
    generatedFileToShare?.let { file ->
        FileGeneratedSuccessDialogLocal(
            file = file,
            onShare = {
                SuperAdminSmsHelper.shareJsonFile(context, file, "Compartir JSON")
            },
            onDismiss = {
                generatedFileToShare = null
            }
        )
    }
}

/**
 * Cuadro superpuesto amplio y legible para ESCANEAR USUARIOS
 */
@Composable
private fun TrialSmsScanOverlayDialog(
    userRequests: List<SuperAdminSmsRequest>,
    onProcessRequest: (SuperAdminSmsRequest) -> Unit,
    onRescan: () -> Unit,
    onOpenPendientes: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val totalCount = userRequests.size
    val pendingCount = remember(userRequests) {
        userRequests.count { !SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
    }
    val confirmedCount = remember(userRequests) {
        userRequests.count { SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
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
                                text = "ESCANEAR USUARIOS",
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
                if (userRequests.isEmpty()) {
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
                                text = "No se encontraron mensajes SMS con solicitudes de creación de usuarios en la bandeja.",
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
                        items(userRequests) { req ->
                            val isProcessed = SuperAdminSmsHelper.isSmsProcessed(context, req.uniqueKey)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isProcessed) Amber200 else Emerald200
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

                                        if (isProcessed) {
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
                                        Text(text = "• Rol Solicitado: ${req.nuevoRol.ifBlank { req.solicitante.ifBlank { "N/A" } }}", fontSize = 12.sp, color = Slate700, fontWeight = FontWeight.SemiBold)
                                        Text(text = "• DVC: ${req.dvc.ifBlank { "N/A" }}", fontSize = 12.sp, color = Slate700)
                                        Text(text = "• Remitente: ${req.numeroMovil.ifBlank { req.senderAddress }}", fontSize = 12.sp, color = Slate700)
                                    }

                                    Button(
                                        onClick = { onProcessRequest(req) },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isProcessed) Amber700 else ElQadreNavy
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
                                            text = if (isProcessed) "Revisar / Procesar" else "Procesar Usuario",
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
    pendingList: List<SuperAdminSmsRequest>,
    onProcess: (SuperAdminSmsRequest) -> Unit,
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
    request: SuperAdminSmsRequest,
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
                    Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.numeroMovil.ifBlank { request.senderAddress }), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                if (request.numeroMovilAlt.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📞 MA (Alternativo): ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate600)
                        Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.numeroMovilAlt), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate800)
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
    confirmedList: List<SuperAdminSmsRequest>,
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
                                        (item.numeroMovil.isNotBlank() && b.phone.equals(item.numeroMovil, ignoreCase = true))
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
    request: SuperAdminSmsRequest,
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
                    Text(ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, business?.phone ?: request.numeroMovil), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
                val altText = business?.phoneAlt?.ifBlank { request.numeroMovilAlt } ?: request.numeroMovilAlt
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

            // Action: Resend confirmation SMS
            OutlinedButton(
                onClick = {
                    val phone = business?.phone ?: request.numeroMovil
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
private fun UsuariosProcessDialog(
    request: SuperAdminSmsRequest,
    onApproved: (java.io.File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    var isProcessing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var initialPassword by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("123456") }

    val business = androidx.compose.runtime.remember {
        com.example.licensing.SuperAdminBusinessManager.getBusinessByDvc(context, request.dvc) 
            ?: com.example.licensing.SuperAdminBusinessManager.getBusinesses(context).firstOrNull { it.name.equals(request.nombreNegocio, ignoreCase = true) }
    }

    val isAdminValid = androidx.compose.runtime.remember {
        business?.users?.any { 
            it.role.equals("ADMINISTRADOR", ignoreCase = true) && 
            (it.phone.contains(request.senderAddress) || request.senderAddress.contains(it.phone))
        } ?: false
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Aprobar Nuevo Usuario",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.ElQadreNavy
                )
                
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = com.example.ui.theme.Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "DVC: ${request.dvc}", fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = com.example.ui.theme.ElQadreNavy)
                        Text(text = "Negocio: ${request.nombreNegocio}", fontSize = 13.sp, color = com.example.ui.theme.Slate800)
                        Text(text = "Solicitante: ${request.solicitante} (${ContactHelper.formatPhoneNumberWithContact(androidx.compose.ui.platform.LocalContext.current, request.senderAddress)})", fontSize = 13.sp, color = com.example.ui.theme.Slate800)
                    }
                }
                
                if (business == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = com.example.ui.theme.Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ No se encontró el negocio registrado para este DVC/Nombre.",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.Rose700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (!isAdminValid) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = com.example.ui.theme.Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ El número de remitente (${request.senderAddress}) no coincide con los administradores de este negocio.",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.Rose700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = com.example.ui.theme.Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Administrador validado correctamente.",
                            fontSize = 12.sp,
                            color = com.example.ui.theme.Emerald700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = com.example.ui.theme.Emerald50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Datos del Usuario Solicitado:", fontWeight = FontWeight.Bold, color = com.example.ui.theme.Emerald700, fontSize = 13.sp)
                        Text(text = "• Nombre: ${request.nuevoNombre}", fontSize = 12.sp, color = com.example.ui.theme.Slate800)
                        Text(text = "• Usuario: ${request.nuevoUsername}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = com.example.ui.theme.ElQadreNavy)
                        Text(text = "• Rol: ${request.nuevoRol}", fontSize = 12.sp, color = com.example.ui.theme.Slate800)
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = initialPassword,
                    onValueChange = { initialPassword = it },
                    label = { Text("Contraseña Inicial") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                if (business != null) {
                                    val (updatedBiz, userFile) = com.example.licensing.SuperAdminBusinessManager.approveUserRequest(
                                        context = context,
                                        business = business,
                                        req = request,
                                        initialPassword = initialPassword
                                    )
                                    isProcessing = false
                                    onApproved(userFile)
                                }
                            }
                        },
                        enabled = !isProcessing && isAdminValid && business != null,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = com.example.ui.theme.ElQadreNavy,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isProcessing) {
                            androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Aprobar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileGeneratedSuccessDialogLocal(
    file: java.io.File,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Archivo Generado Exitosamente") },
        text = { androidx.compose.material3.Text("Se ha guardado ${file.name}. ¿Desea compartirlo ahora?") },
        confirmButton = {
            androidx.compose.material3.Button(onClick = { onShare(); onDismiss() }) { androidx.compose.material3.Text("Compartir") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cerrar") }
        }
    )
}
