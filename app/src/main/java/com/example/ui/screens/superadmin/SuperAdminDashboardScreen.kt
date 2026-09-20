package com.example.ui.screens.superadmin

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.licensing.SuperAdminSmsHelper
import com.example.licensing.SuperAdminSmsRequest
import com.example.licensing.SuperAdminBusinessManager
import com.example.licensing.BusinessRecord
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class SuperAdminTab {
    NEGOCIOS,
    RASTREAR_SMS,
    PRUEBAS,
    LICENCIAS,
    CONFIGURACION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    superAdminUser: String,
    dvc: String,
    onEnterElQadre: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableStateOf(SuperAdminTab.NEGOCIOS) }
    var showSelectBusinessDialog by remember { mutableStateOf(false) }

    // Detección inmediata de solicitudes SMS de prueba
    var newTrialRequestAlert by remember { mutableStateOf<com.example.licensing.TrialSmsRequest?>(null) }
    var autoOpenPendientes by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.example.util.SuperAdminSmsBus.incomingTrialRequests.collect { req ->
            if (!req.isIncomplete) {
                newTrialRequestAlert = req
            }
        }
    }

    val formattedDvc = remember(dvc) {
        if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
    }

    val activity = context as? android.app.Activity
    BackHandler(enabled = true) {
        // Enviar app a segundo plano manteniendo la sesión activa
        activity?.moveTaskToBack(true)
    }

    val handleEnterWithBusiness: (com.example.licensing.BusinessRecord) -> Unit = { business ->
        coroutineScope.launch {
            val db = com.example.data.local.AppDatabase.getDatabase(context)
            com.example.licensing.SuperAdminBusinessManager.applyBusinessUsersToLocalDb(context, db, business)
            com.example.licensing.SuperAdminBusinessManager.setTestingBusiness(context, business)
            onEnterElQadre()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "SUPER ADMIN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Usuario: $superAdminUser | $formattedDvc",
                            fontSize = 12.sp,
                            color = Slate300
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showSelectBusinessDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElQadreGold,
                            contentColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("superadmin_enter_elqadre_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ENTRAR A ELQADRE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    FilledTonalButton(
                        onClick = onExit,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Rose800.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("superadmin_exit_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salir",
                            tint = Rose200,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SALIR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ElQadreNavy,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == SuperAdminTab.NEGOCIOS,
                    onClick = { selectedTab = SuperAdminTab.NEGOCIOS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == SuperAdminTab.NEGOCIOS) Icons.Filled.Storefront else Icons.Outlined.Storefront,
                            contentDescription = "Negocios"
                        )
                    },
                    label = { Text("Negocios", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElQadreNavy,
                        selectedTextColor = ElQadreNavy,
                        indicatorColor = ElQadreGoldSoft
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == SuperAdminTab.RASTREAR_SMS,
                    onClick = { selectedTab = SuperAdminTab.RASTREAR_SMS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == SuperAdminTab.RASTREAR_SMS) Icons.Filled.Sms else Icons.Outlined.Sms,
                            contentDescription = "Rastrear SMS"
                        )
                    },
                    label = { Text("SMS", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElQadreNavy,
                        selectedTextColor = ElQadreNavy,
                        indicatorColor = ElQadreGoldSoft
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == SuperAdminTab.PRUEBAS,
                    onClick = { selectedTab = SuperAdminTab.PRUEBAS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == SuperAdminTab.PRUEBAS) Icons.Filled.VerifiedUser else Icons.Outlined.VerifiedUser,
                            contentDescription = "Pruebas"
                        )
                    },
                    label = { Text("Pruebas 7D", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElQadreNavy,
                        selectedTextColor = ElQadreNavy,
                        indicatorColor = ElQadreGoldSoft
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == SuperAdminTab.LICENCIAS,
                    onClick = { selectedTab = SuperAdminTab.LICENCIAS },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == SuperAdminTab.LICENCIAS) Icons.Filled.VpnKey else Icons.Outlined.VpnKey,
                            contentDescription = "Licencias"
                        )
                    },
                    label = { Text("Licencias", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElQadreNavy,
                        selectedTextColor = ElQadreNavy,
                        indicatorColor = ElQadreGoldSoft
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == SuperAdminTab.CONFIGURACION,
                    onClick = { selectedTab = SuperAdminTab.CONFIGURACION },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == SuperAdminTab.CONFIGURACION) Icons.Filled.SystemUpdate else Icons.Outlined.SystemUpdate,
                            contentDescription = "Versiones / APK"
                        )
                    },
                    label = { Text("Versiones", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElQadreNavy,
                        selectedTextColor = ElQadreNavy,
                        indicatorColor = ElQadreGoldSoft
                    )
                )
            }
        },
        containerColor = ElQadreBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                SuperAdminTab.NEGOCIOS -> {
                    SuperAdminNegociosSection(
                        onEnterElQadreWithBusiness = handleEnterWithBusiness
                    )
                }
                SuperAdminTab.RASTREAR_SMS -> {
                    SuperAdminRastrearSmsSection(
                        autoOpenPendientes = autoOpenPendientes,
                        onPendientesOpened = { autoOpenPendientes = false }
                    )
                }
                SuperAdminTab.PRUEBAS -> {
                    SuperAdminPruebasSection()
                }
                SuperAdminTab.LICENCIAS -> {
                    SuperAdminLicenciasSection()
                }
                SuperAdminTab.CONFIGURACION -> {
                    SuperAdminConfiguracionSection(
                        superAdminUser = superAdminUser,
                        dvc = formattedDvc
                    )
                }
            }
        }
    }

    if (showSelectBusinessDialog) {
        val businesses = remember { com.example.licensing.SuperAdminBusinessManager.getBusinesses(context) }
        SelectBusinessForTestingDialog(
            businesses = businesses,
            onDismiss = { showSelectBusinessDialog = false },
            onSelectBusiness = { selectedBiz ->
                showSelectBusinessDialog = false
                handleEnterWithBusiness(selectedBiz)
            }
        )
    }

    // Cuadro de aviso inmediato al recibir solicitud de prueba por SMS
    newTrialRequestAlert?.let { req ->
        AlertDialog(
            onDismissRequest = { newTrialRequestAlert = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Nueva Solicitud de Prueba Recibida",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Se ha recibido una nueva solicitud de prueba de 7 días vía SMS:",
                        fontSize = 13.sp,
                        color = Slate700
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
                            Text(
                                text = "🏢 Negocio: ${req.nombreNegocio}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            if (req.dueno.isNotBlank()) {
                                Text("👤 Dueño: ${req.dueno}", fontSize = 12.sp, color = Slate700)
                            }
                            Text("📱 Teléfono (MP): ${req.movilPrincipal}", fontSize = 12.sp, color = Slate700)
                            if (req.dvc.isNotBlank()) {
                                Text("🔑 DVC: ${req.dvc}", fontSize = 12.sp, color = Slate700)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        newTrialRequestAlert = null
                        selectedTab = SuperAdminTab.RASTREAR_SMS
                        autoOpenPendientes = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("VER EN PENDIENTES", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { newTrialRequestAlert = null }) {
                    Text("Cerrar", color = Slate600)
                }
            }
        )
    }
}

@Composable
private fun SuperAdminRastrearSmsSection(
    autoOpenPendientes: Boolean = false,
    onPendientesOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<SuperAdminSmsRequest>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("PENDIENTES") } // "PENDIENTES", "TODAS"
    var activeProcessRequest by remember { mutableStateOf<SuperAdminSmsRequest?>(null) }
    var generatedFileToShare by remember { mutableStateOf<File?>(null) }
    var shareDialogTitle by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_SMS] == true ||
                permissions[Manifest.permission.RECEIVE_SMS] == true
        if (granted) {
            requests = SuperAdminSmsHelper.scanInboxForRequests(context)
        }
    }

    fun refreshRequests() {
        isScanning = true
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            requests = SuperAdminSmsHelper.scanInboxForRequests(context)
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


    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) refreshRequests()
        }
    )
    
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
        refreshRequests()
    }


    val filteredList = remember(requests, selectedFilter) {
        if (selectedFilter == "PENDIENTES") {
            requests.filter { !SuperAdminSmsHelper.isSmsProcessed(context, it.uniqueKey) }
        } else {
            requests
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dedicated Card for Trial SMS: ONLY ESCANEAR, PENDIENTES, CONFIRMADAS
        SuperAdminTrialSmsCard(
            autoOpenPendientes = autoOpenPendientes,
            onPendientesOpened = onPendientesOpened,
            onBusinessCreatedOrUpdated = {
                refreshRequests()
            }
        )

        // Dedicated Card for Payment SMS: ESCANEAR, PENDIENTES, CONFIRMADAS, AJUSTES
        SuperAdminPaymentSmsCard(
            onPaymentConfirmed = {
                refreshRequests()
            }
        )

        // Dedicated Card for Activation SMS
        SuperAdminActivationSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshRequests()
            }
        )
        
        // Dedicated Card for Usuarios SMS
        SuperAdminUsuariosSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshRequests()
            }
        )
        
    }

    // Process Dialog
    activeProcessRequest?.let { request ->
        ProcessSmsRequestDialog(
            request = request,
            onApproved = { file, title ->
                SuperAdminSmsHelper.markSmsAsProcessed(context, request.uniqueKey)
                activeProcessRequest = null
                generatedFileToShare = file
                shareDialogTitle = title
                refreshRequests()
            },
            onDismiss = { activeProcessRequest = null }
        )
    }

    // Share File Dialog
    generatedFileToShare?.let { file ->
        FileGeneratedSuccessDialog(
            file = file,
            title = shareDialogTitle,
            onShare = {
                SuperAdminSmsHelper.shareJsonFile(context, file, shareDialogTitle)
            },
            onDismiss = { generatedFileToShare = null }
        )
    }
}

@Composable
private fun SmsRequestCard(
    request: SuperAdminSmsRequest,
    isProcessed: Boolean,
    onProcess: () -> Unit
) {
    val isAutorizacion = request.tipo == "AUTORIZACION"
    val isNuevoUsuario = request.tipo == "NUEVO_USUARIO"
    val dateStr = remember(request.timestamp) {
        if (request.timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(request.timestamp))
        } else "Reciente"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isNuevoUsuario -> Emerald50
                        isAutorizacion -> ElQadreNavySoft
                        else -> Rose50
                    }
                ) {
                    Text(
                        text = when {
                            isNuevoUsuario -> "SOLICITUD NUEVO USUARIO"
                            isAutorizacion -> "SOLICITUD AUTORIZACIÓN (7D)"
                            else -> "SOLICITUD ACTIVACIÓN"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isNuevoUsuario -> Emerald700
                            isAutorizacion -> ElQadreNavy
                            else -> Rose700
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = request.dvc,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Negocio: ${request.nombreNegocio}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800
                    )
                    Text(
                        text = "Móvil: ${request.numeroMovil} (Remitente: ${request.senderAddress})",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                }
            }

            if (isNuevoUsuario) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Usuario solicitado: ${request.nuevoNombre} (@${request.nuevoUsername})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Rol: ${request.nuevoRol} • Móvil: ${request.nuevoTelefono.ifBlank { "N/A" }}",
                            fontSize = 12.sp,
                            color = Slate700
                        )
                        if (request.montoPorProducto > 0) {
                            Text(
                                text = "Monto por Producto: $${request.montoPorProducto}",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }
                        Text(
                            text = "Solicitante: ${request.solicitante.ifBlank { "Administrador / Dueño" }}",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessed) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Emerald50
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Procesada / Aprobada",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald700
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Amber50
                    ) {
                        Text(
                            text = "Pendiente de procesar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Amber800,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Button(
                    onClick = onProcess,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProcessed) Slate100 else ElQadreNavy,
                        contentColor = if (isProcessed) Slate700 else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isProcessed) "Re-procesar" else "Aprobar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessSmsRequestDialog(
    request: SuperAdminSmsRequest,
    onApproved: (File, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAutorizacion = request.tipo == "AUTORIZACION"
    val isNuevoUsuario = request.tipo == "NUEVO_USUARIO"

    var tipoLicencia by remember { mutableStateOf("PERMANENTE") }
    var duracionDias by remember { mutableIntStateOf(-1) }
    var initialPassword by remember { mutableStateOf("1234") }
    var isProcessing by remember { mutableStateOf(false) }

    val allBusinesses = remember { SuperAdminBusinessManager.getBusinesses(context) }
    var selectedBusiness by remember {
        mutableStateOf(SuperAdminBusinessManager.findBusinessForRequest(context, request) ?: allBusinesses.firstOrNull())
    }
    var expandedBizDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isNuevoUsuario -> Icons.Default.PersonAdd
                            isAutorizacion -> Icons.Default.VerifiedUser
                            else -> Icons.Default.VpnKey
                        },
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = when {
                            isNuevoUsuario -> "Aprobar Solicitud de Nuevo Usuario"
                            isAutorizacion -> "Aprobar Prueba (7 Días)"
                            else -> "Aprobar Licencia Comercial"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "DVC: ${request.dvc}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElQadreNavy)
                        Text(text = "Negocio: ${request.nombreNegocio}", fontSize = 13.sp, color = Slate800)
                        Text(text = "Móvil: ${request.numeroMovil}", fontSize = 13.sp, color = Slate800)
                        if (request.solicitante.isNotBlank()) {
                            Text(text = "Solicitante: ${request.solicitante}", fontSize = 12.sp, color = Slate600)
                        }
                    }
                }

                if (isNuevoUsuario) {
                    // Business selection for user assignment
                    Text(
                        text = "Negocio al que se asignará el usuario:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElQadreNavy
                    )

                    if (allBusinesses.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = expandedBizDropdown,
                            onExpandedChange = { expandedBizDropdown = !expandedBizDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedBusiness?.let { "[${it.code}] ${it.name}" } ?: "Crear nuevo negocio",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBizDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedBizDropdown,
                                onDismissRequest = { expandedBizDropdown = false }
                            ) {
                                allBusinesses.forEach { biz ->
                                    DropdownMenuItem(
                                        text = { Text("[${biz.code}] ${biz.name}") },
                                        onClick = {
                                            selectedBusiness = biz
                                            expandedBizDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreNavySoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No hay negocios registrados aún. Se creará automáticamente el negocio [001] '${request.nombreNegocio}'.",
                                fontSize = 12.sp,
                                color = ElQadreNavy,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "Datos del Usuario Solicitado:", fontWeight = FontWeight.Bold, color = Emerald700, fontSize = 13.sp)
                            Text(text = "• Nombre: ${request.nuevoNombre}", fontSize = 12.sp, color = Slate800)
                            Text(text = "• Usuario de acceso: ${request.nuevoUsername}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text(text = "• Rol: ${request.nuevoRol}", fontSize = 12.sp, color = Slate800)
                            if (request.nuevoTelefono.isNotBlank()) {
                                Text(text = "• Móvil: ${request.nuevoTelefono}", fontSize = 12.sp, color = Slate800)
                            }
                            if (request.montoPorProducto > 0) {
                                Text(text = "• Monto por producto: $${request.montoPorProducto}", fontSize = 12.sp, color = Slate800)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = initialPassword,
                        onValueChange = { initialPassword = it },
                        label = { Text("Contraseña Inicial") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Text(
                        text = "Al aprobar, se actualizará el archivo Q_XXXusuarios.json correspondiente a este negocio y quedará listo para sincronizar.",
                        fontSize = 11.sp,
                        color = Slate500
                    )

                } else if (isAutorizacion) {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val cal = Calendar.getInstance()
                    val fInicio = sdf.format(cal.time)
                    cal.add(Calendar.DAY_OF_YEAR, 7)
                    val fFin = sdf.format(cal.time)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "Vigencia de la Prueba: 7 Días", fontWeight = FontWeight.Bold, color = Emerald700, fontSize = 13.sp)
                            Text(text = "Desde: $fInicio  |  Hasta: $fFin", fontSize = 12.sp, color = Slate700)
                            Text(text = "Se generará o actualizará Q_preuba.json conservando los demás clientes.", fontSize = 11.sp, color = Slate600)
                        }
                    }
                } else {
                    // Licencia configuration
                    Text(
                        text = "Tipo y Vigencia de la Licencia:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElQadreNavy
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = tipoLicencia == "PERMANENTE",
                            onClick = {
                                tipoLicencia = "PERMANENTE"
                                duracionDias = -1
                            },
                            label = { Text("Permanente", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = tipoLicencia == "ANUAL",
                            onClick = {
                                tipoLicencia = "ANUAL"
                                duracionDias = 365
                            },
                            label = { Text("Anual (1 Año)", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = tipoLicencia == "MENSUAL",
                            onClick = {
                                tipoLicencia = "MENSUAL"
                                duracionDias = 30
                            },
                            label = { Text("Mensual (30D)", fontSize = 11.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isNuevoUsuario) {
                        TextButton(
                            onClick = {
                                SuperAdminBusinessManager.rejectUserRequest(context, request)
                                onDismiss()
                            }
                        ) {
                            Text("Rechazar", color = Rose700, fontSize = 12.sp)
                        }
                    }

                    OutlinedButton(
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
                                if (isNuevoUsuario) {
                                    val targetBiz = selectedBusiness ?: run {
                                        val nextCode = SuperAdminBusinessManager.getNextBusinessCode(context)
                                        val newBiz = BusinessRecord(
                                            code = nextCode,
                                            name = request.nombreNegocio.ifBlank { "Negocio $nextCode" },
                                            dvc = request.dvc,
                                            phone = request.numeroMovil,
                                            licenseType = "PRUEBA",
                                            startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                            endDate = "",
                                            status = "ACTIVO",
                                            urlUsuarios = "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/Q_${nextCode}usuarios.json",
                                            users = emptyList()
                                        )
                                        SuperAdminBusinessManager.saveBusiness(context, newBiz)
                                        newBiz
                                    }

                                    val (updatedBiz, userFile) = SuperAdminBusinessManager.approveUserRequest(
                                        context = context,
                                        business = targetBiz,
                                        req = request,
                                        initialPassword = initialPassword
                                    )
                                    isProcessing = false
                                    onApproved(userFile, "Guardar / Compartir Q_${updatedBiz.code}usuarios.json")
                                } else if (isAutorizacion) {
                                    val file = SuperAdminSmsHelper.generateOrUpdatePruebasJson(
                                        context = context,
                                        dvc = request.dvc,
                                        nombreNegocio = request.nombreNegocio,
                                        numeroMovil = request.numeroMovil
                                    )
                                    isProcessing = false
                                    onApproved(file, "Guardar / Compartir Q_preuba.json")
                                } else {
                                    val file = SuperAdminSmsHelper.generateOrUpdateLicenciasJson(
                                        context = context,
                                        dvc = request.dvc,
                                        nombreNegocio = request.nombreNegocio,
                                        numeroMovil = request.numeroMovil,
                                        tipoLicencia = tipoLicencia,
                                        durationDays = duracionDias
                                    )
                                    isProcessing = false
                                    onApproved(file, "Guardar / Compartir Q_licencias.json")
                                }
                            }
                        },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (isNuevoUsuario) "Aprobar y Generar JSON" else "Aprobar y Generar JSON",
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
private fun FileGeneratedSuccessDialog(
    file: File,
    title: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Emerald600,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Archivo Generado Exitosamente",
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Se ha actualizado el archivo:",
                    fontSize = 13.sp,
                    color = Slate700,
                    textAlign = TextAlign.Center
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = file.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Text(
                    text = "El archivo se ha guardado en el dispositivo. Compártalo o descárguelo para publicarlo manualmente en GitHub en la rama main.",
                    fontSize = 12.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Compartir / Guardar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun SuperAdminPruebasSection() {
    val context = LocalContext.current
    val localFile = remember { File(context.filesDir, "Q_preuba.json") }
    var fileContent by remember { mutableStateOf("") }

    fun refreshContent() {
        if (localFile.exists() && localFile.readText().isNotBlank()) {
            fileContent = localFile.readText()
        } else {
            val dynamicJson = SuperAdminBusinessManager.buildDynamicQPreubaJson(context)
            localFile.writeText(dynamicJson)
            fileContent = dynamicJson
        }
    }

    LaunchedEffect(Unit) {
        refreshContent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dedicated Card for Trial SMS: ONLY ESCANEAR, PENDIENTES, CONFIRMADAS
        SuperAdminTrialSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshContent()
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = ElQadreNavy
                    )
                    Text(
                        text = "Registro de Pruebas (Q_preuba.json)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Text(
                    text = "Archivo de control para pruebas de 7 días. Los cambios se guardan localmente para su posterior publicación manual en GitHub.",
                    fontSize = 12.sp,
                    color = Slate600
                )

                if (fileContent.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Text(
                            text = fileContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Slate800,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Button(
                        onClick = {
                            if (!localFile.exists()) {
                                localFile.writeText(fileContent)
                            }
                            SuperAdminSmsHelper.shareJsonFile(context, localFile, "Compartir Q_preuba.json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir / Exportar Q_preuba.json", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aún no se ha generado Q_preuba.json localmente.\nPuede escanear o procesar solicitudes SMS en la tarjeta de arriba para generarlo automáticamente.",
                            fontSize = 12.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperAdminLicenciasSection() {
    val context = LocalContext.current
    val localFile = remember { File(context.filesDir, "Q_licencias.json") }
    var fileContent by remember { mutableStateOf("") }

    fun refreshContent() {
        if (localFile.exists() && localFile.readText().isNotBlank()) {
            fileContent = localFile.readText()
        } else {
            val dynamicJson = SuperAdminBusinessManager.buildDynamicQLicenciasJson(context)
            localFile.writeText(dynamicJson)
            fileContent = dynamicJson
        }
    }

    LaunchedEffect(Unit) {
        refreshContent()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Dedicated Card for Activation / Licencias SMS: ESCANEAR, PENDIENTES, CONFIRMADAS
        SuperAdminActivationSmsCard(
            onBusinessCreatedOrUpdated = {
                refreshContent()
            }
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = ElQadreNavy
                    )
                    Text(
                        text = "Registro de Licencias (Q_licencias.json)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Text(
                    text = "Archivo de control para licencias comerciales (permanentes, anuales, mensuales). Publicar manualmente en GitHub.",
                    fontSize = 12.sp,
                    color = Slate600
                )

                if (fileContent.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        Text(
                            text = fileContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Slate800,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Button(
                        onClick = {
                            if (!localFile.exists()) {
                                localFile.writeText(fileContent)
                            }
                            SuperAdminSmsHelper.shareJsonFile(context, localFile, "Compartir Q_licencias.json")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir / Exportar Q_licencias.json", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Aún no se ha generado Q_licencias.json localmente.\nPuede procesar solicitudes de activación en la tarjeta de arriba para generarlo.",
                            fontSize = 12.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuperAdminConfiguracionSection(
    superAdminUser: String,
    dvc: String
) {
    var selectedSubTab by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.White,
            contentColor = ElQadreNavy
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("Versiones y APK", fontWeight = if (selectedSubTab == 0) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(if (selectedSubTab == 0) Icons.Filled.SystemUpdate else Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("Datos Super Admin", fontWeight = if (selectedSubTab == 1) FontWeight.Bold else FontWeight.Normal) },
                icon = { Icon(if (selectedSubTab == 1) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        when (selectedSubTab) {
            0 -> {
                SuperAdminVersionesSection()
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                                Text(
                                    text = "Configuración de Super Admin",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }

                            HorizontalDivider(color = Slate200)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Usuario Activo",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = superAdminUser,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "DVC del Dispositivo",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = dvc,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = ElQadreNavy
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Número Oficial de Super Admin (SMS)",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = SuperAdminSmsHelper.SUPER_ADMIN_PHONE,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Origen de Autorización",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Text(
                                    text = "GitHub / QDF_superadmin.json",
                                    fontSize = 13.sp,
                                    color = Slate700
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Estado de Autorización",
                                    fontSize = 12.sp,
                                    color = Slate500
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Emerald600
                                    ) {
                                        Text(
                                            text = "ACTIVO",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
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
