package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY role ASC, fullName ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY role ASC, fullName ASC")
    suspend fun getAllUsersSync(): List<User>

    @Query("SELECT * FROM users WHERE (username = :username OR LOWER(username) = LOWER(:username)) LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Query("DELETE FROM users WHERE username = :username")
    suspend fun deleteUser(username: String)

    @Query("DELETE FROM users WHERE role = 'ADMIN'")
    suspend fun deleteAdminUsers()

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategoriesSync(): List<Category>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Update
    suspend fun updateCategory(category: Category)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)
}

@Dao
interface JornadaDao {
    @Query("SELECT * FROM jornadas ORDER BY id DESC")
    fun getAllJornadas(): Flow<List<Jornada>>

    @Query("SELECT * FROM jornadas ORDER BY id DESC")
    suspend fun getAllJornadasSync(): List<Jornada>

    @Query("SELECT * FROM jornadas WHERE isOpen = 1 ORDER BY id DESC LIMIT 1")
    fun getActiveJornada(): Flow<Jornada?>

    @Query("SELECT * FROM jornadas WHERE isOpen = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveJornadaSync(): Jornada?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJornada(jornada: Jornada): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJornadas(jornadas: List<Jornada>)

    @Update
    suspend fun updateJornada(jornada: Jornada)

    @Query("DELETE FROM jornadas WHERE id = :id")
    suspend fun deleteJornadaById(id: Long)

    @Query("DELETE FROM jornadas")
    suspend fun deleteAllJornadas()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY category ASC, name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products ORDER BY category ASC, name ASC")
    suspend fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Query("UPDATE products SET stock = stock + :qty WHERE id = :productId")
    suspend fun updateStock(productId: Long, qty: Int)
}

@Dao
interface TableOrderDao {
    @Query("SELECT * FROM table_orders WHERE status != 'COBRADA' AND status != 'CANCELADA' ORDER BY tableNumber ASC, id ASC")
    fun getOpenOrders(): Flow<List<TableOrder>>

    @Query("SELECT * FROM table_orders")
    fun getAllOrders(): Flow<List<TableOrder>>

    @Query("SELECT * FROM table_orders")
    suspend fun getAllOrdersSync(): List<TableOrder>

    @Query("SELECT * FROM order_items")
    fun getAllOrderItems(): Flow<List<OrderItem>>

    @Query("SELECT * FROM order_items")
    suspend fun getAllOrderItemsSync(): List<OrderItem>

    @Query("SELECT * FROM table_orders WHERE jornadaId = :jornadaId ORDER BY id DESC")
    fun getOrdersByJornada(jornadaId: Long): Flow<List<TableOrder>>

    @Query("SELECT COALESCE(MAX(comandaNumber), 0) FROM table_orders")
    suspend fun getMaxComandaNumber(): Int

    @Query("SELECT COALESCE(MAX(comandaNumber), 0) FROM table_orders WHERE jornadaId = :jornadaId")
    suspend fun getMaxComandaNumberForJornada(jornadaId: Long): Int

    @Query("SELECT COALESCE(MAX(cocinaNumber), 0) FROM table_orders")
    suspend fun getMaxCocinaNumber(): Int

    @Query("SELECT COALESCE(MAX(barraNumber), 0) FROM table_orders")
    suspend fun getMaxBarraNumber(): Int

    @Query("SELECT * FROM table_orders WHERE jornadaId = :jornadaId AND comandaNumber = :comandaNumber LIMIT 1")
    suspend fun getOrderByJornadaAndNumber(jornadaId: Long, comandaNumber: Int): TableOrder?

    @Query("SELECT * FROM table_orders WHERE id = :id")
    suspend fun getOrderById(id: Long): TableOrder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TableOrder): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<TableOrder>)

    @Update
    suspend fun updateOrder(order: TableOrder)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    fun getItemsForOrder(orderId: Long): Flow<List<OrderItem>>

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getItemsForOrderSync(orderId: Long): List<OrderItem>

    @Query("SELECT * FROM order_items WHERE destination = :destination AND status != 'ENTREGADO' ORDER BY id ASC")
    fun getPendingItemsByDestination(destination: String): Flow<List<OrderItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItem>)

    @Update
    suspend fun updateOrderItem(item: OrderItem)

    @Query("DELETE FROM order_items WHERE id = :itemId")
    suspend fun deleteOrderItem(itemId: Long)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItemsForOrder(orderId: Long)

    @Query("DELETE FROM table_orders")
    suspend fun deleteAllOrders()

    @Query("DELETE FROM order_items")
    suspend fun deleteAllOrderItems()
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    suspend fun getAllMovementsSync(): List<StockMovement>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovements(movements: List<StockMovement>)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAllMovements()

    @Query("DELETE FROM stock_movements WHERE jornadaId = :jornadaId")
    suspend fun deleteMovementsByJornada(jornadaId: Long)
}

@Dao
interface ProductionBatchDao {
    @Query("SELECT * FROM production_batches ORDER BY timestamp DESC")
    fun getAllBatches(): Flow<List<ProductionBatch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: ProductionBatch): Long

    @Query("DELETE FROM production_batches")
    suspend fun deleteAllBatches()
}

@Dao
interface BitacoraDao {
    @Query("SELECT * FROM bitacora_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<BitacoraEntry>>

    @Query("SELECT * FROM bitacora_entries ORDER BY timestamp DESC")
    suspend fun getAllEntriesSync(): List<BitacoraEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: BitacoraEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<BitacoraEntry>)

    @Query("DELETE FROM bitacora_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM bitacora_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM bitacora_entries WHERE category IN ('BARRA', 'CUADRE')")
    suspend fun deleteBarraCuadreEntries()

    @Query("DELETE FROM bitacora_entries WHERE category IN ('SALON', 'CUADRE')")
    suspend fun deleteSalonCuadreEntries()

    @Query("DELETE FROM bitacora_entries WHERE category IN ('CAJERO', 'CUADRE')")
    suspend fun deleteCajeroCuadreEntries()
}

@Dao
interface ConfiguracionNegocioDao {
    @Query("SELECT * FROM config_negocio WHERE id = 1 LIMIT 1")
    fun getConfig(): Flow<ConfiguracionNegocio?>

    @Query("SELECT * FROM config_negocio WHERE id = 1 LIMIT 1")
    suspend fun getConfigSync(): ConfiguracionNegocio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConfiguracionNegocio)

    @Update
    suspend fun updateConfig(config: ConfiguracionNegocio)
}

@Dao
interface ConfiguracionGeneralDao {
    @Query("SELECT * FROM config_general WHERE id = 1 LIMIT 1")
    fun getConfig(): Flow<ConfiguracionGeneral?>

    @Query("SELECT * FROM config_general WHERE id = 1 LIMIT 1")
    suspend fun getConfigSync(): ConfiguracionGeneral?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ConfiguracionGeneral)

    @Update
    suspend fun updateConfig(config: ConfiguracionGeneral)
}

@Dao
interface MateriaPrimaDao {
    @Query("SELECT * FROM materias_primas ORDER BY name ASC")
    fun getAll(): Flow<List<MateriaPrima>>

    @Query("SELECT * FROM materias_primas ORDER BY name ASC")
    suspend fun getAllSync(): List<MateriaPrima>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(materiaPrima: MateriaPrima)

    @Update
    suspend fun update(materiaPrima: MateriaPrima)

    @Delete
    suspend fun delete(materiaPrima: MateriaPrima)

    @Query("DELETE FROM materias_primas")
    suspend fun deleteAll()
}

@Dao
interface ProductoElaboradoDao {
    @Query("SELECT * FROM productos_elaborados")
    fun getAll(): Flow<List<ProductoElaborado>>

    @Query("SELECT * FROM productos_elaborados")
    suspend fun getAllSync(): List<ProductoElaborado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(producto: ProductoElaborado): Long

    @Update
    suspend fun update(producto: ProductoElaborado)

    @Delete
    suspend fun delete(producto: ProductoElaborado)

    @Query("DELETE FROM productos_elaborados")
    suspend fun deleteAll()
}

@Dao
interface RecetaIngredienteDao {
    @Query("SELECT * FROM receta_ingredientes WHERE productoElaboradoId = :productoElaboradoId")
    fun getIngredientsForProduct(productoElaboradoId: Long): Flow<List<RecetaIngrediente>>

    @Query("SELECT * FROM receta_ingredientes")
    fun getAllIngredients(): Flow<List<RecetaIngrediente>>

    @Query("SELECT * FROM receta_ingredientes")
    suspend fun getAllIngredientsSync(): List<RecetaIngrediente>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ingrediente: RecetaIngrediente)

    @Update
    suspend fun update(ingrediente: RecetaIngrediente)

    @Delete
    suspend fun delete(ingrediente: RecetaIngrediente)

    @Query("DELETE FROM receta_ingredientes WHERE productoElaboradoId = :productoElaboradoId")
    suspend fun deleteIngredientsForProduct(productoElaboradoId: Long)

    @Query("DELETE FROM receta_ingredientes")
    suspend fun deleteAll()
}

@Dao
interface MercaderiaDao {
    @Query("SELECT * FROM mercaderias")
    fun getAll(): Flow<List<Mercaderia>>

    @Query("SELECT * FROM mercaderias")
    suspend fun getAllSync(): List<Mercaderia>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mercaderia: Mercaderia): Long

    @Update
    suspend fun update(mercaderia: Mercaderia)

    @Delete
    suspend fun delete(mercaderia: Mercaderia)

    @Query("DELETE FROM mercaderias")
    suspend fun deleteAllMercaderias()

    @Query("SELECT * FROM movimientos_mercaderia ORDER BY date DESC")
    fun getAllMovimientos(): Flow<List<MovimientoMercaderia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovimiento(movimiento: MovimientoMercaderia): Long

    @Update
    suspend fun updateMovimiento(movimiento: MovimientoMercaderia)

    @Query("DELETE FROM movimientos_mercaderia")
    suspend fun deleteAllMovimientos()

    @Query("DELETE FROM movimientos_mercaderia WHERE mercaderiaId = :mercaderiaId")
    suspend fun deleteMovimientosForMercaderia(mercaderiaId: Long)
}



@Dao
interface GastoGeneralDao {
    @Query("SELECT * FROM gastos_generales ORDER BY name ASC")
    fun getAll(): Flow<List<GastoGeneral>>

    @Query("SELECT * FROM gastos_generales ORDER BY name ASC")
    suspend fun getAllSync(): List<GastoGeneral>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(gasto: GastoGeneral): Long

    @Update
    suspend fun update(gasto: GastoGeneral)

    @Delete
    suspend fun delete(gasto: GastoGeneral)

    @Query("DELETE FROM gastos_generales")
    suspend fun deleteAll()
}

@Dao
interface TandaDao {
    @Query("SELECT * FROM tandas ORDER BY date DESC")
    fun getAll(): Flow<List<Tanda>>

    @Query("SELECT * FROM tandas ORDER BY date DESC")
    suspend fun getAllSync(): List<Tanda>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tanda: Tanda): Long

    @Update
    suspend fun update(tanda: Tanda)

    @Delete
    suspend fun delete(tanda: Tanda)

    @Query("DELETE FROM tandas WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM tandas")
    suspend fun deleteAllTandas()
}

@Dao
interface MovimientoMateriaPrimaDao {
    @Query("SELECT * FROM movimientos_materia_prima ORDER BY date DESC")
    fun getAll(): Flow<List<MovimientoMateriaPrima>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movimiento: MovimientoMateriaPrima): Long

    @Query("DELETE FROM movimientos_materia_prima")
    suspend fun deleteAllMovimientosMateriaPrima()
}

@Dao
interface InversionDao {
    @Query("SELECT * FROM inversiones ORDER BY name ASC")
    fun getAll(): Flow<List<Inversion>>

    @Query("SELECT * FROM inversiones ORDER BY name ASC")
    suspend fun getAllSync(): List<Inversion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inversion: Inversion): Long

    @Update
    suspend fun update(inversion: Inversion)

    @Delete
    suspend fun delete(inversion: Inversion)

    @Query("DELETE FROM inversiones")
    suspend fun deleteAll()
}

@Dao
interface ConsumoPersonalDao {
    @Query("SELECT * FROM consumo_personal_items ORDER BY timestamp DESC")
    fun getAllConsumoPersonal(): Flow<List<ConsumoPersonalItem>>

    @Query("SELECT * FROM consumo_personal_items WHERE jornadaId = :jornadaId ORDER BY timestamp ASC")
    fun getConsumoPersonalByJornada(jornadaId: Long): Flow<List<ConsumoPersonalItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumoPersonal(item: ConsumoPersonalItem): Long

    @Query("DELETE FROM consumo_personal_items WHERE id = :id")
    suspend fun deleteConsumoPersonal(id: Long)

    @Query("DELETE FROM consumo_personal_items WHERE jornadaId = :jornadaId")
    suspend fun clearConsumoPersonalForJornada(jornadaId: Long)

    @Query("DELETE FROM consumo_personal_items")
    suspend fun deleteAllConsumoPersonal()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsumoPersonalList(items: List<ConsumoPersonalItem>)
}

@Dao
interface TransferenciaDao {
    @Query("SELECT * FROM transferencias ORDER BY receivedAt DESC")
    fun getAllTransferencias(): Flow<List<Transferencia>>

    @Query("SELECT * FROM transferencias ORDER BY receivedAt DESC")
    suspend fun getAllTransferenciasSync(): List<Transferencia>

    @Query("SELECT * FROM transferencias WHERE jornadaId = :jornadaId ORDER BY receivedAt DESC")
    fun getTransferenciasByJornada(jornadaId: Long): Flow<List<Transferencia>>

    @Query("SELECT * FROM transferencias WHERE jornadaId = :jornadaId ORDER BY receivedAt DESC")
    suspend fun getTransferenciasByJornadaSync(jornadaId: Long): List<Transferencia>

    @Query("SELECT * FROM transferencias WHERE transactionNumber = :txNumber LIMIT 1")
    suspend fun getByTransactionNumber(txNumber: String): Transferencia?

    @Query("SELECT * FROM transferencias WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Transferencia?

    @Query("SELECT * FROM transferencias WHERE comandaId = :comandaId")
    suspend fun getTransferenciasForComanda(comandaId: Long): List<Transferencia>

    @Query("SELECT * FROM transferencias WHERE comandaId = :comandaId")
    fun getTransferenciasForComandaFlow(comandaId: Long): Flow<List<Transferencia>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransferencia(transferencia: Transferencia): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransferencias(transferencias: List<Transferencia>)

    @Update
    suspend fun updateTransferencia(transferencia: Transferencia)

    @Query("DELETE FROM transferencias WHERE id = :id")
    suspend fun deleteTransferencia(id: Long)

    @Query("DELETE FROM transferencias")
    suspend fun deleteAllTransferencias()
}

@Dao
interface PaymentProposalDao {
    @Query("SELECT * FROM payment_proposals ORDER BY username ASC")
    fun getAllProposalsFlow(): Flow<List<PaymentProposal>>

    @Query("SELECT * FROM payment_proposals ORDER BY username ASC")
    suspend fun getAllProposalsSync(): List<PaymentProposal>

    @Query("SELECT * FROM payment_proposals WHERE username = :username LIMIT 1")
    suspend fun getProposalByUsername(username: String): PaymentProposal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProposal(proposal: PaymentProposal)

    @Update
    suspend fun updateProposal(proposal: PaymentProposal)

    @Query("DELETE FROM payment_proposals WHERE username = :username")
    suspend fun deleteProposal(username: String)

    @Query("DELETE FROM payment_proposals")
    suspend fun deleteAllProposals()
}

@Dao
interface SmsComandaQueueDao {
    @Query("SELECT * FROM sms_comandas_queue ORDER BY receivedAt ASC")
    fun getAllComandasQueue(): Flow<List<SmsComandaQueue>>

    @Query("SELECT * FROM sms_comandas_queue ORDER BY receivedAt ASC")
    suspend fun getAllComandasQueueSync(): List<SmsComandaQueue>

    @Query("SELECT * FROM sms_comandas_queue WHERE estado = 'PENDIENTE' ORDER BY receivedAt ASC")
    fun getPendingComandasQueue(): Flow<List<SmsComandaQueue>>

    @Query("SELECT * FROM sms_comandas_queue WHERE estado = 'PENDIENTE' ORDER BY receivedAt ASC")
    suspend fun getPendingComandasQueueSync(): List<SmsComandaQueue>

    @Query("SELECT * FROM sms_comandas_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsComandaQueue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmsComanda(smsComanda: SmsComandaQueue): Long

    @Update
    suspend fun updateSmsComanda(smsComanda: SmsComandaQueue)

    @Query("UPDATE sms_comandas_queue SET estado = :estado WHERE id = :id")
    suspend fun updateEstado(id: Long, estado: String)

    @Query("SELECT * FROM sms_comandas_queue WHERE smsText = :smsText AND estado = 'PROCESADA' AND (jornadaId = :jornadaId OR :jornadaId = 0) LIMIT 1")
    suspend fun findProcessedDuplicate(smsText: String, jornadaId: Long): SmsComandaQueue?

    @Query("DELETE FROM sms_comandas_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sms_comandas_queue")
    suspend fun deleteAll()
}

@Dao
interface PersonalContratadoDao {
    @Query("SELECT * FROM personal_contratado ORDER BY id DESC")
    fun getAllPersonalFlow(): Flow<List<PersonalContratado>>

    @Query("SELECT * FROM personal_contratado ORDER BY id DESC")
    suspend fun getAllPersonalSync(): List<PersonalContratado>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonal(personal: PersonalContratado): Long

    @Update
    suspend fun updatePersonal(personal: PersonalContratado)

    @Delete
    suspend fun deletePersonal(personal: PersonalContratado)

    @Query("SELECT * FROM personal_contratado WHERE (username = :username OR LOWER(username) = LOWER(:username)) LIMIT 1")
    suspend fun getPersonalByUsername(username: String): PersonalContratado?

    @Query("SELECT * FROM personal_contratado WHERE carnetIdentidad = :carnet LIMIT 1")
    suspend fun getPersonalByCarnet(carnet: String): PersonalContratado?

    @Query("SELECT COUNT(*) FROM personal_contratado WHERE tieneAccesoApp = 1 AND (UPPER(role) = 'DUENO' OR UPPER(role) = 'DUEÑO')")
    suspend fun countDuenos(): Int

    @Query("SELECT COUNT(*) FROM personal_contratado WHERE tieneAccesoApp = 1 AND (UPPER(role) = 'DUENO' OR UPPER(role) = 'DUEÑO') AND id != :excludeId")
    suspend fun countDuenosExcluding(excludeId: Long): Int

    @Query("DELETE FROM personal_contratado")
    suspend fun deleteAllPersonal()
}




