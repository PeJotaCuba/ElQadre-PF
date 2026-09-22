package com.example.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.ui.components.AppVersionSettingsCard
import com.example.ui.components.ContactPickerIconButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.AccountProvisioningHelper
import com.example.util.toSha256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<User?>(null) }
    var selectedUserForDelete by remember { mutableStateOf<User?>(null) }
    var showAdminPasswordDialog by remember { mutableStateOf(false) }
    
    // State for SMS Delivery Dialog
    var generatedSmsData by remember { mutableStateOf<GeneratedSmsInfo?>(null) }

    val businessName = uiState.businessConfig?.nombreNegocio?.ifBlank { uiState.businessName } ?: uiState.businessName
    val businessCode = uiState.businessConfig?.codigoNegocio?.ifBlank { "001" } ?: "001"

    val createUsersBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonString = viewModel.generateUsersBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Respaldo de usuarios guardado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al respaldar usuarios: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val restoreUsersLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    viewModel.importUsersFromJsonContent(jsonContent)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al restaurar usuarios: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val shareApk: (Context) -> Unit = { ctx ->
        try {
            val appInfo = ctx.packageManager.getApplicationInfo(ctx.packageName, 0)
            val sourceApk = File(appInfo.sourceDir)
            if (sourceApk.exists()) {
                val apkDir = File(ctx.cacheDir, "apks")
                if (!apkDir.exists()) apkDir.mkdirs()
                val targetApk = File(apkDir, "ElQadrePF.apk")
                
                // Copiar el APK base al directorio de caché para que FileProvider lo sirva sin restricciones de sandbox de sistema
                sourceApk.copyTo(targetApk, overwrite = true)
                
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", targetApk)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, "ElQadrePF.apk")
                    putExtra(Intent.EXTRA_SUBJECT, "ElQadrePF Instalador APK")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Compartir APK instalada").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(chooser)
            } else {
                Toast.makeText(ctx, "No se encontró el archivo APK instalado.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(ctx, "Error al compartir APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Surface(
                color = ElQadreNavy,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = businessName.ifBlank { "ElQadre" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ElQadreGold.copy(alpha = 0.25f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "ADMINISTRADOR",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreGold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Creación y Gestión de Usuarios",
                                fontSize = 12.sp,
                                color = Slate300
                            )
                        }
                    }

                    // Botón grande y visible de Cerrar Sesión para probar cuentas
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("admin_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Salir / Probar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tarjeta de Bienvenida y Acción Principal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Slate200),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(ElQadreNavy.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ManageAccounts,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Panel de Usuarios del Negocio",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Crea cuentas, genera el SMS de entrega y pruébalas aquí.",
                                fontSize = 13.sp,
                                color = Slate600
                            )
                        }
                    }

                    // Botón Grande para Crear Usuario
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("admin_create_user_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "+ Crear Nuevo Usuario",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Resumen de Roles Creados
            val usersList = uiState.users
            val duenosCount = usersList.count { it.role == UserRole.DUENO }
            val dependientesCount = usersList.count { it.role == UserRole.DEPENDIENTE || it.role == UserRole.SALON || it.role == UserRole.BARRA }
            val cajerosCount = usersList.count { it.role == UserRole.CAJERO }
            val cocinaCount = usersList.count { it.role == UserRole.COCINA }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RoleSummaryChip("DUEÑO", duenosCount, Color(0xFFF59E0B), Modifier.weight(1f))
                RoleSummaryChip("DEPENDIENTE", dependientesCount, Color(0xFF3B82F6), Modifier.weight(1f))
                RoleSummaryChip("CAJERO", cajerosCount, Color(0xFF10B981), Modifier.weight(1f))
                RoleSummaryChip("COCINA", cocinaCount, Color(0xFFF97316), Modifier.weight(1f))
            }

            // Título de la Lista de Usuarios
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Usuarios Registrados (${usersList.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )
                Text(
                    text = "Toca para preparar SMS o editar",
                    fontSize = 12.sp,
                    color = Slate500
                )
            }

            // Lista de Usuarios
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(usersList, key = { it.username }) { user ->
                    AdminUserCard(
                        user = user,
                        businessName = businessName,
                        businessCode = businessCode,
                        onPrepareSms = { plainPassword ->
                            val roleName = AccountProvisioningHelper.mapRoleToProtocolString(user.role)
                            val smsText = AccountProvisioningHelper.buildAccountDeliverySms(
                                numeroNegocio = businessCode.ifBlank { "001" },
                                nombre = user.fullName,
                                usuario = user.username,
                                contrasenaInicial = plainPassword.ifBlank { "1234" },
                                rol = roleName
                            )
                            generatedSmsData = GeneratedSmsInfo(
                                user = user,
                                recipientPhone = user.telefono,
                                smsText = smsText
                            )
                        },
                        onEdit = { selectedUserForEdit = user },
                        onDelete = { selectedUserForDelete = user },
                        onTestAccount = { viewModel.testAccount(user) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Slate200),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFEDE9FE), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LockReset,
                                        contentDescription = null,
                                        tint = Color(0xFF6D28D9),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Seguridad del Administrador",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "Usuario: @admin • Acceso local directo sin SMS",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { showAdminPasswordDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("admin_change_password_btn"),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF6D28D9)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF6D28D9)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "CAMBIAR CONTRASEÑA DE ADMINISTRADOR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val defaultFileName = "Q_${businessCode}_usuarios_backup.json"
                                        createUsersBackupLauncher.launch(defaultFileName)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Emerald600),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald700),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "RESPALDAR USUARIOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { restoreUsersLauncher.launch("application/json") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, ElQadreNavy),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "RESTAURAR USUARIOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { shareApk(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0284C7))
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "COMPARTIR APK INSTALADA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    AppVersionSettingsCard(showGenerateJson = true)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    // Modal Crear / Editar Usuario
    if (showCreateDialog || selectedUserForEdit != null) {
        val editingUser = selectedUserForEdit
        AdminUserFormDialog(
            userToEdit = editingUser,
            businessName = businessName,
            businessCode = businessCode,
            onDismiss = {
                showCreateDialog = false
                selectedUserForEdit = null
            },
            onSaveAndGenerateSms = { user, plainPassword ->
                viewModel.saveUserByAdmin(user) { savedUser ->
                    if (savedUser.username != "admin") {
                        val roleName = AccountProvisioningHelper.mapRoleToProtocolString(savedUser.role)
                        val smsText = AccountProvisioningHelper.buildAccountDeliverySms(
                            numeroNegocio = businessCode.ifBlank { "001" },
                            nombre = savedUser.fullName,
                            usuario = savedUser.username,
                            contrasenaInicial = plainPassword.ifBlank { "1234" },
                            rol = roleName
                        )
                        generatedSmsData = GeneratedSmsInfo(
                            user = savedUser,
                            recipientPhone = savedUser.telefono,
                            smsText = smsText
                        )
                    } else {
                        Toast.makeText(context, "Cuenta de Administrador actualizada localmente.", Toast.LENGTH_SHORT).show()
                    }
                }
                showCreateDialog = false
                selectedUserForEdit = null
            }
        )
    }

    // Modal Cambiar Contraseña de Administrador
    if (showAdminPasswordDialog) {
        AdminPasswordChangeDialog(
            onDismiss = { showAdminPasswordDialog = false },
            onSavePassword = { newPassword ->
                val currentAdmin = uiState.users.firstOrNull { it.username.equals("admin", ignoreCase = true) }
                    ?: User(
                        username = "admin",
                        fullName = "Administrador Principal",
                        passwordHash = "admin26".toSha256(),
                        role = UserRole.ADMIN,
                        montoPorProducto = 0.0
                    )
                val updatedAdmin = currentAdmin.copy(
                    passwordHash = newPassword.toSha256()
                )
                viewModel.saveUserByAdmin(updatedAdmin) {
                    Toast.makeText(context, "Contraseña de Administrador guardada correctamente.", Toast.LENGTH_LONG).show()
                }
                showAdminPasswordDialog = false
            }
        )
    }

    // Modal SMS Generado
    generatedSmsData?.let { smsInfo ->
        AdminSmsPreviewDialog(
            smsInfo = smsInfo,
            onDismiss = { generatedSmsData = null },
            onLogoutToTest = {
                generatedSmsData = null
                onLogout()
            }
        )
    }

    // Diálogo de Confirmación de Eliminación
    selectedUserForDelete?.let { userToDelete ->
        AlertDialog(
            onDismissRequest = { selectedUserForDelete = null },
            title = {
                Text(
                    text = "Eliminar Usuario",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626)
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de eliminar al usuario ${userToDelete.fullName} (@${userToDelete.username})? Esta acción no se puede deshacer.",
                    fontSize = 15.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (userToDelete.username != "admin") {
                            viewModel.deleteUser(userToDelete.username)
                        } else {
                            Toast.makeText(context, "No se puede eliminar la cuenta de Administrador principal", Toast.LENGTH_SHORT).show()
                        }
                        selectedUserForDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUserForDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// Data class para transportar información del SMS generado
data class GeneratedSmsInfo(
    val user: User,
    val recipientPhone: String,
    val smsText: String
)

@Composable
fun RoleSummaryChip(
    roleLabel: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = roleLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate700,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AdminUserCard(
    user: User,
    businessName: String,
    businessCode: String,
    onPrepareSms: (plainPassword: String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestAccount: () -> Unit
) {
    val (roleBg, roleFg, roleIcon) = when (user.role) {
        UserRole.ADMIN -> Triple(Color(0xFFEDE9FE), Color(0xFF6D28D9), Icons.Filled.AdminPanelSettings)
        UserRole.DUENO -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), Icons.Filled.Shield)
        UserRole.DEPENDIENTE, UserRole.SALON, UserRole.BARRA -> Triple(Color(0xFFDBEAFE), Color(0xFF1D4ED8), Icons.Filled.Badge)
        UserRole.CAJERO -> Triple(Color(0xFFD1FAE5), Color(0xFF047857), Icons.Filled.PointOfSale)
        UserRole.COCINA -> Triple(Color(0xFFFFEDD5), Color(0xFFC2410C), Icons.Filled.SoupKitchen)
    }

    var showPasswordPromptForSms by remember { mutableStateOf(false) }
    var inputPasswordForSms by remember { mutableStateOf("1234") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Slate200),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(roleBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = roleIcon,
                            contentDescription = null,
                            tint = roleFg,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Text(
                            text = user.fullName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${user.username}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate600
                            )
                            if (user.telefono.isNotBlank()) {
                                Text(
                                    text = " • 📱 ${user.telefono}",
                                    fontSize = 13.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }

                // Rol Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = roleBg,
                    border = BorderStroke(1.dp, roleFg.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = user.role.displayName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = roleFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Datos adicionales (Comisión, permisos)
            if (user.role == UserRole.DEPENDIENTE || user.role == UserRole.SALON || user.role == UserRole.BARRA) {
                if (user.montoPorProducto > 0) {
                    Text(
                        text = "💵 Comisión por producto: $${"%.2f".format(user.montoPorProducto)} CUP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Emerald700
                    )
                }
            } else if (user.role == UserRole.DUENO) {
                val perms = mutableListOf<String>()
                perms.add("Control Negocio")
                if (user.permisoProduccion) perms.add("Producción")
                if (user.permisoMercancias) perms.add("Mercaderías")
                if (user.permisoPersonal) perms.add("Personal")
                Text(
                    text = "🛡️ Permisos: ${perms.joinToString(", ")}",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }

            Divider(color = Slate100, thickness = 1.dp)

            // Botones de acción grandes y cómodos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (user.username == "admin") {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEDE9FE),
                        border = BorderStroke(1.dp, Color(0xFFDDD6FE))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = Color(0xFF6D28D9),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Acceso Directo Local",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6D28D9)
                            )
                        }
                    }
                } else {
                    // Botón Generar/Preparar SMS
                    Button(
                        onClick = { showPasswordPromptForSms = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preparar SMS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Botón Probar Cuenta
                OutlinedButton(
                    onClick = onTestAccount,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ElQadreNavy),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ElQadreNavy
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "PROBAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Botón Editar
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Slate300),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Editar",
                        tint = Slate700,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Editar", fontSize = 13.sp, color = Slate700)
                }

                // Botón Eliminar (no permitido para admin root)
                if (user.username != "admin") {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal para confirmar contraseña antes de generar SMS
    if (showPasswordPromptForSms) {
        AlertDialog(
            onDismissRequest = { showPasswordPromptForSms = false },
            title = {
                Text(
                    text = "Contraseña para el SMS",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ingresa la contraseña inicial que se incluirá en el SMS de alta para ${user.fullName}:",
                        fontSize = 14.sp,
                        color = Slate700
                    )
                    OutlinedTextField(
                        value = inputPasswordForSms,
                        onValueChange = { inputPasswordForSms = it },
                        label = { Text("Contraseña inicial") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPasswordPromptForSms = false
                        onPrepareSms(inputPasswordForSms)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Generar SMS", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordPromptForSms = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/**
 * Formulario para crear o editar usuarios.
 * Diseñado con campos grandes, botones grandes, tipografía espaciosa y estructura clara:
 * datos de la persona → credenciales → rol → permisos/datos correspondientes → guardar → generar/preparar SMS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserFormDialog(
    userToEdit: User?,
    businessName: String,
    businessCode: String,
    onDismiss: () -> Unit,
    onSaveAndGenerateSms: (User, plainPassword: String) -> Unit
) {
    val isEditing = userToEdit != null
    val isEditingAdmin = isEditing && (userToEdit?.username.equals("admin", ignoreCase = true) || userToEdit?.role == UserRole.ADMIN)

    // 1. Datos de la Persona
    var fullName by remember { mutableStateOf(userToEdit?.fullName ?: "") }
    var telefono by remember { mutableStateOf(userToEdit?.telefono ?: "") }

    // 2. Credenciales
    var username by remember { mutableStateOf(userToEdit?.username ?: "") }
    var password by remember { mutableStateOf(if (isEditing) "" else "1234") }
    var showPassword by remember { mutableStateOf(false) }

    // 3. Rol (Roles creables por el Administrador: DUEÑO, DEPENDIENTE, CAJERO, COCINA)
    var selectedRole by remember {
        mutableStateOf(
            when (userToEdit?.role) {
                UserRole.ADMIN -> UserRole.ADMIN
                UserRole.DUENO -> UserRole.DUENO
                UserRole.CAJERO -> UserRole.CAJERO
                UserRole.COCINA -> UserRole.COCINA
                UserRole.SALON, UserRole.BARRA, UserRole.DEPENDIENTE -> UserRole.DEPENDIENTE
                else -> UserRole.CAJERO
            }
        )
    }

    // 4. Permisos / Datos correspondientes
    var montoPorProductoText by remember {
        mutableStateOf(if ((userToEdit?.montoPorProducto ?: 0.0) > 0) userToEdit?.montoPorProducto.toString() else "50.0")
    }
    var permisoProduccion by remember { mutableStateOf(userToEdit?.permisoProduccion ?: true) }
    var permisoMercancias by remember { mutableStateOf(userToEdit?.permisoMercancias ?: true) }
    var permisoPersonal by remember { mutableStateOf(userToEdit?.permisoPersonal ?: true) }

    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Cabecera del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isEditing) "Editar Usuario" else "Crear Nuevo Usuario",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Completa los datos en los pasos a continuación",
                            fontSize = 13.sp,
                            color = Slate500
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                Divider(color = Slate200, modifier = Modifier.padding(vertical = 12.dp))

                // Contenido desplazable con campos amplios
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SECCIÓN 1: DATOS DE LA PERSONA
                    FormSectionHeader(number = "1", title = "Datos de la Persona", icon = Icons.Filled.Person)

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            validationError = null
                        },
                        label = { Text("Nombre y Apellidos *", fontSize = 15.sp) },
                        placeholder = { Text("Ej. Pedro Rodríguez", color = Slate400) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = Slate300
                        )
                    )

                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        label = { Text("Teléfono Móvil (para SMS)", fontSize = 15.sp) },
                        placeholder = { Text("Ej. 5351234567", color = Slate400) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().testTag("input_user_telefono"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Outlined.Phone, contentDescription = null, tint = Slate500)
                        },
                        trailingIcon = {
                            ContactPickerIconButton(
                                onContactPicked = { pickedName, pickedPhone ->
                                    telefono = pickedPhone
                                    if (fullName.isBlank() && pickedName.isNotBlank()) {
                                        fullName = pickedName
                                    }
                                }
                            )
                        }
                    )

                    // SECCIÓN 2: CREDENCIALES
                    FormSectionHeader(number = "2", title = "Credenciales de Acceso", icon = Icons.Filled.Key)

                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.filter { ch -> !ch.isWhitespace() }.lowercase()
                            validationError = null
                        },
                        label = { Text("Nombre de Usuario (@usuario) *", fontSize = 15.sp) },
                        placeholder = { Text("Ej. pedro", color = Slate400) },
                        singleLine = true,
                        enabled = !isEditing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Outlined.AlternateEmail, contentDescription = null, tint = Slate500)
                        }
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationError = null
                        },
                        label = {
                            Text(
                                if (isEditing) "Nueva Contraseña (dejar vacío para no cambiar)" else "Contraseña Inicial *",
                                fontSize = 15.sp
                            )
                        },
                        placeholder = { Text("Ej. 1234", color = Slate400) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Ver contraseña",
                                    tint = Slate500
                                )
                            }
                        }
                    )

                    // SECCIÓN 3: ROL DEL USUARIO (DUEÑO, DEPENDIENTE, CAJERO, COCINA)
                    FormSectionHeader(number = "3", title = "Rol en el Negocio", icon = Icons.Filled.Badge)

                    if (isEditingAdmin) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEDE9FE),
                            border = BorderStroke(1.dp, Color(0xFFDDD6FE)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = Color(0xFF6D28D9),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "ADMINISTRADOR",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF6D28D9)
                                    )
                                    Text(
                                        text = "Cuenta fija local de la instalación (Acceso directo sin SMS)",
                                        fontSize = 12.sp,
                                        color = Slate600
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val roles = listOf(
                                Triple(UserRole.DUENO, "DUEÑO", "Administración, control y supervisión total del negocio."),
                                Triple(UserRole.DEPENDIENTE, "DEPENDIENTE", "Toma de comandas, gestión de mesas y pedidos."),
                                Triple(UserRole.CAJERO, "CAJERO", "Cobro de cuentas, arqueos y cuadre de caja."),
                                Triple(UserRole.COCINA, "COCINA", "Visualización y preparación de comandas de cocina.")
                            )

                            roles.forEach { (role, label, desc) ->
                                val isSelected = selectedRole == role
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedRole = role },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) ElQadreNavy.copy(alpha = 0.08f) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) ElQadreNavy else Slate200
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedRole = role },
                                            colors = RadioButtonDefaults.colors(selectedColor = ElQadreNavy)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = label,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) ElQadreNavy else Slate900
                                            )
                                            Text(
                                                text = desc,
                                                fontSize = 12.sp,
                                                color = Slate600
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SECCIÓN 4: PERMISOS / DATOS CORRESPONDIENTES
                    FormSectionHeader(number = "4", title = "Permisos y Datos del Rol", icon = Icons.Filled.Tune)

                    when (selectedRole) {
                        UserRole.DEPENDIENTE -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Configuración de Comisión (Dependiente)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    Text(
                                        text = "Monto asignado por cada plato o producto vendido:",
                                        fontSize = 13.sp,
                                        color = Slate700
                                    )
                                    OutlinedTextField(
                                        value = montoPorProductoText,
                                        onValueChange = { montoPorProductoText = it },
                                        label = { Text("Monto por producto (CUP)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                        UserRole.DUENO -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7).copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Permisos de Control (Dueño)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    
                                    // Control del Negocio (Obligatorio)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Emerald50, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Emerald600, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Control del Negocio", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate800)
                                            Text("Obligatorio y siempre activo para Dueños", fontSize = 11.sp, color = Slate500)
                                        }
                                    }

                                    // Switch Producción
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { permisoProduccion = !permisoProduccion }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = permisoProduccion,
                                            onCheckedChange = { permisoProduccion = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Permiso de Producción", fontSize = 14.sp, color = Slate800)
                                    }

                                    // Switch Mercaderías
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { permisoMercancias = !permisoMercancias }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = permisoMercancias,
                                            onCheckedChange = { permisoMercancias = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Permiso de Mercaderías", fontSize = 14.sp, color = Slate800)
                                    }

                                    // Switch Personal
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { permisoPersonal = !permisoPersonal }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = permisoPersonal,
                                            onCheckedChange = { permisoPersonal = it },
                                            colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Permiso de Personal", fontSize = 14.sp, color = Slate800)
                                    }
                                }
                            }
                        }
                        UserRole.CAJERO, UserRole.COCINA -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate100,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = Slate600, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Las funciones operativas para este rol se asignan de forma automática.",
                                        fontSize = 13.sp,
                                        color = Slate600
                                    )
                                }
                            }
                        }
                        else -> {}
                    }

                    // Mensaje de Error de Validación
                    validationError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $err",
                                color = Color(0xFFDC2626),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                Divider(color = Slate200, modifier = Modifier.padding(vertical = 12.dp))

                // Botones de Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancelar", fontSize = 16.sp, color = Slate700)
                    }

                    Button(
                        onClick = {
                            if (fullName.isBlank()) {
                                validationError = "Por favor ingrese el nombre completo."
                                return@Button
                            }
                            if (username.isBlank()) {
                                validationError = "Por favor ingrese el nombre de usuario."
                                return@Button
                            }
                            if (!isEditing && password.isBlank()) {
                                validationError = "Por favor ingrese la contraseña inicial."
                                return@Button
                            }

                            val cleanUsername = username.trim().lowercase()
                            val commissionRate = montoPorProductoText.toDoubleOrNull() ?: 0.0

                            val passwordHash = if (password.isNotBlank()) {
                                password.trim().toSha256()
                            } else {
                                userToEdit?.passwordHash ?: "1234".toSha256()
                            }

                            val effectivePlainPassword = if (password.isNotBlank()) password.trim() else "1234"

                            val userToSave = User(
                                username = cleanUsername,
                                fullName = fullName.trim(),
                                passwordHash = passwordHash,
                                role = selectedRole,
                                montoPorProducto = if (selectedRole == UserRole.DEPENDIENTE) commissionRate else 0.0,
                                isActive = true,
                                telefono = telefono.trim(),
                                permisoProduccion = if (selectedRole == UserRole.DUENO) permisoProduccion else true,
                                permisoMercancias = if (selectedRole == UserRole.DUENO) permisoMercancias else true,
                                permisoPersonal = if (selectedRole == UserRole.DUENO) permisoPersonal else true,
                                permisoControlNegocio = true
                            )

                            onSaveAndGenerateSms(userToSave, effectivePlainPassword)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .testTag("admin_save_user_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditingAdmin) "Guardar Cambios" else "Guardar y Generar SMS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para cambio directo y local de la contraseña del Administrador (@admin).
 */
@Composable
fun AdminPasswordChangeDialog(
    onDismiss: () -> Unit,
    onSavePassword: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LockReset,
                    contentDescription = null,
                    tint = Color(0xFF6D28D9),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Cambiar Contraseña",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Cuenta: @admin (Local)",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Introduce la nueva contraseña local para el usuario @admin. Se guardará localmente y se utilizará en los próximos inicios de sesión.",
                    fontSize = 13.sp,
                    color = Slate700
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorText = null
                    },
                    label = { Text("Nueva Contraseña *") },
                    placeholder = { Text("Ej. mi_nueva_clave") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = "Ver contraseña",
                                tint = Slate500
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorText = null
                    },
                    label = { Text("Confirmar Nueva Contraseña *") },
                    placeholder = { Text("Repita la nueva contraseña") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                errorText?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ $err",
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.isBlank()) {
                        errorText = "La contraseña no puede estar vacía."
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        errorText = "Las contraseñas no coinciden."
                        return@Button
                    }
                    onSavePassword(newPassword.trim())
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6D28D9),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Contraseña", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate600)
            }
        }
    )
}

@Composable
fun FormSectionHeader(number: String, title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = ElQadreNavy,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = ElQadreNavy
        )
    }
}

/**
 * Diálogo de previsualización y envío de SMS generado según formato estándar ElQadre.
 */
@Composable
fun AdminSmsPreviewDialog(
    smsInfo: GeneratedSmsInfo,
    onDismiss: () -> Unit,
    onLogoutToTest: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "SMS de Alta Generado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Usuario guardado exitosamente",
                        fontSize = 12.sp,
                        color = Slate500
                    )
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
                // Cuadro informativo de prueba local
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "La cuenta ya está registrada localmente en este dispositivo. Puedes salir al Login y probarla con su usuario y contraseña antes de entregar el SMS.",
                            fontSize = 12.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 16.sp
                        )
                    }
                }

                Text(
                    text = "Texto del SMS generado para entrega:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Slate800
                )

                // Bloque con el texto del SMS en estilo código
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = smsInfo.smsText,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(14.dp),
                        lineHeight = 18.sp
                    )
                }

                if (smsInfo.recipientPhone.isNotBlank()) {
                    Text(
                        text = "Destinatario: ${smsInfo.recipientPhone}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón Enviar SMS por app nativa
                Button(
                    onClick = {
                        try {
                            val intent = if (smsInfo.recipientPhone.isNotBlank()) {
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${smsInfo.recipientPhone}")
                                    putExtra("sms_body", smsInfo.smsText)
                                }
                            } else {
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = Uri.parse("sms:")
                                    putExtra("sms_body", smsInfo.smsText)
                                }
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir la app de mensajería: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enviar SMS por Mensajería", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // Botón Copiar al Portapapeles
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("SMS ElQadre", smsInfo.smsText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Texto del SMS copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Slate300)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = Slate700, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copiar SMS al Portapapeles", color = Slate700, fontSize = 14.sp)
                }

                // Botón Probar Cuenta Ahora (Cerrar Sesión)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar", color = Slate600)
                    }

                    TextButton(
                        onClick = onLogoutToTest,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Salir a Probar", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {}
    )
}
