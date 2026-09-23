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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Celebration
import com.example.data.local.model.User
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

    var showWelcomeUser by remember { mutableStateOf<User?>(null) }

    androidx.compose.runtime.LaunchedEffect(uiState.currentUser) {
        val user = uiState.currentUser
        if (user != null) {
            val welcomePrefs = context.getSharedPreferences("elqadre_welcome_prefs", android.content.Context.MODE_PRIVATE)
            val alreadyShown = welcomePrefs.getBoolean("welcome_shown_${user.username}", false)
            if (!alreadyShown) {
                showWelcomeUser = user
            }
        } else {
            showWelcomeUser = null
        }
    }

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
        val adminBackup = uiState.adminBackupUser
        val handleLogout = {
            viewModel.logout()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (adminBackup != null) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    contentColor = Color(0xFF92400E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.exitTestAccount() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Text(
                                text = "MODO PRUEBA: Cuenta de ${currentUser?.username ?: ""} (${currentUser?.role?.displayName ?: ""})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "REGRESAR A ADMIN ✕",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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

        // First Login Welcome Message Dialog
        val welcomeUser = showWelcomeUser
        if (welcomeUser != null) {
            FirstLoginWelcomeDialog(
                username = welcomeUser.username,
                fullName = welcomeUser.fullName,
                role = welcomeUser.role,
                onDismiss = {
                    val welcomePrefs = context.getSharedPreferences("elqadre_welcome_prefs", android.content.Context.MODE_PRIVATE)
                    welcomePrefs.edit().putBoolean("welcome_shown_${welcomeUser.username}", true).apply()
                    showWelcomeUser = null
                }
            )
        }
    }
}

@Composable
fun FirstLoginWelcomeDialog(
    username: String,
    fullName: String,
    role: UserRole,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Celebration badge
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Celebration,
                            contentDescription = null,
                            tint = ElQadreGoldDark,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Text(
                    text = "¡Te damos la bienvenida!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = fullName.ifBlank { username },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreGoldDark,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ElQadreNavy.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Rol asignado: ${role.displayName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                        val welcomeDesc = when (role) {
                            UserRole.ADMIN -> "Como Administrador, tienes control completo sobre los usuarios, configuraciones globales y base de datos de ElQadre."
                            UserRole.DUENO -> "Como Dueño, tienes acceso completo al Cuadre de Caja, Reportes de Utilidad, Fichas de Costo y Control General del Negocio."
                            UserRole.CAJERO -> "Como Cajero, puedes realizar aperturas, arqueos de caja, conteos rápidos y registrar transacciones diarias."
                            UserRole.SALON -> "Como Dependiente de Salón, puedes registrar pedidos de mesas, gestionar comandas y agilizar la atención al cliente."
                            UserRole.DEPENDIENTE -> "Como Dependiente, tienes acceso a la toma de comandas, ventas rápidas y atención directa al cliente."
                            UserRole.BARRA -> "Como Dependiente de Barra, puedes gestionar los pedidos dirigidos a la barra y despachar bebidas rápidamente."
                            UserRole.COCINA -> "Como Cocina, puedes ver los pedidos pendientes de preparación y notificar cuando estén listos para el salón."
                        }
                        Text(
                            text = welcomeDesc,
                            fontSize = 13.sp,
                            color = Slate700,
                            lineHeight = 18.sp
                        )
                    }
                }

                Text(
                    text = "ElQadre está optimizado para brindarte la mejor experiencia táctil, con botones grandes, textos legibles y cálculos en tiempo real.",
                    fontSize = 12.sp,
                    color = Slate500,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "EMPEZAR",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}
