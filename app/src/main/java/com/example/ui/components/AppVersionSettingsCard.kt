package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.util.ApkUpdateManager
import kotlinx.coroutines.launch

/**
 * Sección reutilizable de Ajustes para mostrar la versión instalada
 * y permitir la búsqueda manual de actualizaciones de APK en todos los roles.
 */
@Composable
fun AppVersionSettingsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (localCode, localName) = remember {
        ApkUpdateManager.getLocalVersionInfo(context)
    }

    var isChecking by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var remoteVersionName by remember { mutableStateOf("") }
    var remoteVersionCode by remember { mutableStateOf(0L) }
    var remoteApkUrl by remember { mutableStateOf("") }
    var updateNotes by remember { mutableStateOf("") }

    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }

    fun runManualCheck() {
        if (isChecking || isDownloading) return
        isChecking = true

        coroutineScope.launch {
            try {
                // 1. Comprobar conexión a Internet
                val hasInternet = ApkUpdateManager.isNetworkAvailable(context)
                if (!hasInternet) {
                    Toast.makeText(
                        context,
                        "Error de conexión: No hay conexión a Internet. Compruebe su red WiFi o Datos Móviles.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // 2. Consultar version.json
                when (val result = ApkUpdateManager.checkUpdate(context)) {
                    is ApkUpdateManager.VersionCheckResult.AlreadyUpToDate -> {
                        Toast.makeText(
                            context,
                            "La aplicación está actualizada.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is ApkUpdateManager.VersionCheckResult.UpdateAvailable -> {
                        val remoteInfo = result.remoteInfo
                        remoteApkUrl = remoteInfo.apkUrl
                        remoteVersionName = remoteInfo.versionName.ifBlank { "v${remoteInfo.versionCode}" }
                        remoteVersionCode = remoteInfo.versionCode
                        updateNotes = remoteInfo.notes
                        showUpdateDialog = true
                    }
                    is ApkUpdateManager.VersionCheckResult.Error -> {
                        Toast.makeText(
                            context,
                            "Error al comprobar actualizaciones: ${result.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Error de conexión al verificar actualización: ${e.localizedMessage ?: "Error desconocido"}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isChecking = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "VERSIÓN DE LA APLICACIÓN: ${localName.ifBlank { "1.0.0" }}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreNavy,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Compruebe si existe una nueva versión de ElQadre.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }

            Button(
                onClick = { runManualCheck() },
                enabled = !isChecking && !isDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_buscar_actualizaciones")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COMPROBANDO...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BUSCAR ACTUALIZACIONES", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            show = showUpdateDialog,
            installedVersionName = localName,
            installedVersionCode = localCode,
            remoteVersionName = remoteVersionName,
            remoteVersionCode = remoteVersionCode,
            notes = updateNotes,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onConfirmUpdate = {
                isDownloading = true
                coroutineScope.launch {
                    val apkFile = ApkUpdateManager.downloadApk(context, remoteApkUrl) { prog ->
                        downloadProgress = prog
                    }
                    isDownloading = false
                    if (apkFile != null && apkFile.exists() && apkFile.length() > 0) {
                        try {
                            ApkUpdateManager.launchApkInstall(context, apkFile)
                            showUpdateDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                "Error al iniciar la instalación del APK: ${e.localizedMessage ?: "Error"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Error al descargar la nueva versión del APK. Verifique su conexión.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
            onDismiss = {
                showUpdateDialog = false
            }
        )
    }
}
