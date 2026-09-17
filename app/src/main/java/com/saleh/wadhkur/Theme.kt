package com.saleh.wadhkur

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object WadhkurColors {
    val background = Color(0xFF071116)
    val surface = Color(0xFF0E1D25)
    val surface2 = Color(0xFF142833)
    val edge = Color(0xFF2DD4BF)
    val primary = Color(0xFF5EEAD4)
    val text = Color(0xFFF3F7F7)
    val muted = Color(0xFF9FB5BA)
}

@Composable
fun WadhkurTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = WadhkurColors.primary,
        background = WadhkurColors.background,
        surface = WadhkurColors.surface,
        surfaceVariant = WadhkurColors.surface2,
        onPrimary = Color.Black,
        onBackground = WadhkurColors.text,
        onSurface = WadhkurColors.text,
        onSurfaceVariant = WadhkurColors.muted
    )
    val typography = Typography().let { type ->
        type.copy(
            displayLarge = type.displayLarge.copy(fontFamily = FontFamily.Serif),
            headlineLarge = type.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = type.headlineMedium.copy(fontFamily = FontFamily.Serif),
            titleLarge = type.titleLarge.copy(fontFamily = FontFamily.Serif),
            bodyLarge = type.bodyLarge.copy(fontFamily = FontFamily.Serif),
            bodyMedium = type.bodyMedium.copy(fontFamily = FontFamily.Serif),
            labelLarge = type.labelLarge.copy(fontFamily = FontFamily.Serif)
        )
    }
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
