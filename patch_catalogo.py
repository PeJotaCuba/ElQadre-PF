with open('app/src/main/java/com/example/ui/screens/admin/CatalogoSubScreen.kt', 'r') as f:
    text = f.read()

imports = """
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import org.json.JSONObject
import org.json.JSONArray
"""
text = text.replace("import com.example.ui.viewmodel.MainViewModel", "import com.example.ui.viewmodel.MainViewModel\n" + imports)

launcher_code = """
    val context = LocalContext.current
    
    val createCatalogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val jsonObject = JSONObject()
                jsonObject.put("version", System.currentTimeMillis().toString())
                
                val categoriesArray = JSONArray()
                for (cat in uiState.categories) {
                    val catObj = JSONObject()
                    catObj.put("name", cat.name)
                    catObj.put("destination", cat.destination)
                    catObj.put("printerIp", cat.printerIp)
                    categoriesArray.put(catObj)
                }
                jsonObject.put("categories", categoriesArray)
                
                val productsArray = JSONArray()
                for (prod in uiState.products) {
                    val prodObj = JSONObject()
                    prodObj.put("code", prod.code)
                    prodObj.put("name", prod.name)
                    prodObj.put("category", prod.category)
                    prodObj.put("price", prod.price)
                    prodObj.put("cost", prod.cost)
                    prodObj.put("stock", prod.stock)
                    prodObj.put("isActive", prod.isActive)
                    prodObj.put("destination", prod.destination)
                    productsArray.put(prodObj)
                }
                jsonObject.put("products", productsArray)
                
                val jsonString = jsonObject.toString(4)
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                Toast.makeText(context, "Catálogo generado exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
"""

text = text.replace("    var showAddCategoryDialog by remember { mutableStateOf(false) }", "    var showAddCategoryDialog by remember { mutableStateOf(false) }\n" + launcher_code)

button_code = """        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Gestión de Catálogo", style = MaterialTheme.typography.titleLarge, color = ElQadreNavy, fontWeight = FontWeight.Bold)
            Button(
                onClick = { createCatalogLauncher.launch("qcatalogo.json") },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
            ) {
                Text("Generar Catálogo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
"""
text = text.replace("""        Text("Gestión de Catálogo", style = MaterialTheme.typography.titleLarge, color = ElQadreNavy, fontWeight = FontWeight.Bold)""", button_code)

with open('app/src/main/java/com/example/ui/screens/admin/CatalogoSubScreen.kt', 'w') as f:
    f.write(text)
print("CatalogoSubScreen patched.")
