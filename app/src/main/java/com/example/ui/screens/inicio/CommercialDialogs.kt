package com.example.ui.screens.inicio

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.licensing.LicenseUpdateResult
import com.example.licensing.PagoConfirmadoClientInfo
import com.example.licensing.SuperAdminSmsHelper
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TrialSmsStep {
    IDLE,
    GENERATING_SMS,
    SENDING_SMS,
    CHECKING_SMS,
    SUCCESS,
    ERROR
}

@Composable
fun SolicitudAutorizacionDialog(
    dvc: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nombreNegocio by remember { mutableStateOf("") }
    var dueno by remember { mutableStateOf("") }
    var movilPrincipal by remember { mutableStateOf("") }
    var movilAlt by remember { mutableStateOf("") }
    var ciudad by remember { mutableStateOf("") }

    var currentStep by remember { mutableStateOf(TrialSmsStep.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formattedDvc = remember(dvc) {
        if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
    }

    fun startSmsExecution() {
        errorMessage = null
        scope.launch {
            // Step 1: Generando SMS
            currentStep = TrialSmsStep.GENERATING_SMS
            val smsText = SuperAdminSmsHelper.buildAutorizacionSms(
                negocio = nombreNegocio,
                dueno = dueno,
                movilPrincipal = movilPrincipal,
                movilAlt = movilAlt,
                dvc = formattedDvc,
                ciudad = ciudad
            )
            delay(600)

            // Step 2: Enviando SMS
            currentStep = TrialSmsStep.SENDING_SMS
            val result = SuperAdminSmsHelper.sendSmsDirectOnly(context, smsText)
            delay(800)

            // Step 3: Comprobando SMS
            currentStep = TrialSmsStep.CHECKING_SMS
            delay(800)

            if (result.isSuccess) {
                currentStep = TrialSmsStep.SUCCESS
            } else {
                val ex = result.exceptionOrNull()
                errorMessage = "Error al enviar el SMS. ${ex?.localizedMessage ?: "Compruebe su señal o saldo e intente nuevamente."}"
                currentStep = TrialSmsStep.ERROR
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startSmsExecution()
        } else {
            errorMessage = "Se requieren permisos de SMS para enviar la solicitud en segundo plano."
            currentStep = TrialSmsStep.ERROR
        }
    }

    fun validateAndSubmit() {
        if (nombreNegocio.trim().isBlank()) {
            errorMessage = "Por favor ingrese el nombre del negocio"
            return
        }
        if (dueno.trim().isBlank()) {
            errorMessage = "Por favor ingrese el nombre y apellidos del dueño"
            return
        }
        if (movilPrincipal.trim().isBlank()) {
            errorMessage = "Por favor ingrese el móvil principal (MP)"
            return
        }
        if (ciudad.trim().isBlank()) {
            errorMessage = "Por favor ingrese la ciudad"
            return
        }

        val hasSend = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val hasReceive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        if (!hasSend || !hasReceive || !hasRead) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        } else {
            startSmsExecution()
        }
    }

    Dialog(
        onDismissRequest = {
            if (currentStep == TrialSmsStep.IDLE || currentStep == TrialSmsStep.SUCCESS || currentStep == TrialSmsStep.ERROR) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "SOLICITUD DE PRUEBA GRATIS",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Text(
                    text = "Solicitud de prueba gratuita de 7 días para ElQadre mediante SMS.",
                    fontSize = 13.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center
                )

                // DVC info card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DVC DEL DISPOSITIVO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                        Text(
                            text = formattedDvc,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElQadreNavy
                        )
                    }
                }

                when (currentStep) {
                    TrialSmsStep.IDLE, TrialSmsStep.ERROR -> {
                        OutlinedTextField(
                            value = nombreNegocio,
                            onValueChange = {
                                nombreNegocio = it
                                errorMessage = null
                            },
                            label = { Text("Nombre del negocio *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Store,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prueba_gratis_business_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            )
                        )

                        OutlinedTextField(
                            value = dueno,
                            onValueChange = {
                                dueno = it
                                errorMessage = null
                            },
                            label = { Text("Dueño (Nombre y apellidos) *") },
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
                                .testTag("prueba_gratis_owner_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            )
                        )

                        OutlinedTextField(
                            value = movilPrincipal,
                            onValueChange = {
                                movilPrincipal = it
                                errorMessage = null
                            },
                            label = { Text("Móvil Principal (MP) *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Phone,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prueba_gratis_phone_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            )
                        )

                        OutlinedTextField(
                            value = movilAlt,
                            onValueChange = {
                                movilAlt = it
                                errorMessage = null
                            },
                            label = { Text("Móvil Alternativo (MA - opcional)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.ContactPhone,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prueba_gratis_phone_alt_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            )
                        )

                        OutlinedTextField(
                            value = ciudad,
                            onValueChange = {
                                ciudad = it
                                errorMessage = null
                            },
                            label = { Text("Ciudad *") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = ElQadreNavy
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prueba_gratis_city_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElQadreNavy,
                                focusedLabelColor = ElQadreNavy
                            )
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ElQadreGoldSoft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Amber800,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "La solicitud se enviará automáticamente por SMS a ElQadre (${SuperAdminSmsHelper.SUPER_ADMIN_PHONE}) en segundo plano.",
                                    fontSize = 12.sp,
                                    color = Amber800,
                                    lineHeight = 17.sp
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Rose50,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = errorMessage!!,
                                    color = Rose700,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancelar")
                            }

                            Button(
                                onClick = { validateAndSubmit() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElQadreNavy,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("prueba_gratis_dialog_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentStep == TrialSmsStep.ERROR) "REINTENTAR ENVÍO" else "ENVIAR SMS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    TrialSmsStep.GENERATING_SMS, TrialSmsStep.SENDING_SMS, TrialSmsStep.CHECKING_SMS -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Slate50,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "PROCESANDO SOLICITUD DE PRUEBA",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate600,
                                    letterSpacing = 0.5.sp
                                )

                                TrialProcessStepItem(
                                    stepName = "Generando SMS",
                                    isActive = currentStep == TrialSmsStep.GENERATING_SMS,
                                    isCompleted = currentStep > TrialSmsStep.GENERATING_SMS
                                )

                                TrialProcessStepItem(
                                    stepName = "Enviando SMS",
                                    isActive = currentStep == TrialSmsStep.SENDING_SMS,
                                    isCompleted = currentStep > TrialSmsStep.SENDING_SMS
                                )

                                TrialProcessStepItem(
                                    stepName = "Comprobando SMS",
                                    isActive = currentStep == TrialSmsStep.CHECKING_SMS,
                                    isCompleted = currentStep > TrialSmsStep.CHECKING_SMS
                                )
                            }
                        }
                    }

                    TrialSmsStep.SUCCESS -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Emerald50,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Emerald600,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "✓ SMS enviado correctamente a ElQadre",
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald700,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Espere la confirmación de ElQadre para poder entrar a su negocio.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate800,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 19.sp
                                )
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElQadreNavy,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("prueba_gratis_dialog_close_button")
                        ) {
                            Text("ENTENDIDO", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrialProcessStepItem(
    stepName: String,
    isActive: Boolean,
    isCompleted: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isCompleted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Emerald600,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stepName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Slate800
            )
        } else if (isActive) {
            CircularProgressIndicator(
                color = ElQadreNavy,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "$stepName...",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Slate300, CircleShape)
            )
            Text(
                text = stepName,
                fontSize = 14.sp,
                color = Slate400
            )
        }
    }
}

@Composable
fun SolicitudActivacionDialog(
    dvc: String,
    pagoInfo: PagoConfirmadoClientInfo? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var nombreNegocio by remember { mutableStateOf(pagoInfo?.negocio ?: "") }
    var dueno by remember { mutableStateOf(pagoInfo?.dueno ?: "") }
    var movilPrincipal by remember { mutableStateOf(pagoInfo?.movilPrincipal ?: "") }
    var movilAlt by remember { mutableStateOf(pagoInfo?.movilAlt ?: "") }
    var ciudad by remember { mutableStateOf(pagoInfo?.ciudad ?: "") }

    var isSending by remember { mutableStateOf(false) }
    var sentSuccessMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val formattedDvc = remember(dvc) {
        if (dvc.startsWith("DVC-", ignoreCase = true)) dvc.uppercase() else "DVC-${dvc.uppercase()}"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
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
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VpnKey,
                        contentDescription = null,
                        tint = ElQadreNavy,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "SOLICITUD DE ACTIVACIÓN",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElQadreNavy
                    )
                }

                Text(
                    text = "Generar solicitud formal de activación de licencia comercial a ElQadre.",
                    fontSize = 13.sp,
                    color = Slate600,
                    textAlign = TextAlign.Center
                )

                if (pagoInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Emerald700,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Pago confirmado por ElQadre detectado.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Emerald800
                            )
                        }
                    }
                }

                if (sentSuccessMessage == null) {
                    OutlinedTextField(
                        value = nombreNegocio,
                        onValueChange = {
                            nombreNegocio = it
                            errorMessage = null
                        },
                        label = { Text("Nombre del negocio *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Store,
                                contentDescription = null,
                                tint = ElQadreNavy
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activacion_business_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    OutlinedTextField(
                        value = dueno,
                        onValueChange = {
                            dueno = it
                            errorMessage = null
                        },
                        label = { Text("Dueño (Nombre y apellidos) *") },
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
                            .testTag("activacion_owner_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    OutlinedTextField(
                        value = movilPrincipal,
                        onValueChange = {
                            movilPrincipal = it
                            errorMessage = null
                        },
                        label = { Text("Móvil Principal (MP) *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Phone,
                                contentDescription = null,
                                tint = ElQadreNavy
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activacion_phone_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    OutlinedTextField(
                        value = movilAlt,
                        onValueChange = {
                            movilAlt = it
                            errorMessage = null
                        },
                        label = { Text("Móvil Alternativo (MA - opcional)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.ContactPhone,
                                contentDescription = null,
                                tint = ElQadreNavy
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activacion_phone_alt_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    OutlinedTextField(
                        value = ciudad,
                        onValueChange = {
                            ciudad = it
                            errorMessage = null
                        },
                        label = { Text("Ciudad *") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = ElQadreNavy
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activacion_city_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElQadreNavy,
                            focusedLabelColor = ElQadreNavy
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Rose800,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "La solicitud se enviará por SMS a ElQadre (${SuperAdminSmsHelper.SUPER_ADMIN_PHONE}). Tras la activación de su licencia, presione ACTUALIZAR.",
                                fontSize = 12.sp,
                                color = Rose800,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar")
                        }

                        Button(
                            onClick = {
                                if (nombreNegocio.trim().isBlank()) {
                                    errorMessage = "Por favor ingrese el nombre del negocio"
                                    return@Button
                                }
                                if (dueno.trim().isBlank()) {
                                    errorMessage = "Por favor ingrese el nombre y apellidos del dueño"
                                    return@Button
                                }
                                if (movilPrincipal.trim().isBlank()) {
                                    errorMessage = "Por favor ingrese el móvil principal (MP)"
                                    return@Button
                                }
                                if (ciudad.trim().isBlank()) {
                                    errorMessage = "Por favor ingrese la ciudad"
                                    return@Button
                                }

                                isSending = true
                                val smsText = SuperAdminSmsHelper.buildActivacionSms(
                                    negocio = nombreNegocio,
                                    dueno = dueno,
                                    movilPrincipal = movilPrincipal,
                                    movilAlt = movilAlt,
                                    dvc = formattedDvc,
                                    ciudad = ciudad
                                )
                                val result = SuperAdminSmsHelper.sendSmsDirectOrIntent(context, smsText)
                                isSending = false

                                result.onSuccess { msg ->
                                    sentSuccessMessage = msg
                                }.onFailure { ex ->
                                    errorMessage = "Error al enviar SMS: ${ex.localizedMessage ?: "Error desconocido"}"
                                }
                            },
                            enabled = !isSending,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Rose700,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("activacion_dialog_send_button")
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ENVIAR SMS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // Success state
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "¡Solicitud de Activación enviada!",
                                fontWeight = FontWeight.Bold,
                                color = Emerald700,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "ElQadre procesará la activación de su licencia comercial.\n\nUna vez aprobada y publicada, presione el botón ACTUALIZAR para activar el sistema.",
                                fontSize = 12.sp,
                                color = Slate700,
                                textAlign = TextAlign.Center,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("activacion_dialog_close_button")
                    ) {
                        Text("ENTENDIDO", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo mostrado cuando el cliente presiona ACTIVACIÓN pero no se detecta la confirmación de pago por SMS de ElQadre.
 */
@Composable
fun PagoNoConfirmadoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Amber50,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = Amber800,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Text(
                    text = "Confirmación de Pago Pendiente",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Primero debe realizar el pago y esperar la confirmación por SMS de ElQadre. Después vuelva a pulsar ACTIVACIÓN.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate800,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Text(
                    text = "Opciones de contacto con ElQadre:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )

                // 1. SMS
                Button(
                    onClick = {
                        SuperAdminSmsHelper.contactElQadreBySms(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElQadreNavy,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("contact_elqadre_sms_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contactar con ElQadre por SMS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. WhatsApp
                Button(
                    onClick = {
                        SuperAdminSmsHelper.contactElQadreByWhatsApp(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald600,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("contact_elqadre_whatsapp_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contactar con ElQadre por WhatsApp",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 3. WhatsApp Business
                Button(
                    onClick = {
                        SuperAdminSmsHelper.contactElQadreByWhatsAppBusiness(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Emerald800,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("contact_elqadre_whatsapp_biz_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contactar con ElQadre por WhatsApp Business",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text("Cerrar", color = Slate700)
                }
            }
        }
    }
}

@Composable
fun ActualizarResultDialog(
    result: LicenseUpdateResult,
    onDismiss: () -> Unit
) {
    val isSuccess = result is LicenseUpdateResult.Success
    val isError = result is LicenseUpdateResult.Error

    val iconVector = when {
        isSuccess -> Icons.Filled.CheckCircle
        isError -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Info
    }

    val iconTint = when {
        isSuccess -> Emerald600
        isError -> MaterialTheme.colorScheme.error
        else -> ElQadreGoldDark
    }

    val title = when (result) {
        is LicenseUpdateResult.Success -> "Estado Actualizado"
        is LicenseUpdateResult.NoChange -> "Consulta de Autorización"
        is LicenseUpdateResult.Error -> "Error de Consulta"
    }

    val message = when (result) {
        is LicenseUpdateResult.Success -> result.message
        is LicenseUpdateResult.NoChange -> result.message
        is LicenseUpdateResult.Error -> result.message
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = ElQadreNavy,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = Slate700,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElQadreNavy,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("actualizar_result_accept_button")
            ) {
                Text("Aceptar", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}
