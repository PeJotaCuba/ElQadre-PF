with open('app/src/main/java/com/example/ui/screens/admin/UsuariosSubScreen.kt', 'r') as f:
    text = f.read()

target = """                    Text("Configuración de Actualización (qusuarios.json)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var urlInput by remember(uiState.generalConfig?.urlUsuariosJson) { mutableStateOf(uiState.generalConfig?.urlUsuariosJson ?: "") }
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL de qusuarios.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.updateUrlUsuariosJson(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar URL")
                    }"""

replacement = """                    Text("Configuración de URLs de Actualización", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ElQadreNavy)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var urlInput by remember(uiState.generalConfig?.urlUsuariosJson) { mutableStateOf(uiState.generalConfig?.urlUsuariosJson ?: "") }
                    var urlCatalogo by remember(uiState.generalConfig?.urlCatalogoJson) { mutableStateOf(uiState.generalConfig?.urlCatalogoJson ?: "") }
                    var urlMercainv by remember(uiState.generalConfig?.urlMercainvJson) { mutableStateOf(uiState.generalConfig?.urlMercainvJson ?: "") }
                    
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("URL de qusuarios.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlCatalogo,
                        onValueChange = { urlCatalogo = it },
                        label = { Text("URL de qcatalogo.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = urlMercainv,
                        onValueChange = { urlMercainv = it },
                        label = { Text("URL de qmercainv.json") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.updateUrls(urlInput, urlCatalogo, urlMercainv) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar URLs")
                    }"""

text = text.replace(target, replacement)

with open('app/src/main/java/com/example/ui/screens/admin/UsuariosSubScreen.kt', 'w') as f:
    f.write(text)
print("UsuariosSubScreen patched URLs.")
