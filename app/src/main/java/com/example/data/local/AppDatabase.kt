package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import com.example.util.toSha256

@Database(
    entities = [
        User::class,
        Category::class,
        Jornada::class,
        Product::class,
        TableOrder::class,
        OrderItem::class,
        StockMovement::class,
        ProductionBatch::class,
        BitacoraEntry::class,
        ConfiguracionNegocio::class,
        ConfiguracionGeneral::class,
        MateriaPrima::class,
        ProductoElaborado::class,
        RecetaIngrediente::class,
        Mercaderia::class,
        MovimientoMercaderia::class,
        GastoGeneral::class,
        Tanda::class,
        MovimientoMateriaPrima::class,
        Inversion::class,
        ConsumoPersonalItem::class,
        Transferencia::class,
        PaymentProposal::class,
        SmsComandaQueue::class,
        PersonalContratado::class
    ],
    version = 47,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun jornadaDao(): JornadaDao
    abstract fun productDao(): ProductDao
    abstract fun tableOrderDao(): TableOrderDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun productionBatchDao(): ProductionBatchDao
    abstract fun bitacoraDao(): BitacoraDao
    abstract fun configuracionNegocioDao(): ConfiguracionNegocioDao
    abstract fun configuracionGeneralDao(): ConfiguracionGeneralDao
    abstract fun materiaPrimaDao(): MateriaPrimaDao
    abstract fun productoElaboradoDao(): ProductoElaboradoDao
    abstract fun recetaIngredienteDao(): RecetaIngredienteDao
    abstract fun mercaderiaDao(): MercaderiaDao
    abstract fun gastoGeneralDao(): GastoGeneralDao
    abstract fun tandaDao(): TandaDao
    abstract fun movimientoMateriaPrimaDao(): MovimientoMateriaPrimaDao
    abstract fun inversionDao(): InversionDao
    abstract fun consumoPersonalDao(): ConsumoPersonalDao
    abstract fun transferenciaDao(): TransferenciaDao
    abstract fun paymentProposalDao(): PaymentProposalDao
    abstract fun smsComandaQueueDao(): SmsComandaQueueDao
    abstract fun personalContratadoDao(): PersonalContratadoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) { override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {} }

        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE products ADD COLUMN unitOfMeasure TEXT NOT NULL DEFAULT 'Unidad'")
                db.execSQL("ALTER TABLE products ADD COLUMN imagePath TEXT")
                // Migrate receta_ingredientes to use Product.id instead of ProductoElaborado.id
                db.execSQL("UPDATE receta_ingredientes SET productoElaboradoId = (SELECT productId FROM productos_elaborados WHERE id = receta_ingredientes.productoElaboradoId)")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `gastos_generales` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, `period` TEXT NOT NULL, `category` TEXT NOT NULL, `isActive` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN recipeName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN productionUnit TEXT NOT NULL DEFAULT 'unidades'")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN baseYield REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN baseMateriaPrimaId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN baseQuantity REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE materias_primas ADD COLUMN stock REAL NOT NULL DEFAULT 0.0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `tandas` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `date` INTEGER NOT NULL, `responsibleUser` TEXT NOT NULL, `baseMateriaPrimaId` INTEGER NOT NULL, `baseMateriaPrimaName` TEXT NOT NULL, `baseQuantityUsed` REAL NOT NULL, `baseQuantityUnit` TEXT NOT NULL, `productionFactor` REAL NOT NULL, `estimatedYield` REAL NOT NULL, `productionUnit` TEXT NOT NULL, `ingredientsConsumedText` TEXT NOT NULL, `status` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `movimientos_materia_prima` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `materiaPrimaId` INTEGER NOT NULL, `materiaPrimaName` TEXT NOT NULL, `type` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `date` INTEGER NOT NULL, `responsibleUser` TEXT NOT NULL, `notes` TEXT NOT NULL)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN estimatedDailyQuantity REAL NOT NULL DEFAULT 10.0")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN precioDefinitivo REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN hasPrecioDefinitivo INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN targetMarginPct REAL NOT NULL DEFAULT 30.0")
                db.execSQL("ALTER TABLE gastos_generales ADD COLUMN periodDays INTEGER NOT NULL DEFAULT 30")
                db.execSQL("ALTER TABLE gastos_generales ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materias_primas ADD COLUMN initialStock REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE movimientos_materia_prima ADD COLUMN resultingStock REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE materias_primas ADD COLUMN purchasePrice REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE materias_primas ADD COLUMN purchaseUnit TEXT NOT NULL DEFAULT 'g'")
            }
        }

        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `inversiones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `amount` REAL NOT NULL, `date` INTEGER NOT NULL, `usefulLife` REAL NOT NULL, `usefulLifeUnit` TEXT NOT NULL, `observation` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE `gastos_generales` ADD COLUMN `inversionId` INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE productos_elaborados ADD COLUMN ppd REAL NOT NULL DEFAULT 10.0")
            }
        }

        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tandas ADD COLUMN jornada TEXT NOT NULL DEFAULT 'Jornada Mañana'")
                db.execSQL("ALTER TABLE tandas ADD COLUMN observation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tandas ADD COLUMN laborCostType TEXT NOT NULL DEFAULT 'NINGUNO'")
                db.execSQL("ALTER TABLE tandas ADD COLUMN laborCostValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN totalLaborCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN totalDirectIngredientsCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN totalIndirectCostAllocated REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN totalBatchCost REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN realUnitCost REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tandas ADD COLUMN tandaNumber TEXT NOT NULL DEFAULT '01'")
                db.execSQL("ALTER TABLE tandas ADD COLUMN expectedYield REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN actualYield REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN yieldPercentage REAL NOT NULL DEFAULT 100.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN expectedRevenue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN estimatedProfit REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN profitMargin REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN inventoryDeducted INTEGER NOT NULL DEFAULT 1")
            }
        }

        
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gastos_generales ADD COLUMN scope TEXT NOT NULL DEFAULT 'PRODUCCION'")
                db.execSQL("ALTER TABLE inversiones ADD COLUMN scope TEXT NOT NULL DEFAULT 'PRODUCCION'")
            }
        }

        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `consumo_personal_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jornadaId` INTEGER NOT NULL, `productId` INTEGER NOT NULL, `productName` TEXT NOT NULL, `unitPrice` REAL NOT NULL, `quantity` INTEGER NOT NULL, `totalAmount` REAL NOT NULL, `recordedBy` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE table_orders ADD COLUMN confirmedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN servedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN serviceDurationSeconds INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN montoPorProducto REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE jornadas ADD COLUMN utilidadSalonMontoUnitario REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE config_general ADD COLUMN urlUsuariosJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE config_general ADD COLUMN lastUserUpdateDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE config_general ADD COLUMN lastUserUpdateStatus TEXT NOT NULL DEFAULT 'PENDIENTE'")
                db.execSQL("ALTER TABLE config_general ADD COLUMN lastUserUpdateVersion TEXT NOT NULL DEFAULT ''")
            }
        }

                val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE config_general ADD COLUMN urlCatalogoJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateStatus TEXT NOT NULL DEFAULT 'PENDIENTE'")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastCatalogoUpdateVersion TEXT NOT NULL DEFAULT ''")
                
                database.execSQL("ALTER TABLE config_general ADD COLUMN urlMercainvJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateDate INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateStatus TEXT NOT NULL DEFAULT 'PENDIENTE'")
                database.execSQL("ALTER TABLE config_general ADD COLUMN lastMercainvUpdateVersion TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE gastos_generales ADD COLUMN targetProductId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE inversiones ADD COLUMN targetProductId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE tandas ADD COLUMN ownerPayType TEXT NOT NULL DEFAULT 'NINGUNO'")
                db.execSQL("ALTER TABLE tandas ADD COLUMN ownerPayValue REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE tandas ADD COLUMN totalOwnerPay REAL NOT NULL DEFAULT 0.0")
            }
        }

        private val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE table_orders ADD COLUMN currency TEXT NOT NULL DEFAULT 'CUP'")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN exchangeRate REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN originalAmount REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE table_orders ADD COLUMN amountInCurrency REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE config_general ADD COLUMN tasaUsd REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE config_general ADD COLUMN tasaEur REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Recreate table 'table_orders' to safely make 'tableNumber' nullable (INTEGER instead of INTEGER NOT NULL)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `table_orders_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `tableNumber` INTEGER, 
                        `customerName` TEXT NOT NULL, 
                        `waiterUsername` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `closedAt` INTEGER, 
                        `status` TEXT NOT NULL, 
                        `totalAmount` REAL NOT NULL, 
                        `paymentMethod` TEXT NOT NULL, 
                        `tip` REAL NOT NULL, 
                        `jornadaId` INTEGER NOT NULL, 
                        `comandaNumber` INTEGER NOT NULL, 
                        `cocinaNumber` INTEGER NOT NULL, 
                        `barraNumber` INTEGER NOT NULL, 
                        `totalCocina` REAL NOT NULL, 
                        `totalBarra` REAL NOT NULL, 
                        `cashReceived` REAL NOT NULL, 
                        `changeGiven` REAL NOT NULL, 
                        `confirmedAt` INTEGER, 
                        `servedAt` INTEGER, 
                        `serviceDurationSeconds` INTEGER NOT NULL, 
                        `currency` TEXT NOT NULL DEFAULT 'CUP', 
                        `exchangeRate` REAL NOT NULL DEFAULT 1.0, 
                        `originalAmount` REAL NOT NULL DEFAULT 0.0, 
                        `amountInCurrency` REAL NOT NULL DEFAULT 0.0
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `table_orders_new` (
                        `id`, `tableNumber`, `customerName`, `waiterUsername`, `createdAt`, `closedAt`, `status`,
                        `totalAmount`, `paymentMethod`, `tip`, `jornadaId`, `comandaNumber`, `cocinaNumber`, `barraNumber`,
                        `totalCocina`, `totalBarra`, `cashReceived`, `changeGiven`, `confirmedAt`, `servedAt`,
                        `serviceDurationSeconds`, `currency`, `exchangeRate`, `originalAmount`, `amountInCurrency`
                    )
                    SELECT 
                        `id`, `tableNumber`, `customerName`, `waiterUsername`, `createdAt`, `closedAt`, `status`,
                        `totalAmount`, `paymentMethod`, `tip`, `jornadaId`, `comandaNumber`, `cocinaNumber`, `barraNumber`,
                        `totalCocina`, `totalBarra`, `cashReceived`, `changeGiven`, `confirmedAt`, `servedAt`,
                        `serviceDurationSeconds`, `currency`, `exchangeRate`, `originalAmount`, `amountInCurrency`
                    FROM `table_orders`
                """.trimIndent())

                db.execSQL("DROP TABLE `table_orders`")
                db.execSQL("ALTER TABLE `table_orders_new` RENAME TO `table_orders`")
            }
        }

        val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `admitsAgregados` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `products` ADD COLUMN `agregadosList` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transferencias` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionNumber` TEXT NOT NULL,
                        `jornadaId` INTEGER NOT NULL,
                        `comandaId` INTEGER,
                        `comandaNumber` INTEGER,
                        `amount` REAL NOT NULL,
                        `currency` TEXT NOT NULL DEFAULT 'CUP',
                        `phoneNumber` TEXT NOT NULL DEFAULT '',
                        `titularName` TEXT NOT NULL DEFAULT '',
                        `titularCi` TEXT NOT NULL DEFAULT '',
                        `recipientAccount` TEXT NOT NULL DEFAULT '',
                        `smsDate` TEXT NOT NULL DEFAULT '',
                        `receivedAt` INTEGER NOT NULL,
                        `cajeroUsername` TEXT NOT NULL DEFAULT '',
                        `status` TEXT NOT NULL DEFAULT 'NO ASOCIADA',
                        `rawSmsBody` TEXT NOT NULL DEFAULT '',
                        `isManual` INTEGER NOT NULL DEFAULT 0,
                        `source` TEXT NOT NULL DEFAULT 'SMS_AUTOMATICA'
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transferencias_transactionNumber` ON `transferencias` (`transactionNumber`)")
            }
        }

        val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transferencias` ADD COLUMN `isManual` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transferencias` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'SMS_AUTOMATICA'")
            }
        }

        val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `telefonoDueno` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `telefonoCajero` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `telefonoAdmin` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `telefono` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `order_items` ADD COLUMN `productCode` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_32_33 = object : androidx.room.migration.Migration(32, 33) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `inversiones` ADD COLUMN `currency` TEXT NOT NULL DEFAULT 'CUP'")
                db.execSQL("ALTER TABLE `inversiones` ADD COLUMN `originalAmount` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `inversiones` ADD COLUMN `exchangeRate` REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE `inversiones` ADD COLUMN `convertedAmount` REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payment_proposals` (
                        `username` TEXT NOT NULL,
                        `fullName` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `paymentAmount` REAL NOT NULL,
                        `paymentType` TEXT NOT NULL,
                        `isActiveProposal` INTEGER NOT NULL DEFAULT 1,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`username`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_34_35 = object : androidx.room.migration.Migration(34, 35) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sms_comandas_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `senderPhone` TEXT NOT NULL,
                        `smsText` TEXT NOT NULL,
                        `receivedAt` INTEGER NOT NULL,
                        `estado` TEXT NOT NULL DEFAULT 'PENDIENTE',
                        `jornadaId` INTEGER NOT NULL DEFAULT 0,
                        `comandaNumber` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_35_36 = object : androidx.room.migration.Migration(35, 36) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mercaderias` ADD COLUMN `directExpenses` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `mercaderias` ADD COLUMN `directExpensesDetails` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `purchaseQuantity` REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_36_37 = object : androidx.room.migration.Migration(36, 37) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `permisoProduccion` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `permisoMercancias` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `permisoPersonal` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `permisoControlNegocio` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_37_38 = object : androidx.room.migration.Migration(37, 38) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `urlQDuenoJson` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `lastQDuenoUpdateDate` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `lastQDuenoUpdateStatus` TEXT NOT NULL DEFAULT 'PENDIENTE'")
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `lastQDuenoUpdateVersion` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `lastQDuenoUpdateFechaPublicacion` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_38_39 = object : androidx.room.migration.Migration(38, 39) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tandas` ADD COLUMN `quantitySold` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `tandas` ADD COLUMN `salePrice` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `tandas` ADD COLUMN `realRevenue` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `tandas` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT 'DISPOSITIVO-LOCAL'")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `quantitySold` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `salePrice` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `acquisitionCost` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `realRevenue` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `jornadaId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `movimientos_mercaderia` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT 'DISPOSITIVO-LOCAL'")
            }
        }

        val MIGRATION_39_40 = object : androidx.room.migration.Migration(39, 40) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `deviceId` TEXT NOT NULL DEFAULT 'DISPOSITIVO-LOCAL'")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `realSalesProduccion` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `realCostProduccion` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `gastosProduccion` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `inversionesProduccion` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `resultadoProduccion` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `realSalesMercaderias` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `realCostMercaderias` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `gastosMercaderias` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `inversionesMercaderias` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `resultadoMercaderias` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `totalIngresos` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `totalCostos` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `totalGastos` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `totalInversiones` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `utilidadDelDia` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `modulosUtilizados` TEXT NOT NULL DEFAULT 'PRODUCCION,MERCADERIAS'")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `snapshotJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_40_41 = object : androidx.room.migration.Migration(40, 41) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mercaderias` ADD COLUMN `purchaseMode` TEXT NOT NULL DEFAULT 'POR UNIDAD'")
                db.execSQL("ALTER TABLE `mercaderias` ADD COLUMN `purchasePrice` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `mercaderias` ADD COLUMN `unitsPerLot` REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_41_42 = object : androidx.room.migration.Migration(41, 42) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `personal_contratado` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `nombreCompleto` TEXT NOT NULL,
                        `carnetIdentidad` TEXT NOT NULL,
                        `movil` TEXT NOT NULL,
                        `formaPago` TEXT NOT NULL,
                        `fechaRegistro` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `extracciones` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `jornadas` ADD COLUMN `liquidezFinal` REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_42_43 = object : androidx.room.migration.Migration(42, 43) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `config_general` ADD COLUMN `urlVersionJson` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_43_44 = object : androidx.room.migration.Migration(43, 44) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `isConvertedToInsumo` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Column might already exist
                }
                try {
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `productId` INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    // Column might already exist
                }
            }
        }

        val MIGRATION_44_45 = object : androidx.room.migration.Migration(44, 45) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `tieneAccesoApp` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `username` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `passwordHash` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `passwordPlain` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `role` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `dependienteTipo` TEXT NOT NULL DEFAULT 'SALON'")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `montoPorProducto` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `permisoProduccion` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `permisoMercancias` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `permisoPersonal` INTEGER NOT NULL DEFAULT 1")
                    db.execSQL("ALTER TABLE `personal_contratado` ADD COLUMN `permisoControlNegocio` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {
                    // Ignore if columns already added
                }
            }
        }

        val MIGRATION_45_46 = object : androidx.room.migration.Migration(45, 46) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `products` ADD COLUMN `presentacionesEspeciales` TEXT NOT NULL DEFAULT '[]'")
                } catch (e: Exception) {
                    // Ignore if column already added
                }
            }
        }

        val MIGRATION_46_47 = object : androidx.room.migration.Migration(46, 47) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `isAgregado` INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `rationQuantity` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `rationUnit` TEXT NOT NULL DEFAULT ''")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `suggestedPrice` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `salePrice` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `stockEnVenta` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("ALTER TABLE `materias_primas` ADD COLUMN `racionesEnVenta` REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    // Ignore if columns already exist
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "elqadre_local.db"
                )
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                    MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28,
                    MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33,
                    MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37, MIGRATION_37_38,
                    MIGRATION_38_39, MIGRATION_39_40, MIGRATION_40_41, MIGRATION_41_42, MIGRATION_42_43,
                    MIGRATION_43_44, MIGRATION_44_45, MIGRATION_45_46, MIGRATION_46_47
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed only essential configurations and users on first database creation
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getDatabase(context)
                            seedDefaultData(database)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultData(db: AppDatabase) {
            // Seed configs safely
            val currentNegocio = db.configuracionNegocioDao().getConfigSync()
            if (currentNegocio == null) {
                val defaultNegocio = ConfiguracionNegocio(
                    id = 1,
                    nombreNegocio = "Pizzas Factory",
                    direccion = "Bayamo, Cuba",
                    telefono = "+53 51234567",
                    codigoNegocio = "001",
                    logoPath = null
                )
                db.configuracionNegocioDao().insertConfig(defaultNegocio)
            } else if (currentNegocio.nombreNegocio.isBlank() || 
                       currentNegocio.nombreNegocio == "Cafetería La Plaza" || 
                       currentNegocio.nombreNegocio == "Restaurante El Buen Sabor" ||
                       currentNegocio.nombreNegocio == "El Qadre POS" ||
                       currentNegocio.nombreNegocio == "ElQadre") {
                db.configuracionNegocioDao().updateConfig(
                    currentNegocio.copy(
                        nombreNegocio = "Pizzas Factory",
                        direccion = if (currentNegocio.direccion.contains("La Habana") || currentNegocio.direccion.isBlank()) "Bayamo, Cuba" else currentNegocio.direccion,
                        codigoNegocio = if (currentNegocio.codigoNegocio.isBlank() || currentNegocio.codigoNegocio == "NEG-000001") "001" else currentNegocio.codigoNegocio
                    )
                )
            }

            if (db.configuracionGeneralDao().getConfigSync() == null) {
                val defaultGeneral = ConfiguracionGeneral(
                    id = 1,
                    moneda = "CUP",
                    metodosPago = "Efectivo",
                    parametrosJornada = "Apertura automatica:falso,Obligatorio notas de cierre:verdadero",
                    denominacionesCaja = "1000,500,200,100,50,20,10"
                )
                db.configuracionGeneralDao().insertConfig(defaultGeneral)
            }

            // Ensure default users exist (Administrador, Dueño, Cajero, Salón, Barra)
            val existingUsers = db.userDao().getAllUsersSync()
            if (existingUsers.isEmpty()) {
                val defaultUsers = listOf(
                    User("admin", "Administrador Principal", "admin26".toSha256(), UserRole.ADMIN, isActive = true, montoPorProducto = 0.0),
                    User("dueno", "Don Roberto (Dueño)", "1234".toSha256(), UserRole.DUENO, isActive = true, montoPorProducto = 0.0),
                    User("cajero1", "Carlos Mendoza (Caja)", "1234".toSha256(), UserRole.CAJERO, isActive = true, montoPorProducto = 0.0),
                    User("salon1", "Sofía Valdés (Salón)", "1234".toSha256(), UserRole.SALON, isActive = true, montoPorProducto = 50.0),
                    User("barra1", "Mateo Gómez (Barra)", "1234".toSha256(), UserRole.BARRA, isActive = true, montoPorProducto = 30.0)
                )
                defaultUsers.forEach { db.userDao().insertUser(it) }
            } else {
                ensureAdminUserExists(db)
            }

            // Seed only essential configurations and users on first creation.
            // Fresh installs of the app start with an empty catalog, empty materials, empty expenses, empty tables, and empty jornadas.
        }

        /**
         * Comprueba que existe localmente la cuenta ADMINISTRADOR.
         * Si NO existe: la crea automáticamente con username = "admin", password inicial = "admin26", rol = ADMINISTRADOR, activo.
         * Si YA existe:
         *   - Si tiene la contraseña antigua heredada de "1234", se migra automáticamente a "admin26".
         *   - Si tiene cualquier otra contraseña personalizada, se conserva intacta.
         */
        suspend fun ensureAdminUserExists(db: AppDatabase): User {
            val existingAdmin = db.userDao().getUserByUsername("admin")
            if (existingAdmin == null) {
                val newAdmin = User(
                    username = "admin",
                    fullName = "Administrador Principal",
                    passwordHash = "admin26".toSha256(),
                    role = UserRole.ADMIN,
                    isActive = true,
                    montoPorProducto = 0.0
                )
                db.userDao().insertUser(newAdmin)
                return newAdmin
            } else {
                val legacyHash = "1234".toSha256()
                if (existingAdmin.passwordHash == legacyHash) {
                    val migratedAdmin = existingAdmin.copy(passwordHash = "admin26".toSha256())
                    db.userDao().updateUser(migratedAdmin)
                    return migratedAdmin
                }
            }
            return existingAdmin
        }
    }
}
