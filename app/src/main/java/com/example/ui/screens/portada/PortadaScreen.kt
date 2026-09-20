package com.example.ui.screens.portada

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.licensing.CommercialLicenseManager
import com.example.licensing.LicenseUpdateResult
import com.example.ui.components.ElQadreBrandHeader
import com.example.ui.theme.*
import kotlinx.coroutines.launch

import android.widget.Toast
import androidx.compose.material.icons.filled.Sync
import com.example.ui.components.AppUpdateDialog
import com.example.util.ApkUpdateManager

@Composable
fun PortadaScreen(
    dvc: String,
    onTokenValidatedSuccess: () -> Unit,
    onNoToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val licenseManager = remember { CommercialLicenseManager.getInstance(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var tokenInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // APK Update state
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    var remoteApkUrl by remember { mutableStateOf("") }
    var remoteVersionName by remember { mutableStateOf("") }
    var remoteVersionCode by remember { mutableStateOf(0L) }
    var localVersionName by remember { mutableStateOf("") }
    var localVersionCode by remember { mutableStateOf(0L) }
    var updateNotes by remember { mutableStateOf("") }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    val scrollState = rememberScrollState()

    fun handleCheckUpdate() {
        isCheckingUpdate = true
        coroutineScope.launch {
            try {
                when (val result = ApkUpdateManager.checkUpdate(context)) {
                    is ApkUpdateManager.VersionCheckResult.UpdateAvailable -> {
                        val remoteInfo = result.remoteInfo
                        remoteApkUrl = remoteInfo.apkUrl
                        remoteVersionName = remoteInfo.versionName.ifBlank { "v${remoteInfo.versionCode}" }
                        remoteVersionCode = remoteInfo.versionCode
                        localVersionName = result.localVersionName
                        localVersionCode = result.localVersionCode
                        updateNotes = remoteInfo.notes
                        showAppUpdateDialog = true
                    }
                    is ApkUpdateManager.VersionCheckResult.AlreadyUpToDate -> {
                        Toast.makeText(
                            context,
                            "La aplicación está actualizada.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ApkUpdateManager.VersionCheckResult.Error -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error de conexión al verificar actualización: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun validateTokenSubmit() {
        val cleanToken = tokenInput.trim().uppercase()
        if (cleanToken.length != 6 || !cleanToken.all { it.isLetterOrDigit() }) {
            errorMessage = "El TOKEN debe contener exactamente 6 caracteres (letras y números)."
            return
        }

        keyboardController?.hide()
        errorMessage = null
        isValidating = true

        coroutineScope.launch {
            val result = licenseManager.validateToken(context, cleanToken, dvc)
            isValidating = false
            when (result) {
                is LicenseUpdateResult.Success -> {
                    onTokenValidatedSuccess()
                }
                is LicenseUpdateResult.Error -> {
                    errorMessage = result.message
                }
                is LicenseUpdateResult.NoChange -> {
                    errorMessage = result.message
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top background Navy header block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .background(ElQadreNavy)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // ElQadre Header Logo
            ElQadreBrandHeader(
                logoSize = 160.dp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main White Card Container
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VINCULACIÓN DE NEGOCIO",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Introduce el TOKEN de 6 caracteres para activar el dispositivo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Internet Connection Requirement Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Sky50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Sky200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Wifi,
                                contentDescription = null,
                                tint = Sky700,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Para validar el token se requiere conexión a Internet (Wi-Fi o datos móviles).",
                                fontSize = 12.sp,
                                color = Sky900,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Rose50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Rose700,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Token Field Input
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { newValue ->
                            val filtered = newValue.uppercase().filter { it.isLetterOrDigit() }
                            if (filtered.length <= 6) {
                                tokenInput = filtered
                                if (errorMessage != null) errorMessage = null
                            }
                        },
                        label = { Text("TOKEN DE VINCULACIÓN") },
                        placeholder = { Text("6 CARACTERES (EJ. A1B2C3)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Key,
                                contentDescription = null,
                                tint = Slate400
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (tokenInput.length == 6 && !isValidating) {
                                    validateTokenSubmit()
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portada_token_input")
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ACEPTAR Button
                    Button(
                        onClick = { validateTokenSubmit() },
                        enabled = tokenInput.length == 6 && !isValidating,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White,
                            disabledContainerColor = Slate300,
                            disabledContentColor = Slate500
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("portada_aceptar_button")
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "VALIDANDO TOKEN...",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        } else {
                            Text(
                                text = "ACEPTAR",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // NO TENGO TOKEN Button
                    TextButton(
                        onClick = onNoToken,
                        enabled = !isValidating && !isCheckingUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("portada_no_token_button")
                    ) {
                        Text(
                            text = "NO TENGO TOKEN",
                            color = ElQadreGoldDark,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        color = Color(0xFFE2E8F0)
                    )

                    // ACTUALIZAR Button
                    OutlinedButton(
                        onClick = { handleCheckUpdate() },
                        enabled = !isValidating && !isCheckingUpdate,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElQadreNavy
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("portada_actualizar_button")
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                color = ElQadreNavy,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VERIFICANDO ACTUALIZACIÓN...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ACTUALIZAR",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Shared App Update Dialog
        AppUpdateDialog(
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
                    val apkFile = ApkUpdateManager.downloadApk(context, remoteApkUrl) { progress ->
                        downloadProgress = progress
                    }
                    isDownloadingUpdate = false
                    showAppUpdateDialog = false
                    if (apkFile != null) {
                        ApkUpdateManager.launchApkInstall(context, apkFile)
                    } else {
                        Toast.makeText(
                            context,
                            "Error al descargar la APK de actualización. Verifique su conexión.",
                            Toast.LENGTH_LONG
                        ).show()
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
}
