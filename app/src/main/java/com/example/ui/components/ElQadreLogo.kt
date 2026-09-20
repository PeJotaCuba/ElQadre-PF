package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.ElQadreGold

/**
 * Official ELQADRE Emblem Component
 * Displays the official native icon (icono.png) directly via Jetpack Compose Image.
 */
@Composable
fun ElQadreEmblem(
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(size * 0.28f),
        color = Color.Transparent,
        modifier = modifier.size(size)
    ) {
        Image(
            painter = painterResource(id = R.drawable.icono),
            contentDescription = "ElQadre Emblem",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

/**
 * Official ELQADRE Brand Header Component
 * Displays the official complete logo (logo.png) directly via Jetpack Compose Image
 * with the "PF" identifier under "Qadre" aligned to the right in official ElQadre gold.
 */
@Composable
fun ElQadreBrandHeader(
    logoSize: Dp = 80.dp,
    modifier: Modifier = Modifier,
    showPf: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(logoSize)
                .wrapContentWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "ElQadre Logo",
                modifier = Modifier
                    .height(logoSize)
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
            if (showPf) {
                Text(
                    text = "PF",
                    color = ElQadreGold,
                    fontSize = (logoSize.value * 0.085f).coerceIn(10f, 22f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = (logoSize.value * 0.12f).dp,
                            bottom = (logoSize.value * 0.02f).dp
                        )
                )
            }
        }
    }
}


