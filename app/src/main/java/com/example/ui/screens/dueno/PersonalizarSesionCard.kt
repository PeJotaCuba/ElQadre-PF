package com.example.ui.screens.dueno

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
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
import com.example.util.DuenoSessionPreferences

@Composable
fun PersonalizarSesionCard(
    username: String,
    fullName: String,
    visibleModules: Set<String>,
    onVisibleModulesChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOwnerUsername = username.ifBlank { "dueno" }
    val currentOwnerFullName = fullName.ifBlank { "Dueño" }

    Box(modifier = modifier) {
        SettingsSectionCard(
            title = "PERSONALIZAR MI SESIÓN",
            icon = Icons.Outlined.Tune
        ) {
        Text(
            text = "Active o desactive individualmente los módulos que desea visualizar habitualmente en su interfaz de Dueño.",
            fontSize = 12.sp,
            color = Slate600
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Configuración personal para: $currentOwnerFullName ($currentOwnerUsername)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ElQadreGoldDark
        )

        Spacer(modifier = Modifier.height(12.dp))

        val availableModules = remember {
            listOf(
                Triple("PRODUCCION", "Producción (Inventario)", "Control de materias primas, recetas y tandas de elaboración"),
                Triple("MERCADERIAS", "Mercaderías (Inventario)", "Control de existencias de bebidas, confiterías y reventa"),
                Triple("CATALOGO", "Catálogo", "Productos, categorías y precios de venta"),
                Triple("INVERSIONES", "Inversiones", "Control de inversiones y activos"),
                Triple("GASTOS", "Gastos Corrientes", "Gastos corrientes y costos operativos"),
                Triple("CONTROL_NEGOCIO", "Control del Negocio", "Ventas, resultados y estadísticas"),
                Triple("PERSONAL", "Personal", "Trabajadores, nómina y usuarios"),
                Triple("AJUSTES", "Ajustes", "Información del negocio, tasas y respaldos")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val newSet = availableModules.map { it.first }.toSet()
                    DuenoSessionPreferences.setVisibleModules(context, currentOwnerUsername, newSet)
                    onVisibleModulesChanged(newSet)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ElQadreNavy),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("btn_seleccionar_todos_modulos")
            ) {
                Text("SELECCIONAR TODOS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
            }

            OutlinedButton(
                onClick = {
                    val newSet = setOf("AJUSTES")
                    DuenoSessionPreferences.setVisibleModules(context, currentOwnerUsername, newSet)
                    onVisibleModulesChanged(newSet)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate300),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("btn_desseleccionar_modulos")
            ) {
                Text("SOLO AJUSTES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            availableModules.forEach { moduleItem ->
                val (modKey, modTitle, modDesc) = moduleItem
                val isChecked = DuenoSessionPreferences.isModuleVisible(visibleModules, modKey)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isChecked) Color.White else Slate50,
                    border = BorderStroke(1.dp, if (isChecked) ElQadreBorder else Slate200),
                    shadowElevation = if (isChecked) 1.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isChecked) ElQadreNavy else Slate500
                            )
                            Text(
                                text = modDesc,
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }

                        Switch(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val mutableSet = visibleModules.toMutableSet()
                                if (checked) {
                                    mutableSet.add(modKey)
                                } else {
                                    mutableSet.remove(modKey)
                                    mutableSet.remove("INVENTARIO")
                                }
                                DuenoSessionPreferences.setVisibleModules(context, currentOwnerUsername, mutableSet)
                                onVisibleModulesChanged(mutableSet)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ElQadreNavy,
                                checkedBorderColor = ElQadreNavy,
                                uncheckedThumbColor = Slate400,
                                uncheckedTrackColor = Slate200,
                                uncheckedBorderColor = Slate300
                            ),
                            modifier = Modifier.testTag("switch_modulo_${modKey.lowercase()}")
                        )
                    }
                }
            }
        }
    }
}
}
