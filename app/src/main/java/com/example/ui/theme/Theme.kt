package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SonoraDarkColorScheme = darkColorScheme(
    primary = WarmCoral,
    onPrimary = Color(0xFF140704),
    primaryContainer = Color(0xFF3E1911),
    onPrimaryContainer = Color(0xFFFFD6CC),
    secondary = ElectricCyan,
    onSecondary = Color(0xFF041C1A),
    secondaryContainer = Color(0xFF103431),
    onSecondaryContainer = Color(0xFFB3FFF7),
    tertiary = WarmAmber,
    onTertiary = Color(0xFF231600),
    tertiaryContainer = Color(0xFF3A280A),
    onTertiaryContainer = Color(0xFFFFE1B0),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle,
    error = SoftRose,
    onError = Color(0xFF29050C),
)

private val SonoraLightColorScheme = lightColorScheme(
    primary = WarmCoralDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0D8),
    onPrimaryContainer = Color(0xFF471005),
    secondary = Color(0xFF008B80),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F7F3),
    onSecondaryContainer = Color(0xFF00322E),
    tertiary = Color(0xFFB86B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8C4),
    onTertiaryContainer = Color(0xFF381E00),
    background = WarmPaperBackground,
    onBackground = TextPrimaryLight,
    surface = WarmPaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = WarmPaperSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLow = Color(0xFFFDFCFA),
    surfaceContainer = WarmPaperSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE7E2D8),
    outline = WarmPaperOutline,
    outlineVariant = Color(0xFFEAE5DC),
    error = Color(0xFFC82848),
    onError = Color.White,
)

val SonoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SonoraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SonoraDarkColorScheme else SonoraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SonoraTypography,
        shapes = SonoraShapes,
        content = content
    )
}
