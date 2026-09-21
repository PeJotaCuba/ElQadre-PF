package com.example.data.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class UserRole(val displayName: String) {
    ADMIN("Administrador"),
    DUENO("Dueño"),
    DEPENDIENTE("Dependiente"),
    CAJERO("Cajero"),
    COCINA("Cocina"),
    SALON("Dependiente de Salón"),
    BARRA("Dependiente de Barra");

    companion object {
        val CREATABLE_BY_ADMIN = listOf(DUENO, DEPENDIENTE, CAJERO, COCINA)
        val OPERATIONAL_ROLES = listOf(ADMIN, DUENO, DEPENDIENTE, CAJERO, COCINA)

        fun fromString(value: String): UserRole {
            val normalized = value.trim().uppercase()
            return when {
                normalized == "ADMIN" || normalized == "ADMINISTRADOR" -> ADMIN
                normalized == "DUENO" || normalized == "DUEÑO" -> DUENO
                normalized == "DEPENDIENTE" -> DEPENDIENTE
                normalized == "CAJERO" -> CAJERO
                normalized == "COCINA" -> COCINA
                normalized.contains("SALON") || normalized.contains("SALÓN") -> DEPENDIENTE
                normalized.contains("BARRA") -> BARRA
                else -> DEPENDIENTE
            }
        }
    }
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey val username: String,
    val fullName: String,
    val passwordHash: String,
    val role: UserRole,
    val montoPorProducto: Double = 0.0,
    val isActive: Boolean = true,
    val authorizedDeviceId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val telefono: String = "",
    val permisoProduccion: Boolean = true,
    val permisoMercancias: Boolean = true,
    val permisoPersonal: Boolean = true,
    val permisoControlNegocio: Boolean = true
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isActive: Boolean = true
)

@Entity(tableName = "jornadas")
data class Jornada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openedAt: Long,
    val closedAt: Long? = null,
    val initialCash: Double = 0.0,
    val finalCash: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val expectedCash: Double = 0.0,
    val cashDifference: Double = 0.0,
    val notes: String = "",
    val isOpen: Boolean = true,
    val openedBy: String = "admin",
    val closedBy: String? = null,
    val utilidadSalonMontoUnitario: Double = 0.0,
    val deviceId: String = "DISPOSITIVO-LOCAL",
    val realSalesProduccion: Double = 0.0,
    val realCostProduccion: Double = 0.0,
    val gastosProduccion: Double = 0.0,
    val inversionesProduccion: Double = 0.0,
    val resultadoProduccion: Double = 0.0,
    val realSalesMercaderias: Double = 0.0,
    val realCostMercaderias: Double = 0.0,
    val gastosMercaderias: Double = 0.0,
    val inversionesMercaderias: Double = 0.0,
    val resultadoMercaderias: Double = 0.0,
    val totalIngresos: Double = 0.0,
    val totalCostos: Double = 0.0,
    val totalGastos: Double = 0.0,
    val totalInversiones: Double = 0.0,
    val utilidadDelDia: Double = 0.0,
    val modulosUtilizados: String = "PRODUCCION,MERCADERIAS",
    val snapshotJson: String = "",
    val extracciones: Double = 0.0,
    val liquidezFinal: Double = 0.0
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val category: String, // "Bebidas", "Cocina", "Postres", "Insumos"
    val price: Double,
    val cost: Double = 0.0,
    val stock: Int = 10,
    val minStock: Int = 3,
    val destination: String = "COCINA", // "COCINA" or "BARRA"
    val isAvailable: Boolean = true,
    val description: String = "",
    val unitOfMeasure: String = "Unidad",
    val imagePath: String? = null,
    val admitsAgregados: Boolean = false,
    val agregadosList: String = "[]",
    val isConvertedToInsumo: Boolean = false,
    val presentacionesEspeciales: String = "[]"
)

data class PresentacionEspecial(
    val id: String = "",
    val name: String,
    val baseEquivalence: Double = 1.0 // Equivalencia en unidades base (ej. Familiar = 1.0 unidad base)
)

fun parsePresentacionesEspeciales(jsonStr: String?): List<PresentacionEspecial> {
    if (jsonStr.isNullOrBlank() || jsonStr == "[]") return emptyList()
    return try {
        val arr = org.json.JSONArray(jsonStr)
        val list = mutableListOf<PresentacionEspecial>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val id = if (obj.has("id") && obj.optString("id").isNotBlank()) {
                obj.optString("id")
            } else {
                (i + 1).toString()
            }
            val name = obj.optString("name", "")
            val baseEquivalence = obj.optDouble("baseEquivalence", 1.0)
            if (name.isNotBlank()) {
                list.add(
                    PresentacionEspecial(
                        id = id,
                        name = name,
                        baseEquivalence = if (baseEquivalence > 0.0) baseEquivalence else 1.0
                    )
                )
            }
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

fun serializePresentacionesEspeciales(list: List<PresentacionEspecial>): String {
    if (list.isEmpty()) return "[]"
    val arr = org.json.JSONArray()
    list.forEachIndexed { index, item ->
        val obj = org.json.JSONObject()
        obj.put("id", if (item.id.isNotBlank()) item.id else (index + 1).toString())
        obj.put("name", item.name)
        obj.put("baseEquivalence", item.baseEquivalence)
        arr.put(obj)
    }
    return arr.toString()
}

@Entity(tableName = "table_orders")
data class TableOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableNumber: Int? = null,
    val customerName: String = "",
    val waiterUsername: String,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "ABIERTA", // "ABIERTA", "COBRADA", "CANCELADA"
    val totalAmount: Double = 0.0,
    val paymentMethod: String = "EFECTIVO", // "EFECTIVO", "TRANSFERENCIA", "MIXTO"
    val tip: Double = 0.0,
    val jornadaId: Long = 0,
    val comandaNumber: Int = 0,
    val cocinaNumber: Int = 0,
    val barraNumber: Int = 0,
    val totalCocina: Double = 0.0,
    val totalBarra: Double = 0.0,
    val cashReceived: Double = 0.0,
    val changeGiven: Double = 0.0,
    val confirmedAt: Long? = null,
    val servedAt: Long? = null,
    val serviceDurationSeconds: Long = 0L,
    val currency: String = "CUP",
    val exchangeRate: Double = 1.0,
    val originalAmount: Double = 0.0,
    val amountInCurrency: Double = 0.0
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val productId: Long,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val destination: String = "COCINA",
    val notes: String = "",
    val status: String = "PENDIENTE", // "PENDIENTE", "PREPARADO", "ENTREGADO"
    val productCode: String = ""
)

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val productName: String,
    val type: String, // "ENTRADA", "SALIDA", "MERMA", "AJUSTE"
    val quantity: Int,
    val reason: String,
    val recordedBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val jornadaId: Long = 0
)

@Entity(tableName = "production_batches")
data class ProductionBatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val quantity: Int,
    val destination: String, // "BARRA", "COCINA", "SALÓN"
    val notes: String,
    val createdBy: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETADO"
)

@Entity(tableName = "bitacora_entries")
data class BitacoraEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String, // "INCIDENCIA", "CUADRE", "GENERAL", "MANTENIMIENTO"
    val authorUsername: String,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: String = "NORMAL" // "BAJA", "NORMAL", "ALTA"
)

@Entity(tableName = "config_negocio")
data class ConfiguracionNegocio(
    @PrimaryKey val id: Long = 1,
    val nombreNegocio: String,
    val logoPath: String? = null,
    val direccion: String = "",
    val telefono: String = "",
    val codigoNegocio: String = "NEG-000001",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val fechaActualizacion: Long = System.currentTimeMillis()
)

@Entity(tableName = "config_general")
data class ConfiguracionGeneral(
    @PrimaryKey val id: Long = 1,
    val moneda: String = "CUP",
    val metodosPago: String = "Efectivo",
    val parametrosJornada: String = "",
    val denominacionesCaja: String = "20000,10000,5000,2000,1000,500,200,100,50,20,10,5",
    val tasaUsd: Double = 0.0,
    val tasaEur: Double = 0.0,
    val urlUsuariosJson: String = "",
    val lastUserUpdateDate: Long = 0L,
    val lastUserUpdateStatus: String = "PENDIENTE",
    val lastUserUpdateVersion: String = "",
    val urlCatalogoJson: String = "",
    val lastCatalogoUpdateDate: Long = 0L,
    val lastCatalogoUpdateStatus: String = "PENDIENTE",
    val lastCatalogoUpdateVersion: String = "",
    val urlMercainvJson: String = "",
    val lastMercainvUpdateDate: Long = 0L,
    val lastMercainvUpdateStatus: String = "PENDIENTE",
    val lastMercainvUpdateVersion: String = "",
    val telefonoDueno: String = "",
    val telefonoCajero: String = "",
    val telefonoAdmin: String = "",
    val urlQDuenoJson: String = "",
    val urlVersionJson: String = "",
    val lastQDuenoUpdateDate: Long = 0L,
    val lastQDuenoUpdateStatus: String = "PENDIENTE",
    val lastQDuenoUpdateVersion: String = "",
    val lastQDuenoUpdateFechaPublicacion: String = ""
)

@Entity(tableName = "materias_primas")
data class MateriaPrima(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String, // e.g. "g", "ml", "u"
    val unitCost: Double,
    val isActive: Boolean = true,
    val stock: Double = 0.0,
    val initialStock: Double = 0.0,
    val purchasePrice: Double = 0.0,
    val purchaseUnit: String = "g",
    val purchaseQuantity: Double = 1.0,
    val productId: Long? = null,
    val isAgregado: Boolean = false,
    val rationQuantity: Double = 0.0, // Cantidad física por ración en la unidad base (ej. 30g)
    val rationUnit: String = "", // Unidad de la ración (ej. "g", "ml", "u")
    val suggestedPrice: Double = 0.0, // Precio sugerido = costo por ración / 0.70
    val salePrice: Double = 0.0, // Precio de venta establecido por el Dueño
    val stockEnVenta: Double = 0.0, // Cantidad física enviada para venta (Salida para venta)
    val racionesEnVenta: Double = 0.0 // Raciones correspondientes enviadas para venta
) {
    val racionesDisponibles: Double get() = if (rationQuantity > 0.0) (stock / rationQuantity).coerceAtLeast(0.0) else 0.0
    val costoPorRacion: Double get() = if (rationQuantity > 0.0) unitCost * rationQuantity else 0.0
    val precioSugeridoCalculado: Double get() = if (costoPorRacion > 0.0) costoPorRacion / 0.70 else 0.0
    val precioEfectivoVenta: Double get() = if (salePrice > 0.0) salePrice else precioSugeridoCalculado
}

@Entity(tableName = "productos_elaborados")
data class ProductoElaborado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long, // References Product.id
    val isActive: Boolean = true,
    val recipeName: String = "",
    val productionUnit: String = "unidades",
    val baseYield: Double = 1.0,
    val baseMateriaPrimaId: Long = 0L,
    val baseQuantity: Double = 0.0,
    val estimatedDailyQuantity: Double = 10.0, // Cantidad estimada producida/vendida por día
    val ppd: Double = 10.0, // Producción Promedio Diaria (PPD)
    val precioDefinitivo: Double = 0.0, // Precio definitivo confirmado por el Administrador
    val hasPrecioDefinitivo: Boolean = false, // false = SIN PRECIO DEFINITIVO, true = PRECIO DEFINITIVO CONFIGURADO
    val targetMarginPct: Double = 30.0, // Margen de referencia para calcular precio de referencia (ej. 30%)
    val pagoCocinaUnitario: Double = 0.0, // Pago por unidad producida/vendida para cocina
    val cantidadCocineros: Int = 1, // Cantidad de cocineros asociados al producto
    val pagoDependienteUnitario: Double = 0.0, // Pago por unidad producida/vendida para dependiente (1 dependiente asociado)
    val pagoCajeroUnitario: Double = 0.0 // Pago por unidad producida/vendida para cajero
) {
    val effectivePpd: Double get() = if (ppd > 0.0) ppd else (if (estimatedDailyQuantity > 0.0) estimatedDailyQuantity else 10.0)
    val totalPagoCocinaUnitario: Double get() = if (pagoCocinaUnitario > 0.0) pagoCocinaUnitario * (if (cantidadCocineros > 0) cantidadCocineros else 1) else 0.0
    val totalPagoDependienteUnitario: Double get() = pagoDependienteUnitario
    val totalPagoCajeroUnitario: Double get() = pagoCajeroUnitario
    val totalPagoPersonalUnitario: Double get() = totalPagoCocinaUnitario + totalPagoDependienteUnitario + totalPagoCajeroUnitario
}

@Entity(tableName = "receta_ingredientes")
data class RecetaIngrediente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoElaboradoId: Long, // References ProductoElaborado.id
    val materiaPrimaId: Long, // References MateriaPrima.id
    val quantity: Double,
    val unit: String
)

@Entity(tableName = "mercaderias")
data class Mercaderia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long, // References Product.id
    val acquisitionCost: Double,
    val unitOfMeasure: String,
    val initialStock: Double,
    val isActive: Boolean = true,
    val directExpenses: Double = 0.0,
    val directExpensesDetails: String = "[]",
    val purchaseMode: String = "POR UNIDAD", // "POR UNIDAD" o "POR LOTE"
    val purchasePrice: Double = 0.0,
    val unitsPerLot: Double = 1.0
)

@Entity(tableName = "movimientos_mercaderia")
data class MovimientoMercaderia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mercaderiaId: Long, // References Mercaderia.id
    val type: String, // "INVENTARIO_INICIAL", "ENTRADA", "SALIDA", "AJUSTE"
    val quantity: Double,
    val date: Long = System.currentTimeMillis(),
    val responsibleAdmin: String,
    val notes: String = "",
    val quantitySold: Double = 0.0,
    val salePrice: Double = 0.0,
    val acquisitionCost: Double = 0.0,
    val realRevenue: Double = 0.0,
    val jornadaId: Long = 0L,
    val deviceId: String = "DISPOSITIVO-LOCAL"
)

@Entity(tableName = "gastos_generales")
data class GastoGeneral(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val amount: Double,
    val period: String = "MES LABORABLE", // "DÍA", "SEMANA", "SEMANA LABORABLE", "MES", "MES LABORABLE", "AÑO", "AÑO LABORABLE"
    val periodDays: Int = 26, // Días base del período (e.g. 1, 7, 6, 30, 26, 360, 312)
    val category: String = "Otros", // "Personal", "Electricidad", "Transporte", "Limpieza", "Insumos indirectos", "Agua", "Seguridad", "Otros"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val inversionId: Long? = null,
    val targetProductId: Long? = null, // Deprecated / Gastos generales del negocio
    val targetProductIds: String? = null, // Deprecated / Gastos generales del negocio
    val startDate: Long? = null, // For Period-based expenses
    val endDate: Long? = null,    // For Period-based expenses
    val scope: String = "PRODUCCION" // "PRODUCCION" or "MERCADERIAS"
) {
    fun dailyCost(): Double {
        if (!isActive || amount <= 0.0) return 0.0
        val p = period.uppercase().trim()
        val days = when (p) {
            "DÍA", "DIA", "DÍAS", "DIAS", "DIARIO", "ÚNICO", "UNICO" -> 1.0
            "SEMANA", "SEMANAL" -> 7.0
            "SEMANA LABORABLE", "SEMANAL LABORABLE" -> 6.0
            "MES", "MENSUAL" -> 30.0
            "MES LABORABLE", "MENSUAL LABORABLE" -> 26.0
            "AÑO", "ANO", "ANUAL" -> 360.0
            "AÑO LABORABLE", "ANO LABORABLE", "ANUAL LABORABLE" -> 312.0
            else -> if (periodDays > 0) periodDays.toDouble() else 26.0
        }
        return amount / days
    }
}

@Entity(tableName = "tandas")
data class Tanda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String, // format like TANDA-20260825-001
    val productId: Long, // references Product.id
    val productName: String,
    val date: Long = System.currentTimeMillis(),
    val responsibleUser: String,
    val baseMateriaPrimaId: Long,
    val baseMateriaPrimaName: String,
    val baseQuantityUsed: Double,
    val baseQuantityUnit: String,
    val productionFactor: Double,
    val estimatedYield: Double,
    val productionUnit: String,
    val ingredientsConsumedText: String,
    val status: String = "ACTIVADA", // "PENDIENTE", "ACTIVADA", "PROCESADA"
    val jornada: String = "Jornada Unica",
    val jornadaId: Long = 0L,
    val observation: String = "",
    val laborCostType: String = "NINGUNO", // "NINGUNO", "PORCENTAJE", "FIJO_UNITARIO"
    val laborCostValue: Double = 0.0,
    val totalLaborCost: Double = 0.0,
    val totalDirectIngredientsCost: Double = 0.0,
    val totalIndirectCostAllocated: Double = 0.0,
    val totalBatchCost: Double = 0.0,
    val realUnitCost: Double = 0.0,
    val tandaNumber: String = "01",
    val expectedYield: Double = 0.0,
    val actualYield: Double = 0.0,
    val yieldPercentage: Double = 100.0,
    val expectedRevenue: Double = 0.0,
    val estimatedProfit: Double = 0.0,
    val profitMargin: Double = 0.0,
    val inventoryDeducted: Boolean = true,
    val ownerPayType: String = "NINGUNO", // "NINGUNO", "PORCENTAJE", "FIJO_UNITARIO"
    val ownerPayValue: Double = 0.0,
    val totalOwnerPay: Double = 0.0,
    val quantitySold: Double = 0.0,
    val salePrice: Double = 0.0,
    val realRevenue: Double = 0.0,
    val deviceId: String = "DISPOSITIVO-LOCAL"
) {
    val cantidadRestante: Double get() = (actualYield - quantitySold).coerceAtLeast(0.0)
}

@Entity(tableName = "movimientos_materia_prima")
data class MovimientoMateriaPrima(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materiaPrimaId: Long,
    val materiaPrimaName: String,
    val type: String, // "ENTRADA", "SALIDA", "TANDA_CONSUMO"
    val quantity: Double,
    val unit: String,
    val date: Long = System.currentTimeMillis(),
    val responsibleUser: String,
    val notes: String = "",
    val resultingStock: Double = 0.0
)

@Entity(tableName = "inversiones")
data class Inversion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val usefulLife: Double,
    val usefulLifeUnit: String, // "MESES", "AÑOS"
    val observation: String = "",
    val targetProductId: Long? = null, // null or 0L = Todos los productos (Deprecated in favor of targetProductIds)
    val targetProductIds: String? = null, // Comma-separated list of product IDs for Puntual expenses
    val startDate: Long? = null, // For Period-based expenses
    val endDate: Long? = null,    // For Period-based expenses
    val scope: String = "PRODUCCION", // "PRODUCCION" or "MERCADERIAS"
    val currency: String = "CUP",
    val originalAmount: Double = 0.0,
    val exchangeRate: Double = 1.0,
    val convertedAmount: Double = 0.0
) {
    fun monthlyDepreciation(): Double {
        if (usefulLife <= 0.0) return 0.0
        return when (usefulLifeUnit.uppercase()) {
            "DIAS", "DÍAS", "DÍA", "DIA" -> (amount / usefulLife) * 30.0
            "SEMANAS", "SEMANA" -> (amount / usefulLife) * 4.2857
            "MESES", "MES" -> amount / usefulLife
            "AÑOS", "AÑO", "ANUAL" -> amount / (usefulLife * 12.0)
            else -> amount / usefulLife
        }
    }

    fun dailyDepreciation(): Double {
        if (usefulLife <= 0.0) return 0.0
        return when (usefulLifeUnit.uppercase()) {
            "DIAS", "DÍAS", "DÍA", "DIA" -> amount / usefulLife
            "SEMANAS", "SEMANA" -> amount / (usefulLife * 7.0)
            "MESES", "MES" -> amount / (usefulLife * 30.0)
            "AÑOS", "AÑO", "ANUAL" -> amount / (usefulLife * 365.0)
            else -> monthlyDepreciation() / 30.0
        }
    }
}

@Entity(tableName = "consumo_personal_items")
data class ConsumoPersonalItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jornadaId: Long,
    val productId: Long,
    val productName: String,
    val unitPrice: Double,
    val quantity: Int,
    val totalAmount: Double = unitPrice * quantity,
    val recordedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transferencias",
    indices = [Index(value = ["transactionNumber"], unique = true)]
)
data class Transferencia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionNumber: String, // Identificador único ej. KW601MRAWO999
    val jornadaId: Long = 0,
    val comandaId: Long? = null, // ID de la comanda asociada o null
    val comandaNumber: Int? = null, // Número de comanda asociada o null
    val amount: Double = 0.0,
    val currency: String = "CUP",
    val phoneNumber: String = "", // 10 dígitos o vacío si no viene en SMS
    val titularName: String = "", // Completado manualmente por el Cajero
    val titularCi: String = "", // Carnet de identidad completado manualmente
    val recipientAccount: String = "", // Cuenta receptora
    val smsDate: String = "", // Fecha informada en el SMS ej. "27/8/2026"
    val receivedAt: Long = System.currentTimeMillis(), // Timestamp de recepción
    val cajeroUsername: String = "",
    val status: String = "NO ASOCIADA", // "NO ASOCIADA", "ASOCIADA", "PARCIAL"
    val rawSmsBody: String = "",
    val isManual: Boolean = false, // true = Manual/Externa, false = Detección automática por SMS
    val source: String = "SMS_AUTOMATICA" // "SMS_AUTOMATICA" o "MANUAL_EXTERNA"
)

@Entity(tableName = "payment_proposals")
data class PaymentProposal(
    @PrimaryKey val username: String,
    val fullName: String,
    val role: UserRole,
    val paymentAmount: Double,
    val paymentType: String,
    val isActiveProposal: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sms_comandas_queue"
)
data class SmsComandaQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderPhone: String,
    val smsText: String,
    val receivedAt: Long = System.currentTimeMillis(),
    val estado: String = "PENDIENTE", // "PENDIENTE", "PROCESADA", "IGNORADA"
    val jornadaId: Long = 0L,
    val comandaNumber: Int = 0
)

@Entity(tableName = "personal_contratado")
data class PersonalContratado(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombreCompleto: String,
    val carnetIdentidad: String,
    val movil: String,
    val formaPago: String,
    val fechaRegistro: Long = System.currentTimeMillis(),

    // Credenciales y permisos de acceso a ElQadre (cuando aplique)
    val tieneAccesoApp: Boolean = false,
    val username: String = "",
    val passwordHash: String = "",
    val passwordPlain: String = "",
    val role: String = "", // "CAJERO" o "DEPENDIENTE"
    val dependienteTipo: String = "SALON", // "SALON" o "BARRA"
    val montoPorProducto: Double = 0.0,
    val isActive: Boolean = true,
    val permisoProduccion: Boolean = true,
    val permisoMercancias: Boolean = true,
    val permisoPersonal: Boolean = true,
    val permisoControlNegocio: Boolean = false
)




