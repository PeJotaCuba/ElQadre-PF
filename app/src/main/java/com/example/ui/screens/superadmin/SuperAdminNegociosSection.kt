package com.example.ui.screens.superadmin
import com.example.util.ContactHelper

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.AppDatabase
import com.example.licensing.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SuperAdminNegociosSection(
    onEnterElQadreWithBusiness: (BusinessRecord) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var businesses by remember { mutableStateOf(SuperAdminBusinessManager.getBusinesses(context)) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var showNewBusinessDialog by remember { mutableStateOf(false) }
    var editingBusiness by remember { mutableStateOf<BusinessRecord?>(null) }
    var managingUsersBusiness by remember { mutableStateOf<BusinessRecord?>(null) }
    var managingCommercialBusiness by remember { mutableStateOf<BusinessRecord?>(null) }
    var businessToDelete by remember { mutableStateOf<BusinessRecord?>(null) }
    var businessToResendSms by remember { mutableStateOf<BusinessRecord?>(null) }

    fun refreshList() {
        businesses = SuperAdminBusinessManager.getBusinesses(context)
    }

    val filteredBusinesses = remember(businesses, searchQuery) {
        if (searchQuery.isBlank()) businesses
        else businesses.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true) ||
                    it.dvc.contains(searchQuery, ignoreCase = true) ||
                    it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Action Bar: Search & New Business Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar negocio por código, nombre o DVC...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElQadreNavy,
                    unfocusedBorderColor = Slate300,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("superadmin_negocios_search_field")
            )

            Button(
                onClick = { showNewBusinessDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("superadmin_new_business_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddBusiness,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "NUEVO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Header info banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ElQadreNavySoft.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Storefront,
                    contentDescription = null,
                    tint = ElQadreGold,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = "Gestión Multi-Negocio (${businesses.size} Registrados)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Cada negocio cuenta con su código único (ej: 001) y su propio archivo Q_XXXusuarios.json independiente.",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Businesses List
        if (filteredBusinesses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = Slate300,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No se encontraron negocios para '$searchQuery'" else "No hay negocios registrados",
                        color = Slate500,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredBusinesses, key = { it.code }) { business ->
                    BusinessCardItem(
                        business = business,
                        onTestBusiness = {
                            onEnterElQadreWithBusiness(business)
                        },
                        onManageUsers = {
                            managingUsersBusiness = business
                        },
                        onManageCommercial = {
                            managingCommercialBusiness = business
                        },
                        onEdit = {
                            editingBusiness = business
                        },
                        onExportJson = {
                            val jsonFile = SuperAdminBusinessManager.generateUserJsonForBusiness(context, business)
                            SuperAdminSmsHelper.shareJsonFile(context, jsonFile, "Usuarios del negocio ${business.name}")
                        },
                        onResendSms = {
                            businessToResendSms = business
                        },
                        onDelete = {
                            businessToDelete = business
                        }
                    )
                }
            }
        }
    }

    // Dialog: Commercial Info & Actions Management
    if (managingCommercialBusiness != null) {
        val currentBiz = businesses.firstOrNull { it.code == managingCommercialBusiness?.code } ?: managingCommercialBusiness!!
        BusinessCommercialDialog(
            business = currentBiz,
            onDismiss = { managingCommercialBusiness = null },
            onUpdateBusiness = { updatedBiz ->
                SuperAdminBusinessManager.saveBusiness(context, updatedBiz)
                refreshList()
                managingCommercialBusiness = updatedBiz
            },
            onDeleteBusiness = { bizToDelete ->
                businessToDelete = bizToDelete
            }
        )
    }

    // Dialog: Add / Edit Business
    if (showNewBusinessDialog || editingBusiness != null) {
        val target = editingBusiness
        BusinessEditDialog(
            initialBusiness = target,
            nextCode = if (target == null) SuperAdminBusinessManager.getNextBusinessCode(context) else target.code,
            onDismiss = {
                showNewBusinessDialog = false
                editingBusiness = null
            },
            onSave = { savedBiz ->
                SuperAdminBusinessManager.saveBusiness(context, savedBiz)
                refreshList()
                showNewBusinessDialog = false
                editingBusiness = null
                Toast.makeText(context, "Negocio [${savedBiz.code}] guardado correctamente.", Toast.LENGTH_SHORT).show()
            },
            onDelete = if (target != null) { { businessToDelete = target } } else null
        )
    }

    // Dialog: Manage Business Users
    if (managingUsersBusiness != null) {
        val currentBiz = businesses.firstOrNull { it.code == managingUsersBusiness?.code } ?: managingUsersBusiness!!
        BusinessUsersManagerDialog(
            business = currentBiz,
            onDismiss = { managingUsersBusiness = null },
            onUpdateBusiness = { updatedBiz ->
                SuperAdminBusinessManager.saveBusiness(context, updatedBiz)
                refreshList()
                managingUsersBusiness = updatedBiz
            }
        )
    }

    // Dialog: Confirm Delete
    if (businessToDelete != null) {
        val biz = businessToDelete!!
        AlertDialog(
            onDismissRequest = { businessToDelete = null },
            title = {
                Text(
                    text = "Eliminar Negocio",
                    fontWeight = FontWeight.Bold,
                    color = Rose600
                )
            },
            text = {
                Text(
                    text = "¿Está seguro de eliminar el negocio [${biz.code}] '${biz.name}'?\n\n" +
                            "• Desaparecerá inmediatamente del listado de Super Admin.\n" +
                            "• Se eliminarán sus usuarios y archivos operativos locales.\n" +
                            "• El número [${biz.code}] queda definitivamente consumido y NO podrá reutilizarse.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletedCode = biz.code
                        SuperAdminBusinessManager.deleteBusiness(context, deletedCode)
                        refreshList()
                        if (managingCommercialBusiness?.code == deletedCode) {
                            managingCommercialBusiness = null
                        }
                        if (editingBusiness?.code == deletedCode) {
                            editingBusiness = null
                        }
                        if (managingUsersBusiness?.code == deletedCode) {
                            managingUsersBusiness = null
                        }
                        businessToDelete = null
                        Toast.makeText(context, "Negocio [$deletedCode] eliminado correctamente.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose600)
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { businessToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Reenviar SMS de Confirmación / Autorización (Máximo 3 envíos)
    if (businessToResendSms != null) {
        val biz = businessToResendSms!!
        val canSend = biz.trialSmsSentCount < 3
        val smsText = remember(biz) { SuperAdminSmsHelper.generateConfirmationSmsText(biz) }

        AlertDialog(
            onDismissRequest = { businessToResendSms = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = ElQadreNavy,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Reenviar SMS al Negocio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ElQadreNavySoft,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🏢 [${biz.code}] ${biz.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "📱 Destinatario: ${biz.phone.ifBlank { "Sin número asignado" }}",
                                fontSize = 12.sp,
                                color = Slate700
                            )
                            Text(
                                text = "📊 Envíos realizados: ${biz.trialSmsSentCount} de 3 permitidos",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (canSend) Emerald700 else Rose700
                            )
                        }
                    }

                    if (canSend) {
                        Text(
                            text = "Mensaje que se enviará:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Slate100,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = smsText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Slate800,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Text(
                            text = "Nota: Al pulsar ENVIAR se enviará directamente por SMS o se abrirá la app de mensajes del sistema y se contabilizará este reenvío.",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Rose50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Rose300),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Se ha alcanzado el límite máximo de 3 envíos de SMS para este negocio.",
                                fontSize = 12.sp,
                                color = Rose700,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (canSend && biz.phone.isNotBlank()) {
                    Button(
                        onClick = {
                            val newCount = biz.trialSmsSentCount + 1
                            val updatedBiz = biz.copy(trialSmsSentCount = newCount)
                            SuperAdminBusinessManager.saveBusiness(context, updatedBiz)
                            refreshList()
                            SuperAdminSmsHelper.sendSmsDirectOrIntentToPhone(context, biz.phone, smsText)
                            businessToResendSms = null
                            Toast.makeText(context, "SMS reenviado a ${biz.phone} ($newCount/3)", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ENVIAR SMS (${biz.trialSmsSentCount + 1}/3)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { businessToResendSms = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun BusinessCardItem(
    business: BusinessRecord,
    onTestBusiness: () -> Unit,
    onManageUsers: () -> Unit,
    onManageCommercial: () -> Unit,
    onEdit: () -> Unit,
    onExportJson: () -> Unit,
    onResendSms: () -> Unit,
    onDelete: () -> Unit
) {
    val commStatus = SuperAdminBusinessManager.calculateCommercialStatus(business)

    val (statusLabel, statusBg, statusColor) = when (commStatus) {
        CommercialStatus.SIN_AUTORIZACION -> Triple("SIN AUTORIZACIÓN", Slate100, Slate700)
        CommercialStatus.PRUEBA_ACTIVA -> Triple("PRUEBA ACTIVA", Sky100, Sky700)
        CommercialStatus.PRUEBA_VENCIDA -> Triple("PRUEBA VENCIDA", Amber100, Amber800)
        CommercialStatus.LICENCIA_ACTIVA -> Triple("LICENCIA ACTIVA", Emerald100, Emerald800)
        CommercialStatus.LICENCIA_VENCIDA -> Triple("LICENCIA VENCIDA", Rose100, Rose700)
        CommercialStatus.LICENCIA_REVOCADA -> Triple("LICENCIA REVOCADA", Rose700, Color.White)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (commStatus == CommercialStatus.LICENCIA_REVOCADA) Rose300 else Slate200
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("business_card_${business.code}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Code Badge, Name, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreNavy
                    ) {
                        Text(
                            text = business.code,
                            color = ElQadreGold,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = business.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ElQadreNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (commStatus == CommercialStatus.LICENCIA_REVOCADA) Rose800 else statusColor.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.clickable { onManageCommercial() }
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("delete_business_icon_${business.code}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Eliminar Negocio",
                            tint = Rose500,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details Grid
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = ElQadreGold,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TOKEN DE ENTRADA: ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                    Text(
                        text = business.token.ifBlank { "Sin Token" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = ElQadreNavy
                    )
                    if (business.token.isNotBlank()) {
                        val clipboard = LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val context = LocalContext.current
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val clip = android.content.ClipData.newPlainText("Token del Negocio", business.token)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Token copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copiar Token",
                                tint = ElQadreNavy,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DVC: ",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                    Text(
                        text = business.dvc.ifBlank { "Sin asignar" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ElQadreNavy
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Móvil: ",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                    Text(
                        text = business.phone.ifBlank { "—" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate700
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vencimiento: ",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                    Text(
                        text = business.endDate.ifBlank { "PERMANENTE" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (commStatus == CommercialStatus.LICENCIA_VENCIDA || commStatus == CommercialStatus.PRUEBA_VENCIDA) Rose600 else Slate800
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Icon(
                        imageVector = Icons.Outlined.Group,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${business.users.size} usuarios",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }
            }

            // Revocation Warning Banner
            if (commStatus == CommercialStatus.LICENCIA_REVOCADA || business.status == "REVOCADA") {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Rose50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose300),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Rose700,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "⛔ LICENCIA REVOCADA el ${business.fechaRevocacion.ifBlank { "recientemente" }}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Rose800
                            )
                            if (business.motivoRevocacion.isNotBlank()) {
                                Text(
                                    text = "Motivo: ${business.motivoRevocacion}",
                                    fontSize = 11.sp,
                                    color = Rose700
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row 1: Primary Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Main Test Button
                Button(
                    onClick = onTestBusiness,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreGold,
                        contentColor = ElQadreNavy
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("test_business_button_${business.code}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "PROBAR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // Commercial Management Button
                Button(
                    onClick = onManageCommercial,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .height(36.dp)
                        .testTag("manage_commercial_button_${business.code}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = ElQadreGold
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "COMERCIAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Users Management Button
                OutlinedButton(
                    onClick = onManageUsers,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = ElQadreNavy
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "USUARIOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row 2: Secondary Management & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Export JSON Button
                    IconButton(
                        onClick = onExportJson,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Exportar JSON Usuarios",
                            tint = ElQadreNavy,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Edit Button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Editar",
                            tint = Slate600,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reenviar SMS Button
                    val canResend = business.trialSmsSentCount < 3
                    OutlinedButton(
                        onClick = onResendSms,
                        enabled = canResend && business.phone.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (canResend && business.phone.isNotBlank()) ElQadreNavy.copy(alpha = 0.5f) else Slate300
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ElQadreNavy,
                            disabledContentColor = Slate400
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("resend_sms_button_${business.code}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = "Reenviar SMS",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (canResend) "REENVIAR SMS (${business.trialSmsSentCount}/3)" else "SMS (3/3)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Prominent ELIMINAR Button with text & icon
                OutlinedButton(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose300),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("delete_business_button_${business.code}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Eliminar Negocio",
                        tint = Rose600,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ELIMINAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Rose600
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BusinessEditDialog(
    initialBusiness: BusinessRecord?,
    nextCode: String,
    onDismiss: () -> Unit,
    onSave: (BusinessRecord) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isEdit = initialBusiness != null
    val code = remember { BusinessCodeHelper.formatCode(initialBusiness?.code ?: nextCode) }
    var name by remember { mutableStateOf(initialBusiness?.name ?: "") }
    var dvc by remember { mutableStateOf(initialBusiness?.dvc ?: "") }
    var phone by remember { mutableStateOf(initialBusiness?.phone ?: "54413935") }
    var phoneAlt by remember { mutableStateOf(initialBusiness?.phoneAlt ?: "") }
    var licenseType by remember { mutableStateOf(initialBusiness?.licenseType ?: "PRUEBA") }
    var startDate by remember {
        mutableStateOf(
            initialBusiness?.startDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
    }
    var endDate by remember {
        mutableStateOf(
            initialBusiness?.endDate ?: if (licenseType == "PRUEBA") {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 7)
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            } else ""
        )
    }

    // Auto-generated 4 operational URLs (no manual typing)
    val urlUsuarios = remember(code) { SuperAdminBusinessManager.buildUrlUsuarios(code) }
    val urlCatalogo = remember(code) { SuperAdminBusinessManager.buildUrlCatalogo(code) }
    val urlAdmin = remember(code) { SuperAdminBusinessManager.buildUrlAdmin(code) }
    val urlDueño = remember(code) { SuperAdminBusinessManager.buildUrlDueño(code) }

    var expandedLicenseDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "Configurar Negocio [$code]" else "Nuevo Negocio [$code]",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
                Text(
                    text = "Número asignado automáticamente. Generación de 4 URLs y archivos operativos.",
                    fontSize = 12.sp,
                    color = Slate500
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Code (strictly read-only / immutable)
                OutlinedTextField(
                    value = code,
                    onValueChange = {},
                    label = { Text("Código de Negocio (Asignado)") },
                    singleLine = true,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Negocio *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // DVC
                OutlinedTextField(
                    value = dvc,
                    onValueChange = { dvc = it },
                    label = { Text("DVC del Dispositivo (ej: DVC-A1B2C3)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de Móvil Principal *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Alternative Phone
                OutlinedTextField(
                    value = phoneAlt,
                    onValueChange = { phoneAlt = it },
                    label = { Text("Número de Móvil Alternativo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // License Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedLicenseDropdown,
                    onExpandedChange = { expandedLicenseDropdown = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = licenseType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tipo de Licencia") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLicenseDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedLicenseDropdown,
                        onDismissRequest = { expandedLicenseDropdown = false }
                    ) {
                        listOf("PRUEBA", "PERMANENTE", "ANUAL", "MENSUAL").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    licenseType = type
                                    expandedLicenseDropdown = false
                                    if (type == "PERMANENTE") {
                                        endDate = ""
                                    } else if (type == "PRUEBA") {
                                        val cal = Calendar.getInstance()
                                        cal.add(Calendar.DAY_OF_YEAR, 7)
                                        endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Fecha Inicio") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Fecha Fin") },
                        placeholder = { Text("Permanente") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Informative Card with the 4 Generated URLs (Automatic, no manual typing)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Slate50),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "URLs OPERATIVAS GENERADAS ($code)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("• urlUsuarios: Q_${code}usuarios.json", fontSize = 11.sp, color = Slate700)
                        Text("• urlCatalogo: Q_${code}catalogo.json", fontSize = 11.sp, color = Slate700)
                        Text("• urlAdmin: Q_${code}admin.json", fontSize = 11.sp, color = Slate700)
                        Text("• urlDueño: Q_${code}dueño.json", fontSize = 11.sp, color = Slate700)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEdit && onDelete != null) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Rose600),
                            modifier = Modifier.testTag("delete_business_from_edit_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Rose600
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Eliminar Negocio", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = Slate600)
                        }
                        Button(
                            onClick = {
                                if (name.isBlank()) return@Button
                                val formattedDvc = if (dvc.isNotBlank()) {
                                    if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
                                } else ""

                                val existingUsers = initialBusiness?.users ?: SuperAdminBusinessManager.getDefaultUsersForBusiness(name, phone)

                                val record = BusinessRecord(
                                    code = code,
                                    name = name.trim(),
                                    dvc = formattedDvc,
                                    phone = phone.trim(),
                                    phoneAlt = phoneAlt.trim(),
                                    licenseType = licenseType,
                                    startDate = startDate.trim(),
                                    endDate = endDate.trim(),
                                    status = "ACTIVO",
                                    urlUsuarios = urlUsuarios,
                                    urlCatalogo = urlCatalogo,
                                    urlAdmin = urlAdmin,
                                    urlDueño = urlDueño,
                                    users = existingUsers
                                )
                                onSave(record)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isEdit) "Guardar Cambios" else "Crear Negocio")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessUsersManagerDialog(
    business: BusinessRecord,
    onDismiss: () -> Unit,
    onUpdateBusiness: (BusinessRecord) -> Unit
) {
    val context = LocalContext.current
    var currentBusiness by remember(business) { mutableStateOf(business) }
    var usersList by remember(business) { mutableStateOf(business.users) }
    var editingUser by remember { mutableStateOf<BusinessUser?>(null) }
    var passwordChangeUser by remember { mutableStateOf<BusinessUser?>(null) }
    var deletingUser by remember { mutableStateOf<BusinessUser?>(null) }
    var isCreatingUser by remember { mutableStateOf(false) }
    var pendingUserRequests by remember(business) {
        mutableStateOf(SuperAdminBusinessManager.getPendingUserRequestsForBusiness(context, business))
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = ElQadreNavy
                            ) {
                                Text(
                                    text = currentBusiness.code,
                                    color = ElQadreGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Usuarios: ${currentBusiness.name}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Archivo: Q_${currentBusiness.code}usuarios.json (${usersList.size} usuarios)",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Slate500
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { isCreatingUser = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElQadreNavy)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AGREGAR USUARIO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            val file = SuperAdminBusinessManager.generateUserJsonForBusiness(context, currentBusiness)
                            SuperAdminSmsHelper.shareJsonFile(context, file, "Q_${currentBusiness.code}usuarios.json")
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = ElQadreGoldSoft,
                            contentColor = ElQadreNavy
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COMPARTIR JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (pendingUserRequests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Solicitudes SMS pendientes para este negocio (${pendingUserRequests.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald700
                            )
                            pendingUserRequests.forEach { req ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${req.nuevoNombre} (@${req.nuevoUsername})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "Rol: ${req.nuevoRol} • Móvil: ${req.nuevoTelefono.ifBlank { "N/A" }}",
                                                fontSize = 11.sp,
                                                color = Slate700
                                            )
                                            if (req.solicitante.isNotBlank()) {
                                                Text(
                                                    text = "Solicitante: ${req.solicitante}",
                                                    fontSize = 10.sp,
                                                    color = Slate500
                                                )
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(
                                                onClick = {
                                                    SuperAdminBusinessManager.rejectUserRequest(context, req)
                                                    pendingUserRequests = SuperAdminBusinessManager.getPendingUserRequestsForBusiness(context, currentBusiness)
                                                }
                                            ) {
                                                Text("Descartar", fontSize = 10.sp, color = Rose700)
                                            }

                                            Button(
                                                onClick = {
                                                    val (updated, _) = SuperAdminBusinessManager.approveUserRequest(
                                                        context = context,
                                                        business = currentBusiness,
                                                        req = req,
                                                        initialPassword = "1234"
                                                    )
                                                    currentBusiness = updated
                                                    usersList = updated.users
                                                    onUpdateBusiness(updated)
                                                    pendingUserRequests = SuperAdminBusinessManager.getPendingUserRequestsForBusiness(context, updated)
                                                    Toast.makeText(context, "Usuario @${req.nuevoUsername} aprobado y agregado.", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = ElQadreNavy,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Aprobar", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Slate200)
                Spacer(modifier = Modifier.height(8.dp))

                // Users list
                if (usersList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay usuarios configurados para este negocio.",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(usersList, key = { it.id }) { u ->
                            UserRowItem(
                                user = u,
                                onToggleActive = {
                                    val updated = SuperAdminBusinessManager.toggleUserActiveStatus(context, currentBusiness, u.id)
                                    currentBusiness = updated
                                    usersList = updated.users
                                    onUpdateBusiness(updated)
                                    val statusStr = if (!u.active) "activado" else "desactivado"
                                    Toast.makeText(context, "Usuario @${u.username} $statusStr.", Toast.LENGTH_SHORT).show()
                                },
                                onChangePassword = { passwordChangeUser = u },
                                onEdit = { editingUser = u },
                                onDelete = { deletingUser = u }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create or Edit Single User
    if (isCreatingUser || editingUser != null) {
        val targetUser = editingUser
        UserEditDialog(
            initialUser = targetUser,
            existingUsers = usersList,
            defaultPhone = currentBusiness.phone,
            onDismiss = {
                isCreatingUser = false
                editingUser = null
            },
            onSave = { savedUser ->
                val updated = SuperAdminBusinessManager.saveOrUpdateUser(context, currentBusiness, savedUser)
                currentBusiness = updated
                usersList = updated.users
                onUpdateBusiness(updated)
                isCreatingUser = false
                editingUser = null
                Toast.makeText(context, "Usuario '${savedUser.username}' guardado en Q_${updated.code}usuarios.json.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Change Password
    if (passwordChangeUser != null) {
        val targetUser = passwordChangeUser!!
        ChangePasswordDialog(
            user = targetUser,
            onDismiss = { passwordChangeUser = null },
            onSavePassword = { newPass ->
                val updated = SuperAdminBusinessManager.changeUserPassword(context, currentBusiness, targetUser.id, newPass)
                currentBusiness = updated
                usersList = updated.users
                onUpdateBusiness(updated)
                passwordChangeUser = null
                Toast.makeText(context, "Contraseña actualizada para @${targetUser.username}.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Dialog: Delete Confirmation
    if (deletingUser != null) {
        val targetUser = deletingUser!!
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("¿Eliminar usuario?", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
            text = {
                Text(
                    "¿Está seguro de eliminar al usuario '@${targetUser.username}' (${targetUser.fullName})?\n\n" +
                    "Nota: Para suspender temporalmente el acceso manteniendo el registro, se recomienda cambiar su estado a INACTIVO.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = SuperAdminBusinessManager.deleteUser(context, currentBusiness, targetUser.id)
                        currentBusiness = updated
                        usersList = updated.users
                        onUpdateBusiness(updated)
                        deletingUser = null
                        Toast.makeText(context, "Usuario @${targetUser.username} eliminado.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose700)
                ) {
                    Text("Eliminar definitivamente")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) {
                    Text("Cancelar", color = Slate600)
                }
            }
        )
    }
}

@Composable
private fun UserRowItem(
    user: BusinessUser,
    onToggleActive: () -> Unit,
    onChangePassword: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val roleColor = when (user.role.uppercase()) {
        "ADMINISTRADOR" -> ElQadreNavy
        "DUEÑO" -> Amber700
        "CAJERO" -> Emerald700
        else -> Slate700
    }
    val roleBg = when (user.role.uppercase()) {
        "ADMINISTRADOR" -> ElQadreNavySoft
        "DUEÑO" -> Amber50
        "CAJERO" -> Emerald50
        else -> Slate100
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (user.active) Color.White else Slate100,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (user.active) Slate200 else Slate300),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "@${user.username}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (user.active) ElQadreNavy else Slate500
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = roleBg
                    ) {
                        Text(
                            text = user.role,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = roleColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    // Active / Inactive Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (user.active) Emerald50 else Rose50
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (user.active) Emerald600 else Rose600, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (user.active) "ACTIVO" else "INACTIVO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (user.active) Emerald700 else Rose700
                            )
                        }
                    }
                }

                Text(
                    text = user.fullName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (user.active) Slate800 else Slate500
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Clave: ${user.password}",
                        fontSize = 11.sp,
                        color = Slate600,
                        fontFamily = FontFamily.Monospace
                    )
                    if (user.phone.isNotBlank()) {
                        Text(
                            text = "• Tel: ${user.phone}",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                    if (user.montoPorProducto > 0) {
                        Text(
                            text = "• Comis: $${"%.2f".format(user.montoPorProducto)}",
                            fontSize = 10.sp,
                            color = Emerald700
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                // Activar / Desactivar Quick Toggle
                IconButton(
                    onClick = onToggleActive,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (user.active) Icons.Default.CheckCircle else Icons.Default.Block,
                        contentDescription = if (user.active) "Desactivar" else "Activar",
                        tint = if (user.active) Emerald600 else Rose600,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Change Password Button
                IconButton(
                    onClick = onChangePassword,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Cambiar Contraseña",
                        tint = Amber700,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Edit Button
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = Slate600,
                        modifier = Modifier.size(17.dp)
                    )
                }

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = Rose500,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    user: BusinessUser,
    onDismiss: () -> Unit,
    onSavePassword: (String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = Amber700)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cambiar Contraseña", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ElQadreNavy)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Usuario: @${user.username} (${user.fullName})\nContraseña actual: ${user.password}",
                    fontSize = 12.sp,
                    color = Slate600
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("Nueva Contraseña *") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = Rose700, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPassword.trim().isBlank()) {
                        errorMessage = "La nueva contraseña no puede estar vacía."
                        return@Button
                    }
                    onSavePassword(newPassword.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
            ) {
                Text("Guardar Contraseña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate600)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserEditDialog(
    initialUser: BusinessUser?,
    existingUsers: List<BusinessUser>,
    defaultPhone: String,
    onDismiss: () -> Unit,
    onSave: (BusinessUser) -> Unit
) {
    val isEdit = initialUser != null
    var username by remember { mutableStateOf(initialUser?.username ?: "") }
    var fullName by remember { mutableStateOf(initialUser?.fullName ?: "") }
    var password by remember { mutableStateOf(initialUser?.password ?: "1234") }
    var role by remember { mutableStateOf(initialUser?.role ?: "ADMINISTRADOR") }
    var phone by remember { mutableStateOf(initialUser?.phone ?: defaultPhone) }
    var montoPorProducto by remember { mutableStateOf(if ((initialUser?.montoPorProducto ?: 0.0) > 0) initialUser!!.montoPorProducto.toString() else "") }
    var active by remember { mutableStateOf(initialUser?.active ?: true) }
    var expandedRole by remember { mutableStateOf(false) }

    var usernameError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isEdit) "Editar Usuario" else "Nuevo Usuario para Negocio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = ElQadreNavy
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.trim().lowercase()
                        usernameError = null
                    },
                    label = { Text("Usuario (Login) *") },
                    singleLine = true,
                    isError = usernameError != null,
                    supportingText = usernameError?.let { { Text(it, color = Rose700, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        fullNameError = null
                    },
                    label = { Text("Nombre Completo *") },
                    singleLine = true,
                    isError = fullNameError != null,
                    supportingText = fullNameError?.let { { Text(it, color = Rose700, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = { Text("Contraseña *") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(it, color = Rose700, fontSize = 11.sp) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Role Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedRole,
                    onExpandedChange = { expandedRole = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = role,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol de Usuario *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRole) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expandedRole,
                        onDismissRequest = { expandedRole = false }
                    ) {
                        SuperAdminBusinessManager.AVAILABLE_ROLES.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = {
                                    role = r
                                    expandedRole = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Móvil de Contacto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = montoPorProducto,
                    onValueChange = { montoPorProducto = it },
                    label = { Text("Comisión por Producto (CUP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Estado del Usuario", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                        Text(
                            text = if (active) "ACTIVO (puede iniciar sesión)" else "INACTIVO (acceso bloqueado)",
                            fontSize = 11.sp,
                            color = if (active) Emerald700 else Rose700
                        )
                    }
                    Switch(checked = active, onCheckedChange = { active = it })
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Slate600)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanUser = username.trim().lowercase()
                            val cleanName = fullName.trim()
                            val cleanPass = password.trim()

                            var hasError = false
                            if (cleanUser.isBlank()) {
                                usernameError = "El usuario no puede estar vacío."
                                hasError = true
                            } else {
                                val isDuplicate = existingUsers.any {
                                    it.id != (initialUser?.id ?: -1L) && it.username.equals(cleanUser, ignoreCase = true)
                                }
                                if (isDuplicate) {
                                    usernameError = "El usuario '$cleanUser' ya existe en este negocio."
                                    hasError = true
                                }
                            }

                            if (cleanName.isBlank()) {
                                fullNameError = "El nombre completo es obligatorio."
                                hasError = true
                            }

                            if (cleanPass.isBlank()) {
                                passwordError = "La contraseña es obligatoria."
                                hasError = true
                            }

                            if (hasError) return@Button

                            val monto = montoPorProducto.toDoubleOrNull() ?: 0.0
                            val user = BusinessUser(
                                id = initialUser?.id ?: System.currentTimeMillis(),
                                username = cleanUser,
                                fullName = cleanName,
                                password = cleanPass,
                                role = role,
                                phone = phone.trim(),
                                active = active,
                                montoPorProducto = monto
                            )
                            onSave(user)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy)
                    ) {
                        Text(if (isEdit) "Guardar Cambios" else "Crear Usuario")
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog for Super Admin "ENTRAR A ELQADRE":
 * Allows selecting which business to test and load into the session.
 */
@Composable
fun SelectBusinessForTestingDialog(
    businesses: List<BusinessRecord>,
    onDismiss: () -> Unit,
    onSelectBusiness: (BusinessRecord) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "SELECCIONAR NEGOCIO",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = ElQadreNavy
                        )
                        Text(
                            text = "Elige el negocio para probar sus credenciales en el Login",
                            fontSize = 12.sp,
                            color = Slate500
                        )
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
                Spacer(modifier = Modifier.height(10.dp))

                if (businesses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay negocios registrados.", color = Slate400)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(businesses, key = { it.code }) { biz ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBusiness(biz) }
                                    .testTag("select_business_item_${biz.code}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = ElQadreNavy
                                        ) {
                                            Text(
                                                text = biz.code,
                                                color = ElQadreGold,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Column {
                                            Text(
                                                text = biz.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = ElQadreNavy
                                            )
                                            Text(
                                                text = "DVC: ${biz.dvc.ifBlank { "Sin DVC" }} | ${biz.users.size} usuarios",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = ElQadreNavy
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog for Super Admin to manage the commercial status of a business:
 * - Activar Prueba (7 días)
 * - Activar / Renovar Licencia (Mes, Año, Permanente, Custom)
 * - Revocar Licencia con Motivo (SMS automático)
 * - Enviar Avisos de Vencimiento SMS
 * - Exportar / Compartir archivos JSON (Q_licencias.json, Q_preuba.json, Q_XXXusuarios.json)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessCommercialDialog(
    business: BusinessRecord,
    onDismiss: () -> Unit,
    onUpdateBusiness: (BusinessRecord) -> Unit,
    onDeleteBusiness: ((BusinessRecord) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentBiz by remember { mutableStateOf(business) }
    var commStatus by remember(currentBiz) {
        mutableStateOf(SuperAdminBusinessManager.calculateCommercialStatus(currentBiz))
    }

    val (statusLabel, statusBg, statusColor) = when (commStatus) {
        CommercialStatus.SIN_AUTORIZACION -> Triple("SIN AUTORIZACIÓN", Slate100, Slate700)
        CommercialStatus.PRUEBA_ACTIVA -> Triple("PRUEBA ACTIVA", Sky100, Sky700)
        CommercialStatus.PRUEBA_VENCIDA -> Triple("PRUEBA VENCIDA", Amber100, Amber800)
        CommercialStatus.LICENCIA_ACTIVA -> Triple("LICENCIA ACTIVA", Emerald100, Emerald800)
        CommercialStatus.LICENCIA_VENCIDA -> Triple("LICENCIA VENCIDA", Rose100, Rose700)
        CommercialStatus.LICENCIA_REVOCADA -> Triple("LICENCIA REVOCADA", Rose700, Color.White)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElQadreNavy
                            ) {
                                Text(
                                    text = currentBiz.code,
                                    color = ElQadreGold,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = currentBiz.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = ElQadreNavy
                            )
                        }
                        Text(
                            text = "Gestión Comercial, Vencimientos y Licencias",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (onDeleteBusiness != null) {
                            IconButton(
                                onClick = { onDeleteBusiness(currentBiz) },
                                modifier = Modifier.testTag("delete_business_from_commercial_${currentBiz.code}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Eliminar Negocio",
                                    tint = Rose500
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Slate200)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Current Status Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "ESTADO COMERCIAL ACTUAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate500,
                                    letterSpacing = 0.5.sp
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusBg,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (commStatus == CommercialStatus.LICENCIA_REVOCADA) Rose800 else statusColor.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = statusLabel,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("DVC Dispositivo:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.dvc.ifBlank { "Sin DVC" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElQadreNavy
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Principal:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = ContactHelper.formatPhoneNumberWithContact(context, currentBiz.phone.ifBlank { "—" }),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Móvil Alternativo:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = ContactHelper.formatPhoneNumberWithContact(context, currentBiz.phoneAlt.ifBlank { "—" }),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }
                            }


                            Spacer(modifier = Modifier.height(10.dp))
                            
                            if (currentBiz.dueno.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dueño / Propietario:", fontSize = 11.sp, color = Slate500)
                                        Text(
                                            text = currentBiz.dueno,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    }
                                    
                                    if (currentBiz.phone.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                ContactHelper.addContactIntent(context, currentBiz.dueno, currentBiz.phone)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Agregar a Contactos", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tipo de Acceso:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.licenseType.ifBlank { "PRUEBA" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate800
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fecha de Inicio:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.startDate.ifBlank { "—" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate800
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fecha Vencimiento:", fontSize = 11.sp, color = Slate500)
                                    Text(
                                        text = currentBiz.endDate.ifBlank { "PERMANENTE" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (commStatus == CommercialStatus.LICENCIA_VENCIDA || commStatus == CommercialStatus.PRUEBA_VENCIDA) Rose600 else Slate800
                                    )
                                }
                            }

                            if (commStatus == CommercialStatus.LICENCIA_REVOCADA || currentBiz.status == "REVOCADA") {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Rose200)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "⛔ LICENCIA REVOCADA el ${currentBiz.fechaRevocacion.ifBlank { "—" }}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Rose800
                                )
                                Text(
                                    text = "Motivo de revocación: ${currentBiz.motivoRevocacion.ifBlank { "No especificado" }}",
                                    fontSize = 12.sp,
                                    color = Rose700
                                )
                            }
                        }
                    }

                    // Informational Card: Centralized Management of Q_preuba.json and Q_licencias.json
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = ElQadreNavy,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "Gestión Centralizada de Licencias",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElQadreNavy
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Q_preuba.json y Q_licencias.json se generan, actualizan y gestionan exclusivamente desde sus apartados específicos ('Pruebas 7D' y 'Licencias') del menú inferior.",
                                    fontSize = 11.sp,
                                    color = Slate600
                                )
                            }
                        }
                    }

                    // Section: Files Export
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "ARCHIVOS OPERATIVOS DEL NEGOCIO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "4 archivos generados exclusivamente para el negocio ${currentBiz.code}:",
                                fontSize = 11.sp,
                                color = Slate500
                            )


                            Spacer(modifier = Modifier.height(10.dp))
                            
                            if (currentBiz.dueno.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dueño / Propietario:", fontSize = 11.sp, color = Slate500)
                                        Text(
                                            text = currentBiz.dueno,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    }
                                    
                                    if (currentBiz.phone.isNotBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                ContactHelper.addContactIntent(context, currentBiz.dueno, currentBiz.phone)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Outlined.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Agregar a Contactos", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val file = SuperAdminBusinessManager.generateUserJsonForBusiness(context, currentBiz)
                                            SuperAdminSmsHelper.shareJsonFile(context, file, "Q_${currentBiz.code}usuarios.json")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Q_${currentBiz.code}usuarios.json", fontSize = 10.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val file = SuperAdminBusinessManager.generateCatalogoJsonForBusiness(context, currentBiz)
                                            SuperAdminSmsHelper.shareJsonFile(context, file, "Q_${currentBiz.code}catalogo.json")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Q_${currentBiz.code}catalogo.json", fontSize = 10.sp, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val file = SuperAdminBusinessManager.generateAdminJsonForBusiness(context, currentBiz)
                                            SuperAdminSmsHelper.shareJsonFile(context, file, "Q_${currentBiz.code}admin.json")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Q_${currentBiz.code}admin.json", fontSize = 10.sp, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val file = SuperAdminBusinessManager.generateDuenoJsonForBusiness(context, currentBiz)
                                            SuperAdminSmsHelper.shareJsonFile(context, file, "Q_${currentBiz.code}dueño.json")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Q_${currentBiz.code}dueño.json", fontSize = 10.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
