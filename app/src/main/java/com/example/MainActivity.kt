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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.local.model.UserRole
import com.example.util.ApkUpdateManager
import com.example.ui.components.StartupUpdateOverlay
import com.example.ui.components.StartupUpdateState
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.barra.BarraScreen
import com.example.ui.screens.cajero.CajeroScreen
import com.example.ui.screens.dueno.DuenoScreen
import com.example.ui.screens.login.LoginScreen
import com.example.ui.screens.salon.SalonScreen
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

    // Comprobación automática de actualizaciones de APK en segundo plano al iniciar ElQadre
    var startupUpdateState by remember { mutableStateOf<StartupUpdateState>(StartupUpdateState.Idle) }
    var hasCheckedStartupUpdate by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.READ_SMS] == true) {
            viewModel.scanPendingAccountSms()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.READ_SMS)
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.RECEIVE_SMS)
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.SEND_SMS)
        }
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (hasCheckedStartupUpdate) return@LaunchedEffect
        hasCheckedStartupUpdate = true

        // 1. Comprobar si existe conexión a Internet
        val hasInternet = ApkUpdateManager.isNetworkAvailable(context)
        if (!hasInternet) {
            startupUpdateState = StartupUpdateState.Idle
            return@LaunchedEffect
        }

        // 2. Si existe conexión, consultar version.json en segundo plano
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

    Box(modifier = Modifier.fillMaxSize()) {
        val currentUser = uiState.currentUser
        val handleLogout = {
            viewModel.logout()
        }

        if (currentUser == null) {
            LoginScreen(
                uiState = uiState,
                viewModel = viewModel,
                onLoginSuccess = {},
                onBack = null
            )
        } else {
            when (currentUser.role) {
                UserRole.ADMIN -> {
                    AdminDashboardScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
                }
                UserRole.DUENO -> {
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
                UserRole.SALON, UserRole.DEPENDIENTE -> {
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
                UserRole.COCINA -> {
                    SalonScreen(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLogout = handleLogout
                    )
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
