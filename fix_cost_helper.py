import re

with open("app/src/main/java/com/example/util/CostCalculationHelper.kt", "r") as f:
    text = f.read()

text = re.sub(r'fun calculateIngredientDetails\(\s*productoElaboradoId:\s*Long,', 'fun calculateIngredientDetails(\n        productId: Long,', text)
text = text.replace('val ingredients = recetaIngredientes.filter { it.productoElaboradoId == productoElaboradoId }', 'val ingredients = recetaIngredientes.filter { it.productoElaboradoId == productId }')
text = re.sub(r'val elabId = prodElaborado\?\.id \?: product\.id\s+val isProdElaboradoMissing = prodElaborado == null\s+val ingredientDetails = calculateIngredientDetails\(elabId, recetaIngredientes, materiasPrimas\)', 
              'val isProdElaboradoMissing = prodElaborado == null\n        val ingredientDetails = calculateIngredientDetails(product.id, recetaIngredientes, materiasPrimas)', text)

with open("app/src/main/java/com/example/util/CostCalculationHelper.kt", "w") as f:
    f.write(text)
