package com.example.ui.screens.dueno

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.model.Jornada
import com.example.data.local.model.Mercaderia
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import com.example.ui.screens.cajero.TransferenciasPane
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.util.CuadreCajaArchiveManager
import com.example.util.CuadreCajaPdfExporter
import com.example.util.CuadrePagosManager
import com.example.util.CuadrePagosJornada
import com.example.util.DependientePagoDistribucion
import com.example.data.local.model.UserRole
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CuadreTab(val label: String) {
    PRODUCCION("PRODUCCIÓN"),
    MERCADERIA("MERCADERÍAS"),
    TRANSFERENCIA("TRANSFERENCIAS"),
    GENERALES("GENERALES"),
    PAGOS("PAGOS")
}

@Composable
private fun CuadreNavCard(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) ElQadreGold else Color(0xFF1E293B)
    val contentColor = if (isSelected) ElQadreNavy else Color(0xFFF1F5F9)
    val borderColor = if (isSelected) ElQadreGoldDark else Color(0xFF334155)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.2.dp, borderColor),
        shadowElevation = if (isSelected) 3.dp else 0.dp,
        modifier = modifier
            .height(38.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

data class ExtraccionItem(
    val id: Long = System.currentTimeMillis(),
    var montoStr: String = "",
    var descripcion: String = ""
)

class ProduccionItemState(
    val productId: Long,
    val productName: String,
    val unit: String,
    val price: Double,
    val tandasCount: Int = 0,
    val totalProduced: Double = 0.0,
    val qtyPerTanda: Double = 0.0,
    defectuosoStr: String = "0",
    consumoStr: String = "0",
    regaliaStr: String = "0",
    pendientesStr: String = "0",
    val costoUnitarioTeorico: Double = 0.0,
    val pagoCocinaUnitario: Double = 0.0,
    cantidadCocineros: Int = 1,
    val pagoDependienteUnitario: Double = 0.0,
    val pagoCajeroUnitario: Double = 0.0,
    val presentaciones: List<com.example.data.local.model.PresentacionEspecial> = emptyList(),
    val hasTanda00: Boolean = false
) {
    var defectuosoStr by mutableStateOf(defectuosoStr)
    var consumoStr by mutableStateOf(consumoStr)
    var regaliaStr by mutableStateOf(regaliaStr)
    var pendientesStr by mutableStateOf(pendientesStr)
    var customPriceStr by mutableStateOf(if (price > 0.0) "%.2f".format(price).replace(',', '.') else "")
    var cantidadCocinerosStr by mutableStateOf(if (cantidadCocineros > 0) cantidadCocineros.toString() else "1")
    val cantidadCocineros: Int get() = cantidadCocinerosStr.toIntOrNull()?.coerceAtLeast(1) ?: 1

    val effectivePrice: Double get() = customPriceStr.toDoubleOrNull() ?: price

    val effectiveTandasCount: Int get() = tandasCount
    val effectiveTotalProduced: Double get() = totalProduced
    val effectiveQtyPerTanda: Double get() = qtyPerTanda

    val defectuoso: Double get() = defectuosoStr.toDoubleOrNull() ?: 0.0
    val consumo: Double get() = consumoStr.toDoubleOrNull() ?: 0.0
    val regalia: Double get() = regaliaStr.toDoubleOrNull() ?: 0.0
    val pendientes: Double get() = pendientesStr.toDoubleOrNull() ?: 0.0
    val mermaTotal: Double get() = defectuoso + consumo + regalia

    // REGLA FUNDAMENTAL: PENDIENTE != VENDIDA
    // Las unidades pendientes NO forman parte de los ingresos, NO se incluyen en el cálculo
    // de ingresos, NO se consideran vendidas, NO incrementan el efectivo esperado.
    val vendible: Double get() = (effectiveTotalProduced - mermaTotal - pendientes).coerceAtLeast(0.0)
    val ingresoEstimado: Double get() = vendible * effectivePrice
    val costoEstimado: Double get() = vendible * costoUnitarioTeorico
    val mermaValor: Double get() = mermaTotal * effectivePrice
    val pagoCocinaEstimado: Double get() = vendible * pagoCocinaUnitario * (if (cantidadCocineros > 0) cantidadCocineros else 1)
    val pagoDependienteEstimado: Double get() = vendible * pagoDependienteUnitario
    val pagoCajeroEstimado: Double get() = vendible * pagoCajeroUnitario
}

class MercaderiaItemState(
    val mercaderiaId: Long,
    val productId: Long,
    val productName: String,
    val unit: String,
    val price: Double,
    val isConfitura: Boolean = false,
    existenciaInicialStr: String = "0",
    entradasStr: String = "0",
    existenciaFinalStr: String = "",
    defectuosoStr: String = "0",
    consumoStr: String = "0",
    regaliaStr: String = "0",
    val costoUnitarioTeorico: Double = 0.0,
    val pagoDependienteUnitario: Double = 0.0,
    val pagoCajeroUnitario: Double = 0.0
) {
    var existenciaInicialStr by mutableStateOf(existenciaInicialStr)
    var entradasStr by mutableStateOf(entradasStr)
    var existenciaFinalStr by mutableStateOf(existenciaFinalStr)
    var defectuosoStr by mutableStateOf(defectuosoStr)
    var consumoStr by mutableStateOf(consumoStr)
    var regaliaStr by mutableStateOf(regaliaStr)
    var customPriceStr by mutableStateOf(if (price > 0.0) "%.2f".format(price).replace(',', '.') else "")

    val effectivePrice: Double get() = customPriceStr.toDoubleOrNull() ?: price
    val isCompleted: Boolean get() = existenciaFinalStr.isNotBlank()
    val existenciaInicial: Double get() = existenciaInicialStr.toDoubleOrNull() ?: 0.0
    val entradas: Double get() = entradasStr.toDoubleOrNull() ?: 0.0
    val existenciaFinal: Double get() = existenciaFinalStr.toDoubleOrNull() ?: 0.0
    val defectuoso: Double get() = defectuosoStr.toDoubleOrNull() ?: 0.0
    val consumo: Double get() = consumoStr.toDoubleOrNull() ?: 0.0
    val regalia: Double get() = regaliaStr.toDoubleOrNull() ?: 0.0
    val mermaTotal: Double get() = defectuoso + consumo + regalia
    val existenciaDisponible: Double get() = existenciaInicial + entradas
    val ventas: Double get() = if (isCompleted) {
        (existenciaDisponible - existenciaFinal - mermaTotal).coerceAtLeast(0.0)
    } else 0.0

    val ingresoEstimado: Double get() = if (isCompleted) ventas * effectivePrice else 0.0
    val costoEstimado: Double get() = if (isCompleted) ventas * costoUnitarioTeorico else 0.0
    val mermaValor: Double get() = mermaTotal * effectivePrice
    val pagoDependienteEstimado: Double get() = ventas * pagoDependienteUnitario
    val pagoCajeroEstimado: Double get() = ventas * pagoCajeroUnitario
}

class AgregadoCuadreItemState(
    val materiaPrimaId: Long,
    val name: String,
    val unit: String,
    val rationQuantity: Double,
    val rationUnit: String,
    val precioVenta: Double,
    val costoPorRacion: Double,
    racionesEnviadasStr: String = "0",
    racionesVendidasStr: String = "0",
    racionesRegaliaStr: String = "0"
) {
    var racionesEnviadasStr by mutableStateOf(racionesEnviadasStr)
    var racionesVendidasStr by mutableStateOf(racionesVendidasStr)
    var racionesRegaliaStr by mutableStateOf(racionesRegaliaStr)

    val racionesEnviadas: Double get() = racionesEnviadasStr.toDoubleOrNull() ?: 0.0
    val racionesVendidas: Double get() = racionesVendidasStr.toDoubleOrNull() ?: 0.0
    val racionesRegalia: Double get() = racionesRegaliaStr.toDoubleOrNull() ?: 0.0
    val racionesSobrantes: Double get() = (racionesEnviadas - racionesVendidas - racionesRegalia).coerceAtLeast(0.0)
    val ingresoEstimado: Double get() = racionesVendidas * precioVenta
    val costoVendido: Double get() = racionesVendidas * costoPorRacion
    val sobranteFisico: Double get() = racionesSobrantes * rationQuantity
}

@Composable
fun CuadreCajaScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showArchivoScreen by remember { mutableStateOf(false) }

    if (showArchivoScreen) {
        CuadreCajaArchivoScreen(
            uiState = uiState,
            onBack = { showArchivoScreen = false },
            modifier = modifier
        )
        return
    }

    var selectedTab by rememberSaveable { mutableStateOf(CuadreTab.PRODUCCION) }
    var showAvisoPagosDialog by rememberSaveable { mutableStateOf(false) }

    val activeJornada = uiState.activeJornada
    val isJornadaOpen = activeJornada != null && activeJornada.isOpen
    val initialCash = activeJornada?.initialCash ?: 0.0
    val activeJornadaId = activeJornada?.id ?: 1L
    val savedPagos = remember(activeJornadaId) {
        CuadrePagosManager.getPagosJornada(context, activeJornadaId)
    }

    // Filter tandas of active jornada / current day
    val jornadaTandas = remember(uiState.tandas, activeJornada) {
        if (activeJornada != null) {
            uiState.tandas.filter { it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt) }
        } else {
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            uiState.tandas.filter { it.date >= startOfToday }
        }
    }

    // Build Produccion item states from registered tandas
    val produccionStates = remember(
        jornadaTandas, uiState.products, uiState.productosElaborados, uiState.recetaIngredientes,
        uiState.materiasPrimas, uiState.gastosGenerales, uiState.inversiones
    ) {
        val grouped = jornadaTandas.groupBy { it.productId }
        grouped.map { (prodId, tandas) ->
            val product = uiState.products.find { it.id == prodId }
            val prodElab = uiState.productosElaborados.find { it.productId == prodId }
            val prodName = product?.name ?: tandas.firstOrNull()?.productName ?: "Producto #$prodId"

            val price = if (prodElab?.hasPrecioDefinitivo == true && prodElab.precioDefinitivo > 0.0) {
                prodElab.precioDefinitivo
            } else {
                product?.price ?: tandas.firstOrNull()?.salePrice ?: 0.0
            }

            val unit = prodElab?.productionUnit?.ifBlank { product?.unitOfMeasure } ?: tandas.firstOrNull()?.productionUnit ?: "U"
            val totalQty = tandas.sumOf { if (it.actualYield > 0) it.actualYield else it.estimatedYield }
            val count = tandas.size
            val perTanda = if (count > 0) totalQty / count else 0.0

            val costSheet = if (product != null) {
                com.example.util.CostCalculationHelper.calculateCostSheet(
                    product = product,
                    uiState = uiState
                )
            } else null

            val costoUnitario = costSheet?.costoRealUnitario ?: product?.cost ?: 0.0
            val pagoCocinaUnit = costSheet?.pagoCocinaUnitario ?: prodElab?.pagoCocinaUnitario ?: 0.0
            val cantCocineros = savedPagos?.cantidadCocineros ?: 1
            val pagoDepUnit = costSheet?.totalPagoDependienteUnitario ?: prodElab?.totalPagoDependienteUnitario ?: 0.0
            val pagoCajUnit = costSheet?.totalPagoCajeroUnitario ?: prodElab?.totalPagoCajeroUnitario ?: 0.0
            val presList = product?.let { com.example.data.local.model.parsePresentacionesEspeciales(it.presentacionesEspeciales) } ?: emptyList()

            // Pre-llenar unidades pendientes si las tandas ya contienen la anotación de pendientes
            val pendingFromTandas = tandas.map { com.example.util.QJornadaExporter.extractUnidadesPendientesFromObservation(it.observation) }.maxOrNull() ?: 0.0
            val pendingStr = if (pendingFromTandas > 0.0) {
                if (pendingFromTandas % 1.0 == 0.0) pendingFromTandas.toInt().toString() else "%.1f".format(pendingFromTandas)
            } else "0"
            val containsTanda00 = tandas.any { it.tandaNumber == "00" }

            ProduccionItemState(
                productId = prodId,
                productName = prodName,
                unit = unit,
                price = price,
                tandasCount = count,
                totalProduced = totalQty,
                qtyPerTanda = perTanda,
                pendientesStr = pendingStr,
                costoUnitarioTeorico = costoUnitario,
                pagoCocinaUnitario = pagoCocinaUnit,
                cantidadCocineros = cantCocineros,
                pagoDependienteUnitario = pagoDepUnit,
                pagoCajeroUnitario = pagoCajUnit,
                presentaciones = presList,
                hasTanda00 = containsTanda00
            )
        }.toMutableStateList()
    }

    // Build Mercaderias item states from active mercaderias
    val mercaderiasStates = remember(
        uiState.mercaderias, uiState.products, uiState.movimientosMercaderia,
        uiState.tarifasPagoBebidas, uiState.gastosGenerales, uiState.inversiones, activeJornada,
        uiState.productosElaborados, uiState.recetaIngredientes, uiState.materiasPrimas
    ) {
        val activeMercs = uiState.mercaderias.filter { it.isActive }
        val list = mutableListOf<MercaderiaItemState>()
        activeMercs.forEach { merc ->
            val product = uiState.products.find { it.id == merc.productId }
            val prodName = product?.name ?: "Mercadería #${merc.id}"
            val price = product?.price ?: 0.0
            val unit = merc.unitOfMeasure.ifBlank { product?.unitOfMeasure ?: "U" }

            val entradasJornada = if (activeJornada != null) {
                uiState.movimientosMercaderia.filter {
                    it.mercaderiaId == merc.id &&
                    (it.type.uppercase() == "ENTRADA" || it.type.uppercase() == "PARA VENTA" || it.type.uppercase() == "ENTRADA_STOCK") &&
                    (it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt))
                }.sumOf { it.quantity }
            } else 0.0

            val isConfitura = (product?.category?.uppercase() == "CONFITURAS")

            val mercCostSheet = if (product != null) {
                com.example.util.CostCalculationHelper.calculateMercaderiaCostSheet(
                    mercaderia = merc,
                    mercaderias = uiState.mercaderias,
                    products = uiState.products,
                    movimientos = uiState.movimientosMercaderia,
                    gastosGenerales = uiState.gastosGenerales,
                    inversiones = uiState.inversiones,
                    productosElaborados = uiState.productosElaborados,
                    recetaIngredientes = uiState.recetaIngredientes,
                    materiasPrimas = uiState.materiasPrimas,
                    tarifasPagoBebidas = uiState.tarifasPagoBebidas
                )
            } else null

            val costoUnitario = mercCostSheet?.costoRealUnitario ?: merc.acquisitionCost

            list.add(
                MercaderiaItemState(
                    mercaderiaId = merc.id,
                    productId = merc.productId,
                    productName = prodName,
                    unit = unit,
                    price = price,
                    isConfitura = isConfitura,
                    existenciaInicialStr = if (merc.initialStock > 0.0) "%.1f".format(merc.initialStock).replace(',', '.') else "0",
                    entradasStr = if (entradasJornada > 0.0) "%.1f".format(entradasJornada).replace(',', '.') else "0",
                    existenciaFinalStr = "",
                    costoUnitarioTeorico = costoUnitario,
                    pagoDependienteUnitario = if (isConfitura) 0.0 else (mercCostSheet?.pagoDependienteUnitario ?: uiState.tarifasPagoBebidas.calcularPagoDependiente(price)),
                    pagoCajeroUnitario = if (isConfitura) 0.0 else (mercCostSheet?.pagoCajeroUnitario ?: uiState.tarifasPagoBebidas.calcularPagoCajero(price))
                )
            )
        }
        list.toMutableStateList()
    }

    // Build Agregados item states from active materias primas configured as agregados
    val agregadosStates = remember(uiState.materiasPrimas) {
        uiState.materiasPrimas.filter { it.isAgregado && it.isActive }.map { mp ->
            val enviadasRaciones = if (mp.racionesEnVenta > 0.0) {
                mp.racionesEnVenta
            } else if (mp.stockEnVenta > 0.0 && mp.rationQuantity > 0.0) {
                mp.stockEnVenta / mp.rationQuantity
            } else {
                0.0
            }
            AgregadoCuadreItemState(
                materiaPrimaId = mp.id,
                name = mp.name,
                unit = mp.unit,
                rationQuantity = mp.rationQuantity,
                rationUnit = mp.rationUnit.ifBlank { mp.unit },
                precioVenta = mp.precioEfectivoVenta,
                costoPorRacion = mp.costoPorRacion,
                racionesEnviadasStr = if (enviadasRaciones > 0.0) "%.1f".format(enviadasRaciones).replace(',', '.') else "0",
                racionesVendidasStr = "0",
                racionesRegaliaStr = "0"
            )
        }.toMutableStateList()
    }

    // Calculate Transferencias
    val transferenciasForJornada = remember(uiState.allTransferencias, activeJornada) {
        if (activeJornada != null && activeJornada.openedAt > 0) {
            uiState.allTransferencias.filter { it.receivedAt >= activeJornada.openedAt }
        } else {
            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            uiState.allTransferencias.filter { it.receivedAt >= startOfToday }
        }
    }
    val transferenciasTotalMonto = remember(transferenciasForJornada) {
        transferenciasForJornada.sumOf { it.amount }
    }

    var pagosConfirmados by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.isConfirmed ?: false)
    }
    var totalPagosConfirmados by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.totalPagos ?: 0.0)
    }

    // Extracciones & Cash & Notes State
    val extraccionesList = remember(activeJornadaId) {
        mutableStateListOf<ExtraccionItem>().apply {
            if (activeJornada?.extracciones ?: 0.0 > 0.0) {
                add(ExtraccionItem(montoStr = (activeJornada?.extracciones ?: 0.0).toString(), descripcion = "Extracción registrada"))
            } else {
                add(ExtraccionItem())
            }
        }
    }
    var efectivoRealStr by rememberSaveable { mutableStateOf("") }
    var notasCuadre by rememberSaveable { mutableStateOf(activeJornada?.notes ?: "") }

    var isCuadrado by rememberSaveable { mutableStateOf(false) }
    var lastGeneratedPdfFile by remember { mutableStateOf<File?>(null) }

    // Dynamic Calculations
    val ingresosAgregados = agregadosStates.sumOf { it.ingresoEstimado }
    val ingresosProduccion = produccionStates.sumOf { it.ingresoEstimado } + ingresosAgregados
    val ingresosMercaderias = mercaderiasStates.sumOf { it.ingresoEstimado }
    val totalIngresosGenerales = ingresosProduccion + ingresosMercaderias

    val costoProduccionVal = produccionStates.sumOf { it.costoEstimado }
    val costoAgregadosVal = agregadosStates.sumOf { it.costoVendido }
    val costoMercaderiasVal = mercaderiasStates.sumOf { it.costoEstimado }
    val costoTotalTotal = costoProduccionVal + costoAgregadosVal + costoMercaderiasVal
    val utilidadTeorica = totalIngresosGenerales - costoTotalTotal

    val totalMermasValor = produccionStates.sumOf { it.mermaValor } + mercaderiasStates.sumOf { it.mermaValor }
    val totalMermasUnidades = produccionStates.sumOf { it.mermaTotal } + mercaderiasStates.sumOf { it.mermaTotal }

    val extraccionesVal = extraccionesList.sumOf { it.montoStr.toDoubleOrNull() ?: 0.0 }
    val efectivoRealVal = efectivoRealStr.toDoubleOrNull() ?: 0.0

    // Personal Payments Calculation
    val totalPagoCocina = produccionStates.sumOf { item ->
        item.vendible * item.pagoCocinaUnitario * (if (item.cantidadCocineros > 0) item.cantidadCocineros else 1)
    }
    val pagoDependienteProduccion = produccionStates.sumOf { item ->
        item.vendible * item.pagoDependienteUnitario
    }
    val pagoCajeroProduccion = produccionStates.sumOf { item ->
        item.vendible * item.pagoCajeroUnitario
    }
    val pagoDependienteMercaderia = mercaderiasStates.sumOf { item ->
        if (item.isConfitura) 0.0 else {
            val depTarifa = if (item.pagoDependienteUnitario > 0.0) item.pagoDependienteUnitario else uiState.tarifasPagoBebidas.calcularPagoDependiente(item.price)
            item.ventas * depTarifa
        }
    }
    val pagoCajeroMercaderia = mercaderiasStates.sumOf { item ->
        if (item.isConfitura) 0.0 else {
            val cajTarifa = if (item.pagoCajeroUnitario > 0.0) item.pagoCajeroUnitario else uiState.tarifasPagoBebidas.calcularPagoCajero(item.price)
            item.ventas * cajTarifa
        }
    }
    val totalPagoDependiente = pagoDependienteProduccion + pagoDependienteMercaderia
    val totalPagoCajero = pagoCajeroProduccion + pagoCajeroMercaderia
    val totalPagoPersonalCalculado = totalPagoCocina + totalPagoDependiente + totalPagoCajero
    val totalPagosEfectivos = if (pagosConfirmados) totalPagosConfirmados else totalPagoPersonalCalculado

    // Efectivo Esperado en Caja:
    // Fondo Inicial + Ingresos Producción + Ingresos Mercaderías - Transferencias Recibidas - Extracciones
    val efectivoEsperado = (initialCash + ingresosProduccion + ingresosMercaderias - transferenciasTotalMonto - extraccionesVal).coerceAtLeast(0.0)
    val diferencia = efectivoRealVal - efectivoEsperado

    // Dinero final en caja = Efectivo contado − Pagos reales efectuados
    val dineroFinalEnCaja = (efectivoRealVal - (if (pagosConfirmados) totalPagosConfirmados else 0.0)).coerceAtLeast(0.0)

    val performCerrarCaja = {
        isCuadrado = true
        val jId = activeJornada?.id ?: 1L
        val mercCuadreItems = mercaderiasStates.map { m ->
            com.example.ui.viewmodel.MercaderiaCuadreItem(
                mercaderiaId = m.mercaderiaId,
                productId = m.productId,
                ventas = m.ventas,
                mermas = m.mermaTotal,
                price = m.price
            )
        }
        val agCuadreItems = agregadosStates.map { ag ->
            com.example.ui.viewmodel.AgregadoCuadreItem(
                materiaPrimaId = ag.materiaPrimaId,
                name = ag.name,
                racionesEnviadas = ag.racionesEnviadas,
                racionesVendidas = ag.racionesVendidas,
                racionesRegalia = ag.racionesRegalia,
                racionesSobrantes = ag.racionesSobrantes,
                precioVenta = ag.precioVenta,
                costoPorRacion = ag.costoPorRacion,
                rationQuantity = ag.rationQuantity,
                unit = ag.unit
            )
        }
        val finalNotes = notasCuadre
        val prodCuadreItems = produccionStates.map { p ->
            com.example.ui.viewmodel.ProduccionCuadreItem(
                productId = p.productId,
                productName = p.productName,
                totalProduced = p.effectiveTotalProduced,
                vendible = p.vendible,
                pendientes = p.pendientes,
                mermas = p.mermaTotal,
                unit = p.unit
            )
        }
        viewModel.registrarCuadreDueno(
            jornadaId = jId,
            realCash = efectivoRealVal,
            expectedCash = efectivoEsperado,
            diferencia = diferencia,
            ingresosProduccion = ingresosProduccion,
            ingresosMercaderias = ingresosMercaderias,
            mermas = totalMermasValor,
            transferencias = transferenciasTotalMonto,
            extracciones = extraccionesVal,
            notes = finalNotes,
            mercaderiaItems = mercCuadreItems,
            agregadoItems = agCuadreItems,
            produccionItems = prodCuadreItems
        )

        // Guardar snapshot histórico congelado en ARCHIVO
        try {
            val archiveToSave = CuadreCajaArchiveManager.JornadaCuadreCajaArchive(
                jornadaId = jId,
                openedAt = activeJornada?.openedAt ?: System.currentTimeMillis(),
                closedAt = System.currentTimeMillis(),
                closedBy = uiState.currentUser?.username ?: "DUEÑO",
                businessName = uiState.businessConfig?.nombreNegocio ?: "EL QADRE",
                initialCash = initialCash,
                ingresosProduccion = ingresosProduccion,
                ingresosMercaderias = ingresosMercaderias,
                ingresosAgregados = ingresosAgregados,
                totalIngresos = totalIngresosGenerales,
                mermasTotalValor = totalMermasValor,
                mermasTotalUnidades = totalMermasUnidades,
                transferenciasMonto = transferenciasTotalMonto,
                transferenciasCount = transferenciasForJornada.size,
                extracciones = extraccionesVal,
                extraccionesNotas = extraccionesList.filter { (it.montoStr.toDoubleOrNull() ?: 0.0) > 0 }.joinToString("; ") { "${it.montoStr} CUP: ${it.descripcion}" },
                efectivoEsperado = efectivoEsperado,
                efectivoReal = efectivoRealVal,
                diferencia = diferencia,
                costoProduccion = costoProduccionVal,
                costoMercaderias = costoMercaderiasVal,
                costoAgregados = costoAgregadosVal,
                costoTotal = costoTotalTotal,
                utilidadTeorica = utilidadTeorica,
                pagosConfirmados = pagosConfirmados,
                totalPagosPersonal = totalPagosEfectivos,
                totalPagoCocina = if (pagosConfirmados && savedPagos != null) savedPagos.totalCocina else totalPagoCocina,
                totalPagoCajero = if (pagosConfirmados && savedPagos != null) savedPagos.totalCajero else totalPagoCajero,
                totalPagoDependientes = if (pagosConfirmados && savedPagos != null) savedPagos.totalDependiente else totalPagoDependiente,
                dineroFinalEnCaja = dineroFinalEnCaja,
                notas = finalNotes,
                produccionItems = produccionStates.map { p ->
                    CuadreCajaArchiveManager.ArchivedProduccionItem(
                        productId = p.productId,
                        productName = p.productName,
                        tandasCount = p.tandasCount,
                        qtyPerTanda = p.qtyPerTanda,
                        totalProduced = p.effectiveTotalProduced,
                        unit = p.unit,
                        defectuoso = p.defectuoso,
                        consumo = p.consumo,
                        regalia = p.regalia,
                        pendientes = p.pendientes,
                        vendible = p.vendible,
                        price = p.price,
                        ingresoEstimado = p.ingresoEstimado,
                        pagoCocinaUnitario = p.pagoCocinaUnitario,
                        cantidadCocineros = p.cantidadCocineros
                    )
                },
                mercaderiaItems = mercaderiasStates.map { m ->
                    CuadreCajaArchiveManager.ArchivedMercaderiaItem(
                        mercaderiaId = m.mercaderiaId,
                        productId = m.productId,
                        productName = m.productName,
                        unit = m.unit,
                        existenciaInicial = m.existenciaInicial,
                        entradas = m.entradas,
                        existenciaFinal = m.existenciaFinal,
                        defectuoso = m.defectuoso,
                        consumo = m.consumo,
                        regalia = m.regalia,
                        ventas = m.ventas,
                        price = m.price,
                        ingresoEstimado = m.ingresoEstimado,
                        isConfitura = m.isConfitura
                    )
                },
                agregadoItems = agregadosStates.map { ag ->
                    CuadreCajaArchiveManager.ArchivedAgregadoItem(
                        materiaPrimaId = ag.materiaPrimaId,
                        name = ag.name,
                        unit = ag.unit,
                        racionesEnviadas = ag.racionesEnviadas,
                        racionesVendidas = ag.racionesVendidas,
                        racionesRegalia = ag.racionesRegalia,
                        racionesSobrantes = ag.racionesSobrantes,
                        precioVenta = ag.precioVenta,
                        costoPorRacion = ag.costoPorRacion,
                        ingresoEstimado = ag.ingresoEstimado
                    )
                },
                cocinaPagoRows = produccionStates.map { p ->
                    CuadreCajaArchiveManager.ArchivedCocinaPagoRow(
                        productName = p.productName,
                        vendible = p.vendible,
                        unit = p.unit,
                        pagoUnitario = p.pagoCocinaUnitario,
                        cantidadCocineros = if (p.cantidadCocineros > 0) p.cantidadCocineros else 1,
                        totalPago = p.vendible * p.pagoCocinaUnitario * (if (p.cantidadCocineros > 0) p.cantidadCocineros else 1)
                    )
                },
                cajeroPagoInfo = CuadreCajaArchiveManager.ArchivedCajeroPagoInfo(
                    pagoProduccion = pagoCajeroProduccion,
                    pagoMercaderias = pagoCajeroMercaderia,
                    totalPago = totalPagoCajero
                ),
                dependientesRows = (savedPagos?.dependientes ?: listOf(
                    DependientePagoDistribucion(
                        id = 1,
                        name = "Dependiente 1",
                        ventasProduccion = produccionStates.sumOf { it.vendible },
                        ventasBebidas = mercaderiasStates.filter { !it.isConfitura }.sumOf { it.ventas },
                        ventasTotales = produccionStates.sumOf { it.vendible } + mercaderiasStates.filter { !it.isConfitura }.sumOf { it.ventas },
                        montoPago = totalPagoDependiente
                    )
                )).map { d ->
                    CuadreCajaArchiveManager.ArchivedDependientePago(
                        id = d.id,
                        name = d.name,
                        ventasProduccion = d.ventasProduccion,
                        ventasBebidas = d.ventasBebidas,
                        ventasTotales = d.ventasTotales,
                        montoPago = d.montoPago
                    )
                }
            )
            CuadreCajaArchiveManager.saveArchive(context, archiveToSave)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Toast.makeText(context, "¡Cuadre de caja registrado y archivado con éxito!", Toast.LENGTH_SHORT).show()
    }

    if (showAvisoPagosDialog) {
        AlertDialog(
            onDismissRequest = { showAvisoPagosDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Payments,
                    contentDescription = null,
                    tint = ElQadreGoldDark,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Aviso: Pagos de Personal",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = ElQadreNavy,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Recuerde configurar y revisar los PAGOS DE PERSONAL para completar la liquidación de la jornada.",
                        fontSize = 14.sp,
                        color = Slate700
                    )
                    Text(
                        text = "Los pagos no se ejecutan automáticamente. Puede acceder a la sección PAGOS en cualquier momento para revisar y confirmar.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAvisoPagosDialog = false
                        selectedTab = CuadreTab.PAGOS
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_aviso_ir_a_pagos")
                ) {
                    Text("REVISAR PAGOS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAvisoPagosDialog = false
                        performCerrarCaja()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_aviso_cerrar_caja_ahora")
                ) {
                    Text("CERRAR CAJA AHORA", fontWeight = FontWeight.SemiBold, color = Slate700)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ElQadreBackground)
    ) {
        // TOP APP BAR / HEADER
        Surface(
            color = ElQadreNavy,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElQadreGold.copy(alpha = 0.2f)
                        ) {
                            Icon(
                                Icons.Outlined.PointOfSale,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier.padding(4.dp).size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CUADRO DE CAJA",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = Color.White
                            )
                            val jornadaLabel = if (activeJornada != null) "Jornada #${activeJornada.id} - ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(activeJornada.openedAt))}" else "Jornada del día"
                            Text(
                                text = jornadaLabel,
                                fontSize = 10.sp,
                                color = Slate300
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { showArchivoScreen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1E293B)
                            ),
                            border = BorderStroke(1.dp, ElQadreGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp).testTag("btn_cuadre_archivo")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Archive,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ARCHIVO",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(32.dp).testTag("btn_cerrar_cuadre_caja")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                if (isJornadaOpen) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // HORIZONTAL SCROLLABLE NAVIGATION ROW
                    // Allows the owner to smoothly slide left/right between all sections
                    val tabScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tabScrollState)
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CuadreNavCard(
                            label = "PRODUCCIÓN",
                            isSelected = selectedTab == CuadreTab.PRODUCCION,
                            onClick = { selectedTab = CuadreTab.PRODUCCION },
                            testTag = "tab_cuadre_produccion"
                        )
                        CuadreNavCard(
                            label = "MERCADERÍAS",
                            isSelected = selectedTab == CuadreTab.MERCADERIA,
                            onClick = { selectedTab = CuadreTab.MERCADERIA },
                            testTag = "tab_cuadre_mercaderia"
                        )
                        CuadreNavCard(
                            label = "TRANSFERENCIAS",
                            isSelected = selectedTab == CuadreTab.TRANSFERENCIA,
                            onClick = { selectedTab = CuadreTab.TRANSFERENCIA },
                            testTag = "tab_cuadre_transferencia"
                        )
                        CuadreNavCard(
                            label = "GENERALES",
                            isSelected = selectedTab == CuadreTab.GENERALES,
                            onClick = { selectedTab = CuadreTab.GENERALES },
                            testTag = "tab_cuadre_generales"
                        )
                        CuadreNavCard(
                            label = "PAGOS",
                            isSelected = selectedTab == CuadreTab.PAGOS,
                            onClick = { selectedTab = CuadreTab.PAGOS },
                            testTag = "tab_cuadre_pagos"
                        )
                    }
                }
            }
        }

        // CONTENT AREA
        if (!isJornadaOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.widthIn(max = 500.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = "JORNADA CERRADA",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }

                        Text(
                            text = "Acceso a Cuadre de Caja Bloqueado",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "El Cuadre de Caja solo puede utilizarse cuando exista una JORNADA ABIERTA.\n\nPrimero debe abrir la jornada en el sistema para poder realizar el arqueo y registrar el cuadre de caja.",
                            fontSize = 14.sp,
                            color = Slate700,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { showArchivoScreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_ver_archivo_jornada_cerrada")
                        ) {
                            Icon(Icons.Outlined.Archive, contentDescription = null, tint = ElQadreNavy, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CONSULTAR ARCHIVO HISTÓRICO",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                        }

                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_entendido_jornada_cerrada")
                        ) {
                            Text(
                                text = "ENTENDIDO / VOLVER",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            // CONTENT OF SELECTED TAB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when (selectedTab) {
                CuadreTab.PRODUCCION -> {
                    CuadreProduccionTab(
                        produccionStates = produccionStates,
                        agregadosStates = agregadosStates,
                        onConfirmar = { selectedTab = CuadreTab.MERCADERIA }
                    )
                }

                CuadreTab.MERCADERIA -> {
                    CuadreMercaderiasTab(
                        mercaderiasStates = mercaderiasStates,
                        onConfirmar = { selectedTab = CuadreTab.TRANSFERENCIA }
                    )
                }

                CuadreTab.TRANSFERENCIA -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            TransferenciasPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = Color.White,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { selectedTab = CuadreTab.GENERALES },
                                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .height(48.dp)
                                    .testTag("btn_confirmar_transferencias")
                            ) {
                                Text("CONFIRMAR TRANSFERENCIAS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGold)
                            }
                        }
                    }
                }

                CuadreTab.GENERALES -> {
                    CuadreGeneralesTab(
                        uiState = uiState,
                        activeJornada = activeJornada,
                        initialCash = initialCash,
                        ingresosProduccion = ingresosProduccion,
                        ingresosMercaderias = ingresosMercaderias,
                        produccionStates = produccionStates,
                        mercaderiasStates = mercaderiasStates,
                        agregadosStates = agregadosStates,
                        costoProduccionVal = costoProduccionVal,
                        costoAgregadosVal = costoAgregadosVal,
                        costoMercaderiasVal = costoMercaderiasVal,
                        costoTotalTotal = costoTotalTotal,
                        utilidadTeorica = utilidadTeorica,
                        totalMermasValor = totalMermasValor,
                        totalMermasUnidades = totalMermasUnidades,
                        transferenciasMonto = transferenciasTotalMonto,
                        transferenciasCount = transferenciasForJornada.size,
                        extraccionesList = extraccionesList,
                        efectivoEsperado = efectivoEsperado,
                        efectivoRealStr = efectivoRealStr,
                        onEfectivoRealChange = { efectivoRealStr = it },
                        diferencia = diferencia,
                        notasCuadre = notasCuadre,
                        onNotasCuadreChange = { notasCuadre = it },
                        isCuadrado = isCuadrado,
                        pagosConfirmados = pagosConfirmados,
                        totalPagosConfirmados = totalPagosConfirmados,
                        dineroFinalEnCaja = dineroFinalEnCaja,
                        onIrAPagos = { selectedTab = CuadreTab.PAGOS },
                        onCuadrarCaja = {
                            showAvisoPagosDialog = true
                        },
                        onDescargarPdf = {
                            val cocinaPdfRows = produccionStates.map { p ->
                                CuadreCajaPdfExporter.CocinaPagoPdfRow(
                                    productName = p.productName,
                                    vendible = p.vendible,
                                    unit = p.unit,
                                    pagoUnitario = p.pagoCocinaUnitario,
                                    cantidadCocineros = if (p.cantidadCocineros > 0) p.cantidadCocineros else 1,
                                    totalPago = p.vendible * p.pagoCocinaUnitario * (if (p.cantidadCocineros > 0) p.cantidadCocineros else 1)
                                )
                            }
                            val cajeroPdfInfo = CuadreCajaPdfExporter.CajeroPagoPdfInfo(
                                pagoProduccion = pagoCajeroProduccion,
                                pagoMercaderias = pagoCajeroMercaderia,
                                totalPago = totalPagoCajero
                            )
                            val dependientesPdfList = savedPagos?.dependientes ?: listOf(
                                DependientePagoDistribucion(
                                    id = 1,
                                    name = "Dependiente 1",
                                    ventasProduccion = produccionStates.sumOf { it.vendible },
                                    ventasBebidas = mercaderiasStates.filter { !it.isConfitura }.sumOf { it.ventas },
                                    ventasTotales = produccionStates.sumOf { it.vendible } + mercaderiasStates.filter { !it.isConfitura }.sumOf { it.ventas },
                                    montoPago = totalPagoDependiente
                                )
                            )

                            val reportData = CuadreCajaPdfExporter.CuadreCajaReportData(
                                businessName = uiState.businessConfig?.nombreNegocio ?: "EL QADRE",
                                duenoName = uiState.currentUser?.username ?: "DUEÑO",
                                jornadaId = activeJornada?.id ?: 1L,
                                openedAt = activeJornada?.openedAt ?: System.currentTimeMillis(),
                                closedAt = System.currentTimeMillis(),
                                initialCash = initialCash,
                                ingresosProduccion = ingresosProduccion,
                                ingresosMercaderias = ingresosMercaderias,
                                ingresosAgregados = ingresosAgregados,
                                totalIngresos = totalIngresosGenerales,
                                mermasTotalValor = totalMermasValor,
                                transferenciasMonto = transferenciasTotalMonto,
                                transferenciasCount = transferenciasForJornada.size,
                                extracciones = extraccionesVal,
                                extraccionesNotas = extraccionesList.filter { (it.montoStr.toDoubleOrNull() ?: 0.0) > 0 }.joinToString("; ") { "${it.montoStr} CUP: ${it.descripcion}" },
                                efectivoEsperado = efectivoEsperado,
                                efectivoReal = efectivoRealVal,
                                diferencia = diferencia,
                                costoProduccion = costoProduccionVal,
                                costoMercaderias = costoMercaderiasVal,
                                costoAgregados = costoAgregadosVal,
                                costoTotal = costoTotalTotal,
                                utilidadTeorica = utilidadTeorica,
                                pagosConfirmados = pagosConfirmados,
                                totalPagosPersonal = totalPagosEfectivos,
                                totalPagoCocina = if (pagosConfirmados && savedPagos != null) savedPagos.totalCocina else totalPagoCocina,
                                totalPagoCajero = if (pagosConfirmados && savedPagos != null) savedPagos.totalCajero else totalPagoCajero,
                                totalPagoDependientes = if (pagosConfirmados && savedPagos != null) savedPagos.totalDependiente else totalPagoDependiente,
                                cocinaPagoRows = cocinaPdfRows,
                                cajeroPagoInfo = cajeroPdfInfo,
                                dependientesRows = dependientesPdfList,
                                dineroFinalEnCaja = dineroFinalEnCaja,
                                notas = notasCuadre,
                                produccionRows = produccionStates.map { p ->
                                    CuadreCajaPdfExporter.ProduccionPdfRow(
                                        productName = p.productName,
                                        tandasCount = p.tandasCount,
                                        totalProduced = p.totalProduced,
                                        unit = p.unit,
                                        defectuoso = p.defectuoso,
                                        consumo = p.consumo,
                                        regalia = p.regalia,
                                        pendientes = p.pendientes,
                                        vendible = p.vendible,
                                        price = p.price,
                                        ingresoEstimado = p.ingresoEstimado
                                    )
                                },
                                mercaderiaRows = mercaderiasStates.map { m ->
                                    CuadreCajaPdfExporter.MercaderiaPdfRow(
                                        productName = m.productName,
                                        unit = m.unit,
                                        existenciaInicial = m.existenciaInicial,
                                        existenciaFinal = m.existenciaFinal,
                                        defectuoso = m.defectuoso,
                                        consumo = m.consumo,
                                        regalia = m.regalia,
                                        vendidas = m.ventas,
                                        price = m.price,
                                        ingresoEstimado = m.ingresoEstimado
                                    )
                                },
                                agregadoRows = agregadosStates.map { ag ->
                                    CuadreCajaPdfExporter.AgregadoPdfRow(
                                        name = ag.name,
                                        enviadas = ag.racionesEnviadas,
                                        vendidas = ag.racionesVendidas,
                                        regalia = ag.racionesRegalia,
                                        sobrantes = ag.racionesSobrantes,
                                        price = ag.precioVenta,
                                        ingresoEstimado = ag.ingresoEstimado
                                    )
                                }
                            )

                            val pdfFile = CuadreCajaPdfExporter.exportCuadreCajaReport(context, reportData)
                            if (pdfFile != null) {
                                lastGeneratedPdfFile = pdfFile
                                CuadreCajaPdfExporter.shareCuadreCajaReport(context, pdfFile)
                            }
                        }
                    )
                }

                CuadreTab.PAGOS -> {
                    CuadrePagosTab(
                        produccionStates = produccionStates,
                        mercaderiasStates = mercaderiasStates,
                        tarifasBebidas = uiState.tarifasPagoBebidas,
                        users = uiState.users,
                        activeJornadaId = activeJornadaId,
                        efectivoRealVal = efectivoRealVal,
                        utilidadTeorica = utilidadTeorica,
                        pagosConfirmados = pagosConfirmados,
                        onConfirmarPagos = { totalP, totalCoc, totalCaj, totalDep, cantDeps, depsList, distMode, cantCoc ->
                            val finalCash = (efectivoRealVal - totalP).coerceAtLeast(0.0)
                            val record = CuadrePagosJornada(
                                jornadaId = activeJornadaId,
                                isConfirmed = true,
                                confirmedAt = System.currentTimeMillis(),
                                confirmedBy = uiState.currentUser?.username ?: "dueno",
                                totalPagos = totalP,
                                totalCocina = totalCoc,
                                totalCajero = totalCaj,
                                totalDependiente = totalDep,
                                cantidadDependientes = cantDeps,
                                dependientes = depsList,
                                distributionMode = distMode,
                                efectivoContado = efectivoRealVal,
                                dineroFinalEnCaja = finalCash,
                                cantidadCocineros = cantCoc
                            )
                            CuadrePagosManager.savePagosJornada(context, record)
                            val detallesStr = depsList.joinToString("; ") { "${it.name}: $${"%.2f".format(it.montoPago)} CUP (${"%.1f".format(it.ventasTotales)} u)" }
                            viewModel.confirmarPagosPersonal(
                                jornadaId = activeJornadaId,
                                totalPagos = totalP,
                                totalCocina = totalCoc,
                                totalCajero = totalCaj,
                                totalDependiente = totalDep,
                                efectivoContado = efectivoRealVal,
                                dineroFinalEnCaja = finalCash,
                                detallesDistribucion = detallesStr
                            )
                            pagosConfirmados = true
                            totalPagosConfirmados = totalP
                            Toast.makeText(context, "¡Pagos de personal confirmados y registrados correctamente!", Toast.LENGTH_SHORT).show()
                        },
                        onModificarPagos = {
                            pagosConfirmados = false
                        }
                    )
                }
            }
        }
    }
}
}

// -------------------------------------------------------------
// TAB GENERALES (Resumen General, Caja, Utilidad y Cuadre)
// -------------------------------------------------------------
@Composable
fun CuadreGeneralesTab(
    uiState: MainUiState,
    activeJornada: Jornada?,
    initialCash: Double,
    ingresosProduccion: Double,
    ingresosMercaderias: Double,
    produccionStates: List<ProduccionItemState> = emptyList(),
    mercaderiasStates: List<MercaderiaItemState> = emptyList(),
    agregadosStates: List<AgregadoCuadreItemState> = emptyList(),
    costoProduccionVal: Double = 0.0,
    costoAgregadosVal: Double = 0.0,
    costoMercaderiasVal: Double = 0.0,
    costoTotalTotal: Double = 0.0,
    utilidadTeorica: Double = 0.0,
    totalMermasValor: Double,
    totalMermasUnidades: Double,
    transferenciasMonto: Double,
    transferenciasCount: Int,
    extraccionesList: androidx.compose.runtime.snapshots.SnapshotStateList<ExtraccionItem>,
    efectivoEsperado: Double,
    efectivoRealStr: String,
    onEfectivoRealChange: (String) -> Unit,
    diferencia: Double,
    notasCuadre: String,
    onNotasCuadreChange: (String) -> Unit,
    isCuadrado: Boolean,
    pagosConfirmados: Boolean = false,
    totalPagosConfirmados: Double = 0.0,
    dineroFinalEnCaja: Double = 0.0,
    onIrAPagos: () -> Unit = {},
    onCuadrarCaja: () -> Unit,
    onDescargarPdf: () -> Unit
) {
    val scrollState = rememberScrollState()

    val totalIngresosGenerales = ingresosProduccion + ingresosMercaderias
    val extraccionesVal = extraccionesList.sumOf { it.montoStr.toDoubleOrNull() ?: 0.0 }

    val ingresosEsperadosPotencial = produccionStates.sumOf { it.totalProduced * it.price } +
            mercaderiasStates.sumOf { it.existenciaDisponible * it.price } +
            agregadosStates.sumOf { it.racionesEnviadas * it.precioVenta }

    val ingresosEsperadosDisplay = if (ingresosEsperadosPotencial > 0.0) ingresosEsperadosPotencial else totalIngresosGenerales

    val difEfectivoColor = when {
        diferencia > 0.01 -> Color(0xFF047857)
        diferencia < -0.01 -> Color(0xFFDC2626)
        else -> ElQadreNavy
    }
    val difEfectivoBadge = when {
        diferencia > 0.01 -> "SOBRANTE (+ $${"%.2f".format(diferencia)} CUP)"
        diferencia < -0.01 -> "FALTANTE (- $${"%.2f".format(-diferencia)} CUP)"
        else -> "CUADRE EXACTO ($0.00 CUP)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // 1. EXTRACCIONES
        // ==========================================

        // CARD 1: EXTRACCIONES
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.MoneyOff,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "EXTRACCIONES",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                    }

                    if (extraccionesVal > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "TOTAL: -$${"%.2f".format(extraccionesVal)} CUP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Registre todas las extracciones directas de efectivo de la caja.",
                    fontSize = 11.sp,
                    color = Slate500
                )

                HorizontalDivider(color = Slate100)

                // List of extractions
                extraccionesList.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = item.montoStr,
                            onValueChange = { newVal ->
                                val clean = newVal.filter { c -> c.isDigit() || c == '.' }
                                extraccionesList[index] = item.copy(montoStr = clean)
                            },
                            label = { Text("Importe CUP", fontSize = 11.sp) },
                            placeholder = { Text("0.00", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_extraccion_monto_$index")
                        )

                        OutlinedTextField(
                            value = item.descripcion,
                            onValueChange = { newVal ->
                                extraccionesList[index] = item.copy(descripcion = newVal)
                            },
                            label = { Text("Motivo (~2 palabras)", fontSize = 11.sp) },
                            placeholder = { Text("Ej: Compra hielo", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .testTag("input_extraccion_desc_$index")
                        )

                        if (extraccionesList.size > 1) {
                            IconButton(
                                onClick = { extraccionesList.removeAt(index) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("btn_remove_extraccion_$index")
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Eliminar",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        extraccionesList.add(ExtraccionItem())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate100, contentColor = ElQadreNavy),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("btn_add_extraccion")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ AGREGAR EXTRACCIÓN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // ==========================================
        // 2. EFECTIVO
        // ==========================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Payments,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "EFECTIVO",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // EFECTIVO ESPERADO
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "EFECTIVO ESPERADO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Slate600
                            )
                            Text(
                                text = "$${"%.2f".format(efectivoEsperado)} CUP",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Fondo ($${"%.0f".format(initialCash)}) + Ventas ($${"%.0f".format(totalIngresosGenerales)}) - Transf ($${"%.0f".format(transferenciasMonto)}) - Extrac ($${"%.0f".format(extraccionesVal)})",
                                fontSize = 9.sp,
                                color = Slate500,
                                lineHeight = 11.sp
                            )
                        }
                    }

                    // EFECTIVO CONTADO
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEFF6FF),
                        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "EFECTIVO CONTADO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1E40AF)
                            )
                            OutlinedTextField(
                                value = efectivoRealStr,
                                onValueChange = { onEfectivoRealChange(it.filter { c -> c.isDigit() || c == '.' }) },
                                placeholder = { Text("0.00", fontSize = 16.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF1E40AF),
                                    unfocusedBorderColor = Color(0xFF60A5FA)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_efectivo_real_cuadre")
                            )
                        }
                    }
                }

                // DIFERENCIA EFECTIVO
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = difEfectivoColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, difEfectivoColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIFERENCIA:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = difEfectivoColor
                        )
                        Text(
                            text = difEfectivoBadge,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = difEfectivoColor
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. TRANSFERENCIAS
        // ==========================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.SwapHoriz,
                                contentDescription = null,
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "TRANSFERENCIAS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TRANSFERENCIAS ESPERADAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                        Text(
                            text = "$${"%.2f".format(transferenciasMonto)} CUP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TRANSFERENCIAS REGISTRADAS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7)
                        )
                        Text(
                            text = "$${"%.2f".format(transferenciasMonto)} CUP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0284C7)
                        )
                        Text(
                            text = "$transferenciasCount operaciones",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIFERENCIA TRANSFERENCIAS:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate600
                        )
                        Text(
                            text = "$0.00 CUP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 4. INGRESOS
        // ==========================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF15803D).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "INGRESOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INGRESOS ESPERADOS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500
                        )
                        Text(
                            text = "$${"%.2f".format(ingresosEsperadosDisplay)} CUP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Text(
                            text = "Potencial ventas teórico",
                            fontSize = 9.sp,
                            color = Slate400
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INGRESOS REALES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "$${"%.2f".format(totalIngresosGenerales)} CUP",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "Ventas reales ejecutadas",
                            fontSize = 9.sp,
                            color = Color(0xFF15803D)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFDCFCE7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DIFERENCIA INGRESOS:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                        val difIng = totalIngresosGenerales - ingresosEsperadosDisplay
                        Text(
                            text = "${if (difIng >= 0) "+" else ""}$${"%.2f".format(difIng)} CUP",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF166534)
                        )
                    }
                }

                Text(
                    text = "Los ingresos reales corresponden exclusivamente a ventas reales.",
                    fontSize = 10.sp,
                    color = Slate500
                )
            }
        }

        // ==========================================
        // 5. COSTOS
        // ==========================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFB91C1C).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.PieChart,
                                contentDescription = null,
                                tint = Color(0xFFB91C1C),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "COSTOS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "Costos de Producción",
                    value = "$${"%.2f".format(costoProduccionVal + costoAgregadosVal)} CUP",
                    color = Slate700
                )

                CuadreMetricRow(
                    label = "Costos de Mercaderías",
                    value = "$${"%.2f".format(costoMercaderiasVal)} CUP",
                    color = Slate700
                )

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "COSTOS TOTALES DE JORNADA",
                    value = "$${"%.2f".format(costoTotalTotal)} CUP",
                    color = Color(0xFFB91C1C),
                    isBold = true
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Costos provenientes de Fichas de Costo. No se duplican gastos generales ni pagos de personal.",
                        fontSize = 10.sp,
                        color = Slate600,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // BLOQUE EFECTIVO FÍSICO Y DINERO FINAL EN CAJA
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, if (pagosConfirmados) Color(0xFF16A34A).copy(alpha = 0.5f) else Color(0xFF0284C7).copy(alpha = 0.4f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (pagosConfirmados) Color(0xFF16A34A) else Color(0xFF0284C7)).copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (pagosConfirmados) Color(0xFF16A34A) else Color(0xFF0284C7),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "ESTADO DE EFECTIVO Y CAJA FINAL",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Dinero final en caja = Efectivo contado − Pagos reales",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    if (pagosConfirmados) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "PAGOS DEDUCIDOS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "PAGOS PENDIENTES",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                val efContadoVal = efectivoRealStr.toDoubleOrNull() ?: 0.0
                CuadreMetricRow(
                    label = "Efectivo Contado en Caja",
                    value = "$${"%.2f".format(efContadoVal)} CUP",
                    color = ElQadreNavy
                )

                CuadreMetricRow(
                    label = "Pagos Reales a Personal (Salida física)",
                    value = if (pagosConfirmados) "-$${"%.2f".format(totalPagosConfirmados)} CUP" else "Pendiente de confirmar en PAGOS",
                    color = if (pagosConfirmados) Color(0xFFDC2626) else Color(0xFFD97706),
                    isBold = pagosConfirmados
                )

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "DINERO FINAL EN CAJA",
                    value = "$${"%.2f".format(dineroFinalEnCaja)} CUP",
                    color = Color(0xFF15803D),
                    isBold = true
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nota (Sin doble contabilización): La Utilidad Teórica ($${"%.2f".format(utilidadTeorica)} CUP) se mantiene separada. El pago real confirmado descuenta físicamente el dinero de caja.",
                        fontSize = 11.sp,
                        color = Slate600,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // ==========================================
        // 6. MERMAS
        // ==========================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.12f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "MERMAS",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = ElQadreNavy
                    )
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "Ingresos/Valor asociado a Mermas (${"%.1f".format(totalMermasUnidades)} unid.)",
                    value = "$${"%.2f".format(totalMermasValor)} CUP",
                    color = Color(0xFFB91C1C),
                    isBold = true
                )

                Text(
                    text = "Mermas registradas en la jornada. No se convierten en ventas ni se suman a Ingresos Reales.",
                    fontSize = 10.sp,
                    color = Slate500
                )
            }
        }

        // ==========================================
        // 6.B UNIDADES PENDIENTES DE TANDAS
        // ==========================================
        val totalPendientesUnidades = produccionStates.sumOf { it.pendientes }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F766E).copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.HourglassTop,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "UNIDADES PENDIENTES DE TANDAS",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = ElQadreNavy
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFCCFBF1)
                    ) {
                        Text(
                            text = "${"%.1f".format(totalPendientesUnidades)} unid.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0F766E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(color = Slate100)

                val itemsConPendientes = produccionStates.filter { it.pendientes > 0 }
                if (itemsConPendientes.isNotEmpty()) {
                    itemsConPendientes.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.productName,
                                fontSize = 13.sp,
                                color = Slate700,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${"%.1f".format(item.pendientes)} ${item.unit}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Sin unidades pendientes registradas en las Tandas de esta jornada.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }

                Text(
                    text = "Información de referencia tomada automáticamente del cierre de Tandas. Las unidades pendientes se trasladan automáticamente como Tanda 00 a la siguiente jornada.",
                    fontSize = 10.sp,
                    color = Slate500,
                    lineHeight = 14.sp
                )
            }
        }

        // ==========================================
        // 7. UTILIDADES (HERO CARD DESTACADA)
        // ==========================================
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = ElQadreNavy,
            border = BorderStroke(2.dp, ElQadreGold),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElQadreGold.copy(alpha = 0.25f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MonetizationOn,
                                contentDescription = null,
                                tint = ElQadreGold,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(24.dp)
                            )
                        }
                        Text(
                            text = "UTILIDADES DE LA JORNADA",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = ElQadreGold
                    ) {
                        Text(
                            text = "RESULTADO FINAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                Text(
                    text = "INGRESOS ($${"%.2f".format(totalIngresosGenerales)}) − COSTOS ($${"%.2f".format(costoTotalTotal)}) = UTILIDAD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate300
                )

                val utilColor = if (utilidadTeorica >= 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                Text(
                    text = "$${"%.2f".format(utilidadTeorica)} CUP",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = utilColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Los pagos de personal no se restan nuevamente si ya están contemplados en los costos teóricos de las Fichas de Costo.",
                        fontSize = 10.sp,
                        color = Slate300,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // BLOQUE 4: NOTAS Y OBSERVACIONES DEL CUADRE
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate100,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Notes,
                                contentDescription = null,
                                tint = Slate600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "NOTAS Y OBSERVACIONES",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                }

                OutlinedTextField(
                    value = notasCuadre,
                    onValueChange = onNotasCuadreChange,
                    label = { Text("Notas / Observaciones del Cuadre", fontSize = 12.sp) },
                    placeholder = { Text("Detalles de la jornada o incidentes...", fontSize = 12.sp) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .testTag("input_notas_cuadre"),
                    maxLines = 3
                )
            }
        }

        // ==========================================
        // 3. BOTONES DE ACCIÓN (CUADRAR Y PDF)
        // ==========================================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCuadrarCaja,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_cuadrar_caja_action")
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = ElQadreGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CERRAR CAJA",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            if (isCuadrado && !pagosConfirmados) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                            Text(
                                text = "Cuadre registrado: Establezca los pagos de personal.",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        Text(
                            text = "Acceda a la pestaña PAGOS para revisar, distribuir entre dependientes y confirmar los pagos correspondientes de la jornada.",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F)
                        )
                        Button(
                            onClick = onIrAPagos,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_ir_a_pagos_alerta")
                        ) {
                            Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IR A PAGOS DE PERSONAL", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                        }
                    }
                }
            } else if (isCuadrado && pagosConfirmados) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFDCFCE7),
                    border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(22.dp))
                            Column {
                                Text(
                                    text = "Pagos de personal confirmados",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = "Total efectuado: $${"%.2f".format(totalPagosConfirmados)} CUP",
                                    fontSize = 11.sp,
                                    color = Color(0xFF14532D)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onIrAPagos,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF16A34A))
                        ) {
                            Text("Ver Detalle", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF166534))
                        }
                    }
                }
            }

            if (isCuadrado) {
                Button(
                    onClick = onDescargarPdf,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_descargar_pdf_cuadre")
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DESCARGAR PDF DE CUADRE",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
    }
}

@Composable
private fun CuadreMetricRow(
    label: String,
    value: String,
    color: Color = Slate700,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Slate700,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = if (isBold) FontWeight.Black else FontWeight.SemiBold,
            color = color
        )
    }
}

// -------------------------------------------------------------
// TAB 2: PRODUCCIÓN (Tandas e Ingresos con Mermas y Agregados)
// -------------------------------------------------------------
@Composable
fun CuadreProduccionTab(
    produccionStates: List<ProduccionItemState>,
    agregadosStates: List<AgregadoCuadreItemState> = emptyList(),
    onConfirmar: () -> Unit = {}
) {
    if (produccionStates.isEmpty() && agregadosStates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Outlined.SoupKitchen,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No hay tandas de producción ni agregados en esta jornada.",
                    textAlign = TextAlign.Center,
                    color = Slate600,
                    fontSize = 14.sp
                )
                Button(
                    onClick = onConfirmar,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .testTag("btn_confirmar_produccion_empty")
                ) {
                    Text("CONFIRMAR Y CONTINUAR", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEFF6FF),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Ventas de producción: Sólo unidades vendidas (Producido − Mermas − Pendientes). Las mermas se registran en el formulario individual de cada producto y los pendientes provienen automáticamente del cierre de Tandas.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF1E3A8A),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            val totalIngresosProduccion = produccionStates.sumOf { it.ingresoEstimado } + agregadosStates.sumOf { it.ingresoEstimado }
            val totalTandas = produccionStates.sumOf { it.tandasCount }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "INGRESOS TOTALES DE PRODUCCIÓN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate400
                        )
                        Text(
                            text = "$totalTandas tandas registradas",
                            fontSize = 12.sp,
                            color = Slate300
                        )
                    }
                    Text(
                        text = "$${"%.2f".format(totalIngresosProduccion)} CUP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
        }

        if (produccionStates.isNotEmpty()) {
            item {
                Text(
                    text = "PRODUCTOS PRINCIPALES (TANDAS)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Slate600,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(produccionStates) { item ->
                ProduccionCuadreCard(item = item)
            }
        }

        if (agregadosStates.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "AGREGADOS VINCULADOS A PRODUCTOS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            items(agregadosStates) { item ->
                AgregadoCuadreCard(item = item)
            }
        }

        item {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_confirmar_produccion")
            ) {
                Text("CONFIRMAR PRODUCCIÓN", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun AgregadoCuadreCard(
    item: AgregadoCuadreItemState
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.testTag("card_agregado_${item.materiaPrimaId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                        Text(
                            text = "AGREGADO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ElQadreNavy
                    )
                }
                Text(
                    text = "$${"%.2f".format(item.precioVenta)} CUP/rac.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate600
                )
            }

            HorizontalDivider(color = Slate100)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "CONTROL DE RACIONES (1 ración = ${item.rationQuantity} ${item.rationUnit}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.racionesEnviadasStr,
                        onValueChange = { clean ->
                            item.racionesEnviadasStr = clean.filter { c -> c.isDigit() || c == '.' }
                        },
                        label = { Text("Enviadas", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = item.racionesVendidasStr,
                        onValueChange = { clean ->
                            item.racionesVendidasStr = clean.filter { c -> c.isDigit() || c == '.' }
                        },
                        label = { Text("Vendidas", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = item.racionesRegaliaStr,
                        onValueChange = { clean ->
                            item.racionesRegaliaStr = clean.filter { c -> c.isDigit() || c == '.' }
                        },
                        label = { Text("Regalía", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // RESULTS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sobrantes: ${"%.1f".format(item.racionesSobrantes)} raciones (${"%.2f".format(item.sobranteFisico)} ${item.unit})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                    Text(
                        text = "Regresan al inventario al cerrar jornada",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }

                Text(
                    text = "Venta: $${"%.2f".format(item.ingresoEstimado)} CUP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF15803D)
                )
            }
        }
    }
}

@Composable
fun ProduccionCuadreCard(
    item: ProduccionItemState
) {
    var showEditModal by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showEditModal = true }
            .testTag("card_produccion_${item.productId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nombre del producto en tamaño grande y claro
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ElQadreNavy,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Ver detalle",
                    tint = Slate400,
                    modifier = Modifier.size(24.dp)
                )
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // 3 Datos Clave: CANTIDAD, PRECIO ACTUAL, INGRESOS GENERADOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CANTIDAD
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "CANTIDAD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${"%.1f".format(item.effectiveTotalProduced)} ${item.unit}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                // PRECIO ACTUAL
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "PRECIO ACTUAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$${"%.2f".format(item.effectivePrice)} CUP",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = ElQadreNavy
                    )
                }

                // INGRESOS GENERADOS
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "INGRESOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$${"%.2f".format(item.ingresoEstimado)} CUP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF15803D)
                    )
                }
            }
        }
    }

    if (showEditModal) {
        ProduccionEditModal(
            item = item,
            onDismiss = { showEditModal = false }
        )
    }
}

@Composable
fun ProduccionEditModal(
    item: ProduccionItemState,
    onDismiss: () -> Unit
) {
    var defectuosoInput by remember { mutableStateOf(item.defectuosoStr) }
    var consumoInput by remember { mutableStateOf(item.consumoStr) }
    var regaliaInput by remember { mutableStateOf(item.regaliaStr) }
    var pendientesInput by remember { mutableStateOf(item.pendientesStr) }
    var priceInput by remember { mutableStateOf(item.customPriceStr) }

    val defectuosoVal = defectuosoInput.toDoubleOrNull() ?: 0.0
    val consumoVal = consumoInput.toDoubleOrNull() ?: 0.0
    val regaliaVal = regaliaInput.toDoubleOrNull() ?: 0.0
    val pendientesVal = pendientesInput.toDoubleOrNull() ?: 0.0
    val priceVal = priceInput.toDoubleOrNull() ?: item.price

    val mermaTotalCalc = defectuosoVal + consumoVal + regaliaVal
    val vendibleCalc = (item.effectiveTotalProduced - mermaTotalCalc - pendientesVal).coerceAtLeast(0.0)
    val ingresoCalc = vendibleCalc * priceVal

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("modal_edit_produccion_${item.productId}")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Detalle y Registro de Producción",
                                fontSize = 13.sp,
                                color = Slate500
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                        }
                    }
                }

                item {
                    HorizontalDivider(color = Slate200)
                }

                // Resumen de Producción
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Producido:", fontSize = 14.sp, color = Slate600)
                                Text(
                                    "${"%.1f".format(item.effectiveTotalProduced)} ${item.unit}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tandas registradas:", fontSize = 14.sp, color = Slate600)
                                Text(
                                    "${item.effectiveTandasCount} tandas (${"%.1f".format(item.effectiveQtyPerTanda)} ${item.unit}/tanda)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }
                            if (item.hasTanda00) {
                                Text(
                                    text = "• Incluye Tanda 00 (pendientes recuperadas de la jornada anterior)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                            }
                        }
                    }
                }

                // Información de Unidades Pendientes (Tomadas de Tandas)
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFCCFBF1),
                        border = BorderStroke(1.dp, Color(0xFF0F766E)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "UNIDADES PENDIENTES (Cierre de Tanda):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                            Text(
                                text = "${"%.1f".format(item.pendientes)} ${item.unit}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F766E)
                            )
                            Text(
                                text = "Información tomada automáticamente del cierre de Tandas. Las unidades pendientes pasarán como Tanda 00 a la siguiente jornada.",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }
                    }
                }

                // Campo: Mermas / Desechos
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "MERMA O DEFECTUOSO (${item.unit}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = defectuosoInput,
                            onValueChange = { clean ->
                                defectuosoInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("0.0", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_merma_produccion_modal")
                        )
                    }
                }

                // Campo: Consumo y Regalías
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CONSUMO:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = consumoInput,
                                onValueChange = { clean ->
                                    consumoInput = clean.filter { c -> c.isDigit() || c == '.' }
                                },
                                placeholder = { Text("0.0", fontSize = 16.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_consumo_produccion_modal")
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "REGALÍA:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            OutlinedTextField(
                                value = regaliaInput,
                                onValueChange = { clean ->
                                    regaliaInput = clean.filter { c -> c.isDigit() || c == '.' }
                                },
                                placeholder = { Text("0.0", fontSize = 16.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("input_regalia_produccion_modal")
                            )
                        }
                    }
                }

                // Campo: Precio de venta unitario
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PRECIO DE VENTA ($ CUP):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = priceInput,
                            onValueChange = { clean ->
                                priceInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("%.2f".format(item.price), fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("input_precio_produccion_modal")
                        )
                    }
                }

                // Resumen de Cálculo
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CÁLCULO DE VENTA:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF166534),
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cantidad vendida:", fontSize = 14.sp, color = Slate700)
                                Text(
                                    "${"%.1f".format(vendibleCalc)} ${item.unit}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ingreso generado:", fontSize = 14.sp, color = Slate700)
                                Text(
                                    "$${"%.2f".format(ingresoCalc)} CUP",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }

                // Botón grande CONFIRMAR
                item {
                    Button(
                        onClick = {
                            item.defectuosoStr = defectuosoInput.ifBlank { "0" }
                            item.consumoStr = consumoInput.ifBlank { "0" }
                            item.regaliaStr = regaliaInput.ifBlank { "0" }
                            item.pendientesStr = pendientesInput.ifBlank { "0" }
                            if (priceInput.isNotBlank()) {
                                item.customPriceStr = priceInput
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_confirmar_edit_produccion_modal")
                    ) {
                        Text(
                            text = "CONFIRMAR Y GUARDAR",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: MERCADERÍAS (Local de Ventas)
// -------------------------------------------------------------
@Composable
fun CuadreMercaderiasTab(
    mercaderiasStates: List<MercaderiaItemState>,
    onConfirmar: () -> Unit = {}
) {
    if (mercaderiasStates.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No hay productos de Mercaderías configurados en el sistema.",
                    textAlign = TextAlign.Center,
                    color = Slate600,
                    fontSize = 14.sp
                )
                Button(
                    onClick = onConfirmar,
                    colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(44.dp)
                        .testTag("btn_confirmar_mercaderias_empty")
                ) {
                    Text("CONFIRMAR Y CONTINUAR", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGold)
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFEF3C7).copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Toque cualquier tarjeta de producto para ingresar las existencias de Inicio, Final y Merma/Defectuoso.",
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }

        item {
            val totalIngresosMercaderias = mercaderiasStates.sumOf { it.ingresoEstimado }
            val completedCount = mercaderiasStates.count { it.isCompleted }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ElQadreNavy),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "INGRESOS TOTALES DE MERCADERÍAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate400
                        )
                        Text(
                            text = "$completedCount de ${mercaderiasStates.size} productos completados",
                            fontSize = 12.sp,
                            color = Slate300
                        )
                    }
                    Text(
                        text = "$${"%.2f".format(totalIngresosMercaderias)} CUP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
        }

        items(mercaderiasStates) { item ->
            MercaderiaCuadreCard(item = item)
        }

        item {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_confirmar_mercaderias")
            ) {
                Text("CONFIRMAR MERCADERÍAS", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = ElQadreGold)
            }
        }

        item {
            Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun MercaderiaCuadreCard(
    item: MercaderiaItemState
) {
    var showEditModal by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { showEditModal = true }
            .testTag("card_mercaderia_${item.mercaderiaId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Nombre de la mercadería
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ElQadreNavy,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Editar mercadería",
                    tint = Slate400,
                    modifier = Modifier.size(24.dp)
                )
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // 2 Datos Clave: CANTIDAD VENDIDA e INGRESOS GENERADOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CANTIDAD VENDIDA
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "CANTIDAD VENDIDA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${"%.1f".format(item.ventas)} ${item.unit}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = if (item.ventas > 0.0 || item.isCompleted) Color(0xFF15803D) else ElQadreNavy
                    )
                }

                // INGRESOS GENERADOS
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "INGRESOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "$${"%.2f".format(item.ingresoEstimado)} CUP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF15803D)
                    )
                }
            }
        }
    }

    if (showEditModal) {
        MercaderiaEditModal(
            item = item,
            onDismiss = { showEditModal = false }
        )
    }
}

@Composable
fun MercaderiaEditModal(
    item: MercaderiaItemState,
    onDismiss: () -> Unit
) {
    var inicioInput by remember { mutableStateOf(item.existenciaInicialStr) }
    var entradasInput by remember { mutableStateOf(item.entradasStr) }
    var finalInput by remember { mutableStateOf(item.existenciaFinalStr) }
    var mermaInput by remember { mutableStateOf(item.defectuosoStr) }

    val inicioVal = inicioInput.toDoubleOrNull() ?: 0.0
    val finalVal = finalInput.toDoubleOrNull() ?: 0.0
    val entradasVal = entradasInput.toDoubleOrNull() ?: 0.0
    val mermaVal = mermaInput.toDoubleOrNull() ?: 0.0

    // Unidades vendidas = Inicio + Entradas - Final - Merma
    val vendidasCalc = (inicioVal + entradasVal - finalVal - mermaVal).coerceAtLeast(0.0)
    val ingresosCalc = vendidasCalc * item.price

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("modal_edit_mercaderia_${item.mercaderiaId}")
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.productName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Precio: $${"%.2f".format(item.price)} CUP / ${item.unit}",
                                fontSize = 13.sp,
                                color = Slate500
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate500)
                        }
                    }
                }

                item {
                    HorizontalDivider(color = Slate200)
                }

                // 1. Existencia de INICIO
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. EXISTENCIA DE INICIO (${item.unit}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = inicioInput,
                            onValueChange = { clean ->
                                inicioInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("0.0", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_inicio_mercaderia_modal")
                        )
                    }
                }

                // 2. Entradas de Jornada
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "2. ENTRADAS / COMPRAS (${item.unit}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = entradasInput,
                            onValueChange = { clean ->
                                entradasInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("0.0", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_entradas_mercaderia_modal")
                        )
                    }
                }

                // 3. Mermas
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "3. MERMA O DEFECTUOSO (${item.unit}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = mermaInput,
                            onValueChange = { clean ->
                                mermaInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("0.0", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_merma_mercaderia_modal")
                        )
                    }
                }

                // 4. Existencia FINAL
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "4. EXISTENCIA FINAL (${item.unit}):",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = ElQadreNavy
                        )
                        OutlinedTextField(
                            value = finalInput,
                            onValueChange = { clean ->
                                finalInput = clean.filter { c -> c.isDigit() || c == '.' }
                            },
                            placeholder = { Text("0.0", fontSize = 16.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8)),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_final_mercaderia_modal")
                        )
                    }
                }

                // Cálculo automático
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CÁLCULO AUTOMÁTICO:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF166534),
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Unidades vendidas:", fontSize = 14.sp, color = Slate700)
                                Text(
                                    "${"%.1f".format(vendidasCalc)} ${item.unit}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF15803D)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ingresos generados:", fontSize = 14.sp, color = Slate700)
                                Text(
                                    "$${"%.2f".format(ingresosCalc)} CUP",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }

                // Botón grande CONFIRMAR
                item {
                    Button(
                        onClick = {
                            item.existenciaInicialStr = inicioInput.ifBlank { "0" }
                            item.entradasStr = entradasInput.ifBlank { "0" }
                            item.existenciaFinalStr = finalInput.ifBlank { "0" }
                            item.defectuosoStr = mermaInput.ifBlank { "0" }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_confirmar_edit_mercaderia_modal")
                    ) {
                        Text(
                            text = "CONFIRMAR Y GUARDAR",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}



// -------------------------------------------------------------
// TAB 5: PAGOS (Cálculos de pagos a personal)
// -------------------------------------------------------------
@Composable
fun CuadrePagosTab(
    produccionStates: List<ProduccionItemState>,
    mercaderiasStates: List<MercaderiaItemState>,
    tarifasBebidas: com.example.util.TarifasPagoBebidas = com.example.util.TarifasPagoBebidas(),
    users: List<com.example.data.local.model.User> = emptyList(),
    activeJornadaId: Long = 1L,
    efectivoRealVal: Double = 0.0,
    utilidadTeorica: Double = 0.0,
    pagosConfirmados: Boolean = false,
    onConfirmarPagos: (
        totalPagos: Double,
        totalCocina: Double,
        totalCajero: Double,
        totalDependiente: Double,
        cantidadDependientes: Int,
        dependientes: List<DependientePagoDistribucion>,
        distributionMode: String,
        cantidadCocineros: Int
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onModificarPagos: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Cargar distribución previa guardada si existe
    val savedPagos = remember(activeJornadaId) {
        CuadrePagosManager.getPagosJornada(context, activeJornadaId)
    }

    // Estado de trabajadores de cocina a pagar hoy (independiente de la Ficha de Costo)
    var cantidadCocineros by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.cantidadCocineros ?: 1)
    }

    LaunchedEffect(cantidadCocineros) {
        produccionStates.forEach { it.cantidadCocinerosStr = cantidadCocineros.toString() }
    }

    // 1. CÁLCULO DE PAGOS COCINA (Producción)
    // Pago unitario de la Ficha × Cantidad de trabajadores indicada en el Pago de ese día
    val totalPagoCocina = produccionStates.sumOf { item ->
        item.vendible * item.pagoCocinaUnitario * item.cantidadCocineros
    }

    // 2. CÁLCULO DE PAGOS DEPENDIENTE Y CAJERO
    val pagoDependienteProduccion = produccionStates.sumOf { item ->
        item.vendible * item.pagoDependienteUnitario
    }
    val pagoCajeroProduccion = produccionStates.sumOf { item ->
        item.vendible * item.pagoCajeroUnitario
    }

    val pagoDependienteMercaderia = mercaderiasStates.sumOf { item ->
        if (item.isConfitura) {
            0.0
        } else {
            val depTarifa = if (item.pagoDependienteUnitario > 0.0) item.pagoDependienteUnitario else tarifasBebidas.calcularPagoDependiente(item.price)
            item.ventas * depTarifa
        }
    }

    val pagoCajeroMercaderia = mercaderiasStates.sumOf { item ->
        if (item.isConfitura) {
            0.0
        } else {
            val cajTarifa = if (item.pagoCajeroUnitario > 0.0) item.pagoCajeroUnitario else tarifasBebidas.calcularPagoCajero(item.price)
            item.ventas * cajTarifa
        }
    }

    val totalPagoDependiente = pagoDependienteProduccion + pagoDependienteMercaderia
    val totalPagoCajero = pagoCajeroProduccion + pagoCajeroMercaderia
    val totalPagoPersonal = totalPagoCocina + totalPagoDependiente + totalPagoCajero

    // Ventas totales sujetas a dependientes
    val totalVentasProduccion = produccionStates.sumOf { it.vendible }
    val totalVentasBebidas = mercaderiasStates.filter { !it.isConfitura }.sumOf { it.ventas }
    val totalVentasSujetas = totalVentasProduccion + totalVentasBebidas

    // Dinero final en caja = Efectivo contado − Pagos reales
    val dineroFinalEnCaja = (efectivoRealVal - (if (pagosConfirmados) totalPagoPersonal else 0.0)).coerceAtLeast(0.0)

    // Usuarios con rol de dependiente o salón
    val dependienteUsers = remember(users) {
        users.filter { it.role == UserRole.DEPENDIENTE || it.role == UserRole.SALON }.map { it.username }
    }

    // Estado de dependientes: 1, 2 o 3
    var cantidadDependientes by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.cantidadDependientes ?: 1)
    }

    // Modo de distribución para 2 o 3 dependientes: "CATEGORIAS" (separando Producción y Bebidas) o "GLOBAL"
    var distributionMode by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.distributionMode ?: "CATEGORIAS")
    }

    // Nombres
    var dep1Name by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.dependientes?.getOrNull(0)?.name ?: dependienteUsers.getOrNull(0) ?: "Dependiente 1")
    }
    var dep2Name by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.dependientes?.getOrNull(1)?.name ?: dependienteUsers.getOrNull(1) ?: "Dependiente 2")
    }
    var dep3Name by rememberSaveable(activeJornadaId) {
        mutableStateOf(savedPagos?.dependientes?.getOrNull(2)?.name ?: dependienteUsers.getOrNull(2) ?: "Dependiente 3")
    }

    // Inputs modo CATEGORIAS (Producción y Bebidas)
    var dep1ProdStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.isNotEmpty()) {
                val v = savedPagos.dependientes[0].ventasProduccion
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else {
                if (totalVentasProduccion % 1.0 == 0.0) totalVentasProduccion.toInt().toString() else "%.1f".format(totalVentasProduccion)
            }
        )
    }
    var dep1BebStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.isNotEmpty()) {
                val v = savedPagos.dependientes[0].ventasBebidas
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else {
                if (totalVentasBebidas % 1.0 == 0.0) totalVentasBebidas.toInt().toString() else "%.1f".format(totalVentasBebidas)
            }
        )
    }

    var dep2ProdStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 1) {
                val v = savedPagos.dependientes[1].ventasProduccion
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }
    var dep2BebStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 1) {
                val v = savedPagos.dependientes[1].ventasBebidas
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }

    var dep3ProdStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 2) {
                val v = savedPagos.dependientes[2].ventasProduccion
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }
    var dep3BebStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 2) {
                val v = savedPagos.dependientes[2].ventasBebidas
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }

    // Inputs modo GLOBAL
    var dep1GlobalStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.isNotEmpty()) {
                val v = savedPagos.dependientes[0].ventasTotales
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else {
                if (totalVentasSujetas % 1.0 == 0.0) totalVentasSujetas.toInt().toString() else "%.1f".format(totalVentasSujetas)
            }
        )
    }
    var dep2GlobalStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 1) {
                val v = savedPagos.dependientes[1].ventasTotales
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }
    var dep3GlobalStr by rememberSaveable(activeJornadaId) {
        mutableStateOf(
            if (savedPagos != null && savedPagos.dependientes.size > 2) {
                val v = savedPagos.dependientes[2].ventasTotales
                if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
            } else "0"
        )
    }

    // Función auxiliar para dividir en partes iguales
    fun aplicarDivisionEquitativa() {
        val n = cantidadDependientes.coerceAtLeast(1)
        if (distributionMode == "CATEGORIAS") {
            val partProd = totalVentasProduccion / n
            val partBeb = totalVentasBebidas / n
            val pStr = if (partProd % 1.0 == 0.0) partProd.toInt().toString() else "%.1f".format(partProd)
            val bStr = if (partBeb % 1.0 == 0.0) partBeb.toInt().toString() else "%.1f".format(partBeb)
            dep1ProdStr = pStr
            dep1BebStr = bStr
            if (n >= 2) {
                dep2ProdStr = pStr
                dep2BebStr = bStr
            }
            if (n >= 3) {
                dep3ProdStr = pStr
                dep3BebStr = bStr
            }
        } else {
            val partGlobal = totalVentasSujetas / n
            val gStr = if (partGlobal % 1.0 == 0.0) partGlobal.toInt().toString() else "%.1f".format(partGlobal)
            dep1GlobalStr = gStr
            if (n >= 2) dep2GlobalStr = gStr
            if (n >= 3) dep3GlobalStr = gStr
        }
    }

    // Tarifas unitarias efectivas de dependiente
    val rateDepProd = if (totalVentasProduccion > 0.0) pagoDependienteProduccion / totalVentasProduccion else 0.0
    val rateDepBeb = if (totalVentasBebidas > 0.0) pagoDependienteMercaderia / totalVentasBebidas else 0.0

    // Valores numéricos por dependiente
    val d1Prod = if (cantidadDependientes == 1) totalVentasProduccion else (dep1ProdStr.toDoubleOrNull() ?: 0.0)
    val d1Beb = if (cantidadDependientes == 1) totalVentasBebidas else (dep1BebStr.toDoubleOrNull() ?: 0.0)
    val d1Global = if (cantidadDependientes == 1) totalVentasSujetas else (dep1GlobalStr.toDoubleOrNull() ?: 0.0)
    val d1Pago = when {
        cantidadDependientes == 1 -> totalPagoDependiente
        distributionMode == "CATEGORIAS" -> d1Prod * rateDepProd + d1Beb * rateDepBeb
        else -> if (totalVentasSujetas > 0.0) (d1Global / totalVentasSujetas) * totalPagoDependiente else 0.0
    }

    val d2Prod = if (cantidadDependientes >= 2) (dep2ProdStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d2Beb = if (cantidadDependientes >= 2) (dep2BebStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d2Global = if (cantidadDependientes >= 2) (dep2GlobalStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d2Pago = when {
        cantidadDependientes < 2 -> 0.0
        distributionMode == "CATEGORIAS" -> d2Prod * rateDepProd + d2Beb * rateDepBeb
        else -> if (totalVentasSujetas > 0.0) (d2Global / totalVentasSujetas) * totalPagoDependiente else 0.0
    }

    val d3Prod = if (cantidadDependientes >= 3) (dep3ProdStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d3Beb = if (cantidadDependientes >= 3) (dep3BebStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d3Global = if (cantidadDependientes >= 3) (dep3GlobalStr.toDoubleOrNull() ?: 0.0) else 0.0
    val d3Pago = when {
        cantidadDependientes < 3 -> 0.0
        distributionMode == "CATEGORIAS" -> d3Prod * rateDepProd + d3Beb * rateDepBeb
        else -> if (totalVentasSujetas > 0.0) (d3Global / totalVentasSujetas) * totalPagoDependiente else 0.0
    }

    // Validación de comprobación
    val sumaProdAsignada = d1Prod + d2Prod + d3Prod
    val sumaBebAsignada = d1Beb + d2Beb + d3Beb
    val sumaGlobalAsignada = if (distributionMode == "CATEGORIAS") (sumaProdAsignada + sumaBebAsignada) else (d1Global + d2Global + d3Global)
    val diffProd = totalVentasProduccion - sumaProdAsignada
    val diffBeb = totalVentasBebidas - sumaBebAsignada
    val diffGlobal = totalVentasSujetas - sumaGlobalAsignada

    val isDistributionValid = when {
        cantidadDependientes == 1 -> true
        distributionMode == "CATEGORIAS" -> kotlin.math.abs(diffProd) < 0.05 && kotlin.math.abs(diffBeb) < 0.05
        else -> kotlin.math.abs(diffGlobal) < 0.05
    }

    // Lista construida de dependientes para persistencia
    val dependientesList = remember(
        cantidadDependientes, distributionMode, dep1Name, dep2Name, dep3Name,
        d1Prod, d1Beb, d1Global, d1Pago,
        d2Prod, d2Beb, d2Global, d2Pago,
        d3Prod, d3Beb, d3Global, d3Pago
    ) {
        val list = mutableListOf<DependientePagoDistribucion>()
        list.add(
            DependientePagoDistribucion(
                id = 1,
                name = dep1Name,
                ventasProduccion = d1Prod,
                ventasBebidas = d1Beb,
                ventasTotales = if (distributionMode == "CATEGORIAS") (d1Prod + d1Beb) else d1Global,
                montoPago = d1Pago
            )
        )
        if (cantidadDependientes >= 2) {
            list.add(
                DependientePagoDistribucion(
                    id = 2,
                    name = dep2Name,
                    ventasProduccion = d2Prod,
                    ventasBebidas = d2Beb,
                    ventasTotales = if (distributionMode == "CATEGORIAS") (d2Prod + d2Beb) else d2Global,
                    montoPago = d2Pago
                )
            )
        }
        if (cantidadDependientes >= 3) {
            list.add(
                DependientePagoDistribucion(
                    id = 3,
                    name = dep3Name,
                    ventasProduccion = d3Prod,
                    ventasBebidas = d3Beb,
                    ventasTotales = if (distributionMode == "CATEGORIAS") (d3Prod + d3Beb) else d3Global,
                    montoPago = d3Pago
                )
            )
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // =============================================================
        // ENCABEZADO: TOTAL PAGOS DE PERSONAL DE LA JORNADA
        // =============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ElQadreNavy,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "TOTAL PAGOS DE PERSONAL DE LA JORNADA",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ElQadreGold
                        )
                        Text(
                            text = "Cocina + Cajero + Dependientes",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    if (pagosConfirmados) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF16A34A)
                        ) {
                            Text(
                                text = "CONFIRMADO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "$${"%.2f".format(totalPagoPersonal)} CUP",
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = Color.White
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "COCINA", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        Text(text = "$${"%.2f".format(totalPagoCocina)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column {
                        Text(text = "CAJERO", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        Text(text = "$${"%.2f".format(totalPagoCajero)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Column {
                        Text(text = "DEPENDIENTES", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                        Text(text = "$${"%.2f".format(totalPagoDependiente)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }

        // =============================================================
        // 1. SECCIÓN COCINA
        // =============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "COCINA (PRODUCCIÓN)",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Pago unitario de Ficha × Trabajadores de hoy",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFFF7ED)
                    ) {
                        Text(
                            text = "$${"%.2f".format(totalPagoCocina)} CUP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFEA580C),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Selector de cantidad de trabajadores de cocina a pagar en la jornada
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Trabajadores a pagar hoy",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                            Text(
                                text = "Independiente de la Ficha de Costo",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    if (cantidadCocineros > 1) {
                                        cantidadCocineros--
                                        produccionStates.forEach { it.cantidadCocinerosStr = cantidadCocineros.toString() }
                                    }
                                },
                                enabled = !pagosConfirmados && cantidadCocineros > 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos trabajadores", modifier = Modifier.size(16.dp))
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Slate300),
                                modifier = Modifier.widthIn(min = 44.dp)
                            ) {
                                Text(
                                    text = "$cantidadCocineros",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    if (cantidadCocineros < 99) {
                                        cantidadCocineros++
                                        produccionStates.forEach { it.cantidadCocinerosStr = cantidadCocineros.toString() }
                                    }
                                },
                                enabled = !pagosConfirmados,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más trabajadores", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                if (produccionStates.isEmpty()) {
                    Text(
                        text = "No se registraron productos de producción en esta jornada.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                } else {
                    produccionStates.forEach { item ->
                        val subtotalCocina = item.vendible * item.pagoCocinaUnitario * item.cantidadCocineros
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.productName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                                Text(
                                    text = "${"%.1f".format(item.vendible)} ${item.unit} vendibles × $${"%.2f".format(item.pagoCocinaUnitario)} × ${item.cantidadCocineros} ${if (item.cantidadCocineros > 1) "trabajadores" else "trabajador"}",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                            Text(
                                text = "$${"%.2f".format(subtotalCocina)} CUP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = if (subtotalCocina > 0) Slate800 else Slate400
                            )
                        }
                        HorizontalDivider(color = Slate50)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFFBEB),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Pago Cocina = Pago unitario de Ficha ($${"%.2f".format(produccionStates.firstOrNull()?.pagoCocinaUnitario ?: 0.0)} CUP) × Cantidad de trabajadores indicada hoy ($cantidadCocineros). La cantidad configurada en la Ficha de Costo no fija los cocineros del día.",
                        fontSize = 10.sp,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        // =============================================================
        // 2. SECCIÓN CAJERO
        // =============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Slate200),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PointOfSale, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "CAJERO",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Tarifas de Producción y Bebidas",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF0F9FF)
                    ) {
                        Text(
                            text = "$${"%.2f".format(totalPagoCajero)} CUP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0284C7),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = Slate100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = " • Producción (${"%.1f".format(totalVentasProduccion)} u vendibles)", fontSize = 12.sp, color = Slate700)
                        Text(text = "Tarifas por unidad de Ficha de Costo", fontSize = 10.sp, color = Slate500)
                    }
                    Text(text = "$${"%.2f".format(pagoCajeroProduccion)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = " • Bebidas (${"%.1f".format(totalVentasBebidas)} u vendidas)", fontSize = 12.sp, color = Slate700)
                        val cajTxt = if (tarifasBebidas.pagoCajeroModalidad == "PORCENTAJE") {
                            "${"%.1f".format(tarifasBebidas.pagoCajeroValor)}%"
                        } else {
                            "$${"%.2f".format(tarifasBebidas.pagoCajeroPorUnidad)} CUP/u"
                        }
                        Text(text = "Tarifa configurada: $cajTxt", fontSize = 10.sp, color = Slate500)
                    }
                    Text(text = "$${"%.2f".format(pagoCajeroMercaderia)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate800)
                }

                HorizontalDivider(color = Slate100)

                CuadreMetricRow(
                    label = "TOTAL PAGO CAJERO",
                    value = "$${"%.2f".format(totalPagoCajero)} CUP",
                    color = Color(0xFF0284C7),
                    isBold = true
                )
            }
        }

        // =============================================================
        // 3. SECCIÓN DEPENDIENTES (Con Distribución Manual 1 a 3)
        // =============================================================
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f)),
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.People, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = "DEPENDIENTES",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Generado por Producción y Bebidas",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF5F3FF)
                    ) {
                        Text(
                            text = "$${"%.2f".format(totalPagoDependiente)} CUP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF7C3AED),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Resumen base de cálculo dependientes
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Producción:", fontSize = 11.sp, color = Slate600)
                            Text(text = "${"%.1f".format(totalVentasProduccion)} u → $${"%.2f".format(pagoDependienteProduccion)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Bebidas:", fontSize = 11.sp, color = Slate600)
                            val depTxt = if (tarifasBebidas.pagoDependienteModalidad == "PORCENTAJE") {
                                "${"%.1f".format(tarifasBebidas.pagoDependienteValor)}%"
                            } else {
                                "$${"%.2f".format(tarifasBebidas.pagoDependientePorUnidad)}/u"
                            }
                            Text(text = "${"%.1f".format(totalVentasBebidas)} u (@ $depTxt) → $${"%.2f".format(pagoDependienteMercaderia)} CUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                        }
                        HorizontalDivider(color = Slate200)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Total Ventas Sujetas a Distribución:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElQadreNavy)
                            Text(text = "${"%.1f".format(totalVentasSujetas)} unidades", fontSize = 11.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // SELECTOR DE CANTIDAD DE DEPENDIENTES (1, 2 o 3)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Cantidad de dependientes en el turno:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 3).forEach { count ->
                            val isSelected = (cantidadDependientes == count)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF7C3AED) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF7C3AED) else Slate200),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable {
                                        cantidadDependientes = count
                                        if (count == 1) {
                                            dep1ProdStr = if (totalVentasProduccion % 1.0 == 0.0) totalVentasProduccion.toInt().toString() else "%.1f".format(totalVentasProduccion)
                                            dep1BebStr = if (totalVentasBebidas % 1.0 == 0.0) totalVentasBebidas.toInt().toString() else "%.1f".format(totalVentasBebidas)
                                            dep1GlobalStr = if (totalVentasSujetas % 1.0 == 0.0) totalVentasSujetas.toInt().toString() else "%.1f".format(totalVentasSujetas)
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$count ${if (count == 1) "Dependiente" else "Dependientes"}",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Slate700
                                    )
                                }
                            }
                        }
                    }
                }

                // SI ES 1 DEPENDIENTE: 100% AUTOMÁTICO
                if (cantidadDependientes == 1) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF5F3FF),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                                Text(
                                    text = "1 Dependiente: Se asigna automáticamente el 100 % de las ventas correspondientes.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF5B21B6)
                                )
                            }
                            OutlinedTextField(
                                value = dep1Name,
                                onValueChange = { dep1Name = it },
                                label = { Text("Nombre del Dependiente", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Total a cobrar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate700)
                                Text(text = "$${"%.2f".format(d1Pago)} CUP", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                            }
                        }
                    }
                } else {
                    // SI SON 2 O 3 DEPENDIENTES:
                    // Selección de modo y botón de dividir equitativo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Selector de Modo
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (distributionMode == "CATEGORIAS") Color(0xFF7C3AED) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { distributionMode = "CATEGORIAS" }
                            ) {
                                Text(
                                    text = "Por Categorías",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (distributionMode == "CATEGORIAS") Color.White else Slate600,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (distributionMode == "GLOBAL") Color(0xFF7C3AED) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { distributionMode = "GLOBAL" }
                            ) {
                                Text(
                                    text = "Por Total Ventas",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (distributionMode == "GLOBAL") Color.White else Slate600,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Botón de división equitativa rápida
                        OutlinedButton(
                            onClick = { aplicarDivisionEquitativa() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF7C3AED))
                        ) {
                            Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF7C3AED))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Partes iguales", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                        }
                    }

                    // TARJETA DE CADA DEPENDIENTE (1..cantidadDependientes)
                    // Dependiente 1
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Dependiente 1", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(text = "Pago: $${"%.2f".format(d1Pago)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                            }
                            OutlinedTextField(
                                value = dep1Name,
                                onValueChange = { dep1Name = it },
                                label = { Text("Nombre / Usuario", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (distributionMode == "CATEGORIAS") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = dep1ProdStr,
                                        onValueChange = { dep1ProdStr = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Vtas Producción (u)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = dep1BebStr,
                                        onValueChange = { dep1BebStr = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Vtas Bebidas (u)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = dep1GlobalStr,
                                    onValueChange = { dep1GlobalStr = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Ventas Totales Asignadas (u)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Dependiente 2
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Dependiente 2", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                Text(text = "Pago: $${"%.2f".format(d2Pago)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                            }
                            OutlinedTextField(
                                value = dep2Name,
                                onValueChange = { dep2Name = it },
                                label = { Text("Nombre / Usuario", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (distributionMode == "CATEGORIAS") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = dep2ProdStr,
                                        onValueChange = { dep2ProdStr = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Vtas Producción (u)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = dep2BebStr,
                                        onValueChange = { dep2BebStr = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Vtas Bebidas (u)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                OutlinedTextField(
                                    value = dep2GlobalStr,
                                    onValueChange = { dep2GlobalStr = it.filter { c -> c.isDigit() || c == '.' } },
                                    label = { Text("Ventas Totales Asignadas (u)", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Dependiente 3 (si aplica)
                    if (cantidadDependientes >= 3) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "Dependiente 3", fontSize = 12.sp, fontWeight = FontWeight.Black, color = ElQadreNavy)
                                    Text(text = "Pago: $${"%.2f".format(d3Pago)} CUP", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFF15803D))
                                }
                                OutlinedTextField(
                                    value = dep3Name,
                                    onValueChange = { dep3Name = it },
                                    label = { Text("Nombre / Usuario", fontSize = 10.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (distributionMode == "CATEGORIAS") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = dep3ProdStr,
                                            onValueChange = { dep3ProdStr = it.filter { c -> c.isDigit() || c == '.' } },
                                            label = { Text("Vtas Producción (u)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = dep3BebStr,
                                            onValueChange = { dep3BebStr = it.filter { c -> c.isDigit() || c == '.' } },
                                            label = { Text("Vtas Bebidas (u)", fontSize = 10.sp) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = dep3GlobalStr,
                                        onValueChange = { dep3GlobalStr = it.filter { c -> c.isDigit() || c == '.' } },
                                        label = { Text("Ventas Totales Asignadas (u)", fontSize = 10.sp) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // COMPROBACIÓN EN TIEMPO REAL
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDistributionValid) Color(0xFFDCFCE7) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (isDistributionValid) Color(0xFF16A34A) else Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isDistributionValid) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = if (isDistributionValid) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isDistributionValid) "Distribución Cuadrada al 100%" else "Diferencia Pendiente de Distribución",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isDistributionValid) Color(0xFF166534) else Color(0xFF991B1B)
                                    )
                                }
                                val sumaPagosDeps = d1Pago + d2Pago + d3Pago
                                Text(
                                    text = "$${"%.2f".format(sumaPagosDeps)} / $${"%.2f".format(totalPagoDependiente)} CUP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isDistributionValid) Color(0xFF166534) else Color(0xFF991B1B)
                                )
                            }
                            if (!isDistributionValid) {
                                if (distributionMode == "CATEGORIAS") {
                                    if (kotlin.math.abs(diffProd) >= 0.05) {
                                        Text(
                                            text = if (diffProd > 0) "• Producción: Faltan ${"%.1f".format(diffProd)} u por asignar" else "• Producción: Exceso de ${"%.1f".format(-diffProd)} u asignadas",
                                            fontSize = 10.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                    if (kotlin.math.abs(diffBeb) >= 0.05) {
                                        Text(
                                            text = if (diffBeb > 0) "• Bebidas: Faltan ${"%.1f".format(diffBeb)} u por asignar" else "• Bebidas: Exceso de ${"%.1f".format(-diffBeb)} u asignadas",
                                            fontSize = 10.sp,
                                            color = Color(0xFF991B1B)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = if (diffGlobal > 0) "Faltan ${"%.1f".format(diffGlobal)} unidades por asignar (Total: ${"%.1f".format(totalVentasSujetas)} u)" else "Exceso de ${"%.1f".format(-diffGlobal)} unidades asignadas",
                                        fontSize = 10.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // =============================================================
        // 4. ESTADO DE EFECTIVO Y DINERO FINAL EN CAJA (Sin doble誕生)
        // =============================================================
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            border = BorderStroke(1.5.dp, Slate200),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ENCABEZADO DE SECCIÓN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ElQadreNavy.copy(alpha = 0.1f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "BALANCE FINAL DE CAJA",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Efectivo disponible tras liquidar pagos",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }
                    if (pagosConfirmados) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "PAGOS REALIZADOS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "PENDIENTE PAGOS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Slate100)

                // PRIMER BLOQUE: EFECTIVO CONTADO EN CAJA + TOTAL PAGOS DE PERSONAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // TARJETA 1: EFECTIVO CONTADO EN CAJA
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = ElQadreNavy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "EFECTIVO CONTADO",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Slate600
                                )
                            }
                            Text(
                                text = "$${"%.2f".format(efectivoRealVal)} CUP",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = ElQadreNavy
                            )
                            Text(
                                text = "Físico en caja",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        }
                    }

                    // TARJETA 2: TOTAL PAGOS DE PERSONAL
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Badge,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "PAGOS PERSONAL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF991B1B)
                                )
                            }
                            Text(
                                text = "-$${"%.2f".format(totalPagoPersonal)} CUP",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = if (pagosConfirmados) "Salida real confirmada" else "Salida por pagar",
                                fontSize = 10.sp,
                                color = Color(0xFF991B1B).copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // TARJETA DE MAYOR PRESENCIA VISUAL (HERO): DINERO FINAL EN CAJA
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ElQadreNavy,
                    border = BorderStroke(2.dp, ElQadreGold),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ElQadreGold.copy(alpha = 0.25f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountBalance,
                                        contentDescription = null,
                                        tint = ElQadreGold,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(24.dp)
                                    )
                                }
                                Text(
                                    text = "DINERO FINAL EN CAJA",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElQadreGold
                            ) {
                                Text(
                                    text = "EFECTIVO RESTANTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = ElQadreNavy,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                        Text(
                            text = "EFECTIVO CONTADO ($${"%.2f".format(efectivoRealVal)}) − PAGOS REALES ($${"%.2f".format(if (pagosConfirmados) totalPagoPersonal else totalPagoPersonal)}) = DINERO FINAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate300
                        )

                        Text(
                            text = "$${"%.2f".format(dineroFinalEnCaja)} CUP",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4ADE80),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Los pagos de personal representan la salida física real de efectivo de la caja. No se vuelven a descontar como costo para calcular la utilidad del negocio.",
                                fontSize = 10.sp,
                                color = Slate300,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // =============================================================
        // 5. ACCIÓN DE CONFIRMACIÓN DE PAGOS
        // =============================================================
        if (pagosConfirmados) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFDCFCE7),
                border = BorderStroke(1.5.dp, Color(0xFF16A34A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                        Column {
                            Text(
                                text = "PAGOS DE PERSONAL CONFIRMADOS",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = "Registrados en la jornada y en la bitácora del negocio.",
                                fontSize = 11.sp,
                                color = Color(0xFF14532D)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onModificarPagos,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF166534)),
                        border = BorderStroke(1.dp, Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text("MODIFICAR DISTRIBUCIÓN O DATOS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        if (isDistributionValid) {
                            onConfirmarPagos(
                                totalPagoPersonal,
                                totalPagoCocina,
                                totalPagoCajero,
                                totalPagoDependiente,
                                cantidadDependientes,
                                dependientesList,
                                distributionMode,
                                cantidadCocineros
                            )
                        }
                    },
                    enabled = isDistributionValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF15803D),
                        disabledContainerColor = Slate300
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_confirmar_pagos_personal")
                ) {
                    Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONFIRMAR PAGOS DE PERSONAL",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                if (!isDistributionValid) {
                    Text(
                        text = "⚠️ Cuadre la distribución entre dependientes para habilitar la confirmación.",
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(56.dp).navigationBarsPadding())
    }
}
