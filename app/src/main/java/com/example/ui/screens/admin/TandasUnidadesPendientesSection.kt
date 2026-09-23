package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.model.Jornada
import com.example.data.local.model.Product
import com.example.data.local.model.Tanda
import com.example.data.local.model.parsePresentacionesEspeciales
import com.example.ui.theme.ElQadreNavy
import com.example.ui.theme.Emerald600
import com.example.ui.theme.Emerald700
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.viewmodel.MainUiState
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Modelo para representar las unidades pendientes de un producto de la jornada anterior
data class PendingProductItem(
    val productId: Long,
    val productName: String,
    val basePendingQty: Double,
    val basePendingUnit: String,
    val specialPresentations: List<PendingSpecialPresentation>,
    val averageYield: Double,
    val baseIngredientName: String,
    val baseQuantityUnit: String,
    val sampleTanda: Tanda?,
    val isBaseAlreadyConverted: Boolean
)

data class PendingSpecialPresentation(
    val name: String,
    val quantity: Double,
    val unit: String,
    val baseEquivalence: Double,
    val isAlreadyConverted: Boolean
)

@Composable
fun TandasUnidadesPendientesSection(
    uiState: MainUiState,
    viewModel: MainViewModel,
    onShowJornadaCerradaDialog: () -> Unit
) {
    val activeJornada = uiState.activeJornada

    // 1. Identificar la jornada inmediatamente anterior (la más reciente cerrada)
    val previousJornada = remember(uiState.allJornadas, activeJornada) {
        val candidates = uiState.allJornadas.filter {
            it.id != activeJornada?.id && (!it.isOpen || (it.closedAt != null && it.closedAt > 0))
        }
        candidates.sortedWith(
            compareByDescending<Jornada> { it.closedAt ?: 0L }
                .thenByDescending { it.openedAt }
                .thenByDescending { it.id }
        ).firstOrNull()
    }

    if (previousJornada == null) return

    // 2. Tandas de la jornada anterior
    val previousJornadaTandas = remember(uiState.tandas, previousJornada) {
        uiState.tandas.filter {
            it.jornadaId == previousJornada.id || (previousJornada.openedAt > 0 && it.date >= previousJornada.openedAt && (previousJornada.closedAt == null || it.date <= previousJornada.closedAt!! + 60000L))
        }
    }

    if (previousJornadaTandas.isEmpty()) return

    // 3. Tandas de la jornada actual (para verificar qué unidades pendientes ya fueron convertidas)
    val currentJornadaTandas = remember(uiState.tandas, activeJornada) {
        if (activeJornada == null) emptyList()
        else uiState.tandas.filter {
            it.jornadaId == activeJornada.id || (activeJornada.openedAt > 0 && it.date >= activeJornada.openedAt)
        }
    }

    // 4. Analizar y agrupar las unidades pendientes por producto
    val pendingItems = remember(previousJornadaTandas, currentJornadaTandas, uiState.products) {
        val productMap = uiState.products.associateBy { it.id }
        val groups = previousJornadaTandas.groupBy { it.productId }

        groups.mapNotNull { (prodId, tList) ->
            val product = productMap[prodId]
            val pName = product?.name ?: tList.firstOrNull()?.productName ?: "Producto #$prodId"

            // Buscar la observación de pendientes en las tandas archivadas de ese producto
            val rawObs = tList.firstNotNullOfOrNull { t ->
                if (t.observation.contains("Pendientes:")) {
                    val part = t.observation.substringAfter("Pendientes:").trim()
                    if (part.isNotBlank() && part != "0") part else null
                } else null
            } ?: return@mapNotNull null

            // Rendimiento promedio histórico de la jornada anterior (excluyendo Tanda 00 previa para no distorsionar rendimiento real)
            val prodTandas = tList.filter { it.tandaNumber != "00" && it.baseQuantityUsed > 0.0 }
            val targetList = if (prodTandas.isNotEmpty()) prodTandas else tList
            val totalFinalQty = targetList.sumOf {
                if (it.actualYield > 0.0) it.actualYield
                else if (it.expectedYield > 0.0) it.expectedYield
                else it.estimatedYield
            }
            val totalBaseQtyUsed = targetList.sumOf { it.baseQuantityUsed }
            val averageYield = if (totalBaseQtyUsed > 0.0) {
                totalFinalQty / totalBaseQtyUsed
            } else {
                val pe = uiState.productosElaborados.find { it.productId == prodId }
                if (pe != null && pe.baseYield > 0.0 && pe.baseQuantity > 0.0) pe.baseYield / pe.baseQuantity else if (pe != null && pe.baseYield > 0.0) pe.baseYield else 1.0
            }

            val sampleTanda = tList.firstOrNull()
            val baseIngredientName = sampleTanda?.baseMateriaPrimaName?.ifBlank { null } ?: "Insumo Base"
            val baseQuantityUnit = sampleTanda?.baseQuantityUnit?.ifBlank { null } ?: "lb"

            // Parsear rawObs: e.g. "50 Pizzas | Familiar: 10 u"
            val parts = rawObs.split("|").map { it.trim() }.filter { it.isNotBlank() }
            var baseQty = 0.0
            var baseUnit = sampleTanda?.productionUnit?.ifBlank { null } ?: product?.unitOfMeasure?.ifBlank { null } ?: "u"
            val specialList = mutableListOf<PendingSpecialPresentation>()

            val prodPresentaciones = product?.let { parsePresentacionesEspeciales(it.presentacionesEspeciales) } ?: emptyList()

            for (p in parts) {
                if (p.contains(":")) {
                    val pNamePart = p.substringBefore(":").trim()
                    val pValPart = p.substringAfter(":").trim()
                    val qty = """([\d.,]+)""".toRegex().find(pValPart)?.value?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                    val unit = pValPart.replace("""[\d.,]+""".toRegex(), "").trim().ifBlank { "u" }
                    val presObj = prodPresentaciones.find { it.name.equals(pNamePart, ignoreCase = true) }
                    val equiv = presObj?.baseEquivalence ?: 1.0

                    val isConverted = currentJornadaTandas.any {
                        it.observation.contains("ORIGEN_PENDIENTE_JORNADA_${previousJornada.id}_PROD_${prodId}_PRES_${pNamePart}") ||
                        (it.tandaNumber == "00" && it.productId == prodId && it.observation.contains("ORIGEN_JORNADA_ANTERIOR: ${previousJornada.id}") && it.observation.contains(pNamePart))
                    }

                    if (qty > 0.0) {
                        specialList.add(
                            PendingSpecialPresentation(
                                name = pNamePart,
                                quantity = qty,
                                unit = unit,
                                baseEquivalence = equiv,
                                isAlreadyConverted = isConverted
                            )
                        )
                    }
                } else {
                    val qty = """^([\d.,]+)""".toRegex().find(p)?.value?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                    val unitPart = p.replace("""^[\d.,]+\s*""".toRegex(), "").trim()
                    if (unitPart.isNotBlank()) baseUnit = unitPart
                    if (qty > 0.0) baseQty = qty
                }
            }

            val isBaseConverted = currentJornadaTandas.any {
                it.observation.contains("ORIGEN_PENDIENTE_JORNADA_${previousJornada.id}_PROD_${prodId}_BASE") ||
                (it.tandaNumber == "00" && it.productId == prodId && it.observation.contains("ORIGEN_JORNADA_ANTERIOR: ${previousJornada.id}") && !it.observation.contains("Presentación:"))
            }

            // Si no hay cantidades pendientes detectadas, omitir
            if (baseQty <= 0.0 && specialList.isEmpty()) return@mapNotNull null

            PendingProductItem(
                productId = prodId,
                productName = pName,
                basePendingQty = baseQty,
                basePendingUnit = baseUnit,
                specialPresentations = specialList,
                averageYield = averageYield,
                baseIngredientName = baseIngredientName,
                baseQuantityUnit = baseQuantityUnit,
                sampleTanda = sampleTanda,
                isBaseAlreadyConverted = isBaseConverted
            )
        }
    }

    // Filtrar solo los productos que tengan al menos una unidad pendiente por convertir
    val itemsAvailableForConversion = pendingItems.filter { item ->
        (!item.isBaseAlreadyConverted && item.basePendingQty > 0.0) ||
                item.specialPresentations.any { !it.isAlreadyConverted && it.quantity > 0.0 }
    }

    if (itemsAvailableForConversion.isEmpty()) {
        // No hay unidades pendientes pendientes de convertir
        return
    }

    // Estado para el modal de confirmación
    var conversionTarget by remember { mutableStateOf<ConversionTarget?>(null) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFFBEB), // Amber 50
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B)), // Amber 500
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("seccion_unidades_pendientes_jornada_anterior")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabecera de la sección
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFEF3C7), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "UNIDADES PENDIENTES DE LA JORNADA ANTERIOR",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF92400E),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Procedentes del cierre de la Jornada #${previousJornada.id}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFFDE68A), thickness = 1.dp)

            // Lista de productos con unidades pendientes
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsAvailableForConversion.forEach { item ->
                    PendingProductCard(
                        item = item,
                        previousJornadaId = previousJornada.id,
                        onConvertBase = {
                            if (activeJornada == null || !activeJornada.isOpen) {
                                onShowJornadaCerradaDialog()
                            } else {
                                conversionTarget = ConversionTarget.Base(item)
                            }
                        },
                        onConvertPresentation = { pres ->
                            if (activeJornada == null || !activeJornada.isOpen) {
                                onShowJornadaCerradaDialog()
                            } else {
                                conversionTarget = ConversionTarget.Presentation(item, pres)
                            }
                        }
                    )
                }
            }
        }
    }

    // Modal de confirmación para crear la nueva tanda
    conversionTarget?.let { target ->
        ConfirmConversionDialog(
            target = target,
            previousJornada = previousJornada,
            activeJornada = activeJornada!!,
            currentJornadaTandas = currentJornadaTandas,
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { conversionTarget = null }
        )
    }
}

sealed class ConversionTarget {
    data class Base(val item: PendingProductItem) : ConversionTarget()
    data class Presentation(val item: PendingProductItem, val presentation: PendingSpecialPresentation) : ConversionTarget()
}

@Composable
fun PendingProductCard(
    item: PendingProductItem,
    previousJornadaId: Long,
    onConvertBase: () -> Unit,
    onConvertPresentation: (PendingSpecialPresentation) -> Unit
) {
    val bQtyFormatted = if (item.basePendingQty % 1.0 == 0.0) item.basePendingQty.toInt().toString() else "%.1f".format(item.basePendingQty)
    val avgYieldFormatted = if (item.averageYield % 1.0 == 0.0) item.averageYield.toInt().toString() else "%.2f".format(item.averageYield)
    val calculatedBaseIng = if (item.averageYield > 0.0) item.basePendingQty / item.averageYield else item.basePendingQty
    val calculatedBaseIngFormatted = "%.2f".format(calculatedBaseIng)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Nombre del producto
            Text(
                text = item.productName.uppercase(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = ElQadreNavy
            )

            // Caso 1: Unidades pendientes base
            if (item.basePendingQty > 0.0 && !item.isBaseAlreadyConverted) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate100.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unidades pendientes:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Text(
                            text = "$bQtyFormatted ${item.basePendingUnit}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB45309)
                        )
                    }

                    // Detalle del cálculo del ingrediente base según requerimiento
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.baseIngredientName} equivalente (informativo):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                        Text(
                            text = "$calculatedBaseIngFormatted ${item.baseQuantityUnit}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }

                    Text(
                        text = "Fórmula analítica: $bQtyFormatted ÷ $avgYieldFormatted = $calculatedBaseIngFormatted ${item.baseQuantityUnit} (${item.baseIngredientName}) [Solo informativo • Sin consumo de insumos]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onConvertBase,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElQadreNavy,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_convertir_en_tanda_${item.productId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONVERTIR EN TANDA 00",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Caso 2: Presentaciones especiales pendientes
            item.specialPresentations.filter { !it.isAlreadyConverted && it.quantity > 0.0 }.forEach { pres ->
                val pQtyFormatted = if (pres.quantity % 1.0 == 0.0) pres.quantity.toInt().toString() else "%.1f".format(pres.quantity)
                val baseEquivTotal = pres.quantity * pres.baseEquivalence
                val presBaseIng = if (item.averageYield > 0.0) baseEquivTotal / item.averageYield else baseEquivTotal
                val presBaseIngFormatted = "%.2f".format(presBaseIng)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF9C3).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Presentación especial: ${pres.name}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Text(
                            text = "$pQtyFormatted ${pres.unit}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFB45309)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${item.baseIngredientName} equivalente (informativo):",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate600
                        )
                        Text(
                            text = "$presBaseIngFormatted ${item.baseQuantityUnit}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElQadreNavy
                        )
                    }

                    Text(
                        text = "Equiv: ${"%.1f".format(baseEquivTotal)} u base | Insumo: ${"%.1f".format(baseEquivTotal)} ÷ $avgYieldFormatted = $presBaseIngFormatted ${item.baseQuantityUnit} [Informativo • Sin consumo]",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate500
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { onConvertPresentation(pres) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F766E),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_convertir_pres_${pres.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CONVERTIR EN TANDA 00 (${pres.name.uppercase()})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmConversionDialog(
    target: ConversionTarget,
    previousJornada: Jornada,
    activeJornada: Jornada,
    currentJornadaTandas: List<Tanda>,
    uiState: MainUiState,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val item = when (target) {
        is ConversionTarget.Base -> target.item
        is ConversionTarget.Presentation -> target.item
    }

    val isPresentation = target is ConversionTarget.Presentation
    val presentation = (target as? ConversionTarget.Presentation)?.presentation

    val pendingUnits = if (isPresentation && presentation != null) presentation.quantity else item.basePendingQty
    val pendingUnitsStr = if (pendingUnits % 1.0 == 0.0) pendingUnits.toInt().toString() else "%.1f".format(pendingUnits)
    val productionUnitStr = if (isPresentation && presentation != null) presentation.unit else item.basePendingUnit

    val baseEquivTotal = if (isPresentation && presentation != null) {
        presentation.quantity * presentation.baseEquivalence
    } else {
        item.basePendingQty
    }

    val calculatedBaseIngQty = if (item.averageYield > 0.0) {
        baseEquivTotal / item.averageYield
    } else {
        baseEquivTotal
    }
    val calculatedBaseIngFormatted = "%.2f".format(calculatedBaseIngQty)

    // Regla 1: NUMERACIÓN OBLIGATORIA TANDA 00 reservada para unidades pendientes
    val tandaNumStr = "00"

    val avgYieldFormatted = if (item.averageYield % 1.0 == 0.0) item.averageYield.toInt().toString() else "%.2f".format(item.averageYield)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = null,
                    tint = Color(0xFFB45309),
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = "CONVERTIR EN TANDA 00",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = ElQadreNavy
                    )
                    Text(
                        text = "Unidades pendientes de jornada anterior",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB45309)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Se creará la Tanda 00 en la jornada actual para organizar y trasladar las unidades pendientes.",
                    fontSize = 13.5.sp,
                    color = Slate600
                )

                // Panel explicativo con las reglas de la Tanda 00
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "REGLAS CRÍTICAS DE LA TANDA 00:",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.5.sp,
                            color = Color(0xFF92400E)
                        )
                        Text(
                            text = "• NO es una nueva producción (producida en la jornada anterior).\n• NO descuenta insumos ni modifica inventario.\n• El insumo base es estrictamente informativo y analítico.\n• Las tandas normales continuarán numerándose desde 01.",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F),
                            lineHeight = 15.sp
                        )
                    }
                }

                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Producto: ${item.productName}${if (isPresentation) " (${presentation?.name})" else ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Slate800
                        )
                        Text(
                            text = "Unidades disponibles: $pendingUnitsStr $productionUnitStr",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFB45309)
                        )
                        Text(
                            text = "Rendimiento promedio previo: $avgYieldFormatted",
                            fontSize = 12.5.sp,
                            color = Slate700
                        )
                        HorizontalDivider(color = Slate200, thickness = 1.dp)
                        Text(
                            text = "Insumo base equivalente (informativo): ${item.baseIngredientName}: $calculatedBaseIngFormatted ${item.baseQuantityUnit}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ElQadreNavy
                        )
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Identificador asignado: TANDA 00",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val product = uiState.products.find { it.id == item.productId }
                    val sample = item.sampleTanda

                    val unitCost = if (sample != null && sample.realUnitCost > 0.0) {
                        sample.realUnitCost
                    } else if (sample != null && sample.actualYield > 0.0) {
                        sample.totalBatchCost / sample.actualYield
                    } else {
                        0.0
                    }
                    val totalCost = unitCost * pendingUnits
                    val salePrice = sample?.salePrice ?: (product?.price ?: 0.0)

                    val originTag = if (isPresentation && presentation != null) {
                        "[TANDA_00_PENDIENTES] [ORIGEN_JORNADA_ANTERIOR: ${previousJornada.id}] [ORIGEN_PENDIENTE_JORNADA_${previousJornada.id}_PROD_${item.productId}_PRES_${presentation.name}]"
                    } else {
                        "[TANDA_00_PENDIENTES] [ORIGEN_JORNADA_ANTERIOR: ${previousJornada.id}] [ORIGEN_PENDIENTE_JORNADA_${previousJornada.id}_PROD_${item.productId}_BASE]"
                    }

                    val humanObs = if (isPresentation && presentation != null) {
                        "Tanda 00 — Unidades pendientes de jornada anterior (Jornada #${previousJornada.id}) - Presentación: ${presentation.name} | UNIDADES PENDIENTES DE LA JORNADA ANTERIOR | Insumo base equivalente (solo informativo): $calculatedBaseIngFormatted ${item.baseQuantityUnit} (${item.baseIngredientName}) | Rendimiento promedio previo: $avgYieldFormatted"
                    } else {
                        "Tanda 00 — Unidades pendientes de jornada anterior (Jornada #${previousJornada.id}) | UNIDADES PENDIENTES DE LA JORNADA ANTERIOR | Insumo base equivalente (solo informativo): $calculatedBaseIngFormatted ${item.baseQuantityUnit} (${item.baseIngredientName}) | Rendimiento promedio previo: $avgYieldFormatted"
                    }

                    val fullObs = "$originTag $humanObs"

                    val batchUuid = "TANDA-00-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-${System.currentTimeMillis().toString().takeLast(4)}"

                    val newTanda = Tanda(
                        uuid = batchUuid,
                        productId = item.productId,
                        productName = if (isPresentation && presentation != null) "${item.productName} (${presentation.name})" else item.productName,
                        date = System.currentTimeMillis(),
                        responsibleUser = activeJornada.openedBy.ifBlank { "admin" },
                        baseMateriaPrimaId = sample?.baseMateriaPrimaId ?: 0L,
                        baseMateriaPrimaName = item.baseIngredientName,
                        baseQuantityUsed = calculatedBaseIngQty,
                        baseQuantityUnit = item.baseQuantityUnit,
                        productionFactor = item.averageYield,
                        estimatedYield = pendingUnits,
                        productionUnit = productionUnitStr,
                        ingredientsConsumedText = "Insumo base equivalente: ${item.baseIngredientName}: $calculatedBaseIngFormatted ${item.baseQuantityUnit} (Solo informativo - Sin descuento de inventario)",
                        status = "ABIERTA",
                        jornada = "Jornada #${activeJornada.id}",
                        jornadaId = activeJornada.id,
                        observation = fullObs,
                        laborCostType = "NINGUNO",
                        laborCostValue = 0.0,
                        totalLaborCost = 0.0,
                        totalDirectIngredientsCost = totalCost,
                        totalIndirectCostAllocated = 0.0,
                        totalBatchCost = totalCost,
                        realUnitCost = unitCost,
                        tandaNumber = "00",
                        expectedYield = pendingUnits,
                        actualYield = 0.0,
                        yieldPercentage = 100.0,
                        expectedRevenue = pendingUnits * salePrice,
                        estimatedProfit = (pendingUnits * salePrice) - totalCost,
                        profitMargin = if (salePrice > 0.0 && pendingUnits * salePrice > 0.0) (((pendingUnits * salePrice) - totalCost) / (pendingUnits * salePrice)) * 100.0 else 0.0,
                        inventoryDeducted = true,
                        ownerPayType = "NINGUNO",
                        ownerPayValue = 0.0,
                        totalOwnerPay = 0.0,
                        quantitySold = 0.0,
                        salePrice = salePrice,
                        realRevenue = 0.0,
                        deviceId = "DISPOSITIVO-LOCAL"
                    )

                    // Se registra la Tanda 00 sin consumos porque los insumos fueron consumidos en la jornada previa
                    viewModel.registrarTanda(newTanda, emptyList())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElQadreNavy),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CONFIRMAR Y CREAR TANDA 00", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("CANCELAR", fontWeight = FontWeight.Bold, color = Slate700)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
