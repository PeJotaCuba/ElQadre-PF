package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ConfigurarPagosBebidasDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val currentTarifas = uiState.tarifasPagoBebidas

    var pagoDependienteStr by remember(currentTarifas.pagoDependientePorUnidad) {
        mutableStateOf(
            if (currentTarifas.pagoDependientePorUnidad > 0.0) currentTarifas.pagoDependientePorUnidad.toString() else ""
        )
    }

    var pagoCajeroStr by remember(currentTarifas.pagoCajeroPorUnidad) {
        mutableStateOf(
            if (currentTarifas.pagoCajeroPorUnidad > 0.0) currentTarifas.pagoCajeroPorUnidad.toString() else ""
        )
    }

    val pagoDependienteVal = pagoDependienteStr.toDoubleOrNull() ?: 0.0
    val pagoCajeroVal = pagoCajeroStr.toDoubleOrNull() ?: 0.0
    val totalPagoPersonalVal = pagoDependienteVal + pagoCajeroVal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFEEF2FF),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Tarifas de Personal para Bebidas",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Configuración Global de Pagos (Mercaderías)",
                                fontSize = 12.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_config_pagos_bebidas")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Nota informativa / Banner de Reglas
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "REGLAS DE TARIFAS GLOBALES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }
                            Text(
                                text = "• Estas tarifas se aplican automáticamente a todas las Bebidas del módulo de Mercaderías.",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            Text(
                                text = "• Las Confituras NO tienen pagos de personal asociados (sin comisión de dependiente ni cajero).",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            Text(
                                text = "• En la ficha de cada bebida estos valores se mostrarán en modo informativo y de solo lectura.",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }
                    }

                    // Campo 1: Dependiente (pago por unidad vendida)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "DEPENDIENTE: Pago por unidad vendida",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "Monto asignado al Dependiente por cada bebida vendida",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = pagoDependienteStr,
                                onValueChange = { pagoDependienteStr = it },
                                label = { Text("Pago unitario ($ CUP / u)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_pago_dependiente_bebida"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0F766E),
                                    unfocusedBorderColor = Slate300
                                )
                            )
                        }
                    }

                    // Campo 2: Cajero (pago por unidad vendida)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PointOfSale,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "CAJERO: Pago por unidad vendida",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "Monto asignado al Cajero por cada bebida vendida",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = pagoCajeroStr,
                                onValueChange = { pagoCajeroStr = it },
                                label = { Text("Pago unitario ($ CUP / u)") },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_pago_cajero_bebida"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFB45309),
                                    unfocusedBorderColor = Slate300
                                )
                            )
                        }
                    }

                    // Tarjeta de Resumen y Cálculo por Unidad
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEEF2FF),
                        border = BorderStroke(1.5.dp, Color(0xFF818CF8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocalBar,
                                    contentDescription = null,
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "RESUMEN DE PAGO POR UNIDAD VENDIDA",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF312E81)
                                )
                            }

                            HorizontalDivider(color = Color(0xFFC7D2FE))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pago Dependiente:", fontSize = 12.sp, color = Slate700)
                                Text("$${"%.2f".format(pagoDependienteVal)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pago Cajero:", fontSize = 12.sp, color = Slate700)
                                Text("$${"%.2f".format(pagoCajeroVal)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }

                            HorizontalDivider(color = Color(0xFFC7D2FE))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PAGO TOTAL DE PERSONAL:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF312E81)
                                )
                                Text(
                                    text = "$${"%.2f".format(totalPagoPersonalVal)} CUP / u",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF4338CA)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_cancel_config_pagos_bebidas")
                    ) {
                        Text("CANCELAR", fontWeight = FontWeight.Bold, color = Slate700)
                    }

                    Button(
                        onClick = {
                            viewModel.updateTarifasPagoBebidas(
                                pagoDependiente = pagoDependienteVal,
                                pagoCajero = pagoCajeroVal
                            )
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("btn_save_config_pagos_bebidas")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GUARDAR TARIFAS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
