package com.example.ui.screens.superadmin

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.material.icons.outlined.Share
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
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SuperAdminVersionesSection(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gestión de Versiones y APK",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = ElQadreNavy
        )
        Text(
            text = "Panel exclusivo de Super Admin para preparar la APK instalada, compartirla y publicar el archivo version.json para las actualizaciones de los clientes.",
            fontSize = 13.sp,
            color = Slate600
        )

        GestionArchivosSection()
        AppVersionSection()
    }
}

@Composable
fun GestionArchivosSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var versionName by remember { mutableStateOf("1.0") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val cleanVersion = if (versionName.startsWith("v", ignoreCase = true)) versionName else "v$versionName"
    val apkFileName = "ElQadre_$cleanVersion.apk"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderShared,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Gestión de archivos",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ElQadreNavy
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Versión instalada: $versionName",
                    fontSize = 14.sp,
                    color = Slate700
                )
                Text(
                    text = "Archivo: $apkFileName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElQadreNavy
                )
                Text(
                    text = "Obtiene una copia del APK actualmente instalado y abre el selector de Android para compartirlo por WhatsApp, Bluetooth, Files, Drive, etc.",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }

            Button(
                onClick = { showConfirmDialog = true },
                enabled = !isSharing,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("share_current_apk_button")
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preparando APK...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                } else {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COMPARTIR APK ACTUAL", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSharing) showConfirmDialog = false },
            title = {
                Text(
                    "Compartir APK actual",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se va a compartir la APK instalada actualmente en este dispositivo:",
                        fontSize = 14.sp,
                        color = Slate700
                    )
                    Surface(
                        color = Slate100,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Versión: $versionName", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ElQadreNavy)
                            Text("• Archivo: $apkFileName", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ElQadreNavy)
                        }
                    }
                    Text(
                        "¿Desea continuar y abrir el selector de aplicaciones?",
                        fontSize = 13.sp,
                        color = Slate600
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isSharing = true
                        scope.launch {
                            try {
                                val success = withContext(Dispatchers.IO) {
                                    val sourcePath = context.applicationInfo.sourceDir
                                    if (sourcePath.isNullOrBlank()) return@withContext false
                                    val sourceFile = File(sourcePath)
                                    if (!sourceFile.exists() || !sourceFile.canRead()) return@withContext false

                                    val shareDir = File(context.cacheDir, "shared_apk")
                                    if (!shareDir.exists()) {
                                        shareDir.mkdirs()
                                    } else {
                                        shareDir.listFiles()?.forEach { it.delete() }
                                    }

                                    val destFile = File(shareDir, apkFileName)
                                    sourceFile.inputStream().use { input ->
                                        destFile.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    destFile.exists() && destFile.length() > 0
                                }

                                if (success) {
                                    val destFile = File(File(context.cacheDir, "shared_apk"), apkFileName)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        destFile
                                    )

                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.android.package-archive"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, apkFileName)
                                        putExtra(Intent.EXTRA_TEXT, "APK instalador de ElQadre ($apkFileName)")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }

                                    val chooser = Intent.createChooser(sendIntent, "Compartir APK de ElQadre").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    val resInfoList = context.packageManager.queryIntentActivities(
                                        chooser,
                                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                                    )
                                    for (resolveInfo in resInfoList) {
                                        val packageName = resolveInfo.activityInfo.packageName
                                        context.grantUriPermission(
                                            packageName,
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    }

                                    context.startActivity(chooser)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error: No se pudo obtener la APK instalada para compartir.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error al compartir APK: ${e.message ?: "Error desconocido"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("COMPARTIR")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("CANCELAR", color = Slate700)
                }
            }
        )
    }
}

@Composable
fun AppVersionSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var versionName by remember { mutableStateOf("Desconocida") }
    var versionCode by remember { mutableStateOf<Long>(0L) }
    var targetVersionCode by remember { mutableStateOf("") }
    var targetVersionName by remember { mutableStateOf("") }
    var targetApkUrl by remember { mutableStateOf("https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v1.1/ElQadre.apk") }
    var targetNotes by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            versionName = packageInfo.versionName ?: "Desconocida"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            if (targetVersionCode.isBlank()) targetVersionCode = versionCode.toString()
            if (targetVersionName.isBlank()) targetVersionName = versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    val currentDate = sdf.format(Date())
                    val finalCode = targetVersionCode.toLongOrNull() ?: versionCode
                    val finalName = targetVersionName.ifBlank { versionName }
                    val finalUrl = targetApkUrl.ifBlank { "https://github.com/PeJotaCuba/BD-Qadre-PF/releases/download/v1.1/ElQadre.apk" }
                    val jsonContent = """
                        {
                          "product": "ElQadre",
                          "versionCode": $finalCode,
                          "versionName": "$finalName",
                          "apkUrl": "$finalUrl",
                          "releaseDate": "$currentDate",
                          "notes": "${targetNotes.replace("\"", "\\\"")}"
                        }
                    """.trimIndent()

                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(jsonContent.toByteArray())
                    }
                    Toast.makeText(context, "Archivo version.json guardado (versionCode: $finalCode)", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Actualización de la aplicación",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = ElQadreNavy
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "Producto: ElQadre", fontSize = 14.sp)
                Text(text = "Versión instalada actual: $versionName (code $versionCode)", fontSize = 13.sp, color = Slate700)
            }

            HorizontalDivider(color = Slate200)

            Text(
                text = "Parámetros para generar version.json:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = ElQadreNavy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = targetVersionCode,
                    onValueChange = { targetVersionCode = it.filter { ch -> ch.isDigit() } },
                    label = { Text("versionCode", fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val current = targetVersionCode.toLongOrNull() ?: versionCode
                        targetVersionCode = (current + 1).toString()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("+1 CODE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = targetVersionName,
                onValueChange = { targetVersionName = it },
                label = { Text("versionName (ej: 1.5.5 o 1.6)", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = targetApkUrl,
                onValueChange = { targetApkUrl = it },
                label = { Text("URL de descarga directa del APK", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = {
                    launcher.launch("version.json")
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold, contentColor = ElQadreNavy),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("GENERAR VERSION.JSON", fontWeight = FontWeight.Bold)
            }
        }
    }
}
