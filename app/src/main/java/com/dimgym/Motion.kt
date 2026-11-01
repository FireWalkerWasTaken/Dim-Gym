package com.dimgym

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs

/*
 * Motion language, taken from Apple's "Designing Fluid Interfaces":
 *  - everything a finger can touch is a spring, so it can be grabbed and redirected mid-flight
 *  - damping 1.0 (no overshoot) by default; bounce only where the gesture carried momentum
 *  - Apple's two knobs, damping ratio and response (seconds), are mapped to Compose's damping + stiffness
 *  - "Remove animations" in system settings swaps every spring for a short cross-fade
 */

/** True when the person has turned system animations off. Springs and slides become short fades. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduce = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduce
}

/** Apple-style spring. [response] is the time in seconds to reach the target (not a duration). stiffness = (2π / response)². */
fun <T> appleSpring(damping: Float = 1f, response: Float = 0.4f, threshold: T? = null): SpringSpec<T> {
    val omega = (2.0 * PI / response).toFloat()
    return spring(dampingRatio = damping, stiffness = omega * omega, visibilityThreshold = threshold)
}

/** [appleSpring], or a 140 ms fade-friendly tween when reduce motion is on. Use this everywhere in composition. */
@Composable
fun <T> motionSpec(damping: Float = 1f, response: Float = 0.4f): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) tween(140) else appleSpring(damping, response)

/**
 * Where a flick will come to rest, from its release velocity (px/s). Apple's exponential-decay form:
 * 0.998 for scroll-like throws, 0.99 for snappier decisions.
 */
fun project(velocity: Float, decelerationRate: Float = 0.998f): Float =
    (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

/** Progressive resistance past a boundary: the further you pull, the less it follows. */
fun rubberband(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float =
    (overshoot * dimension * constant) / (dimension + constant * abs(overshoot))

/**
 * Entrance for the first few rows of a screen: each rises and fades in 40 ms after the one before.
 * This is the app's one orchestrated moment (launch and finishing a workout). Off for reduce motion.
 */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    if (LocalReduceMotion.current || index > 8) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        p.animateTo(1f, appleSpring(1f, 0.5f))
    }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise
    }
}
