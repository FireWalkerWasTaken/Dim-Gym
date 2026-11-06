package com.dimgym
import androidx.compose.animation.VectorConverter
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
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

/**
 * Theme change as a crossfade. In normal frames the app is recorded into [old] and drawn from it. When [dark]
 * flips, the live content gets the new colours underneath while [old], still holding the last frame in the old
 * colours, sits on top as its own layer and fades out. The fade only changes that layer's alpha (a layer property,
 * read in the layer block), so the app itself is not redrawn or recomposed while it runs.
 */
val LocalThemeFade = compositionLocalOf { ThemeFade() }

/** Coordinates a theme switch: freeze the current frame once, flip the theme at once, then fade the frozen frame out. */
@Stable
class ThemeFade {
    /** Theme applied instantly, before the DB write comes back. Cleared once settings catch up. */
    var pending by mutableStateOf<String?>(null)
    internal var flip by mutableIntStateOf(0)
    internal var capture by mutableStateOf(false)
    internal var holding by mutableStateOf(false)
    internal var recorded = false

    fun effective(saved: String) = pending ?: saved

    suspend fun switchTo(theme: String, persist: suspend () -> Unit) {
        if (capture || holding) return            // ignore taps for the 200 ms the fade runs
        recorded = false
        capture = true
        repeat(3) { if (!recorded) withFrameNanos { } } // wait until the draw pass has recorded the old frame
        capture = false
        if (recorded) holding = true
        pending = theme                            // new colours from this frame on
        flip++
        persist()
    }
}

@Composable
fun ThemeCrossfade(fade: ThemeFade, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val old = rememberGraphicsLayer()
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                if (fade.capture) {                 // only the one frame at the moment of selection
                    old.record { this@drawWithContent.drawContent() }
                    fade.recorded = true
                    drawLayer(old)
                } else drawContent()                // every other frame: no extra work at all
            },
        ) { content() }
        FrozenFrame(fade, old)
    }
}

/** Own composable so only it recomposes when the fade starts or ends, not the app. */
@Composable
private fun FrozenFrame(fade: ThemeFade, old: GraphicsLayer) {
    val alpha = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(fade.flip) {
        if (!fade.holding) return@LaunchedEffect
        alpha.snapTo(1f)
        withFrameNanos { }                          // let the heavy first frame in the new theme pass unseen
        alpha.animateTo(0f, tween(if (reduce) 120 else 200, easing = FastOutSlowInEasing))
        fade.holding = false
        alpha.snapTo(1f)
    }
    if (fade.holding) {
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value }.drawBehind { drawLayer(old) })
    }
}

/**
 * Drop-in for animateColorAsState on theme colours: animates normally for state changes (pressed, checked),
 * but snaps when the theme itself changed, so nothing animates beneath the crossfade.
 */
@Composable
fun animateThemedColor(target: Color, spec: AnimationSpec<Color> = tween(200)): State<Color> {
    val theme = MaterialTheme.colorScheme.background // only changes when the theme does
    val anim = remember { Animatable(target, Color.VectorConverter(target.colorSpace)) }
    val seen = remember { arrayOf(theme) }
    LaunchedEffect(target, theme) {
        if (seen[0] != theme) { seen[0] = theme; anim.snapTo(target) } else anim.animateTo(target, spec)
    }
    return anim.asState()
}
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
fun <T> motionSpec(damping: Float = 1f, response: Float = 0.4f, threshold: T? = null): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) tween(140) else appleSpring(damping, response, threshold)

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