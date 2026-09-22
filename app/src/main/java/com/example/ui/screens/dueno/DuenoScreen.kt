package com.example.ui.screens.dueno

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.font.FontFamily
import com.example.data.local.model.ConfiguracionNegocio
import com.example.data.local.model.ConfiguracionGeneral
import com.example.data.local.model.MateriaPrima
import com.example.data.local.model.ProductoElaborado
import com.example.data.local.model.RecetaIngrediente
import com.example.data.local.model.Product
import com.example.data.local.model.Mercaderia
import com.example.data.local.model.MovimientoMercaderia
import com.example.data.local.model.Tanda
import com.example.data.local.model.MovimientoMateriaPrima
import com.example.data.local.model.StockMovement
import com.example.data.local.model.Inversion
import com.example.data.local.model.GastoGeneral
import com.example.data.local.model.Jornada
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.data.local.model.PaymentProposal
import com.example.data.local.model.PersonalContratado
import com.example.data.local.model.PresentacionEspecial
import com.example.data.local.model.parsePresentacionesEspeciales
import com.example.data.local.model.serializePresentacionesEspeciales
import com.example.util.BusinessBackupManager
import com.example.util.PersonalPdfExporter
import com.example.util.toSha256
import com.example.util.QJornadaExporter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.barra.BarraProductItem
import androidx.compose.material.icons.filled.Edit
import com.example.ui.components.ContactPickerIconButton
import com.example.ui.screens.admin.AddEditMateriaPrimaDialog
import com.example.ui.screens.admin.ToggleAgregadoInsumoDialog
import com.example.ui.screens.admin.AddEditMercaderiaDialog
import com.example.ui.screens.admin.ConfigurarPagosBebidasDialog
import androidx.compose.material.icons.filled.Payments
import com.example.ui.screens.admin.AddEditProductoElaboradoDialog
import com.example.ui.screens.admin.AddEditGastoGeneralDialog
import com.example.ui.screens.admin.FichaCostoMercaderiaDialog
import com.example.ui.screens.admin.convertToBaseQty
import com.example.ui.screens.admin.RecipeManagementDialog
import com.example.ui.screens.admin.AddRecipeIngredientDialog
import com.example.ui.screens.admin.EditRecipeIngredientDialog
import com.example.ui.screens.admin.FichaCostoDialog
import com.example.ui.screens.admin.getCompatibleUnits
import com.example.ui.screens.admin.getBaseUnit
import com.example.ui.screens.admin.getNormalizedCost
import com.example.ui.screens.cajero.ConteoResumenDialog
import com.example.ui.screens.cajero.QuickActionsDialog
import com.example.ui.screens.cajero.TransferenciasPane
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.screens.admin.CatalogoSubScreen
import com.example.ui.screens.admin.GestionSubScreen
import com.example.ui.screens.admin.TandasPane
import com.example.ui.screens.admin.UsuariosSubScreen
import com.example.util.BarraBackupManager
import com.example.util.CajeroBackupManager
import com.example.util.InitializationManager
import com.example.util.SalonBackupManager
import kotlinx.coroutines.launch

enum class DuenoView {
    INICIO,
    TANDAS,
    CUADRE_CAJA,
    INVENTARIO,
    CATALOGO,
    INVERSIONES,
    GASTOS,
    CONTROL_NEGOCIO,
    PERSONAL,
    GESTION,
    AJUSTES
}

@Composable
fun DuenoScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentView by remember { mutableStateOf(DuenoView.INICIO) }
    var previousView by remember { mutableStateOf<DuenoView?>(null) }
    var showQuickActions by remember { mutableStateOf(false) }

    val ownerUsername = uiState.currentUser?.username ?: "dueno"
    var visibleModules by remember(ownerUsername) {
        mutableStateOf(com.example.util.DuenoSessionPreferences.getVisibleModules(context, ownerUsername))
    }
    val billDenominationStacks = remember { mutableStateMapOf<Int, androidx.compose.runtime.snapshots.SnapshotStateList<String>>() }
    var showResumenConteoDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    // State for restore dialogues
    var showRestoreSalonModal by remember { mutableStateOf(false) }
    var showRestoreBarraModal by remember { mutableStateOf(false) }
    var showRestoreCajeroModal by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    var showRestoreDuenoModal by remember { mutableStateOf(false) }
    var duenoRestoreJsonInput by remember { mutableStateOf("") }
    var duenoBackupSummary by remember { mutableStateOf<String?>(null) }

    // State for Jornada Apertura, Cierre y Detalle (Fase 4)
    var showAbrirJornadaModal by remember { mutableStateOf(false) }
    var showCerrarJornadaModal by remember { mutableStateOf(false) }
    var showDetalleJornadaModal by remember { mutableStateOf<Jornada?>(null) }
    var showCuadreCajaModal by remember { mutableStateOf(false) }

    val isAnyDuenoModalOpen = drawerState.isOpen ||
            showAbrirJornadaModal ||
            showCerrarJornadaModal ||
            showDetalleJornadaModal != null ||
            showCuadreCajaModal ||
            showNotificationsDialog ||
            showResumenConteoDialog ||
            showRestoreDuenoModal ||
            showRestoreSalonModal ||
            showRestoreBarraModal ||
            showRestoreCajeroModal ||
            showQuickActions

    BackHandler(enabled = isAnyDuenoModalOpen || currentView != DuenoView.INICIO) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            showAbrirJornadaModal -> showAbrirJornadaModal = false
            showCerrarJornadaModal -> showCerrarJornadaModal = false
            showDetalleJornadaModal != null -> showDetalleJornadaModal = null
            showCuadreCajaModal -> showCuadreCajaModal = false
            showNotificationsDialog -> showNotificationsDialog = false
            showResumenConteoDialog -> showResumenConteoDialog = false
            showRestoreDuenoModal -> showRestoreDuenoModal = false
            showRestoreSalonModal -> showRestoreSalonModal = false
            showRestoreBarraModal -> showRestoreBarraModal = false
            showRestoreCajeroModal -> showRestoreCajeroModal = false
            showQuickActions -> showQuickActions = false
            currentView != DuenoView.INICIO -> {
                currentView = previousView ?: DuenoView.INICIO
                previousView = null
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = ElQadreNavy,
                drawerContentColor = Color.White,
                windowInsets = WindowInsets.safeDrawing,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Drawer Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ElQadreGold.copy(alpha = 0.22f),
                                border = BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.4f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalance,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = uiState.businessName.ifBlank { "El Qadre" },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 19.sp,
                                    color = Color.White,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Auditoría & Propietario",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ElQadreGold
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

                        // Navigation Items
                        val allNavItems = listOf(
                            Triple(DuenoView.INICIO, "Inicio", Icons.Outlined.Home),
                            Triple(DuenoView.TANDAS, "Tandas", Icons.Outlined.History),
                            Triple(DuenoView.CUADRE_CAJA, "Cuadre de Caja", Icons.Outlined.PointOfSale),
                            Triple(DuenoView.AJUSTES, "Ajustes", Icons.Outlined.Settings)
                        )

                        val navItems = allNavItems

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            navItems.forEach { item ->
                                val (view, label, icon) = item
                                val isSelected = currentView == view
                                NavigationDrawerItem(
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        currentView = view
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = ElQadreGold,
                                        selectedIconColor = ElQadreNavy,
                                        selectedTextColor = ElQadreNavy,
                                        unselectedContainerColor = Color.Transparent,
                                        unselectedIconColor = Color.White.copy(alpha = 0.88f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.92f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                )
                            }
                        }
                    }

                    // Logout Button in Drawer Footer with comfortable target & high contrast
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF2F2).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFFECACA).copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationDrawerItem(
                            label = {
                                Text(
                                    text = "Cerrar Sesión",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFFFECACA)
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                    tint = Color(0xFFFECACA)
                                )
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onLogout()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedIconColor = Color(0xFFFECACA),
                                unselectedTextColor = Color(0xFFFECACA)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = ElQadreBackground,
            topBar = {
                Surface(
                    color = ElQadreNavy,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menú",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = uiState.businessName.ifBlank { "El Qadre" },
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Dueño: ${uiState.currentUser?.fullName ?: "Don Roberto"}",
                                    color = ElQadreGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Synchronize Button (Local data refresh)
                            IconButton(
                                onClick = {
                                    viewModel.refreshCatalog()
                                    Toast.makeText(context, "Datos actualizados localmente.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("btn_top_sync")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = "ACTUALIZAR",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Notifications Button
                            Box(contentAlignment = Alignment.TopEnd) {
                                IconButton(
                                    onClick = { showNotificationsDialog = true },
                                    modifier = Modifier.testTag("btn_top_notifications")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = "Notificaciones",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(ElQadreGold, CircleShape)
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-4).dp, y = 4.dp)
                                )
                            }

                            // Exit Button
                            IconButton(onClick = onLogout) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                                    contentDescription = "Salir",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentView == DuenoView.INICIO) {
                    FloatingActionButton(
                        onClick = { showQuickActions = true },
                        containerColor = ElQadreGold,
                        contentColor = ElQadreNavy,
                        shape = CircleShape,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("btn_acciones_rapidas")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Calculate,
                            contentDescription = "Acciones Rápidas"
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWide = maxWidth >= 600.dp

                    when (currentView) {
                        DuenoView.INICIO -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                // Jornada Status & Control Banner
                                val activeJornada = uiState.activeJornada
                                if (activeJornada != null) {
                                    val openDateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(activeJornada.openedAt))
                                    ElQadreCard(
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = Color.White,
                                        borderColor = ElQadreGoldSoft,
                                        borderWidth = 1.5.dp,
                                        contentPadding = PaddingValues(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Header Row: Status Badge & Title
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
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFFEF3C7),
                                                    modifier = Modifier.size(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.AccessTimeFilled,
                                                            contentDescription = null,
                                                            tint = Color(0xFFB45309),
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = "JORNADA ACTIVA #${activeJornada.id}",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy,
                                                        letterSpacing = 0.3.sp
                                                    )
                                                    Text(
                                                        text = "Registro operativo en curso",
                                                        fontSize = 12.sp,
                                                        color = Slate500
                                                    )
                                                }
                                            }

                                            ElQadrePillBadge(
                                                text = "EN CURSO",
                                                style = ElQadreBadgeStyle.SUCCESS,
                                                icon = Icons.Outlined.CheckCircle
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Central Data Panel (Responsable, Fecha, Fondo Inicial)
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = Slate50,
                                            border = BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Outlined.Person, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(16.dp))
                                                        Text("Iniciada por: ${activeJornada.openedBy}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
                                                        Text("Apertura: $openDateStr", fontSize = 12.sp, color = Slate600)
                                                    }
                                                }

                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = "FONDO INICIAL",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate500,
                                                        letterSpacing = 0.5.sp
                                                    )
                                                    Text(
                                                        text = "$${"%.2f".format(activeJornada.initialCash)} CUP",
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // Action Buttons
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Button(
                                                onClick = { showCerrarJornadaModal = true },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFFB45309),
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(50.dp)
                                                    .testTag("btn_cerrar_jornada_banner")
                                            ) {
                                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Cerrar Jornada & Generar Q_jornada.json", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                } else {
                                    ElQadreCard(
                                        shape = RoundedCornerShape(20.dp),
                                        containerColor = Color.White,
                                        borderColor = Slate200,
                                        contentPadding = PaddingValues(20.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
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
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Slate100,
                                                    modifier = Modifier.size(42.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Outlined.Lock,
                                                            contentDescription = null,
                                                            tint = Slate500,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Column {
                                                    Text(
                                                        text = "JORNADA CERRADA",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Slate800,
                                                        letterSpacing = 0.3.sp
                                                    )
                                                    Text(
                                                        text = "Sin jornada activa en este dispositivo",
                                                        fontSize = 12.sp,
                                                        color = Slate500
                                                    )
                                                }
                                            }

                                            ElQadrePillBadge(
                                                text = "SIN ACTIVIDAD",
                                                style = ElQadreBadgeStyle.NEUTRAL
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = "No hay ninguna jornada abierta en este dispositivo. Inicie una jornada para comenzar a registrar operaciones.",
                                            fontSize = 13.sp,
                                            color = Slate600,
                                            lineHeight = 18.sp
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Button(
                                            onClick = { showAbrirJornadaModal = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ElQadreNavy,
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(50.dp)
                                                .testTag("btn_abrir_jornada_banner")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("ABRIR JORNADA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }

                                // 4 Big Buttons Grid
                                DuenoCardsGrid(
                                    uiState = uiState,
                                    isWide = isWide,
                                    visibleModules = visibleModules
                                ) { selectedView ->
                                    previousView = DuenoView.INICIO
                                    currentView = selectedView
                                }
                            }
                        }

                        DuenoView.TANDAS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DuenoSubscreenHeader(
                                    title = "Tandas de Producción",
                                    subtitle = "Lotes de producción, control de costos y rendimientos",
                                    icon = Icons.Outlined.History,
                                    onBack = {
                                        currentView = previousView ?: DuenoView.INICIO
                                        previousView = null
                                    }
                                )
                                TandasPane(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                        }

                        DuenoView.CUADRE_CAJA -> {
                            CuadreCajaScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize(),
                                onClose = {
                                    currentView = previousView ?: DuenoView.INICIO
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.INVENTARIO -> {
                            DuenoInventarioView(
                                uiState = uiState,
                                viewModel = viewModel,
                                isWide = isWide,
                                visibleModules = visibleModules,
                                onBack = {
                                    currentView = previousView ?: DuenoView.GESTION
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.CATALOGO -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DuenoSubscreenHeader(
                                    title = "Catálogo",
                                    subtitle = "Gestión de productos, categorías y precios de venta",
                                    icon = Icons.Outlined.MenuBook,
                                    onBack = {
                                        currentView = previousView ?: DuenoView.GESTION
                                        previousView = null
                                    }
                                )
                                CatalogoSubScreen(
                                    uiState = uiState,
                                    viewModel = viewModel
                                )
                            }
                        }

                        DuenoView.INVERSIONES -> {
                            DuenoInversionesView(
                                uiState = uiState,
                                viewModel = viewModel,
                                isWide = isWide,
                                onBack = {
                                    currentView = previousView ?: DuenoView.GESTION
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.GASTOS -> {
                            DuenoGastosSubScreen(
                                uiState = uiState,
                                viewModel = viewModel,
                                onBack = {
                                    currentView = previousView ?: DuenoView.GESTION
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.CONTROL_NEGOCIO -> {
                            DuenoControlNegocioView(
                                uiState = uiState,
                                viewModel = viewModel,
                                isWide = isWide,
                                onBack = {
                                    currentView = previousView ?: DuenoView.GESTION
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.PERSONAL -> {
                            DuenoPersonalView(
                                uiState = uiState,
                                viewModel = viewModel,
                                isWide = isWide,
                                onBack = {
                                    currentView = previousView ?: DuenoView.GESTION
                                    previousView = null
                                }
                            )
                        }

                        DuenoView.GESTION, DuenoView.AJUSTES -> {
                            DuenoAjustesView(
                                uiState = uiState,
                                viewModel = viewModel,
                                isWide = isWide,
                                visibleModules = visibleModules,
                                onVisibleModulesChanged = { newSet ->
                                    visibleModules = newSet
                                },
                                initialSection = if (currentView == DuenoView.GESTION) "GESTION" else "PREFERENCIAS",
                                onNavigate = { targetView ->
                                    previousView = DuenoView.GESTION
                                    currentView = targetView
                                },
                                onBack = {
                                    currentView = DuenoView.INICIO
                                    previousView = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Cuadre de Caja Modal (Fase 2)
    if (showCuadreCajaModal) {
        Dialog(
            onDismissRequest = { showCuadreCajaModal = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                color = ElQadreBackground
            ) {
                CuadreCajaScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    onClose = { showCuadreCajaModal = false }
                )
            }
        }
    }

    if (showQuickActions) {
        QuickActionsDialog(
            billFields = billDenominationStacks,
            onShowResumen = { showResumenConteoDialog = true },
            onDismiss = { showQuickActions = false }
        )
    }

    if (showResumenConteoDialog) {
        ConteoResumenDialog(
            billFields = billDenominationStacks,
            onDismiss = { showResumenConteoDialog = false }
        )
    }

    // Notifications Dialog
    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null, tint = ElQadreNavy)
                    Text("Notificaciones del Sistema", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                }
            },
            text = {
                val lowStockCount = uiState.products.count { it.stock <= it.minStock }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (lowStockCount > 0) {
                        Text("• $lowStockCount producto(s) con stock bajo en inventario.", fontSize = 13.sp, color = Slate700)
                    } else {
                        Text("• Estado de inventarios normal sin alertas de stock bajo.", fontSize = 13.sp, color = Slate700)
                    }
                    Text("• Respaldo de seguridad diario activo.", fontSize = 13.sp, color = Slate700)
                    Text("• Control operativo de jornadas sincronizado.", fontSize = 13.sp, color = Slate700)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showNotificationsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Dialog for Restore Salon
    if (showRestoreSalonModal) {
        RestoreBackupDialog(
            title = "Restaurar Salón (qdepsalon.json)",
            inputValue = restoreJsonInput,
            isProcessing = isRestoring,
            onValueChange = { restoreJsonInput = it },
            onConfirm = {
                isRestoring = true
                viewModel.restoreSalonBackupJson(restoreJsonInput) { success ->
                    isRestoring = false
                    if (success) {
                        showRestoreSalonModal = false
                        Toast.makeText(context, "Respaldo de Salón restaurado exitosamente.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showRestoreSalonModal = false }
        )
    }

    // Dialog for Restore Barra
    if (showRestoreBarraModal) {
        RestoreBackupDialog(
            title = "Restaurar Barra (qdepbarra.json)",
            inputValue = restoreJsonInput,
            isProcessing = isRestoring,
            onValueChange = { restoreJsonInput = it },
            onConfirm = {
                isRestoring = true
                viewModel.restoreBarraBackupJson(restoreJsonInput) { success ->
                    isRestoring = false
                    if (success) {
                        showRestoreBarraModal = false
                        Toast.makeText(context, "Respaldo de Barra restaurado exitosamente.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showRestoreBarraModal = false }
        )
    }

    // Dialog for Restore Cajero
    if (showRestoreCajeroModal) {
        RestoreBackupDialog(
            title = "Restaurar Caja (qdepcajero.json)",
            inputValue = restoreJsonInput,
            isProcessing = isRestoring,
            onValueChange = { restoreJsonInput = it },
            onConfirm = {
                isRestoring = true
                viewModel.restoreCajeroBackupJson(restoreJsonInput) { success ->
                    isRestoring = false
                    if (success) {
                        showRestoreCajeroModal = false
                        Toast.makeText(context, "Respaldo de Caja restaurado exitosamente.", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showRestoreCajeroModal = false }
        )
    }

    // Dialog for Restore Dueno (Full System)
    if (showRestoreDuenoModal) {
        AlertDialog(
            onDismissRequest = { showRestoreDuenoModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = ElQadreNavy)
                    Text("Restaurar Copia Completa", fontWeight = FontWeight.Black, color = ElQadreNavy)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (duenoBackupSummary == null) {
                        Text(
                            "Pegue el contenido JSON del Respaldo Completo para validar e importar todo el sistema de forma atómica.",
                            fontSize = 13.sp,
                            color = Slate600
                        )
                        OutlinedTextField(
                            value = duenoRestoreJsonInput,
                            onValueChange = { duenoRestoreJsonInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .testTag("restore_dueno_paste_field"),
                            placeholder = { Text("Pegue el respaldo completo JSON aquí...") },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                cursorColor = ElQadreNavy
                            )
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate100),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("RESUMEN DE DATOS A RESTAURAR:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(duenoBackupSummary!!, fontSize = 13.sp, color = Slate800, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "⚠️ ATENCIÓN MÁXIMA:\nAl proceder, todos los datos operativos de este dispositivo serán reemplazados por completo de forma atómica con la información de la copia.",
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                if (duenoBackupSummary == null) {
                    Button(
                        onClick = {
                            val res = viewModel.getDuenoBackupSummary(duenoRestoreJsonInput)
                            if (res.isSuccess) {
                                duenoBackupSummary = res.getOrNull()
                            } else {
                                Toast.makeText(context, "Respaldo inválido: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.testTag("btn_validar_dueno_backup")
                    ) {
                        Text("Validar Respaldo", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            isRestoring = true
                            viewModel.restoreDuenoBackupJson(duenoRestoreJsonInput) { success, msg ->
                                isRestoring = false
                                if (success) {
                                    showRestoreDuenoModal = false
                                    Toast.makeText(context, "Sistema completo restaurado con éxito.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.testTag("btn_confirmar_restore_dueno")
                    ) {
                        Text("RESTAURAR TODO EL SISTEMA", fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDuenoModal = false }) {
                    Text("Cancelar", fontWeight = FontWeight.Bold, color = Slate600)
                }
            },
            containerColor = Color.White
        )
    }

    // Jornada Modals (Fase 4)
    if (showAbrirJornadaModal) {
        AbrirJornadaDuenoDialog(
            currentUser = uiState.currentUser,
            onDismiss = { showAbrirJornadaModal = false },
            onConfirm = { fondo ->
                showAbrirJornadaModal = false
                val devName = android.os.Build.MODEL ?: "DISPOSITIVO-01"
                viewModel.openJornada(
                    initialCash = fondo,
                    openedBy = uiState.currentUser?.fullName ?: uiState.currentUser?.username ?: "Dueño",
                    device = devName
                ) { success, msg ->
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    if (showCerrarJornadaModal && uiState.activeJornada != null) {
        val currentActiveJor = uiState.activeJornada!!
        CerrarJornadaDuenoDialog(
            jornada = currentActiveJor,
            uiState = uiState,
            onDismiss = { showCerrarJornadaModal = false },
            onConfirm = { finalCash, notes ->
                showCerrarJornadaModal = false
                viewModel.closeJornadaWithSnapshot(
                    context = context,
                    jornadaId = currentActiveJor.id,
                    finalCash = finalCash,
                    notes = notes
                ) { success, _, errorMsg ->
                    if (success) {
                        Toast.makeText(context, "Jornada #${currentActiveJor.id} cerrada con éxito y Q_jornada.json generado.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, errorMsg ?: "Error al cerrar jornada.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (showDetalleJornadaModal != null) {
        DetalleJornadaCerradaDialog(
            jornada = showDetalleJornadaModal!!,
            onDismiss = { showDetalleJornadaModal = null }
        )
    }
}

@Composable
fun DuenoSubscreenHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = ElQadreNavy
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ElQadreNavy.copy(alpha = 0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            }

            if (actions != null) {
                actions()
            }
        }
    }
}

@Composable
fun DuenoPlaceholderContent(
    title: String,
    text: String,
    isWide: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = ElQadreGold.copy(alpha = 0.15f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                text = title,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = ElQadreNavy,
                textAlign = TextAlign.Center
            )
            Text(
                text = text,
                fontSize = 14.sp,
                color = Slate600,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate100,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "FASE DE DESARROLLO ESTRUCTURAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, ElQadreBorder),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy.copy(alpha = 0.08f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ElQadreNavy,
                    letterSpacing = 0.2.sp
                )
            }
            HorizontalDivider(color = Slate100, modifier = Modifier.padding(bottom = 14.dp))
            content()
        }
    }
}

@Composable
fun DuenoCardsGrid(
    uiState: MainUiState,
    isWide: Boolean,
    visibleModules: Set<String> = com.example.util.DuenoSessionPreferences.ALL_MODULES,
    onCardClick: (DuenoView) -> Unit
) {
    val items = listOf(
        Triple(DuenoView.TANDAS, "TANDAS", "Lotes de producción, control de costos y rendimientos"),
        Triple(DuenoView.CUADRE_CAJA, "CUADRE DE CAJA", "Arqueo de efectivo, balances e ingresos"),
        Triple(DuenoView.AJUSTES, "AJUSTES", "Preferencias del sistema y Gestión operativa")
    )

    if (items.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "No hay módulos seleccionados.",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Slate700
                )
                Text(
                    text = "Puede personalizar y activar los módulos que desea visualizar en la sección de Ajustes.",
                    fontSize = 12.sp,
                    color = Slate500,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (isWide) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems.forEach { item ->
                        DuenoBigCard(item, Modifier.weight(1f), isEnabled = true, onClick = onCardClick)
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items.forEach { item ->
                DuenoBigCard(item, Modifier.fillMaxWidth(), isEnabled = true, onClick = onCardClick)
            }
        }
    }
}

@Composable
fun DuenoBigCard(
    item: Triple<DuenoView, String, String>,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onClick: (DuenoView) -> Unit
) {
    val (view, title, subtitle) = item
    val icon = when (view) {
        DuenoView.TANDAS -> Icons.Outlined.History
        DuenoView.CUADRE_CAJA -> Icons.Outlined.PointOfSale
        DuenoView.INVENTARIO -> Icons.Outlined.Inventory2
        DuenoView.CATALOGO -> Icons.Outlined.MenuBook
        DuenoView.INVERSIONES -> Icons.Outlined.AttachMoney
        DuenoView.GASTOS -> Icons.Outlined.ReceiptLong
        DuenoView.CONTROL_NEGOCIO -> Icons.Outlined.Analytics
        DuenoView.PERSONAL -> Icons.Outlined.Group
        DuenoView.GESTION -> Icons.Outlined.Tune
        DuenoView.AJUSTES -> Icons.Outlined.Settings
        else -> Icons.Outlined.Info
    }

    Surface(
        onClick = { if (isEnabled) onClick(view) },
        shape = RoundedCornerShape(20.dp),
        color = if (isEnabled) Color.White else Slate100,
        border = BorderStroke(1.dp, if (isEnabled) ElQadreBorder else Slate200),
        shadowElevation = if (isEnabled) 1.dp else 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_${view.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isEnabled) ElQadreNavy.copy(alpha = 0.07f) else Slate200,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (!isEnabled) Icons.Outlined.Lock else icon,
                        contentDescription = null,
                        tint = if (isEnabled) ElQadreNavy else Slate400,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) ElQadreNavy else Slate500,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = if (isEnabled) Slate600 else Slate400,
                    lineHeight = 17.sp
                )
            }
            Icon(
                imageVector = if (!isEnabled) Icons.Outlined.Lock else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isEnabled) Slate400 else Slate300,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RestoreBackupDialog(
    title: String,
    inputValue: String,
    isProcessing: Boolean,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "⚠️ ADVERTENCIA CRÍTICA:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Rose700
                        )
                        Text(
                            text = "Esto reemplazará todos los datos operativos actuales. Asegúrese de que el JSON pegado sea válido.",
                            fontSize = 11.sp,
                            color = Rose700
                        )
                    }
                }
                Text("Pegue el contenido JSON del respaldo aquí:", fontSize = 12.sp, color = Slate600)
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onValueChange,
                    placeholder = { Text("Pegue el contenido JSON aquí...", fontSize = 11.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = inputValue.isNotBlank() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("RESTABLECER DATOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    )
}

// ==========================================
// FASE 5.5.2 - INVENTARIO DEL DUEÑO (IMPLEMENTACIÓN)
// ==========================================

@Composable
fun DuenoInventarioView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    visibleModules: Set<String> = com.example.util.DuenoSessionPreferences.ALL_MODULES,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser = uiState.currentUser
    val isDueno = currentUser?.role == UserRole.DUENO
    val canProduccion = !isDueno || (currentUser?.permisoProduccion ?: true)
    val canMercancias = !isDueno || (currentUser?.permisoMercancias ?: true)

    val isProduccionVisible = com.example.util.DuenoSessionPreferences.isModuleVisible(visibleModules, "PRODUCCION")
    val isMercaderiasVisible = com.example.util.DuenoSessionPreferences.isModuleVisible(visibleModules, "MERCADERIAS")

    var activeSubView by remember(isProduccionVisible, isMercaderiasVisible) {
        mutableStateOf(
            when {
                isProduccionVisible && isMercaderiasVisible -> "MENU"
                isProduccionVisible -> "PRODUCCION"
                isMercaderiasVisible -> "MERCADERIAS"
                else -> "MENU"
            }
        )
    }

    val handleSubscreenBack: () -> Unit = {
        if (isProduccionVisible && isMercaderiasVisible) {
            activeSubView = "MENU"
        } else {
            onBack()
        }
    }
    
    // Tracking for Jornada first-entry inventory recommendation warnings
    val activeJornada = uiState.activeJornada
    val activeJornadaId = activeJornada?.id
    var acknowledgedProduccionWarningJornadaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var acknowledgedMercaderiasWarningJornadaId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showProduccionWarningDialog by remember { mutableStateOf(false) }
    var showMercaderiasWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeSubView, activeJornadaId) {
        if (activeJornada != null && activeJornada.isOpen && activeJornadaId != null) {
            if (activeSubView == "PRODUCCION" && acknowledgedProduccionWarningJornadaId != activeJornadaId) {
                showProduccionWarningDialog = true
            } else if (activeSubView == "MERCADERIAS" && acknowledgedMercaderiasWarningJornadaId != activeJornadaId) {
                showMercaderiasWarningDialog = true
            }
        }
    }

    // Dialog state
    var selectedMateriaPrimaForMov by remember { mutableStateOf<MateriaPrima?>(null) }
    var selectedMercaderiaForMov by remember { mutableStateOf<Mercaderia?>(null) }
    var showTandaRegistration by remember { mutableStateOf(false) }

    when (activeSubView) {
        "MENU" -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DuenoSubscreenHeader(
                    title = "Inventario",
                    subtitle = "Seleccione una opción de control",
                    icon = Icons.Outlined.Inventory2,
                    onBack = onBack
                )
                
                // Options (Exclusivamente PRODUCCIÓN y MERCADERÍAS)
                val allOptions = listOf(
                    Triple(
                        "PRODUCCION",
                        "PRODUCCIÓN",
                        if (canProduccion) "Control de materias primas, recetas y tandas de elaboración." else "Acceso restringido para este usuario."
                    ),
                    Triple(
                        "MERCADERIAS",
                        "MERCADERÍAS",
                        if (canMercancias) "Control de existencias de bebidas, confiterías y productos de reventa." else "Acceso restringido para este usuario."
                    )
                )

                val options = allOptions.filter { (subKey, _, _) ->
                    com.example.util.DuenoSessionPreferences.isModuleVisible(visibleModules, subKey)
                }

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        options.forEach { (subView, title, desc) ->
                            val isPermitted = when (subView) {
                                "PRODUCCION" -> canProduccion
                                "MERCADERIAS" -> canMercancias
                                else -> true
                            }
                            InventarioMenuCard(
                                title = title,
                                description = desc,
                                icon = when (subView) {
                                    "PRODUCCION" -> Icons.Outlined.Factory
                                    else -> Icons.Outlined.Storefront
                                },
                                isEnabled = isPermitted,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (isPermitted) {
                                        activeSubView = subView
                                    } else {
                                        Toast.makeText(context, "Acceso no autorizado.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        options.forEach { (subView, title, desc) ->
                            val isPermitted = when (subView) {
                                "PRODUCCION" -> canProduccion
                                "MERCADERIAS" -> canMercancias
                                else -> true
                            }
                            InventarioMenuCard(
                                title = title,
                                description = desc,
                                icon = when (subView) {
                                    "PRODUCCION" -> Icons.Outlined.Factory
                                    else -> Icons.Outlined.Storefront
                                },
                                isEnabled = isPermitted,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (isPermitted) {
                                        activeSubView = subView
                                    } else {
                                        Toast.makeText(context, "Acceso no autorizado.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
        "CATALOGO" -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DuenoSubscreenHeader(
                    title = "Catálogo de Productos",
                    subtitle = "Gestión de cartas, precios y categorías",
                    icon = Icons.Outlined.MenuBook,
                    onBack = handleSubscreenBack
                )
                CatalogoSubScreen(uiState = uiState, viewModel = viewModel)
            }
        }
        "PRODUCCION" -> {
            if (canProduccion) {
                DuenoProduccionSubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    isWide = isWide,
                    onBack = handleSubscreenBack,
                    onRegisterMpMovement = { selectedMateriaPrimaForMov = it },
                    onStartTanda = { showTandaRegistration = true }
                )
            } else {
                AccessDeniedSubscreen(title = "Producción", onBack = handleSubscreenBack)
            }
        }
        "MERCADERIAS" -> {
            if (canMercancias) {
                DuenoMercaderiasSubscreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    isWide = isWide,
                    onBack = handleSubscreenBack,
                    onRegisterMov = { selectedMercaderiaForMov = it }
                )
            } else {
                AccessDeniedSubscreen(title = "Mercadería", onBack = handleSubscreenBack)
            }
        }
        "HISTORIAL" -> {
            DuenoHistorialSubscreen(
                uiState = uiState,
                viewModel = viewModel,
                isWide = isWide,
                onBack = handleSubscreenBack
            )
        }
    }

    // Dialogs
    if (selectedMateriaPrimaForMov != null) {
        RegisterMateriaPrimaMovDialog(
            materiaPrima = selectedMateriaPrimaForMov!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedMateriaPrimaForMov = null }
        )
    }

    if (selectedMercaderiaForMov != null) {
        RegisterMercaderiaMovDialog(
            mercaderia = selectedMercaderiaForMov!!,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { selectedMercaderiaForMov = null }
        )
    }

    if (showTandaRegistration) {
        RegisterTandaDialog(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { showTandaRegistration = false }
        )
    }

    // Advertencia informativa al entrar a Producción con Jornada abierta
    if (showProduccionWarningDialog) {
        AlertDialog(
            onDismissRequest = {
                acknowledgedProduccionWarningJornadaId = activeJornadaId
                showProduccionWarningDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = ElQadreGoldDark,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Aviso de Jornada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Se recomienda comprobar/actualizar el inventario antes de registrar nuevas tandas.",
                    fontSize = 14.sp,
                    color = Slate700,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        acknowledgedProduccionWarningJornadaId = activeJornadaId
                        showProduccionWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_entendido_aviso_produccion")
                ) {
                    Text("ENTENDIDO", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Advertencia informativa al entrar a Mercaderías con Jornada abierta
    if (showMercaderiasWarningDialog) {
        AlertDialog(
            onDismissRequest = {
                acknowledgedMercaderiasWarningJornadaId = activeJornadaId
                showMercaderiasWarningDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = ElQadreGoldDark,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Aviso de Jornada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Se recomienda comprobar/actualizar las existencias de almacén antes de registrar movimientos de la jornada.",
                    fontSize = 14.sp,
                    color = Slate700,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        acknowledgedMercaderiasWarningJornadaId = activeJornadaId
                        showMercaderiasWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_entendido_aviso_mercaderias")
                ) {
                    Text("ENTENDIDO", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun AccessDeniedSubscreen(
    title: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = title,
            subtitle = "Módulo restringido",
            icon = Icons.Outlined.Lock,
            onBack = onBack
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "ACCESO NO AUTORIZADO",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
                Text(
                    text = "No se han concedido permisos para el control de $title a este usuario.",
                    fontSize = 13.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Volver al Menú de Inventario", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InventarioMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isEnabled) Color.White else Slate100,
        border = BorderStroke(if (isEnabled) 1.5.dp else 1.dp, if (isEnabled) ElQadreNavy.copy(alpha = 0.2f) else Slate300),
        shadowElevation = if (isEnabled) 3.dp else 0.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isEnabled) ElQadreNavy.copy(alpha = 0.08f) else Slate200,
                border = BorderStroke(1.dp, if (isEnabled) ElQadreNavy.copy(alpha = 0.15f) else Slate300),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (!isEnabled) Icons.Outlined.Lock else icon,
                        contentDescription = null,
                        tint = if (isEnabled) ElQadreNavy else Slate400,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (isEnabled) ElQadreNavy else Slate500,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                fontSize = 14.sp,
                color = if (isEnabled) Slate700 else Slate400,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            if (!isEnabled) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = "ACCESO RESTRINGIDO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElQadreNavy.copy(alpha = 0.06f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Acceder al módulo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DuenoProduccionSubscreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit,
    onRegisterMpMovement: ((MateriaPrima) -> Unit)? = null,
    onStartTanda: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val materiasPrimas = uiState.materiasPrimas
    val movimientos = uiState.movimientosMateriaPrima
    val productosElaborados = uiState.productosElaborados
    val products = uiState.products
    val recetas = uiState.recetaIngredientes

    val produccionProducts = remember(products, productosElaborados) {
        products.filter { it.destination == "COCINA" || it.id in productosElaborados.map { pe -> pe.productId } }
    }

    val openTanda = remember(uiState.tandas) {
        uiState.tandas.firstOrNull { it.status != "CERRADA" }
    }

    // Dialog & Interaction States
    var showNuevoInsumoDialog by remember { mutableStateOf(false) }
    var showNuevoProductoDialog by remember { mutableStateOf(false) }
    var showNuevaTandaDialog by remember { mutableStateOf(false) }

    var selectedInsumoForEntrada by remember { mutableStateOf<MateriaPrima?>(null) }
    var insumoToDelete by remember { mutableStateOf<MateriaPrima?>(null) }
    var insumoToToggleAgregado by remember { mutableStateOf<MateriaPrima?>(null) }
    var insumoToEdit by remember { mutableStateOf<MateriaPrima?>(null) }

    var selectedProductToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var selectedProductForRecipe by remember { mutableStateOf<Product?>(null) }
    var selectedProductForFichaCosto by remember { mutableStateOf<Product?>(null) }
    var showAddIngredientDialog by remember { mutableStateOf(false) }
    var selectedIngredientForEdit by remember { mutableStateOf<RecetaIngrediente?>(null) }

    var tandaToClose by remember { mutableStateOf<Tanda?>(null) }
    var tandaToAdjust by remember { mutableStateOf<Tanda?>(null) }
    var selectedTandaForRealSale by remember { mutableStateOf<Tanda?>(null) }
    var selectedDateFilter by remember { mutableStateOf("HOY") } // "HOY", "AYER", "7_DIAS", "TODAS"

    // Selected Tab State: "INSUMOS", "PRODUCTOS", "TANDAS"
    var selectedTab by remember { mutableStateOf("INSUMOS") }

    // Modal detail dialog states for summarized cards
    var selectedInsumoForDetail by remember { mutableStateOf<MateriaPrima?>(null) }
    var selectedAgregadoForSalidaVenta by remember { mutableStateOf<MateriaPrima?>(null) }
    var selectedMateriaPrimaForMov by remember { mutableStateOf<MateriaPrima?>(null) }
    var selectedProductForDetail by remember { mutableStateOf<Product?>(null) }
    var selectedTandaForDetail by remember { mutableStateOf<Tanda?>(null) }

    // Product stock movements (Entrada / Merma)
    var selectedProductForEntrada by remember { mutableStateOf<Product?>(null) }
    var selectedProductForMerma by remember { mutableStateOf<Product?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Producción",
            subtitle = "Control de insumos, productos y tandas de elaboración",
            icon = Icons.Outlined.Factory,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        // ==========================================================
        // 1. BOTONES SUPERIORES: INSUMOS | PRODUCTOS | TANDAS
        // ==========================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // BOTÓN INSUMOS
            Button(
                onClick = { selectedTab = "INSUMOS" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "INSUMOS") ElQadreNavy else Slate100,
                    contentColor = if (selectedTab == "INSUMOS") Color.White else Slate700
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (selectedTab == "INSUMOS") ElQadreNavy else Slate300),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_toggle_insumos")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedTab == "INSUMOS") ElQadreGold else Slate600
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "INSUMOS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selectedTab == "INSUMOS") Color.White else Slate800
                    )
                }
            }

            // BOTÓN PRODUCTOS
            Button(
                onClick = { selectedTab = "PRODUCTOS" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "PRODUCTOS") ElQadreNavy else Slate100,
                    contentColor = if (selectedTab == "PRODUCTOS") Color.White else Slate700
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (selectedTab == "PRODUCTOS") ElQadreNavy else Slate300),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_toggle_productos")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedTab == "PRODUCTOS") ElQadreGold else Slate600
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "PRODUCTOS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selectedTab == "PRODUCTOS") Color.White else Slate800
                    )
                }
            }

            // BOTÓN TANDAS
            Button(
                onClick = { selectedTab = "TANDAS" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedTab == "TANDAS") ElQadreNavy else Slate100,
                    contentColor = if (selectedTab == "TANDAS") Color.White else Slate700
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, if (selectedTab == "TANDAS") ElQadreNavy else Slate300),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("btn_toggle_tandas")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (selectedTab == "TANDAS") ElQadreGold else Slate600
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "TANDAS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = if (selectedTab == "TANDAS") Color.White else Slate800
                    )
                }
            }
        }

        // ==========================================================
        // 2. CONTENIDO SEGÚN BOTÓN SELECCIONADO (SIN REPETIR NOMBRE)
        // ==========================================================
        when (selectedTab) {
            "INSUMOS" -> {
                Button(
                    onClick = { showNuevoInsumoDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_agregar_insumo")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NUEVO INSUMO", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                }

                val agregadosList = remember(materiasPrimas) { materiasPrimas.filter { it.isAgregado } }
                val standardInsumosList = remember(materiasPrimas) { materiasPrimas.filter { !it.isAgregado } }

                if (materiasPrimas.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No hay insumos o materias primas registradas. Puede agregar uno con 'NUEVO INSUMO'.",
                            color = Slate500,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // SECCIÓN 1: AGREGADOS (CONTROL POR RACIONES)
                        if (agregadosList.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.5.dp, Color(0xFFBBF7D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(22.dp))
                                        Text(
                                            text = "AGREGADOS (CONTROL POR RACIONES)",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = Color(0xFF15803D)
                                        )
                                    }
                                }

                                agregadosList.forEach { mp ->
                                    val assocProdName = uiState.products.find { it.id == mp.productId }?.name
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                                        shadowElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedInsumoForDetail = mp }
                                            .testTag("card_agregado_${mp.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = mp.name.uppercase(),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 16.sp,
                                                        color = ElQadreNavy,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFFDCFCE7)
                                                    ) {
                                                        Text(
                                                            text = "AGREGADO",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color(0xFF15803D),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = if (assocProdName != null) "✓ Producto Asociado: $assocProdName" else "• Agregado General (Catálogo)",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F766E)
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "${"%.1f".format(mp.racionesDisponibles)} raciones",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF15803D)
                                                    )
                                                    Text(
                                                        text = "•",
                                                        fontSize = 13.sp,
                                                        color = Slate400
                                                    )
                                                    Text(
                                                        text = "${"%.1f".format(mp.stock)} ${mp.unit}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate700
                                                    )
                                                    Text(
                                                        text = "•",
                                                        fontSize = 13.sp,
                                                        color = Slate400
                                                    )
                                                    Text(
                                                        text = "$${"%.2f".format(mp.precioEfectivoVenta)} CUP",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = ElQadreNavy
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Default.ChevronRight,
                                                contentDescription = "Ver Detalle Agregado",
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // SECCIÓN 2: INSUMOS Y MATERIAS PRIMAS BASE
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (agregadosList.isNotEmpty()) {
                                Text(
                                    text = "INSUMOS Y MATERIAS PRIMAS BASE",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = ElQadreNavy,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            if (standardInsumosList.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Slate200),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No hay insumos base adicionales.",
                                        color = Slate500,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                standardInsumosList.forEach { mp ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Slate200),
                                        shadowElevation = 2.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedInsumoForDetail = mp }
                                            .testTag("card_insumo_${mp.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mp.name.uppercase(),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 16.sp,
                                                    color = ElQadreNavy,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "Existencia: ${"%.1f".format(mp.stock)} ${mp.unit}",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFF0F766E)
                                                    )
                                                    Text(
                                                        text = "•",
                                                        fontSize = 13.sp,
                                                        color = Slate400
                                                    )
                                                    Text(
                                                        text = "Costo: $${"%.2f".format(mp.unitCost)}/${mp.unit}",
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Slate700
                                                    )
                                                }
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { insumoToToggleAgregado = mp },
                                                    modifier = Modifier.size(36.dp).testTag("btn_convert_to_agregado_${mp.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.ShoppingCart,
                                                        contentDescription = "Convertir en Agregado",
                                                        tint = Color(0xFF16A34A),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = "Ver Detalle",
                                                    tint = Slate400,
                                                    modifier = Modifier.size(24.dp)
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

            "PRODUCTOS" -> {
                Button(
                    onClick = { showNuevoProductoDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_agregar_producto")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NUEVO PRODUCTO", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                }

                if (produccionProducts.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No hay productos de producción registrados. Puede agregar uno con 'NUEVO PRODUCTO'.",
                            color = Slate500,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        produccionProducts.forEach { prod ->
                            val pe = productosElaborados.find { it.productId == prod.id }
                            val peReceta = recetas.filter { it.productoElaboradoId == prod.id || (pe != null && it.productoElaboradoId == pe.id) }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductForDetail = prod }
                                    .testTag("card_producto_${prod.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name.uppercase(),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp,
                                            color = ElQadreNavy,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Precio: $${"%.2f".format(prod.price)} CUP",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 13.sp,
                                                color = Slate400
                                            )
                                            Text(
                                                text = "Receta: ${peReceta.size} ingredientes",
                                                fontSize = 13.sp,
                                                color = Slate600
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Ver Detalle",
                                        tint = Slate400,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "TANDAS" -> {
                val isJornadaOpen = uiState.activeJornada?.isOpen == true

                Button(
                    onClick = {
                        if (!isJornadaOpen) {
                            Toast.makeText(
                                context,
                                "No se puede abrir una tanda porque no hay una Jornada abierta. Inicie una jornada primero.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (openTanda != null) {
                            Toast.makeText(
                                context,
                                "Solo puede existir una tanda abierta a la vez. Debe cerrar la Tanda #${openTanda.tandaNumber} antes de iniciar una nueva.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showNuevaTandaDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isJornadaOpen && openTanda == null) ElQadreNavy else Slate400,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("btn_agregar_tanda")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("NUEVA TANDA", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                }

                // ==========================================================
                // 1. TANDA ABIERTA (SECCIÓN DESTACADA SUPERIOR)
                // ==========================================================
                if (openTanda != null) {
                    val prod = products.find { it.id == openTanda.productId }
                    val catPrice = if (prod != null && prod.price > 0.0) prod.price else openTanda.salePrice
                    val actualQty = if (openTanda.actualYield > 0.0) openTanda.actualYield else (if (openTanda.expectedYield > 0.0) openTanda.expectedYield else openTanda.estimatedYield)
                    val restQty = (actualQty - openTanda.quantitySold).coerceAtLeast(0.0)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(2.dp, Color(0xFFF59E0B)),
                        shadowElevation = 3.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTandaForDetail = openTanda }
                            .testTag("card_tanda_abierta")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "TANDA #${openTanda.tandaNumber}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFF59E0B)
                                    ) {
                                        Text(
                                            text = "EN PRODUCCIÓN",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Ver Detalle",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = openTanda.productName.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = ElQadreNavy
                            )

                            HorizontalDivider(color = Color(0xFFFDE68A))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Insumo base utilizado:", fontSize = 13.sp, color = Slate700)
                                Text("${"%.1f".format(openTanda.baseQuantityUsed)} ${openTanda.baseQuantityUnit} (${openTanda.baseMateriaPrimaName.ifBlank { "Insumo principal" }})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Producción esperada:", fontSize = 13.sp, color = Slate700)
                                Text("${"%.1f".format(openTanda.expectedYield)} ${openTanda.productionUnit}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo total:", fontSize = 13.sp, color = Slate700)
                                Text("$${"%.2f".format(openTanda.totalBatchCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo unitario / Precio venta:", fontSize = 13.sp, color = Slate700)
                                Text("$${"%.2f".format(openTanda.realUnitCost)} / $${"%.2f".format(catPrice)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ingreso esperado:", fontSize = 13.sp, color = Slate700)
                                Text("$${"%.2f".format(openTanda.expectedRevenue)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0F766E))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Obtenido real / Restante:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                Text("${"%.1f".format(actualQty)} / ${"%.1f".format(restQty)} ${openTanda.productionUnit}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreNavy)
                            }

                            HorizontalDivider(color = Color(0xFFFDE68A))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { tandaToAdjust = openTanda },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("AJUSTAR TANDA", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }

                                Button(
                                    onClick = { tandaToClose = openTanda },
                                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("CERRAR TANDA", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // ==========================================================
                // 2. HISTORIAL DE TANDAS ANTERIORES
                // ==========================================================
                Text(
                    text = "HISTORIAL DE TANDAS ANTERIORES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // FILTROS DE FECHA
                val todayStartMs = remember {
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }
                val yesterdayStartMs = remember { todayStartMs - (24 * 60 * 60 * 1000L) }
                val sevenDaysAgoMs = remember { todayStartMs - (7 * 24 * 60 * 60 * 1000L) }
                val monthStartMs = remember {
                    java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.DAY_OF_MONTH, 1)
                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                        set(java.util.Calendar.MINUTE, 0)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }.timeInMillis
                }

                val filteredTandas = remember(uiState.tandas, selectedDateFilter) {
                    when (selectedDateFilter) {
                        "HOY" -> uiState.tandas.filter { it.date >= todayStartMs }
                        "AYER" -> uiState.tandas.filter { it.date in yesterdayStartMs until todayStartMs }
                        "7_DIAS" -> uiState.tandas.filter { it.date >= sevenDaysAgoMs }
                        "MES" -> uiState.tandas.filter { it.date >= monthStartMs }
                        else -> uiState.tandas
                    }
                }

                val closedTandas = remember(filteredTandas) {
                    filteredTandas.filter { it.status == "CERRADA" }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("HOY" to "Hoy", "AYER" to "Ayer", "7_DIAS" to "7 Días", "MES" to "Mes", "TODAS" to "Todas").forEach { (filterKey, label) ->
                        val isSelected = selectedDateFilter == filterKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDateFilter = filterKey },
                            label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElQadreNavy,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Slate700
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) ElQadreNavy else Slate200,
                                selectedBorderColor = ElQadreNavy,
                                borderWidth = 1.dp
                            ),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                if (closedTandas.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        closedTandas.forEach { tanda ->
                            val actualQty = if (tanda.actualYield > 0.0) tanda.actualYield else (if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield)

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate200),
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTandaForDetail = tanda }
                                    .testTag("card_tanda_${tanda.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Tanda #${tanda.tandaNumber} • ${tanda.productName}",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                color = ElQadreNavy,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFECFDF5)
                                            ) {
                                                Text(
                                                    text = "CERRADA",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF047857),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "Prod: ${"%.1f".format(actualQty)} ${tanda.productionUnit}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 13.sp,
                                                color = Slate400
                                            )
                                            Text(
                                                text = "Vendido: ${"%.1f".format(tanda.quantitySold)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF047857)
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 13.sp,
                                                color = Slate400
                                            )
                                            Text(
                                                text = "Beneficio: $${"%.2f".format(tanda.estimatedProfit)} CUP",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF0F766E)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Ver Detalle",
                                        tint = Slate400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No hay tandas cerradas en el período seleccionado.",
                            color = Slate500,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        }

        // ==========================================================
        // DETALLE MODAL: INSUMO (GRANDE, LEGIBLE Y CÓMODO PARA ADULTO MAYOR)
        // ==========================================================
        if (selectedInsumoForDetail != null) {
            val mp = selectedInsumoForDetail!!
            val mpMovs = movimientos.filter { it.materiaPrimaId == mp.id }
            val entries = mpMovs.filter { it.type == "ENTRADA" }.sumOf { it.quantity }
            val exits = mpMovs.filter { it.type == "SALIDA" || it.type == "MERMA" || it.type == "TANDA_CONSUMO" }.sumOf { it.quantity }

            Dialog(
                onDismissRequest = { selectedInsumoForDetail = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, ElQadreNavy),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .padding(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ENCABEZADO
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mp.name.uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "DETALLE Y CONTROL DE INSUMO",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate500
                                )
                            }
                            IconButton(
                                onClick = { selectedInsumoForDetail = null },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate700, modifier = Modifier.size(28.dp))
                            }
                        }

                        HorizontalDivider(color = Slate200, thickness = 2.dp)

                        // DATOS DE LECTURA CLARA Y GRANDE PARA ADULTO MAYOR
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // COSTO UNITARIO Y UNIDAD DE MEDIDA
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.5.dp, Emerald600),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Costo Unitario:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                                        Text(
                                            "$${"%.2f".format(mp.unitCost)} / ${mp.unit}",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF0F766E)
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Unidad de Medida:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        Text(
                                            mp.unit,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = ElQadreNavy
                                        )
                                    }
                                }
                            }

                            // TABLA DE STOCKS Y MOVIMIENTOS
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Slate50,
                                border = BorderStroke(1.dp, Slate300),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("• Stock Inicial:", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                        Text("${"%.1f".format(mp.initialStock)} ${mp.unit}", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Slate900)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("• Entradas de la Jornada:", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                        Text("+${"%.1f".format(entries)} ${mp.unit}", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("• Salidas / Consumo Tandas:", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                        Text("-${"%.1f".format(exits)} ${mp.unit}", fontSize = 19.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                    }
                                }
                            }

                            // EXISTENCIA RESULTANTE
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = ElQadreNavy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("EXISTENCIA RESULTANTE:", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Text("${"%.1f".format(mp.stock)} ${mp.unit}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = ElQadreGold)
                                }
                            }

                            // INFORMACIÓN ADICIONAL DE AGREGADO
                            if (mp.isAgregado) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "CONTROL DE AGREGADO POR RACIONES",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = Color(0xFF15803D)
                                        )
                                        val assocProd = uiState.products.find { it.id == mp.productId }
                                        if (assocProd != null) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Producto Asociado:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                                Text("${assocProd.name} (${assocProd.code})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                            }
                                        }
                                        HorizontalDivider(color = Color(0xFFDCFCE7))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Raciones Disponibles en Almacén:", fontSize = 13.sp, color = Slate700)
                                            Text("${"%.1f".format(mp.racionesDisponibles)} raciones", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Cantidad por Ración:", fontSize = 13.sp, color = Slate700)
                                            Text("${mp.rationQuantity} ${mp.rationUnit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Costo por Ración:", fontSize = 13.sp, color = Slate700)
                                            Text("$${"%.2f".format(mp.costoPorRacion)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Precio Sugerido (+30% Margen):", fontSize = 13.sp, color = Slate700)
                                            Text("$${"%.2f".format(mp.precioSugeridoCalculado)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Precio de Venta Efectivo:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                            Text("$${"%.2f".format(mp.precioEfectivoVenta)}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Slate200, thickness = 2.dp)

                        // BOTÓN PARA ACTIVAR / CONFIGURAR O DESACTIVAR MODO AGREGADO
                        Button(
                            onClick = {
                                val target = mp
                                selectedInsumoForDetail = null
                                insumoToToggleAgregado = target
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (mp.isAgregado) Color(0xFF15803D) else Color(0xFF0F766E)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_insumo_toggle_agregado_detail")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White)
                                Text(
                                    text = if (mp.isAgregado) "CONFIGURAR / DESACTIVAR AGREGADO" else "CONVERTIR EN AGREGADO (ACTIVAR)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        // BOTÓN DE ACCIÓN ESPECIAL PARA AGREGADOS: SALIDA PARA VENTA
                        if (mp.isAgregado) {
                            Button(
                                onClick = {
                                    val target = mp
                                    selectedInsumoForDetail = null
                                    selectedAgregadoForSalidaVenta = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("btn_agregado_salida_venta")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White)
                                    Text("SALIDA PARA VENTA (ENVIAR RACIONES A VENTA)", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                        }

                        // BOTÓN EDITAR DATOS COMPLETOS DE INSUMO
                        OutlinedButton(
                            onClick = {
                                val target = mp
                                selectedInsumoForDetail = null
                                insumoToEdit = target
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, ElQadreNavy),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_insumo_edit_full_detail")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = ElQadreNavy)
                                Text(
                                    text = "EDITAR DATOS DE INSUMO",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }
                        }

                        // BOTONES DE ACCIÓN: ENTRADA | MERMA | ELIMINAR
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val target = mp
                                    selectedInsumoForDetail = null
                                    selectedInsumoForEntrada = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("btn_insumo_detail_entrada"),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("ENTRADA", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }

                            Button(
                                onClick = {
                                    val target = mp
                                    selectedInsumoForDetail = null
                                    selectedMateriaPrimaForMov = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("btn_insumo_detail_merma"),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("MERMA", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }

                            Button(
                                onClick = {
                                    val target = mp
                                    selectedInsumoForDetail = null
                                    insumoToDelete = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                                    .testTag("btn_insumo_detail_eliminar"),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("ELIMINAR", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedAgregadoForSalidaVenta != null) {
            val mpAgregado = selectedAgregadoForSalidaVenta!!
            SalidaParaVentaAgregadoDialog(
                materiaPrima = mpAgregado,
                onDismiss = { selectedAgregadoForSalidaVenta = null },
                onConfirm = { cantidadFisica ->
                    viewModel.registrarSalidaParaVentaAgregado(mpAgregado.id, cantidadFisica)
                    selectedAgregadoForSalidaVenta = null
                }
            )
        }

        // ==========================================================
        // DETALLE MODAL: PRODUCTO
        // ==========================================================
        if (selectedProductForDetail != null) {
            val prod = selectedProductForDetail!!
            val pe = productosElaborados.find { it.productId == prod.id }
            val peReceta = recetas.filter { it.productoElaboradoId == prod.id || (pe != null && it.productoElaboradoId == pe.id) }

            Dialog(
                onDismissRequest = { selectedProductForDetail = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, ElQadreNavy),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .padding(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ENCABEZADO
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prod.name.uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    color = ElQadreNavy
                                )
                                Text(
                                    text = "DETALLE DE PRODUCTO DE PRODUCCIÓN",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Slate500
                                )
                            }
                            IconButton(
                                onClick = { selectedProductForDetail = null },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate700, modifier = Modifier.size(26.dp))
                            }
                        }

                        HorizontalDivider(color = Slate200, thickness = 2.dp)

                        // BADGES DE CATEGORÍA Y ESTADO
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Slate100) {
                                Text(prod.category, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEFF6FF)) {
                                Text(prod.code, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = if (prod.isAvailable) Color(0xFFECFDF5) else Color(0xFFFEF2F2)) {
                                Text(if (prod.isAvailable) "ACTIVO" else "INACTIVO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (prod.isAvailable) Color(0xFF047857) else Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }

                        // PRECIO Y RENDIMIENTO EN TARJETAS DESTACADAS
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Precio de Venta:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    Text(
                                        "$${"%.2f".format(prod.price)} CUP / ${prod.unitOfMeasure}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                }
                                HorizontalDivider(color = Slate200)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Rendimiento Base:", fontSize = 14.sp, color = Slate600)
                                    Text(
                                        "${pe?.baseYield ?: 1.0} ${pe?.productionUnit ?: prod.unitOfMeasure} / lote",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("PPD Estimado:", fontSize = 14.sp, color = Slate600)
                                    Text(
                                        "${pe?.effectivePpd ?: 10.0} ud/día",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700
                                    )
                                }
                            }
                        }

                        // RECETA / INGREDIENTES
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("INGREDIENTES DE LA RECETA:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                if (peReceta.isNotEmpty()) {
                                    peReceta.forEach { ing ->
                                        val raw = materiasPrimas.find { it.id == ing.materiaPrimaId }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("• ${raw?.name ?: "Insumo"}", fontSize = 13.sp, color = Slate700)
                                            Text("${"%.1f".format(ing.quantity)} ${ing.unit}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Sin ingredientes configurados en la receta",
                                        fontSize = 13.sp,
                                        color = Slate400,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Slate200, thickness = 2.dp)

                        // ACCIONES ERGONÓMICAS Y VISIBLES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val target = prod
                                    selectedProductForDetail = null
                                    selectedProductForRecipe = target
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Receta", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val target = prod
                                    selectedProductForDetail = null
                                    selectedProductForFichaCosto = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.3f).height(50.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Ficha Costo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            IconButton(
                                onClick = {
                                    val target = prod
                                    selectedProductForDetail = null
                                    selectedProductToEdit = target
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = ElQadreNavy, modifier = Modifier.size(22.dp))
                            }

                            IconButton(
                                onClick = {
                                    val target = prod
                                    selectedProductForDetail = null
                                    productToDelete = target
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFFEE2E2)),
                                modifier = Modifier.size(50.dp)
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }

        // ==========================================================
        // DETALLE MODAL: TANDA
        // ==========================================================
        if (selectedTandaForDetail != null) {
            TandaDetailDialog(
                tanda = selectedTandaForDetail!!,
                uiState = uiState,
                onDismiss = { selectedTandaForDetail = null },
                onAjustarTanda = { tanda ->
                    selectedTandaForDetail = null
                    tandaToAdjust = tanda
                },
                onCerrarTanda = { tanda ->
                    selectedTandaForDetail = null
                    tandaToClose = tanda
                }
            )
        }

        // ==========================================================
        // DIÁLOGOS
        // ==========================================================
        if (showNuevaTandaDialog) {
            RegisterTandaDialog(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showNuevaTandaDialog = false }
            )
        }

        if (showNuevoInsumoDialog) {
            AddEditMateriaPrimaDialog(
                materia = null,
                isJornadaOpen = uiState.activeJornada?.isOpen == true,
                uiState = uiState,
                onDismiss = { showNuevoInsumoDialog = false },
                onConfirm = { m, entQty, entUnit, entNotes, _, _, _ ->
                    viewModel.insertMateriaPrima(m)
                    if (entQty > 0.0) {
                        val username = uiState.currentUser?.username ?: "Dueño"
                        val entQtyBase = convertToBaseQty(entQty, entUnit)
                        viewModel.insertMovimientoMateriaPrima(
                            materiaPrimaId = m.id,
                            type = "ENTRADA",
                            quantity = entQtyBase,
                            notes = entNotes.ifEmpty { "Entrada inicial de insumo creado por Dueño" },
                            responsibleUser = username
                        )
                    }
                    showNuevoInsumoDialog = false
                }
            )
        }

        if (showNuevoProductoDialog) {
            AddEditProductoElaboradoDialog(
                producto = null,
                uiState = uiState,
                onDismiss = { showNuevoProductoDialog = false },
                onConfirm = { prod, ppdVal ->
                    viewModel.createProductElaborado(prod, ppdVal)
                    showNuevoProductoDialog = false
                }
            )
        }

        selectedProductToEdit?.let { prod ->
            AddEditProductoElaboradoDialog(
                producto = prod,
                uiState = uiState,
                onDismiss = { selectedProductToEdit = null },
                onConfirm = { updatedProd, ppdVal ->
                    viewModel.updateProductElaboradoFull(updatedProd, ppdVal)
                    selectedProductToEdit = null
                }
            )
        }

        productToDelete?.let { prod ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { productToDelete = null },
                title = { Text("¿Eliminar Producto?", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
                text = { Text("¿Está seguro de que desea eliminar el producto '${prod.name}'? Se eliminará de Producción y del Catálogo.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProduct(prod.id)
                            productToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Eliminar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { productToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (selectedInsumoForEntrada != null) {
            RegistrarEntradaInsumoDialog(
                materiaPrima = selectedInsumoForEntrada!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedInsumoForEntrada = null }
            )
        }

        if (selectedMateriaPrimaForMov != null) {
            RegisterMateriaPrimaMovDialog(
                materiaPrima = selectedMateriaPrimaForMov!!,
                uiState = uiState,
                viewModel = viewModel,
                initialType = "MERMA",
                onDismiss = { selectedMateriaPrimaForMov = null }
            )
        }

        insumoToDelete?.let { mp ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { insumoToDelete = null },
                title = { Text("¿Eliminar Insumo?", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
                text = { Text("¿Está seguro de que desea eliminar el insumo '${mp.name}'? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMateriaPrima(mp)
                            insumoToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Eliminar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { insumoToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (insumoToToggleAgregado != null) {
            val mpToToggle = insumoToToggleAgregado!!
            ToggleAgregadoInsumoDialog(
                materiaPrima = mpToToggle,
                products = uiState.products,
                onDismiss = { insumoToToggleAgregado = null },
                onSave = { updatedMp ->
                    viewModel.updateMateriaPrima(updatedMp)
                    insumoToToggleAgregado = null
                }
            )
        }

        if (insumoToEdit != null) {
            val mpToEdit = insumoToEdit!!
            AddEditMateriaPrimaDialog(
                materia = mpToEdit,
                isJornadaOpen = uiState.activeJornada?.isOpen == true,
                uiState = uiState,
                onDismiss = { insumoToEdit = null },
                onConfirm = { updatedMp, entQty, entUnit, entNotes, salQty, salUnit, salNotes ->
                    viewModel.updateMateriaPrima(updatedMp)
                    val currentUserStr = viewModel.uiState.value.currentUser?.username ?: "Dueño"
                    if (entQty > 0.0) {
                        val entQtyBase = convertToBaseQty(entQty, entUnit)
                        viewModel.insertMovimientoMateriaPrima(
                            materiaPrimaId = updatedMp.id,
                            type = "ENTRADA",
                            quantity = entQtyBase,
                            notes = if (entNotes.isNotBlank()) entNotes else "Entrada al editar insumo",
                            responsibleUser = currentUserStr
                        )
                    }
                    if (salQty > 0.0) {
                        val salQtyBase = convertToBaseQty(salQty, salUnit)
                        viewModel.insertMovimientoMateriaPrima(
                            materiaPrimaId = updatedMp.id,
                            type = "MERMA",
                            quantity = salQtyBase,
                            notes = if (salNotes.isNotBlank()) salNotes else "Ajuste de salida al editar insumo",
                            responsibleUser = currentUserStr
                        )
                    }
                    insumoToEdit = null
                }
            )
        }

        selectedProductForRecipe?.let { p ->
            val recipeIngredients = uiState.recetaIngredientes.filter { it.productoElaboradoId == p.id }
            RecipeManagementDialog(
                product = p,
                productName = p.name,
                salePrice = p.price,
                ingredients = recipeIngredients,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedProductForRecipe = null },
                onAddIngredientClick = { showAddIngredientDialog = true },
                onEditIngredientClick = { ing -> selectedIngredientForEdit = ing },
                onDeleteIngredient = { ing -> viewModel.deleteRecetaIngrediente(ing) }
            )

            if (showAddIngredientDialog) {
                AddRecipeIngredientDialog(
                    productoElaboradoId = p.id,
                    uiState = uiState,
                    onDismiss = { showAddIngredientDialog = false },
                    onConfirm = { ing ->
                        viewModel.insertRecetaIngrediente(ing)
                        showAddIngredientDialog = false
                    }
                )
            }

            selectedIngredientForEdit?.let { ing ->
                EditRecipeIngredientDialog(
                    ingredient = ing,
                    uiState = uiState,
                    onDismiss = { selectedIngredientForEdit = null },
                    onConfirm = { updated ->
                        viewModel.updateRecetaIngrediente(updated)
                        selectedIngredientForEdit = null
                    },
                    onDelete = {
                        viewModel.deleteRecetaIngrediente(ing)
                        selectedIngredientForEdit = null
                    }
                )
            }
        }

        selectedProductForFichaCosto?.let { p ->
            FichaCostoDialog(
                product = p,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedProductForFichaCosto = null }
            )
        }

        if (tandaToAdjust != null) {
            AjustarTandaDialog(
                tanda = tandaToAdjust!!,
                viewModel = viewModel,
                onDismiss = { tandaToAdjust = null }
            )
        }

        if (tandaToClose != null) {
            CerrarTandaDialog(
                tanda = tandaToClose!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { tandaToClose = null }
            )
        }

        if (selectedTandaForRealSale != null) {
            RegistrarProduccionYVentaTandaDialog(
                tanda = selectedTandaForRealSale!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedTandaForRealSale = null }
            )
        }
    }
}


@Composable
fun DuenoMercaderiasSubscreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit,
    onRegisterMov: (Mercaderia) -> Unit
) {
    val mercaderias = uiState.mercaderias
    val movimientos = uiState.movimientosMercaderia
    var showNewProductDialog by remember { mutableStateOf(false) }
    var showPagosBebidasDialog by remember { mutableStateOf(false) }
    var editingMercaderia by remember { mutableStateOf<Mercaderia?>(null) }
    var selectedMercForCostSheet by remember { mutableStateOf<Mercaderia?>(null) }
    var selectedMercForEntrada by remember { mutableStateOf<Mercaderia?>(null) }
    var selectedMercForMerma by remember { mutableStateOf<Mercaderia?>(null) }
    var selectedMercForParaVenta by remember { mutableStateOf<Mercaderia?>(null) }
    var selectedMercaderiaForDetail by remember { mutableStateOf<Mercaderia?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Mercaderías",
            subtitle = "Control de existencias de bebidas, confiterías y reventa",
            icon = Icons.Outlined.Storefront,
            onBack = onBack
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showNewProductDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(50.dp)
                    .testTag("btn_nuevo_producto_mercaderia")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("NUEVO PRODUCTO", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            }

            Button(
                onClick = { showPagosBebidasDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("btn_pagos_mercaderias")
            ) {
                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PAGOS", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
            }
        }

        if (mercaderias.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No hay productos de mercadería configurados.",
                    color = Slate500,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                mercaderias.forEach { merc ->
                    val product = uiState.products.find { it.id == merc.productId }
                    val currentAlmacenStock = viewModel.getMercaderiaCurrentStock(merc.id, merc.initialStock)
                    val price = product?.price ?: 0.0
                    val isBebida = com.example.util.MercaderiaCategoryHelper.isBebida(product)
                    val isConfitura = com.example.util.MercaderiaCategoryHelper.isConfitura(product)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMercaderiaForDetail = merc }
                            .testTag("card_merc_${merc.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = (product?.name ?: "MERCADERÍA #${merc.id}").uppercase(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = ElQadreNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isBebida) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFEEF2FF)
                                        ) {
                                            Text(
                                                text = "BEBIDAS",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF4338CA),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (isConfitura) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFFDF2F8)
                                        ) {
                                            Text(
                                                text = "CONFITURAS",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFBE185D),
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Stock: ${"%.1f".format(currentAlmacenStock)} ${merc.unitOfMeasure}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                    Text(
                                        text = "Precio: $${"%.2f".format(price)} CUP",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                    Text(
                                        text = "•",
                                        fontSize = 12.sp,
                                        color = Slate400
                                    )
                                    Text(
                                        text = "Costo: $${"%.2f".format(merc.acquisitionCost)} CUP",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                                if (isBebida && uiState.tarifasPagoBebidas.totalPagoPersonalPorUnidad > 0.0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Pago Personal: $${"%.2f".format(uiState.tarifasPagoBebidas.totalPagoPersonalPorUnidad)} CUP/u (Dep: $${"%.2f".format(uiState.tarifasPagoBebidas.pagoDependientePorUnidad)} + Caj: $${"%.2f".format(uiState.tarifasPagoBebidas.pagoCajeroPorUnidad)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4F46E5)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Ver Detalle",
                                tint = Slate400,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // DETALLE MODAL: MERCADERÍA
        if (selectedMercaderiaForDetail != null) {
            val merc = selectedMercaderiaForDetail!!
            val product = uiState.products.find { it.id == merc.productId }
            val prodMovs = movimientos.filter { it.mercaderiaId == merc.id }
            val initialStock = merc.initialStock
            val entries = prodMovs.filter { it.type == "ENTRADA" || it.type == "AJUSTE_POSITIVO" }.sumOf { it.quantity }
            val mermas = prodMovs.filter { it.type == "MERMA" }.sumOf { it.quantity }
            val paraVenta = prodMovs.filter { it.type == "PARA_VENTA" }.sumOf { it.quantity }
            val otherSalidas = prodMovs.filter { it.type == "SALIDA" || it.type == "AJUSTE_NEGATIVO" }.sumOf { it.quantity }
            val currentAlmacenStock = viewModel.getMercaderiaCurrentStock(merc.id, merc.initialStock)
            val realCost = merc.acquisitionCost
            val price = product?.price ?: 0.0
            val marginPct = if (realCost > 0.0) ((price - realCost) / realCost) * 100.0 else 0.0
            val isBebida = com.example.util.MercaderiaCategoryHelper.isBebida(product)
            val isConfitura = com.example.util.MercaderiaCategoryHelper.isConfitura(product)

            AlertDialog(
                onDismissRequest = { selectedMercaderiaForDetail = null },
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = (product?.name ?: "MERCADERÍA #${merc.id}").uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = ElQadreNavy
                            )
                        }
                        Text(
                            text = "Categoría: ${product?.category ?: "Mercadería"} • Almacén y Costos",
                            fontSize = 13.sp,
                            color = Slate500
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEFF6FF)) {
                                Text(
                                    text = "Costo: $${"%.2f".format(realCost)} CUP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFFBEB)) {
                                Text(
                                    text = "Margen: ${"%.1f".format(marginPct)}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFECFDF5)) {
                                Text(
                                    text = "Precio: $${"%.2f".format(price)} CUP",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // SECCIÓN DE PAGOS DE PERSONAL (SOLO LECTURA)
                        if (isBebida) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEEF2FF),
                                border = BorderStroke(1.dp, Color(0xFFC7D2FE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
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
                                            Icon(
                                                imageVector = Icons.Default.Payments,
                                                contentDescription = null,
                                                tint = Color(0xFF4F46E5),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "PAGOS DE PERSONAL ASOCIADOS",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF312E81)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFFE0E7FF)
                                        ) {
                                            Text(
                                                text = "SOLO LECTURA • GLOBAL",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF3730A3),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• Pago Dependiente por unidad:", fontSize = 11.5.sp, color = Slate700)
                                        Text("$${"%.2f".format(uiState.tarifasPagoBebidas.pagoDependientePorUnidad)} CUP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• Pago Cajero por unidad:", fontSize = 11.5.sp, color = Slate700)
                                        Text("$${"%.2f".format(uiState.tarifasPagoBebidas.pagoCajeroPorUnidad)} CUP", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    }

                                    HorizontalDivider(color = Color(0xFFC7D2FE))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "TOTAL PAGO PERSONAL:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF312E81)
                                        )
                                        Text(
                                            text = "$${"%.2f".format(uiState.tarifasPagoBebidas.totalPagoPersonalPorUnidad)} CUP / u",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF4338CA)
                                        )
                                    }

                                    Text(
                                        text = "Tarifas globales configuradas desde Mercaderías → Pagos.",
                                        fontSize = 10.sp,
                                        color = Slate500,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        } else if (isConfitura) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFDF2F8),
                                border = BorderStroke(1.dp, Color(0xFFFBCFE8)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFBE185D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Confituras: Sin pagos de personal asociados (sin comisión de dependiente ni cajero).",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9D174D),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Slate200)

                        // Resumen de Stock en Almacén
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Stock Inicial Almacén:", fontSize = 12.sp, color = Slate600)
                                    Text("${"%.1f".format(initialStock)} ${merc.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Entradas registradas:", fontSize = 12.sp, color = Color(0xFF0F766E))
                                    Text("+${"%.1f".format(entries)} ${merc.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Mermas en Almacén:", fontSize = 12.sp, color = Color(0xFFD97706))
                                    Text("-${"%.1f".format(mermas)} ${merc.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Para Venta (a Local):", fontSize = 12.sp, color = Color(0xFF1D4ED8))
                                    Text("-${"%.1f".format(paraVenta)} ${merc.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                }
                                if (otherSalidas > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Otras Salidas / Ajustes:", fontSize = 12.sp, color = Color(0xFFB45309))
                                        Text("-${"%.1f".format(otherSalidas)} ${merc.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                    }
                                }
                                HorizontalDivider(color = Slate200)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("EXISTENCIA ALMACÉN:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                    Text("${"%.1f".format(currentAlmacenStock)} ${merc.unitOfMeasure}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }
                            }
                        }

                        HorizontalDivider(color = Slate200)

                        // 3 ACCIONES PRINCIPALES OBLIGATORIAS: ENTRADAS | MERMA | PARA VENTA
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    val target = merc
                                    selectedMercaderiaForDetail = null
                                    selectedMercForEntrada = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("ENTRADAS", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = {
                                    val target = merc
                                    selectedMercaderiaForDetail = null
                                    selectedMercForMerma = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("MERMA", fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                            Button(
                                onClick = {
                                    val target = merc
                                    selectedMercaderiaForDetail = null
                                    selectedMercForParaVenta = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(44.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("PARA VENTA", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        // Acciones secundarias
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val target = merc
                                    selectedMercaderiaForDetail = null
                                    selectedMercForCostSheet = target
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Ficha Costo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                            Button(
                                onClick = {
                                    val target = merc
                                    selectedMercaderiaForDetail = null
                                    editingMercaderia = target
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Editar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedMercaderiaForDetail = null }) {
                        Text("CERRAR", fontWeight = FontWeight.Bold, color = Slate700)
                    }
                }
            )
        }

        if (showPagosBebidasDialog) {
            ConfigurarPagosBebidasDialog(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { showPagosBebidasDialog = false }
            )
        }

        if (showNewProductDialog) {
            AddEditMercaderiaDialog(
                uiState = uiState,
                viewModel = viewModel,
                mercaderia = null,
                onDismiss = { showNewProductDialog = false }
            )
        }

        if (editingMercaderia != null) {
            AddEditMercaderiaDialog(
                uiState = uiState,
                viewModel = viewModel,
                mercaderia = editingMercaderia,
                onDismiss = { editingMercaderia = null }
            )
        }

        if (selectedMercForCostSheet != null) {
            FichaCostoMercaderiaDialog(
                mercaderia = selectedMercForCostSheet!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedMercForCostSheet = null }
            )
        }

        if (selectedMercForEntrada != null) {
            RegistrarEntradaMercaderiaDialog(
                mercaderia = selectedMercForEntrada!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedMercForEntrada = null }
            )
        }

        if (selectedMercForMerma != null) {
            RegistrarMermaMercaderiaDialog(
                mercaderia = selectedMercForMerma!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedMercForMerma = null }
            )
        }

        if (selectedMercForParaVenta != null) {
            RegistrarParaVentaMercaderiaDialog(
                mercaderia = selectedMercForParaVenta!!,
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { selectedMercForParaVenta = null }
            )
        }
    }
}

@Composable
fun DuenoHistorialSubscreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit
) {
    var selectedTypeFilter by remember { mutableStateOf("TODOS") } // "TODOS", "PRODUCCION", "MERCADERIA", "TANDA"
    var selectedMovFilter by remember { mutableStateOf("TODOS") } // "TODOS", "ENTRADA", "SALIDA"
    var selectedDateFilter by remember { mutableStateOf("TODOS") } // "TODOS", "HOY", "7_DIAS", "30_DIAS"

    // Construct unified history list
    val mpItems = uiState.movimientosMateriaPrima.map {
        UnifiedHistoryItem(
            id = "MP_${it.id}",
            type = "PRODUCCION",
            productName = it.materiaPrimaName,
            movementType = it.type,
            quantity = it.quantity,
            unit = it.unit,
            date = it.date,
            responsibleUser = it.responsibleUser,
            notes = it.notes
        )
    }

    val prodItems = uiState.stockMovements.map {
        UnifiedHistoryItem(
            id = "SM_${it.id}",
            type = "PRODUCCION",
            productName = it.productName,
            movementType = it.type,
            quantity = it.quantity.toDouble(),
            unit = "u",
            date = it.timestamp,
            responsibleUser = it.recordedBy,
            notes = it.reason
        )
    }

    val mercItems = uiState.movimientosMercaderia.map {
        val merc = uiState.mercaderias.find { m -> m.id == it.mercaderiaId }
        val prod = uiState.products.find { p -> p.id == merc?.productId }
        UnifiedHistoryItem(
            id = "MC_${it.id}",
            type = "MERCADERIA",
            productName = prod?.name ?: "Mercadería #${it.mercaderiaId}",
            movementType = it.type,
            quantity = it.quantity,
            unit = merc?.unitOfMeasure ?: "u",
            date = it.date,
            responsibleUser = it.responsibleAdmin,
            notes = it.notes
        )
    }

    val tandaItems = uiState.tandas.map {
        UnifiedHistoryItem(
            id = "TD_${it.id}",
            type = "TANDA",
            productName = it.productName,
            movementType = "TANDA",
            quantity = if (it.actualYield > 0.0) it.actualYield else it.estimatedYield,
            unit = it.productionUnit,
            date = it.date,
            responsibleUser = it.responsibleUser,
            notes = "Tanda #${it.tandaNumber}. Consumo: ${it.ingredientsConsumedText}. Obs: ${it.observation}"
        )
    }

    val unifiedHistory = remember(uiState.movimientosMateriaPrima, uiState.stockMovements, uiState.movimientosMercaderia, uiState.tandas) {
        (mpItems + prodItems + mercItems + tandaItems).sortedByDescending { it.date }
    }

    val now = System.currentTimeMillis()
    val filteredHistory = remember(unifiedHistory, selectedTypeFilter, selectedMovFilter, selectedDateFilter) {
        unifiedHistory.filter { item ->
            val matchesType = when (selectedTypeFilter) {
                "TODOS" -> true
                "PRODUCCION" -> item.type == "PRODUCCION"
                "MERCADERIA" -> item.type == "MERCADERIA"
                "TANDA" -> item.type == "TANDA"
                else -> true
            }

            val matchesMov = when (selectedMovFilter) {
                "TODOS" -> true
                "ENTRADA" -> item.movementType == "ENTRADA" || item.movementType == "INVENTARIO_INICIAL" || item.movementType == "AJUSTE_POSITIVO"
                "SALIDA" -> item.movementType == "SALIDA" || item.movementType == "TANDA_CONSUMO" || item.movementType == "MERMA" || item.movementType == "AJUSTE_NEGATIVO"
                else -> true
            }

            val matchesDate = when (selectedDateFilter) {
                "TODOS" -> true
                "HOY" -> {
                    val diff = now - item.date
                    diff <= 24 * 60 * 60 * 1000L
                }
                "7_DIAS" -> {
                    val diff = now - item.date
                    diff <= 7 * 24 * 60 * 60 * 1000L
                }
                "30_DIAS" -> {
                    val diff = now - item.date
                    diff <= 30 * 24 * 60 * 60 * 1000L
                }
                else -> true
            }

            matchesType && matchesMov && matchesDate
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Historial de Movimientos",
            subtitle = "Movimientos de existencias y tandas",
            icon = Icons.Outlined.History,
            onBack = onBack
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CATEGORÍA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "TODOS" to "TODOS",
                            "PRODUCCION" to "PROD.",
                            "MERCADERIA" to "MERC.",
                            "TANDA" to "TANDAS"
                        ).forEach { (value, label) ->
                            val isSelected = selectedTypeFilter == value
                            InputChip(
                                selected = isSelected,
                                onClick = { selectedTypeFilter = value },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TIPO DE MOVIMIENTO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "TODOS" to "TODOS",
                            "ENTRADA" to "ENTRADAS (+)",
                            "SALIDA" to "SALIDAS (-)"
                        ).forEach { (value, label) ->
                            val isSelected = selectedMovFilter == value
                            InputChip(
                                selected = isSelected,
                                onClick = { selectedMovFilter = value },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("RANGO DE FECHAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "TODOS" to "TODAS",
                            "HOY" to "HOY",
                            "7_DIAS" to "7 DÍAS",
                            "30_DIAS" to "30 DÍAS"
                        ).forEach { (value, label) ->
                            val isSelected = selectedDateFilter == value
                            InputChip(
                                selected = isSelected,
                                onClick = { selectedDateFilter = value },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOVIMIENTOS ENCONTRADOS (${filteredHistory.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )
        }

        if (filteredHistory.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No se encontraron movimientos con los filtros seleccionados.",
                    color = Slate500,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredHistory.forEach { item ->
                    UnifiedMovementCard(item)
                }
            }
        }
    }
}

@Composable
fun UnifiedMovementCard(item: UnifiedHistoryItem) {
    val isPositive = item.movementType in listOf("ENTRADA", "INVENTARIO_INICIAL", "AJUSTE_POSITIVO")
    val isTanda = item.movementType == "TANDA" || item.movementType == "PRODUCCION_TANDA" || item.movementType == "TANDA_CONSUMO"
    
    val badgeColor = when {
        isTanda -> Color(0xFF4338CA)
        isPositive -> Color(0xFF0F766E)
        else -> Color(0xFFB45309)
    }
    
    val badgeText = when {
        isTanda -> "TANDA"
        isPositive -> "ENTRADA"
        else -> "SALIDA"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeColor.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.type == "PRODUCCION") Color(0xFFF3E8FF) else Color(0xFFECFDF5)
                        ) {
                            Text(
                                text = if (item.type == "PRODUCCION") "PROD." else "MERC.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.type == "PRODUCCION") Color(0xFF7E22CE) else Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.productName.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = ElQadreNavy
                    )
                }

                Text(
                    text = "${if (isPositive) "+" else "-"}${item.quantity} ${item.unit}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = item.notes,
                fontSize = 12.sp,
                color = Slate600
            )

            HorizontalDivider(color = Slate100, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registrado por: ${item.responsibleUser}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
                Text(
                    text = formatTimestamp(item.date),
                    fontSize = 11.sp,
                    color = Slate400
                )
            }
        }
    }
}

@Composable
fun RegistrarEntradaInsumoDialog(
    materiaPrima: MateriaPrima,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var cantidadText by remember { mutableStateOf("") }
    var purchaseUnit by remember { mutableStateOf(materiaPrima.purchaseUnit.ifEmpty { materiaPrima.unit }) }
    var importeText by remember { mutableStateOf("") }
    var gastosText by remember { mutableStateOf("") }

    val baseUnitCategory = getBaseUnit(materiaPrima.unit)
    val compatibleUnits = getCompatibleUnits(baseUnitCategory)

    val cantidadVal = cantidadText.trim().toDoubleOrNull() ?: 0.0
    val importeVal = importeText.trim().toDoubleOrNull() ?: 0.0
    val gastosVal = gastosText.trim().toDoubleOrNull() ?: 0.0

    val totalInversion = importeVal + maxOf(0.0, gastosVal)
    val existenciaAgregadaBase = convertToBaseQty(cantidadVal, purchaseUnit)

    val currentStock = maxOf(0.0, materiaPrima.stock)
    val currentTotalVal = currentStock * materiaPrima.unitCost
    val nextStock = currentStock + existenciaAgregadaBase
    val nextAvgCost = if (nextStock > 0.0) {
        (currentTotalVal + totalInversion) / nextStock
    } else {
        materiaPrima.unitCost
    }

    val canConfirm = cantidadVal > 0.0 && importeVal > 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR
                Surface(
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.ArrowDownward,
                                contentDescription = null,
                                tint = Color(0xFF0F766E),
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "ENTRADA DE INSUMO",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 21.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }

                // CONTENIDO DEL FORMULARIO
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TARJETA DE INFORMACIÓN DEL INSUMO ACTUAL
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = materiaPrima.name.uppercase(),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )

                            HorizontalDivider(color = Slate200)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Existencia Actual:", fontSize = 16.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                Text(
                                    "${"%.1f".format(materiaPrima.stock)} ${materiaPrima.unit}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = ElQadreNavy
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Costo Unitario Actual:", fontSize = 16.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                Text(
                                    "$${"%.2f".format(materiaPrima.unitCost)} / ${materiaPrima.unit}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = Color(0xFF0F766E)
                                )
                            }
                        }
                    }

                    // 1. CANTIDAD Y UNIDAD DE MEDIDA
                    Text(
                        text = "DATOS DE LA ENTRADA (*)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = cantidadText,
                            onValueChange = { cantidadText = it },
                            label = { Text("Cantidad (*)", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            placeholder = { Text("Ej. 10", color = Slate400) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF0F766E),
                                focusedLabelColor = Color(0xFF0F766E)
                            ),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(66.dp)
                                .testTag("entrada_cantidad_input")
                        )

                        // Selector Unidad de Medida
                        var expandedUnit by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedUnit = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(66.dp)
                                    .testTag("entrada_purchase_unit_selector"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, ElQadreNavy)
                            ) {
                                Text(purchaseUnit, color = ElQadreNavy, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(24.dp))
                            }
                            DropdownMenu(
                                expanded = expandedUnit,
                                onDismissRequest = { expandedUnit = false }
                            ) {
                                compatibleUnits.forEach { u ->
                                    DropdownMenuItem(
                                        text = { Text(u, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            purchaseUnit = u
                                            expandedUnit = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. IMPORTE (PRECIO O COSTO TOTAL DE LA COMPRA)
                    OutlinedTextField(
                        value = importeText,
                        onValueChange = { importeText = it },
                        label = { Text("Importe de la Compra ($) (*)", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                        placeholder = { Text("0.00", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0F766E),
                            focusedLabelColor = Color(0xFF0F766E)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .testTag("entrada_importe_input")
                    )

                    // 3. GASTOS DE LA COMPRA (OPCIONAL)
                    OutlinedTextField(
                        value = gastosText,
                        onValueChange = { gastosText = it },
                        label = { Text("Gastos de la Compra ($) (Opcional)", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                        placeholder = { Text("0.00", color = Slate400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFD97706),
                            focusedLabelColor = Color(0xFFD97706)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(66.dp)
                            .testTag("entrada_gastos_input")
                    )

                    // TARJETA DE RECALCULO AUTOMATICO DEL COSTO PROMEDIO
                    if (cantidadVal > 0.0 && importeVal > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.5.dp, Emerald600),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Inversión Total:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    Text("$${"%.2f".format(totalInversion)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Existencia Resultante:", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                    Text("${"%.1f".format(nextStock)} ${materiaPrima.unit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                }

                                HorizontalDivider(color = Emerald600.copy(alpha = 0.3f))

                                Column {
                                    Text(
                                        text = "Nuevo Costo Unitario Recalculado (Promedio Ponderado):",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald800
                                    )
                                    Text(
                                        text = "$${"%.2f".format(nextAvgCost)} / ${materiaPrima.unit}",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0F766E)
                                    )
                                }
                            }
                        }
                    }
                }

                // PIE DE PÁGINA: BOTONES FIJADOS SOBRE LA NAVEGACIÓN DE ANDROID (CON MARGEN DE SEGURIDAD ELEVADO)
                Surface(
                    color = Color.White,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.5.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(2.dp, Slate300),
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                        }

                        Button(
                            onClick = {
                                if (canConfirm) {
                                    val username = uiState.currentUser?.username ?: "Dueño"
                                    viewModel.registerEntradaMateriaPrima(
                                        materiaPrimaId = materiaPrima.id,
                                        cantidad = cantidadVal,
                                        unidad = purchaseUnit,
                                        importe = importeVal,
                                        gastosCompra = gastosVal,
                                        responsibleUser = username,
                                        notes = "Entrada registrada desde Dueño"
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = canConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F766E),
                                disabledContainerColor = Slate300,
                                disabledContentColor = Slate500
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(60.dp)
                                .testTag("confirm_entrada_insumo_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("REGISTRAR ENTRADA", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CerrarTandaDialog(
    tanda: Tanda,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val prod = uiState.products.find { it.id == tanda.productId }
    val unitPrice = if (prod != null && prod.price > 0.0) prod.price else tanda.salePrice
    val estimatedQty = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield

    var cantidadObtenidaText by remember {
        mutableStateOf(if (tanda.actualYield > 0.0) "%.1f".format(tanda.actualYield) else if (estimatedQty > 0.0) "%.1f".format(estimatedQty) else "")
    }
    var cantidadRestanteText by remember { mutableStateOf("0") }
    var observationText by remember { mutableStateOf(tanda.observation) }

    val cantidadObtenida = cantidadObtenidaText.toDoubleOrNull() ?: 0.0
    val cantidadRestante = cantidadRestanteText.toDoubleOrNull() ?: 0.0
    val vendido = (cantidadObtenida - cantidadRestante).coerceAtLeast(0.0)

    val totalBatchCost = tanda.totalBatchCost
    val realUnitCost = if (cantidadObtenida > 0.0) totalBatchCost / cantidadObtenida else tanda.realUnitCost
    val ingresoObtenido = vendido * unitPrice
    val profitOverall = ingresoObtenido - totalBatchCost
    val yieldPct = if (estimatedQty > 0.0) (cantidadObtenida / estimatedQty) * 100.0 else 100.0

    val presentaciones: List<PresentacionEspecial> = remember(prod) {
        prod?.let { parsePresentacionesEspeciales(it.presentacionesEspeciales) } ?: emptyList()
    }

    val canConfirm = cantidadObtenida > 0.0 && cantidadRestante <= cantidadObtenida

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "CERRAR TANDA #${tanda.tandaNumber}",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO SCROLLABLE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // INFO DE LA TANDA
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tanda.productName.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = ElQadreNavy
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Insumo base:", fontSize = 14.sp, color = Slate600)
                                Text("${"%.1f".format(tanda.baseQuantityUsed)} ${tanda.baseQuantityUnit} (${tanda.baseMateriaPrimaName.ifBlank { "Insumo" }})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Producción planificada:", fontSize = 14.sp, color = Slate600)
                                Text("${"%.1f".format(estimatedQty)} ${tanda.productionUnit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo total de insumos:", fontSize = 14.sp, color = Slate600)
                                Text("$${"%.2f".format(totalBatchCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            }
                        }
                    }

                    // CAMPOS PRINCIPALES DE ENTRADA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "CANTIDAD OBTENIDA (*)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = cantidadObtenidaText,
                                onValueChange = { cantidadObtenidaText = it },
                                placeholder = { Text("Ej. 90", fontSize = 16.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("input_cantidad_obtenida"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy)
                            )
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "CANTIDAD RESTANTE (*)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = cantidadRestanteText,
                                onValueChange = { cantidadRestanteText = it },
                                placeholder = { Text("Ej. 5", fontSize = 16.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("input_cantidad_restante"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy)
                            )
                        }
                    }

                    // PRESENTACIONES ESPECIALES EQUIVALENCIAS
                    if (presentaciones.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF0FDF4),
                            border = BorderStroke(1.5.dp, Color(0xFFBBF7D0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("EQUIVALENCIA EN PRESENTACIONES ESPECIALES", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF166534))
                                for (pres in presentaciones) {
                                    val presObt = if (pres.baseEquivalence > 0) cantidadObtenida / pres.baseEquivalence else 0.0
                                    val presVen = if (pres.baseEquivalence > 0) vendido / pres.baseEquivalence else 0.0
                                    val presRest = if (pres.baseEquivalence > 0) cantidadRestante / pres.baseEquivalence else 0.0
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(pres.name.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                                                Text("(1 ${pres.name} = ${pres.baseEquivalence} ${tanda.productionUnit})", fontSize = 12.sp, color = Slate500)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Obtenido: ${"%.1f".format(presObt)}", fontSize = 12.sp, color = Slate700)
                                                Text("Vendido: ${"%.1f".format(presVen)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                                Text("Restante: ${"%.1f".format(presRest)}", fontSize = 12.sp, color = Slate600)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // RESUMEN ECONÓMICO Y DE RESULTADOS
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("RESUMEN DE CIERRE", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Producido (Obtenido):", fontSize = 14.sp, color = Slate700)
                                Text("${"%.1f".format(cantidadObtenida)} ${tanda.productionUnit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vendido (Obtenido - Restante):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                Text("${"%.1f".format(vendido)} ${tanda.productionUnit}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Restante en stock:", fontSize = 14.sp, color = Slate700)
                                Text("${"%.1f".format(cantidadRestante)} ${tanda.productionUnit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }

                            HorizontalDivider(color = Slate200)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Total Tanda:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(totalBatchCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Unitario Real:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(realUnitCost)} CUP / ${tanda.productionUnit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Precio de Venta Unitario:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(unitPrice)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }

                            HorizontalDivider(color = Slate200)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ingreso Obtenido por Venta:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                Text("$${"%.2f".format(ingresoObtenido)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ganancia / Beneficio Estimado:", fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (profitOverall >= 0) Color(0xFF047857) else Color(0xFFDC2626))
                                Text("$${"%.2f".format(profitOverall)} CUP", fontSize = 17.sp, fontWeight = FontWeight.Black, color = if (profitOverall >= 0) Color(0xFF047857) else Color(0xFFDC2626))
                            }
                        }
                    }

                    // OBSERVACIONES
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OBSERVACIONES / NOTAS DE CIERRE", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                        OutlinedTextField(
                            value = observationText,
                            onValueChange = { observationText = it },
                            placeholder = { Text("Ej. Merma de cocción 2 unidades...", fontSize = 15.sp, color = Slate400) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy)
                        )
                    }
                }

                // ACCIONES INFERIORES: CANCELAR Y FINALIZAR
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 32.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                        }

                        Button(
                            onClick = {
                                if (canConfirm) {
                                    val updatedTanda = tanda.copy(
                                        actualYield = cantidadObtenida,
                                        quantitySold = vendido,
                                        yieldPercentage = yieldPct,
                                        realUnitCost = realUnitCost,
                                        salePrice = unitPrice,
                                        realRevenue = ingresoObtenido,
                                        estimatedProfit = profitOverall,
                                        status = "CERRADA",
                                        observation = observationText.trim(),
                                        inventoryDeducted = true
                                    )
                                    viewModel.cerrarTanda(updatedTanda)
                                    onDismiss()
                                }
                            },
                            enabled = canConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(58.dp)
                                .testTag("btn_confirm_cerrar_tanda")
                        ) {
                            Text("FINALIZAR Y CERRAR TANDA", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterMateriaPrimaMovDialog(
    materiaPrima: MateriaPrima,
    uiState: MainUiState,
    viewModel: MainViewModel,
    initialType: String = "ENTRADA",
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(initialType) } // "ENTRADA", "MERMA", "SALIDA", "STOCK_INICIAL"
    var quantityInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REGISTRAR MOVIMIENTO",
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Slate500,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "INSUMO / MATERIA PRIMA:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                        Text(
                            text = materiaPrima.name.uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Stock actual:", fontSize = 13.sp, color = Slate600)
                            Text("${"%.1f".format(materiaPrima.stock)} ${materiaPrima.unit}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TIPO DE OPERACIÓN (*)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "ENTRADA" to "ENTRADA",
                            "MERMA" to "MERMA",
                            "SALIDA" to "SALIDA",
                            "STOCK_INICIAL" to "INICIAL"
                        ).forEach { (value, label) ->
                            val isSelected = type == value
                            Button(
                                onClick = { type = value },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) ElQadreNavy else Slate100,
                                    contentColor = if (isSelected) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CANTIDAD (${materiaPrima.unit}) (*)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        placeholder = { Text("Ej. 10.5", fontSize = 15.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOTAS / JUSTIFICACIÓN (OPCIONAL)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Ej. Compra semanal de insumos", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityInput.toDoubleOrNull()
                    if (qty != null && qty > 0.0) {
                        val username = uiState.currentUser?.username ?: "Dueño"
                        if (type == "STOCK_INICIAL") {
                            val mpMovs = uiState.movimientosMateriaPrima.filter { it.materiaPrimaId == materiaPrima.id }
                            val entries = mpMovs.filter { it.type == "ENTRADA" }.sumOf { it.quantity }
                            val exits = mpMovs.filter { it.type == "SALIDA" || it.type == "TANDA_CONSUMO" }.sumOf { it.quantity }
                            val nextStock = qty + entries - exits
                            val updatedMp = materiaPrima.copy(initialStock = qty, stock = nextStock)
                            viewModel.updateMateriaPrima(updatedMp)
                            viewModel.insertMovimientoMateriaPrima(
                                materiaPrimaId = materiaPrima.id,
                                type = "AJUSTE",
                                quantity = 0.0,
                                notes = "Modificación de Stock Inicial a $qty por Dueño",
                                responsibleUser = username
                            )
                        } else {
                            viewModel.insertMovimientoMateriaPrima(
                                materiaPrimaId = materiaPrima.id,
                                type = type,
                                quantity = qty,
                                notes = notesInput.ifBlank { "$type registrado por Dueño" },
                                responsibleUser = username
                            )
                        }
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("CONFIRMAR Y GUARDAR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    )
}

@Composable
fun RegisterMercaderiaMovDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf("SALIDA") }
    var quantityInput by remember { mutableStateOf("") }
    var quantitySoldInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val product = uiState.products.find { it.id == mercaderia.productId }
    val catalogSalePrice = product?.price ?: 0.0

    val qtySalida = quantityInput.toDoubleOrNull() ?: 0.0
    val qtySold = quantitySoldInput.toDoubleOrNull() ?: 0.0
    val calcRevenue = qtySold * catalogSalePrice
    val remainingFromSalida = (qtySalida - qtySold).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REGISTRAR MOVIMIENTO",
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Slate500,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MERCADERÍA:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                        Text(
                            text = (product?.name ?: "Producto").uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TIPO DE OPERACIÓN (*)", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ElQadreNavy)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "SALIDA" to "SALIDA (RETIRO / VENTA)",
                            "ENTRADA" to "ENTRADA (REABASTECER)"
                        ).forEach { (value, label) ->
                            val isSelected = type == value
                            Button(
                                onClick = { type = value },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) ElQadreNavy else Slate100,
                                    contentColor = if (isSelected) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }

                if (type == "SALIDA") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "DISTINCIÓN: SALIDA ≠ VENTA",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                            Text(
                                text = "• La SALIDA retira unidades del stock de almacén.\n• La CANTIDAD VENDIDA genera los ingresos reales a precio administrativo ($${"%.2f".format(catalogSalePrice)} CUP).",
                                fontSize = 12.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CANTIDAD RETIRADA DEL INVENTARIO (SALIDA) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        OutlinedTextField(
                            value = quantityInput,
                            onValueChange = { 
                                quantityInput = it
                                if (quantitySoldInput.isEmpty()) {
                                    quantitySoldInput = it
                                }
                            },
                            placeholder = { Text("Ej. 24", fontSize = 15.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CANTIDAD REALMENTE VENDIDA *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        OutlinedTextField(
                            value = quantitySoldInput,
                            onValueChange = { quantitySoldInput = it },
                            placeholder = { Text("Ej. 20", fontSize = 15.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("RESULTADO ECONÓMICO CALCULADO", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF065F46))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Precio Venta Administrativo:", fontSize = 13.sp, color = Slate600)
                                Text("$${"%.2f".format(catalogSalePrice)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ingreso Real Calculado:", fontSize = 13.sp, color = Slate600)
                                Text("$${"%.2f".format(calcRevenue)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF047857))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Restante de la Salida:", fontSize = 13.sp, color = Slate600)
                                Text("${"%.1f".format(remainingFromSalida)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("CANTIDAD DE ENTRADA (${mercaderia.unitOfMeasure}) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        OutlinedTextField(
                            value = quantityInput,
                            onValueChange = { quantityInput = it },
                            placeholder = { Text("Ej. 12", fontSize = 15.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOTAS / JUSTIFICACIÓN (OPCIONAL)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Ej. Venta del turno tarde", fontSize = 14.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            val isSalida = type == "SALIDA"
            val qty = quantityInput.toDoubleOrNull()
            val valid = qty != null && qty > 0.0
            
            Button(
                onClick = {
                    if (valid) {
                        if (isSalida) {
                            val sold = quantitySoldInput.toDoubleOrNull() ?: qty!!
                            viewModel.registrarSalidaMercaderiaConVenta(
                                mercaderiaId = mercaderia.id,
                                quantitySalida = qty!!,
                                quantitySold = sold,
                                notes = notesInput.ifBlank { "Salida registrada por Dueño" }
                            )
                        } else {
                            val username = uiState.currentUser?.username ?: "Dueño"
                            viewModel.insertMovimientoMercaderia(
                                mercaderiaId = mercaderia.id,
                                type = type,
                                quantity = qty!!,
                                responsibleAdmin = username,
                                notes = notesInput.ifBlank { "$type registrado por Dueño" }
                            )
                        }
                        onDismiss()
                    }
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("CONFIRMAR Y GUARDAR", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    )
}

@Composable
fun RegistrarProduccionYVentaTandaDialog(
    tanda: Tanda,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = remember(tanda.productId, uiState.products) {
        uiState.products.find { it.id == tanda.productId }
    }
    val catalogPrice = if (product != null && product.price > 0.0) product.price else tanda.salePrice

    val initialYield = if (tanda.actualYield > 0.0) tanda.actualYield else tanda.estimatedYield
    var actualYieldInput by remember { mutableStateOf(initialYield.toInt().toString()) }
    var quantitySoldInput by remember { mutableStateOf(tanda.quantitySold.toInt().toString()) }
    var observationInput by remember { mutableStateOf(tanda.observation) }

    val actualYieldVal = actualYieldInput.toDoubleOrNull() ?: 0.0
    val quantitySoldVal = quantitySoldInput.toDoubleOrNull() ?: 0.0
    val remainingQty = (actualYieldVal - quantitySoldVal).coerceAtLeast(0.0)
    val calcRevenue = quantitySoldVal * catalogPrice
    val calcProfit = calcRevenue - tanda.totalBatchCost

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = ElQadreNavy)
                    Text(
                        text = "TANDA #${tanda.tandaNumber} — PRODUCCIÓN Y VENTA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 15.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tanda.productName.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cantidad Planificada:", fontSize = 11.sp, color = Slate600)
                            val planQty = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                            Text("${planQty.toInt()} ${tanda.productionUnit}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costo Total Producción:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(tanda.totalBatchCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFBE123C))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Precio Venta Catálogo:", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(catalogPrice)} CUP", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CANTIDAD REALMENTE PRODUCIDA (${tanda.productionUnit}) *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    OutlinedTextField(
                        value = actualYieldInput,
                        onValueChange = { actualYieldInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CANTIDAD REALMENTE VENDIDA (${tanda.productionUnit}) *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    OutlinedTextField(
                        value = quantitySoldInput,
                        onValueChange = { quantitySoldInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("RESULTADO ECONÓMICO DE LA TANDA", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color(0xFF065F46))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cantidad Restante:", fontSize = 11.sp, color = Slate600)
                            Text("${"%.1f".format(remainingQty)} ${tanda.productionUnit}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElQadreNavy)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingresos Reales (Venta × Precio):", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(calcRevenue)} CUP", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF047857))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Resultado / Utilidad (Ingresos - Costos):", fontSize = 11.sp, color = Slate600)
                            Text("$${"%.2f".format(calcProfit)} CUP", fontWeight = FontWeight.Black, fontSize = 12.sp, color = if (calcProfit >= 0) Color(0xFF047857) else Color(0xFFBE123C))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("OBSERVACIONES / NOTAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    OutlinedTextField(
                        value = observationInput,
                        onValueChange = { observationInput = it },
                        placeholder = { Text("Ej. Ajuste de venta al cerrar turno", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            val canConfirm = actualYieldVal > 0.0 && quantitySoldVal >= 0.0
            Button(
                onClick = {
                    viewModel.actualizarProduccionRealYVentaTanda(
                        tandaId = tanda.id,
                        actualYield = actualYieldVal,
                        quantitySold = quantitySoldVal,
                        observation = observationInput
                    )
                    onDismiss()
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GUARDAR PRODUCCIÓN Y VENTA REAL", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RegistrarVentaRealMovimientoDialog(
    movimiento: MovimientoMercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val mercaderia = uiState.mercaderias.find { it.id == movimiento.mercaderiaId }
    val product = uiState.products.find { it.id == mercaderia?.productId }
    val catalogPrice = product?.price ?: movimiento.salePrice

    var quantitySoldInput by remember { mutableStateOf(movimiento.quantitySold.toInt().toString()) }
    var notesInput by remember { mutableStateOf(movimiento.notes) }

    val qtySold = quantitySoldInput.toDoubleOrNull() ?: 0.0
    val remainingFromSalida = (movimiento.quantity - qtySold).coerceAtLeast(0.0)
    val calcRevenue = qtySold * catalogPrice
    val calcCost = qtySold * (mercaderia?.acquisitionCost ?: movimiento.acquisitionCost)
    val calcResult = calcRevenue - calcCost

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EDITAR VENTA REAL DE SALIDA",
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(28.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = (product?.name ?: "Mercadería").uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cantidad Retirada (Salida):", fontSize = 13.sp, color = Slate600)
                            Text("${movimiento.quantity} ${mercaderia?.unitOfMeasure ?: ""}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Precio Venta Administrativo:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(catalogPrice)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CANTIDAD REALMENTE VENDIDA *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    OutlinedTextField(
                        value = quantitySoldInput,
                        onValueChange = { quantitySoldInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("RESULTADO ECONÓMICO", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF065F46))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Restante de la Salida:", fontSize = 13.sp, color = Slate600)
                            Text("${"%.1f".format(remainingFromSalida)} ${mercaderia?.unitOfMeasure ?: ""}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingresos Reales:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(calcRevenue)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF047857))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costo de lo Vendido:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(calcCost)} CUP", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFBE123C))
                        }
                        HorizontalDivider(color = Color(0xFFA7F3D0))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Resultado Económico:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text("$${"%.2f".format(calcResult)} CUP", fontWeight = FontWeight.Black, fontSize = 14.sp, color = if (calcResult >= 0) Color(0xFF047857) else Color(0xFFBE123C))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOTAS / JUSTIFICACIÓN", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            val valid = qtySold >= 0.0 && qtySold <= movimiento.quantity
            Button(
                onClick = {
                    viewModel.actualizarVentaRealSalidaMercaderia(
                        movimientoId = movimiento.id,
                        quantitySold = qtySold,
                        notes = notesInput
                    )
                    onDismiss()
                },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("ACTUALIZAR VENTA REAL", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    )
}

@Composable
fun RegistrarEntradaMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val currentStock = product?.stock?.toDouble() ?: viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)
    val activeJornada = uiState.activeJornada
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var quantityInput by remember { mutableStateOf("") }
    var costInput by remember { mutableStateOf(if (mercaderia.acquisitionCost > 0.0) "%.2f".format(Locale.US, mercaderia.acquisitionCost) else "") }
    var notesInput by remember { mutableStateOf("") }

    val qty = quantityInput.toDoubleOrNull()
    val unitCost = costInput.toDoubleOrNull() ?: mercaderia.acquisitionCost
    val canConfirm = qty != null && qty > 0.0

    val newStock = if (qty != null && qty > 0.0) currentStock + qty else currentStock
    val totalCost = if (qty != null && qty > 0.0) qty * unitCost else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = null, tint = Color(0xFF0F766E))
                    Text(
                        text = "REGISTRAR ENTRADA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 18.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(28.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF0FDFA),
                    border = BorderStroke(1.dp, Color(0xFF99F6E4)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = (product?.name ?: "MERCADERÍA #${mercaderia.id}").uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF134E4A)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha / Jornada:", fontSize = 13.sp, color = Color(0xFF0F766E))
                            Text(
                                text = if (activeJornada != null) "$currentDateStr (Jornada #${activeJornada.id})" else "$currentDateStr (Sin jornada)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF134E4A)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Existencia actual:", fontSize = 13.sp, color = Color(0xFF0F766E))
                            Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF134E4A))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CANTIDAD A INGRESAR (${mercaderia.unitOfMeasure}) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        placeholder = { Text("Ej. 10", fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("entrada_mercaderia_qty_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("COSTO UNITARIO CORRESPONDIENTE (CUP) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    OutlinedTextField(
                        value = costInput,
                        onValueChange = { costInput = it },
                        placeholder = { Text("Ej. 150.00", fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("entrada_mercaderia_cost_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Cálculo en tiempo real: EXISTENCIA = EXISTENCIA ANTERIOR + ENTRADA
                if (canConfirm && qty != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Existencia Anterior:", fontSize = 13.sp, color = Slate600)
                                Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Entrada:", fontSize = 13.sp, color = Color(0xFF0F766E))
                                Text("+${"%.1f".format(qty)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            }
                            HorizontalDivider(color = Slate200)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NUEVA EXISTENCIA:", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text("${"%.1f".format(newStock)} ${mercaderia.unitOfMeasure}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                            }
                            HorizontalDivider(color = Slate200)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Total Entrada:", fontSize = 13.sp, color = Slate600)
                                Text("$${"%.2f".format(totalCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Slate800)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("NOTAS / JUSTIFICACIÓN (OPCIONAL)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Ej. Compra proveedor habitual", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("entrada_mercaderia_notes_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm && qty != null) {
                        viewModel.registrarEntradaMercaderia(
                            mercaderiaId = mercaderia.id,
                            quantity = qty,
                            costoUnitario = unitCost,
                            notes = notesInput
                        )
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_entrada_mercaderia_btn")
            ) {
                Text("REGISTRAR ENTRADA (+ EXISTENCIA)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    )
}

@Composable
fun RegistrarMermaMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val currentStock = viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)
    val activeJornada = uiState.activeJornada
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var quantityInput by remember { mutableStateOf("") }
    var conceptoSeleccionado by remember { mutableStateOf("Defectuoso") }
    var notasDetalle by remember { mutableStateOf("") }

    val qty = quantityInput.toDoubleOrNull()
    val canConfirm = qty != null && qty > 0.0
    val newStock = if (qty != null && qty > 0.0) maxOf(0.0, currentStock - qty) else currentStock

    val conceptos = listOf("Defectuoso", "Consumo", "Regalía")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = Color(0xFFD97706))
                    Text(
                        text = "REGISTRAR MERMA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 18.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(28.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MERMA DE ALMACÉN",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "La merma se resta exclusivamente de la existencia del ALMACÉN por defecto, consumo interno o regalía.",
                            fontSize = 12.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = (product?.name ?: "MERCADERÍA #${mercaderia.id}").uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha / Jornada:", fontSize = 13.sp, color = Slate600)
                            Text(
                                text = if (activeJornada != null) "$currentDateStr (Jornada #${activeJornada.id})" else "$currentDateStr (Sin jornada)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Existencia actual en Almacén:", fontSize = 13.sp, color = Slate600)
                            Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CONCEPTO DE MERMA *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        conceptos.forEach { c ->
                            FilterChip(
                                selected = conceptoSeleccionado == c,
                                onClick = { conceptoSeleccionado = c },
                                label = { Text(c, fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CANTIDAD DE MERMA (${mercaderia.unitOfMeasure}) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        placeholder = { Text("Ej. 1", fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("merma_mercaderia_qty_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DETALLE / OBSERVACIONES", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notasDetalle,
                        onValueChange = { notasDetalle = it },
                        placeholder = { Text("Observación opcional...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("merma_mercaderia_notes_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Cálculo en tiempo real
                if (canConfirm && qty != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Existencia Almacén Anterior:", fontSize = 13.sp, color = Slate600)
                                Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Merma ($conceptoSeleccionado):", fontSize = 13.sp, color = Color(0xFFD97706))
                                Text("-${"%.1f".format(qty)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                            }
                            HorizontalDivider(color = Color(0xFFFDE68A))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NUEVA EXISTENCIA ALMACÉN:", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text("${"%.1f".format(newStock)} ${mercaderia.unitOfMeasure}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm && qty != null) {
                        viewModel.registrarMermaMercaderia(
                            mercaderiaId = mercaderia.id,
                            quantity = qty,
                            motivo = conceptoSeleccionado,
                            notes = notasDetalle
                        )
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_merma_mercaderia_btn")
            ) {
                Text("REGISTRAR MERMA (− ALMACÉN)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    )
}

@Composable
fun RegistrarParaVentaMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val currentStock = viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)
    val activeJornada = uiState.activeJornada
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var quantityInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val qty = quantityInput.toDoubleOrNull()
    val canConfirm = qty != null && qty > 0.0
    val newStock = if (qty != null && qty > 0.0) maxOf(0.0, currentStock - qty) else currentStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = ElQadreNavy)
                    Text(
                        text = "PARA VENTA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 18.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(28.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ALMACÉN → LOCAL DE VENTAS",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            text = "Registra la salida desde el ALMACÉN hacia el LOCAL DE VENTAS. Esta acción descuenta exclusivamente del inventario del Almacén. NO representa una venta ni modifica automáticamente las ventas del Cuadre de Caja.",
                            fontSize = 12.sp,
                            color = Color(0xFF1E3A8A)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = (product?.name ?: "MERCADERÍA #${mercaderia.id}").uppercase(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha / Jornada:", fontSize = 13.sp, color = Slate600)
                            Text(
                                text = if (activeJornada != null) "$currentDateStr (Jornada #${activeJornada.id})" else "$currentDateStr (Sin jornada)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Existencia actual en Almacén:", fontSize = 13.sp, color = Slate600)
                            Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Black, fontSize = 14.sp, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("CANTIDAD A TRASLADAR (${mercaderia.unitOfMeasure}) *", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        placeholder = { Text("Ej. 10", fontSize = 15.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("paraventa_mercaderia_qty_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("DETALLE / DESTINO", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Ej. Entrega para la barra / turno", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("paraventa_mercaderia_notes_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Cálculo en tiempo real
                if (canConfirm && qty != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Existencia Almacén Anterior:", fontSize = 13.sp, color = Slate600)
                                Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Traslado Para Venta:", fontSize = 13.sp, color = Color(0xFF1D4ED8))
                                Text("-${"%.1f".format(qty)} ${mercaderia.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                            }
                            HorizontalDivider(color = Color(0xFFBFDBFE))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NUEVA EXISTENCIA ALMACÉN:", fontSize = 14.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text("${"%.1f".format(newStock)} ${mercaderia.unitOfMeasure}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm && qty != null) {
                        viewModel.registrarParaVentaMercaderia(
                            mercaderiaId = mercaderia.id,
                            quantity = qty,
                            notes = notesInput
                        )
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("confirm_paraventa_mercaderia_btn")
            ) {
                Text("REGISTRAR PARA VENTA (− ALMACÉN)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        }
    )
}

@Composable
fun RegistrarSalidaFisicaMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val currentStock = product?.stock?.toDouble() ?: viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)
    val activeJornada = uiState.activeJornada
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var quantityInput by remember { mutableStateOf("") }
    var motivoSeleccionado by remember { mutableStateOf("Deterioro") }
    var motivoDetalle by remember { mutableStateOf("") }

    val qty = quantityInput.toDoubleOrNull()
    val canConfirm = qty != null && qty > 0.0
    val newStock = if (qty != null && qty > 0.0) maxOf(0.0, currentStock - qty) else currentStock

    val motivosPredefinidos = listOf("Deterioro", "Vencimiento", "Consumo Interno", "Rotura", "Pérdida", "Ajuste")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = Color(0xFFB45309))
                    Text(
                        text = "REGISTRAR SALIDA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 16.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(24.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "SALIDA FÍSICA (NO ES VENTA)",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "Registra una disminución física del inventario por deterioro, rotura, vencimiento o consumo interno. Una salida NO genera ventas ni ingresos.",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = (product?.name ?: "MERCADERÍA #${mercaderia.id}").uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha / Jornada:", fontSize = 11.sp, color = Slate600)
                            Text(
                                text = if (activeJornada != null) "$currentDateStr (Jornada #${activeJornada.id})" else "$currentDateStr (Sin jornada)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Existencia actual:", fontSize = 11.sp, color = Slate600)
                            Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ElQadreNavy)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CANTIDAD A RETIRAR (${mercaderia.unitOfMeasure}) *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it },
                        placeholder = { Text("Ej. 2", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("salida_mercaderia_qty_input"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MOTIVO DE SALIDA *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        motivosPredefinidos.take(3).forEach { m ->
                            FilterChip(
                                selected = motivoSeleccionado == m,
                                onClick = { motivoSeleccionado = m },
                                label = { Text(m, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        motivosPredefinidos.drop(3).forEach { m ->
                            FilterChip(
                                selected = motivoSeleccionado == m,
                                onClick = { motivoSeleccionado = m },
                                label = { Text(m, fontSize = 10.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = motivoDetalle,
                        onValueChange = { motivoDetalle = it },
                        placeholder = { Text("Detalles del motivo (opcional)...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("salida_mercaderia_notes_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Cálculo en tiempo real: EXISTENCIA = EXISTENCIA ANTERIOR − SALIDA
                if (canConfirm && qty != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Existencia Anterior:", fontSize = 11.sp, color = Slate600)
                                Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Salida Física:", fontSize = 11.sp, color = Color(0xFFB45309))
                                Text("-${"%.1f".format(qty)} ${mercaderia.unitOfMeasure}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NUEVA EXISTENCIA:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text("${"%.1f".format(newStock)} ${mercaderia.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309))
                            }
                            HorizontalDivider(color = Color(0xFFFDE68A), modifier = Modifier.padding(vertical = 2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ingreso Económico Generado:", fontSize = 11.sp, color = Slate600)
                                Text("$0.00 CUP (Sin Ingreso)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm && qty != null) {
                        val motivoFinal = if (motivoDetalle.isNotBlank()) "$motivoSeleccionado: $motivoDetalle" else motivoSeleccionado
                        viewModel.registrarSalidaMercaderiaFisica(
                            mercaderiaId = mercaderia.id,
                            quantity = qty,
                            notes = motivoFinal
                        )
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_salida_mercaderia_btn")
            ) {
                Text("REGISTRAR SALIDA (− EXISTENCIA, SIN INGRESO)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            }
        }
    )
}

@Composable
fun RegistrarVentaDirectaMercaderiaDialog(
    mercaderia: Mercaderia,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val product = uiState.products.find { it.id == mercaderia.productId }
    val defaultSalePrice = product?.price ?: 0.0
    val currentStock = product?.stock?.toDouble() ?: viewModel.getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock)
    val activeJornada = uiState.activeJornada
    val currentDateStr = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()) }

    var quantityInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf(if (defaultSalePrice > 0.0) "%.2f".format(Locale.US, defaultSalePrice) else "") }
    var notesInput by remember { mutableStateOf("") }

    val qty = quantityInput.toDoubleOrNull()
    val salePrice = priceInput.toDoubleOrNull() ?: defaultSalePrice
    val canConfirm = qty != null && qty > 0.0 && salePrice >= 0.0

    // VENTA = CANTIDAD VENDIDA × PRECIO DE VENTA
    val totalRevenue = if (canConfirm && qty != null) qty * salePrice else 0.0
    val totalCost = if (canConfirm && qty != null) qty * mercaderia.acquisitionCost else 0.0
    val profit = totalRevenue - totalCost
    val newStock = if (qty != null && qty > 0.0) maxOf(0.0, currentStock - qty) else currentStock

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PointOfSale, contentDescription = null, tint = Color(0xFF047857))
                    Text(
                        text = "REGISTRAR VENTA",
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy,
                        fontSize = 16.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500, modifier = Modifier.size(24.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFECFDF5),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "VENTA DE MERCANCÍA AL CLIENTE",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = Color(0xFF065F46)
                        )
                        Text(
                            text = "Registra exclusivamente la mercancía realmente vendida. Genera ingreso económico y descuenta las unidades de la existencia.",
                            fontSize = 11.sp,
                            color = Color(0xFF047857)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = (product?.name ?: "MERCADERÍA #${mercaderia.id}").uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha / Jornada:", fontSize = 11.sp, color = Slate600)
                            Text(
                                text = if (activeJornada != null) "$currentDateStr (Jornada #${activeJornada.id})" else "$currentDateStr (Sin jornada)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Existencia actual:", fontSize = 11.sp, color = Slate600)
                            Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ElQadreNavy)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CANTIDAD VENDIDA *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        OutlinedTextField(
                            value = quantityInput,
                            onValueChange = { quantityInput = it },
                            placeholder = { Text("Ej. 8", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("venta_mercaderia_qty_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PRECIO DE VENTA (CUP) *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { priceInput = it },
                            placeholder = { Text("Ej. 200.00", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("venta_mercaderia_price_input"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                // Cálculo en tiempo real: VENTA = CANTIDAD VENDIDA × PRECIO DE VENTA
                if (canConfirm && qty != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFECFDF5),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Existencia Anterior:", fontSize = 11.sp, color = Color(0xFF065F46))
                                Text("${"%.1f".format(currentStock)} ${mercaderia.unitOfMeasure}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Unidades Vendidas:", fontSize = 11.sp, color = Color(0xFF065F46))
                                Text("-${"%.1f".format(qty)} ${mercaderia.unitOfMeasure}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("NUEVA EXISTENCIA:", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text("${"%.1f".format(newStock)} ${mercaderia.unitOfMeasure}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            }
                            HorizontalDivider(color = Color(0xFFA7F3D0), modifier = Modifier.padding(vertical = 2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL VENTA (INGRESO):", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF065F46))
                                Text("$${"%.2f".format(totalRevenue)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo de Adquisición:", fontSize = 11.sp, color = Slate600)
                                Text("$${"%.2f".format(totalCost)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Utilidad en Venta:", fontSize = 11.sp, color = Color(0xFF065F46))
                                Text("$${"%.2f".format(profit)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (profit >= 0) Color(0xFF047857) else Color(0xFFDC2626))
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("NOTAS / OBSERVACIONES (OPCIONAL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("Ej. Venta al mostrador", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("venta_mercaderia_notes_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (canConfirm && qty != null) {
                        viewModel.registrarVentaMercaderia(
                            mercaderiaId = mercaderia.id,
                            quantitySold = qty,
                            salePrice = salePrice,
                            notes = notesInput.ifBlank { "Venta registrada por Dueño" }
                        )
                        onDismiss()
                    }
                },
                enabled = canConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_venta_mercaderia_btn")
            ) {
                Text("REGISTRAR VENTA (INGRESO)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            }
        }
    )
}

data class InsufficientIngredientDetail(
    val name: String,
    val required: Double,
    val available: Double,
    val unit: String
)

@Composable
fun RegisterTandaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val isJornadaOpen = uiState.activeJornada != null && uiState.activeJornada.isOpen

    val elaboratedProducts = remember(uiState.productosElaborados, uiState.products) {
        uiState.productosElaborados.mapNotNull { pe ->
            uiState.products.find { it.id == pe.productId }?.let { p -> Pair(pe, p) }
        }
    }

    // Cálculo automático del número de tanda (secuencial de hoy / jornada)
    val autoTandaNumberStr = remember(uiState.tandas) {
        val maxNum = uiState.tandas.mapNotNull { it.tandaNumber.toIntOrNull() }.maxOrNull() ?: 0
        "%02d".format(maxNum + 1)
    }

    var selectedProductPair by remember { mutableStateOf<Pair<ProductoElaborado, Product>?>(null) }
    var baseQuantityInput by remember { mutableStateOf("") }
    var productDropdownExpanded by remember { mutableStateOf(false) }
    var missingIngredientsAlert by remember { mutableStateOf<List<InsufficientIngredientDetail>?>(null) }

    LaunchedEffect(selectedProductPair) {
        selectedProductPair?.let { (pe, _) ->
            baseQuantityInput = if (pe.baseQuantity > 0.0) "%.1f".format(pe.baseQuantity) else "1.0"
        }
    }

    val selectedPe = selectedProductPair?.first
    val selectedProd = selectedProductPair?.second
    val baseMp = remember(selectedPe, uiState.materiasPrimas) {
        selectedPe?.let { pe -> uiState.materiasPrimas.find { it.id == pe.baseMateriaPrimaId } }
    }

    var selectedBaseUnit by remember(selectedProductPair) {
        mutableStateOf(baseMp?.unit ?: "g")
    }

    val baseUnitCategory = remember(baseMp) {
        getBaseUnit(baseMp?.unit ?: "g")
    }
    val compatibleUnits = remember(baseUnitCategory) {
        getCompatibleUnits(baseUnitCategory)
    }

    val baseQtyEntered = baseQuantityInput.toDoubleOrNull() ?: 0.0
    val baseQtyInRecipeUnit = remember(baseQtyEntered, selectedBaseUnit, baseMp) {
        val recipeUnit = baseMp?.unit ?: selectedBaseUnit
        if (baseQtyEntered > 0.0) {
            com.example.ui.viewmodel.UnitConverter.convert(baseQtyEntered, selectedBaseUnit, recipeUnit) ?: baseQtyEntered
        } else 0.0
    }

    val productionFactor = remember(selectedPe, baseQtyInRecipeUnit) {
        if (selectedPe != null && selectedPe.baseQuantity > 0.0) {
            baseQtyInRecipeUnit / selectedPe.baseQuantity
        } else if (baseQtyInRecipeUnit > 0.0) {
            baseQtyInRecipeUnit
        } else {
            0.0
        }
    }

    val activeRecipeIngredients = remember(selectedProductPair, uiState.recetaIngredientes) {
        selectedProductPair?.let { (pe, _) ->
            uiState.recetaIngredientes.filter { it.productoElaboradoId == pe.productId || it.productoElaboradoId == pe.id }
        } ?: emptyList()
    }

    // Modal de Alerta: INVENTARIO INSUFICIENTE
    if (missingIngredientsAlert != null) {
        AlertDialog(
            onDismissRequest = { missingIngredientsAlert = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "NO HAY INVENTARIO SUFICIENTE",
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = Color(0xFF991B1B),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "No se puede activar la tanda ni realizar descuentos parciales. Faltan los siguientes insumos en el inventario:",
                        fontSize = 15.sp,
                        color = Slate700,
                        fontWeight = FontWeight.Medium
                    )

                    missingIngredientsAlert?.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.5.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.name.uppercase(),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color(0xFF991B1B)
                                )
                                Text(
                                    text = "• Necesita: ${"%.2f".format(item.required)} ${item.unit} / Disponible: ${"%.2f".format(item.available)} ${item.unit}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { missingIngredientsAlert = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Text(
                        text = "ENTENDIDO / CORREGIR INVENTARIO",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR PANTALLA COMPLETA
                Surface(
                    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "ACTIVAR NUEVA TANDA",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }

                // VALIDACIÓN DE JORNADA
                if (!isJornadaOpen) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(2.dp, Color(0xFFFECACA)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFBE123C), modifier = Modifier.size(48.dp))
                            Text(
                                text = "SE REQUIERE UNA JORNADA ABIERTA",
                                color = Color(0xFFBE123C),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Para activar una tanda de producción y descontar inventario, primero debe iniciar una jornada de trabajo.",
                                color = Slate700,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBE123C)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("ENTENDIDO / REGRESAR", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            }
                        }
                    }
                } else {
                    // CUERPO PRINCIPAL DEL FORMULARIO ACCESIBLE
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 1. NÚMERO DE TANDA (AUTOMÁTICO / NO EDITABLE)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(2.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "NÚMERO DE TANDA",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                    Text(
                                        text = "Generado automáticamente",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ElQadreNavy
                                ) {
                                    Text(
                                        text = "Tanda #$autoTandaNumberStr",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // 2. PRODUCTO (SELECTOR DE PRODUCTOS REALES)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "PRODUCTO (*)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { productDropdownExpanded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .testTag("select_producto_elaborado_btn"),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(2.dp, if (selectedProductPair != null) ElQadreNavy else Slate400),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White,
                                        contentColor = ElQadreNavy
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = selectedProductPair?.second?.name?.uppercase() ?: "SELECCIONAR PRODUCTO...",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (selectedProductPair != null) ElQadreNavy else Slate500
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp),
                                            tint = ElQadreNavy
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = productDropdownExpanded,
                                    onDismissRequest = { productDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    elaboratedProducts.forEach { pair ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    pair.second.name.uppercase(),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ElQadreNavy,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            },
                                            onClick = {
                                                selectedProductPair = pair
                                                productDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 3. CANTIDAD Y UNIDAD DE MEDIDA
                        if (selectedProductPair != null) {
                            val pe = selectedProductPair!!.first
                            val p = selectedProductPair!!.second

                            if (activeRecipeIngredients.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = BorderStroke(1.5.dp, Color(0xFFFECACA)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFBE123C), modifier = Modifier.size(28.dp))
                                        Text(
                                            text = "Este producto no tiene una receta con insumos configurada.",
                                            color = Color(0xFFBE123C),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "CANTIDAD (*)",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "Insumo base: ${baseMp?.name?.uppercase() ?: "Base"} • Receta estándar: ${pe.baseYield} ${pe.productionUnit} con ${pe.baseQuantity} ${baseMp?.unit ?: ""}",
                                        fontSize = 14.sp,
                                        color = Slate600
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = baseQuantityInput,
                                            onValueChange = { baseQuantityInput = it },
                                            label = { Text("Cantidad (*)", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                            placeholder = { Text("Ej. ${pe.baseQuantity}", fontSize = 18.sp) },
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(68.dp)
                                                .testTag("input_base_quantity"),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(16.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = ElQadreNavy),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = ElQadreNavy,
                                                focusedLabelColor = ElQadreNavy
                                            )
                                        )

                                        // Selector / Visualizador de Unidad de Medida
                                        var expandedBaseUnit by remember { mutableStateOf(false) }
                                        Box(modifier = Modifier.weight(1f)) {
                                            OutlinedButton(
                                                onClick = { if (compatibleUnits.size > 1) expandedBaseUnit = true },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(68.dp)
                                                    .testTag("select_base_unit_btn"),
                                                shape = RoundedCornerShape(16.dp),
                                                border = BorderStroke(2.dp, ElQadreNavy),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = Color.White,
                                                    contentColor = ElQadreNavy
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text("Unidad", fontSize = 11.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                                        Text(selectedBaseUnit, color = ElQadreNavy, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                                    }
                                                    if (compatibleUnits.size > 1) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(26.dp))
                                                    }
                                                }
                                            }

                                            if (compatibleUnits.size > 1) {
                                                DropdownMenu(
                                                    expanded = expandedBaseUnit,
                                                    onDismissRequest = { expandedBaseUnit = false }
                                                ) {
                                                    compatibleUnits.forEach { unitItem ->
                                                        DropdownMenuItem(
                                                            text = { Text(unitItem, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
                                                            onClick = {
                                                                selectedBaseUnit = unitItem
                                                                expandedBaseUnit = false
                                                            }
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

                    // BOTÓN PRINCIPAL ELEVADO Y SEGURO SOBRE LA NAVEGACIÓN ANDROID
                    val hasRecipe = selectedProductPair != null && activeRecipeIngredients.isNotEmpty()
                    val canActivate = selectedProductPair != null && hasRecipe && baseQtyEntered > 0.0 && productionFactor > 0.0

                    Surface(
                        color = Color.White,
                        shadowElevation = 12.dp,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val pair = selectedProductPair ?: return@Button
                                    val (pe, p) = pair
                                    val mp = baseMp ?: return@Button
                                    if (baseQtyEntered <= 0.0 || productionFactor <= 0.0) return@Button

                                    // 1. VALIDACIÓN EXHAUSTIVA DE INVENTARIO ANTES DE ACTIVAR
                                    val missingList = mutableListOf<InsufficientIngredientDetail>()
                                    val consumosList = mutableListOf<Triple<Long, Double, String>>()
                                    val summaryIngredients = mutableListOf<String>()
                                    var totalDirectCost = 0.0

                                    activeRecipeIngredients.forEach { ing ->
                                        val raw = uiState.materiasPrimas.find { it.id == ing.materiaPrimaId }
                                        val rawName = raw?.name ?: "Insumo desconocido"
                                        val requiredQty = ing.quantity * productionFactor
                                        val convertedQty = if (raw != null) {
                                            com.example.ui.viewmodel.UnitConverter.convert(requiredQty, ing.unit, raw.unit) ?: requiredQty
                                        } else requiredQty

                                        val availableStock = raw?.stock ?: 0.0
                                        val invUnit = raw?.unit ?: ing.unit

                                        if (availableStock < convertedQty) {
                                            missingList.add(
                                                InsufficientIngredientDetail(
                                                    name = rawName,
                                                    required = convertedQty,
                                                    available = availableStock,
                                                    unit = invUnit
                                                )
                                            )
                                        } else {
                                            val cost = convertedQty * (raw?.unitCost ?: 0.0)
                                            totalDirectCost += cost
                                            consumosList.add(Triple(ing.materiaPrimaId, convertedQty, "${"%.1f".format(requiredQty)} ${ing.unit}"))
                                            summaryIngredients.add("$rawName: ${"%.1f".format(requiredQty)} ${ing.unit}")
                                        }
                                    }

                                    // Si uno o varios insumos no alcanzan: NO activar y NO descontar nada
                                    if (missingList.isNotEmpty()) {
                                        missingIngredientsAlert = missingList
                                        return@Button
                                    }

                                    // Si todos alcanzan: Activar tanda y descontar inventario normalmente
                                    val now = System.currentTimeMillis()
                                    val batchUuid = "TANDA-$autoTandaNumberStr-$now"
                                    val expectedYield = (if (pe.baseYield > 0.0) pe.baseYield else 1.0) * productionFactor

                                    val costSheet = com.example.util.CostCalculationHelper.calculateCostSheet(
                                        product = p,
                                        uiState = uiState
                                    )
                                    val indirectCost = expectedYield * costSheet.gastoIndirectoUnitario
                                    val totalBatchCost = totalDirectCost + indirectCost
                                    val realUnitCost = if (expectedYield > 0.0) totalBatchCost / expectedYield else 0.0
                                    val expectedRevenue = expectedYield * p.price
                                    val estimatedProfit = expectedRevenue - totalBatchCost

                                    val tanda = Tanda(
                                        uuid = batchUuid,
                                        tandaNumber = autoTandaNumberStr,
                                        productId = p.id,
                                        productName = p.name,
                                        date = now,
                                        responsibleUser = uiState.currentUser?.username ?: "Dueño",
                                        baseMateriaPrimaId = pe.baseMateriaPrimaId,
                                        baseMateriaPrimaName = mp.name,
                                        baseQuantityUsed = baseQtyEntered,
                                        baseQuantityUnit = selectedBaseUnit,
                                        productionFactor = productionFactor,
                                        estimatedYield = expectedYield,
                                        expectedYield = expectedYield,
                                        actualYield = 0.0,
                                        yieldPercentage = 100.0,
                                        productionUnit = pe.productionUnit,
                                        ingredientsConsumedText = summaryIngredients.joinToString(", "),
                                        status = "ACTIVA",
                                        jornada = uiState.activeJornada?.let { "Jornada #${it.id}" } ?: "Jornada Abierta",
                                        jornadaId = uiState.activeJornada?.id ?: 0L,
                                        observation = "",
                                        laborCostType = "NINGUNO",
                                        laborCostValue = 0.0,
                                        totalLaborCost = 0.0,
                                        ownerPayType = "NINGUNO",
                                        ownerPayValue = 0.0,
                                        totalOwnerPay = 0.0,
                                        totalDirectIngredientsCost = totalDirectCost,
                                        totalIndirectCostAllocated = indirectCost,
                                        totalBatchCost = totalBatchCost,
                                        realUnitCost = realUnitCost,
                                        expectedRevenue = expectedRevenue,
                                        estimatedProfit = estimatedProfit,
                                        profitMargin = if (expectedRevenue > 0.0) (estimatedProfit / expectedRevenue) * 100.0 else 0.0,
                                        inventoryDeducted = true,
                                        quantitySold = 0.0,
                                        salePrice = p.price,
                                        realRevenue = 0.0,
                                        deviceId = "DISPOSITIVO-LOCAL"
                                    )

                                    viewModel.registrarTanda(tanda, consumosList)
                                    onDismiss()
                                },
                                enabled = canActivate,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0F766E),
                                    disabledContainerColor = Slate300,
                                    disabledContentColor = Slate500
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .testTag("submit_tanda_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                    Text(
                                        text = "ACTIVAR TANDA Y DESCONTAR INVENTARIO",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, Slate300),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text("CANCELAR", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate600)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AjustarTandaDialog(
    tanda: Tanda,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentYield = if (tanda.actualYield > 0.0) tanda.actualYield else tanda.expectedYield
    var actualYieldText by remember { mutableStateOf(if (currentYield > 0.0) "%.1f".format(currentYield) else "") }
    var observationText by remember { mutableStateOf(tanda.observation) }

    val actualYieldVal = actualYieldText.toDoubleOrNull() ?: 0.0
    val canConfirm = actualYieldVal > 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO SUPERIOR
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "AJUSTAR TANDA #${tanda.tandaNumber}",
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy,
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CUERPO
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tanda.productName.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 17.sp,
                                color = ElQadreNavy
                            )
                            Text("Insumo base: ${"%.1f".format(tanda.baseQuantityUsed)} ${tanda.baseQuantityUnit} (${tanda.baseMateriaPrimaName})", fontSize = 14.sp, color = Slate700)
                            Text("Producción esperada: ${"%.1f".format(tanda.expectedYield)} ${tanda.productionUnit}", fontSize = 14.sp, color = Slate700)
                            Text("Costo total insumos: $${"%.2f".format(tanda.totalBatchCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CANTIDAD OBTENIDA / AJUSTADA (${tanda.productionUnit}) (*)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = actualYieldText,
                            onValueChange = { actualYieldText = it },
                            placeholder = { Text("Ej. ${tanda.expectedYield.toInt()}", fontSize = 16.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .testTag("input_ajustar_yield"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OBSERVACIONES / MOTIVO DEL AJUSTE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = observationText,
                            onValueChange = { observationText = it },
                            placeholder = { Text("Ej. Se corrigió merma en el proceso...", fontSize = 15.sp, color = Slate400) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy, focusedLabelColor = ElQadreNavy)
                        )
                    }
                }

                // BOTONES INFERIORES
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("CANCELAR", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                        }

                        Button(
                            onClick = {
                                if (canConfirm) {
                                    val expectedVal = if (tanda.expectedYield > 0.0) tanda.expectedYield else tanda.estimatedYield
                                    val yieldPct = if (expectedVal > 0.0) (actualYieldVal / expectedVal) * 100.0 else 100.0
                                    val realUnitCost = if (actualYieldVal > 0.0) tanda.totalBatchCost / actualYieldVal else tanda.realUnitCost
                                    val updatedTanda = tanda.copy(
                                        actualYield = actualYieldVal,
                                        yieldPercentage = yieldPct,
                                        realUnitCost = realUnitCost,
                                        observation = observationText.trim()
                                    )
                                    viewModel.ajustarTanda(updatedTanda)
                                    onDismiss()
                                }
                            },
                            enabled = canConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.4f)
                                .height(58.dp)
                                .testTag("btn_save_ajustar_tanda")
                        ) {
                            Text("GUARDAR AJUSTE", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TandaDetailDialog(
    tanda: Tanda,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onAjustarTanda: (Tanda) -> Unit,
    onCerrarTanda: (Tanda) -> Unit
) {
    val prod = uiState.products.find { it.id == tanda.productId }
    val catPrice = if (prod != null && prod.price > 0.0) prod.price else tanda.salePrice
    val actualQty = if (tanda.actualYield > 0.0) tanda.actualYield else tanda.estimatedYield
    val restQty = (actualQty - tanda.quantitySold).coerceAtLeast(0.0)
    val rev = if (tanda.realRevenue > 0.0) tanda.realRevenue else (tanda.quantitySold * catPrice)
    val profit = rev - tanda.totalBatchCost

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ENCABEZADO
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Slate200),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            colors = IconButtonDefaults.iconButtonColors(containerColor = Slate100),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Regresar",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "TANDA #${tanda.tandaNumber}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = ElQadreNavy
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (tanda.status == "CERRADA") Color(0xFFECFDF5) else Color(0xFFFEF3C7)
                                ) {
                                    Text(
                                        text = if (tanda.status == "CERRADA") "CERRADA" else "ABIERTA",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tanda.status == "CERRADA") Color(0xFF047857) else Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(tanda.date)),
                                fontSize = 13.sp,
                                color = Slate500
                            )
                        }

                        Spacer(modifier = Modifier.width(52.dp))
                    }
                }

                // CONTENIDO
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // IDENTIFICACIÓN
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tanda.productName.uppercase(),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = ElQadreNavy
                            )
                            Text("Responsable: ${tanda.responsibleUser} • ${tanda.jornada}", fontSize = 13.sp, color = Slate600)
                            Text("Insumo base: ${"%.1f".format(tanda.baseQuantityUsed)} ${tanda.baseQuantityUnit} (${tanda.baseMateriaPrimaName})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                    }

                    // CANTIDADES
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("BALANCE DE PRODUCCIÓN Y VENTA", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Producido:", fontSize = 14.sp, color = Slate700)
                                Text("${"%.1f".format(actualQty)} ${tanda.productionUnit}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vendido:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                Text("${"%.1f".format(tanda.quantitySold)} ${tanda.productionUnit}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF047857))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Restante:", fontSize = 14.sp, color = Slate700)
                                Text("${"%.1f".format(restQty)} ${tanda.productionUnit}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                        }
                    }

                    // FINANZAS
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.5.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("DATOS ECONÓMICOS", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Insumos:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(tanda.totalBatchCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Costo Unitario Real:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(tanda.realUnitCost)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Precio Venta Unitario:", fontSize = 14.sp, color = Slate700)
                                Text("$${"%.2f".format(catPrice)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ingreso Venta Real:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                                Text("$${"%.2f".format(rev)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F766E))
                            }
                            HorizontalDivider(color = Slate200)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ganancia Estimada:", fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (profit >= 0) Color(0xFF047857) else Color(0xFFDC2626))
                                Text("$${"%.2f".format(profit)} CUP", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (profit >= 0) Color(0xFF047857) else Color(0xFFDC2626))
                            }
                        }
                    }

                    // INSUMOS CONSUMIDOS
                    if (tanda.ingredientsConsumedText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("INSUMOS CONSUMIDOS", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(tanda.ingredientsConsumedText, fontSize = 14.sp, color = Slate700)
                            }
                        }
                    }

                    // OBSERVACIONES
                    if (tanda.observation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("OBSERVACIONES", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(tanda.observation, fontSize = 14.sp, color = Slate700)
                            }
                        }
                    }
                }

                // ACCIONES INFERIORES
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("VOLVER", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate700)
                        }

                        if (tanda.status != "CERRADA") {
                            OutlinedButton(
                                onClick = { onAjustarTanda(tanda) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(58.dp)
                            ) {
                                Text("AJUSTAR TANDA", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = ElQadreNavy)
                            }

                            Button(
                                onClick = { onCerrarTanda(tanda) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(58.dp)
                            ) {
                                Text("CERRAR TANDA", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Support Entities and Helper Methods
data class UnifiedHistoryItem(
    val id: String,
    val type: String, // "PRODUCCION", "MERCADERIA", "TANDA"
    val productName: String,
    val movementType: String, // "ENTRADA", "SALIDA", "TANDA_CONSUMO", "INVENTARIO_INICIAL", etc.
    val quantity: Double,
    val unit: String,
    val date: Long,
    val responsibleUser: String,
    val notes: String
)

data class IngredientBatchResult(
    val materiaPrimaId: Long,
    val name: String,
    val recipeUnit: String,
    val inventoryUnit: String,
    val recalculatedRecipeQty: Double,
    val convertedInventoryQty: Double,
    val unitCost: Double,
    val totalCost: Double,
    val stockAvailable: Double,
    val isSufficient: Boolean,
    val isCompatible: Boolean
)

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun DuenoInversionesView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var filterType by remember { mutableStateOf("TODAS") } // "TODAS", "MES", "ANIO"
    
    val now = remember { Calendar.getInstance() }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }

    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    // Launcher for Q_inversiones.json
    val qInversionesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val jsonContent = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!jsonContent.isNullOrBlank()) {
                    viewModel.importQInversionesJsonContent(jsonContent)
                } else {
                    Toast.makeText(context, "El archivo seleccionado está vacío.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedInversionForDetail by remember { mutableStateOf<Inversion?>(null) }
    var selectedInversionForEdit by remember { mutableStateOf<Inversion?>(null) }
    var inversionToDelete by remember { mutableStateOf<Inversion?>(null) }

    val allInversiones = uiState.inversiones
    val filteredInversiones = remember(allInversiones, filterType, selectedMonth, selectedYear) {
        allInversiones.filter { inv ->
            val invCal = Calendar.getInstance().apply { timeInMillis = inv.date }
            when (filterType) {
                "TODAS" -> true
                "MES" -> {
                    invCal.get(Calendar.YEAR) == selectedYear &&
                    invCal.get(Calendar.MONTH) == selectedMonth
                }
                "ANIO" -> {
                    invCal.get(Calendar.YEAR) == selectedYear
                }
                else -> true
            }
        }.sortedByDescending { it.date }
    }

    val totalInvertido = remember(filteredInversiones) {
        filteredInversiones.sumOf { it.amount }
    }
    val cantidadInversiones = filteredInversiones.size
    val ultimaInversion = remember(allInversiones) {
        allInversiones.maxByOrNull { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Inversiones",
            subtitle = "Control de inversiones y activos fijos",
            icon = Icons.Outlined.AttachMoney,
            onBack = onBack
        )

        // Summary Cards Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL INVERTIDO",
                            color = ElQadreGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "$${"%,.2f".format(totalInvertido)} CUP",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TrendingUp,
                            contentDescription = null,
                            tint = ElQadreGold,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 1.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CANTIDAD",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$cantidadInversiones inversiones",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "ÚLTIMA REGISTRADA",
                            color = Slate300,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (ultimaInversion != null) {
                                "${ultimaInversion.name} ($${"%,.2f".format(ultimaInversion.originalAmount)} ${ultimaInversion.currency})"
                            } else {
                                "Ninguna aún"
                            },
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Action Buttons & Filters Area (TODAS / MES / AÑO)
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("add_inversion_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "NUEVA INVERSIÓN",
                            color = ElQadreNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                InversionesFilterTabs(
                    selectedType = filterType,
                    onSelect = { type -> filterType = type }
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add_inversion_button_mobile"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "NUEVA INVERSIÓN",
                            color = ElQadreNavy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterOptions = listOf(
                        "TODAS" to "Todas",
                        "MES" to "Mes",
                        "ANIO" to "Año"
                    )
                    filterOptions.forEach { (type, label) ->
                        val isSelected = filterType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { filterType = type },
                            label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElQadreNavy,
                                selectedLabelColor = Color.White,
                                containerColor = Slate100,
                                labelColor = Slate700
                            ),
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }
        }

        // Selector específico para MES o AÑO
        if (filterType == "MES") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (selectedMonth > 0) {
                                selectedMonth--
                            } else {
                                selectedMonth = 11
                                selectedYear--
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior", tint = ElQadreNavy)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(20.dp))
                        Text(
                            text = "${monthNames[selectedMonth]} $selectedYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }

                    IconButton(
                        onClick = {
                            if (selectedMonth < 11) {
                                selectedMonth++
                            } else {
                                selectedMonth = 0
                                selectedYear++
                            }
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", tint = ElQadreNavy)
                    }
                }
            }
        } else if (filterType == "ANIO") {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedYear-- }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Año anterior", tint = ElQadreNavy)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Año: $selectedYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }

                    IconButton(
                        onClick = { selectedYear++ }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Año siguiente", tint = ElQadreNavy)
                    }
                }
            }
        }

        // List of Investments
        if (filteredInversiones.isEmpty()) {
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
                        imageVector = Icons.Outlined.MonetizationOn,
                        contentDescription = null,
                        tint = Slate300,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No hay inversiones registradas",
                        fontSize = 18.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Use el botón superior para agregar una nueva inversión.",
                        fontSize = 14.sp,
                        color = Slate400,
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
                items(filteredInversiones) { inversion ->
                    InversionCard(
                        inversion = inversion,
                        onClick = { selectedInversionForDetail = inversion }
                    )
                }
            }
        }
    }

    if (showAddDialog || selectedInversionForEdit != null) {
        AddEditInversionDuenoDialog(
            inversion = selectedInversionForEdit,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = {
                showAddDialog = false
                selectedInversionForEdit = null
            }
        )
    }

    if (selectedInversionForDetail != null) {
        InversionDetailDialog(
            inversion = selectedInversionForDetail!!,
            onDismiss = { selectedInversionForDetail = null },
            onEditClick = {
                selectedInversionForEdit = selectedInversionForDetail
                selectedInversionForDetail = null
            },
            onDeleteClick = {
                inversionToDelete = selectedInversionForDetail
                selectedInversionForDetail = null
            }
        )
    }

    if (inversionToDelete != null) {
        AlertDialog(
            onDismissRequest = { inversionToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(40.dp)) },
            title = {
                Text(
                    text = "Confirmar Eliminación",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de que desea eliminar la inversión \"${inversionToDelete!!.name}\" permanentemente? Esta acción no se puede deshacer.",
                    fontSize = 16.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInversion(inversionToDelete!!)
                        inversionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("SÍ, ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { inversionToDelete = null },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun InversionesFilterTabs(
    selectedType: String,
    onSelect: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = listOf(
            "TODAS" to "Todas",
            "MES" to "Mes",
            "ANIO" to "Año"
        )
        options.forEach { (type, label) ->
            val isSelected = selectedType == type
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(type) },
                label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElQadreNavy,
                    selectedLabelColor = Color.White,
                    containerColor = Slate100,
                    labelColor = Slate700
                ),
                modifier = Modifier.height(48.dp)
            )
        }
    }
}

@Composable
fun InversionCard(
    inversion: Inversion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate200),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = inversion.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = formatTimestamp(inversion.date),
                        fontSize = 13.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (inversion.observation.isNotBlank()) {
                    Text(
                        text = inversion.observation,
                        fontSize = 13.sp,
                        color = Slate600,
                        maxLines = 1,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (inversion.currency != "CUP") {
                    Text(
                        text = "$${"%,.2f".format(inversion.originalAmount)} ${inversion.currency}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "≈ $${"%,.2f".format(inversion.amount)} CUP",
                        fontSize = 13.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "$${"%,.2f".format(inversion.amount)} CUP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElQadreNavy
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun AddEditInversionDuenoDialog(
    inversion: Inversion? = null,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(inversion?.name ?: "") }
    var amountText by remember {
        mutableStateOf(
            if (inversion != null && inversion.originalAmount > 0.0) inversion.originalAmount.toString()
            else inversion?.amount?.toString() ?: ""
        )
    }
    var currency by remember { mutableStateOf(inversion?.currency ?: "CUP") }
    var observation by remember { mutableStateOf(inversion?.observation ?: "") }
    
    val generalConfig = uiState.generalConfig
    val tasaUsd = generalConfig?.tasaUsd ?: 0.0
    val tasaEur = generalConfig?.tasaEur ?: 0.0
    
    val exchangeRate = remember(currency, tasaUsd, tasaEur) {
        when (currency) {
            "USD" -> if (tasaUsd > 0.0) tasaUsd else 1.0
            "EUR" -> if (tasaEur > 0.0) tasaEur else 1.0
            else -> 1.0
        }
    }
    
    val amountVal = amountText.toDoubleOrNull() ?: 0.0
    val convertedAmount = amountVal * exchangeRate
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddCard,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = if (inversion == null) "Registrar Nueva Inversión" else "Editar Inversión",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Slate600,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Concepto o Nombre de la Inversión *",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Ej. Compra de Cafetera Express") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Seleccione la Moneda de la Operación",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val currencies = listOf("CUP", "USD", "EUR")
                        currencies.forEach { curr ->
                            val isSelected = currency == curr
                            Button(
                                onClick = { currency = curr },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) ElQadreNavy else Slate100,
                                    contentColor = if (isSelected) Color.White else Slate700
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(curr, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Monto Original de la Inversión *",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        placeholder = { Text("0.00") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }

                if (currency != "CUP") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Slate100),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Conversión Automática de Moneda",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tasa del sistema:", fontSize = 13.sp, color = Slate600)
                                Text(
                                    text = "1 $currency = $exchangeRate CUP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Monto Convertido:", fontSize = 13.sp, color = Slate600)
                                Text(
                                    text = "$${"%,.2f".format(convertedAmount)} CUP",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ElQadreNavy
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Observación u Detalles (Opcional)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = observation,
                        onValueChange = { observation = it },
                        placeholder = { Text("Detalles del proveedor, número de recibo, etc.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.trim().isBlank() || amountVal <= 0.0) return@Button
                    
                    val inv = Inversion(
                        id = inversion?.id ?: 0L,
                        name = name.trim(),
                        category = inversion?.category ?: "DUEÑO",
                        amount = convertedAmount,
                        date = inversion?.date ?: System.currentTimeMillis(),
                        usefulLife = inversion?.usefulLife ?: 12.0,
                        usefulLifeUnit = inversion?.usefulLifeUnit ?: "MESES",
                        observation = observation.trim(),
                        scope = inversion?.scope ?: "PRODUCCION",
                        currency = currency,
                        originalAmount = amountVal,
                        exchangeRate = exchangeRate,
                        convertedAmount = convertedAmount,
                        targetProductId = inversion?.targetProductId
                    )
                    if (inversion == null) {
                        viewModel.insertInversion(inv)
                    } else {
                        viewModel.updateInversion(inv)
                    }
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                enabled = name.trim().isNotBlank() && amountVal > 0.0
            ) {
                Text(
                    text = if (inversion == null) "GUARDAR INVERSIÓN" else "ACTUALIZAR INVERSIÓN",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Text(
                    text = "CANCELAR",
                    color = Slate600,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun InversionDetailDialog(
    inversion: Inversion,
    onDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Detalle de Inversión",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Slate600,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DetailItemRow(label = "Concepto / Nombre", value = inversion.name, isBold = true)
                DetailItemRow(label = "Fecha y Hora", value = formatTimestamp(inversion.date))
                
                DetailItemRow(
                    label = "Moneda Original", 
                    value = inversion.currency,
                    valueColor = ElQadreNavy
                )
                
                DetailItemRow(
                    label = "Monto Original", 
                    value = "$${"%,.2f".format(inversion.originalAmount)} ${inversion.currency}",
                    isBold = true
                )

                if (inversion.currency != "CUP") {
                    DetailItemRow(
                        label = "Tasa de Cambio Aplicada", 
                        value = "1 ${inversion.currency} = ${inversion.exchangeRate} CUP"
                    )
                    DetailItemRow(
                        label = "Monto Convertido (CUP)", 
                        value = "$${"%,.2f".format(inversion.convertedAmount)} CUP",
                        isBold = true,
                        valueColor = ElQadreNavy
                    )
                }

                DetailItemRow(
                    label = "Detalles / Observaciones", 
                    value = if (inversion.observation.isNotBlank()) inversion.observation else "Sin observaciones"
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onEditClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "EDITAR INVERSIÓN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = "ELIMINAR INVERSIÓN",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Slate300)
            ) {
                Text(
                    text = "CERRAR",
                    color = Slate600,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
fun DetailItemRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    valueColor: Color = Slate800
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Slate500,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            fontSize = 16.sp,
            color = valueColor,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium
        )
        HorizontalDivider(color = Slate100, thickness = 1.dp)
    }
}

@Composable
fun DuenoPersonalView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showAddForm by remember { mutableStateOf(false) }

    var nombreCompleto by remember { mutableStateOf("") }
    var carnetIdentidad by remember { mutableStateOf("") }
    var movil by remember { mutableStateOf("") }
    var formaPago by remember { mutableStateOf("") }

    var tieneAccesoApp by remember { mutableStateOf(false) }
    var usernameInput by remember { mutableStateOf("") }
    var passwordPlainInput by remember { mutableStateOf("") }
    var roleSelection by remember { mutableStateOf("CAJERO") }
    var dependienteTipoSelection by remember { mutableStateOf("SALON") }
    var montoPorProductoInput by remember { mutableStateOf("") }
    var isActiveApp by remember { mutableStateOf(true) }
    var permisoProduccion by remember { mutableStateOf(true) }
    var permisoMercancias by remember { mutableStateOf(true) }
    var permisoPersonal by remember { mutableStateOf(true) }
    var permisoControlNegocio by remember { mutableStateOf(false) }

    var editingPersonal by remember { mutableStateOf<PersonalContratado?>(null) }

    val personalList = uiState.personalContratado

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Personal Contratado",
            subtitle = "Registro de personal y configuración de acceso a ElQadre",
            icon = Icons.Outlined.Group,
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Action Buttons Row: AGREGAR PERSONAL & GENERAR PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
            Button(
                onClick = { showAddForm = !showAddForm },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("btn_agregar_personal"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showAddForm) Slate700 else ElQadreNavy
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (showAddForm) "CERRAR FORMULARIO" else "AGREGAR PERSONAL",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Button(
                onClick = {
                    val businessName = uiState.businessConfig?.nombreNegocio ?: uiState.businessName
                    val duenoName = uiState.currentUser?.fullName ?: "Dueño"
                    PersonalPdfExporter.exportPersonalContratadoPdf(context, personalList, businessName, duenoName)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
                    .testTag("btn_generar_pdf_personal"),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold, contentColor = ElQadreNavy),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = ElQadreNavy
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GENERAR PDF",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            }
        }

        // Formulario para Agregar Personal
        if (showAddForm) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_agregar_personal"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(2.dp, ElQadreGold),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "NUEVO PERSONAL CONTRATADO",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )

                    // 1. Nombre completo
                    OutlinedTextField(
                        value = nombreCompleto,
                        onValueChange = { nombreCompleto = it },
                        label = { Text("Nombre completo *") },
                        placeholder = { Text("Ej. Juan Pérez García") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_nombre_completo"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    // 2. Carné de identidad (CI)
                    OutlinedTextField(
                        value = carnetIdentidad,
                        onValueChange = { carnetIdentidad = it },
                        label = { Text("Carné de identidad (CI) *") },
                        placeholder = { Text("Ej. 90051212345") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_carnet_identidad"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    // 3. Móvil
                    OutlinedTextField(
                        value = movil,
                        onValueChange = { movil = it },
                        label = { Text("Móvil *") },
                        placeholder = { Text("Ej. +53 52123456") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_movil"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        ),
                        trailingIcon = {
                            ContactPickerIconButton(
                                onContactPicked = { pickedName, pickedPhone ->
                                    movil = pickedPhone
                                    if (nombreCompleto.isBlank() && pickedName.isNotBlank()) {
                                        nombreCompleto = pickedName
                                    }
                                }
                            )
                        }
                    )

                    // 4. Forma de pago
                    OutlinedTextField(
                        value = formaPago,
                        onValueChange = { formaPago = it },
                        label = { Text("Forma de pago *") },
                        placeholder = { Text("Ej. Por jornada, Por plato, Fijo mensual...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_forma_pago"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    // SECCIÓN DE ACCESO A LA APLICACIÓN ELQADRE
                    HorizontalDivider(color = Slate200, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (tieneAccesoApp) Sky50 else Slate50, RoundedCornerShape(10.dp))
                            .border(1.dp, if (tieneAccesoApp) Sky200 else Slate200, RoundedCornerShape(10.dp))
                            .clickable { tieneAccesoApp = !tieneAccesoApp }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Outlined.ManageAccounts,
                                contentDescription = null,
                                tint = if (tieneAccesoApp) ElQadreNavy else Slate500,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "¿Utilizará la aplicación ElQadre?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (tieneAccesoApp) ElQadreNavy else Slate700
                                )
                                Text(
                                    text = if (tieneAccesoApp) "Configurar credenciales y permisos locales" else "Persona sin usuario en la app",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                        Switch(
                            checked = tieneAccesoApp,
                            onCheckedChange = { tieneAccesoApp = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElQadreNavy
                            )
                        )
                    }

                    if (tieneAccesoApp) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate50),
                            border = BorderStroke(1.dp, Slate200)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "CREDENCIALES Y ROL OPERATIVO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )

                                // Usuario
                                OutlinedTextField(
                                    value = usernameInput,
                                    onValueChange = { usernameInput = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                                    label = { Text("Usuario *") },
                                    placeholder = { Text("Ej. carlos") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("input_usuario_personal"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                // Contraseña
                                var passwordVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = passwordPlainInput,
                                    onValueChange = { passwordPlainInput = it },
                                    label = { Text("Contraseña *") },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = "Mostrar contraseña",
                                                tint = Slate500
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("input_password_personal"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ElQadreNavy,
                                        focusedLabelColor = ElQadreNavy
                                    )
                                )

                                // Rol Operativo: DUEÑO, CAJERO o DEPENDIENTE
                                Text(
                                    text = "Rol asignado:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate700
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = roleSelection == "DUENO" || roleSelection == "DUEÑO",
                                        onClick = {
                                            roleSelection = "DUENO"
                                            permisoProduccion = true
                                            permisoMercancias = true
                                            permisoPersonal = true
                                            permisoControlNegocio = true
                                        },
                                        label = { Text("DUEÑO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).testTag("chip_role_dueno"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElQadreNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = roleSelection == "CAJERO",
                                        onClick = { roleSelection = "CAJERO" },
                                        label = { Text("CAJERO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).testTag("chip_role_cajero"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElQadreNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                    FilterChip(
                                        selected = roleSelection == "DEPENDIENTE",
                                        onClick = { roleSelection = "DEPENDIENTE" },
                                        label = { Text("DEPENDIENTE", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f).testTag("chip_role_dependiente"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElQadreNavy,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }

                                if (roleSelection == "DEPENDIENTE") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = dependienteTipoSelection == "SALON",
                                            onClick = { dependienteTipoSelection = "SALON" },
                                            label = { Text("Salón", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = dependienteTipoSelection == "BARRA",
                                            onClick = { dependienteTipoSelection = "BARRA" },
                                            label = { Text("Barra", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = montoPorProductoInput,
                                        onValueChange = { montoPorProductoInput = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Comisión / Utilidad por producto (CUP)") },
                                        placeholder = { Text("Ej. 50.0") },
                                        singleLine = true,
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }

                                // Permisos
                                Text(
                                    text = "Permisos de acceso:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate700
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { permisoProduccion = !permisoProduccion }
                                    ) {
                                        Checkbox(
                                            checked = permisoProduccion,
                                            onCheckedChange = { permisoProduccion = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Text("Control de Producción", fontSize = 12.sp, color = Slate800)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { permisoMercancias = !permisoMercancias }
                                    ) {
                                        Checkbox(
                                            checked = permisoMercancias,
                                            onCheckedChange = { permisoMercancias = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Text("Control de Mercaderías", fontSize = 12.sp, color = Slate800)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { permisoPersonal = !permisoPersonal }
                                    ) {
                                        Checkbox(
                                            checked = permisoPersonal,
                                            onCheckedChange = { permisoPersonal = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Text("Control de Personal", fontSize = 12.sp, color = Slate800)
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { permisoControlNegocio = !permisoControlNegocio }
                                    ) {
                                        Checkbox(
                                            checked = permisoControlNegocio,
                                            onCheckedChange = { permisoControlNegocio = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Text("Control del Negocio", fontSize = 12.sp, color = Slate800)
                                    }
                                }

                                // Estado de acceso
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Text("Estado de acceso (Activo)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate800)
                                    Switch(
                                        checked = isActiveApp,
                                        onCheckedChange = { isActiveApp = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF10B981)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (nombreCompleto.isBlank()) {
                                Toast.makeText(context, "El nombre completo es obligatorio", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (tieneAccesoApp) {
                                if (usernameInput.isBlank()) {
                                    Toast.makeText(context, "El nombre de usuario es obligatorio", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (passwordPlainInput.isBlank()) {
                                    Toast.makeText(context, "La contraseña es obligatoria", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (roleSelection.uppercase() in listOf("DUENO", "DUEÑO")) {
                                    val currentDuenos = personalList.count { it.tieneAccesoApp && it.role.uppercase() in listOf("DUENO", "DUEÑO") }
                                    if (currentDuenos >= 3) {
                                        Toast.makeText(
                                            context,
                                            "Límite alcanzado: El negocio solo puede tener un máximo de 3 DUEÑOS en total (el principal y hasta 2 adicionales).",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@Button
                                    }
                                }
                            }

                            val passHash = if (passwordPlainInput.isNotBlank()) passwordPlainInput.trim().toSha256() else ""
                            val nuevoPersonal = PersonalContratado(
                                nombreCompleto = nombreCompleto.trim(),
                                carnetIdentidad = carnetIdentidad.trim(),
                                movil = movil.trim(),
                                formaPago = formaPago.trim().ifBlank { "A convenir" },
                                tieneAccesoApp = tieneAccesoApp,
                                username = usernameInput.trim().lowercase(),
                                passwordHash = passHash,
                                passwordPlain = passwordPlainInput.trim(),
                                role = roleSelection,
                                dependienteTipo = dependienteTipoSelection,
                                montoPorProducto = montoPorProductoInput.toDoubleOrNull() ?: 0.0,
                                isActive = isActiveApp,
                                permisoProduccion = permisoProduccion,
                                permisoMercancias = permisoMercancias,
                                permisoPersonal = permisoPersonal,
                                permisoControlNegocio = permisoControlNegocio
                            )
                            viewModel.insertPersonalContratado(nuevoPersonal) {
                                if (nuevoPersonal.tieneAccesoApp) {
                                    try {
                                        val jsonFile = com.example.util.PersonalUserDataManager.generatePersonalUserJson(
                                            context = context,
                                            personal = nuevoPersonal,
                                            configNegocio = uiState.businessConfig,
                                            configGeneral = uiState.generalConfig
                                        )
                                        com.example.util.PersonalUserDataManager.sharePersonalUserViaWhatsApp(
                                            context = context,
                                            personal = nuevoPersonal,
                                            jsonFile = jsonFile
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Personal guardado. Puede compartir sus datos desde la lista.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                nombreCompleto = ""
                                carnetIdentidad = ""
                                movil = ""
                                formaPago = ""
                                tieneAccesoApp = false
                                usernameInput = ""
                                passwordPlainInput = ""
                                roleSelection = "CAJERO"
                                dependienteTipoSelection = "SALON"
                                montoPorProductoInput = ""
                                isActiveApp = true
                                showAddForm = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_guardar_nuevo_personal"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GUARDAR PERSONAL", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Sección: Lista de Personal Contratado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PERSONAL CONTRATADO (${personalList.size})",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy
            )
        }

        if (personalList.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.PersonOff,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No hay personal contratado registrado.",
                            fontSize = 14.sp,
                            color = Slate500,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Toque 'AGREGAR PERSONAL' para registrar un nuevo trabajador y habilitar acceso.",
                            fontSize = 12.sp,
                            color = Slate400
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                personalList.forEach { p ->
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_personal_${p.id}"),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, if (p.tieneAccesoApp) ElQadreGold.copy(alpha = 0.6f) else Slate200),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.nombreCompleto,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    if (p.tieneAccesoApp && p.username.isNotBlank()) {
                                        Text(
                                            text = "Usuario: @${p.username}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ElQadreNavy
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showDeleteDialog = true },
                                    modifier = Modifier.testTag("btn_delete_personal_${p.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Eliminar personal",
                                        tint = Color(0xFFDC2626)
                                    )
                                }
                            }

                            // Badges: Rol y Estado de acceso
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (p.tieneAccesoApp) {
                                    val isDuenoRole = p.role.uppercase() in listOf("DUENO", "DUEÑO")
                                    val roleLabel = when {
                                        isDuenoRole -> "DUEÑO"
                                        p.role.uppercase() == "CAJERO" -> "CAJERO"
                                        else -> "DEPENDIENTE (${if (p.dependienteTipo == "BARRA") "Barra" else "Salón"})"
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDuenoRole) ElQadreGold.copy(alpha = 0.25f) else ElQadreNavy.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = roleLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDuenoRole) Color(0xFF92400E) else ElQadreNavy,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (p.isActive) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = if (p.isActive) "ACCESO ACTIVO" else "ACCESO INACTIVO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (p.isActive) Color(0xFF065F46) else Color(0xFF991B1B),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Slate100
                                    ) {
                                        Text(
                                            text = "SIN ACCESO A LA APP",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Slate600,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Slate100)

                            // CI, Móvil, Forma de pago
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = Slate500,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "CI: ${if (p.carnetIdentidad.isNotBlank()) p.carnetIdentidad else "No especificado"}",
                                    fontSize = 13.sp,
                                    color = Slate600
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = Slate500,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Móvil: ${if (p.movil.isNotBlank()) p.movil else "No especificado"}",
                                    fontSize = 13.sp,
                                    color = Slate600
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Forma de pago: ",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate700
                                )
                                Text(
                                    text = p.formaPago,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Acciones de Usuario
                            if (p.tieneAccesoApp) {
                                Button(
                                    onClick = {
                                        try {
                                            val jsonFile = com.example.util.PersonalUserDataManager.generatePersonalUserJson(
                                                context = context,
                                                personal = p,
                                                configNegocio = uiState.businessConfig,
                                                configGeneral = uiState.generalConfig
                                            )
                                            com.example.util.PersonalUserDataManager.sharePersonalUserViaWhatsApp(
                                                context = context,
                                                personal = p,
                                                jsonFile = jsonFile
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error al generar datos: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElQadreGold,
                                        contentColor = ElQadreNavy
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_compartir_datos_${p.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Share,
                                        contentDescription = null,
                                        tint = ElQadreNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "COMPARTIR DATOS",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }

                                OutlinedButton(
                                    onClick = { editingPersonal = p },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Slate300),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .testTag("btn_editar_personal_${p.id}")
                                ) {
                                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = Slate700, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EDITAR ACCESO Y DATOS", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val firstWord = p.nombreCompleto.trim().split(" ").firstOrNull()?.lowercase() ?: "usuario"
                                        editingPersonal = p.copy(
                                            tieneAccesoApp = true,
                                            username = firstWord,
                                            role = "CAJERO"
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElQadreNavy,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("btn_habilitar_acceso_${p.id}")
                                ) {
                                    Icon(Icons.Outlined.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("HABILITAR ACCESO A ELQADRE", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Eliminar Personal", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
                            text = { Text("¿Está seguro de que desea eliminar a ${p.nombreCompleto} de la lista de personal contratado?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.deletePersonalContratado(p)
                                        showDeleteDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                ) {
                                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancelar", color = Slate600)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

    if (editingPersonal != null) {
        EditarPersonalDialog(
            personal = editingPersonal!!,
            personalList = personalList,
            onDismiss = { editingPersonal = null },
            onSave = { updated ->
                viewModel.updatePersonalContratado(updated)
                editingPersonal = null
            }
        )
    }
}

@Composable
fun EditarPersonalDialog(
    personal: PersonalContratado,
    personalList: List<PersonalContratado> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (PersonalContratado) -> Unit
) {
    val context = LocalContext.current
    var nombreCompleto by remember { mutableStateOf(personal.nombreCompleto) }
    var carnetIdentidad by remember { mutableStateOf(personal.carnetIdentidad) }
    var movil by remember { mutableStateOf(personal.movil) }
    var formaPago by remember { mutableStateOf(personal.formaPago) }

    var tieneAccesoApp by remember { mutableStateOf(personal.tieneAccesoApp) }
    var username by remember { mutableStateOf(personal.username) }
    var newPassword by remember { mutableStateOf(personal.passwordPlain) }
    var roleSelection by remember { mutableStateOf(if (personal.role.isNotBlank()) personal.role else "CAJERO") }
    var dependienteTipoSelection by remember { mutableStateOf(if (personal.dependienteTipo.isNotBlank()) personal.dependienteTipo else "SALON") }
    var montoPorProductoInput by remember { mutableStateOf(if (personal.montoPorProducto > 0) personal.montoPorProducto.toString() else "") }
    var isActiveApp by remember { mutableStateOf(personal.isActive) }

    var permisoProduccion by remember { mutableStateOf(personal.permisoProduccion) }
    var permisoMercancias by remember { mutableStateOf(personal.permisoMercancias) }
    var permisoPersonal by remember { mutableStateOf(personal.permisoPersonal) }
    var permisoControlNegocio by remember { mutableStateOf(personal.permisoControlNegocio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Editar Personal y Acceso", fontWeight = FontWeight.Bold, color = ElQadreNavy)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nombreCompleto,
                    onValueChange = { nombreCompleto = it },
                    label = { Text("Nombre completo *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = carnetIdentidad,
                    onValueChange = { carnetIdentidad = it },
                    label = { Text("Carné de identidad (CI) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = movil,
                    onValueChange = { movil = it },
                    label = { Text("Móvil *") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_input_movil"),
                    trailingIcon = {
                        ContactPickerIconButton(
                            onContactPicked = { pickedName, pickedPhone ->
                                movil = pickedPhone
                                if (nombreCompleto.isBlank() && pickedName.isNotBlank()) {
                                    nombreCompleto = pickedName
                                }
                            }
                        )
                    }
                )
                OutlinedTextField(
                    value = formaPago,
                    onValueChange = { formaPago = it },
                    label = { Text("Forma de pago *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acceso a ElQadre", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                        Text("Habilitar inicio de sesión para esta persona", fontSize = 11.sp, color = Slate500)
                    }
                    Switch(
                        checked = tieneAccesoApp,
                        onCheckedChange = { tieneAccesoApp = it }
                    )
                }

                if (tieneAccesoApp) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                        label = { Text("Usuario *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    var passVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Contraseña *") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(if (passVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Rol operativo:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = roleSelection == "DUENO" || roleSelection == "DUEÑO",
                            onClick = {
                                roleSelection = "DUENO"
                                permisoProduccion = true
                                permisoMercancias = true
                                permisoPersonal = true
                                permisoControlNegocio = true
                            },
                            label = { Text("DUEÑO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).testTag("chip_edit_role_dueno"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElQadreNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = roleSelection == "CAJERO",
                            onClick = { roleSelection = "CAJERO" },
                            label = { Text("CAJERO", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).testTag("chip_edit_role_cajero"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElQadreNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = roleSelection == "DEPENDIENTE",
                            onClick = { roleSelection = "DEPENDIENTE" },
                            label = { Text("DEPENDIENTE", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f).testTag("chip_edit_role_dependiente"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElQadreNavy,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    if (roleSelection == "DEPENDIENTE") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = dependienteTipoSelection == "SALON",
                                onClick = { dependienteTipoSelection = "SALON" },
                                label = { Text("Salón") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = dependienteTipoSelection == "BARRA",
                                onClick = { dependienteTipoSelection = "BARRA" },
                                label = { Text("Barra") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = montoPorProductoInput,
                            onValueChange = { montoPorProductoInput = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Comisión por producto (CUP)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("Permisos:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permisoProduccion, onCheckedChange = { permisoProduccion = it })
                        Text("Control de Producción", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permisoMercancias, onCheckedChange = { permisoMercancias = it })
                        Text("Control de Mercaderías", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permisoPersonal, onCheckedChange = { permisoPersonal = it })
                        Text("Control de Personal", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = permisoControlNegocio, onCheckedChange = { permisoControlNegocio = it })
                        Text("Control del Negocio", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Estado de acceso (Activo)", fontSize = 13.sp)
                        Switch(checked = isActiveApp, onCheckedChange = { isActiveApp = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nombreCompleto.isBlank()) {
                        Toast.makeText(context, "El nombre completo es obligatorio", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (tieneAccesoApp) {
                        if (username.isBlank()) {
                            Toast.makeText(context, "El usuario es obligatorio", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPassword.isBlank() && personal.passwordHash.isBlank()) {
                            Toast.makeText(context, "La contraseña es obligatoria", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (roleSelection.uppercase() in listOf("DUENO", "DUEÑO")) {
                            val currentDuenos = personalList.count { it.tieneAccesoApp && it.role.uppercase() in listOf("DUENO", "DUEÑO") && it.id != personal.id }
                            if (currentDuenos >= 3) {
                                Toast.makeText(
                                    context,
                                    "Límite alcanzado: El negocio solo puede tener un máximo de 3 DUEÑOS en total (el principal y hasta 2 adicionales).",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                        }
                    }

                    val updatedPassHash = if (newPassword.isNotBlank()) newPassword.trim().toSha256() else personal.passwordHash
                    val updatedPersonal = personal.copy(
                        nombreCompleto = nombreCompleto.trim(),
                        carnetIdentidad = carnetIdentidad.trim(),
                        movil = movil.trim(),
                        formaPago = formaPago.trim().ifBlank { "A convenir" },
                        tieneAccesoApp = tieneAccesoApp,
                        username = username.trim().lowercase(),
                        passwordHash = updatedPassHash,
                        passwordPlain = newPassword.trim().ifBlank { personal.passwordPlain },
                        role = roleSelection,
                        dependienteTipo = dependienteTipoSelection,
                        montoPorProducto = montoPorProductoInput.toDoubleOrNull() ?: 0.0,
                        isActive = isActiveApp,
                        permisoProduccion = permisoProduccion,
                        permisoMercancias = permisoMercancias,
                        permisoPersonal = permisoPersonal,
                        permisoControlNegocio = permisoControlNegocio
                    )
                    onSave(updatedPersonal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate600)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuenoControlNegocioView(
    uiState: MainUiState,
    viewModel: com.example.ui.viewmodel.MainViewModel,
    isWide: Boolean,
    onBack: () -> Unit
) {
    DuenoControlNegocioScreen(
        uiState = uiState,
        viewModel = viewModel,
        isWide = isWide,
        onBack = onBack
    )
}

/*
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuenoControlNegocioViewLegacy(

    // Filters implementation
    val filteredOrders = if (selectedPeriod == "Jornada") {
        if (selectedJornadaId != null) {
            uiState.allOrders.filter { it.jornadaId == selectedJornadaId && it.status == "COBRADA" }
        } else {
            emptyList()
        }
    } else {
        uiState.allOrders.filter {
            it.status == "COBRADA" && it.closedAt != null && it.closedAt in periodRange
        }
    }

    val filteredJornadas = if (selectedPeriod == "Jornada") {
        if (selectedJornadaId != null) {
            uiState.allJornadas.filter { it.id == selectedJornadaId }
        } else {
            emptyList()
        }
    } else {
        uiState.allJornadas.filter {
            val t = it.closedAt ?: it.openedAt
            t in periodRange
        }
    }

    val orderIds = filteredOrders.map { it.id }.toSet()
    val filteredOrderItems = uiState.allOrderItems.filter { it.orderId in orderIds }

    // Core Metrics Calculations
    val totalVendido = filteredOrders.sumOf { it.totalAmount }
    val comandaCount = filteredOrders.size

    val cashIngresos = filteredOrders.sumOf { order ->
        when (order.paymentMethod.uppercase()) {
            "EFECTIVO" -> order.totalAmount
            "MIXTO" -> (order.cashReceived - order.changeGiven).coerceAtLeast(0.0)
            else -> 0.0
        }
    }

    val transferIngresos = filteredOrders.sumOf { order ->
        when (order.paymentMethod.uppercase()) {
            "TRANSFERENCIA" -> order.totalAmount
            "MIXTO" -> (order.totalAmount - (order.cashReceived - order.changeGiven).coerceAtLeast(0.0)).coerceAtLeast(0.0)
            else -> 0.0
        }
    }

    val diferenciaCuadre = filteredJornadas.sumOf { it.cashDifference }

    // Currency Breakdown
    val salesCup = filteredOrders.filter { it.currency == "CUP" }.sumOf { it.totalAmount }
    val salesUsd = filteredOrders.filter { it.currency == "USD" }.sumOf { it.originalAmount }
    val salesEur = filteredOrders.filter { it.currency == "EUR" }.sumOf { it.originalAmount }

    // Cocina vs Barra
    val prodSales = filteredOrders.sumOf { it.totalCocina }
    val prodQty = filteredOrderItems.filter { it.destination == "COCINA" }.sumOf { it.quantity }

    val mercSales = filteredOrders.sumOf { it.totalBarra }
    val mercQty = filteredOrderItems.filter { it.destination == "BARRA" }.sumOf { it.quantity }

    val comandaAverage = if (comandaCount > 0) totalVendido / comandaCount else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Control del Negocio",
            subtitle = "Consulte, entienda y compare el estado económico",
            icon = Icons.Outlined.Analytics,
            onBack = onBack
        )

        // Sub Tab selector for Elder Friendly Navigation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate100, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "RESUMEN" to "Resumen",
                "AREAS" to "Áreas",
                "JORNADAS" to "Jornadas",
                "INFORMES" to "Informes"
            ).forEach { (tabKey, label) ->
                val isSelected = activeSubTab == tabKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) ElQadreNavy else Color.Transparent)
                        .clickable { activeSubTab = tabKey }
                        .testTag("subtab_${tabKey.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) ElQadreGold else Slate700,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        when (activeSubTab) {
            "RESUMEN" -> {
                // Period Filters Block
                Card(
                    colors = CardDefaults.cardColors(containerColor = Slate50),
                    border = BorderStroke(1.dp, Slate200),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "FILTRAR PERÍODO DE CONSULTA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate500
                        )

                        // Elder-friendly wrap row
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Hoy", "Jornada", "Esta Semana", "Este Mes", "Rango de Fechas").forEach { opt ->
                                val active = selectedPeriod == opt
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (active) ElQadreNavy else Color.White,
                                    border = BorderStroke(1.dp, if (active) ElQadreNavy else Slate300),
                                    modifier = Modifier
                                        .clickable { selectedPeriod = opt }
                                        .testTag("filter_period_$opt")
                                ) {
                                    Text(
                                        text = opt.uppercase(),
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (active) ElQadreGold else Slate700
                                    )
                                }
                            }
                        }

                        // Jornada dropdown selector if chosen
                        if (selectedPeriod == "Jornada") {
                            var isJornadaDropdownExpanded by remember { mutableStateOf(false) }
                            val activeJor = uiState.allJornadas.find { it.id == selectedJornadaId }
                            val label = if (activeJor != null) {
                                "Jornada #${activeJor.id} - ${java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(java.util.Date(activeJor.openedAt))} (${activeJor.openedBy})"
                            } else "Seleccionar Jornada"

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Seleccione la jornada a inspeccionar:", fontSize = 12.sp, color = Slate600, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, Slate300, RoundedCornerShape(10.dp))
                                        .clickable { isJornadaDropdownExpanded = true }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = ElQadreNavy)
                                    }
                                }

                                DropdownMenu(
                                    expanded = isJornadaDropdownExpanded,
                                    onDismissRequest = { isJornadaDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    if (uiState.allJornadas.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No hay jornadas registradas") },
                                            onClick = { isJornadaDropdownExpanded = false }
                                        )
                                    } else {
                                        uiState.allJornadas.forEach { jor ->
                                            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(jor.openedAt))
                                            val statusText = if (jor.isOpen) "Abierta" else "Cerrada"
                                            DropdownMenuItem(
                                                text = { Text("Jor. #${jor.id} - $dateStr ($statusText) - Ventas: $${jor.totalSales}") },
                                                onClick = {
                                                    selectedJornadaId = jor.id
                                                    isJornadaDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Rango de fechas selector
                        if (selectedPeriod == "Rango de Fechas") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customStartDateStr,
                                    onValueChange = { customStartDateStr = it },
                                    label = { Text("Desde (DD/MM/AAAA)") },
                                    placeholder = { Text("Ej. 01/09/2026") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy)
                                )
                                OutlinedTextField(
                                    value = customEndDateStr,
                                    onValueChange = { customEndDateStr = it },
                                    label = { Text("Hasta (DD/MM/AAAA)") },
                                    placeholder = { Text("Ej. 30/09/2026") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElQadreNavy)
                                )
                            }
                        }
                    }
                }

                // Four Big Indicators
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Ventas & Arqueo
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = ElQadreNavy)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("VENTAS TOTALES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreGold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$${"%.2f".format(totalVendido)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                                Text("Ventas acumuladas", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        val isDiffZero = diferenciaCuadre == 0.0
                        val diffColor = if (diferenciaCuadre < 0.0) Color(0xFFDC2626) else if (isDiffZero) Slate700 else Color(0xFF0D9488)
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (diferenciaCuadre < 0.0) Color(0xFFFEF2F2) else Slate100),
                            border = BorderStroke(1.dp, if (diferenciaCuadre < 0.0) Color(0xFFFCA5A5) else Slate200)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("DIFERENCIA CUADRE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (diferenciaCuadre >= 0.0) "+$${"%.2f".format(diferenciaCuadre)}" else "$${"%.2f".format(diferenciaCuadre)}",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = diffColor
                                )
                                Text("Arqueos de jornadas", fontSize = 10.sp, color = Slate500)
                            }
                        }
                    }

                    // Cash & Transfers
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("INGRESOS EFECTIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$${"%.2f".format(cashIngresos)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF065F46))
                                Text("Efectivo real recibido", fontSize = 10.sp, color = Color(0xFF047857).copy(alpha = 0.8f))
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("TRANSFERENCIAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$${"%.2f".format(transferIngresos)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E40AF))
                                Text("Bancos acumulados", fontSize = 10.sp, color = Color(0xFF1D4ED8).copy(alpha = 0.8f))
                            }
                        }
                    }
                }

                // Detailed sales report
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "DESGLOSE DE VENTAS DETALLADO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )

                        val detailRows = listOf(
                            Triple("Total Vendido ($)", "$${"%.2f".format(totalVendido)} CUP", true),
                            Triple("Comandas cobradas", "$comandaCount", false),
                            Triple("Ventas de Producción/Cocina", "$${"%.2f".format(prodSales)} CUP", false),
                            Triple("Ventas de Mercaderías/Barra", "$${"%.2f".format(mercSales)} CUP", false),
                            Triple("Ventas por CUP", "$${"%.2f".format(salesCup)} CUP", false),
                            Triple("Ventas por USD", "$${"%.2f".format(salesUsd)} USD", false),
                            Triple("Ventas por EUR", "$${"%.2f".format(salesEur)} EUR", false),
                            Triple("Efectivo Recibido", "$${"%.2f".format(cashIngresos)} CUP", false),
                            Triple("Transferencias Recibidas", "$${"%.2f".format(transferIngresos)} CUP", false),
                            Triple("Promedio por comanda", "$${"%.2f".format(comandaAverage)} CUP", false),
                            Triple("Total de descuentos", "$0.00 CUP (No utilizado)", false),
                            Triple("Diferencias de cuadre acum.", (if (diferenciaCuadre >= 0.0) "+$${"%.2f".format(diferenciaCuadre)}" else "$${"%.2f".format(diferenciaCuadre)}") + " CUP", false)
                        )

                        detailRows.forEach { (title, valStr, isBold) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    color = if (isBold) ElQadreNavy else Slate600,
                                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Medium
                                )
                                Text(
                                    text = valStr,
                                    fontSize = 13.sp,
                                    color = if (isBold) ElQadreNavy else Slate800,
                                    fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold
                                )
                            }
                            HorizontalDivider(color = Slate100)
                        }
                    }
                }
            }

            "AREAS" -> {
                // Production and Mercaderías comparative
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "COMPARACIÓN DE ÁREAS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )

                        Text(
                            text = "Este reporte compara las ventas generadas por el área de Cocina (Producción) vs el área de Barra (Mercaderías) para el período seleccionado.",
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        // Progress indicator comparison
                        val totalAreas = (prodSales + mercSales).coerceAtLeast(1.0)
                        val prodPercent = (prodSales / totalAreas).toFloat()
                        val mercPercent = (mercSales / totalAreas).toFloat()

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cocina: ${"%.0f".format(prodPercent * 100)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                Text("Barra: ${"%.0f".format(mercPercent * 100)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreGold)
                            }
                            LinearProgressIndicator(
                                progress = { prodPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(7.dp)),
                                color = ElQadreNavy,
                                trackColor = ElQadreGold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Production details
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate50,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("🍳 PRODUCCIÓN (COCINA)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total vendido:", fontSize = 13.sp, color = Slate600)
                                    Text("$${"%.2f".format(prodSales)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cantidad vendida:", fontSize = 13.sp, color = Slate600)
                                    Text("$prodQty unidades", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                            }
                        }

                        // Mercaderías details
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Slate50,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("🍺 MERCADERÍAS (BARRA)", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total vendido:", fontSize = 13.sp, color = Slate600)
                                    Text("$${"%.2f".format(mercSales)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cantidad vendida:", fontSize = 13.sp, color = Slate600)
                                    Text("$mercQty unidades", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                }
                            }
                        }
                    }
                }
            }

            "JORNADAS" -> {
                // Control de Jornadas
                val closedJors = uiState.allJornadas.filter { !it.isOpen }
                val openJors = uiState.allJornadas.filter { it.isOpen }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "HISTORIAL Y CONTROL DE JORNADAS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )

                    // Indicators of count
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate100)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("JORNADAS TOTALES", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                Text("${uiState.allJornadas.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate100)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("CERRADAS", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                Text("${closedJors.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = if (openJors.isNotEmpty()) Color(0xFFFFFBEB) else Slate100),
                            border = BorderStroke(1.dp, if (openJors.isNotEmpty()) Color(0xFFFDE68A) else Color.Transparent)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("ACTIVAS / PEN.", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Bold)
                                Text("${openJors.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (openJors.isNotEmpty()) Color(0xFFB45309) else ElQadreNavy)
                            }
                        }
                    }

                    // List of Jornadas
                    if (uiState.allJornadas.isEmpty()) {
                        Text(
                            text = "No se han registrado jornadas en el dispositivo.",
                            fontSize = 13.sp,
                            color = Slate500,
                            modifier = Modifier.padding(vertical = 24.dp),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        uiState.allJornadas.forEach { jor ->
                            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(jor.openedAt))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, if (jor.isOpen) Color(0xFFFCD34D) else Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showJornadaDetailDialog = jor }
                                    .testTag("jornada_item_${jor.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(text = "Jornada #${jor.id}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                            if (jor.isOpen) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFEF3C7)
                                                ) {
                                                    Text(
                                                        "ACTIVA",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color(0xFFB45309)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Fecha: $dateStr", fontSize = 12.sp, color = Slate500)
                                        Text(text = "Cajero: ${jor.openedBy}", fontSize = 12.sp, color = Slate500)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = "$${"%.2f".format(jor.totalSales)} CUP", fontSize = 15.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                        val colorDiff = if (jor.cashDifference < 0.0) Color(0xFFDC2626) else if (jor.cashDifference == 0.0) Slate500 else Color(0xFF10B981)
                                        Text(
                                            text = "Cuadre: " + if (jor.cashDifference >= 0.0) "+$${"%.2f".format(jor.cashDifference)}" else "$${"%.2f".format(jor.cashDifference)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorDiff
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "INFORMES" -> {
                // Section 5: Informes del Administrador / Sincronizados
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "ÚLTIMA INFORMACIÓN RECIBIDA",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )

                        if (latestImportTimestamp == 0L) {
                            Text(
                                text = "NO HAY INFORMACIÓN RECIBIDA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = "El dispositivo aún no ha importado reportes JSON de los terminales operativos (Salón, Barra, Caja).",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        } else {
                            val importDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(latestImportTimestamp))
                            val importTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(latestImportTimestamp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fecha:", fontSize = 13.sp, color = Slate600)
                                Text(importDate, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            HorizontalDivider(color = Slate100)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Hora:", fontSize = 13.sp, color = Slate600)
                                Text(importTime, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            HorizontalDivider(color = Slate100)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Período:", fontSize = 13.sp, color = Slate600)
                                Text("Acumulado consolidado", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            }
                            HorizontalDivider(color = Slate100)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Estado:", fontSize = 13.sp, color = Slate600)
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFECFDF5)) {
                                    Text(
                                        "Sincronizado",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF047857)
                                    )
                                }
                            }
                        }
                    }
                }

                // Manual imports clipboard pasting block
                Text(
                    text = "IMPORTAR REPORTES OPERATIVOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate500,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val terminals = listOf(
                    Triple("SALON", "Salón (Comandas y Mesas)", lastSalonImport),
                    Triple("BARRA", "Barra (Bebidas y Despacho)", lastBarraImport),
                    Triple("CAJA", "Caja (Arqueos, Pagos y SMS)", lastCajaImport)
                )

                terminals.forEach { (key, title, date) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ElQadreNavy
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Último: $date",
                                    fontSize = 11.sp,
                                    color = if (date.contains("No")) Color(0xFFDC2626) else Slate500
                                )
                            }
                            Button(
                                onClick = {
                                    pasteJsonInput = ""
                                    showImportModalByTerminal = key
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Importar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Import Dialog
    if (showImportModalByTerminal != null) {
        val terminalKey = showImportModalByTerminal!!
        val terminalName = when (terminalKey) {
            "SALON" -> "Salón"
            "BARRA" -> "Barra"
            else -> "Caja"
        }

        AlertDialog(
            onDismissRequest = { showImportModalByTerminal = null },
            title = {
                Text(
                    text = "Importar Reporte de $terminalName",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Pegue el contenido JSON generado para actualizar la información de $terminalName.",
                        fontSize = 12.sp,
                        color = Slate600
                    )
                    OutlinedTextField(
                        value = pasteJsonInput,
                        onValueChange = { pasteJsonInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .testTag("import_paste_field"),
                        placeholder = { Text("Pegar contenido JSON aquí...") },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            cursorColor = ElQadreNavy
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isProcessingImport = true
                        val callback: (Boolean) -> Unit = { success ->
                            isProcessingImport = false
                            if (success) {
                                showImportModalByTerminal = null
                                Toast.makeText(context, "Reporte de $terminalName importado correctamente.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Error al importar. Verifique el formato JSON.", Toast.LENGTH_LONG).show()
                            }
                        }
                        when (terminalKey) {
                            "SALON" -> viewModel.restoreSalonBackupJson(pasteJsonInput, callback)
                            "BARRA" -> viewModel.restoreBarraBackupJson(pasteJsonInput, callback)
                            "CAJA" -> viewModel.restoreCajeroBackupJson(pasteJsonInput, callback)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.testTag("btn_confirm_paste_import")
                ) {
                    Text("Procesar e Importar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportModalByTerminal = null }) {
                    Text("Cancelar")
                }
            },
            containerColor = Color.White
        )
    }

    // AlertDialog for Jornada Detail (View-Only Snapshot)
    if (showJornadaDetailDialog != null) {
        DetalleJornadaCerradaDialog(
            jornada = showJornadaDetailDialog!!,
            onDismiss = { showJornadaDetailDialog = null }
        )
    }
}
*/

@Composable
fun DuenoGastosSubScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val gastosList = uiState.gastosGenerales
    var showAddGastoDialog by remember { mutableStateOf(false) }
    var gastoToEdit by remember { mutableStateOf<GastoGeneral?>(null) }
    var gastoToDelete by remember { mutableStateOf<GastoGeneral?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Gastos Corrientes",
            subtitle = "Costos fijos, operativos y gastos generales",
            icon = Icons.Outlined.ReceiptLong,
            onBack = onBack,
            actions = {
                Button(
                    onClick = { showAddGastoDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp).testTag("btn_nuevo_gasto")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NUEVO GASTO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        )

        // Summary Card
        val totalGastosMensual = gastosList.filter { it.isActive }.sumOf {
            when (it.period) {
                "DIARIO" -> it.amount * 30
                "SEMANAL" -> it.amount * 4.33
                "MENSUAL" -> it.amount
                "ANUAL" -> it.amount / 12
                else -> it.amount
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Gastos Corrientes Estimados (Mensual)",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$${"%.2f".format(totalGastosMensual)} CUP",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreGold
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "LISTADO DE GASTOS (${gastosList.size})",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Slate700
        )

        if (gastosList.isEmpty()) {
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
                        imageVector = Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No se registran gastos corrientes.",
                        color = Slate500,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Los gastos registrados aparecerán listados aquí.",
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(gastosList) { gasto ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = gasto.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = Slate900
                                    )
                                    Surface(
                                        color = if (gasto.isActive) Color(0xFFDCFCE7) else Slate200,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (gasto.isActive) "ACTIVO" else "INACTIVO",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (gasto.isActive) Color(0xFF166534) else Slate600,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (gasto.description.isNotBlank()) {
                                    Text(
                                        text = gasto.description,
                                        fontSize = 13.sp,
                                        color = Slate600,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Categoría: ${gasto.category} • Período: ${gasto.period} (${gasto.periodDays}d)",
                                    fontSize = 12.sp,
                                    color = Slate500,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$${"%.2f".format(gasto.amount)} CUP",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = ElQadreNavy
                                )
                                IconButton(
                                    onClick = {
                                        gastoToEdit = gasto
                                        showAddGastoDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar", tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { gastoToDelete = gasto },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Rose600, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddGastoDialog) {
        AddEditGastoGeneralDialog(
            gasto = gastoToEdit,
            products = uiState.products,
            onDismiss = {
                showAddGastoDialog = false
                gastoToEdit = null
            },
            onConfirm = { savedGasto ->
                if (gastoToEdit == null) {
                    viewModel.insertGastoGeneral(savedGasto)
                } else {
                    viewModel.updateGastoGeneral(savedGasto)
                }
                showAddGastoDialog = false
                gastoToEdit = null
            }
        )
    }

    gastoToDelete?.let { gasto ->
        AlertDialog(
            onDismissRequest = { gastoToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Rose600, modifier = Modifier.size(40.dp)) },
            title = {
                Text(
                    text = "Confirmar Eliminación",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de que desea eliminar el gasto \"${gasto.name}\"? Esta acción no se puede deshacer.",
                    fontSize = 16.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteGastoGeneral(gasto)
                        gastoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("SÍ, ELIMINAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { gastoToDelete = null },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun AbrirJornadaDuenoDialog(
    currentUser: User?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var fondoInicialStr by remember { mutableStateOf("") }
    val fondoInicial = fondoInicialStr.toDoubleOrNull() ?: 0.0
    val isValid = fondoInicialStr.isBlank() || (fondoInicial != null && fondoInicial >= 0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy.copy(alpha = 0.1f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = "Apertura de Jornada",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Inicie una nueva jornada de trabajo en este dispositivo. No se permite más de una jornada abierta al mismo tiempo.",
                    fontSize = 13.sp,
                    color = Slate600,
                    lineHeight = 18.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Responsable: ${currentUser?.fullName ?: currentUser?.username ?: "Dueño"}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Slate500, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Fecha: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = fondoInicialStr,
                    onValueChange = { fondoInicialStr = it },
                    label = { Text("Fondo Inicial de Caja (CUP)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_fondo_inicial"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        cursorColor = ElQadreNavy
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fondoInicial) },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_confirm_abrir_jornada")
            ) {
                Text("Abrir Jornada", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancelar", fontWeight = FontWeight.Bold, color = Slate600, fontSize = 14.sp)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun CerrarJornadaDuenoDialog(
    jornada: Jornada,
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onConfirm: (finalCash: Double, notes: String) -> Unit
) {
    val jorOrders = uiState.allOrders.filter { it.jornadaId == jornada.id && it.status == "COBRADA" }
    val totalVentas = jorOrders.sumOf { it.totalAmount }
    val totalCashReceived = jorOrders.sumOf { (it.cashReceived - it.changeGiven).coerceAtLeast(0.0) }
    val expectedCash = jornada.initialCash + totalCashReceived

    var finalCashStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val finalCash = finalCashStr.toDoubleOrNull() ?: expectedCash
    val cashDifference = finalCash - expectedCash

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = "Cerrar Jornada #${jornada.id}",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Al cerrar la jornada, se consolidará el resultado económico real, se creará una instantánea inmutable y se generará el archivo Q_jornada.json.",
                    fontSize = 12.sp,
                    color = Slate600,
                    lineHeight = 17.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fondo Inicial:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(jornada.initialCash)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ventas Cobradas:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(totalVentas)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        HorizontalDivider(color = Slate200)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Efectivo Esperado:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text(
                                text = "$${"%.2f".format(expectedCash)} CUP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = finalCashStr,
                    onValueChange = { finalCashStr = it },
                    label = { Text("Efectivo Contado Real (Arqueo)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_arqueo_final"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        cursorColor = ElQadreNavy
                    )
                )

                val diffColor = if (cashDifference < 0.0) Color(0xFFDC2626) else if (cashDifference == 0.0) Slate600 else Color(0xFF047857)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (cashDifference < 0.0) Color(0xFFFEF2F2) else if (cashDifference > 0.0) Color(0xFFECFDF5) else Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Diferencia de Caja:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                        Text(
                            text = if (cashDifference >= 0.0) "+$${"%.2f".format(cashDifference)} CUP" else "$${"%.2f".format(cashDifference)} CUP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = diffColor
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones / Notas (Opcional)") },
                    placeholder = { Text("Detalles del cierre de turno...") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cierre_notes"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        cursorColor = ElQadreNavy
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(finalCash, notes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB45309),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("btn_confirm_cerrar_jornada")
            ) {
                Text("Confirmar Cierre & Snapshot", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cancelar", fontWeight = FontWeight.Bold, color = Slate600, fontSize = 14.sp)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun DetalleJornadaCerradaDialog(
    jornada: Jornada,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val openDateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(jornada.openedAt))
    val closeDateFormatted = jornada.closedAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) } ?: "En curso"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ElQadreNavy.copy(alpha = 0.1f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Assessment,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Text(
                    text = "Cierre Jornada #${jornada.id}",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Header Info
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Apertura:", fontSize = 12.sp, color = Slate600)
                            Text(openDateFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cierre:", fontSize = 12.sp, color = Slate600)
                            Text(closeDateFormatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Usuario:", fontSize = 12.sp, color = Slate600)
                            Text(jornada.openedBy, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dispositivo:", fontSize = 12.sp, color = Slate600)
                            Text(jornada.deviceId, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                    }
                }

                // Resultado Económico y Utilidad (Snapshot Inmutable)
                Text(
                    text = "RESULTADO ECONÓMICO Y UTILIDAD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    letterSpacing = 0.5.sp
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, ElQadreBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ingresos Reales:", fontSize = 13.sp, color = Slate600)
                            Text("$${"%.2f".format(jornada.totalIngresos)} CUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        if (jornada.realSalesProduccion > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("  • Producción:", fontSize = 12.sp, color = Slate500)
                                Text("$${"%.2f".format(jornada.realSalesProduccion)} CUP", fontSize = 12.sp, color = Slate700)
                            }
                        }
                        if (jornada.realSalesMercaderias > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("  • Mercaderías:", fontSize = 12.sp, color = Slate500)
                                Text("$${"%.2f".format(jornada.realSalesMercaderias)} CUP", fontSize = 12.sp, color = Slate700)
                            }
                        }

                        HorizontalDivider(color = Slate200)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Costos de Productos Vendidos:", fontSize = 12.sp, color = Slate600)
                            Text("- $${"%.2f".format(jornada.totalCostos)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Gastos Corrientes (Día):", fontSize = 12.sp, color = Slate600)
                            Text("- $${"%.2f".format(jornada.totalGastos)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Inversiones (Depreciación Día):", fontSize = 12.sp, color = Slate600)
                            Text("- $${"%.2f".format(jornada.totalInversiones)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }

                        HorizontalDivider(color = Slate200)

                        val isProfit = jornada.utilidadDelDia >= 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("UTILIDAD NETA DEL DÍA:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Text(
                                text = "$${"%.2f".format(jornada.utilidadDelDia)} CUP",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isProfit) Color(0xFF047857) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // Cuadre de Caja
                Text(
                    text = "ARQUEO Y CUADRE DE CAJA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy,
                    letterSpacing = 0.5.sp
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fondo Inicial:", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(jornada.initialCash)} CUP", fontSize = 12.sp, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Esperado:", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(jornada.expectedCash)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Efectivo Contado (Arqueo):", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(jornada.finalCash)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        val diffColor = if (jornada.cashDifference < 0.0) Color(0xFFDC2626) else if (jornada.cashDifference == 0.0) Slate700 else Color(0xFF047857)
                        HorizontalDivider(color = Slate200)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Diferencia:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = if (jornada.cashDifference >= 0.0) "+$${"%.2f".format(jornada.cashDifference)} CUP" else "$${"%.2f".format(jornada.cashDifference)} CUP",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = diffColor
                            )
                        }
                    }
                }

                if (jornada.notes.isNotBlank()) {
                    Text("Observaciones:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate600)
                    Text(jornada.notes, fontSize = 13.sp, color = Slate700)
                }

                // Copy JSON button if snapshotJson is present
                if (!jornada.snapshotJson.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Q_jornada.json", jornada.snapshotJson)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Q_jornada.json copiado al portapapeles.", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElQadreNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_copy_qjornada")
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar Q_jornada.json al Portapapeles", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Cerrar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        containerColor = Color.White
    )
}

private data class GestionModuleItem(
    val view: DuenoView,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DuenoAjustesView(
    uiState: MainUiState,
    viewModel: MainViewModel,
    isWide: Boolean,
    visibleModules: Set<String> = com.example.util.DuenoSessionPreferences.ALL_MODULES,
    onVisibleModulesChanged: (Set<String>) -> Unit = {},
    initialSection: String = "PREFERENCIAS",
    onNavigate: (DuenoView) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedAjustesSection by remember { mutableStateOf(initialSection) }

    // Configuración general y Divisas (exclusivamente Tasa de Cambio)
    var tasaUsdText by remember { mutableStateOf("") }
    var tasaEurText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.generalConfig) {
        uiState.generalConfig?.let { config ->
            tasaUsdText = if (config.tasaUsd > 0) config.tasaUsd.toString() else ""
            tasaEurText = if (config.tasaEur > 0) config.tasaEur.toString() else ""
        }
    }

    // State for Restore
    var showRestoreModal by remember { mutableStateOf(false) }
    var restoreJsonText by remember { mutableStateOf("") }
    var restoreSummary by remember { mutableStateOf<String?>(null) }
    var isRestoring by remember { mutableStateOf(false) }

    // State for Legacy Produccion Import
    var showImportLegacyProduccionModal by remember { mutableStateOf(false) }
    var legacyProduccionJsonText by remember { mutableStateOf("") }
    var legacyProduccionSummary by remember { mutableStateOf<String?>(null) }
    var isImportingLegacyProduccion by remember { mutableStateOf(false) }

    // File picker launcher for Legacy Produccion
    val legacyProduccionFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotBlank()) {
                    legacyProduccionJsonText = content
                    val summaryRes = viewModel.getLegacyProduccionBackupSummary(content)
                    if (summaryRes.isSuccess) {
                        legacyProduccionSummary = summaryRes.getOrNull()
                    } else {
                        legacyProduccionSummary = null
                        Toast.makeText(context, "Archivo inválido para Producción: ${summaryRes.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // File picker launcher for Restore
    val restoreFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (content.isNotBlank()) {
                    restoreJsonText = content
                    val summaryRes = viewModel.getDuenoBackupSummary(content)
                    if (summaryRes.isSuccess) {
                        restoreSummary = summaryRes.getOrNull()
                    } else {
                        restoreSummary = null
                        Toast.makeText(context, "Archivo de respaldo inválido: ${summaryRes.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // State for Archivo de Jornadas
    var selectedArchivoFilter by remember { mutableStateOf("JORNADA") } // "JORNADA", "SEMANA", "MES", "ANIO"
    var selectedJornadaForDetail by remember { mutableStateOf<Jornada?>(null) }
    var jornadaToDelete by remember { mutableStateOf<Jornada?>(null) }

    // Date filters for Week, Month, Year
    val now = remember { Calendar.getInstance() }
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var selectedWeekOfYear by remember { mutableIntStateOf(now.get(Calendar.WEEK_OF_YEAR)) }

    val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    // Filter archived jornadas (only closed/archived jornadas)
    val archivedJornadas = remember(uiState.allJornadas) {
        uiState.allJornadas.filter { !it.isOpen || it.closedAt != null }.sortedByDescending { it.closedAt ?: it.openedAt }
    }

    // Filtered list based on selected tab
    val displayedJornadas = remember(archivedJornadas, selectedArchivoFilter, selectedWeekOfYear, selectedMonth, selectedYear) {
        archivedJornadas.filter { j ->
            val timestamp = j.closedAt ?: j.openedAt
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            when (selectedArchivoFilter) {
                "JORNADA" -> true
                "SEMANA" -> {
                    cal.get(Calendar.YEAR) == selectedYear && cal.get(Calendar.WEEK_OF_YEAR) == selectedWeekOfYear
                }
                "MES" -> {
                    cal.get(Calendar.YEAR) == selectedYear && cal.get(Calendar.MONTH) == selectedMonth
                }
                "ANIO" -> {
                    cal.get(Calendar.YEAR) == selectedYear
                }
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DuenoSubscreenHeader(
            title = "Ajustes del Negocio",
            subtitle = "Tasas de cambio, contraseñas, respaldos y archivo de jornadas",
            icon = Icons.Outlined.Settings,
            onBack = onBack
        )

        // Banners/Feedback Messages
        if (uiState.successMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Emerald50),
                border = BorderStroke(1.dp, Emerald600),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Éxito", tint = Emerald600)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.successMessage,
                        color = Emerald600,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Emerald600, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // DOS ENTRADAS PRINCIPALES INDEPENDIENTES: PREFERENCIAS Y GESTIÓN
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AjustesHubCard(
                title = "PREFERENCIAS",
                subtitle = "Tasa de cambio, seguridad, respaldos y archivo",
                icon = Icons.Outlined.Settings,
                isSelected = selectedAjustesSection == "PREFERENCIAS",
                modifier = Modifier.weight(1f).testTag("card_ajustes_preferencias"),
                onClick = { selectedAjustesSection = "PREFERENCIAS" }
            )

            AjustesHubCard(
                title = "GESTIÓN",
                subtitle = "Módulos operativos y administración",
                icon = Icons.Outlined.Tune,
                isSelected = selectedAjustesSection == "GESTION",
                modifier = Modifier.weight(1f).testTag("card_ajustes_gestion"),
                onClick = { selectedAjustesSection = "GESTION" }
            )
        }

        if (selectedAjustesSection == "PREFERENCIAS") {
            // -------------------------------------------------------------
            // SECCIÓN ACTUALIZACIÓN DE APK Y VERSIÓN DE LA APLICACIÓN
            // -------------------------------------------------------------
            com.example.ui.components.AppVersionSettingsCard()

        // -------------------------------------------------------------
        // SECCIÓN: CONFIGURACIÓN GENERAL Y DIVISAS (TASA DE CAMBIO)
        // -------------------------------------------------------------
        val currentOwnerUsername = uiState.currentUser?.username ?: "dueno"

        SettingsSectionCard(
            title = "CONFIGURACIÓN GENERAL Y DIVISAS",
            icon = Icons.Outlined.AttachMoney
        ) {
            Text(
                text = "Configure las tasas de cambio de referencia para USD y EUR.",
                fontSize = 12.sp,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tasas de cambio
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tasaUsdText,
                    onValueChange = { tasaUsdText = it },
                    label = { Text("Tasa USD (CUP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("input_tasa_usd"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        cursorColor = ElQadreNavy
                    )
                )

                OutlinedTextField(
                    value = tasaEurText,
                    onValueChange = { tasaEurText = it },
                    label = { Text("Tasa EUR (CUP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).testTag("input_tasa_eur"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        cursorColor = ElQadreNavy
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val baseConfig = uiState.generalConfig ?: ConfiguracionGeneral()
                    viewModel.updateGeneralConfig(
                        baseConfig.copy(
                            tasaUsd = tasaUsdText.toDoubleOrNull() ?: 0.0,
                            tasaEur = tasaEurText.toDoubleOrNull() ?: 0.0
                        )
                    )
                    Toast.makeText(context, "Tasa de cambio guardada correctamente.", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_guardar_config_general"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GUARDAR TASA DE CAMBIO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // -------------------------------------------------------------
        // SECTION: CAMBIO DE CONTRASEÑA DEL DUEÑO
        // -------------------------------------------------------------
        SettingsSectionCard(
            title = "CAMBIO DE CONTRASEÑA DEL DUEÑO",
            icon = Icons.Outlined.Lock
        ) {
            Text(
                text = "Modifique localmente la contraseña de acceso del usuario Dueño. Este cambio es estrictamente para la autenticación local y no altera la autorización comercial del dispositivo.",
                fontSize = 12.sp,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(12.dp))

            var nuevaContrasena by remember { mutableStateOf("") }
            var confirmarContrasena by remember { mutableStateOf("") }
            var contrasenaVisible by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = nuevaContrasena,
                onValueChange = { nuevaContrasena = it },
                label = { Text("Nueva Contraseña") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { contrasenaVisible = !contrasenaVisible }) {
                        Icon(
                            if (contrasenaVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (contrasenaVisible) "Ocultar" else "Mostrar"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("input_nueva_contrasena_dueno"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElQadreNavy,
                    unfocusedBorderColor = ElQadreBorder,
                    cursorColor = ElQadreNavy
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = confirmarContrasena,
                onValueChange = { confirmarContrasena = it },
                label = { Text("Confirmar Nueva Contraseña") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (contrasenaVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("input_confirmar_contrasena_dueno"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElQadreNavy,
                    unfocusedBorderColor = ElQadreBorder,
                    cursorColor = ElQadreNavy
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (nuevaContrasena.isBlank()) {
                        Toast.makeText(context, "La nueva contraseña no puede estar vacía.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (nuevaContrasena != confirmarContrasena) {
                        Toast.makeText(context, "Las contraseñas no coinciden.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val currentUsername = uiState.currentUser?.username ?: currentOwnerUsername
                    viewModel.updateDuenoPasswordLocally(currentUsername, nuevaContrasena)
                    nuevaContrasena = ""
                    confirmarContrasena = ""
                    Toast.makeText(context, "Contraseña del Dueño actualizada correctamente.", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_cambiar_contrasena_dueno"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ACTUALIZAR CONTRASEÑA LOCAL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // -------------------------------------------------------------
        // SECTION 4: COPIAS DE SEGURIDAD LOCALES (RESPALDAR & RESTAURAR)
        // -------------------------------------------------------------
        SettingsSectionCard(
            title = "COPIAS DE SEGURIDAD LOCALES",
            icon = Icons.Outlined.CloudSync
        ) {
            Text(
                text = "Administre el respaldo y restauración integral de la base de datos local del Dueño.",
                fontSize = 12.sp,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        BusinessBackupManager.exportAndShareBackup(context, uiState)
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("btn_respaldar_dueno"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESPALDAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        restoreJsonText = ""
                        restoreSummary = null
                        showRestoreModal = true
                    },
                    modifier = Modifier.weight(1f).height(50.dp).testTag("btn_restaurar_dueno"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E), contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTAURAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ElQadreBorder, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Recuperar exclusivamente Datos de Producción (Insumos, Productos, Recetas, Categorías) desde JSON antiguos de ElQadre. No afecta licencias, contraseñas, jornadas ni usuarios.",
                fontSize = 11.sp,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    legacyProduccionJsonText = ""
                    legacyProduccionSummary = null
                    showImportLegacyProduccionModal = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("btn_importar_legacy_produccion"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("IMPORTAR PRODUCCIÓN (JSON ANTIGUO)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // -------------------------------------------------------------
        // SECTION 4: ARCHIVO DE JORNADAS
        // -------------------------------------------------------------
        SettingsSectionCard(
            title = "ARCHIVO DE JORNADAS",
            icon = Icons.Outlined.History
        ) {
            Text(
                text = "Historial de jornadas archivadas desde Control del Negocio. Los datos corresponden al momento exacto de cierre y se conservan de forma inmutable.",
                fontSize = 12.sp,
                color = Slate600
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Filter Tabs: Jornada, Semana, Mes, Año
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf(
                    "JORNADA" to "Jornada",
                    "SEMANA" to "Semana",
                    "MES" to "Mes",
                    "ANIO" to "Año"
                )
                options.forEach { (type, label) ->
                    val isSelected = selectedArchivoFilter == type
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedArchivoFilter = type },
                        label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElQadreNavy,
                            selectedLabelColor = Color.White,
                            containerColor = Slate100,
                            labelColor = Slate700
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Temporal Navigators for Semana, Mes, Año
            when (selectedArchivoFilter) {
                "SEMANA" -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, ElQadreBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedWeekOfYear > 1) {
                                        selectedWeekOfYear--
                                    } else {
                                        selectedWeekOfYear = 52
                                        selectedYear--
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Semana anterior", tint = ElQadreNavy)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.DateRange, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                Text("Semana $selectedWeekOfYear - Año $selectedYear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            IconButton(
                                onClick = {
                                    if (selectedWeekOfYear < 52) {
                                        selectedWeekOfYear++
                                    } else {
                                        selectedWeekOfYear = 1
                                        selectedYear++
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Semana siguiente", tint = ElQadreNavy)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                "MES" -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, ElQadreBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (selectedMonth > 0) {
                                        selectedMonth--
                                    } else {
                                        selectedMonth = 11
                                        selectedYear--
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior", tint = ElQadreNavy)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                Text("${monthNames[selectedMonth]} $selectedYear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            IconButton(
                                onClick = {
                                    if (selectedMonth < 11) {
                                        selectedMonth++
                                    } else {
                                        selectedMonth = 0
                                        selectedYear++
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", tint = ElQadreNavy)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                "ANIO" -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, ElQadreBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedYear-- }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Año anterior", tint = ElQadreNavy)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                                Text("Año $selectedYear", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            }

                            IconButton(onClick = { selectedYear++ }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Año siguiente", tint = ElQadreNavy)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Summary Totals Card if there are items
            if (displayedJornadas.isNotEmpty() && selectedArchivoFilter != "JORNADA") {
                val totalIng = displayedJornadas.sumOf { it.totalIngresos }
                val totalCos = displayedJornadas.sumOf { it.totalCostos }
                val totalGas = displayedJornadas.sumOf { it.totalGastos }
                val totalInv = displayedJornadas.sumOf { it.totalInversiones }
                val totalUtil = displayedJornadas.sumOf { it.utilidadDelDia }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, ElQadreBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Jornadas en período:", fontSize = 12.sp, color = Slate600)
                            Text("${displayedJornadas.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ingresos Totales:", fontSize = 12.sp, color = Slate600)
                            Text("$${"%.2f".format(totalIng)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Costos + Gastos + Inversiones:", fontSize = 12.sp, color = Slate600)
                            Text("- $${"%.2f".format(totalCos + totalGas + totalInv)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                        HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("UTILIDAD NETA TOTAL:", fontSize = 13.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                            Text(
                                text = "$${"%.2f".format(totalUtil)} CUP",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = if (totalUtil >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // List of Archived Jornadas
            if (displayedJornadas.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Outlined.HistoryEdu, contentDescription = null, tint = Slate400, modifier = Modifier.size(36.dp))
                        Text(
                            text = "No se encontraron jornadas archivadas para esta selección.",
                            fontSize = 12.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayedJornadas.forEach { j ->
                        val dateFormatted = j.closedAt?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) }
                            ?: SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(j.openedAt))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, ElQadreBorder),
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
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            color = ElQadreNavy.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Jornada #${j.id}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ElQadreNavy
                                            )
                                        }
                                        Text(
                                            text = dateFormatted,
                                            fontSize = 12.sp,
                                            color = Slate500
                                        )
                                    }

                                    Text(
                                        text = "$${"%.2f".format(j.utilidadDelDia)} CUP",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (j.utilidadDelDia >= 0) Color(0xFF047857) else Color(0xFFDC2626)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ingresos: $${"%.2f".format(j.totalIngresos)}", fontSize = 12.sp, color = Slate600)
                                    Text("Costos/Gastos: $${"%.2f".format(j.totalCostos + j.totalGastos + j.totalInversiones)}", fontSize = 12.sp, color = Slate600)
                                    Text("Caja: $${"%.2f".format(j.finalCash)}", fontSize = 12.sp, color = Slate600)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { jornadaToDelete = j },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp).testTag("btn_delete_jornada_${j.id}")
                                    ) {
                                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ELIMINAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    }

                                    TextButton(
                                        onClick = { selectedJornadaForDetail = j },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreNavy)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("VER DETALLE COMPLETO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
            // -------------------------------------------------------------
            // SECCIÓN: GESTIÓN OPERATIVA
            // Punto de entrada a los módulos operativos del negocio
            // -------------------------------------------------------------
            SettingsSectionCard(
                title = "MÓDULOS DE GESTIÓN OPERATIVA",
                icon = Icons.Outlined.Tune
            ) {
                Text(
                    text = "Punto de entrada a los módulos operativos del negocio.",
                    fontSize = 12.sp,
                    color = Slate600
                )
                Spacer(modifier = Modifier.height(14.dp))

                val gestionModules = listOf(
                    GestionModuleItem(
                        view = DuenoView.INVENTARIO,
                        title = "Inventario",
                        subtitle = "Producción, Mercaderías y Control de Stock",
                        icon = Icons.Outlined.Inventory2,
                        tag = "btn_nav_gestion_inventario"
                    ),
                    GestionModuleItem(
                        view = DuenoView.INVERSIONES,
                        title = "Inversiones",
                        subtitle = "Control de inversiones y activos del negocio",
                        icon = Icons.Outlined.AttachMoney,
                        tag = "btn_nav_gestion_inversiones"
                    ),
                    GestionModuleItem(
                        view = DuenoView.CATALOGO,
                        title = "Catálogo",
                        subtitle = "Productos, categorías y precios de venta",
                        icon = Icons.Outlined.MenuBook,
                        tag = "btn_nav_gestion_catalogo"
                    ),
                    GestionModuleItem(
                        view = DuenoView.GASTOS,
                        title = "Gastos",
                        subtitle = "Registro de gastos y costos de operación",
                        icon = Icons.Outlined.ReceiptLong,
                        tag = "btn_nav_gestion_gastos"
                    ),
                    GestionModuleItem(
                        view = DuenoView.PERSONAL,
                        title = "Personal",
                        subtitle = "Trabajadores, nómina y gestión de usuarios",
                        icon = Icons.Outlined.Group,
                        tag = "btn_nav_gestion_personal"
                    ),
                    GestionModuleItem(
                        view = DuenoView.CONTROL_NEGOCIO,
                        title = "Control del Negocio",
                        subtitle = "Ventas, balances, estadísticas y arqueo",
                        icon = Icons.Outlined.Analytics,
                        tag = "btn_nav_gestion_control_negocio"
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    gestionModules.forEach { mod ->
                        Card(
                            onClick = { onNavigate(mod.view) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Slate50),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(mod.tag)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = ElQadreNavy.copy(alpha = 0.08f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = mod.icon,
                                            contentDescription = null,
                                            tint = ElQadreNavy,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mod.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = ElQadreNavy
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mod.subtitle,
                                        fontSize = 12.sp,
                                        color = Slate500,
                                        lineHeight = 16.sp
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Acceder a ${mod.title}",
                                    tint = Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Modal for Eliminación Individual de Jornada
    if (jornadaToDelete != null) {
        val targetJornada = jornadaToDelete!!
        AlertDialog(
            onDismissRequest = { jornadaToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Color(0xFFDC2626))
                    Text("¿Eliminar Jornada #${targetJornada.id}?", fontWeight = FontWeight.Bold, color = Slate900)
                }
            },
            text = {
                Text(
                    "Esta acción eliminará únicamente esta jornada archivada (#${targetJornada.id}) del registro histórico. Las demás jornadas no serán afectadas ni eliminadas.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteJornada(targetJornada.id) {
                            Toast.makeText(context, "Jornada #${targetJornada.id} eliminada.", Toast.LENGTH_SHORT).show()
                        }
                        jornadaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("btn_confirm_delete_jornada")
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { jornadaToDelete = null }) {
                    Text("Cancelar", color = Slate600)
                }
            }
        )
    }

    // Modal for Detalle de Jornada Archivada
    if (selectedJornadaForDetail != null) {
        DetalleJornadaCerradaDialog(
            jornada = selectedJornadaForDetail!!,
            onDismiss = { selectedJornadaForDetail = null }
        )
    }

    // Modal for RESTAURAR
    if (showRestoreModal) {
        AlertDialog(
            onDismissRequest = { showRestoreModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = ElQadreNavy)
                    Text("Restaurar Respaldo de Datos", fontWeight = FontWeight.Black, color = ElQadreNavy, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (restoreSummary == null) {
                        Text(
                            "Seleccione el archivo .json de respaldo o pegue su contenido para restaurar el sistema sin datos duplicados.",
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        Button(
                            onClick = {
                                restoreFilePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_pick_restore_file")
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SELECCIONAR ARCHIVO (.JSON)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text("O pegue el contenido JSON:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)

                        OutlinedTextField(
                            value = restoreJsonText,
                            onValueChange = { restoreJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("restore_paste_field"),
                            placeholder = { Text("Pegue el JSON de respaldo aquí...") },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                cursorColor = ElQadreNavy
                            )
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate100),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("RESUMEN DEL RESPALDO A RESTAURAR:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(restoreSummary!!, fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "⚠️ ATENCIÓN:\nAl proceder, la base de datos local se restaurará con la información del respaldo sin duplicar registros.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                if (restoreSummary == null) {
                    Button(
                        onClick = {
                            if (restoreJsonText.isBlank()) {
                                Toast.makeText(context, "Seleccione un archivo o pegue el contenido JSON.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val res = viewModel.getDuenoBackupSummary(restoreJsonText)
                            if (res.isSuccess) {
                                restoreSummary = res.getOrNull()
                            } else {
                                Toast.makeText(context, "Respaldo inválido: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.testTag("btn_validar_backup_json")
                    ) {
                        Text("VALIDAR RESPALDO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            isRestoring = true
                            viewModel.restoreDuenoBackupJson(restoreJsonText) { success, msg ->
                                isRestoring = false
                                if (success) {
                                    showRestoreModal = false
                                    Toast.makeText(context, "Datos restaurados con éxito.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Error al restaurar: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.testTag("btn_confirmar_restaurar")
                    ) {
                        Text("CONFIRMAR Y RESTAURAR", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreModal = false }) {
                    Text("Cancelar", fontWeight = FontWeight.Bold, color = Slate600)
                }
            },
            containerColor = Color.White
        )
    }

    // Modal for Importar Producción (JSON Antiguo)
    if (showImportLegacyProduccionModal) {
        AlertDialog(
            onDismissRequest = { showImportLegacyProduccionModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color(0xFFD97706))
                    Text("Importar Producción (JSON Antiguo)", fontWeight = FontWeight.Black, color = Color(0xFFD97706), fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (legacyProduccionSummary == null) {
                        Text(
                            "Seleccione un archivo de respaldo .json antiguo o pegue su contenido para recuperar selectivamente la producción (Insumos, Productos y Recetas).",
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        Button(
                            onClick = {
                                legacyProduccionFilePicker.launch(arrayOf("application/json", "text/*", "*/*"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("btn_pick_legacy_file")
                        ) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SELECCIONAR ARCHIVO (.JSON)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text("O pegue el contenido JSON:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)

                        OutlinedTextField(
                            value = legacyProduccionJsonText,
                            onValueChange = { legacyProduccionJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("legacy_paste_field"),
                            placeholder = { Text("Pegue el JSON antiguo aquí...") },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD97706),
                                cursorColor = Color(0xFFD97706)
                            )
                        )
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Slate100),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("RESUMEN DE PRODUCCIÓN DETECTADO:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                Text(legacyProduccionSummary!!, fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            "💡 INFORMACIÓN:\nEsta operación es selectiva (UPsert). Los datos existentes no se eliminarán. Los insumos/productos coincidentes se actualizarán de forma segura.",
                            fontSize = 12.sp,
                            color = Color(0xFF0F766E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                if (legacyProduccionSummary == null) {
                    Button(
                        onClick = {
                            if (legacyProduccionJsonText.isBlank()) {
                                Toast.makeText(context, "Seleccione un archivo o pegue el contenido JSON.", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val res = viewModel.getLegacyProduccionBackupSummary(legacyProduccionJsonText)
                            if (res.isSuccess) {
                                legacyProduccionSummary = res.getOrNull()
                            } else {
                                Toast.makeText(context, "JSON inválido o incompatible con Producción: ${res.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.testTag("btn_validar_legacy_json")
                    ) {
                        Text("VALIDAR DATOS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            isImportingLegacyProduccion = true
                            viewModel.importLegacyProduccionBackupJson(legacyProduccionJsonText) { success, msg ->
                                isImportingLegacyProduccion = false
                                if (success) {
                                    showImportLegacyProduccionModal = false
                                    Toast.makeText(context, "Producción importada correctamente.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Error al importar: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        modifier = Modifier.testTag("btn_confirmar_importar_legacy")
                    ) {
                        Text("CONFIRMAR E IMPORTAR", fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportLegacyProduccionModal = false }) {
                    Text("Cancelar", fontWeight = FontWeight.Bold, color = Slate600)
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
private fun AjustesHubCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElQadreNavy else Color.White
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) ElQadreGold else Slate200
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) ElQadreGold.copy(alpha = 0.2f) else Slate100,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) ElQadreGold else ElQadreNavy,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElQadreGold
                    ) {
                        Text(
                            text = "ACTIVO",
                            color = ElQadreNavy,
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isSelected) Color.White else ElQadreNavy
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else Slate500,
                    lineHeight = 15.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SalidaParaVentaAgregadoDialog(
    materiaPrima: MateriaPrima,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var racionesText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    val racionesVal = racionesText.trim().toDoubleOrNull() ?: 0.0
    val cantidadFisicaCalc = if (materiaPrima.rationQuantity > 0.0) racionesVal * materiaPrima.rationQuantity else racionesVal

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "SALIDA PARA VENTA",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color(0xFF15803D)
                )
                Text(
                    text = materiaPrima.name.uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = ElQadreNavy
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF0FDF4),
                    border = BorderStroke(1.5.dp, Color(0xFFBBF7D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("• Stock en Almacén: ${"%.1f".format(materiaPrima.stock)} ${materiaPrima.unit}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        Text("• Raciones en Almacén: ${"%.1f".format(materiaPrima.racionesDisponibles)} raciones (${materiaPrima.rationQuantity} ${materiaPrima.rationUnit}/ración)", fontSize = 13.sp, color = Slate700)
                        Text("• Precio de Venta Ración: $${"%.2f".format(materiaPrima.precioEfectivoVenta)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "CANTIDAD DE RACIONES (*)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ElQadreNavy
                    )
                    OutlinedTextField(
                        value = racionesText,
                        onValueChange = { 
                            racionesText = it
                            showError = false
                        },
                        placeholder = { Text("Ej. 10", fontSize = 15.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        isError = showError,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF16A34A),
                            focusedLabelColor = Color(0xFF16A34A)
                        ),
                        modifier = Modifier.fillMaxWidth().height(60.dp).testTag("input_salida_agregado_cant")
                    )
                }

                if (racionesVal > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Equivalente en Almacén:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                            Text("${"%.1f".format(cantidadFisicaCalc)} ${materiaPrima.unit}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E40AF))
                        }
                    }
                }

                if (showError) {
                    Text(
                        text = "Ingrese una cantidad válida mayor a 0 y no superior a las raciones disponibles (${"%.1f".format(materiaPrima.racionesDisponibles)} raciones)",
                        color = Color(0xFFDC2626),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Nota: Esta acción descuenta de Almacén la cantidad equivalente a las raciones enviadas y la pone disponible en el área de Venta. No representa un cobro en dinero.",
                    fontSize = 12.sp,
                    color = Slate500,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (racionesVal <= 0.0 || racionesVal > materiaPrima.racionesDisponibles) {
                        showError = true
                    } else {
                        onConfirm(cantidadFisicaCalc)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(52.dp).testTag("btn_confirm_salida_agregado")
            ) {
                Text("REGISTRAR SALIDA", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(52.dp)
            ) {
                Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    )
}


