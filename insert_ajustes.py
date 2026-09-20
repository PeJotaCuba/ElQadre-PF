def insert_after_text(filepath, search_str, block_to_insert):
    with open(filepath, 'r') as f:
        text = f.read()
    
    # We need to find the end of the item { ... } containing the search_str
    idx = text.find(search_str)
    if idx == -1:
        print(f"Not found in {filepath}")
        return
    
    # find the next `item {` after this idx
    next_item_idx = text.find("item {", idx)
    if next_item_idx == -1:
        print("next item not found")
        return
    
    new_text = text[:next_item_idx] + block_to_insert + "\n        " + text[next_item_idx:]
    with open(filepath, 'w') as f:
        f.write(new_text)

salon_btn = """item {
            Surface(
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
                }
            }
        }
"""

barra_caja_btn = """item {
            Surface(
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
        }
"""

insert_after_text('app/src/main/java/com/example/ui/screens/salon/SalonScreen.kt', 'text = "Ajustes de Salón",', salon_btn)
insert_after_text('app/src/main/java/com/example/ui/screens/barra/BarraAjustesTab.kt', 'text = "Ajustes de Barra",', barra_caja_btn)
insert_after_text('app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt', 'text = "Ajustes de Caja",', barra_caja_btn)
print("Inserted safely.")
