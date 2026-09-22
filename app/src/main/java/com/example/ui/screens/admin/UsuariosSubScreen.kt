package com.example.ui.screens.admin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.model.User
import com.example.data.local.model.UserRole
import com.example.licensing.BusinessCodeHelper
import com.example.ui.components.ContactPickerIconButton
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.PasswordResetHelper
import com.example.util.PasswordResetRequest
import com.example.util.QDuenoExporter
import com.example.util.toSha256
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun UsuariosSubScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showSolicitarUsuarioDialog by remember { mutableStateOf(false) }
    var showSendDataDialog by remember { mutableStateOf(false) }
    var sendDataPassword by remember { mutableStateOf("") }
    var userForSendData by remember { mutableStateOf<User?>(null) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<User?>(null) }
    var showDeviceConfirm by remember { mutableStateOf<User?>(null) }
    var pendingDeviceChange by remember { mutableStateOf<String?>("") }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        var urlCatalogo by remember(uiState.generalConfig?.urlCatalogoJson) { mutableStateOf(uiState.generalConfig?.urlCatalogoJson ?: "") }
        var urlMercainv by remember(uiState.generalConfig?.urlMercainvJson) { mutableStateOf(uiState.generalConfig?.urlMercainvJson ?: "") }
        var urlVersionJson by remember(uiState.generalConfig?.urlVersionJson) { mutableStateOf(uiState.generalConfig?.urlVersionJson ?: "") }
        var urlQDueno by remember(uiState.generalConfig?.urlQDuenoJson) { mutableStateOf(uiState.generalConfig?.urlQDuenoJson ?: "") }
        var telefonoDueno by remember(uiState.generalConfig?.telefonoDueno) { mutableStateOf(uiState.generalConfig?.telefonoDueno ?: "") }
        var telefonoCajero by remember(uiState.generalConfig?.telefonoCajero) { mutableStateOf(uiState.generalConfig?.telefonoCajero ?: "") }
        var telefonoAdmin by remember(uiState.generalConfig?.telefonoAdmin) { mutableStateOf(uiState.generalConfig?.telefonoAdmin ?: "") }

    val currentBizCode = remember(uiState.businessConfig, uiState.generalConfig) {
        BusinessCodeHelper.resolveBusinessCode(context, uiState.businessConfig, uiState.generalConfig)
    }

    val createQDuenoDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonString = viewModel.generateQDuenoJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Archivo Q_${currentBizCode}dueño.json generado exitosamente", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error al generar Q_${currentBizCode}dueño.json: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importQAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    viewModel.importQAdminJsonContent(jsonContent)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer Q_admin.json: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("version", System.currentTimeMillis().toString())

                val currentConfig = uiState.generalConfig
                val uUrl = BusinessCodeHelper.buildUrlUsuarios(currentBizCode)
                val cUrl = BusinessCodeHelper.buildUrlCatalogo(currentBizCode)
                val aUrl = BusinessCodeHelper.buildUrlAdmin(currentBizCode)
                val dUrl = BusinessCodeHelper.buildUrlDueño(currentBizCode)
                val mUrl = urlMercainv.trim().ifBlank { currentConfig?.urlMercainvJson ?: "" }
                val vUrl = urlVersionJson.trim().ifBlank { currentConfig?.urlVersionJson ?: "" }
                val ownerPhone = telefonoDueno.trim().ifBlank { currentConfig?.telefonoDueno ?: "" }
                val cajeroPhone = telefonoCajero.trim().ifBlank { currentConfig?.telefonoCajero ?: "" }
                val adminPhone = telefonoAdmin.trim().ifBlank { currentConfig?.telefonoAdmin ?: "" }

                // Negocio metadata
                val negObj = JSONObject().apply {
                    put("codigo", currentBizCode)
                    put("nombre", uiState.businessConfig?.nombreNegocio ?: uiState.businessName)
                }
                jsonObject.put("negocio", negObj)
                jsonObject.put("codigoNegocio", currentBizCode)

                // Include URLs and phones in Q_XXXusuarios.json
                jsonObject.put("urlUsuarios", uUrl)
                jsonObject.put("urlCatalogo", cUrl)
                jsonObject.put("urlAdmin", aUrl)
                jsonObject.put("urlDueño", dUrl)
                jsonObject.put("urlDueno", dUrl)
                jsonObject.put("urlUsuariosJson", uUrl)
                jsonObject.put("urlCatalogoJson", cUrl)
                jsonObject.put("urlAdminJson", aUrl)
                jsonObject.put("urlQDuenoJson", dUrl)
                jsonObject.put("urlMercainvJson", mUrl)
                jsonObject.put("versionJsonUrl", vUrl)
                jsonObject.put("telefonoDueno", ownerPhone)
                jsonObject.put("telefonoCajero", cajeroPhone)
                jsonObject.put("telefonoAdmin", adminPhone)

                val usersArray = JSONArray()
                for (user in uiState.users) {
                    val userObj = JSONObject()
                    userObj.put("username", user.username)
                    userObj.put("fullName", user.fullName)
                    userObj.put("passwordHash", user.passwordHash)
                    userObj.put("role", user.role.name)
                    userObj.put("montoPorProducto", user.montoPorProducto)
                    userObj.put("isActive", user.isActive)
                    userObj.put("telefono", user.telefono)

                    // Operational URLs per user role
                    when (user.role) {
                        UserRole.DUENO -> {
                            userObj.put("urlDueño", dUrl)
                            userObj.put("urlDueno", dUrl)
                            userObj.put("urlQDuenoJson", dUrl)
                            userObj.put("urlAdmin", aUrl)
                            userObj.put("urlCatalogo", cUrl)
                            userObj.put("urlUsuarios", uUrl)
                            val permissionsObj = JSONObject()
                            permissionsObj.put("produccion", user.permisoProduccion)
                            permissionsObj.put("mercancias", user.permisoMercancias)
                            permissionsObj.put("personal", user.permisoPersonal)
                            permissionsObj.put("controlNegocio", true)
                            userObj.put("permissions", permissionsObj)
                        }
                        UserRole.ADMIN -> {
                            userObj.put("urlAdmin", aUrl)
                            userObj.put("urlAdminJson", aUrl)
                            userObj.put("urlCatalogo", cUrl)
                            userObj.put("urlCatalogoJson", cUrl)
                            userObj.put("urlUsuarios", uUrl)
                            userObj.put("urlUsuariosJson", uUrl)
                            userObj.put("urlMercainv", mUrl)
                            userObj.put("urlMercainvJson", mUrl)
                        }
                        else -> { // CAJERO, DEPENDIENTE DE SALÓN, DEPENDIENTE DE BARRA
                            userObj.put("urlCatalogo", cUrl)
                            userObj.put("urlCatalogoJson", cUrl)
                            userObj.put("urlUsuarios", uUrl)
                            userObj.put("urlUsuariosJson", uUrl)
                            userObj.put("urlAdmin", aUrl)
                        }
                    }
                    usersArray.put(userObj)
                }
                jsonObject.put("usuarios", usersArray)
                val jsonString = jsonObject.toString(4)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                }
                viewModel.updateUrls(uUrl, cUrl, mUrl, ownerPhone, cajeroPhone, adminPhone, null, vUrl)
                Toast.makeText(context, "Archivo Q_${currentBizCode}usuarios.json generado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonContent = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    viewModel.importUsersFromJsonContent(jsonContent)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al leer archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp), // Space for FAB
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gestión de Usuarios", style = MaterialTheme.typography.titleLarge, color = ElQadreNavy, fontWeight = FontWeight.Bold)
                    Text("Administración completa de accesos y roles", color = Slate500, fontSize = 12.sp)
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("btn_crear_nuevo_usuario"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CREAR NUEVO USUARIO", fontWeight = FontWeight.Bold)
            }
 
            // URL & Phones Configuration
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Configuración de Sincronización y Teléfonos de Contacto", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                    
                    OutlinedTextField(
                        value = telefonoDueno,
                        onValueChange = { telefonoDueno = it },
                        label = { Text("Teléfono del Dueño (WhatsApp Transferencias)") },
                        placeholder = { Text("Ej. 5351234567 o +53 51234567") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_telefono_dueno")
                    )

                    OutlinedTextField(
                        value = telefonoCajero,
                        onValueChange = { telefonoCajero = it },
                        label = { Text("Teléfono de Caja / Cajero (Comandas SMS)") },
                        placeholder = { Text("Ej. 5357654321") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_telefono_cajero")
                    )

                    OutlinedTextField(
                        value = telefonoAdmin,
                        onValueChange = { telefonoAdmin = it },
                        label = { Text("Teléfono de Administrador (Opcional)") },
                        placeholder = { Text("Ej. 5359876543") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_telefono_admin")
                    )

                    OutlinedTextField(
                        value = urlCatalogo,
                        onValueChange = { urlCatalogo = it },
                        label = { Text("URL de qcatalogo.json") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = urlMercainv,
                        onValueChange = { urlMercainv = it },
                        label = { Text("URL de qmercainv.json") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = urlQDueno,
                        onValueChange = { urlQDueno = it },
                        label = { Text("URL Q_DUEÑO") },
                        placeholder = { Text("https://raw.githubusercontent.com/...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder,
                            focusedLabelColor = ElQadreNavy
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_url_q_dueno")
                    )
                    
                    Button(
                        onClick = {
                            viewModel.updateUrls(
                                "https://raw.githubusercontent.com/PeJotaCuba/BD-Qadre-PF/refs/heads/main/qusuarios.json",
                                urlCatalogo,
                                urlMercainv,
                                telefonoDueno,
                                telefonoCajero,
                                telefonoAdmin,
                                urlQDueno,
                                urlVersionJson
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.align(Alignment.End).testTag("btn_guardar_urls_dueno")
                    ) {
                        Text("Guardar Configuración")
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Spacer(modifier = Modifier.height(4.dp))

                     Button(
                        onClick = { createQDuenoDocumentLauncher.launch("Q_${currentBizCode}dueño.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.fillMaxWidth().testTag("btn_generar_q_dueno_json")
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("GENERAR Q_${currentBizCode}dueño.json", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { createDocumentLauncher.launch("Q_${currentBizCode}usuarios.json") },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            modifier = Modifier.weight(1f).testTag("btn_descargar_qusuarios_json")
                        ) {
                            Text("Exportar Q_${currentBizCode}usuarios.json", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { importDocumentLauncher.launch("application/json") },
                            modifier = Modifier.weight(1f).testTag("btn_importar_qusuarios_json")
                        ) {
                            Text("Importar Local", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            uiState.users.forEach { u ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ElQadreNavy)
                                Text("Usuario: ${u.username} • Rol: ${u.role.displayName}", fontSize = 12.sp, color = Slate600)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Estado: ", fontSize = 12.sp, color = Slate600)
                                    Text(
                                        text = if (u.isActive) "ACTIVO" else "INACTIVO",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (u.isActive) Emerald600 else Rose600
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Dispositivo: ", fontSize = 12.sp, color = Slate600)
                                    Text(
                                        text = u.authorizedDeviceId ?: "CUALQUIERA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate700
                                    )
                                }
                                if (u.telefono.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Teléfono: ", fontSize = 12.sp, color = Slate600)
                                        Text(
                                            text = u.telefono,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Emerald600
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Monto por Producto (Pago/Utilidad): ", fontSize = 12.sp, color = Slate600)
                                    Text(
                                        text = "$${"%.2f".format(u.montoPorProducto)} CUP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ElQadreNavy
                                    )
                                }
                                if (u.role == UserRole.DUENO) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Permisos asignados:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                                    Row(
                                        modifier = Modifier.padding(top = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Emerald100
                                        ) {
                                            Text("Negocio ✓", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Emerald700, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (u.permisoProduccion) Emerald100 else Slate200
                                        ) {
                                            Text(
                                                text = if (u.permisoProduccion) "Producción ✓" else "Producción ✗",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (u.permisoProduccion) Emerald700 else Slate500,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (u.permisoMercancias) Emerald100 else Slate200
                                        ) {
                                            Text(
                                                text = if (u.permisoMercancias) "Mercaderías ✓" else "Mercaderías ✗",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (u.permisoMercancias) Emerald700 else Slate500,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (u.permisoPersonal) Emerald100 else Slate200
                                        ) {
                                            Text(
                                                text = if (u.permisoPersonal) "Personal ✓" else "Personal ✗",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (u.permisoPersonal) Emerald700 else Slate500,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Role Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElQadreGoldSoft
                            ) {
                                Text(
                                    text = u.role.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 10.dp), color = Slate200)
                        
                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.testAccount(u) },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                border = BorderStroke(1.dp, ElQadreNavy),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy)
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ENTRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            
                            TextButton(
                                onClick = {
                                    userForSendData = u
                                    sendDataPassword = ""
                                    showSendDataDialog = true
                                },
                                modifier = Modifier.testTag("btn_enviar_datos_usuario_${u.username}")
                            ) {
                                Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(14.dp), tint = ElQadreNavy)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("PREPARAR SMS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { 
                                val updatedUser = u.copy(isActive = !u.isActive)
                                viewModel.updateUser(updatedUser)
                            }) {
                                Icon(
                                    imageVector = if (u.isActive) Icons.Outlined.ToggleOn else Icons.Outlined.ToggleOff,
                                    contentDescription = "Alternar estado",
                                    tint = if (u.isActive) Emerald600 else Slate400
                                )
                            }
                            IconButton(onClick = { 
                                selectedUser = u
                                showEditDialog = true 
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Editar", tint = ElQadreNavy)
                            }
                            IconButton(onClick = { showDeleteConfirm = u }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Eliminar", tint = Rose600)
                            }
                        }
                    }
                }
            }
        }
        
        // FAB removed
    }

    if (showCreateDialog || showSolicitarUsuarioDialog) {
        UserDialog(
            user = null,
            currentDeviceId = uiState.deviceId,
            onDismiss = {
                showCreateDialog = false
                showSolicitarUsuarioDialog = false
            },
            onConfirm = { newUser ->
                viewModel.createUser(newUser)
                showCreateDialog = false
                showSolicitarUsuarioDialog = false
            }
        )
    }

    if (showEditDialog && selectedUser != null) {
        UserDialog(
            user = selectedUser,
            currentDeviceId = uiState.deviceId,
            onDismiss = { 
                showEditDialog = false
                selectedUser = null
            },
            onConfirm = { updatedUser ->
                // Check if device changed to ask for confirmation
                if (selectedUser?.authorizedDeviceId != updatedUser.authorizedDeviceId) {
                    showDeviceConfirm = selectedUser
                    pendingDeviceChange = updatedUser.authorizedDeviceId
                    selectedUser = updatedUser // Temporarily store the updated user
                    showEditDialog = false
                } else {
                    viewModel.updateUser(updatedUser)
                    showEditDialog = false
                    selectedUser = null
                }
            }
        )
    }
    
    if (showDeviceConfirm != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeviceConfirm = null 
                selectedUser = null
            },
            title = { Text("Confirmar cambio de dispositivo", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = { Text("Estás a punto de cambiar el dispositivo autorizado para este usuario. Si asignas un nuevo dispositivo, el usuario no podrá iniciar sesión en otros terminales. ¿Deseas continuar?") },
            confirmButton = {
                Button(
                    onClick = { 
                        selectedUser?.let { viewModel.updateUser(it) }
                        showDeviceConfirm = null
                        selectedUser = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeviceConfirm = null
                    selectedUser = null
                }) { Text("Cancelar") }
            }
        )
    }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Eliminar Usuario", fontWeight = FontWeight.Bold, color = Rose600) },
            text = { Text("¿Estás seguro de que deseas eliminar al usuario ${showDeleteConfirm?.username}? Sus operaciones anteriores se mantendrán en el historial, pero no podrá acceder más al sistema.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUser(showDeleteConfirm!!.username)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600, contentColor = Color.White)
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancelar") }
            }
        )
    }


    
    // Dialog for PREPARAR SMS DE DATOS
    if (showSendDataDialog && userForSendData != null) {
        val u = userForSendData!!
        AlertDialog(
            onDismissRequest = { showSendDataDialog = false },
            title = {
                Text("PREPARAR SMS DE DATOS", fontWeight = FontWeight.Bold, color = ElQadreNavy)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Detalles de la cuenta:", fontSize = 12.sp, color = Slate600)
                    Text("Usuario: ${u.username}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                    Text("Rol: ${u.role.displayName}", fontSize = 13.sp, color = Slate700)
                    Text("Móvil: ${u.telefono.ifBlank { "No asignado" }}", fontSize = 13.sp, color = Slate700)

                    OutlinedTextField(
                        value = sendDataPassword,
                        onValueChange = { sendDataPassword = it },
                        label = { Text("Contraseña a incluir en SMS") },
                        placeholder = { Text("Ej. 1234") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            unfocusedBorderColor = ElQadreBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("input_send_data_password")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Amber50,
                        border = BorderStroke(1.dp, Amber500),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Active WiFi o Datos Móviles y PULSE BOTÓN ENVIAR SMS.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Amber800,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val recipientPhone = u.telefono.trim()
                        if (recipientPhone.isBlank()) {
                            Toast.makeText(context, "El usuario no tiene un número de móvil asignado.", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        val passStr = sendDataPassword.trim().ifBlank { "1234" }
                        val configNegocio = uiState.businessConfig
                        val negocioName = configNegocio?.nombreNegocio?.ifBlank { uiState.businessName.ifBlank { "ElQadre" } } ?: uiState.businessName.ifBlank { "ElQadre" }
                        val codigoNegocio = configNegocio?.codigoNegocio?.ifBlank { "001" } ?: "001"
                        val rolName = com.example.util.AccountProvisioningHelper.mapRoleToProtocolString(u.role)

                        val smsBody = com.example.util.AccountProvisioningHelper.buildAccountDeliverySms(
                            numeroNegocio = codigoNegocio,
                            nombre = u.fullName,
                            usuario = u.username,
                            contrasenaInicial = passStr,
                            rol = rolName
                        )

                        val sent = try {
                            val smsManager = context.getSystemService(android.telephony.SmsManager::class.java)
                            smsManager.sendTextMessage(recipientPhone, null, smsBody, null, null)
                            true
                        } catch (e: Exception) {
                            false
                        }

                        if (!sent) {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:$recipientPhone")
                                    putExtra("sms_body", smsBody)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al enviar SMS: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }

                        Toast.makeText(context, "SMS de datos generado para ${u.username}", Toast.LENGTH_SHORT).show()
                        showSendDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                ) {
                    Text("Enviar SMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendDataDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDialog(
    user: User?,
    currentDeviceId: String,
    onDismiss: () -> Unit,
    onConfirm: (User) -> Unit
) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var fullName by remember { mutableStateOf(user?.fullName ?: "") }
    var password by remember { mutableStateOf("") } // Always empty when editing
    var role by remember { mutableStateOf(user?.role ?: UserRole.CAJERO) }
    var montoPorProductoText by remember { mutableStateOf(user?.montoPorProducto?.toString() ?: "0.0") }
    var telefono by remember { mutableStateOf(user?.telefono ?: "") }
    var authorizedDeviceId by remember { mutableStateOf(user?.authorizedDeviceId ?: "") }
    var assignCurrentDevice by remember { mutableStateOf(user?.authorizedDeviceId == currentDeviceId) }
    
    var expandedRole by remember { mutableStateOf(false) }
    var permisoProduccion by remember { mutableStateOf(user?.permisoProduccion ?: true) }
    var permisoMercancias by remember { mutableStateOf(user?.permisoMercancias ?: true) }
    var permisoPersonal by remember { mutableStateOf(user?.permisoPersonal ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (user == null) "Nuevo Usuario" else "Editar Usuario", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de usuario") },
                    singleLine = true,
                    enabled = user == null // Cannot change username if editing
                )
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Nombre completo") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (user == null) "Contraseña" else "Nueva contraseña (opcional)") },
                    singleLine = true
                )
                
                // Role Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedRole,
                    onExpandedChange = { expandedRole = !expandedRole }
                ) {
                    OutlinedTextField(
                        value = role.displayName,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRole) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRole,
                        onDismissRequest = { expandedRole = false }
                    ) {
                        UserRole.OPERATIONAL_ROLES.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.displayName) },
                                onClick = {
                                    role = selectionOption
                                    expandedRole = false
                                }
                            )
                        }
                    }
                }

                if (role == UserRole.DUENO) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = ElQadreBackground),
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "PERMISOS DE CONTROL (DUEÑO)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            
                            // Control del Negocio (Obligatorio, no editable, siempre activo)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Emerald50, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Obligatorio",
                                    tint = Emerald600,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Control del Negocio", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                    Text("Obligatorio para todos los Dueños (Siempre activo)", fontSize = 10.sp, color = Slate500)
                                }
                            }

                            Divider(color = Slate200, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))

                            // Control de Producción (Editable)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { permisoProduccion = !permisoProduccion },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = permisoProduccion,
                                    onCheckedChange = { permisoProduccion = it },
                                    colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Control de Producción", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate800)
                            }

                            // Control de Mercaderías (Editable)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { permisoMercancias = !permisoMercancias },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = permisoMercancias,
                                    onCheckedChange = { permisoMercancias = it },
                                    colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Control de Mercaderías", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate800)
                            }

                            // Control de Personal (Editable)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { permisoPersonal = !permisoPersonal },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = permisoPersonal,
                                    onCheckedChange = { permisoPersonal = it },
                                    colors = CheckboxDefaults.colors(checkedColor = ElQadreNavy)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Control de Personal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate800)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = montoPorProductoText,
                    onValueChange = { montoPorProductoText = it },
                    label = { Text("Monto por producto (Pago / Utilidad CUP)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text("Teléfono de contacto (Opcional)") },
                    placeholder = { Text("Ej. 5351234567") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_user_telefono"),
                    trailingIcon = {
                        ContactPickerIconButton(
                            onContactPicked = { pickedName, pickedPhone ->
                                telefono = pickedPhone
                                if (fullName.isBlank() && pickedName.isNotBlank()) {
                                    fullName = pickedName
                                }
                            }
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Dispositivo autorizado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = assignCurrentDevice,
                        onCheckedChange = { 
                            assignCurrentDevice = it 
                            if (it) authorizedDeviceId = currentDeviceId
                        },
                        colors = CheckboxDefaults.colors(checkedColor = ElQadreGold)
                    )
                    Text("Asignar a este dispositivo ($currentDeviceId)", fontSize = 12.sp)
                }
                
                OutlinedTextField(
                    value = authorizedDeviceId,
                    onValueChange = { 
                        authorizedDeviceId = it
                        assignCurrentDevice = (it == currentDeviceId)
                    },
                    label = { Text("ID de dispositivo (Dejar vacío para cualquiera)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (username.isNotBlank() && fullName.isNotBlank() && (user != null || password.isNotBlank())) {
                        val finalDevice = authorizedDeviceId.trim().ifEmpty { null }
                        val passHash = if (password.isNotBlank()) password.trim().toSha256() else user!!.passwordHash
                        
                        val resultUser = User(
                            username = username.trim(),
                            fullName = fullName.trim(),
                            passwordHash = passHash,
                            role = role,
                            montoPorProducto = montoPorProductoText.toDoubleOrNull() ?: 0.0,
                            isActive = user?.isActive ?: true,
                            authorizedDeviceId = finalDevice,
                            createdAt = user?.createdAt ?: System.currentTimeMillis(),
                            telefono = telefono.trim(),
                            permisoProduccion = if (role == UserRole.DUENO) permisoProduccion else true,
                            permisoMercancias = if (role == UserRole.DUENO) permisoMercancias else true,
                            permisoPersonal = if (role == UserRole.DUENO) permisoPersonal else true,
                            permisoControlNegocio = true
                        )
                        onConfirm(resultUser)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                enabled = username.isNotBlank() && fullName.isNotBlank() && (user != null || password.isNotBlank())
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
