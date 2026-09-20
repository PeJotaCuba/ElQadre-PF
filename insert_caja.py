def insert_after_text(filepath, search_str, block_to_insert):
    with open(filepath, 'r') as f:
        text = f.read()
    
    idx = text.find(search_str)
    if idx == -1:
        print(f"Not found in {filepath}")
        return
    
    # Cajero settings doesn't use LazyColumn, it's a Column.
    # So we can just append the block inside the Column.
    
    new_text = text[:idx] + search_str + "\n\n" + block_to_insert + text[idx+len(search_str):]
    with open(filepath, 'w') as f:
        f.write(new_text)

caja_btn = """        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Actualizaciones", fontWeight = FontWeight.Bold, color = ElQadreNavy)
                Button(
                    onClick = { viewModel.updateCatalogo() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.updateMercainv() },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = FontWeight.Bold)
                }
            }
        }
"""

insert_after_text('app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt', 'text = "Configuración del Cajero",\n            fontWeight = FontWeight.Bold,\n            fontSize = 20.sp,\n            color = ElQadreNavy\n        )', caja_btn)
print("Inserted safely.")
