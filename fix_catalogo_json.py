with open('app/src/main/java/com/example/ui/screens/admin/CatalogoSubScreen.kt', 'r') as f:
    text = f.read()

# Fix categories generator
old_cat = """                    val catObj = JSONObject()
                    catObj.put("name", cat.name)
                    catObj.put("destination", cat.destination)
                    catObj.put("printerIp", cat.printerIp)"""
new_cat = """                    val catObj = JSONObject()
                    catObj.put("id", cat.id)
                    catObj.put("name", cat.name)
                    catObj.put("description", cat.description)
                    catObj.put("isActive", cat.isActive)"""
text = text.replace(old_cat, new_cat)

# Fix products generator
old_prod = """                    val prodObj = JSONObject()
                    prodObj.put("code", prod.code)
                    prodObj.put("name", prod.name)
                    prodObj.put("category", prod.category)
                    prodObj.put("price", prod.price)
                    prodObj.put("cost", prod.cost)
                    prodObj.put("stock", prod.stock)
                    prodObj.put("isActive", prod.isActive)
                    prodObj.put("destination", prod.destination)"""
new_prod = """                    val prodObj = JSONObject()
                    prodObj.put("id", prod.id)
                    prodObj.put("code", prod.code)
                    prodObj.put("name", prod.name)
                    prodObj.put("category", prod.category)
                    prodObj.put("price", prod.price)
                    prodObj.put("cost", prod.cost)
                    prodObj.put("stock", prod.stock)
                    prodObj.put("minStock", prod.minStock)"""
text = text.replace(old_prod, new_prod)

with open('app/src/main/java/com/example/ui/screens/admin/CatalogoSubScreen.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/example/util/DataUpdateManager.kt', 'r') as f:
    data = f.read()

old_dcat = """                    name = catObj.getString("name")"""
new_dcat = """                    id = catObj.optLong("id", 0),
                    name = catObj.getString("name"),
                    description = catObj.optString("description", ""),
                    isActive = catObj.optBoolean("isActive", true)"""
data = data.replace(old_dcat, new_dcat)

old_dprod = """                    code = prodObj.getString("code"),
                    name = prodObj.getString("name"),
                    category = prodObj.getString("category"),
                    price = prodObj.getDouble("price"),
                    cost = prodObj.getDouble("cost"),
                    stock = prodObj.getInt("stock")"""
new_dprod = """                    id = prodObj.optLong("id", 0),
                    code = prodObj.getString("code"),
                    name = prodObj.getString("name"),
                    category = prodObj.getString("category"),
                    price = prodObj.getDouble("price"),
                    cost = prodObj.getDouble("cost"),
                    stock = prodObj.getInt("stock"),
                    minStock = prodObj.optInt("minStock", 3)"""
data = data.replace(old_dprod, new_dprod)

with open('app/src/main/java/com/example/util/DataUpdateManager.kt', 'w') as f:
    f.write(data)

print("Catalogo json fields fixed.")
