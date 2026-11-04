package com.shreenathji.sales.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ShadcnLightColorScheme = lightColorScheme(
    primary = Zinc900,
    onPrimary = ShadcnWhite,
    primaryContainer = Zinc100,
    onPrimaryContainer = Zinc900,
    secondary = Zinc700,
    onSecondary = ShadcnWhite,
    secondaryContainer = Zinc100,
    onSecondaryContainer = Zinc800,
    background = Zinc50,
    onBackground = Zinc950,
    surface = ShadcnWhite,
    onSurface = Zinc950,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc500,
    outline = ShadcnBorder,
    outlineVariant = Zinc200
)

@Composable
fun ShreenathjiSalesTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.White.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = ShadcnLightColorScheme,
        typography = Typography,
        content = content
    )
}
