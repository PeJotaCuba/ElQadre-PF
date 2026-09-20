import os
cajero_file = 'app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt'
with open(cajero_file, 'r') as f:
    text = f.read()

btn1 = """                    Button(
                        onClick = { viewModel.updateCatalogo() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Catálogo", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }"""

btn2 = """                    Button(
                        onClick = { viewModel.updateMercainv() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Actualizar Inventario", color = ElQadreNavy, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }"""

empty_btn = """                    Button(
                        onClick = {  },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                        modifier = Modifier.fillMaxWidth()
                    )"""

# In CajeroScreen, the buttons appear inside CajeroScreen proper, because my patching inserted it in `item {` inside the LazyColumn of the main Cajero tab layout ?
# Let's check where the buttons are located in the file
