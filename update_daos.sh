#!/bin/bash
cat << 'INNER_EOF' >> app/src/main/java/com/example/data/local/dao/Daos.kt

@Dao
interface GastoGeneralDao {
    @Query("SELECT * FROM gastos_generales ORDER BY name ASC")
    fun getAll(): Flow<List<GastoGeneral>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gasto: GastoGeneral): Long

    @Update
    suspend fun update(gasto: GastoGeneral)

    @Delete
    suspend fun delete(gasto: GastoGeneral)
}
INNER_EOF
