package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * =====================================================================
 * SISTEMA DE COMPONENTES VISUALES COMUNES DE ELQADRE (SESIÓN DUEÑO)
 * =====================================================================
 *
 * Principios aplicados:
 * 1. Jerarquía visual clara: Títulos destacados, cifras en gran formato.
 * 2. Tarjetas limpias e independientes con esquinas redondeadas modernas (16-20dp).
 * 3. Separación estricta entre datos, estados y acciones.
 * 4. Cantidades físicas y montos monetarios visualmente diferenciados.
 * 5. Accesibilidad para personas mayores: tamaños táctiles mínimos de 48dp,
 *    alto contraste cromático y legibilidad tipográfica.
 * 6. Preservación absoluta de la funcionalidad: NO modifica textos funcionales,
 *    cálculos, datos ni navegación.
 */

// =====================================================================
// 1. BADGES E INDICADORES DE ESTADO (PILLS)
// =====================================================================

enum class ElQadreBadgeStyle {
    SUCCESS,  // Verde / Ganancia / Activo / Abierta
    WARNING,  // Ámbar / Alerta / Atención
    DANGER,   // Rojo / Pérdida / Merma / Inactivo
    INFO,     // Azul / Informativo / En Proceso
    GOLD,     // Dorado de la marca / Destacado / Principal
    NEUTRAL   // Gris / Secundario
}

/**
 * Indicador tipo pastilla (Pill Badge) para estados, porcentajes o categorías.
 */
@Composable
fun ElQadrePillBadge(
    text: String,
    style: ElQadreBadgeStyle = ElQadreBadgeStyle.NEUTRAL,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (style) {
        ElQadreBadgeStyle.SUCCESS -> Emerald50 to Emerald700
        ElQadreBadgeStyle.WARNING -> Amber50 to Amber700
        ElQadreBadgeStyle.DANGER -> Rose50 to Rose700
        ElQadreBadgeStyle.INFO -> Sky50 to Sky700
        ElQadreBadgeStyle.GOLD -> ElQadreGoldSoft to ElQadreNavy
        ElQadreBadgeStyle.NEUTRAL -> Slate100 to Slate600
    }

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(100.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(13.dp)
                        .padding(end = 4.dp)
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// =====================================================================
// 2. CONTENEDORES Y TARJETAS LIMPIAS (CARDS)
// =====================================================================

/**
 * Tarjeta base limpia con bordes redondeados y contorno sutil.
 */
@Composable
fun ElQadreCard(
    modifier: Modifier = Modifier,
    containerColor: Color = ElQadreSurface,
    borderColor: Color = ElQadreBorder,
    borderWidth: Dp = 1.dp,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Tarjeta con banda lateral de acento visual para jerarquía y agrupamiento.
 */
@Composable
fun ElQadreAccentCard(
    accentColor: Color = ElQadreGold,
    accentWidth: Dp = 4.dp,
    containerColor: Color = ElQadreSurface,
    borderColor: Color = ElQadreBorder,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = shape,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(accentWidth)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(contentPadding),
                content = content
            )
        }
    }
}

// =====================================================================
// 3. ENCABEZADOS DE SECCIÓN (SECTION HEADERS)
// =====================================================================

/**
 * Encabezado de sección con icono en pastilla redondeada, título destacado
 * y espacio opcional para acciones secundarias o badges.
 */
@Composable
fun ElQadreSectionHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconAccentColor: Color = ElQadreNavy,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = iconAccentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconAccentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = ElQadreNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingAction()
        }
    }
}

// =====================================================================
// 4. BLOQUES DE CIFRAS E INDICADORES (HERO STAT CARDS)
// =====================================================================

/**
 * Bloque de cifras de alto impacto visual para valores monetarios o inventario
 * (Idéntico al patrón visual de la referencia: Grandes números, etiqueta clara, badge).
 */
@Composable
fun ElQadreHeroStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    badgeText: String? = null,
    badgeStyle: ElQadreBadgeStyle = ElQadreBadgeStyle.SUCCESS,
    accentColor: Color = ElQadreGold,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = ElQadreSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, ElQadreBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Fila superior: Etiqueta y Badge/Icono
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        fontSize = 11.sp
                    ),
                    color = Slate500
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!badgeText.isNullOrBlank()) {
                        ElQadrePillBadge(text = badgeText, style = badgeStyle)
                    }
                    if (icon != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Cifra principal destacada
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = ElQadreNavy
            )

            // Subtexto / Detalle secundario
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Slate500
                )
            }
        }
    }
}

// =====================================================================
// 5. FILAS DE DATOS Y DESGLOSE (DATA ROWS)
// =====================================================================

/**
 * Fila de datos para desgloses tabulares o listas detalladas.
 * Separa visualmente el nombre del ítem, su cantidad física y su valor monetario.
 */
@Composable
fun ElQadreDataRow(
    title: String,
    subtitle: String? = null,
    quantity: String? = null,
    value: String? = null,
    valueColor: Color = ElQadreNavy,
    icon: ImageVector? = null,
    iconTint: Color = Slate400,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Concepto y subtítulo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 8.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = ElQadreNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Cantidad física (diferenciada) y Monto monetario
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!quantity.isNullOrBlank()) {
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = quantity,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = Slate700,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = valueColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// =====================================================================
// 6. BOTONES ACCESIBLES Y JERARQUIZADOS (BUTTONS)
// =====================================================================

/**
 * Botón Principal ElQadre (Navy profundo con texto blanco, 48dp+ altura para accesibilidad).
 */
@Composable
fun ElQadrePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    testTag: String? = null
) {
    val buttonModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElQadreNavy,
            contentColor = Color.White,
            disabledContainerColor = Slate200,
            disabledContentColor = Slate400
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        modifier = buttonModifier
            .defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * Botón Acento ElQadre (Dorado brillante con texto Navy, ideal para llamadas a la acción clave).
 */
@Composable
fun ElQadreAccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    testTag: String? = null
) {
    val buttonModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ElQadreGold,
            contentColor = ElQadreNavy,
            disabledContainerColor = Slate200,
            disabledContentColor = Slate400
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        modifier = buttonModifier
            .defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElQadreNavy,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

/**
 * Botón Secundario ElQadre (Borde nítido, fondo transparente/blanco).
 */
@Composable
fun ElQadreSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = ElQadreNavy,
    borderColor: Color = ElQadreBorder,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    testTag: String? = null
) {
    val buttonModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, if (enabled) borderColor else Slate200),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = Slate400
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        modifier = buttonModifier
            .defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

// =====================================================================
// 7. PESTAÑAS SEGMENTADAS (SEGMENTED PILL TABS)
// =====================================================================

/**
 * Selector de pestañas segmentadas en pastillas limpias y ergonómicas.
 * Diseñado según la referencia visual (Insumos | Productos | Tandas).
 */
@Composable
fun <T> ElQadreSegmentedTabs(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    activeColor: Color = ElQadreNavy,
    activeContentColor: Color = Color.White
) {
    Surface(
        color = Slate100,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val targetBg = if (isSelected) activeColor else Color.Transparent
                val targetText = if (isSelected) activeContentColor else Slate600

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(targetBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onItemSelected(item) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = targetText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =====================================================================
// 8. CAMPOS DE ENTRADA LIMPIOS Y ACCESIBLES (TEXT FIELDS)
// =====================================================================

/**
 * Campo de texto con estilo unificado, esquinas redondeadas y tipografía accesible.
 */
@Composable
fun ElQadreTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val fieldModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Column(modifier = fieldModifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (placeholder != null) { { Text(placeholder, color = Slate400) } } else null,
            leadingIcon = if (leadingIcon != null) {
                {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = if (isError) Rose500 else Slate500,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else null,
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            enabled = enabled,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElQadreNavy,
                unfocusedBorderColor = ElQadreBorder,
                focusedLabelColor = ElQadreNavy,
                unfocusedLabelColor = Slate500,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = Slate50
            ),
            modifier = Modifier.fillMaxWidth()
        )

        if (isError && !errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                color = Rose600,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// =====================================================================
// 9. CUADRO INFORMATIVO / SUPERPUESTO (INFO CALLOUT)
// =====================================================================

/**
 * Cuadro informativo o destacado con icono y fondo suave (e.g. "Rendimiento", avisos).
 */
@Composable
fun ElQadreInfoCallout(
    text: String,
    title: String? = null,
    icon: ImageVector = Icons.Default.Info,
    style: ElQadreBadgeStyle = ElQadreBadgeStyle.INFO,
    modifier: Modifier = Modifier
) {
    val (bgColor, accentColor) = when (style) {
        ElQadreBadgeStyle.SUCCESS -> Emerald50 to Emerald700
        ElQadreBadgeStyle.WARNING -> Amber50 to Amber700
        ElQadreBadgeStyle.DANGER -> Rose50 to Rose700
        ElQadreBadgeStyle.INFO -> Sky50 to Sky700
        ElQadreBadgeStyle.GOLD -> ElQadreGoldSoft to ElQadreGoldDark
        ElQadreBadgeStyle.NEUTRAL -> Slate100 to Slate600
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = ElQadreNavy
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Slate700
                )
            }
        }
    }
}

// =====================================================================
// 10. ESTADO VACÍO (EMPTY STATE)
// =====================================================================

/**
 * Componente visual para estados sin registros o datos.
 */
@Composable
fun ElQadreEmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Slate100,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Slate400,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = ElQadreNavy,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            color = Slate500,
            textAlign = TextAlign.Center
        )
    }
}
