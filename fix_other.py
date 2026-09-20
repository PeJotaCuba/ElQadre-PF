import os

# CajeroScreen.kt fix
cajero_file = 'app/src/main/java/com/example/ui/screens/cajero/CajeroScreen.kt'
with open(cajero_file, 'r') as f:
    cajero_text = f.read()

cajero_text = cajero_text.replace("onClick = { viewModel.updateCatalogo() }", "onClick = {  }")
cajero_text = cajero_text.replace("onClick = { viewModel.updateMercainv() }", "onClick = {  }")

with open(cajero_file, 'w') as f:
    f.write(cajero_text)

# MercaderiasWorkspaceDialog.kt fix
merc_file = 'app/src/main/java/com/example/ui/screens/admin/MercaderiasWorkspaceDialog.kt'
with open(merc_file, 'r') as f:
    merc_text = f.read()

merc_text = merc_text.replace("for (merc in uiState.mercaderiasList)", "for (merc in uiState.mercaderias)")
with open(merc_file, 'w') as f:
    f.write(merc_text)

# DataUpdateManager.kt fix
data_update = 'app/src/main/java/com/example/util/DataUpdateManager.kt'
with open(data_update, 'r') as f:
    data_text = f.read()

# Replace newCategories and newProducts constructor parameters
old_cat = """                    name = catObj.getString("name"),
                    destination = catObj.optString("destination", "SALON"),
                    printerIp = catObj.optString("printerIp", "")"""
new_cat = """                    name = catObj.getString("name")"""
data_text = data_text.replace(old_cat, new_cat)

old_prod = """                    code = prodObj.getString("code"),
                    name = prodObj.getString("name"),
                    category = prodObj.getString("category"),
                    price = prodObj.getDouble("price"),
                    cost = prodObj.getDouble("cost"),
                    stock = prodObj.getInt("stock"),
                    isActive = prodObj.getBoolean("isActive"),
                    destination = prodObj.optString("destination", "BARRA")"""
new_prod = """                    code = prodObj.getString("code"),
                    name = prodObj.getString("name"),
                    category = prodObj.getString("category"),
                    price = prodObj.getDouble("price"),
                    cost = prodObj.getDouble("cost"),
                    stock = prodObj.getInt("stock")"""
data_text = data_text.replace(old_prod, new_prod)

old_merc = """                    id = mercObj.getLong("id"),
                    name = mercObj.getString("name"),
                    unit = mercObj.getString("unit"),
                    stockFisico = mercObj.getDouble("stockFisico"),
                    averageCost = mercObj.getDouble("averageCost")"""
new_merc = """                    id = mercObj.getLong("id"),
                    productId = 0,
                    acquisitionCost = mercObj.getDouble("averageCost"),
                    unitOfMeasure = mercObj.getString("unit"),
                    initialStock = mercObj.getDouble("stockFisico")"""
data_text = data_text.replace(old_merc, new_merc)

with open(data_update, 'w') as f:
    f.write(data_text)

# Daos.kt might have been patched but let's check
with open('app/src/main/java/com/example/data/local/dao/Daos.kt', 'r') as f:
    dao_text = f.read()
if "fun deleteAllCategories()" not in dao_text:
    print("WARNING: deleteAllCategories missing in Daos.kt")
if "fun deleteAllProducts()" not in dao_text:
    print("WARNING: deleteAllProducts missing in Daos.kt")

print("Fixed other files.")
