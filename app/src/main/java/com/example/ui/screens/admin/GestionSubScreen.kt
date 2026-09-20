package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestionSubScreen(
    uiState: MainUiState,
    viewModel: MainViewModel
) {
    val scrollState = rememberScrollState()

    // 1. Datos del negocio (Draft State)
    var nombreNegocio by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf("") }
    val codigoNegocio = uiState.businessConfig?.codigoNegocio ?: "NEG-000001"

    // 2. Configuración general (Draft State)
    var moneda by remember { mutableStateOf("CUP") }
    var metodosPagoList by remember { mutableStateOf(listOf("Efectivo")) }
    var tasaUsdText by remember { mutableStateOf("") }
    var tasaEurText by remember { mutableStateOf("") }

    // 3. Configuración de jornada (Draft State)
    var aperturaAutomatica by remember { mutableStateOf(false) }
    var notasObligatoriasCierre by remember { mutableStateOf(true) }

    // 4. Configuración de caja (Draft State)
    var denominacionesList by remember { mutableStateOf(listOf(1000, 500, 200, 100, 50, 20, 10)) }
    var nuevaDenominacionText by remember { mutableStateOf("") }

    // Synchronize draft states when DB loaded
    LaunchedEffect(uiState.businessConfig) {
        uiState.businessConfig?.let { config ->
            nombreNegocio = config.nombreNegocio
            direccion = config.direccion
            telefono = config.telefono
            logoPath = config.logoPath ?: ""
        }
    }

    LaunchedEffect(uiState.generalConfig) {
        uiState.generalConfig?.let { config ->
            moneda = config.moneda
            metodosPagoList = config.metodosPago.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            tasaUsdText = if (config.tasaUsd > 0) config.tasaUsd.toString() else ""
            tasaEurText = if (config.tasaEur > 0) config.tasaEur.toString() else ""
            
            val params = config.parametrosJornada.split(",").associate {
                val parts = it.split(":")
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
            }
            aperturaAutomatica = params["Apertura automatica"] == "verdadero"
            notasObligatoriasCierre = params["Obligatorio notas de cierre"] != "falso"

            denominacionesList = config.denominacionesCaja.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .sortedDescending()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "GESTIÓN DEL NEGOCIO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy,
                modifier = Modifier.testTag("gestion_title")
            )
            Text(
                text = "Configuración general del establecimiento",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate600
            )
        }

        // Banners/Feedback Messages
        if (uiState.successMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Emerald50),
                border = BorderStroke(1.dp, Emerald600),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Éxito", tint = Emerald600)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.successMessage,
                        color = Emerald600,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { viewModel.clearMessages() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Emerald600, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 1. DATOS DEL NEGOCIO & IDENTIDAD INTERNA
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = ElQadreSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = ElQadreGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Datos del negocio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = ElQadreBorderLight)

                // Logo Visualizer + Internal Identifier
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ElQadreGoldSoft, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Styled Logo Circle
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(ElQadreGold),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restaurant,
                            contentDescription = "Restaurante",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (nombreNegocio.isNotBlank()) nombreNegocio else "Mi Establecimiento",
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Identidad Interna: ",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElQadreNavy)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = codigoNegocio,
                                    color = ElQadreGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Input Fields
                OutlinedTextField(
                    value = nombreNegocio,
                    onValueChange = { nombreNegocio = it },
                    label = { Text("Nombre del Establecimiento") },
                    placeholder = { Text("Ej. Cafetería La Plaza") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        focusedLabelColor = ElQadreNavy
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_nombre_negocio")
                )

                OutlinedTextField(
                    value = direccion,
                    onValueChange = { direccion = it },
                    label = { Text("Dirección Física") },
                    placeholder = { Text("Ej. Calle Obispo #254, Habana Vieja") },
                    singleLine = false,
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        focusedLabelColor = ElQadreNavy
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_direccion_negocio")
                )

                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Teléfono de Contacto") },
                    placeholder = { Text("Ej. +53 51234567") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        focusedLabelColor = ElQadreNavy
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_telefono_negocio")
                )

                OutlinedTextField(
                    value = logoPath,
                    onValueChange = { logoPath = it },
                    label = { Text("Ruta del Logo (Local Storage)") },
                    placeholder = { Text("Ej. /internal/storage/elqadre/logo.png") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        unfocusedBorderColor = ElQadreBorder,
                        focusedLabelColor = ElQadreNavy
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_logo_negocio")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val finalLogoPath = if (logoPath.isBlank()) null else logoPath
                        viewModel.updateBusinessConfig(
                            ConfiguracionNegocio(
                                id = 1,
                                nombreNegocio = nombreNegocio.ifBlank { "Pizzas Factory" },
                                direccion = direccion,
                                telefono = telefono,
                                logoPath = finalLogoPath,
                                codigoNegocio = codigoNegocio,
                                fechaCreacion = uiState.businessConfig?.fechaCreacion ?: System.currentTimeMillis(),
                                fechaActualizacion = System.currentTimeMillis()
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_business_data"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Datos", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 2. CONFIGURACIÓN GENERAL (MONEDA, MÉTODOS DE PAGO)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = ElQadreSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Payments, contentDescription = null, tint = ElQadreGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configuración general",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = ElQadreBorderLight)

                // Currency selector (CUP, USD, MLC, EUR)
                Text("Moneda del sistema", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CUP", "USD", "MLC", "EUR").forEach { cur ->
                        val isSelected = currencyMatch(moneda, cur)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ElQadreNavy else ElQadreBgSecondary)
                                .clickable { moneda = cur }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cur,
                                color = if (isSelected) ElQadreGold else Slate600,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tasas de cambio (Base: CUP)
                Text("Tasas de Cambio de Divisas (Base: CUP)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tasaUsdText,
                        onValueChange = { tasaUsdText = it },
                        label = { Text("1 USD en CUP") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("tasa_usd_input")
                    )
                    OutlinedTextField(
                        value = tasaEurText,
                        onValueChange = { tasaEurText = it },
                        label = { Text("1 EUR en CUP") },
                        placeholder = { Text("0.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("tasa_eur_input")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Payment Methods
                Text("Métodos de pago autorizados", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ElQadreNavy)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val availableMethods = listOf("Efectivo", "Transferencia", "Pago Móvil", "Tarjeta")
                    availableMethods.forEach { method ->
                        val isChecked = metodosPagoList.contains(method)
                        val isMandatory = method == "Efectivo" // Cash is mandatory initially

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !isMandatory) {
                                    if (isChecked) {
                                        metodosPagoList = metodosPagoList - method
                                    } else {
                                        metodosPagoList = metodosPagoList + method
                                    }
                                },
                            color = ElQadreBgSecondary,
                            border = BorderStroke(1.dp, if (isChecked) ElQadreGold else Color.Transparent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when(method) {
                                            "Efectivo" -> Icons.Default.AttachMoney
                                            "Transferencia" -> Icons.Default.SyncAlt
                                            "Pago Móvil" -> Icons.Default.PhonelinkRing
                                            else -> Icons.Default.CreditCard
                                        },
                                        contentDescription = null,
                                        tint = if (isChecked) ElQadreNavy else Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = method + if (isMandatory) " (Requerido)" else "",
                                        fontSize = 13.sp,
                                        color = if (isChecked) ElQadreNavy else Slate600,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (!isMandatory) {
                                            metodosPagoList = if (checked) {
                                                metodosPagoList + method
                                            } else {
                                                metodosPagoList - method
                                            }
                                        }
                                    },
                                    enabled = !isMandatory,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = ElQadreNavy,
                                        checkmarkColor = ElQadreGold
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val metodosPagoString = if (metodosPagoList.isEmpty()) "Efectivo" else metodosPagoList.joinToString(",")
                        val currentParams = "Apertura automatica:${if (aperturaAutomatica) "verdadero" else "falso"},Obligatorio notas de cierre:${if (notasObligatoriasCierre) "verdadero" else "falso"}"
                        val denominacionesString = denominacionesList.joinToString(",")

                        val baseConfig = uiState.generalConfig ?: ConfiguracionGeneral()
                        viewModel.updateGeneralConfig(
                            baseConfig.copy(
                                moneda = moneda,
                                metodosPago = metodosPagoString,
                                parametrosJornada = currentParams,
                                denominacionesCaja = denominacionesString,
                                tasaUsd = tasaUsdText.toDoubleOrNull() ?: 0.0,
                                tasaEur = tasaEurText.toDoubleOrNull() ?: 0.0
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_general_config"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Configuración", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 3. CONFIGURACIÓN DE JORNADA
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = ElQadreSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = ElQadreGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configuración de jornada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = ElQadreBorderLight)

                Text(
                    text = "Reglas operativas para la apertura y el cierre diario.",
                    fontSize = 12.sp,
                    color = Slate600
                )

                // Rule 1
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElQadreBgSecondary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Apertura automática de jornadas",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Iniciar el día de forma automatizada al iniciar sesión",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                    Switch(
                        checked = aperturaAutomatica,
                        onCheckedChange = { aperturaAutomatica = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElQadreGold,
                            checkedTrackColor = ElQadreNavy
                        )
                    )
                }

                // Rule 2
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElQadreBgSecondary)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notas obligatorias al cerrar caja",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Exigir comentarios al cajero si se registran descuadres",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                    Switch(
                        checked = notasObligatoriasCierre,
                        onCheckedChange = { notasObligatoriasCierre = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ElQadreGold,
                            checkedTrackColor = ElQadreNavy
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val metodosPagoString = if (metodosPagoList.isEmpty()) "Efectivo" else metodosPagoList.joinToString(",")
                        val currentParams = "Apertura automatica:${if (aperturaAutomatica) "verdadero" else "falso"},Obligatorio notas de cierre:${if (notasObligatoriasCierre) "verdadero" else "falso"}"
                        val denominacionesString = denominacionesList.joinToString(",")

                        val baseConfig = uiState.generalConfig ?: ConfiguracionGeneral()
                        viewModel.updateGeneralConfig(
                            baseConfig.copy(
                                moneda = moneda,
                                metodosPago = metodosPagoString,
                                parametrosJornada = currentParams,
                                denominacionesCaja = denominacionesString
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_jornada_config"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Reglas de Jornada", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 4. CONFIGURACIÓN DE CAJA (DENOMINACIONES)
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = ElQadreSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PriceChange, contentDescription = null, tint = ElQadreGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configuración de caja",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = ElQadreBorderLight)

                Text(
                    text = "Billetes y monedas aceptados para cuadres de caja.",
                    fontSize = 12.sp,
                    color = Slate600
                )

                // Denominations grid/chips flow
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    denominacionesList.forEach { den ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ElQadreBgSecondary,
                            border = BorderStroke(1.dp, ElQadreBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$den $moneda",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Eliminar denominación",
                                    tint = Rose600,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            denominacionesList = (denominacionesList - den).sortedDescending()
                                        }
                                )
                            }
                        }
                    }
                }

                // Add custom denomination
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = nuevaDenominacionText,
                        onValueChange = { nuevaDenominacionText = it },
                        label = { Text("Nueva Denominación ($moneda)") },
                        placeholder = { Text("Ej. 2000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_new_denomination")
                    )

                    Button(
                        onClick = {
                            val newValue = nuevaDenominacionText.trim().toIntOrNull()
                            if (newValue != null && newValue > 0 && !denominacionesList.contains(newValue)) {
                                denominacionesList = (denominacionesList + newValue).sortedDescending()
                                nuevaDenominacionText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold, contentColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("add_denomination_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val metodosPagoString = if (metodosPagoList.isEmpty()) "Efectivo" else metodosPagoList.joinToString(",")
                        val currentParams = "Apertura automatica:${if (aperturaAutomatica) "verdadero" else "falso"},Obligatorio notas de cierre:${if (notasObligatoriasCierre) "verdadero" else "falso"}"
                        val denominacionesString = denominacionesList.joinToString(",")

                        val baseConfig = uiState.generalConfig ?: ConfiguracionGeneral()
                        viewModel.updateGeneralConfig(
                            baseConfig.copy(
                                moneda = moneda,
                                metodosPago = metodosPagoString,
                                parametrosJornada = currentParams,
                                denominacionesCaja = denominacionesString
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_caja_config"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Denominaciones", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // 5. INFORMACIÓN DEL DISPOSITIVO
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ElQadreBgSecondary),
            border = BorderStroke(1.dp, ElQadreBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Slate600, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Información del dispositivo",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = ElQadreBorder)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DeviceInfoRow(label = "Identificador de terminal", value = uiState.deviceId)
                    DeviceInfoRow(label = "Versión de la aplicación", value = "1.4.0")
                    DeviceInfoRow(label = "Versión del esquema local", value = "6 (SQLite)")
                    DeviceInfoRow(label = "Estado de almacenamiento", value = "SQLite Room (elqadre_local.db)")
                    DeviceInfoRow(label = "Integridad del terminal", value = "Sincronizado / 100% Offline")
                }
            }
        }
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = Slate600)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
    }
}

fun currencyMatch(selected: String, item: String): Boolean {
    return selected.trim().equals(item.trim(), ignoreCase = true)
}
