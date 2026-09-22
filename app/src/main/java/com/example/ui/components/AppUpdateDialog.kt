package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700

/**
 * Diálogo compartido y reutilizable para la confirmación y descarga de actualizaciones de APK.
 */
@Composable
fun AppUpdateDialog(
    show: Boolean,
    installedVersionName: String,
    installedVersionCode: Long,
    remoteVersionName: String,
    remoteVersionCode: Long,
    notes: String = "",
    isDownloading: Boolean,
    downloadProgress: Float,
    onConfirmUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = ElQadreNavy,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Hay una nueva actualización disponible",
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Existe una nueva versión de ElQadre disponible para descargar e instalar.",
                    fontSize = 14.sp,
                    color = Slate700,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Versión instalada:", fontSize = 12.sp, color = Slate500)
                            Text(
                                if (installedVersionName.isNotBlank()) "$installedVersionName (código $installedVersionCode)" else "Código $installedVersionCode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Nueva versión:", fontSize = 12.sp, color = Slate500)
                            Text(
                                if (remoteVersionName.isNotBlank()) "$remoteVersionName (código $remoteVersionCode)" else "Código $remoteVersionCode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                        }
                    }
                }

                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Novedades: $notes",
                        fontSize = 12.sp,
                        color = Slate500,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = ElQadreNavy,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (downloadProgress > 0f) "Descargando actualización... ${(downloadProgress * 100).toInt()}%" else "Iniciando descarga...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "¿Desea descargar e instalar la actualización ahora?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElQadreNavy,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onConfirmUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ACTUALIZAR", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = Slate700, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
