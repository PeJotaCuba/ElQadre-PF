package com.example.ui.screens.barra

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.BarraBackupManager

@Composable
fun BarraAjustesTab(
    uiState: MainUiState,
    viewModel: MainViewModel,
    computedBarraItems: List<BarraProductItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser = uiState.currentUser
    val activeJornada = uiState.activeJornada

    val montoPorBebida = remember(activeJornada, currentUser) {
        if ((activeJornada?.utilidadSalonMontoUnitario ?: 0.0) > 0.0) {
            activeJornada!!.utilidadSalonMontoUnitario
        } else {
            currentUser?.montoPorProducto ?: 0.0
        }
    }

    var showRestoreModal by remember { mutableStateOf(false) }
    var restoreJsonInput by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Column {
                Text(
                    text = "Ajustes de Barra",
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    fontSize = 18.sp
                )
                Text(
                    text = "Configuración del dependiente, copia de seguridad y restauración",
                    color = Slate500,
                    fontSize = 12.sp
                )
            }
        }

        // Versión de la Aplicación y Buscar Actualizaciones
        item {
            com.example.ui.components.AppVersionSettingsCard()
        }

        // 1. Configuración del Dependiente

        // 1. Sincronización y URLs
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudSync,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "SINCRONIZACIÓN Y URLs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Direcciones de actualización configuradas en el sistema",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    // URLs list
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text("URL Catálogo (qcatalogo.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlCatalogoJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlCatalogoJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                        Column {
                            Text("URL Inventario (qmercainv.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlMercainvJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlMercainvJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                        Column {
                            Text("URL Usuarios (qusuarios.json)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                            Text(
                                text = uiState.generalConfig?.urlUsuariosJson?.ifBlank { "No configurada" } ?: "No configurada",
                                fontSize = 12.sp,
                                color = if (uiState.generalConfig?.urlUsuariosJson.isNullOrBlank()) Slate400 else ElQadreNavy
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateCatalogo() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { viewModel.updateMercainv() },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Teléfono de Caja / Comandas
        item {
            val cajeroInfo = remember(uiState.users, uiState.activeJornada, uiState.generalConfig) {
                com.example.util.CajaPhoneHelper.resolveCajeroInfo(
                    uiState.users,
                    uiState.activeJornada,
                    uiState.generalConfig
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (cajeroInfo.isConfigured) Emerald200 else Amber200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().testTag("card_telefono_caja_barra")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (cajeroInfo.isConfigured) Color(0xFFD1FAE5) else Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (cajeroInfo.isConfigured) Emerald700 else Color(0xFFD97706),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "TELÉFONO DE CAJA / COMANDAS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Destinatario automático según qusuarios.json",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cajero Habilitado:", fontSize = 12.sp, color = Slate600)
                            Text(
                                text = cajeroInfo.cajeroUser?.let { "${it.fullName} (@${it.username})" } ?: "Sin usuario Cajero activo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cajeroInfo.cajeroUser != null) ElQadreNavy else Slate400
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Número de Teléfono:", fontSize = 12.sp, color = Slate600)
                            if (cajeroInfo.isConfigured) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFD1FAE5)
                                ) {
                                    Text(
                                        text = cajeroInfo.phoneNumber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Emerald800
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFFEE2E2)
                                ) {
                                    Text(
                                        text = "Caja no tiene teléfono configurado",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Solo lectura. Este número se administra exclusivamente desde qusuarios.json.",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
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
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "CONFIGURACIÓN DEL DEPENDIENTE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Parámetros y perfil asignado por la administración",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dependiente:", fontSize = 12.sp, color = Slate600)
                        Text(
                            text = currentUser?.fullName?.ifBlank { currentUser.username } ?: "Dependiente de Barra",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Usuario / Rol:", fontSize = 12.sp, color = Slate600)
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFEFF6FF)) {
                            Text(
                                text = "${currentUser?.username ?: "barra1"} (DEPENDIENTE DE BARRA)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tarifa de Utilidad por Bebida:", fontSize = 12.sp, color = Slate600)
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFD1FAE5)) {
                            Text(
                                text = "$${"%.2f".format(montoPorBebida)} CUP / ud.",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Categorías Operadas:", fontSize = 12.sp, color = Slate600)
                        Text("BEBIDAS & CONFITERÍAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Estado de Terminal:", fontSize = 12.sp, color = Slate600)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(Emerald500, shape = RoundedCornerShape(4.dp)))
                            Text("Activo y Sincronizado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                        }
                    }
                }
            }
        }

        // 2. Aceptar Transferencias para Mercaderías
        item {
            val prefs = remember { context.getSharedPreferences("SessionPrefs", android.content.Context.MODE_PRIVATE) }
            var aceptarTransferencias by remember {
                mutableStateOf(prefs.getBoolean("barra_aceptar_transferencias", true))
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, if (aceptarTransferencias) Emerald200 else Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
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
                            color = if (aceptarTransferencias) Color(0xFFD1FAE5) else Color(0xFFF1F5F9),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalance,
                                    contentDescription = null,
                                    tint = if (aceptarTransferencias) Emerald700 else Slate500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACEPTAR TRANSFERENCIAS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Cobro de productos de mercadería en barra",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = if (aceptarTransferencias) "Transferencias Habilitadas" else "Transferencias Deshabilitadas",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (aceptarTransferencias) Emerald700 else Slate700
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (aceptarTransferencias) 
                                    "Se ofrece la opción de pago por transferencia al cobrar comandas de barra."
                                else 
                                    "No se ofrece la opción de transferencia al cobrar comandas de barra.",
                                fontSize = 11.sp,
                                color = Slate500,
                                lineHeight = 15.sp
                            )
                        }

                        Switch(
                            checked = aceptarTransferencias,
                            onCheckedChange = { isChecked ->
                                aceptarTransferencias = isChecked
                                prefs.edit().putBoolean("barra_aceptar_transferencias", isChecked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Emerald600,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Slate300
                            ),
                            modifier = Modifier.testTag("switch_aceptar_transferencias")
                        )
                    }
                }
            }
        }

        // 2. Respaldo de Datos (qdepbarra.json)

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
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
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Backup,
                                    contentDescription = null,
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "RESPALDO DE DATOS (qdepbarra.json)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Exporta la jornada activa, inventario, entradas, comandas y archivo",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Text(
                        text = "El archivo qdepbarra.json contiene la totalidad del estado operativo necesario para reconstruir la terminal de Barra sin pérdida de datos.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    Button(
                        onClick = {
                            val jsonStr = BarraBackupManager.createBackupJson(uiState, computedBarraItems)
                            val backupFile = BarraBackupManager.exportBackupFile(context, jsonStr)
                            if (backupFile != null) {
                                Toast.makeText(context, "Respaldo qdepbarra.json creado con éxito", Toast.LENGTH_SHORT).show()
                                BarraBackupManager.shareBackupFile(context, backupFile)
                            } else {
                                Toast.makeText(context, "Error al generar archivo qdepbarra.json", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_respaldar_datos_barra"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESPALDAR DATOS (qdepbarra.json)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 3. Restauración de Datos

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Slate200),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
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
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Restore,
                                    contentDescription = null,
                                    tint = Amber800,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "RESTAURACIÓN DE DATOS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Recupera el estado del panel a partir de qdepbarra.json",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    HorizontalDivider(color = Slate100)

                    Text(
                        text = "Restaura jornadas, archivo histórico, inventario, entradas, comandas, cobros y utilidades calculadas.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedButton(
                        onClick = {
                            restoreJsonInput = ""
                            showRestoreModal = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_restaurar_datos_barra"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber800),
                        border = BorderStroke(1.dp, Amber800)
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RESTAURAR DATOS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Inicializar Sistema
            com.example.ui.components.InitializeSystemCard(viewModel = viewModel)
        }
    }

    // Modal: Restaurar Datos con Advertencia y Confirmación Explícita
    if (showRestoreModal) {
        AlertDialog(
            onDismissRequest = { if (!isRestoring) showRestoreModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFD97706))
                    Text("Restaurar Datos (qdepbarra.json)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "⚠️ ADVERTENCIA CRÍTICA:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Rose700
                            )
                            Text(
                                text = "Los datos actuales de inventario de barra, entradas, comandas, historial, jornadas y archivo serán reemplazados por el contenido exacto del respaldo.",
                                fontSize = 11.sp,
                                color = Rose600
                            )
                        }
                    }

                    Text(
                        text = "Pega a continuación el contenido de tu archivo qdepbarra.json:",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = restoreJsonInput,
                        onValueChange = { restoreJsonInput = it },
                        placeholder = { Text("{\"appIdentifier\": \"QDEPBARRA_BACKUP_V1\", ...}", fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .testTag("input_restore_json_barra"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (restoreJsonInput.isNotBlank()) {
                            isRestoring = true
                            viewModel.restoreBarraBackupJson(
                                jsonString = restoreJsonInput,
                                onComplete = { success ->
                                    isRestoring = false
                                    if (success) {
                                        showRestoreModal = false
                                        Toast.makeText(context, "Respaldo qdepbarra.json restaurado exitosamente.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }
                    },
                    enabled = restoreJsonInput.isNotBlank() && !isRestoring,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.testTag("btn_confirm_restore_barra")
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("CONFIRMAR Y RESTAURAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreModal = false },
                    enabled = !isRestoring
                ) {
                    Text("CANCELAR", color = Slate600, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        )
    }
}
