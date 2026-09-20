import re

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'r') as f:
    content = f.read()

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
        UsuariosProcessDialog(
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

split_str = "private fun TrialSmsReviewConfirmDialog("
parts = content.split(split_str)
content = parts[0] + """
@Composable
private fun UsuariosProcessDialog(
    request: SuperAdminSmsRequest,
    onApproved: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var initialPassword by remember { mutableStateOf("123456") }

    val business = remember {
        SuperAdminBusinessManager.getBusinessByDvcOrName(context, request.dvc, request.nombreNegocio)
    }

    val isAdminValid = remember {
        business?.users?.any { 
            it.role.equals("ADMINISTRADOR", ignoreCase = true) && 
            (it.phone.contains(request.senderAddress) || request.senderAddress.contains(it.phone))
        } ?: false
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
                    text = "Aprobar Nuevo Usuario",
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
                        Text(text = "Solicitante: ${request.solicitante} (${request.senderAddress})", fontSize = 13.sp, color = Slate800)
                    }
                }
                
                if (business == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ No se encontró el negocio registrado para este DVC/Nombre.",
                            fontSize = 12.sp,
                            color = Rose700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (!isAdminValid) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose50,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠ El número de remitente (${request.senderAddress}) no coincide con los administradores de este negocio.",
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
                            text = "✅ Administrador validado correctamente.",
                            fontSize = 12.sp,
                            color = Emerald700,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Emerald50,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "Datos del Usuario Solicitado:", fontWeight = FontWeight.Bold, color = Emerald700, fontSize = 13.sp)
                        Text(text = "• Nombre: ${request.nuevoNombre}", fontSize = 12.sp, color = Slate800)
                        Text(text = "• Usuario: ${request.nuevoUsername}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                        Text(text = "• Rol: ${request.nuevoRol}", fontSize = 12.sp, color = Slate800)
                    }
                }

                OutlinedTextField(
                    value = initialPassword,
                    onValueChange = { initialPassword = it },
                    label = { Text("Contraseña Inicial") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

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
                                if (business != null) {
                                    val (updatedBiz, userFile) = SuperAdminBusinessManager.approveUserRequest(
                                        context = context,
                                        business = business,
                                        req = request,
                                        initialPassword = initialPassword
                                    )
                                    isProcessing = false
                                    onApproved(userFile)
                                }
                            }
                        },
                        enabled = !isProcessing && isAdminValid && business != null,
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

with open('app/src/main/java/com/example/ui/screens/superadmin/SuperAdminUsuariosSmsCard.kt', 'w') as f:
    f.write(content)

