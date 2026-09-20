package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HourglassDisabled
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TimerOff
import kotlinx.coroutines.launch
import com.example.licensing.CommercialLicenseManager
import com.example.licensing.CommercialStatus
import com.example.data.local.model.UserRole
import com.example.util.ApkUpdateManager
import com.example.ui.components.StartupUpdateOverlay
import com.example.ui.components.StartupUpdateState
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.barra.BarraScreen
import com.example.ui.screens.cajero.CajeroScreen
import com.example.ui.screens.dueno.DuenoScreen
import com.example.ui.screens.inicio.InicioScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.portada.PortadaScreen
import com.example.ui.screens.salon.SalonScreen
import com.example.ui.screens.superadmin.SuperAdminDashboardScreen
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElQadreTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ElQadreBackground
                ) {
                    ElQadreApp(viewModel = viewModel)
                }
            }
        }

    }
}


@Composable
fun ElQadreApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val licenseManager = remember { CommercialLicenseManager.getInstance(context) }
    val licenseInfo by licenseManager.licenseInfo.collectAsStateWithLifecycle()

    val isDeviceBoundAndAuthorized = remember(licenseInfo.status) {
        licenseInfo.status == CommercialStatus.PRUEBA_ACTIVA || licenseInfo.status == CommercialStatus.LICENCIA_ACTIVA
    }

    val isOfflineOverdue = remember(licenseInfo.lastCheckedTimestamp, licenseInfo.status) {
        licenseManager.isOfflineCheckRequired()
    }

    val isDeviceLinked = remember(licenseInfo.status) {
        licenseInfo.status != CommercialStatus.SIN_AUTORIZACION
    }

    // Requisito: Comprobación automática de actualizaciones de APK en segundo plano al iniciar ElQadre
    var startupUpdateState by remember { mutableStateOf<StartupUpdateState>(StartupUpdateState.Idle) }
    var hasCheckedStartupUpdate by rememberSaveable { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasCheckedStartupUpdate) return@LaunchedEffect
        hasCheckedStartupUpdate = true

        // 1. Comprobar si existe conexión a Internet
        val hasInternet = ApkUpdateManager.isNetworkAvailable(context)
        if (!hasInternet) {
            // Si no hay conexión, continuar normalmente sin mostrar ningún mensaje ni error
            startupUpdateState = StartupUpdateState.Idle
            return@LaunchedEffect
        }

        // 2. Si existe conexión, consultar version.json en segundo plano (sin mostrar "BUSCANDO ACTUALIZACIONES...")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                when (val result = ApkUpdateManager.checkUpdate(context)) {
                    is ApkUpdateManager.VersionCheckResult.UpdateAvailable -> {
                        val remoteInfo = result.remoteInfo
                        startupUpdateState = StartupUpdateState.ConfirmationRequired(
                            remoteInfo = remoteInfo,
                            localVersionCode = result.localVersionCode,
                            localVersionName = result.localVersionName
                        )
                    }
                    else -> {
                        // Si la aplicación está actualizada o la comprobación falla, continuar normalmente sin mensajes
                        startupUpdateState = StartupUpdateState.Idle
                    }
                }
            } catch (_: Exception) {
                startupUpdateState = StartupUpdateState.Idle
            }
        }
    }

    val startDownloadAndInstall: (ApkUpdateManager.RemoteVersionInfo, Long, String) -> Unit = { remoteInfo, localCode, localName ->
        coroutineScope.launch {
            startupUpdateState = StartupUpdateState.Downloading(
                remoteInfo = remoteInfo,
                localVersionCode = localCode,
                localVersionName = localName,
                progress = 0f,
                statusMessage = "Iniciando descarga de actualización..."
            )

            val apkFile = ApkUpdateManager.downloadApk(context, remoteInfo.apkUrl) { prog ->
                startupUpdateState = StartupUpdateState.Downloading(
                    remoteInfo = remoteInfo,
                    localVersionCode = localCode,
                    localVersionName = localName,
                    progress = prog,
                    statusMessage = "Descargando actualización... ${(prog * 100).toInt()}%"
                )
            }

            if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
                startupUpdateState = StartupUpdateState.Downloading(
                    remoteInfo = remoteInfo,
                    localVersionCode = localCode,
                    localVersionName = localName,
                    progress = 1f,
                    statusMessage = "Iniciando instalación oficial de APK..."
                )
                kotlinx.coroutines.delay(800)
                try {
                    ApkUpdateManager.launchApkInstall(context, apkFile)
                    startupUpdateState = StartupUpdateState.Idle
                } catch (e: Exception) {
                    startupUpdateState = StartupUpdateState.Error("Error al iniciar la instalación del APK: ${e.localizedMessage ?: "Error desconocido"}")
                }
            } else {
                startupUpdateState = StartupUpdateState.Error("Error al descargar la nueva versión de APK.\nContinuando con la versión actual.")
            }
        }
    }

    // Requisito 2: Comprobación en segundo plano cada vez que se abre la aplicación con conexión a Internet
    androidx.compose.runtime.LaunchedEffect(isDeviceLinked) {
        if (isDeviceLinked) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                licenseManager.executeBackgroundCheck(context, uiState.deviceId)
            }
        }
    }

    val isSuperAdminActive = remember { com.example.util.SuperAdminSessionManager.isSessionActive(context) }
    val savedSuperAdminUser = remember { com.example.util.SuperAdminSessionManager.getSessionUser(context) }

    var superAdminUser by rememberSaveable {
        mutableStateOf(if (isSuperAdminActive) (savedSuperAdminUser ?: "SuperAdmin") else null)
    }

    var currentScreen by rememberSaveable {
        mutableStateOf(
            if (isSuperAdminActive) {
                "SUPER_ADMIN"
            } else if (isDeviceLinked) {
                "LOGIN"
            } else {
                "PORTADA"
            }
        )
    }

    // Si existe una sesión Super Admin persistente válida, asegurar que se restaura directamente
    // sin mostrar PORTADA ni INICIO ante cualquier recreación de ciclo de vida
    if (isSuperAdminActive) {
        if (superAdminUser == null) {
            superAdminUser = savedSuperAdminUser ?: "SuperAdmin"
        }
        if (currentScreen == "PORTADA" || currentScreen == "INICIO") {
            currentScreen = "SUPER_ADMIN"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentUser = uiState.currentUser
        val handleLogout = {
            viewModel.logout()
            if (superAdminUser != null) {
                currentScreen = "SUPER_ADMIN"
            } else if (isDeviceLinked) {
                currentScreen = "LOGIN"
            } else {
                currentScreen = "PORTADA"
            }
        }

        if (currentUser == null) {
            when (currentScreen) {
                "PORTADA" -> {
                    PortadaScreen(
                        dvc = uiState.deviceId,
                        onTokenValidatedSuccess = {
                            currentScreen = "LOGIN"
                        },
                        onNoToken = {
                            currentScreen = "INICIO"
                        }
                    )
                }
                "INICIO" -> {
                    InicioScreen(
                        dvc = uiState.deviceId,
                        onNavigateToLogin = { currentScreen = "LOGIN" },
                        onNavigateToSuperAdmin = { username ->
                            com.example.util.SuperAdminSessionManager.saveSession(context, username)
                            superAdminUser = username
                            currentScreen = "SUPER_ADMIN"
                        }
                    )
                }
                "SUPER_ADMIN" -> {
                    SuperAdminDashboardScreen(
                        superAdminUser = superAdminUser ?: "SuperAdmin",
                        dvc = uiState.deviceId,
                        onEnterElQadre = { currentScreen = "LOGIN" },
                        onExit = {
                            com.example.util.SuperAdminSessionManager.clearSession(context)
                            superAdminUser = null
                            currentScreen = if (isDeviceLinked) "LOGIN" else "PORTADA"
                        }
                    )
                }
                else -> { // "LOGIN"
                    LoginScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLoginSuccess = {},
                        onBack = if (superAdminUser != null) {
                            { currentScreen = "SUPER_ADMIN" }
                        } else if (!isDeviceLinked) {
                            { currentScreen = "PORTADA" }
                        } else {
                            null
                        },
                        superAdminUser = superAdminUser
                    )
                }
            }
        } else {
            when (currentUser.role) {
                UserRole.DUENO, UserRole.ADMIN -> {
                    DuenoScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
                }
                UserRole.CAJERO -> {
                    CajeroScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
                }
                UserRole.SALON -> {
                    SalonScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
                }
                UserRole.BARRA -> {
                    BarraScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
                }
            }
        }

        // Blocking overlay when license/trial is expired, revoked, or offline > 7 days (only if linked and not under SuperAdmin view)
        if (isDeviceLinked && (!isDeviceBoundAndAuthorized || isOfflineOverdue) && superAdminUser == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clickable(enabled = false) {}, // Intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    com.example.ui.components.ElQadreBrandHeader(
                        logoSize = 140.dp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    val status = licenseInfo.status
                    val businessName = licenseInfo.businessName.ifBlank { "el Negocio" }
                    val endDate = licenseInfo.endDate

                    val title: String
                    val icon: androidx.compose.ui.graphics.vector.ImageVector
                    val iconColor: Color
                    val message: String

                    if (isOfflineOverdue) {
                        title = "COMPROBACIÓN OBLIGATORIA"
                        icon = Icons.Default.Lock
                        iconColor = Rose700
                        message = "Han transcurrido más de 7 días sin una comprobación comercial satisfactoria.\n\nDebe conectarse a Internet para verificar el estado de su ${if (status == CommercialStatus.PRUEBA_ACTIVA) "prueba" else "licencia"} comercial para el negocio \"$businessName\"."
                    } else {
                        when (status) {
                            CommercialStatus.PRUEBA_VENCIDA -> {
                                title = "PERÍODO DE PRUEBA VENCIDO"
                                icon = Icons.Default.HourglassDisabled
                                iconColor = Rose700
                                message = "El período de prueba gratis de 7 días para el negocio \"$businessName\" ha finalizado el $endDate.\n\nPara continuar utilizando ElQadre en este dispositivo, solicite al Dueño del negocio la adquisición de una Licencia Comercial Activa."
                            }
                            CommercialStatus.LICENCIA_VENCIDA -> {
                                title = "LICENCIA COMERCIAL VENCIDA"
                                icon = Icons.Default.TimerOff
                                iconColor = Rose700
                                message = "La licencia de uso comercial para el negocio \"$businessName\" ha vencido el $endDate.\n\nComuníquese con el Dueño del negocio para proceder con la renovación del plan."
                            }
                            CommercialStatus.LICENCIA_REVOCADA -> {
                                val motivo = if (licenseInfo.motivoRevocacion.isNotBlank()) {
                                    "Motivo: ${licenseInfo.motivoRevocacion}"
                                } else {
                                    "Motivo no especificado."
                                }
                                title = "LICENCIA REVOCADA"
                                icon = Icons.Default.Cancel
                                iconColor = Rose700
                                message = "La licencia comercial para \"$businessName\" ha sido revocada por la administración.\n\n$motivo\n\nSi considera que esto es un error, contacte al administrador de ElQadre."
                            }
                            else -> {
                                title = "ACCESO RESTRINGIDO"
                                icon = Icons.Default.Lock
                                iconColor = Rose700
                                message = "El dispositivo no cuenta con una autorización vigente y activa para el negocio \"$businessName\"."
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Rose50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Rose800,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Rose800,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    var isCheckingUpdate by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            coroutineScope.launch {
                                val result = licenseManager.executeActualizarFull(context, uiState.deviceId)
                                isCheckingUpdate = false
                                when (result) {
                                    is com.example.licensing.LicenseUpdateResult.Success -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            "¡Autorización actualizada con éxito!",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is com.example.licensing.LicenseUpdateResult.NoChange -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            result.message,
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is com.example.licensing.LicenseUpdateResult.Error -> {
                                        android.widget.Toast.makeText(
                                            context,
                                            result.message,
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("comprobar_actualizacion_button")
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("COMPROBANDO...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "COMPROBAR ACTUALIZACIÓN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }



        // Global Update Overlay and AlertDialog
        val statusText = uiState.updateStatusText
        if (statusText != null) {
            if (statusText == "Comprobando conexión..." || 
                statusText == "Conexión disponible." ||
                statusText == "Comprobando si corresponde realizar actualización..." ||
                statusText == "Actualización en curso...") {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            color = ElQadreGold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusText,
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                AlertDialog(
                    onDismissRequest = { viewModel.clearUpdateStatus() },
                    title = { 
                        Text(
                            "Actualización de Datos", 
                            fontWeight = FontWeight.Bold, 
                            color = ElQadreNavy
                        ) 
                    },
                    text = { 
                        Text(
                            statusText, 
                            color = Slate700
                        ) 
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearUpdateStatus() }) {
                            Text(
                                "Aceptar", 
                                color = ElQadreGoldDark, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    containerColor = Color.White
                )
            }
        }

        // Automatic startup APK update overlay
        StartupUpdateOverlay(
            state = startupUpdateState,
            onConfirmUpdate = {
                val current = startupUpdateState
                if (current is StartupUpdateState.ConfirmationRequired) {
                    startDownloadAndInstall(current.remoteInfo, current.localVersionCode, current.localVersionName)
                }
            },
            onDismissUpdate = {
                startupUpdateState = StartupUpdateState.Idle
            },
            onDismissError = {
                startupUpdateState = StartupUpdateState.Idle
            }
        )
    }
}

