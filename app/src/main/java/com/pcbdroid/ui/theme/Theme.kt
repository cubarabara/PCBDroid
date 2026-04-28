package com.pcbdroid.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PcbDarkColorScheme = darkColorScheme(
    primary          = Color(0xFF00D4FF),
    onPrimary        = Color(0xFF001F29),
    primaryContainer = Color(0xFF003547),
    secondary        = Color(0xFF00FF88),
    onSecondary      = Color(0xFF00391E),
    tertiary         = Color(0xFFFFD740),
    error            = Color(0xFFFF5252),
    background       = Color(0xFF1A1A2E),
    onBackground     = Color(0xFFE0E0E0),
    surface          = Color(0xFF16213E),
    onSurface        = Color(0xFFE0E0E0),
    surfaceVariant   = Color(0xFF0F3460),
    outline          = Color(0xFF546E7A)
)

@Composable
fun PCBDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PcbDarkColorScheme,
        typography = Typography(
            titleLarge  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,      fontSize = 22.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp),
            titleSmall  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,    fontSize = 14.sp),
            bodyMedium  = TextStyle(fontFamily = FontFamily.Default,   fontWeight = FontWeight.Normal,    fontSize = 14.sp),
            bodySmall   = TextStyle(fontFamily = FontFamily.Default,   fontWeight = FontWeight.Normal,    fontSize = 12.sp),
            labelSmall  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,    fontSize = 10.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,    fontSize = 12.sp)
        ),
        content = content
    )
}
