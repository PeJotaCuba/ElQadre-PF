import os

def patch_salon():
    path = 'app/src/main/java/com/example/ui/screens/salon/SalonScreen.kt'
    with open(path, 'r') as f:
        text = f.read()
    
    if "Actualizar Catálogo" not in text:
        btn = """
        item {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actualizaciones", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ElQadreNavy)
                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
        
        item {"""
        text = text.replace("        item {\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,", btn + "\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,")
        with open(path, 'w') as f:
            f.write(text)

def patch_barra():
    path = 'app/src/main/java/com/example/ui/screens/barra/BarraAjustesTab.kt'
    with open(path, 'r') as f:
        text = f.read()
    
    if "Actualizar Catálogo" not in text:
        btn = """
        item {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actualizaciones", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ElQadreNavy)
                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.updateMercainv() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
        
        item {"""
        text = text.replace("        item {\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,", btn + "\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,")
        with open(path, 'w') as f:
            f.write(text)

def patch_caja():
    path = 'app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt'
    with open(path, 'r') as f:
        text = f.read()
    
    if "Actualizar Catálogo" not in text:
        btn = """
        item {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Actualizaciones", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = ElQadreNavy)
                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.updateMercainv() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
        
        item {"""
        text = text.replace("        item {\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,", btn + "\n            Surface(\n                shape = RoundedCornerShape(14.dp),\n                color = Color.White,")
        with open(path, 'w') as f:
            f.write(text)

patch_salon()
patch_barra()
patch_caja()
print("Ajustes patched.")
