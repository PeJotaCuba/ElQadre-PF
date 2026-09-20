package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

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
 * Displays the official complete logo (logo.png) directly via Jetpack Compose Image.
 */
@Composable
fun ElQadreBrandHeader(
    logoSize: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "ElQadre Logo",
            modifier = Modifier
                .height(logoSize)
                .fillMaxWidth(),
            contentScale = ContentScale.Fit
        )
    }
}

