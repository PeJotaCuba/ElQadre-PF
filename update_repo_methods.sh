#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/data/repository/AppRepository.kt

    // Gastos Generales
    suspend fun insertGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().insert(gasto)
    suspend fun updateGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().update(gasto)
    suspend fun deleteGastoGeneral(gasto: com.example.data.local.model.GastoGeneral) = db.gastoGeneralDao().delete(gasto)
}
INNER_EOF
# Remove the existing closing brace at the end of the file before appending
sed -i '$ d' app/src/main/java/com/example/data/repository/AppRepository.kt
bash update_repo_methods.sh