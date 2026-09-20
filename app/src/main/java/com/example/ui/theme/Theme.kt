package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = ElQadreNavy,
    onPrimary = Color.White,
    primaryContainer = ElQadreNavyLight,
    onPrimaryContainer = Color.White,
    secondary = ElQadreGold,
    onSecondary = ElQadreNavy,
    secondaryContainer = ElQadreGoldSoft,
    onSecondaryContainer = ElQadreNavy,
    background = ElQadreBackground,
    onBackground = ElQadreNavy,
    surface = ElQadreSurface,
    onSurface = ElQadreNavy,
    surfaceVariant = ElQadreSurfaceAlt,
    onSurfaceVariant = Slate600,
    outline = ElQadreBorder
)

@Composable
fun ElQadreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                it.statusBarColor = ElQadreNavy.toArgb()
                it.navigationBarColor = Color.White.toArgb()
                val insetsController = WindowCompat.getInsetsController(it, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
