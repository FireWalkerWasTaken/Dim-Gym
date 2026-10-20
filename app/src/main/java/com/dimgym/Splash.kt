package com.dimgym

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Opening screen. Always dark (it is the brand), whatever theme the app uses. Add res/drawable/dimgym_logo.png. */
@Composable
fun SplashScreen() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0D0D0F)).pointerInput(Unit) {}, // swallows taps while it fades out
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.dimgym_logo), contentDescription = "DimGym",
                modifier = Modifier.size(240.dp).graphicsLayer {
                    alpha = t.value
                    scaleX = 0.9f + 0.1f * t.value
                    scaleY = 0.9f + 0.1f * t.value
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "DIMGYM", color = Color(0xFFECE7DF),
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 6.sp),
                modifier = Modifier.graphicsLayer {
                    val w = ((t.value - 0.4f) / 0.6f).coerceIn(0f, 1f) // starts at 40% of the logo's ease
                    alpha = w
                    translationY = (1f - w) * 16f
                },
            )
        }
    }
}