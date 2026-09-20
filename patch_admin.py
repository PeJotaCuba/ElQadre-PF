with open('app/src/main/java/com/example/ui/screens/admin/UsuariosSubScreen.kt', 'r') as f:
    text = f.read()

# Imports
imports_to_add = """
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import org.json.JSONArray
"""
text = text.replace("import com.example.util.toSha256", "import com.example.util.toSha256" + imports_to_add)

# In the Composable, we need context and launcher
context_code = """    val context = LocalContext.current
    
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("version", System.currentTimeMillis().toString())
                val usersArray = JSONArray()
                for (user in uiState.users) {
                    val userObj = JSONObject()
                    userObj.put("username", user.username)
                    userObj.put("fullName", user.fullName)
                    userObj.put("passwordHash", user.passwordHash)
                    userObj.put("role", user.role.name)
                    userObj.put("montoPorProducto", user.montoPorProducto)
                    userObj.put("isActive", user.isActive)
                    usersArray.put(userObj)
                }
                jsonObject.put("usuarios", usersArray)
                val jsonString = jsonObject.toString(4)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Toast.makeText(context, "Archivo guardado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    Column(
"""
text = text.replace("    Column(\n", context_code, 1) # Replace only the first Column

# Add Export button below the "Guardar URL" button
button_code = """                    Button(
                        onClick = { viewModel.updateUrlUsuariosJson(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar URL")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { createDocumentLauncher.launch("qusuarios.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generar y Descargar qusuarios.json", color = Color.White, fontWeight = FontWeight.Bold)
                    }"""
text = text.replace("""                    Button(
                        onClick = { viewModel.updateUrlUsuariosJson(urlInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Guardar URL")
                    }""", button_code)

with open('app/src/main/java/com/example/ui/screens/admin/UsuariosSubScreen.kt', 'w') as f:
    f.write(text)
print("UsuariosSubScreen patched.")
