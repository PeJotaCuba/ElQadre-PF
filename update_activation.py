import re

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'r') as f:
    content = f.read()

# Replace TrialSmsReviewConfirmDialog logic
old_dialog_usage = """    requestToReview?.let { req ->
        TrialSmsReviewConfirmDialog(
            request = req,
            onConfirm = { name, dueno, mp, ma, ciudad, dvc ->
                coroutineScope.launch {
                    val result = SuperAdminSmsHelper.processTrialRequest(
                        context = context,
                        request = req,
                        customName = name,
                        customDueno = dueno,
                        customMp = mp,
                        customMa = ma,
                        customCiudad = ciudad,
                        customDvc = dvc
                    )
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    successBusinessRecord = result.first
                    generatedFileToShare = result.second
                }
            },
            onDismiss = { requestToReview = null }
        )
    }"""

new_dialog_usage = """    requestToReview?.let { req ->
        ActivationProcessDialog(
            request = req,
            onApproved = { file ->
                coroutineScope.launch {
                    SuperAdminSmsHelper.markSmsAsProcessed(context, req.uniqueKey)
                    requestToReview = null
                    doScan()
                    onBusinessCreatedOrUpdated()
                    generatedFileToShare = file
                }
            },
            onDismiss = { requestToReview = null }
        )
    }"""

content = content.replace(old_dialog_usage, new_dialog_usage)

# Now we need to append the ActivationProcessDialog, replacing TrialSmsReviewConfirmDialog definition
# We'll just truncate everything from `private fun TrialSmsReviewConfirmDialog` to the end, and write our own.
split_str = "private fun TrialSmsReviewConfirmDialog("
parts = content.split(split_str)
content = parts[0] + """
import com.example.licensing.SuperAdminPaymentHelper

@Composable
private fun ActivationProcessDialog(
    request: SuperAdminSmsRequest,
    onApproved: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    
    var tipoLicencia by remember { mutableStateOf("MENSUAL") }
    var duracionDias by remember { mutableStateOf(30) }
    var showLicenciaDropdown by remember { mutableStateOf(false) }

    val hasPayment = remember {
        SuperAdminPaymentHelper.hasConfirmedPaymentForBusiness(context, businessName = request.nombreNegocio, phone = request.numeroMovil)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Aprobar Activación",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElQadreNavy
                )
                
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate100,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "DVC: ${request.dvc}", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = ElQadreNavy)
                        Text(text = "Negocio: ${request.nombreNegocio}", fontSize = 13.sp, color = Slate800)
                        Text(text = "Móvil: ${request.numeroMovil}", fontSize = 13.sp, color = Slate800)
                    }
                }
                
                if (!hasPayment) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ No se encontró un pago confirmado para este negocio. Verifique los pagos antes de activar.",
                            fontSize = 12.sp,
                            color = Rose700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "✅ Pago confirmado para este negocio.",
                            fontSize = 12.sp,
                            color = Emerald700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Text(
                    text = "Tipo y Vigencia de la Licencia:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ElQadreNavy
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showLicenciaDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(tipoLicencia, fontSize = 12.sp, color = ElQadreNavy)
                        }
                        DropdownMenu(
                            expanded = showLicenciaDropdown,
                            onDismissRequest = { showLicenciaDropdown = false }
                        ) {
                            DropdownMenuItem(text = { Text("MENSUAL") }, onClick = { tipoLicencia = "MENSUAL"; duracionDias = 30; showLicenciaDropdown = false })
                            DropdownMenuItem(text = { Text("ANUAL") }, onClick = { tipoLicencia = "ANUAL"; duracionDias = 365; showLicenciaDropdown = false })
                            DropdownMenuItem(text = { Text("PERMANENTE") }, onClick = { tipoLicencia = "PERMANENTE"; duracionDias = 36500; showLicenciaDropdown = false })
                        }
                    }
                    OutlinedTextField(
                        value = duracionDias.toString(),
                        onValueChange = { duracionDias = it.toIntOrNull() ?: 30 },
                        label = { Text("Días") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar")
                    }
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isProcessing = true
                                val file = SuperAdminSmsHelper.generateOrUpdateLicenciasJson(
                                    context = context,
                                    dvc = request.dvc,
                                    nombreNegocio = request.nombreNegocio,
                                    numeroMovil = request.numeroMovil,
                                    tipoLicencia = tipoLicencia,
                                    durationDays = duracionDias
                                )
                                isProcessing = false
                                onApproved(file)
                            }
                        },
                        enabled = !isProcessing && hasPayment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Aprobar",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
"""

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminActivationSmsCard.kt', 'w') as f:
    f.write(content)

