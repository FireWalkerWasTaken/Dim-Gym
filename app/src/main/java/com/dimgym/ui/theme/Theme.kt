package com.dimgym.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// One accent (amber) on neutral stone. Every token the M3 widgets read is set here,
// so no default purple tint leaks into chips, dialogs or snackbars.
private val Dark = darkColorScheme(
    primary = Color(0xFFE3A857), onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF3A2C12), onPrimaryContainer = Color(0xFFF2D9AE),
    secondary = Color(0xFFE3A857), onSecondary = Color(0xFF1A1205),
    secondaryContainer = Color(0xFF3A2C12), onSecondaryContainer = Color(0xFFF2D9AE),
    background = Color(0xFF0D0D0F), onBackground = Color(0xFFECE7DF),
    surface = Color(0xFF17171A), onSurface = Color(0xFFECE7DF),
    surfaceVariant = Color(0xFF17171A), onSurfaceVariant = Color(0xFF9A958B),
    surfaceContainerLow = Color(0xFF131316), surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF1F1F23), surfaceContainerHighest = Color(0xFF26262B),
    outline = Color(0xFF6F6B64), // input borders, 3:1 against the background
    outlineVariant = Color(0xFF26262B), // hairlines and card borders
    inverseSurface = Color(0xFFECE7DF), inverseOnSurface = Color(0xFF1A1A1C), inversePrimary = Color(0xFF9A5F0C),
    error = Color(0xFFFF6961), onError = Color(0xFF1A0505),
)

// Same palette, light. Neutral stone background, white cards, amber dark enough for white text (5:1).
private val Light = lightColorScheme(
    primary = Color(0xFF9A5F0C), onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFF3DFB8), onPrimaryContainer = Color(0xFF3A2605),
    secondary = Color(0xFF9A5F0C), onSecondary = Color(0xFFFFF8EC),
    secondaryContainer = Color(0xFFF3DFB8), onSecondaryContainer = Color(0xFF3A2605),
    background = Color(0xFFF2F2F0), onBackground = Color(0xFF17171A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF5F5F59),
    surfaceContainerLow = Color(0xFFF6F6F4), surfaceContainer = Color(0xFFFAFAF9),
    surfaceContainerHigh = Color(0xFFF5F5F3), surfaceContainerHighest = Color(0xFFEBEBE8),
    outline = Color(0xFF83837D),
    outlineVariant = Color(0xFFDEDEDA),
    inverseSurface = Color(0xFF26262B), inverseOnSurface = Color(0xFFF1F1EF), inversePrimary = Color(0xFFE3A857),
    error = Color(0xFFD32F2F), onError = Color(0xFFFFFFFF),
)

// Cards 16dp, inputs 12dp, sheets 28dp. Buttons use M3 default pill (fully round).
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DimGymTheme(dark: Boolean = true, content: @Composable () -> Unit) =
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
