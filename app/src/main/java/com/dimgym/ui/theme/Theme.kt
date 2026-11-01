package com.dimgym.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Every token the M3 widgets read is set, so no default purple leaks into sheets, menus or dialogs.
private val Dark = darkColorScheme(
    primary = InkAccent, onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF3A2C12), onPrimaryContainer = Color(0xFFF2D9AE),
    secondary = InkAccent, onSecondary = Color(0xFF1A1205),
    secondaryContainer = Color(0xFF3A2C12), onSecondaryContainer = Color(0xFFF2D9AE),
    tertiary = InkAccent, onTertiary = Color(0xFF1A1205),
    background = InkBackground, onBackground = InkText,
    surface = InkSurface, onSurface = InkText,
    surfaceVariant = InkSurface, onSurfaceVariant = InkTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = InkBackground, surfaceContainerLow = Color(0xFF131316), surfaceContainer = InkSurface,
    surfaceContainerHigh = InkSurfaceHigh, surfaceContainerHighest = InkSurfaceHighest,
    outline = InkOutline, outlineVariant = InkHairline,
    error = InkError, onError = Color(0xFF1A0705),
    inverseSurface = InkText, inverseOnSurface = Color(0xFF1A1A1C), inversePrimary = Color(0xFF9A5F0C),
    scrim = Color.Black,
)

private val Light = lightColorScheme(
    primary = PaperAccent, onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFF3DFB8), onPrimaryContainer = Color(0xFF3A2605),
    secondary = PaperAccent, onSecondary = Color(0xFFFFF8EC),
    secondaryContainer = Color(0xFFF3DFB8), onSecondaryContainer = Color(0xFF3A2605),
    tertiary = PaperAccent, onTertiary = Color(0xFFFFF8EC),
    background = PaperBackground, onBackground = PaperText,
    surface = PaperSurface, onSurface = PaperText,
    surfaceVariant = PaperSurface, onSurfaceVariant = PaperTextSecondary,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = PaperSurface, surfaceContainerLow = Color(0xFFF7F7F5), surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceHigh, surfaceContainerHighest = PaperSurfaceHighest,
    outline = PaperOutline, outlineVariant = PaperHairline,
    error = PaperError, onError = Color.White,
    inverseSurface = Color(0xFF26262B), inverseOnSurface = PaperBackground, inversePrimary = InkAccent,
    scrim = Color.Black,
)

// Rounder than before and consistent: inputs and chips 12, cards 20, sheets and dialogs 28.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DimGymTheme(dark: Boolean = true, content: @Composable () -> Unit) =
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography, // was missing before, so Type.kt was never applied
        shapes = AppShapes,
        content = content,
    )
