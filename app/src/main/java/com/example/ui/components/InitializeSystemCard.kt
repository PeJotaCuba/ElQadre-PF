package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import kotlinx.coroutines.launch

@Composable
fun InitializeSystemCard(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFEF4444)),
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
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "INICIALIZAR SISTEMA",
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        color = Color(0xFFDC2626),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Borrar datos operativos y comenzar de cero",
                        fontSize = 13.sp,
                        color = Slate600,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(color = Slate200, thickness = 1.dp)

            Text(
                text = "⚠️ ADVERTENCIA MUY IMPORTANTE:",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFDC2626)
            )

            Text(
                text = "Esta acción borrará todas las jornadas, comandas, cobros, transferencias, movimientos de inventario y producciones del dispositivo.\n\n" +
                       "EL PROCESO ES IRREVERSIBLE. Sin embargo, su configuración, catálogo de productos, nóminas de personal e inversiones se conservarán intactos.",
                fontSize = 14.sp,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            )
            
            Text(
                text = "Se descargará un archivo de respaldo antes de realizar la inicialización.",
                fontSize = 13.sp,
                color = Color(0xFF0F766E),
                fontWeight = FontWeight.Bold
            )

            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SÍ, INICIALIZAR EL SISTEMA", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }

    if (showConfirmDialog) {
        var confirmText by remember { mutableStateOf("") }
        val isConfirmed = confirmText.trim().equals("BORRAR", ignoreCase = true)

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(28.dp))
                    Text("¡ÚLTIMA CONFIRMACIÓN!", fontWeight = FontWeight.Black, color = Color(0xFFDC2626), fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Para evitar un borrado por accidente, por favor escriba la palabra BORRAR en la casilla de abajo para activar el botón de inicialización:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Escriba BORRAR aquí") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFDC2626),
                            cursorColor = Color(0xFFDC2626)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isConfirmed) {
                            showConfirmDialog = false
                            viewModel.initializeSystem(context) {
                                viewModel.logout()
                            }
                        }
                    },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        disabledContainerColor = Slate200
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        "ELIMINAR DATOS OPERATIVOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = if (isConfirmed) Color.White else Slate500
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("CANCELAR", fontWeight = FontWeight.Bold, color = Slate600)
                }
            },
            containerColor = Color.White
        )
    }
}
