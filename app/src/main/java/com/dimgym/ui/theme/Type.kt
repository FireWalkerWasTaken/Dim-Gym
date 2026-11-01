package com.dimgym.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System font (it already ships optical sizing and tuning). Sizes follow Apple's scale: 17 body, 34 large title.
// Tracking is size-specific: big text is tightened, small text is left alone. Leading shrinks as size grows.
// All sizes are sp, so they follow the system text-size setting.
val AppTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp), // buttons
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp), // eyebrow
)
