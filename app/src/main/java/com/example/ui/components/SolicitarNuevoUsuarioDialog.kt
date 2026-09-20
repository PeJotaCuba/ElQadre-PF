package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.UserRole
import com.example.licensing.SuperAdminSmsHelper
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolicitarNuevoUsuarioDialog(
    solicitanteNombre: String,
    businessName: String,
    deviceId: String,
    onDismiss: () -> Unit,
    onSmsSent: () -> Unit = {}
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.CAJERO) }
    var roleDropdownExpanded by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var montoPorProducto by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val rolesList = listOf(
        UserRole.DUENO,
        UserRole.CAJERO,
        UserRole.SALON,
        UserRole.BARRA
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreNavySoft
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PersonAdd,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(8.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "SOLICITAR NUEVO USUARIO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Solicitud SMS al Super Admin (${SuperAdminSmsHelper.SUPER_ADMIN_PHONE})",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Slate200)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Rose50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = Rose700,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Card with business context
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Negocio:", fontSize = 11.sp, color = Slate500)
                                Text(
                                    text = businessName.ifBlank { "ElQadre" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "DVC Dispositivo:", fontSize = 11.sp, color = Slate500)
                                Text(
                                    text = deviceId,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = ElQadreGoldDark
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Solicitante:", fontSize = 11.sp, color = Slate500)
                                Text(
                                    text = solicitanteNombre.ifBlank { "Dueño" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate700
                                )
                            }
                        }
                    }

                    // Full Name
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            errorMessage = null
                        },
                        label = { Text("Nombre y Apellidos *") },
                        placeholder = { Text("Ej. Juan Pérez") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Badge, contentDescription = null, tint = Slate400)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_solicitar_nombre")
                    )

                    // Username / Identifier
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.filter { char -> char.isLetterOrDigit() || char == '_' }
                            errorMessage = null
                        },
                        label = { Text("Usuario / Identificador *") },
                        placeholder = { Text("Ej. juan1, cajero2") },
                        leadingIcon = {
                            Icon(Icons.Outlined.AccountCircle, contentDescription = null, tint = Slate400)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_solicitar_username")
                    )

                    // Role Selector
                    ExposedDropdownMenuBox(
                        expanded = roleDropdownExpanded,
                        onExpandedChange = { roleDropdownExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedRole.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol Solicitado *") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = Slate400)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleDropdownExpanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreGold,
                                unfocusedBorderColor = ElQadreBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )

                        ExposedDropdownMenu(
                            expanded = roleDropdownExpanded,
                            onDismissRequest = { roleDropdownExpanded = false }
                        ) {
                            rolesList.forEach { role ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(role.displayName, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = when (role) {
                                                    UserRole.DUENO -> "Gestión integral del negocio, auditoría y reportes"
                                                    UserRole.CAJERO -> "Cobro, jornadas y comandas"
                                                    UserRole.SALON -> "Atención y comandas de salón"
                                                    UserRole.BARRA -> "Atención y comandas de barra"
                                                    else -> ""
                                                },
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedRole = role
                                        roleDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Phone / Mobile
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono / Móvil del Usuario (Opcional)") },
                        placeholder = { Text("Ej. 52123456") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Phone, contentDescription = null, tint = Slate400)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreGold,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Monto por Producto (if SALON or BARRA)
                    if (selectedRole == UserRole.SALON || selectedRole == UserRole.BARRA) {
                        OutlinedTextField(
                            value = montoPorProducto,
                            onValueChange = { montoPorProducto = it },
                            label = { Text("Monto por Producto ($)") },
                            placeholder = { Text("Ej. 5.00") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Payments, contentDescription = null, tint = Slate400)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreGold,
                                unfocusedBorderColor = ElQadreBorder
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "Al pulsar SOLICITAR, la aplicación generará automáticamente un SMS dirigido al Super Admin (${SuperAdminSmsHelper.SUPER_ADMIN_PHONE}). Una vez que el Super Admin apruebe el usuario y actualice el archivo de usuarios del negocio, usted podrá pulsar ACTUALIZAR en el inicio.",
                        fontSize = 11.sp,
                        color = Slate500,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Slate200)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("CANCELAR", color = Slate600)
                    }

                    Button(
                        onClick = {
                            if (fullName.isBlank()) {
                                errorMessage = "Por favor ingrese el nombre y apellidos del usuario."
                                return@Button
                            }
                            if (username.isBlank()) {
                                errorMessage = "Por favor ingrese el nombre de usuario / identificador."
                                return@Button
                            }

                            isSending = true
                            val montoVal = montoPorProducto.toDoubleOrNull() ?: 0.0

                            val smsBody = SuperAdminSmsHelper.buildNuevoUsuarioSms(
                                dvc = deviceId,
                                negocio = businessName.ifBlank { "ElQadre" },
                                solicitante = solicitanteNombre.ifBlank { "Dueño" },
                                nombre = fullName.trim(),
                                username = username.trim().lowercase(),
                                rol = selectedRole.displayName,
                                movil = phone.trim(),
                                montoPorProducto = montoVal
                            )

                            val sendResult = SuperAdminSmsHelper.sendSmsDirectOrIntent(context, smsBody)
                            isSending = false

                            if (sendResult.isSuccess) {
                                Toast.makeText(
                                    context,
                                    "Solicitud enviada al Super Admin (${SuperAdminSmsHelper.SUPER_ADMIN_PHONE}).",
                                    Toast.LENGTH_LONG
                                ).show()
                                onSmsSent()
                                onDismiss()
                            } else {
                                errorMessage = "Error al enviar SMS: ${sendResult.exceptionOrNull()?.message}"
                            }
                        },
                        enabled = !isSending && fullName.isNotBlank() && username.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(48.dp)
                            .testTag("btn_enviar_solicitar_nuevo_usuario")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SOLICITAR", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
