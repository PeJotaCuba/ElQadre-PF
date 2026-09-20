package com.example.ui.screens.login

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.ElQadreBrandHeader
import com.example.ui.screens.cajero.ConteoResumenDialog
import com.example.ui.screens.cajero.QuickActionsDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.PasswordResetHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.content.FileProvider
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val loginPrefs = remember { context.getSharedPreferences("elqadre_login_prefs", Context.MODE_PRIVATE) }
    val savedRemember = remember { loginPrefs.getBoolean("remember_user", false) }

    var username by remember {
        mutableStateOf(if (savedRemember) loginPrefs.getString("saved_username", "") ?: "" else "")
    }
    var password by remember {
        mutableStateOf(if (savedRemember) loginPrefs.getString("saved_password", "") ?: "" else "")
    }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberUser by remember { mutableStateOf(savedRemember) }

    var showQuickActions by remember { mutableStateOf(false) }
    val billDenominationStacks = remember { mutableStateMapOf<Int, SnapshotStateList<String>>() }
    var showResumenConteoDialog by remember { mutableStateOf(false) }

    // State for Password Recovery
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var recoveryPhone by remember { mutableStateOf("") }
    var recoveryNewPass by remember { mutableStateOf("") }
    var recoveryConfirmPass by remember { mutableStateOf("") }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var showNotRegisteredDialog by remember { mutableStateOf(false) }
    var showRequestSuccessDialog by remember { mutableStateOf(false) }

    var showAppUpdateDialog by remember { mutableStateOf(false) }
    var remoteVersionName by remember { mutableStateOf("") }
    var remoteVersionCode by remember { mutableStateOf(0L) }
    var localVersionName by remember { mutableStateOf("") }
    var localVersionCode by remember { mutableStateOf(0L) }
    var remoteApkUrl by remember { mutableStateOf("") }
    var updateNotes by remember { mutableStateOf("") }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showLoginGuiado by remember {
        mutableStateOf(!com.example.ui.components.GuiadoPrefsManager.isLoginGuideShown(context))
    }
    var isImportingUserData by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val jsonFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isImportingUserData = true
            scope.launch {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                    
                    if (jsonString.isBlank()) {
                        Toast.makeText(context, "El archivo seleccionado está vacío.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    
                    val fileContent = jsonString.trim()

                    if (com.example.util.AccountProvisioningHelper.isAccountSms(fileContent)) {
                        when (val res = com.example.util.AccountProvisioningHelper.processAccountSms(context, fileContent)) {
                            is com.example.util.AccountProvisioningResult.Success -> {
                                username = res.user.username
                                Toast.makeText(
                                    context,
                                    "¡Cuenta de usuario cargada con éxito!\nUsuario: ${res.user.username} (${res.user.role.displayName})",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            is com.example.util.AccountProvisioningResult.Error -> {
                                Toast.makeText(context, res.reason, Toast.LENGTH_LONG).show()
                            }
                            is com.example.util.AccountProvisioningResult.IgnoredNotAccountSms -> {
                                Toast.makeText(
                                    context,
                                    "El SMS no tiene el formato de alta requerido.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        val root = try {
                            org.json.JSONObject(fileContent)
                        } catch (e: Exception) {
                            null
                        }

                        if (root != null && com.example.util.PersonalUserDataManager.isPersonalUserJson(root)) {
                            when (val res = com.example.util.PersonalUserDataManager.importPersonalUserJson(context, uri, db)) {
                                is com.example.util.PersonalUserDataManager.ImportResult.Success -> {
                                    username = res.username
                                    Toast.makeText(
                                        context,
                                        "¡Datos de usuario cargados con éxito!\nUsuario: ${res.username} (${res.roleDisplayName})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.example.util.PersonalUserDataManager.ImportResult.Error -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        } else if (root != null && com.example.util.CatalogoDataManager.isCatalogoJson(root)) {
                            when (val res = com.example.util.CatalogoDataManager.importCatalogoJson(context, uri, db)) {
                                is com.example.util.CatalogoDataManager.ImportResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        "¡Catálogo cargado con éxito!\n${res.businessName} (${res.businessNumber}): ${res.productsCount} productos y ${res.categoriesCount} categorías.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                is com.example.util.CatalogoDataManager.ImportResult.Error -> {
                                    Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "El archivo seleccionado no corresponde a un SMS de alta, usuario ni catálogo válido de ElQadre.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al procesar archivo: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isImportingUserData = false
                }
            }
        }
    }

    val readSmsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isImportingUserData = true
            scope.launch {
                try {
                    val results = withContext(Dispatchers.IO) {
                        com.example.util.AccountProvisioningHelper.scanAndProcessInbox(context, forceReprocess = true)
                    }
                    val firstSuccess = results.filterIsInstance<com.example.util.AccountProvisioningResult.Success>().lastOrNull()
                    if (firstSuccess != null) {
                        username = firstSuccess.user.username
                        Toast.makeText(
                            context,
                            "¡Cuenta cargada con éxito desde SMS!\nUsuario: ${firstSuccess.user.username} (${firstSuccess.user.role.displayName})",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "No se encontró ningún SMS de alta nuevo en la bandeja. Seleccione un archivo.", Toast.LENGTH_SHORT).show()
                        jsonFilePickerLauncher.launch("*/*")
                    }
                } catch (e: Exception) {
                    jsonFilePickerLauncher.launch("*/*")
                } finally {
                    isImportingUserData = false
                }
            }
        } else {
            jsonFilePickerLauncher.launch("*/*")
        }
    }

    fun executeCargarDatosFlow() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            isImportingUserData = true
            scope.launch {
                try {
                    val results = withContext(Dispatchers.IO) {
                        com.example.util.AccountProvisioningHelper.scanAndProcessInbox(context, forceReprocess = true)
                    }
                    val firstSuccess = results.filterIsInstance<com.example.util.AccountProvisioningResult.Success>().lastOrNull()
                    if (firstSuccess != null) {
                        username = firstSuccess.user.username
                        Toast.makeText(
                            context,
                            "¡Cuenta cargada con éxito desde SMS!\nUsuario: ${firstSuccess.user.username} (${firstSuccess.user.role.displayName})",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Bandeja SMS revisada sin altas pendientes. Seleccione archivo de datos.", Toast.LENGTH_SHORT).show()
                        jsonFilePickerLauncher.launch("*/*")
                    }
                } catch (e: Exception) {
                    jsonFilePickerLauncher.launch("*/*")
                } finally {
                    isImportingUserData = false
                }
            }
        } else {
            readSmsPermissionLauncher.launch(Manifest.permission.READ_SMS)
        }
    }

    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val isUserDbUpdated = remember(uiState.generalConfig) {
        (uiState.generalConfig?.lastUserUpdateDate ?: 0L) > 0L ||
                uiState.generalConfig?.lastUserUpdateStatus == "COMPLETADO"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top background color for the transition effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .background(ElQadreNavy)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Top ELQADRE Official Logo (logo.png)
            ElQadreBrandHeader(
                logoSize = 168.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // White Floating / Elevated Login Container
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "INICIA SESIÓN",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ElQadreNavy
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ingresa tus credenciales",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    if (uiState.errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Rose50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                color = Rose700,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Username Input with clean line icon
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            if (uiState.errorMessage != null) viewModel.clearMessages()
                        },
                        label = { Text("Usuario") },
                        placeholder = { Text("dueno, cajero1, salon1...") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = Slate400)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_username_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password Input with line lock icon and visibility toggle
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (uiState.errorMessage != null) viewModel.clearMessages()
                        },
                        label = { Text("Contraseña") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = Slate400)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Mostrar contraseña",
                                    tint = Slate400
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { rememberUser = !rememberUser }
                        ) {
                            Checkbox(
                                checked = rememberUser,
                                onCheckedChange = { rememberUser = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = ElQadreGold,
                                    checkmarkColor = Color.White,
                                    uncheckedColor = ElQadreGold
                                )
                            )
                            Text(
                                text = "Recordar usuario",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }

                        Text(
                            text = "¿Olvidaste tu contraseña?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ElQadreGoldDark,
                            modifier = Modifier.clickable {
                                recoveryPhone = ""
                                recoveryNewPass = ""
                                recoveryConfirmPass = ""
                                recoveryError = null
                                showRecoveryDialog = true
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Spacer(modifier = Modifier.height(20.dp))

                    // Main Action: Solid Navy "Ingresar" button (Exact match to reference)
                    Button(
                        onClick = {
                            viewModel.login(username, password) {
                                if (rememberUser) {
                                    loginPrefs.edit()
                                        .putBoolean("remember_user", true)
                                        .putString("saved_username", username)
                                        .putString("saved_password", password)
                                        .apply()
                                } else {
                                    loginPrefs.edit()
                                        .putBoolean("remember_user", false)
                                        .remove("saved_username")
                                        .remove("saved_password")
                                        .apply()
                                }
                                onLoginSuccess()
                            }
                        },
                        enabled = username.isNotBlank() && password.isNotBlank() && !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("login_submit_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = ElQadreGold,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "INGRESAR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            executeCargarDatosFlow()
                        },
                        enabled = !uiState.isLoading && !isImportingUserData,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, ElQadreNavy.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElQadreNavy
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_cargar_datos_usuario_login")
                    ) {
                        if (isImportingUserData) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ElQadreNavy,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.FileOpen,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CARGAR DATOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 0.3.sp,
                                color = ElQadreNavy
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Footer Device ID & Offline status (Exact match to reference)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = Slate500,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "ID del dispositivo",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                        Text(
                            text = "DVC-${uiState.deviceId.uppercase()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Aplicación 100% local y offline",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate500
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Flotante de Acciones Rápidas (Calculadora y Conteo de Billetes)
        FloatingActionButton(
            onClick = { showQuickActions = true },
            containerColor = ElQadreGold,
            contentColor = ElQadreNavy,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("btn_acciones_rapidas_login")
        ) {
            Icon(
                imageVector = Icons.Filled.Calculate,
                contentDescription = "Acciones Rápidas"
            )
        }

        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .zIndex(10f)
                    .testTag("login_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
        }
    }

    // Flotante de herramientas (Calculadora y Conteo de Billetes)
    if (showQuickActions) {
        QuickActionsDialog(
            initialTab = 0,
            billFields = billDenominationStacks,
            onShowResumen = { showResumenConteoDialog = true },
            onDismiss = { showQuickActions = false }
        )
    }

    // Resumen de Conteo de Billetes
    if (showResumenConteoDialog) {
        ConteoResumenDialog(
            billFields = billDenominationStacks,
            onDismiss = { showResumenConteoDialog = false }
        )
    }

    // Dialog for RECUPERAR CONTRASEÑA
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = {
                Text("RECUPERAR CONTRASEÑA", fontWeight = FontWeight.Bold, color = ElQadreNavy)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Ingresa tu número de móvil registrado y la nueva contraseña deseada.",
                        fontSize = 12.sp,
                        color = Slate600
                    )

                    if (recoveryError != null) {
                        Text(
                            text = recoveryError!!,
                            color = Rose600,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = recoveryPhone,
                        onValueChange = { recoveryPhone = it; recoveryError = null },
                        label = { Text("Número de móvil") },
                        placeholder = { Text("Ej. 5351234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_phone_input")
                    )

                    OutlinedTextField(
                        value = recoveryNewPass,
                        onValueChange = { recoveryNewPass = it; recoveryError = null },
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_new_pass_input")
                    )

                    OutlinedTextField(
                        value = recoveryConfirmPass,
                        onValueChange = { recoveryConfirmPass = it; recoveryError = null },
                        label = { Text("Confirmar nueva contraseña") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("recovery_confirm_pass_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedPhone = recoveryPhone.trim()
                        if (trimmedPhone.isBlank()) {
                            recoveryError = "Por favor ingresa tu número de móvil."
                            return@Button
                        }

                        // Validate against local users
                        val matchedUser = PasswordResetHelper.matchUserByPhone(uiState.users, trimmedPhone)
                        if (matchedUser == null) {
                            showRecoveryDialog = false
                            showNotRegisteredDialog = true
                            return@Button
                        }

                        // Validate passwords
                        val newPass = recoveryNewPass.trim()
                        val confirmPass = recoveryConfirmPass.trim()

                        if (newPass.isBlank()) {
                            recoveryError = "Ingresa la nueva contraseña."
                            return@Button
                        }

                        if (newPass != confirmPass) {
                            recoveryError = "Las contraseñas no coinciden."
                            return@Button
                        }

                        // Formulate SMS content
                        val duenoPhone = uiState.generalConfig?.telefonoDueno?.ifBlank {
                            uiState.generalConfig?.telefonoAdmin
                        }?.trim() ?: ""

                        if (duenoPhone.isBlank()) {
                            recoveryError = "El número del Dueño no está configurado en Ajustes."
                            return@Button
                        }

                        // Exact SMS content format:
                        // CAMBIAR CONTRASEÑA
                        // [NÚMERO DE MÓVIL]
                        // [CONTRASEÑA NUEVA]
                        val smsText = "CAMBIAR CONTRASEÑA\n$trimmedPhone\n$newPass"

                        val sent = try {
                            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                            smsManager.sendTextMessage(duenoPhone, null, smsText, null, null)
                            true
                        } catch (e: Exception) {
                            false
                        }

                        if (!sent) {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$duenoPhone")
                                    putExtra("sms_body", smsText)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al abrir la app de SMS: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }

                        showRecoveryDialog = false
                        showRequestSuccessDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog when mobile number is not registered in local users
    if (showNotRegisteredDialog) {
        val duenoPhone = uiState.generalConfig?.telefonoDueno?.ifBlank {
            uiState.generalConfig?.telefonoAdmin
        }?.trim() ?: "No configurado"

        AlertDialog(
            onDismissRequest = { showNotRegisteredDialog = false },
            title = {
                Text("Usuario no registrado", fontWeight = FontWeight.Bold, color = Rose600)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "No eres un usuario registrado. Contacta con el Dueño.",
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Teléfono del Dueño: $duenoPhone",
                        color = ElQadreNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showNotRegisteredDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Aceptar")
                }
            }
        )
    }

    // Dialog confirming SMS request generated
    if (showRequestSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showRequestSuccessDialog = false },
            title = { Text("Solicitud enviada", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Text(
                    "Se ha generado el mensaje SMS de cambio de contraseña dirigido al Dueño.\n\n" +
                            "La contraseña NO se aplicará en este dispositivo hasta que el Dueño rastree la solicitud y confirme el cambio.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRequestSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Entendido")
                }
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
            scope.launch {
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

    if (showLoginGuiado) {
        com.example.ui.components.LoginGuiadoDialog(
            onDismiss = {
                com.example.ui.components.GuiadoPrefsManager.setLoginGuideShown(context)
                showLoginGuiado = false
            }
        )
    }
}
