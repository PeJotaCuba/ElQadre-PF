@Composable
fun AgregarTransferenciaExternaDialog(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var smsPastedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var foundSmsList by remember { mutableStateOf<List<com.example.util.ParsedTransferSms>?>(null) }
    var selectedSms by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val existingTxs = uiState.allTransferencias.map { it.transactionNumber }.toSet()
            foundSmsList = com.example.util.SmsSearchHelper.findUnregisteredPagoXMovil(context, existingTxs)
            if (foundSmsList?.isEmpty() == true) {
                errorMessage = "No se encontraron pagos nuevos sin registrar."
            } else {
                errorMessage = null
            }
        } else {
            errorMessage = "Permiso de SMS denegado."
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .padding(vertical = 12.dp)
                .testTag("dialog_agregar_transferencia_externa")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Banner
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
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF3E8FF)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AddCard,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "TRANSFERENCIA EXTERNA",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Pegar SMS o Buscar pagos perdidos",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400)
                    }
                }

                HorizontalDivider(color = Slate100)

                // SMS text field
                OutlinedTextField(
                    value = smsPastedText,
                    onValueChange = { smsPastedText = it },
                    label = { Text("Pegar SMS completo de PAGOxMOVIL") },
                    placeholder = { Text("Ej. El titular del telefono 5351110746 le ha realizado una transferencia...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("input_manual_sms"),
                    maxLines = 5
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                val existingTxs = uiState.allTransferencias.map { it.transactionNumber }.toSet()
                                foundSmsList = com.example.util.SmsSearchHelper.findUnregisteredPagoXMovil(context, existingTxs)
                                if (foundSmsList?.isEmpty() == true) {
                                    errorMessage = "No se encontraron pagos nuevos sin registrar."
                                } else {
                                    errorMessage = null
                                }
                            } else {
                                launcher.launch(android.Manifest.permission.READ_SMS)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BUSCAR PAGO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val parsed = com.example.util.SmsTransferParser.parseTransferSms(smsPastedText)
                            if (parsed == null) {
                                errorMessage = "El texto pegado no es un SMS válido de PAGOxMOVIL."
                                return@Button
                            }
                            viewModel.registrarTransferenciaManual(
                                parsed = parsed,
                                onSuccess = {
                                    Toast.makeText(context, "Transferencia registrada correctamente", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                                onError = { err -> errorMessage = err }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("REGISTRAR PEGADO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (foundSmsList != null && foundSmsList!!.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "PAGOS ENCONTRADOS — NO REGISTRADOS (${foundSmsList!!.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFB45309)
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(foundSmsList!!) { tx ->
                            val isSelected = selectedSms.contains(tx.transactionNumber)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFECFDF5) else Slate50,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Emerald600 else Slate200
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedSms = if (isSelected) {
                                            selectedSms - tx.transactionNumber
                                        } else {
                                            selectedSms + tx.transactionNumber
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { 
                                            selectedSms = if (it) {
                                                selectedSms + tx.transactionNumber
                                            } else {
                                                selectedSms - tx.transactionNumber
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Nro: ${tx.transactionNumber}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ElQadreNavy
                                        )
                                        Text(
                                            text = "Fecha SMS: ${tx.dateStr}",
                                            fontSize = 11.sp,
                                            color = Slate500
                                        )
                                        if (tx.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = "Teléfono: ${tx.phoneNumber}",
                                                fontSize = 11.sp,
                                                color = Slate500
                                            )
                                        }
                                    }
                                    Text(
                                        text = "$${"%.2f".format(tx.amount)} ${tx.currency}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        color = Emerald600
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSms.isNotEmpty()) {
                        Button(
                            onClick = {
                                val toRegister = foundSmsList!!.filter { selectedSms.contains(it.transactionNumber) }
                                // We iterate and register. In a real app we'd do a batch insert, but here we can just loop.
                                var count = 0
                                toRegister.forEach { parsed ->
                                    viewModel.registrarTransferenciaManual(
                                        parsed = parsed,
                                        onSuccess = {
                                            count++
                                            if (count == toRegister.size) {
                                                Toast.makeText(context, "$count transferencias registradas.", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        },
                                        onError = { err -> errorMessage = err }
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REGISTRAR SELECCIONADOS (${selectedSms.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
