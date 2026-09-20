import re

with open('app/src/main/java/com/example/ui/screens/inicio/InicioScreen.kt', 'r') as f:
    content = f.read()

state_vars = """    var showInicioGuiado by remember {
        mutableStateOf(!GuiadoPrefsManager.isInicioGuideShown(context))
    }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showAppUpdateDialog by remember { mutableStateOf(false) }
    var remoteVersionName by remember { mutableStateOf("") }
    var remoteApkUrl by remember { mutableStateOf("") }
    var updateDialogMessage by remember { mutableStateOf("") }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatus by remember { mutableStateOf("") }"""

content = content.replace('    var showInicioGuiado by remember {\n        mutableStateOf(!GuiadoPrefsManager.isInicioGuideShown(context))\n    }', state_vars)

old_onclick = """                            onClick = {
                                coroutineScope.launch {
                                    isCheckingActualizar = true
                                    val result = licenseManager.executeActualizarFull(context, dvc)
                                    isCheckingActualizar = false
                                    actualizarResult = result
                                }
                            },
                            enabled = !isCheckingActualizar,"""

new_onclick = """                            onClick = {
                                coroutineScope.launch {
                                    isCheckingActualizar = true
                                    val result = licenseManager.executeActualizarFull(context, dvc)
                                    isCheckingActualizar = false
                                    actualizarResult = result
                                    
                                    isCheckingUpdate = true
                                    try {
                                        val db = com.example.data.local.AppDatabase.getDatabase(context)
                                        val config = db.configuracionGeneralDao().getConfigSync()
                                        val versionJsonUrl = config?.urlVersionJson?.trim().orEmpty()
                                        if (versionJsonUrl.isNotBlank() && (versionJsonUrl.startsWith("http://") || versionJsonUrl.startsWith("https://"))) {
                                            val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                try {
                                                    val url = java.net.URL(versionJsonUrl)
                                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                                    conn.connectTimeout = 5000
                                                    conn.readTimeout = 5000
                                                    conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                                                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; ElQadrePOS)")
                                                    if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                                                        conn.inputStream.bufferedReader().use { it.readText() }
                                                    } else null
                                                } catch (e: Exception) {
                                                    null
                                                }
                                            }

                                            if (response != null) {
                                                val json = try {
                                                    org.json.JSONObject(response)
                                                } catch (e: Exception) {
                                                    null
                                                }
                                                
                                                if (json != null) {
                                                    val remoteVersionCode = json.optLong("versionCode", 0L)
                                                    val remoteName = json.optString("versionName", "")
                                                    val apkUrl = json.optString("apkUrl", "")

                                                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                                    val localVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                        packageInfo.longVersionCode
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        packageInfo.versionCode.toLong()
                                                    }
                                                    val localVersionName = packageInfo.versionName ?: ""

                                                    if (remoteVersionCode > localVersionCode) {
                                                        remoteVersionName = if (remoteName.isNotBlank()) remoteName else "v$remoteVersionCode"
                                                        remoteApkUrl = apkUrl
                                                        updateDialogMessage = "Versión instalada: $localVersionName\\nNueva versión: $remoteVersionName"
                                                        showAppUpdateDialog = true
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    } finally {
                                        isCheckingUpdate = false
                                    }
                                }
                            },
                            enabled = !isCheckingActualizar && !isCheckingUpdate,"""

content = content.replace(old_onclick, new_onclick)

old_loading = """                        ) {
                            if (isCheckingActualizar) {"""

new_loading = """                        ) {
                            if (isCheckingActualizar || isCheckingUpdate) {"""

content = content.replace(old_loading, new_loading)


update_dialog = """    if (showAppUpdateDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!isDownloadingUpdate) showAppUpdateDialog = false 
            },
            title = { Text("Actualización Disponible", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Column {
                    Text(updateDialogMessage, fontSize = 14.sp)
                    if (isDownloadingUpdate) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElQadreNavy,
                            trackColor = Slate200
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(downloadStatus, fontSize = 12.sp, color = Slate600)
                    }
                }
            },
            confirmButton = {
                if (!isDownloadingUpdate) {
                    Button(
                        onClick = {
                            isDownloadingUpdate = true
                            downloadProgress = 0f
                            downloadStatus = "Iniciando descarga..."
                            
                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val downloader = com.example.util.ApkDownloader(context)
                                val downloadedFile = downloader.downloadApk(
                                    apkUrl = remoteApkUrl,
                                    onProgress = { progress, status ->
                                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            downloadProgress = progress
                                            downloadStatus = status
                                        }
                                    }
                                )
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    isDownloadingUpdate = false
                                    if (downloadedFile != null && downloadedFile.exists()) {
                                        showAppUpdateDialog = false
                                        try {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                downloadedFile
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/vnd.android.package-archive")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "Error al abrir instalador: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Error al descargar la actualización", android.widget.Toast.LENGTH_LONG).show()
                                        showAppUpdateDialog = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                    ) {
                        Text("ACTUALIZAR AHORA")
                    }
                }
            },
            dismissButton = {
                if (!isDownloadingUpdate) {
                    TextButton(
                        onClick = { 
                            showAppUpdateDialog = false
                        }
                    ) {
                        Text("AHORA NO", color = Slate700)
                    }
                }
            }
        )
    }

"""

old_end = """    if (showInicioGuiado) {
        InicioGuiadoDialog(
            onDismiss = { showInicioGuiado = false }
        )
    }
}"""

content = content.replace(old_end, update_dialog + old_end)

with open('app/src/main/java/com/example/ui/screens/inicio/InicioScreen.kt', 'w') as f:
    f.write(content)
