package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Code
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
import androidx.core.content.FileProvider
import com.example.ui.theme.ElQadreGold
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.util.ApkUpdateManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Componente de tarjeta de Ajustes para visualización de la versión actual instalada,
 * generación directa del archivo version.json listo para enviar y compartir,
 * y verificación / descarga manual de actualizaciones remotas.
 */
@Composable
fun AppVersionSettingsCard(
    modifier: Modifier = Modifier,
    showGenerateJson: Boolean = false
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

    // Estado para el archivo version.json generado
    var showGeneratedDialog by remember { mutableStateOf(false) }
    var generatedJsonResult by remember { mutableStateOf("") }
    var generatedJsonFile by remember { mutableStateOf<File?>(null) }

    fun shareVersionJsonFile(ctx: Context, file: File, jsonText: String) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, jsonText)
                putExtra(Intent.EXTRA_TITLE, "version.json")
                putExtra(Intent.EXTRA_SUBJECT, "version.json ElQadrePF")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Enviar / Compartir version.json").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, jsonText)
                putExtra(Intent.EXTRA_TITLE, "version.json")
            }
            ctx.startActivity(Intent.createChooser(textIntent, "Compartir version.json"))
        }
    }

    fun onGenerateJsonClicked() {
        val jsonContent = ApkUpdateManager.generateVersionJson(
            versionCode = localCode,
            versionName = localName,
            apkUrl = "https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v1.1/ElQadrePF.apk",
            notes = ""
        )
        generatedJsonResult = jsonContent

        // Crear/guardar físicamente el archivo version.json en la caché de la aplicación
        try {
            val jsonDir = File(context.cacheDir, "version_updates")
            if (!jsonDir.exists()) jsonDir.mkdirs()
            val file = File(jsonDir, "version.json")
            file.writeText(jsonContent)
            generatedJsonFile = file
        } catch (e: Exception) {
            generatedJsonFile = null
        }

        showGeneratedDialog = true
    }

    fun runManualCheck() {
        if (isChecking || isDownloading) return
        isChecking = true
        coroutineScope.launch {
            val result = ApkUpdateManager.checkUpdate(context)
            isChecking = false
            when (result) {
                is ApkUpdateManager.VersionCheckResult.UpdateAvailable -> {
                    remoteVersionName = result.remoteInfo.versionName
                    remoteVersionCode = result.remoteInfo.versionCode
                    remoteApkUrl = result.remoteInfo.apkUrl
                    updateNotes = result.remoteInfo.notes
                    showUpdateDialog = true
                }
                is ApkUpdateManager.VersionCheckResult.AlreadyUpToDate -> {
                    Toast.makeText(
                        context,
                        "La aplicación está actualizada (v$localName)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                is ApkUpdateManager.VersionCheckResult.Error -> {
                    Toast.makeText(
                        context,
                        "No se pudo verificar: ${result.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_version_settings"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Encabezado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ElQadreNavy.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
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
                        text = "Versión de la aplicación",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                    Text(
                        text = "Compruebe si existe una nueva versión de ElQadre",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            }

            HorizontalDivider(color = Slate200)

            // Fila de versión actual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Versión instalada",
                    fontSize = 13.sp,
                    color = Slate600
                )
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Slate200)
                ) {
                    Text(
                        text = localName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (showGenerateJson) {
                OutlinedButton(
                    onClick = { onGenerateJsonClicked() },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ElQadreNavy),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_generar_version_json")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GENERAR version.json", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    Text("ACTUALIZAR APLICACIÓN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showGeneratedDialog && generatedJsonResult.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showGeneratedDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Code, contentDescription = null, tint = ElQadreNavy)
                    Text("version.json Generado", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "El archivo version.json fue generado listo para enviarse o compartirse.",
                        fontSize = 13.sp,
                        color = Slate600
                    )
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedJsonResult,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Slate800
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = generatedJsonFile
                        if (file != null && file.exists()) {
                            shareVersionJsonFile(context, file, generatedJsonResult)
                        } else {
                            val textIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, generatedJsonResult)
                            }
                            context.startActivity(Intent.createChooser(textIntent, "Compartir version.json"))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ENVIAR / COMPARTIR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("version.json", generatedJsonResult)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "version.json copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPIAR", fontSize = 12.sp)
                    }
                    TextButton(onClick = { showGeneratedDialog = false }) {
                        Text("CERRAR", color = Slate600, fontSize = 12.sp)
                    }
                }
            }
        )
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
