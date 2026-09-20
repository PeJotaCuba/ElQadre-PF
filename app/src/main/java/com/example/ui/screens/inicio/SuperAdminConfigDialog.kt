package com.example.ui.screens.inicio

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.ui.theme.ElQadreGold
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminConfigDialog(
    dvc: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    val formattedDvc = remember(dvc) {
        if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header icon & title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "CONFIGURACIÓN DE SUPER ADMIN",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // DVC Reference Container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "DVC actual del dispositivo",
                                fontSize = 12.sp,
                                color = Slate600
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formattedDvc,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ElQadreNavy
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.Fingerprint,
                            contentDescription = null,
                            tint = ElQadreNavy,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Usuario field
                OutlinedTextField(
                    value = usuario,
                    onValueChange = {
                        usuario = it
                        errorMessage = null
                    },
                    label = { Text("Usuario") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = ElQadreNavy
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("superadmin_usuario_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy,
                        cursorColor = ElQadreNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password field
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Contraseña") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = ElQadreNavy
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = Slate600
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("superadmin_password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy,
                        cursorColor = ElQadreNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Confirm Password field
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirmar contraseña") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = ElQadreNavy
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Icon(
                                imageVector = if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showConfirmPassword) "Ocultar contraseña" else "Mostrar contraseña",
                                tint = Slate600
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("superadmin_confirm_password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElQadreNavy,
                        focusedLabelColor = ElQadreNavy,
                        cursorColor = ElQadreNavy
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CANCELAR", color = Slate700, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (usuario.isBlank()) {
                                errorMessage = "El nombre de usuario es obligatorio"
                                return@Button
                            }
                            if (password.isBlank()) {
                                errorMessage = "La contraseña es obligatoria"
                                return@Button
                            }
                            if (password != confirmPassword) {
                                errorMessage = "Las contraseñas no coinciden"
                                return@Button
                            }

                            val passwordHash = generateSha256(password)

                            val adminObject = JSONObject().apply {
                                put("dvc", formattedDvc)
                                put("usuario", usuario.trim())
                                put("passwordHash", passwordHash)
                                put("estado", "ACTIVO")
                            }
                            val superAdminsArray = JSONArray().put(adminObject)
                            val rootJson = JSONObject().apply {
                                put("superAdmins", superAdminsArray)
                            }
                            val jsonString = rootJson.toString(2)

                            try {
                                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                    ?: context.filesDir
                                if (!downloadsDir.exists()) {
                                    downloadsDir.mkdirs()
                                }
                                val jsonFile = File(downloadsDir, "QDF_superadmin.json")
                                FileOutputStream(jsonFile).use { fos ->
                                    fos.write(jsonString.toByteArray(Charsets.UTF_8))
                                }

                                SuperAdminAuthService.setSuperAdminConfigured(context, true)

                                Toast.makeText(
                                    context,
                                    "QDF_superadmin.json generado con éxito",
                                    Toast.LENGTH_LONG
                                ).show()

                                // Open share sheet for convenience in manual publication
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        jsonFile
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "QDF_superadmin.json")
                                        putExtra(Intent.EXTRA_TEXT, "Archivo de configuración Super Admin QDF_superadmin.json")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(sendIntent, "Compartir / Guardar QDF_superadmin.json").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    val resInfoList = context.packageManager.queryIntentActivities(
                                        chooser,
                                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                                    )
                                    for (resolveInfo in resInfoList) {
                                        context.grantUriPermission(
                                            resolveInfo.activityInfo.packageName,
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    }
                                    context.startActivity(chooser)
                                } catch (shareEx: Exception) {
                                    shareEx.printStackTrace()
                                }

                                onDismiss()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                errorMessage = "Error al generar archivo: ${e.message}"
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("superadmin_generate_json_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("GENERAR JSON", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun generateSha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(input.toByteArray(Charsets.UTF_8))
    return digest.fold("") { str, it -> str + "%02x".format(it) }
}
