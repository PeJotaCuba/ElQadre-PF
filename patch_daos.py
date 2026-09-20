with open('app/src/main/java/com/example/data/local/dao/Daos.kt', 'r') as f:
    text = f.read()

# CategoryDao
if "fun deleteAllCategories()" not in text:
    text = text.replace(
        "    suspend fun insertCategory(category: Category): Long\n",
        "    suspend fun insertCategory(category: Category): Long\n\n    @Query(\"DELETE FROM categories\")\n    suspend fun deleteAllCategories()\n"
    )

# ProductDao
if "fun deleteAllProducts()" not in text:
    text = text.replace(
        "    suspend fun insertProduct(product: Product): Long\n",
        "    suspend fun insertProduct(product: Product): Long\n\n    @Query(\"DELETE FROM products\")\n    suspend fun deleteAllProducts()\n"
    )

with open('app/src/main/java/com/example/data/local/dao/Daos.kt', 'w') as f:
    f.write(text)
print("Daos patched correctly.")
