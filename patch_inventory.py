with open('app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt', 'r') as f:
    text = f.read()

imports = """import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONArray"""
text = text.replace("import com.example.ui.viewmodel.MainViewModel", "import com.example.ui.viewmodel.MainViewModel\n" + imports)

# add onGenerateInventoryClick: () -> Unit,
text = text.replace(
"""    onDeleteClick: (Mercaderia) -> Unit,
    onClearAllClick: () -> Unit
) {""",
"""    onDeleteClick: (Mercaderia) -> Unit,
    onClearAllClick: () -> Unit,
    onGenerateInventoryClick: () -> Unit = {}
) {""")

# add button to InventarioMercaderiasTab
target_btn = """                    OutlinedButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose300),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp).testTag("btn_limpiar_todo_mercaderias")
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose600)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar Todo", color = Rose600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }"""

new_btn = """                    OutlinedButton(
                        onClick = onClearAllClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose600),
                        border = BorderStroke(1.dp, Rose300),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp).testTag("btn_limpiar_todo_mercaderias")
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp), tint = Rose600)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar Todo", color = Rose600, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onGenerateInventoryClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generar Inventario", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }"""

text = text.replace(target_btn, new_btn)

# Add launcher in MercaderiasWorkspaceDialog
launcher_code = """
    val context = LocalContext.current
    
    val createInventoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("version", System.currentTimeMillis().toString())
                
                val mercaderiasArray = JSONArray()
                for (merc in uiState.mercaderiasList) {
                    val mercObj = JSONObject()
                    mercObj.put("id", merc.id) // Including ID so it maps correctly locally if we want
                    mercObj.put("name", merc.name)
                    mercObj.put("unit", merc.unit)
                    mercObj.put("stockFisico", merc.stockFisico)
                    mercObj.put("averageCost", merc.averageCost)
                    mercaderiasArray.put(mercObj)
                }
                jsonObject.put("mercaderias", mercaderiasArray)
                
                val jsonString = jsonObject.toString(4)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Toast.makeText(context, "Inventario generado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
"""

text = text.replace("    var showAddDialog by remember { mutableStateOf(false) }", "    var showAddDialog by remember { mutableStateOf(false) }\n" + launcher_code)

text = text.replace(
"""                                onClearAllClick = {
                                    showClearAllConfirmation = true
                                }""",
"""                                onClearAllClick = {
                                    showClearAllConfirmation = true
                                },
                                onGenerateInventoryClick = {
                                    createInventoryLauncher.launch("qmercainv.json")
                                }""")

with open('app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt', 'w') as f:
    f.write(text)
print("MercaderiasWorkspaceDialog patched.")
