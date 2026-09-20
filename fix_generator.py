with open('app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt', 'r') as f:
    text = f.read()

# Fix the JSON generation in MercaderiasWorkspaceDialog
old_json_merc = """                    val mercObj = JSONObject()
                    mercObj.put("id", merc.id) // Including ID so it maps correctly locally if we want
                    mercObj.put("name", merc.name)
                    mercObj.put("unit", merc.unit)
                    mercObj.put("stockFisico", merc.stockFisico)
                    mercObj.put("averageCost", merc.averageCost)"""
new_json_merc = """                    val mercObj = JSONObject()
                    mercObj.put("id", merc.id)
                    mercObj.put("productId", merc.productId)
                    mercObj.put("acquisitionCost", merc.acquisitionCost)
                    mercObj.put("unitOfMeasure", merc.unitOfMeasure)
                    mercObj.put("initialStock", merc.initialStock)
                    mercObj.put("isActive", merc.isActive)"""

text = text.replace(old_json_merc, new_json_merc)
with open('app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt', 'w') as f:
    f.write(text)

# Also fix DataUpdateManager.kt
with open('app/src/main/java/com/example/util/DataUpdateManager.kt', 'r') as f:
    data = f.read()

old_manager_merc = """                    id = mercObj.getLong("id"),
                    productId = 0,
                    acquisitionCost = mercObj.getDouble("averageCost"),
                    unitOfMeasure = mercObj.getString("unit"),
                    initialStock = mercObj.getDouble("stockFisico")"""

new_manager_merc = """                    id = mercObj.getLong("id"),
                    productId = mercObj.getLong("productId"),
                    acquisitionCost = mercObj.getDouble("acquisitionCost"),
                    unitOfMeasure = mercObj.getString("unitOfMeasure"),
                    initialStock = mercObj.getDouble("initialStock"),
                    isActive = mercObj.optBoolean("isActive", true)"""
data = data.replace(old_manager_merc, new_manager_merc)
with open('app/src/main/java/com/example/util/DataUpdateManager.kt', 'w') as f:
    f.write(data)

print("Generator fixed.")
