with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'r') as f:
    text = f.read()

# 1. Remove the inline text for updateStatusText
to_remove = """                    if (uiState.updateStatusText != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.updateStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (uiState.updateStatusText.contains("Error") || uiState.updateStatusText.contains("inválido") || uiState.updateStatusText.contains("No hay")) Rose700 else Emerald600,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }"""
text = text.replace(to_remove, "")

# 2. Add the overlay and dialog at the end of the root Box
end_marker = "            Spacer(modifier = Modifier.height(32.dp))\n        }\n    }\n}"
overlay_code = """            Spacer(modifier = Modifier.height(32.dp))
        }

        if (uiState.updateStatusText == "Comprobando conexión...") {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .androidx.compose.ui.input.pointer.pointerInput(Unit) {},
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElQadreGold)
            }
        } else if (uiState.updateStatusText != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { viewModel.clearUpdateStatus() },
                title = { Text("Actualización", fontWeight = FontWeight.Bold, color = ElQadreNavy) },
                text = { Text(uiState.updateStatusText, color = Slate700) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearUpdateStatus() }) {
                        Text("Aceptar", color = ElQadreGoldDark, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color.White
            )
        }
    }
}"""
text = text.replace(end_marker, overlay_code)

with open('app/src/main/java/com/example/ui/screens/login/LoginScreen.kt', 'w') as f:
    f.write(text)
print("LoginScreen patched.")
