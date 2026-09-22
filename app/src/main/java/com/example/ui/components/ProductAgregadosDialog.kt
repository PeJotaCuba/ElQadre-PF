package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Product
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

data class ProductAgregadoItem(
    val id: String = "01",
    val name: String,
    val price: Double,
    val stock: Double = 0.0
)

fun parseProductAgregados(jsonStr: String?): List<ProductAgregadoItem> {
    if (jsonStr.isNullOrBlank() || jsonStr == "[]") return emptyList()
    return try {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<ProductAgregadoItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = if (obj.has("id") && obj.optString("id").isNotBlank()) {
                obj.optString("id")
            } else if (obj.has("code") && obj.optString("code").isNotBlank()) {
                obj.optString("code")
            } else {
                String.format("%02d", i + 1)
            }
            val name = obj.optString("name", "")
            val price = obj.optDouble("price", 0.0)
            val stock = if (obj.has("stock")) {
                obj.optDouble("stock", 0.0)
            } else if (obj.has("quantity")) {
                obj.optDouble("quantity", 0.0)
            } else {
                0.0
            }
            if (name.isNotBlank()) {
                list.add(ProductAgregadoItem(id = id, name = name, price = price, stock = stock))
            }
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializeProductAgregados(list: List<ProductAgregadoItem>): String {
    val arr = JSONArray()
    list.forEachIndexed { index, it ->
        val assignedId = if (it.id.isNotBlank()) it.id else String.format("%02d", index + 1)
        val obj = JSONObject()
        obj.put("id", assignedId)
        obj.put("name", it.name)
        obj.put("price", it.price)
        obj.put("stock", it.stock)
        arr.put(obj)
    }
    return arr.toString()
}

@Composable
fun ProductAgregadosDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSaveProduct: (Product) -> Unit
) {
    val initialAgregados = remember(product.agregadosList) {
        parseProductAgregados(product.agregadosList)
    }
    val agregadosList = remember { mutableStateListOf<ProductAgregadoItem>().apply { addAll(initialAgregados) } }

    var isEditingIndex by remember { mutableStateOf<Int?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    var stockInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        isEditingIndex = null
        nameInput = ""
        priceInput = ""
        stockInput = ""
        errorMessage = null
    }

    fun startEdit(index: Int, item: ProductAgregadoItem) {
        isEditingIndex = index
        nameInput = item.name
        priceInput = if (item.price % 1.0 == 0.0) item.price.toInt().toString() else item.price.toString()
        stockInput = if (item.stock % 1.0 == 0.0) item.stock.toInt().toString() else item.stock.toString()
        errorMessage = null
    }

    fun saveAgregado() {
        val trimmedName = nameInput.trim()
        val parsedPrice = priceInput.trim().toDoubleOrNull()
        val parsedStock = stockInput.trim().toDoubleOrNull() ?: 0.0

        if (trimmedName.isBlank()) {
            errorMessage = "El nombre del agregado no puede estar vacío."
            return
        }
        if (parsedPrice == null || parsedPrice < 0) {
            errorMessage = "Introduce un precio válido."
            return
        }
        if (parsedStock < 0) {
            errorMessage = "El stock disponible no puede ser negativo."
            return
        }

        val editIdx = isEditingIndex
        val assignedId = if (editIdx != null && editIdx in agregadosList.indices) {
            agregadosList[editIdx].id.ifBlank { String.format("%02d", editIdx + 1) }
        } else {
            String.format("%02d", agregadosList.size + 1)
        }

        val newItem = ProductAgregadoItem(
            id = assignedId,
            name = trimmedName,
            price = parsedPrice,
            stock = parsedStock
        )

        if (editIdx != null && editIdx in agregadosList.indices) {
            agregadosList[editIdx] = newItem
        } else {
            agregadosList.add(newItem)
        }

        // Persist immediately to product
        val updatedJson = serializeProductAgregados(agregadosList)
        val updatedProduct = product.copy(
            admitsAgregados = agregadosList.isNotEmpty(),
            agregadosList = updatedJson
        )
        onSaveProduct(updatedProduct)
        resetForm()
    }

    fun deleteAgregado(index: Int) {
        if (index in agregadosList.indices) {
            agregadosList.removeAt(index)
            val updatedJson = serializeProductAgregados(agregadosList)
            val updatedProduct = product.copy(
                admitsAgregados = agregadosList.isNotEmpty(),
                agregadosList = updatedJson
            )
            onSaveProduct(updatedProduct)
            if (isEditingIndex == index) {
                resetForm()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 16.dp, bottom = 64.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gestión de Agregados",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ElQadreNavy
                            )
                        }
                        Text(
                            text = "Producto: ${product.name} (${product.category})",
                            fontSize = 12.sp,
                            color = Slate500,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                    }
                }

                HorizontalDivider(color = Slate200)

                // Form to add or edit an agregado
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, if (isEditingIndex != null) Color(0xFF38BDF8) else Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (isEditingIndex != null) "Editar Agregado" else "Nuevo Agregado para este Producto",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isEditingIndex != null) Color(0xFF0284C7) else ElQadreNavy
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it; errorMessage = null },
                            label = { Text("Nombre del Agregado (ej: Queso Extra)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = priceInput,
                                onValueChange = { priceInput = it; errorMessage = null },
                                label = { Text("Precio (CUP)", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = stockInput,
                                onValueChange = { stockInput = it; errorMessage = null },
                                label = { Text("Stock / Disp.", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = Rose600,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditingIndex != null) {
                                TextButton(
                                    onClick = { resetForm() },
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text("Cancelar Edición", fontSize = 12.sp, color = Slate600)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Button(
                                onClick = { saveAgregado() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEditingIndex != null) Color(0xFF0284C7) else Emerald600
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(
                                    imageVector = if (isEditingIndex != null) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEditingIndex != null) "Guardar Cambios" else "Agregar",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // List of current agregados
                Text(
                    text = "Agregados del Producto (${agregadosList.size}):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = ElQadreNavy
                )

                if (agregadosList.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Este producto aún no tiene agregados configurados. Usa el formulario superior para crear el primero.",
                            fontSize = 12.sp,
                            color = Slate500,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(agregadosList) { index, item ->
                            val isCurrentEditing = isEditingIndex == index
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentEditing) Color(0xFFF0F9FF) else Slate50,
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrentEditing) Color(0xFF38BDF8) else Slate200
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.id} ${item.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = ElQadreNavy
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Precio: $${"%.2f".format(item.price)} CUP",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Emerald700
                                            )
                                            Text(
                                                text = "Stock: ${if (item.stock % 1.0 == 0.0) item.stock.toInt().toString() else item.stock} u",
                                                fontSize = 11.sp,
                                                color = Slate600
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { startEdit(index, item) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Editar",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { deleteAgregado(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Eliminar",
                                                tint = Rose600,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Done button
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("CERRAR", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
