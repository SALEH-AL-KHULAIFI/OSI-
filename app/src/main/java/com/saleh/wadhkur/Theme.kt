package com.saleh.wadhkur

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    MaterialTheme(colorScheme = colors, content = content)
}
