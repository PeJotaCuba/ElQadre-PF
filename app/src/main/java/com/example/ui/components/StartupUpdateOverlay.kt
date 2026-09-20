package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElQadreGoldDark
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.util.ApkUpdateManager

/**
 * Estados del flujo de comprobación automática de actualizaciones de APK al iniciar ElQadre.
 */
sealed class StartupUpdateState {
    object Idle : StartupUpdateState()

    object Checking : StartupUpdateState()

    data class NoUpdate(
        val localVersionName: String,
        val localVersionCode: Long
    ) : StartupUpdateState()

    data class ConfirmationRequired(
        val remoteInfo: ApkUpdateManager.RemoteVersionInfo,
        val localVersionCode: Long,
        val localVersionName: String
    ) : StartupUpdateState()

    data class Downloading(
        val remoteInfo: ApkUpdateManager.RemoteVersionInfo,
        val localVersionCode: Long,
        val localVersionName: String,
        val progress: Float,
        val statusMessage: String
    ) : StartupUpdateState()

    data class Error(
        val message: String
    ) : StartupUpdateState()
}

/**
 * Cartel/Superposición para la verificación y descarga automática de actualizaciones al abrir la app.
 */
@Composable
fun StartupUpdateOverlay(
    state: StartupUpdateState,
    onConfirmUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onDismissError: () -> Unit = {}
) {
    if (state is StartupUpdateState.Idle) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(enabled = false) {}, // Bloquea toques de fondo mientras opera
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (state) {
                    is StartupUpdateState.Checking -> {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "BUSCANDO ACTUALIZACIONES...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Comprobando si existe una nueva versión de ElQadre...",
                            fontSize = 13.sp,
                            color = Slate500,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        CircularProgressIndicator(
                            color = ElQadreNavy,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                    }

                    is StartupUpdateState.NoUpdate -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NO HAY ACTUALIZACIONES DISPONIBLES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Está utilizando la versión más reciente de ElQadre.",
                            fontSize = 13.sp,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                        if (state.localVersionName.isNotBlank() || state.localVersionCode > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Versión: ${state.localVersionName.ifBlank { "Instalada" }} (código ${state.localVersionCode})",
                                fontSize = 12.sp,
                                color = Slate500,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    is StartupUpdateState.ConfirmationRequired -> {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ElQadreGoldDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "NUEVA ACTUALIZACIÓN DISPONIBLE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Se ha detectado una nueva versión de ElQadre lista para instalar.",
                            fontSize = 13.sp,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Versión instalada:", fontSize = 12.sp, color = Slate500)
                                    Text(
                                        text = "${state.localVersionName} (${state.localVersionCode})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate700
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Nueva versión:", fontSize = 12.sp, color = Slate500)
                                    Text(
                                        text = "${state.remoteInfo.versionName.ifBlank { "v${state.remoteInfo.versionCode}" }} (${state.remoteInfo.versionCode})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "¿Desea descargar e instalar la actualización ahora?",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismissUpdate,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("MÁS TARDE", color = Slate700, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = onConfirmUpdate,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("ACTUALIZAR AHORA", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    is StartupUpdateState.Downloading -> {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "DESCARGANDO ACTUALIZACIÓN",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Descargando versión:", fontSize = 12.sp, color = Slate500)
                                    Text(
                                        text = "${state.remoteInfo.versionName} (${state.remoteInfo.versionCode})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = ElQadreNavy,
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.statusMessage,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                    }

                    is StartupUpdateState.Error -> {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Actualización de ElQadre",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismissError,
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Continuar", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    is StartupUpdateState.Idle -> {}
                }
            }
        }
    }
}
