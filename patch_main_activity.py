with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    text = f.read()

overlay = """
        if (uiState.updateStatusText == "Comprobando conexión...") {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .androidx.compose.foundation.layout.fillMaxSize()
                    .androidx.compose.foundation.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                    .androidx.compose.foundation.clickable(enabled = false) {},
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = com.example.ui.theme.ElQadreGold)
            }
        } else if (uiState.updateStatusText != null && currentUser != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearUpdateStatus() },
                title = { androidx.compose.material3.Text("Actualización", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = com.example.ui.theme.ElQadreNavy) },
                text = { androidx.compose.material3.Text(uiState.updateStatusText!!, color = com.example.ui.theme.Slate700) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { viewModel.clearUpdateStatus() }) {
                        androidx.compose.material3.Text("Aceptar", color = com.example.ui.theme.ElQadreGoldDark, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                containerColor = androidx.compose.ui.graphics.Color.White
            )
        }
    }
}
"""

if "uiState.updateStatusText == \"Comprobando conexión...\"" not in text:
    text = text.replace("    }\n}", overlay)
    with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
        f.write(text)
    print("MainActivity patched.")
else:
    print("MainActivity already patched.")
