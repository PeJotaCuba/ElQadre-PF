package com.example.ui.screens.inicio

import androidx.activity.compose.BackHandler

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.licensing.CommercialLicenseInfo
import com.example.licensing.CommercialLicenseManager
import com.example.licensing.CommercialStatus
import com.example.licensing.LicenseUpdateResult
import com.example.licensing.PagoConfirmadoClientInfo
import com.example.licensing.SuperAdminSmsHelper
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun InicioScreen(
    dvc: String,
    onNavigateToLogin: () -> Unit,
    onNavigateToSuperAdmin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val licenseManager = remember { CommercialLicenseManager.getInstance(context) }
    val licenseInfo by licenseManager.licenseInfo.collectAsStateWithLifecycle()

    // Periodically re-evaluate expiration on start
    LaunchedEffect(Unit) {
        licenseManager.checkCurrentExpiration()
        licenseManager.checkAndSendExpiryWarnings(context, dvc)
        licenseManager.checkAndSendClientExpiryWarnings(context, dvc)
    }

    var isCheckingAuth by remember { mutableStateOf(false) }
    var isCheckingActualizar by remember { mutableStateOf(false) }
    var showInitialConfigDialog by remember { mutableStateOf(false) }
    var activeSuperAdminRecord by remember { mutableStateOf<SuperAdminRecord?>(null) }
    var showUnauthorizedDialog by remember { mutableStateOf(false) }
    var errorDialogMessage by remember { mutableStateOf<String?>(null) }

    var showAutorizacionDialog by remember { mutableStateOf(false) }
    var showActivacionDialog by remember { mutableStateOf(false) }
    var showPagoNoConfirmadoDialog by remember { mutableStateOf(false) }
    var clientPagoConfirmadoInfo by remember { mutableStateOf<PagoConfirmadoClientInfo?>(null) }
    var actualizarResult by remember { mutableStateOf<LicenseUpdateResult?>(null) }
    var showInicioGuiado by remember {
        mutableStateOf(!GuiadoPrefsManager.isInicioGuideShown(context))
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    var remoteVersionName by remember { mutableStateOf("") }
    var remoteVersionCode by remember { mutableStateOf(0L) }
    var localVersionName by remember { mutableStateOf("") }
    var localVersionCode by remember { mutableStateOf(0L) }
    var remoteApkUrl by remember { mutableStateOf("") }
    var updateNotes by remember { mutableStateOf("") }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    val isAnyInicioModalOpen = showInitialConfigDialog ||
            showUnauthorizedDialog ||
            errorDialogMessage != null ||
            showAutorizacionDialog ||
            showActivacionDialog ||
            showPagoNoConfirmadoDialog ||
            actualizarResult != null ||
            showAppUpdateDialog

    BackHandler(enabled = true) {
        when {
            showInitialConfigDialog -> showInitialConfigDialog = false
            showUnauthorizedDialog -> showUnauthorizedDialog = false
            errorDialogMessage != null -> errorDialogMessage = null
            showAutorizacionDialog -> showAutorizacionDialog = false
            showActivacionDialog -> showActivacionDialog = false
            showPagoNoConfirmadoDialog -> showPagoNoConfirmadoDialog = false
            actualizarResult != null -> actualizarResult = null
            showAppUpdateDialog -> showAppUpdateDialog = false
            else -> onNavigateToLogin()
        }
    }

    val pruebaSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasRead = permissions[Manifest.permission.READ_SMS] == true
        coroutineScope.launch {
            if (hasRead) {
                val confirmed = licenseManager.checkAndProcessInboxTrialConfirmation(context)
                if (confirmed) {
                    onNavigateToLogin()
                    return@launch
                }
            }
            showAutorizacionDialog = true
        }
    }

    fun handlePruebaGratisClick() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasReadPermission) {
            coroutineScope.launch {
                val confirmed = licenseManager.checkAndProcessInboxTrialConfirmation(context)
                if (confirmed) {
                    onNavigateToLogin()
                } else {
                    showAutorizacionDialog = true
                }
            }
        } else {
            pruebaSmsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
                )
            )
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val pagoConfirmado = SuperAdminSmsHelper.checkClientPagoConfirmado(context)
        if (pagoConfirmado != null) {
            clientPagoConfirmadoInfo = pagoConfirmado
            showActivacionDialog = true
        } else {
            showPagoNoConfirmadoDialog = true
        }
    }

    fun handleActivacionClick() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasReadPermission) {
            val pagoConfirmado = SuperAdminSmsHelper.checkClientPagoConfirmado(context)
            if (pagoConfirmado != null) {
                clientPagoConfirmadoInfo = pagoConfirmado
                showActivacionDialog = true
            } else {
                showPagoNoConfirmadoDialog = true
            }
        } else {
            smsPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top background banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(ElQadreNavy)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Official Logo with secret 5-second long-press detection
                Box(
                    modifier = Modifier
                        .pointerInput(dvc) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val released = withTimeoutOrNull(5000L) {
                                    waitForUpOrCancellation()
                                }
                                if (released == null) {
                                    // Held 5 seconds -> trigger Super Admin check automatically
                                    coroutineScope.launch {
                                        isCheckingAuth = true
                                        val result = SuperAdminAuthService.verifyDeviceAuthorization(context, dvc)
                                        isCheckingAuth = false
                                        when (result) {
                                            is SuperAdminValidationResult.Authorized -> {
                                                activeSuperAdminRecord = result.adminRecord
                                            }
                                            is SuperAdminValidationResult.DeviceNotConfigured -> {
                                                showInitialConfigDialog = true
                                            }
                                            is SuperAdminValidationResult.DeviceInactive -> {
                                                showUnauthorizedDialog = true
                                            }
                                            is SuperAdminValidationResult.ConnectionOrFormatError -> {
                                                errorDialogMessage = result.message
                                            }
                                        }
                                    }
                                    waitForUpOrCancellation()
                                }
                            }
                        }
                        .padding(12.dp)
                        .testTag("inicio_logo_container"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "ElQadre Logo",
                        modifier = Modifier
                            .height(130.dp)
                            .fillMaxWidth(0.85f),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Welcome & Commercial Status Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Bienvenido a ElQadre",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sistema integral de punto de venta y gestión comercial",
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Status Badge
                        CommercialStatusBadge(licenseInfo = licenseInfo)

                        Spacer(modifier = Modifier.height(24.dp))

                        // Commercial Action Button: PRUEBA GRATIS, ACTIVACIÓN, or INICIAR SESIÓN
                        when (licenseInfo.status) {
                            CommercialStatus.SIN_AUTORIZACION -> {
                                Button(
                                    onClick = { handlePruebaGratisClick() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElQadreNavy,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("prueba_gratis_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PRUEBA GRATIS",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            CommercialStatus.PRUEBA_VENCIDA,
                            CommercialStatus.LICENCIA_VENCIDA -> {
                                Button(
                                    onClick = { handleActivacionClick() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Rose700,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("activacion_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VpnKey,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "ACTIVACIÓN",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            CommercialStatus.LICENCIA_REVOCADA -> {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Rose700,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("licencia_revocada_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Lock,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "LICENCIA REVOCADA",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }

                            CommercialStatus.PRUEBA_ACTIVA,
                            CommercialStatus.LICENCIA_ACTIVA -> {
                                Button(
                                    onClick = onNavigateToLogin,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElQadreNavy,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("iniciar_sesion_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "INICIAR SESIÓN",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Bottom Central Section: Big ACTUALIZAR button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(0.92f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                isCheckingUpdate = true
                                coroutineScope.launch {
                                    try {
                                        when (val result = com.example.util.ApkUpdateManager.checkUpdate(context)) {
                                            is com.example.util.ApkUpdateManager.VersionCheckResult.UpdateAvailable -> {
                                                val remoteInfo = result.remoteInfo
                                                remoteApkUrl = remoteInfo.apkUrl
                                                remoteVersionName = remoteInfo.versionName.ifBlank { "v${remoteInfo.versionCode}" }
                                                remoteVersionCode = remoteInfo.versionCode
                                                localVersionName = result.localVersionName
                                                localVersionCode = result.localVersionCode
                                                updateNotes = remoteInfo.notes
                                                showAppUpdateDialog = true
                                            }
                                            is com.example.util.ApkUpdateManager.VersionCheckResult.AlreadyUpToDate -> {
                                                Toast.makeText(
                                                    context,
                                                    "La aplicación está actualizada.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            is com.example.util.ApkUpdateManager.VersionCheckResult.Error -> {
                                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error de conexión al verificar actualización: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreGold,
                                contentColor = ElQadreNavy
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("inicio_actualizar_button")
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(
                                    color = ElQadreNavy,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "CONSULTANDO...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ACTUALIZAR",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Buscar, descargar e instalar nuevas versiones de la aplicación ElQadre",
                            fontSize = 11.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "ElQadre POS",
                    fontSize = 12.sp,
                    color = Slate500
                )
            }
        }
    }

    // Super Admin Flow Dialogs
    if (isCheckingAuth) {
        SuperAdminCheckingDialog()
    }

    if (showInitialConfigDialog) {
        SuperAdminConfigDialog(
            dvc = dvc,
            onDismiss = { showInitialConfigDialog = false }
        )
    }

    if (showUnauthorizedDialog) {
        SuperAdminUnauthorizedDialog(
            onDismiss = { showUnauthorizedDialog = false }
        )
    }

    if (errorDialogMessage != null) {
        SuperAdminErrorDialog(
            message = errorDialogMessage!!,
            onDismiss = { errorDialogMessage = null }
        )
    }

    activeSuperAdminRecord?.let { record ->
        SuperAdminLoginDialog(
            superAdminRecord = record,
            onSuccess = { username ->
                activeSuperAdminRecord = null
                onNavigateToSuperAdmin(username)
            },
            onDismiss = {
                activeSuperAdminRecord = null
            }
        )
    }

    // Commercial Flow Dialogs
    if (showAutorizacionDialog) {
        SolicitudAutorizacionDialog(
            dvc = dvc,
            onDismiss = { showAutorizacionDialog = false }
        )
    }

    if (showActivacionDialog) {
        SolicitudActivacionDialog(
            dvc = dvc,
            pagoInfo = clientPagoConfirmadoInfo,
            onDismiss = {
                showActivacionDialog = false
                clientPagoConfirmadoInfo = null
            }
        )
    }

    if (showPagoNoConfirmadoDialog) {
        PagoNoConfirmadoDialog(
            onDismiss = { showPagoNoConfirmadoDialog = false }
        )
    }

    actualizarResult?.let { result ->
        ActualizarResultDialog(
            result = result,
            onDismiss = { actualizarResult = null }
        )
    }

    if (showInicioGuiado) {
        InicioGuiadoDialog(
            onDismiss = {
                GuiadoPrefsManager.setInicioGuideShown(context)
                showInicioGuiado = false
            }
        )
    }

    // Shared App Update Dialog
    com.example.ui.components.AppUpdateDialog(
        show = showAppUpdateDialog,
        installedVersionName = localVersionName,
        installedVersionCode = localVersionCode,
        remoteVersionName = remoteVersionName,
        remoteVersionCode = remoteVersionCode,
        notes = updateNotes,
        isDownloading = isDownloadingUpdate,
        downloadProgress = downloadProgress,
        onConfirmUpdate = {
            isDownloadingUpdate = true
            downloadProgress = 0f
            coroutineScope.launch {
                val file = com.example.util.ApkUpdateManager.downloadApk(context, remoteApkUrl) { progress ->
                    downloadProgress = progress
                }
                isDownloadingUpdate = false
                if (file != null && file.exists() && file.length() > 0) {
                    showAppUpdateDialog = false
                    Toast.makeText(context, "Descarga completada. Preparando instalación...", Toast.LENGTH_SHORT).show()
                    try {
                        com.example.util.ApkUpdateManager.launchApkInstall(context, file)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al abrir instalador: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(context, "Error al descargar la actualización. Verifique su conexión.", Toast.LENGTH_LONG).show()
                    showAppUpdateDialog = false
                }
            }
        },
        onDismiss = {
            if (!isDownloadingUpdate) {
                showAppUpdateDialog = false
            }
        }
    )
}

@Composable
private fun CommercialStatusBadge(licenseInfo: CommercialLicenseInfo) {
    val (badgeBg, badgeText, badgeColor) = when (licenseInfo.status) {
        CommercialStatus.SIN_AUTORIZACION -> {
            Triple(
                Slate100,
                "Estado: Sin Prueba Activa",
                Slate700
            )
        }
        CommercialStatus.PRUEBA_ACTIVA -> {
            val dateInfo = if (licenseInfo.endDate.isNotBlank()) " (hasta ${licenseInfo.endDate})" else ""
            Triple(
                Emerald50,
                "Prueba de 7 Días Activa$dateInfo",
                Emerald700
            )
        }
        CommercialStatus.PRUEBA_VENCIDA -> {
            Triple(
                Rose50,
                "Período de Prueba Vencido",
                Rose700
            )
        }
        CommercialStatus.LICENCIA_ACTIVA -> {
            val typeInfo = if (licenseInfo.licenseType.isNotBlank()) "${licenseInfo.licenseType} " else ""
            val dateInfo = if (licenseInfo.endDate.isNotBlank()) " (hasta ${licenseInfo.endDate})" else ""
            Triple(
                Emerald50,
                "Licencia ${typeInfo}Activa$dateInfo",
                Emerald700
            )
        }
        CommercialStatus.LICENCIA_VENCIDA -> {
            Triple(
                Rose50,
                "Licencia Comercial Vencida",
                Rose700
            )
        }
        CommercialStatus.LICENCIA_REVOCADA -> {
            val motivoInfo = if (licenseInfo.motivoRevocacion.isNotBlank()) "\nMotivo: ${licenseInfo.motivoRevocacion}" else ""
            val fechaInfo = if (licenseInfo.fechaRevocacion.isNotBlank()) " (${licenseInfo.fechaRevocacion})" else ""
            Triple(
                Rose100,
                "⛔ Licencia Revocada por la Administración$fechaInfo$motivoInfo",
                Rose800
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = badgeBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = badgeText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = badgeColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
