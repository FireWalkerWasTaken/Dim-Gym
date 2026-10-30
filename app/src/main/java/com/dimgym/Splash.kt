package com.dimgym

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Opening screen:
 * Premium dark background with fluid spring physics on the logo entrance.
 */
@Composable
fun SplashScreen() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 300f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0F))
            .pointerInput(Unit) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.dimgym_logo),
                contentDescription = "DimGym",
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        alpha = t.value
                        scaleX = 0.88f + 0.12f * t.value
                        scaleY = 0.88f + 0.12f * t.value
                    },
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "DIMGYM",
                color = Color(0xFFECE7DF),
                style = MaterialTheme.typography.titleMedium.copy(
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.graphicsLayer {
                    val w = ((t.value - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    alpha = w
                    translationY = (1f - w) * 12f
                },
            )
        }
    }
}
