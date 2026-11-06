package com.dimgym

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.onSizeChanged

val NavBarContent = 44.dp
val TabBarContent = 56.dp

/** Height at the bottom that content must clear: the tab bar plus the system navigation bar. */
val LocalBarInset = compositionLocalOf { 0.dp }

/** Status bar plus the 44 dp navigation bar. Scrolling content starts below this and slides under it. */
@Composable
fun navBarHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + NavBarContent

// ---------------------------------------------------------------------------------------------
// Large title that collapses into the bar as you scroll
// ---------------------------------------------------------------------------------------------

/** 0 while the large title is showing, 1 once it has scrolled away. Read it inside draw/layer lambdas. */
@Composable
fun rememberCollapse(state: LazyListState, distance: Dp = 40.dp): State<Float> {
    val px = with(LocalDensity.current) { distance.toPx() }
    return remember(state, px) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f)
        }
    }
}

@Composable
fun rememberCollapse(state: ScrollState, distance: Dp = 40.dp): State<Float> {
    val px = with(LocalDensity.current) { distance.toPx() }
    return remember(state, px) { derivedStateOf { (state.value / px).coerceIn(0f, 1f) } }
}

/**
 * The bar that floats over scrolling content. It is clear at rest; as the large title collapses it
 * gains a translucent background, a hairline, and the small centred title. Nothing here recomposes
 * while scrolling: [collapse] is only read in the draw and layer phases.
 */
@Composable
fun NavBar(
    title: String,
    collapse: () -> Float,
    modifier: Modifier = Modifier,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    val bg = c.background
    val hairline = c.outlineVariant
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier.fillMaxWidth().drawBehind {
            val p = collapse()
            drawRect(bg, alpha = 0.92f * p)
            drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx(), alpha = p)
        }.padding(top = top).height(NavBarContent),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp)
                .graphicsLayer { alpha = collapse() }
                .clearAndSetSemantics { }, // the large title already carries it for screen readers
        )
        if (leading != null) Row(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically, content = leading)
        if (trailing != null) Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

/** The big title under the bar. Horizontal padding comes from the list or column that holds it. */
@Composable
fun LargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Text(eyebrow.uppercase(), color = c.primary, style = MaterialTheme.typography.labelSmall)
            Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        if (trailing != null) trailing()
    }
}

/** Plain, accent-tinted icon button for the bar. Dims the instant it is touched. */
@Composable
fun NavIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.4f else 1f, tween(80), label = "navbtn")
    Box(
        Modifier.size(48.dp).graphicsLayer { alpha = a }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = MaterialTheme.colorScheme.primary) }
}

/** "‹ Plan": chevron plus the name of the screen you came from. */
@Composable
fun BackButton(label: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.4f else 1f, tween(80), label = "back")
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.heightIn(min = 48.dp).graphicsLayer { alpha = a }
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = primary, modifier = Modifier.size(32.dp))
        Text(label, color = primary, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------------------------

data class TabItem(val label: String, val icon: ImageVector)

/**
 * Translucent bar with content scrolling under it (Android has no backdrop blur, so this is a 90% surface
 * with a hairline top edge). The selection pill slides to the new tab on a critically damped spring.
 */
@Composable
fun TabBar(items: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val frac by animateFloatAsState(selected.toFloat(), motionSpec(1f, 0.36f), label = "tabpill")
    val hairline = c.outlineVariant
    Box(
        modifier.fillMaxWidth()
            .background(c.surface.copy(alpha = 0.9f))
            .drawBehind { drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(TabBarContent).selectableGroup()) {
            val itemWidth = maxWidth / items.size
            Box(
                Modifier.width(itemWidth).fillMaxHeight()
                    .graphicsLayer { translationX = frac * itemWidth.toPx() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .background(c.primary.copy(alpha = 0.14f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { i, item ->
                    val on = i == selected
                    val src = remember { MutableInteractionSource() }
                    val tint by animateThemedColor(if (on) c.primary else c.onSurfaceVariant, tween(200))
                    Column(
                        Modifier.weight(1f).fillMaxHeight().pressScale(src, 0.92f)
                            .selectable(selected = on, interactionSource = src, indication = null, role = Role.Tab, onClick = { onSelect(i) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(item.label, color = tint, style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Drill-in navigation: the page follows your finger, the screen behind it moves less and dims
// ---------------------------------------------------------------------------------------------

/**
 * Root screen plus one page pushed on top of it.
 *  - Opening and closing are springs driven by one progress value, so they can be interrupted: tap again
 *    mid-slide and it reverses from where it is, with the velocity it had.
 *  - On Android 14+ with android:enableOnBackInvokedCallback="true", the back-swipe drives that progress
 *    directly, so the page tracks the finger and springs back if you let go early.
 *  - The root stays composed underneath (scroll position is kept) and parallaxes 28% as far as the page.
 *  - With reduce motion on, the page cross-fades in place.
 */
@Composable
fun <T : Any> DrillHost(
    detail: T?,
    onPop: () -> Unit,
    modifier: Modifier = Modifier,
    root: @Composable () -> Unit,
    page: @Composable (T) -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<T?>(null) } // kept after detail clears so the page can slide out
    var widthPx by remember { mutableFloatStateOf(0f) }
    // 0.0005 cutoff: the default 0.01 would end the spring ~10 px early and pop the page into place
    val spec = motionSpec<Float>(1f, 0.4f, 0.0005f)

    LaunchedEffect(detail) {
        if (detail != null) {
            shown = detail
            // The page is composed this frame, off-screen. Let it lay out and draw once before moving,
            // so its first-composition cost never lands inside the animation.
            withFrameNanos { }
            withFrameNanos { }
            progress.animateTo(1f, spec)
        } else {
            progress.animateTo(0f, spec)
            shown = null
        }
    }

    PredictiveBackHandler(enabled = detail != null && LocalActive.current) { events ->
        try {
            events.collect { progress.snapTo(1f - it.progress) }
            onPop() // committed: the effect above springs the rest of the way from where the finger left it
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(1f, spec) } // let go early: spring back
        }
    }

    Box(modifier.fillMaxSize().onSizeChanged { widthPx = it.width.toFloat() }) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { if (!reduce) translationX = -progress.value * widthPx * 0.28f },
        ) { root() }

        val item = detail ?: shown // composed the moment you tap, not a frame later
        if (item != null) {
            // Dims the root, and swallows touches on it while the page is open
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = progress.value * 0.28f }
                    .background(Color.Black)
                    .then(if (detail != null) Modifier.pointerInput(Unit) {} else Modifier),
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        if (reduce) alpha = progress.value else translationX = (1f - progress.value) * widthPx
                    }
                    .background(c.background)
                    .pointerInput(Unit) {},
            ) { page(item) }
        }
    }
}
