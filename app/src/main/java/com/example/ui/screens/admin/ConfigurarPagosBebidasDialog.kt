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

    var pagoTrabajadorModalidad by remember(currentTarifas.pagoDependienteModalidad) {
        mutableStateOf(currentTarifas.pagoDependienteModalidad)
    }
    var pagoTrabajadorValorStr by remember(currentTarifas.pagoDependienteValor) {
        mutableStateOf(
            if (currentTarifas.pagoDependienteValor > 0.0) currentTarifas.pagoDependienteValor.toString() else ""
        )
    }

    val pagoTrabajadorVal = pagoTrabajadorValorStr.toDoubleOrNull() ?: 0.0

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
                                text = "• Esta tarifa se aplica automáticamente a todas las Bebidas del módulo de Mercaderías tanto para el trabajador (Dependiente) como para el Cajero.",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            Text(
                                text = "• Las Confituras NO tienen pagos de personal asociados (sin comisión de dependiente ni cajero).",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            Text(
                                text = "• En la ficha de cada bebida estos valores se calcularán según la modalidad configurada.",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                        }
                    }

                    // Tarjeta Única: Trabajador - Pago por unidad vendida
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
                                        text = "TRABAJADOR - PAGO POR UNIDAD VENDIDA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = ElQadreNavy
                                    )
                                    Text(
                                        text = "Define el pago que corresponde tanto al Dependiente como al Cajero",
                                        fontSize = 11.sp,
                                        color = Slate500
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Modalidad Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { pagoTrabajadorModalidad = "FIJO" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (pagoTrabajadorModalidad == "FIJO") Color(0xFF0F766E) else Slate100,
                                        contentColor = if (pagoTrabajadorModalidad == "FIJO") Color.White else Slate700
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("btn_trabajador_fijo"),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("MONTO FIJO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { pagoTrabajadorModalidad = "PORCENTAJE" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (pagoTrabajadorModalidad == "PORCENTAJE") Color(0xFF0F766E) else Slate100,
                                        contentColor = if (pagoTrabajadorModalidad == "PORCENTAJE") Color.White else Slate700
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).height(36.dp).testTag("btn_trabajador_porcentaje"),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("PORCENTAJE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = pagoTrabajadorValorStr,
                                onValueChange = { pagoTrabajadorValorStr = it },
                                label = { 
                                    Text(
                                        if (pagoTrabajadorModalidad == "PORCENTAJE") "Porcentaje (% sobre precio de venta)" 
                                        else "Monto unitario ($ CUP / u)"
                                    ) 
                                },
                                placeholder = { Text(if (pagoTrabajadorModalidad == "PORCENTAJE") "e.g. 5.0" else "0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_pago_trabajador_bebida"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0F766E),
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
                                    text = "RESUMEN DE CONFIGURACIÓN DE PAGO",
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
                                val textDep = if (pagoTrabajadorModalidad == "PORCENTAJE") {
                                    "${"%.1f".format(pagoTrabajadorVal)}% del precio de venta"
                                } else {
                                    "$${"%.2f".format(pagoTrabajadorVal)} CUP"
                                }
                                Text(textDep, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F766E))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Pago Cajero:", fontSize = 12.sp, color = Slate700)
                                val textCaj = if (pagoTrabajadorModalidad == "PORCENTAJE") {
                                    "${"%.1f".format(pagoTrabajadorVal)}% del precio de venta"
                                } else {
                                    "$${"%.2f".format(pagoTrabajadorVal)} CUP"
                                }
                                Text(textCaj, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            }

                            HorizontalDivider(color = Color(0xFFC7D2FE))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MODALIDAD DE PAGO:",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF312E81)
                                )
                                val textTotal = if (pagoTrabajadorModalidad == "PORCENTAJE") {
                                    "${"%.1f".format(pagoTrabajadorVal)}% (Dep) + ${"%.1f".format(pagoTrabajadorVal)}% (Caj) del precio de venta"
                                } else {
                                    "$${"%.2f".format(pagoTrabajadorVal + pagoTrabajadorVal)} CUP / u"
                                }
                                Text(
                                    text = textTotal,
                                    fontSize = 15.sp,
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
                                pagoDependiente = if (pagoTrabajadorModalidad == "FIJO") pagoTrabajadorVal else 0.0,
                                pagoCajero = if (pagoTrabajadorModalidad == "FIJO") pagoTrabajadorVal else 0.0,
                                pagoDependienteModalidad = pagoTrabajadorModalidad,
                                pagoDependienteValor = pagoTrabajadorVal,
                                pagoCajeroModalidad = pagoTrabajadorModalidad,
                                pagoCajeroValor = pagoTrabajadorVal
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
