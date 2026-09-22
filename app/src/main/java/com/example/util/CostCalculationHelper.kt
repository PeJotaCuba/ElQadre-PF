package com.example.util

import com.example.data.local.model.*
import com.example.ui.viewmodel.UnitConverter

data class IngredientCostDetail(
    val materiaPrima: MateriaPrima?,
    val quantity: Double,
    val unit: String,
    val unitCost: Double,
    val totalCost: Double
)

data class ProductCostSheet(
    val product: Product,
    val productoElaborado: ProductoElaborado?,
    val ingredientDetails: List<IngredientCostDetail>,
    val totalDirectRecipeCost: Double,
    val baseYield: Double,
    val productionUnit: String,
    val costoDirectoUnitario: Double,
    val ppd: Double,
    val totalKitchenPpd: Double,
    val porcentajeParticipacionPpd: Double,
    val gastosGeneralesDiariosTotales: Double,
    val depreciacionInversionesDiariaTotales: Double,
    val costosIndirectosDiariosTotales: Double,
    val gastoGeneralAsignado: Double,
    val depreciacionAsignada: Double,
    val gastoIndirectoAsignado: Double,
    val gastoIndirectoUnitario: Double,
    val gastoGeneralUnitarioProrrateo: Double = 0.0, // Gasto general prorrateado por unidad (Prompt 2)
    val pagoCocinaUnitario: Double = 0.0,
    val cantidadCocineros: Int = 1,
    val totalPagoCocinaUnitario: Double = 0.0,
    val pagoDependienteUnitario: Double = 0.0,
    val totalPagoDependienteUnitario: Double = 0.0,
    val pagoCajeroUnitario: Double = 0.0,
    val totalPagoCajeroUnitario: Double = 0.0,
    val totalPagoPersonalUnitario: Double = 0.0,
    val costoTotalUnitario: Double, // costoDirectoUnitario + gastoGeneralUnitarioProrrateo
    val costoRealUnitario: Double,
    val precioReferencia: Double,
    val precioDefinitivo: Double,
    val hasPrecioDefinitivo: Boolean,
    val isComplete: Boolean = true,
    val missingDataReason: String? = null,
    val estimatedDailyQuantity: Double = ppd,
    val movimientoDiarioProducto: Double = costoDirectoUnitario * ppd,
    val movimientoDiarioTotal: Double = totalKitchenPpd,
    val porcentajeProrrateo: Double = porcentajeParticipacionPpd
)

data class AllocatedCostItem(
    val id: Long,
    val name: String,
    val category: String,
    val originalAmount: Double,
    val period: String,
    val dailyEquivalent: Double,
    val isSpecific: Boolean,
    val allocatedDailyAmount: Double,
    val allocatedUnitAmount: Double
)

data class AllocatedInversionItem(
    val id: Long,
    val name: String,
    val category: String,
    val originalAmount: Double,
    val usefulLifeText: String,
    val monthlyDepreciation: Double,
    val dailyDepreciation: Double,
    val isSpecific: Boolean,
    val allocatedDailyAmount: Double,
    val allocatedUnitAmount: Double
)

data class ProrrateoBaseItem(
    val id: Long,
    val name: String,
    val type: String, // "PRODUCCION" o "MERCADERIAS"
    val productId: Long,
    val quantity: Double, // Cantidad de base (producción diaria planificada o existencia actual)
    val costoDirectoUnitario: Double,
    val valorBase: Double, // quantity * costoDirectoUnitario
    val porcentajeParticipacion: Double, // % sobre la base total
    val gastoGeneralAsignado: Double, // Gasto general diario total * % participacion
    val gastoGeneralUnitario: Double // Gasto asignado / cantidad de base
)

data class ProrrateoGastosGeneralesResult(
    val gastoGeneralDiarioTotal: Double,
    val baseProduccion: List<ProrrateoBaseItem>,
    val baseMercaderias: List<ProrrateoBaseItem>,
    val baseTotal: Double,
    val itemsProrrateo: List<ProrrateoBaseItem>,
    val isValid: Boolean,
    val reason: String?,
    val sumaPorcentajes: Double = 0.0
) {
    val baseProduccionTotal: Double get() = baseProduccion.sumOf { it.valorBase }
    val baseMercaderiasTotal: Double get() = baseMercaderias.sumOf { it.valorBase }
    val gastoTotalAsignado: Double get() = itemsProrrateo.sumOf { it.gastoGeneralAsignado }
}

data class MercaderiaCostSheet(
    val mercaderia: Mercaderia,
    val product: Product,
    val acquisitionCost: Double,
    val directExpenses: Double = mercaderia.directExpenses,
    val costoDirectoUnitario: Double = acquisitionCost + directExpenses,
    val currentStock: Double,
    val totalAcquisitionValue: Double,
    val totalMercaderiasAcquisitionValue: Double,
    val porcentajeParticipacion: Double,
    val gastosComunesAsignados: Double,
    val gastosEspecificosAsignados: Double,
    val totalGastosAsignados: Double,
    val depreciacionComunAsignada: Double,
    val depreciacionEspecificaAsignada: Double,
    val totalDepreciacionAsignada: Double,
    val totalCostosIndirectosAsignados: Double,
    val gastoIndirectoUnitario: Double,
    val gastoGeneralUnitarioProrrateo: Double = 0.0,
    val costoTotalUnitario: Double = costoDirectoUnitario + gastoGeneralUnitarioProrrateo,
    val costoRealUnitario: Double,
    val targetMarginPct: Double,
    val precioReferencia: Double,
    val precioDefinitivo: Double,
    val hasPrecioDefinitivo: Boolean,
    val utilidadUnitaria: Double,
    val margenPorcentual: Double,
    val totalUtilidadProyectada: Double,
    val detailedExpenses: List<AllocatedCostItem>,
    val detailedInversions: List<AllocatedInversionItem>
) {
    val realCost: Double get() = costoRealUnitario
    val unitMargin: Double get() = utilidadUnitaria
    val unitMarginPercent: Double get() = margenPorcentual
    val proratedIndirectDaily: Double get() = gastosComunesAsignados
    val directIndirectDaily: Double get() = gastosEspecificosAsignados
    val proratedDepreciationDaily: Double get() = depreciacionComunAsignada
    val directDepreciationDaily: Double get() = depreciacionEspecificaAsignada
}

object CostCalculationHelper {

    /**
     * Calcula los gastos generales diarios totales activos convirtiendo cada período a base diaria.
     */
    fun calculateTotalDailyOverheads(gastosGenerales: List<GastoGeneral>): Double {
        return gastosGenerales.filter { it.isActive && it.inversionId == null }.sumOf { it.dailyCost() }
    }

    /**
     * Calcula la depreciación diaria total acumulada de inversiones activas.
     */
    fun calculateTotalDailyDepreciation(inversiones: List<Inversion>, gastosGenerales: List<GastoGeneral>): Double {
        if (inversiones.isNotEmpty()) {
            return inversiones.filter { it.scope == "PRODUCCION" }.sumOf { it.dailyDepreciation() }
        }
        return gastosGenerales.filter { it.isActive && it.inversionId != null && it.scope == "PRODUCCION" }.sumOf { it.dailyCost() }
    }

    /**
     * Calcula la Producción Promedio Diaria (PPD) acumulada de todos los productos de cocina activos.
     * Denominador principal para el prorrateo de costos indirectos.
     */
    fun calculateTotalKitchenPpd(
        products: List<Product>,
        productosElaborados: List<ProductoElaborado>
    ): Double {
        val prodElabMap = productosElaborados.associateBy { it.productId }
        var totalPpd = 0.0

        for (product in products) {
            if (product.destination == "COCINA" && product.isAvailable) {
                val prodElaborado = prodElabMap[product.id]
                val ppdVal = prodElaborado?.effectivePpd ?: 10.0
                if (ppdVal > 0.0) {
                    totalPpd += ppdVal
                }
            }
        }
        return if (totalPpd > 0.0) totalPpd else 1.0
    }

    /**
     * Calcula el costo directo unitario de un producto de producción basado en su receta.
     * Utiliza la estructura existente de cálculo de costos (calculateCostSheet).
     */
    private fun calcularCostoDirectoUnitarioProduccion(
        productId: Long,
        productosElaborados: List<ProductoElaborado>,
        prodElabMap: Map<Long, ProductoElaborado>,
        recetaIngredientes: List<RecetaIngrediente>,
        materiasPrimas: List<MateriaPrima>
    ): Double {
        val prodElaborado = prodElabMap[productId] ?: return 0.0
        
        // Calcular detalles de ingredientes para este producto
        val ingredientDetails = calculateIngredientDetails(productId, recetaIngredientes, materiasPrimas)
        
        if (ingredientDetails.isEmpty()) {
            return 0.0 // Sin receta definida
        }
        
        // Calcular costo directo total de la receta
        val totalDirectRecipeCost = ingredientDetails.sumOf { it.totalCost }
        
        // Obtener rendimiento base
        val baseYield = prodElaborado.baseYield.takeIf { it > 0.0 } ?: 1.0
        
        // Calcular costo directo unitario
        return totalDirectRecipeCost / baseYield
    }

    /**
     * Versión completa de calcularBaseProrrateoGastosGenerales que incluye el cálculo real del costo directo.
     */
    fun calcularBaseProrrateoGastosGenerales(
        products: List<Product>,
        productosElaborados: List<ProductoElaborado>,
        mercaderias: List<Mercaderia>,
        movimientosMercaderia: List<MovimientoMercaderia>,
        gastosGenerales: List<GastoGeneral>,
        recetaIngredientes: List<RecetaIngrediente>,
        materiasPrimas: List<MateriaPrima>
    ): ProrrateoGastosGeneralesResult {
        val prodElabMap = productosElaborados.associateBy { it.productId }
        
        // 1. GASTO GENERAL DIARIO TOTAL (solo scope PRODUCCION, activos, sin inversiones)
        val gastoGeneralDiarioTotal = calculateTotalDailyOverheads(gastosGenerales)
        
        // 2. BASE DE PRODUCCIÓN
        val baseProduccionItems = mutableListOf<ProrrateoBaseItem>()
        for (product in products) {
            if (product.destination == "COCINA" && product.isAvailable) {
                val prodElaborado = prodElabMap[product.id]
                val cantidadDiariaPlanificada = prodElaborado?.effectivePpd ?: 0.0
                
                if (cantidadDiariaPlanificada > 0.0) {
                    // Calcular costo directo unitario desde la ficha de costo existente
                    val costoDirectoUnitario = calcularCostoDirectoUnitarioProduccion(
                        product.id,
                        productosElaborados,
                        prodElabMap,
                        recetaIngredientes,
                        materiasPrimas
                    )
                    
                    if (costoDirectoUnitario > 0.0) {
                        val valorBase = cantidadDiariaPlanificada * costoDirectoUnitario
                        baseProduccionItems.add(
                            ProrrateoBaseItem(
                                id = product.id,
                                name = product.name,
                                type = "PRODUCCION",
                                productId = product.id,
                                quantity = cantidadDiariaPlanificada,
                                costoDirectoUnitario = costoDirectoUnitario,
                                valorBase = valorBase,
                                porcentajeParticipacion = 0.0, // Se calcula después
                                gastoGeneralAsignado = 0.0,    // Se calcula después
                                gastoGeneralUnitario = 0.0     // Se calcula después
                            )
                        )
                    }
                }
            }
        }
        
        // 3. BASE DE MERCADERÍAS
        val baseMercaderiasItems = mutableListOf<ProrrateoBaseItem>()
        for (mercaderia in mercaderias) {
            if (mercaderia.isActive) {
                val existenciaActual = getMercaderiaCurrentStock(
                    mercaderia.id,
                    mercaderia.initialStock,
                    movimientosMercaderia
                )
                
                if (existenciaActual > 0.0) {
                    val costoDirectoUnitario = mercaderia.acquisitionCost + mercaderia.directExpenses
                    val valorBase = existenciaActual * costoDirectoUnitario
                    
                    if (valorBase > 0.0) {
                        baseMercaderiasItems.add(
                            ProrrateoBaseItem(
                                id = mercaderia.id,
                                name = mercaderia.productId.toString(), // Se actualizará con el nombre del producto
                                type = "MERCADERIAS",
                                productId = mercaderia.productId,
                                quantity = existenciaActual,
                                costoDirectoUnitario = costoDirectoUnitario,
                                valorBase = valorBase,
                                porcentajeParticipacion = 0.0, // Se calcula después
                                gastoGeneralAsignado = 0.0,    // Se calcula después
                                gastoGeneralUnitario = 0.0     // Se calcula después
                            )
                        )
                    }
                }
            }
        }
        
        // Actualizar nombres de mercaderías con los nombres de productos
        val productMap = products.associateBy { it.id }
        val baseMercaderiasConNombres = baseMercaderiasItems.map { item ->
            val productName = productMap[item.productId]?.name ?: "Mercadería #${item.productId}"
            item.copy(name = productName)
        }.toMutableList()
        
        // 4. BASE TOTAL
        val baseTotal = baseProduccionItems.sumOf { it.valorBase } + baseMercaderiasConNombres.sumOf { it.valorBase }
        
        // Si la BASE TOTAL es cero, retornar resultado inválido
        if (baseTotal <= 0.0) {
            return ProrrateoGastosGeneralesResult(
                gastoGeneralDiarioTotal = gastoGeneralDiarioTotal,
                baseProduccion = emptyList(),
                baseMercaderias = emptyList(),
                baseTotal = 0.0,
                itemsProrrateo = emptyList(),
                isValid = false,
                reason = "BASE TOTAL es cero. No hay productos con valor directo para prorratear.",
                sumaPorcentajes = 0.0
            )
        }
        
        // 5. PORCENTAJE DE CADA PRODUCTO Y PRORRATEO
        val allItems = mutableListOf<ProrrateoBaseItem>()
        
        for (item in baseProduccionItems) {
            val porcentajeParticipacion = (item.valorBase / baseTotal) * 100.0
            val gastoGeneralAsignado = gastoGeneralDiarioTotal * (porcentajeParticipacion / 100.0)
            val gastoGeneralUnitario = if (item.quantity > 0.0) gastoGeneralAsignado / item.quantity else 0.0
            
            allItems.add(
                item.copy(
                    porcentajeParticipacion = porcentajeParticipacion,
                    gastoGeneralAsignado = gastoGeneralAsignado,
                    gastoGeneralUnitario = gastoGeneralUnitario
                )
            )
        }
        
        for (item in baseMercaderiasConNombres) {
            val porcentajeParticipacion = (item.valorBase / baseTotal) * 100.0
            val gastoGeneralAsignado = gastoGeneralDiarioTotal * (porcentajeParticipacion / 100.0)
            val gastoGeneralUnitario = if (item.quantity > 0.0) gastoGeneralAsignado / item.quantity else 0.0
            
            allItems.add(
                item.copy(
                    porcentajeParticipacion = porcentajeParticipacion,
                    gastoGeneralAsignado = gastoGeneralAsignado,
                    gastoGeneralUnitario = gastoGeneralUnitario
                )
            )
        }
        
        // Verificar que la suma de porcentajes sea 100%
        val sumaPorcentajes = allItems.sumOf { it.porcentajeParticipacion }
        
        return ProrrateoGastosGeneralesResult(
            gastoGeneralDiarioTotal = gastoGeneralDiarioTotal,
            baseProduccion = baseProduccionItems.map { item ->
                val porcentajeParticipacion = (item.valorBase / baseTotal) * 100.0
                val gastoGeneralAsignado = gastoGeneralDiarioTotal * (porcentajeParticipacion / 100.0)
                val gastoGeneralUnitario = if (item.quantity > 0.0) gastoGeneralAsignado / item.quantity else 0.0
                item.copy(
                    porcentajeParticipacion = porcentajeParticipacion,
                    gastoGeneralAsignado = gastoGeneralAsignado,
                    gastoGeneralUnitario = gastoGeneralUnitario
                )
            },
            baseMercaderias = baseMercaderiasConNombres.map { item ->
                val porcentajeParticipacion = (item.valorBase / baseTotal) * 100.0
                val gastoGeneralAsignado = gastoGeneralDiarioTotal * (porcentajeParticipacion / 100.0)
                val gastoGeneralUnitario = if (item.quantity > 0.0) gastoGeneralAsignado / item.quantity else 0.0
                item.copy(
                    porcentajeParticipacion = porcentajeParticipacion,
                    gastoGeneralAsignado = gastoGeneralAsignado,
                    gastoGeneralUnitario = gastoGeneralUnitario
                )
            },
            baseTotal = baseTotal,
            itemsProrrateo = allItems,
            isValid = true,
            reason = null,
            sumaPorcentajes = sumaPorcentajes
        )
    }

    /**
     * Calcula los costos indirectos asignados a un producto específico.
     * Si un gasto o inversión tiene targetProductId específico, se asigna únicamente a los productos correspondientes.
     * De lo contrario, se distribuye proporcionalmente por la cuota PPD sobre el total de la cocina.
     */
    fun calculateAllocatedIndirectCosts(
        product: Product,
        productPpd: Double,
        totalKitchenPpd: Double,
        gastosGenerales: List<GastoGeneral>,
        inversiones: List<Inversion>,
        products: List<Product>,
        productosElaborados: List<ProductoElaborado>
    ): Pair<Double, Double> {
        val prodElabMap = productosElaborados.associateBy { it.productId }
        val sharedPpdShare = if (totalKitchenPpd > 0.0) productPpd / totalKitchenPpd else 0.0

        var allocatedGastoGeneral = 0.0
        val activeGastos = gastosGenerales.filter { it.isActive && it.inversionId == null && it.scope == "PRODUCCION" }
        for (gasto in activeGastos) {
            val daily = gasto.dailyCost()
            val target = gasto.targetProductId
            if (target != null && target != 0L) {
                if (target == product.id) {
                    val sharingProducts = products.filter { p ->
                        p.destination == "COCINA" && p.isAvailable && p.id == target
                    }
                    val targetPpdSum = sharingProducts.sumOf { p -> prodElabMap[p.id]?.effectivePpd ?: 10.0 }
                    val share = if (targetPpdSum > 0.0) productPpd / targetPpdSum else 1.0
                    allocatedGastoGeneral += daily * share
                }
            } else {
                allocatedGastoGeneral += daily * sharedPpdShare
            }
        }

        var allocatedDepreciation = 0.0
        if (inversiones.isNotEmpty()) {
            for (inv in inversiones.filter { it.scope == "PRODUCCION" }) {
                val dailyDep = inv.dailyDepreciation()
                val target = inv.targetProductId
                if (target != null && target != 0L) {
                    if (target == product.id) {
                        val sharingProducts = products.filter { p ->
                            p.destination == "COCINA" && p.isAvailable && p.id == target
                        }
                        val targetPpdSum = sharingProducts.sumOf { p -> prodElabMap[p.id]?.effectivePpd ?: 10.0 }
                        val share = if (targetPpdSum > 0.0) productPpd / targetPpdSum else 1.0
                        allocatedDepreciation += dailyDep * share
                    }
                } else {
                    allocatedDepreciation += dailyDep * sharedPpdShare
                }
            }
        } else {
            val activeDepGastos = gastosGenerales.filter { it.isActive && it.inversionId != null && it.scope == "PRODUCCION" }
            for (gasto in activeDepGastos) {
                val daily = gasto.dailyCost()
                val target = gasto.targetProductId
                if (target != null && target != 0L) {
                    if (target == product.id) {
                        allocatedDepreciation += daily
                    }
                } else {
                    allocatedDepreciation += daily * sharedPpdShare
                }
            }
        }

        return Pair(allocatedGastoGeneral, allocatedDepreciation)
    }

    /**
     * Calcula los detalles de costo de ingredientes para un producto.
     */
    fun calculateIngredientDetails(
        productId: Long,
        recetaIngredientes: List<RecetaIngrediente>,
        materiasPrimas: List<MateriaPrima>
    ): List<IngredientCostDetail> {
        val mpMap = materiasPrimas.associateBy { it.id }
        val ingredients = recetaIngredientes.filter { it.productoElaboradoId == productId }
        
        return ingredients.map { ing ->
            val mp = mpMap[ing.materiaPrimaId]
            val mpUnit = mp?.unit ?: ing.unit
            val convertedQty = if (mp != null) {
                UnitConverter.convert(ing.quantity, ing.unit, mpUnit) ?: ing.quantity
            } else {
                ing.quantity
            }
            val unitCost = mp?.unitCost ?: 0.0
            val totalCost = convertedQty * unitCost
            IngredientCostDetail(
                materiaPrima = mp,
                quantity = convertedQty,
                unit = mpUnit,
                unitCost = unitCost,
                totalCost = totalCost
            )
        }
    }

    /**
     * Calcula el Costo Directo Unitario (CDU) de un producto.
     */
    fun calculateCostoDirectoUnitario(
        ingredientDetails: List<IngredientCostDetail>,
        baseYield: Double
    ): Double {
        val totalDirectCost = ingredientDetails.sumOf { it.totalCost }
        val yield = if (baseYield > 0.0) baseYield else 1.0
        return totalDirectCost / yield
    }

    /**
     * Mantiene compatibilidad con versiones previas calculando el movimiento diario a costo.
     */
    fun calculateTotalDailyKitchenMovement(
        products: List<Product>,
        productosElaborados: List<ProductoElaborado>,
        recetaIngredientes: List<RecetaIngrediente>,
        materiasPrimas: List<MateriaPrima>
    ): Double {
        return calculateTotalKitchenPpd(products, productosElaborados)
    }

    /**
     * Genera la Ficha de Costo Completa distribuyendo costos indirectos según PPD y con trazabilidad total.
     */
    fun calculateCostSheet(
        product: Product,
        products: List<Product>,
        productosElaborados: List<ProductoElaborado>,
        recetaIngredientes: List<RecetaIngrediente>,
        materiasPrimas: List<MateriaPrima>,
        gastosGenerales: List<GastoGeneral>,
        inversiones: List<Inversion> = emptyList(),
        mercaderias: List<Mercaderia> = emptyList(),
        movimientosMercaderia: List<MovimientoMercaderia> = emptyList()
    ): ProductCostSheet {
        val prodElaborado = productosElaborados.find { it.productId == product.id }
        val isProdElaboradoMissing = prodElaborado == null
        val ingredientDetails = calculateIngredientDetails(product.id, recetaIngredientes, materiasPrimas)
        val isRecipeMissing = ingredientDetails.isEmpty()

        val isComplete = !isProdElaboradoMissing && !isRecipeMissing
        val missingDataReason = when {
            isProdElaboradoMissing -> "Faltan datos de producción (Producto Elaborado no vinculado)."
            isRecipeMissing -> "Faltan datos de receta / materia prima."
            else -> null
        }

        // 1. COSTOS DIRECTOS: Ingredientes y rendimiento
        val totalDirectRecipeCost = ingredientDetails.sumOf { it.totalCost }
        val baseYield = prodElaborado?.baseYield?.takeIf { it > 0.0 } ?: 1.0
        val productionUnit = prodElaborado?.productionUnit ?: "unidades"
        val cdu = if (baseYield > 0.0) totalDirectRecipeCost / baseYield else 0.0

        // 2. PRODUCCIÓN PROMEDIO DIARIA (PPD) Y PARTICIPACIÓN
        val productPpd = prodElaborado?.effectivePpd ?: 10.0
        val totalKitchenPpd = calculateTotalKitchenPpd(products, productosElaborados)

        // Si el producto no está activo o su PPD es 0, no recibe participación ni costos indirectos (Producto Ocasional inactivo)
        val porcentajeParticipacionPpd = if (product.isAvailable && productPpd > 0.0 && totalKitchenPpd > 0.0) {
            (productPpd / totalKitchenPpd) * 100.0
        } else {
            0.0
        }

        // 3. GASTOS E INVERSIONES TOTALES DIARIOS
        val gastosGeneralesDiariosTotales = calculateTotalDailyOverheads(gastosGenerales)
        val depreciacionInversionesDiariaTotales = calculateTotalDailyDepreciation(inversiones, gastosGenerales)
        val costosIndirectosDiariosTotales = gastosGeneralesDiariosTotales + depreciacionInversionesDiariaTotales

        // 4. PRORRATEO DE COSTOS INDIRECTOS (PPD + RECURSO ESPECÍFICO SI EXISTE)
        val (_, depreciacionAsignada) = if (product.isAvailable && productPpd > 0.0) {
            calculateAllocatedIndirectCosts(
                product = product,
                productPpd = productPpd,
                totalKitchenPpd = totalKitchenPpd,
                gastosGenerales = gastosGenerales,
                inversiones = inversiones,
                products = products,
                productosElaborados = productosElaborados
            )
        } else {
            Pair(0.0, 0.0)
        }

        val prorrateoResult = calcularBaseProrrateoGastosGenerales(
            products = products,
            productosElaborados = productosElaborados,
            mercaderias = mercaderias,
            movimientosMercaderia = movimientosMercaderia,
            gastosGenerales = gastosGenerales,
            recetaIngredientes = recetaIngredientes,
            materiasPrimas = materiasPrimas
        )
        val prorrateoItem = prorrateoResult.itemsProrrateo.find { it.productId == product.id && it.type == "PRODUCCION" }
        val gastoGeneralAsignado = prorrateoItem?.gastoGeneralAsignado ?: 0.0
        val gastoIndirectoAsignado = gastoGeneralAsignado + depreciacionAsignada

        val gastoIndirectoUnitario = if (productPpd > 0.0 && product.isAvailable) {
            gastoIndirectoAsignado / productPpd
        } else {
            0.0
        }

        // 5. PAGOS DE PERSONAL ASOCIADOS
        val pagoCocinaUnitario = prodElaborado?.pagoCocinaUnitario ?: 0.0
        val cantidadCocineros = prodElaborado?.cantidadCocineros?.takeIf { it > 0 } ?: 1
        val totalPagoCocina = if (pagoCocinaUnitario > 0.0) pagoCocinaUnitario * cantidadCocineros else 0.0
        val pagoDependienteUnitario = prodElaborado?.pagoDependienteUnitario ?: 0.0
        val totalPagoDependiente = pagoDependienteUnitario
        val pagoCajeroUnitario = prodElaborado?.pagoCajeroUnitario ?: 0.0
        val totalPagoCajero = pagoCajeroUnitario
        val totalPagoPersonal = totalPagoCocina + totalPagoDependiente + totalPagoCajero

        // 6. COSTO REAL UNITARIO / COSTO TEÓRICO (Materias Primas + Gastos Indirectos + Pagos de Personal)
        val costoRealUnitario = cdu + gastoIndirectoUnitario + totalPagoPersonal

        // Gasto general prorrateado por unidad y Costo total unitario (Prompt 3)
        val gastoGeneralUnitarioProrrateo = if (productPpd > 0.0 && product.isAvailable) {
            gastoGeneralAsignado / productPpd
        } else {
            0.0
        }
        val costoTotalUnitario = cdu + gastoGeneralUnitarioProrrateo

        // 7. PRECIO DE REFERENCIA (+30% margen sugerido)
        val targetMarginPct = prodElaborado?.targetMarginPct ?: 30.0
        val precioReferencia = if (costoRealUnitario > 0.0) {
            costoRealUnitario * (1.0 + targetMarginPct / 100.0)
        } else {
            0.0
        }

        // 8. PRECIO DEFINITIVO
        val definitivePrice = prodElaborado?.precioDefinitivo ?: 0.0
        val hasDefinitivePrice = prodElaborado?.hasPrecioDefinitivo == true && definitivePrice > 0.0

        return ProductCostSheet(
            product = product,
            productoElaborado = prodElaborado,
            ingredientDetails = ingredientDetails,
            totalDirectRecipeCost = totalDirectRecipeCost,
            baseYield = baseYield,
            productionUnit = productionUnit,
            costoDirectoUnitario = cdu,
            ppd = productPpd,
            totalKitchenPpd = totalKitchenPpd,
            porcentajeParticipacionPpd = porcentajeParticipacionPpd,
            gastosGeneralesDiariosTotales = gastosGeneralesDiariosTotales,
            depreciacionInversionesDiariaTotales = depreciacionInversionesDiariaTotales,
            costosIndirectosDiariosTotales = costosIndirectosDiariosTotales,
            gastoGeneralAsignado = gastoGeneralAsignado,
            depreciacionAsignada = depreciacionAsignada,
            gastoIndirectoAsignado = gastoIndirectoAsignado,
            gastoIndirectoUnitario = gastoIndirectoUnitario,
            gastoGeneralUnitarioProrrateo = gastoGeneralUnitarioProrrateo,
            pagoCocinaUnitario = pagoCocinaUnitario,
            cantidadCocineros = cantidadCocineros,
            totalPagoCocinaUnitario = totalPagoCocina,
            pagoDependienteUnitario = pagoDependienteUnitario,
            totalPagoDependienteUnitario = totalPagoDependiente,
            pagoCajeroUnitario = pagoCajeroUnitario,
            totalPagoCajeroUnitario = totalPagoCajero,
            totalPagoPersonalUnitario = totalPagoPersonal,
            costoTotalUnitario = costoTotalUnitario,
            costoRealUnitario = costoRealUnitario,
            precioReferencia = precioReferencia,
            precioDefinitivo = definitivePrice,
            hasPrecioDefinitivo = hasDefinitivePrice,
            isComplete = isComplete,
            missingDataReason = missingDataReason,
            estimatedDailyQuantity = productPpd,
            movimientoDiarioProducto = cdu * productPpd,
            movimientoDiarioTotal = totalKitchenPpd,
            porcentajeProrrateo = porcentajeParticipacionPpd
        )
    }

    /**
     * Calcula la existencia actual de una mercadería a partir de sus movimientos.
     */
    fun getMercaderiaCurrentStock(
        mercaderiaId: Long,
        initialStock: Double,
        movimientos: List<MovimientoMercaderia>
    ): Double {
        val mercMovs = movimientos.filter { it.mercaderiaId == mercaderiaId }
        val hasInitial = mercMovs.any { it.type == "INVENTARIO_INICIAL" }
        var current = if (hasInitial) 0.0 else initialStock
        for (m in mercMovs) {
            when (m.type.uppercase()) {
                "INVENTARIO_INICIAL", "ENTRADA", "AJUSTE_POSITIVO" -> current += m.quantity
                "SALIDA", "AJUSTE_NEGATIVO", "MERMA", "PARA_VENTA" -> current -= m.quantity
                "AJUSTE" -> current += m.quantity
            }
        }
        return maxOf(0.0, current)
    }

    /**
     * Sobrecarga de conveniencia que acepta MainUiState y MainViewModel
     */

    private fun getPeriodAcquisitionQuantity(
        mercaderiaId: Long,
        movimientos: List<MovimientoMercaderia>,
        windowStart: Long,
        windowEnd: Long
    ): Double {
        return movimientos.filter { 
            it.mercaderiaId == mercaderiaId && 
            it.date in windowStart..windowEnd && 
            (it.type == "ENTRADA" || it.type == "INVENTARIO_INICIAL") 
        }.sumOf { it.quantity }
    }

    fun calculateMercaderiaCostSheet(
        mercaderia: Mercaderia,
        uiState: com.example.ui.viewmodel.MainUiState,
        viewModel: com.example.ui.viewmodel.MainViewModel,
        targetMarginPct: Double = 30.0
    ): MercaderiaCostSheet {
        return calculateMercaderiaCostSheet(
            mercaderia = mercaderia,
            mercaderias = uiState.mercaderias,
            products = uiState.products,
            movimientos = uiState.movimientosMercaderia,
            gastosGenerales = uiState.gastosGenerales,
            inversiones = uiState.inversiones,
            targetMarginPct = targetMarginPct,
            productosElaborados = uiState.productosElaborados,
            recetaIngredientes = uiState.recetaIngredientes,
            materiasPrimas = uiState.materiasPrimas
        )
    }

    fun calculateMercaderiaCostSheet(
        mercaderia: Mercaderia,
        mercaderias: List<Mercaderia>,
        products: List<Product>,
        movimientos: List<MovimientoMercaderia>,
        gastosGenerales: List<GastoGeneral>,
        inversiones: List<Inversion> = emptyList(),
        targetMarginPct: Double = 30.0,
        productosElaborados: List<ProductoElaborado> = emptyList(),
        recetaIngredientes: List<RecetaIngrediente> = emptyList(),
        materiasPrimas: List<MateriaPrima> = emptyList()
    ): MercaderiaCostSheet {
        val product = products.find { it.id == mercaderia.productId }
            ?: Product(
                id = mercaderia.productId,
                code = "MERC-${mercaderia.id}",
                name = "Mercadería #${mercaderia.id}",
                category = "Bebidas",
                destination = "BARRA",
                price = 0.0,
                cost = mercaderia.acquisitionCost,
                unitOfMeasure = mercaderia.unitOfMeasure
            )

        val currentStock = getMercaderiaCurrentStock(mercaderia.id, mercaderia.initialStock, movimientos)
        val effectiveStock = if (currentStock > 0.0) currentStock else maxOf(mercaderia.initialStock, 1.0)
        val myAcquisitionValue = mercaderia.acquisitionCost * effectiveStock
        val now = System.currentTimeMillis()

        // 1. GASTOS INDIRECTOS: Prorrateados (General) o asignados (Puntual)
        val detailedExpenses = mutableListOf<AllocatedCostItem>()
        var gastosComunesAsignados = 0.0
        var gastosEspecificosAsignados = 0.0

        val prorrateoResult = calcularBaseProrrateoGastosGenerales(
            products = products,
            productosElaborados = productosElaborados,
            mercaderias = mercaderias,
            movimientosMercaderia = movimientos,
            gastosGenerales = gastosGenerales,
            recetaIngredientes = recetaIngredientes,
            materiasPrimas = materiasPrimas
        )
        val prorrateoItem = prorrateoResult.itemsProrrateo.find { it.id == mercaderia.id && it.type == "MERCADERIAS" }

        // Procesamos TODOS los gastos activos (sin inversiones)
        val activeGastos = gastosGenerales.filter { it.isActive && it.inversionId == null }
        for (g in activeGastos) {
            val dailyEq = g.dailyCost()
            
            // Verificación de Período
            if (g.startDate != null && g.startDate != 0L && now < g.startDate) continue
            if (g.endDate != null && g.endDate != 0L && now > g.endDate) continue

            val targetIds = mutableListOf<Long>()
            if (!g.targetProductIds.isNullOrBlank()) {
                g.targetProductIds.split(",").mapNotNull { it.trim().toLongOrNull() }.let { targetIds.addAll(it) }
            } else if (g.targetProductId != null && g.targetProductId != 0L) {
                targetIds.add(g.targetProductId!!)
            }

            val isSpecific = targetIds.isNotEmpty()

            if (isSpecific) {
                val isMyShare = targetIds.contains(product.id)
                if (isMyShare) {
                    val sharingMercs = mercaderias.filter { m -> targetIds.contains(m.productId) && m.isActive }
                    val totalPeriodValue = sharingMercs.sumOf { m ->
                        val s = getMercaderiaCurrentStock(m.id, m.initialStock, movimientos)
                        val effS = if (s > 0.0) s else maxOf(m.initialStock, 1.0)
                        m.acquisitionCost * effS
                    }.takeIf { it > 0.0 } ?: 1.0

                    val myValue = effectiveStock * mercaderia.acquisitionCost
                    val myShare = myValue / totalPeriodValue

                    val allocatedDaily = dailyEq * myShare
                    val unitAlloc = if (effectiveStock > 0.0) allocatedDaily / effectiveStock else 0.0

                    gastosEspecificosAsignados += allocatedDaily

                    detailedExpenses.add(
                        AllocatedCostItem(
                            id = g.id,
                            name = g.name,
                            category = g.category,
                            originalAmount = g.amount,
                            period = g.period,
                            dailyEquivalent = dailyEq,
                            isSpecific = true,
                            allocatedDailyAmount = allocatedDaily,
                            allocatedUnitAmount = unitAlloc
                        )
                    )
                }
            } else {
                val baseTotal = prorrateoResult.baseTotal
                val myBaseValue = effectiveStock * (mercaderia.acquisitionCost + mercaderia.directExpenses)
                val myShare = if (baseTotal > 0.0) myBaseValue / baseTotal else 0.0

                if (myShare > 0.0) {
                    val allocatedDaily = dailyEq * myShare
                    val unitAlloc = if (effectiveStock > 0.0) allocatedDaily / effectiveStock else 0.0

                    gastosComunesAsignados += allocatedDaily

                    detailedExpenses.add(
                        AllocatedCostItem(
                            id = g.id,
                            name = g.name,
                            category = g.category,
                            originalAmount = g.amount,
                            period = g.period,
                            dailyEquivalent = dailyEq,
                            isSpecific = false,
                            allocatedDailyAmount = allocatedDaily,
                            allocatedUnitAmount = unitAlloc
                        )
                    )
                }
            }
        }
        val totalGastosAsignados = gastosComunesAsignados + gastosEspecificosAsignados

        // 2. INVERSIONES Y DEPRECIACIÓN
        val detailedInversions = mutableListOf<AllocatedInversionItem>()
        var depreciacionComunAsignada = 0.0
        var depreciacionEspecificaAsignada = 0.0

        for (inv in inversiones.filter { it.scope == "MERCADERIAS" }) {
            val dailyDep = inv.dailyDepreciation()
            val monthlyDep = inv.monthlyDepreciation()
            
            if (inv.startDate != null && inv.startDate != 0L && now < inv.startDate) continue
            if (inv.endDate != null && inv.endDate != 0L && now > inv.endDate) continue

            val targetIds = mutableListOf<Long>()
            if (!inv.targetProductIds.isNullOrBlank()) {
                inv.targetProductIds.split(",").mapNotNull { it.trim().toLongOrNull() }.let { targetIds.addAll(it) }
            } else if (inv.targetProductId != null && inv.targetProductId != 0L) {
                targetIds.add(inv.targetProductId!!)
            }

            val isMyShare = if (targetIds.isNotEmpty()) targetIds.contains(product.id) else mercaderia.isActive

            if (isMyShare) {
                val sharingMercs = if (targetIds.isNotEmpty()) {
                    mercaderias.filter { m -> targetIds.contains(m.productId) && m.isActive }
                } else {
                    mercaderias.filter { it.isActive }
                }
                var totalPeriodValue = sharingMercs.sumOf { m ->
                    val s = getMercaderiaCurrentStock(m.id, m.initialStock, movimientos)
                    val effS = if (s > 0.0) s else maxOf(m.initialStock, 1.0)
                    m.acquisitionCost * effS
                }.takeIf { it > 0.0 } ?: 1.0

                val myShare = myAcquisitionValue / totalPeriodValue
                
                val allocatedDaily = dailyDep * myShare
                val unitAlloc = if (effectiveStock > 0.0) allocatedDaily / effectiveStock else 0.0

                if (targetIds.isNotEmpty()) {
                    depreciacionEspecificaAsignada += allocatedDaily
                } else {
                    depreciacionComunAsignada += allocatedDaily
                }

                detailedInversions.add(
                    AllocatedInversionItem(
                        id = inv.id,
                        name = inv.name,
                        category = inv.category,
                        originalAmount = inv.amount,
                        usefulLifeText = "${inv.usefulLife.toInt()} ${inv.usefulLifeUnit.lowercase()}",
                        monthlyDepreciation = monthlyDep,
                        dailyDepreciation = dailyDep,
                        isSpecific = targetIds.isNotEmpty(),
                        allocatedDailyAmount = allocatedDaily,
                        allocatedUnitAmount = unitAlloc
                    )
                )
            }
        }
        val totalDepreciacionAsignada = depreciacionComunAsignada + depreciacionEspecificaAsignada

        // 3. COSTOS INDIRECTOS TOTALES Y UNITARIOS
        val totalCostosIndirectosAsignados = totalGastosAsignados + totalDepreciacionAsignada
        val gastoIndirectoUnitario = detailedExpenses.sumOf { it.allocatedUnitAmount } + detailedInversions.sumOf { it.allocatedUnitAmount }

        // 4. COSTO DIRECTO Y COSTO TOTAL UNITARIO (PROMPT 4)
        val directExpenses = mercaderia.directExpenses
        val costoDirectoUnitario = mercaderia.acquisitionCost + directExpenses
        val gastoGeneralUnitarioProrrateo = prorrateoItem?.gastoGeneralUnitario ?: 0.0
        val costoTotalUnitario = costoDirectoUnitario + gastoGeneralUnitarioProrrateo

        // COSTO REAL UNITARIO: COSTO DIRECTO UNITARIO + COSTOS INDIRECTOS
        val costoRealUnitario = costoDirectoUnitario + gastoIndirectoUnitario

        // 5. PRECIO DEFINITIVO
        val precioDefinitivo = product.price
        val hasPrecioDefinitivo = precioDefinitivo > 0.0
        val precioReferencia = if (costoRealUnitario > 0.0) {
            costoRealUnitario * (1.0 + targetMarginPct / 100.0)
        } else {
            0.0
        }

        // 6. UTILIDAD Y MARGEN
        val utilidadUnitaria = if (hasPrecioDefinitivo) precioDefinitivo - costoRealUnitario else precioReferencia - costoRealUnitario
        val margenPorcentual = if (hasPrecioDefinitivo) {
            (utilidadUnitaria / precioDefinitivo) * 100.0
        } else {
            targetMarginPct
        }
        val totalUtilidadProyectada = utilidadUnitaria * currentStock

        // Valor total de mercaderías para resumen
        val totalMercaderiasValueForSummary = mercaderias.filter { it.isActive }.sumOf { m ->
            val s = getMercaderiaCurrentStock(m.id, m.initialStock, movimientos)
            val effS = if (s > 0.0) s else maxOf(m.initialStock, 1.0)
            m.acquisitionCost * effS
        }
        val shareRatio = if (totalMercaderiasValueForSummary > 0) myAcquisitionValue / totalMercaderiasValueForSummary else 0.0

        return MercaderiaCostSheet(
            mercaderia = mercaderia,
            product = product,
            acquisitionCost = mercaderia.acquisitionCost,
            directExpenses = directExpenses,
            costoDirectoUnitario = costoDirectoUnitario,
            currentStock = currentStock,
            totalAcquisitionValue = myAcquisitionValue,
            totalMercaderiasAcquisitionValue = totalMercaderiasValueForSummary,
            porcentajeParticipacion = shareRatio * 100.0,
            
            gastosComunesAsignados = gastosComunesAsignados,
            gastosEspecificosAsignados = gastosEspecificosAsignados,
            totalGastosAsignados = totalGastosAsignados,
            
            depreciacionComunAsignada = depreciacionComunAsignada,
            depreciacionEspecificaAsignada = depreciacionEspecificaAsignada,
            totalDepreciacionAsignada = totalDepreciacionAsignada,
            
            totalCostosIndirectosAsignados = totalCostosIndirectosAsignados,
            gastoIndirectoUnitario = gastoIndirectoUnitario,
            gastoGeneralUnitarioProrrateo = gastoGeneralUnitarioProrrateo,
            costoTotalUnitario = costoTotalUnitario,
            costoRealUnitario = costoRealUnitario,
            
            targetMarginPct = targetMarginPct,
            precioReferencia = precioReferencia,
            precioDefinitivo = precioDefinitivo,
            hasPrecioDefinitivo = hasPrecioDefinitivo,
            utilidadUnitaria = utilidadUnitaria,
            margenPorcentual = margenPorcentual,
            totalUtilidadProyectada = totalUtilidadProyectada,
            
            detailedExpenses = detailedExpenses,
            detailedInversions = detailedInversions
        )
    }
}
