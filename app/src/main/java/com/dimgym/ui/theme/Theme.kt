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
)

// Same palette, light. Neutral stone instead of cream, and an amber dark enough for white text (5:1).
private val Light = lightColorScheme(
    primary = Color(0xFF9A5F0C), onPrimary = Color(0xFFFFF8EC),
    primaryContainer = Color(0xFFF3DFB8), onPrimaryContainer = Color(0xFF3A2605),
    secondary = Color(0xFF9A5F0C), onSecondary = Color(0xFFFFF8EC),
    secondaryContainer = Color(0xFFF3DFB8), onSecondaryContainer = Color(0xFF3A2605),
    background = Color(0xFFF1F1EF), onBackground = Color(0xFF17171A),
    surface = Color(0xFFFAFAF9), onSurface = Color(0xFF17171A),
    surfaceVariant = Color(0xFFFAFAF9), onSurfaceVariant = Color(0xFF5F5F59),
    surfaceContainerLow = Color(0xFFF6F6F4), surfaceContainer = Color(0xFFFAFAF9),
    surfaceContainerHigh = Color(0xFFFCFCFB), surfaceContainerHighest = Color(0xFFE9E9E6),
    outline = Color(0xFF83837D),
    outlineVariant = Color(0xFFDEDEDA),
    inverseSurface = Color(0xFF26262B), inverseOnSurface = Color(0xFFF1F1EF), inversePrimary = Color(0xFFE3A857),
)

// Shape rule: buttons are pills (M3 default), inputs and chips 12, cards 16, dialogs 24.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DimGymTheme(dark: Boolean = true, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (dark) Dark else Light, shapes = AppShapes, content = content)