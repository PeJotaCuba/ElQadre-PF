package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.model.UserRole
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import androidx.core.content.FileProvider
import java.io.File


/**
 * Official ELQADRE Geometric Logo Icon
 * Uses the official native icon (icono.png) directly.
 */
@Composable
fun ElQadreLogoIcon(
    size: Dp = 64.dp,
    badgeColor: Color = ElQadreNavy,
    frameColor: Color = Color.White,
    slashColor: Color = ElQadreGold,
    modifier: Modifier = Modifier
) {
    ElQadreEmblem(size = size, modifier = modifier)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoCuadraTopBar(
    uiState: MainUiState,
    onLogoutClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onWorkerNameClick: () -> Unit = {}
) {
    val isOperationalUser = uiState.currentUser != null

    val userInitials = when (uiState.currentUser?.role) {
        UserRole.DUENO -> "DN"
        UserRole.CAJERO -> "CJ"
        UserRole.SALON -> "SL"
        UserRole.BARRA -> "BR"
        else -> "EQ"
    }

    Surface(
        color = ElQadreNavy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Menu Icon + Business Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menú",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = uiState.businessName.ifBlank { "Restaurante El Buen Sabor" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "DVC-${uiState.deviceId.takeLast(6).uppercase()}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Slate300
                            )
                        }
                    }
                }

                // Right actions: Notifications Bell with Gold Badge + Update Icon + Logout
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isOperationalUser) {
                        IconButton(
                            onClick = onUpdateClick,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("update_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Actualizar",
                                tint = ElQadreGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Notification Bell with Yellow Counter Badge
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = ElQadreGold,
                                contentColor = ElQadreNavy,
                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                            ) {
                                Text("3", fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    ) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = "Notificaciones",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Logout Icon
                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("logout_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salir",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WorkerNameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = ElQadreNavy)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Identificación del Trabajador", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    "Indique el nombre con el que trabajará durante esta jornada en ELQADRE.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Trabajador") },
                    placeholder = { Text("Ej. Pedro, Carlos, Laura...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("worker_name_confirm_button")
            ) {
                Text("Comenzar Jornada")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun UpdateModalDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onGeneratePdfRequested: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = ElQadreNavy
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Actualizar Datos del Negocio",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Text("La funcionalidad de actualización mediante JSON se encuentra temporalmente deshabilitada en esta versión de la arquitectura.")
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun PdfShareDialog(
    pdfFile: File,
    onDismiss: () -> Unit,
    onDeliveredConfirmed: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald600)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reporte PDF Generado", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            pdfFile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = ElQadreNavy
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tamaño: ${pdfFile.length() / 1024} KB  •  Ruta local segura",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            pdfFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Enviar Reporte al Administrador"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_pdf_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir / Enviar PDF")
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        onDeliveredConfirmed()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_delivery_button"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald600)
                ) {
                    Icon(Icons.Default.DoneAll, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REPORTE ENTREGADO AL ADMINISTRADOR", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    accentColor: Color = ElQadreGold,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ElQadreBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Slate500
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = ElQadreNavy
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Slate500
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
