package com.dimgym

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** False for a tab that is kept alive but hidden, so its back handlers stay quiet. */
val LocalActive = compositionLocalOf { true }

/** Space the floating primary button needs: 24 above it, 56 for it, 12 below. */
val ButtonZone = 92.dp

// ---------------------------------------------------------------------------------------------
// Press feedback: instant on touch-down, springs back on release
// ---------------------------------------------------------------------------------------------

/** The element dips on touch-down (fast spring in) and eases back out (slower spring). Drawn in the layer phase. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        motionSpec(1f, if (pressed) 0.18f else 0.32f),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Card and row highlight, shown from the moment of touch. */
@Composable
fun Modifier.pressHighlight(source: InteractionSource, color: Color): Modifier {
    val pressed by source.collectIsPressedAsState()
    return drawBehind { if (pressed) drawRect(color) }
}

// ---------------------------------------------------------------------------------------------
// Surfaces and buttons
// ---------------------------------------------------------------------------------------------

/** The one card style: filled surface, 20 dp corners, no border, no ripple. Tappable when onClick is given. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed && onClick != null) c.surfaceContainerHigh else c.surface, tween(90), label = "card")
    var m = modifier
    if (onClick != null) m = m.pressScale(src)
    m = m.clip(MaterialTheme.shapes.medium).drawBehind { drawRect(fill) }
    if (onClick != null) m = m.clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
    Column(m, content = content)
}

/** A grouped list container: rows share one rounded surface and are separated by inset hairlines. */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface), content = content)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Pill button. Filled with the accent when [prominent], quiet otherwise; the change between the two animates. */
@Composable
fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    prominent: Boolean = true, enabled: Boolean = true,
) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val container by animateColorAsState(if (prominent) c.primary else c.surfaceContainerHighest, tween(260), label = "btn")
    val label by animateColorAsState(if (prominent) c.onPrimary else c.onSurface, tween(260), label = "btnText")
    val dim by animateFloatAsState(if (pressed) 0.85f else 1f, tween(80), label = "btnDim")
    Box(
        modifier.heightIn(min = 56.dp).pressScale(src).alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape).drawBehind { drawRect(container, alpha = dim) }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
}

/** The one primary action per screen: full width, 56 dp. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, prominent: Boolean = true) =
    PillButton(text, onClick, modifier.fillMaxWidth(), prominent)

/** Plain accent text for sheet and bar actions. Dims on touch. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, bold: Boolean = false, enabled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.4f else if (enabled) 1f else 0.4f, tween(80), label = "text")
    Box(
        modifier.heightIn(min = 48.dp).graphicsLayer { alpha = a }
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = MaterialTheme.colorScheme.primary,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.heightIn(min = 40.dp).pressScale(src).clip(CircleShape).background(c.surfaceContainerHighest)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Switch(
        checked = checked,
        onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onCheckedChange(it) },
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.primary, checkedBorderColor = c.primary,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = c.outline, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Filled, borderless fields that take the accent on focus. */
@Composable
fun appFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)

// ---------------------------------------------------------------------------------------------
// Steppers: tap to step, hold to repeat
// ---------------------------------------------------------------------------------------------

/**
 * Round − / + button. Steps on release for a tap, and after a 450 ms hold keeps stepping every 110 ms.
 * Screen readers get a normal click action.
 */
@Composable
fun StepButton(symbol: String, description: String, onStep: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    var pressed by remember { mutableStateOf(false) }
    var repeated by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, motionSpec(1f, if (pressed) 0.18f else 0.32f), label = "step")
    Box(
        modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }.alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape).background(c.primary.copy(alpha = if (pressed) 0.28f else 0.16f))
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick { if (enabled) step(); true }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true; repeated = false
                        val job = scope.launch {
                            delay(450); repeated = true
                            while (isActive) { step(); delay(110) }
                        }
                        try { tryAwaitRelease() } finally { job.cancel(); pressed = false }
                    },
                    onTap = { if (!repeated) step() },
                )
            },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, color = c.primary, style = MaterialTheme.typography.titleLarge) }
}

/** "Sets  − 3 +": the number rolls up or down as it changes. */
@Composable
fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        StepButton("−", "Fewer $label", {
            if (value > range.first) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(value - 1) }
        }, enabled = value > range.first)
        AnimatedContent(
            targetState = value,
            modifier = Modifier.width(72.dp),
            transitionSpec = {
                val up = targetState > initialState
                val spec = appleSpring<IntOffset>(1f, 0.3f)
                if (reduce) fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                else (slideInVertically(spec) { if (up) it else -it } + fadeIn(tween(120))) togetherWith
                    (slideOutVertically(spec) { if (up) -it else it } + fadeOut(tween(120)))
            },
            label = "count",
        ) { v ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(v.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), textAlign = TextAlign.Center)
            }
        }
        StepButton("+", "More $label", {
            if (value < range.last) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(value + 1) }
        }, enabled = value < range.last)
    }
}

// ---------------------------------------------------------------------------------------------
// Sheet: replaces dialogs for forms. Drag down to dismiss; Cancel and the action animate out too.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    title: String, confirmLabel: String, confirmEnabled: Boolean,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.outline.copy(alpha = 0.5f)) },
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextAction("Cancel", { close() })
                Text(
                    title, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                TextAction(confirmLabel, { onConfirm(); close() }, bold = true, enabled = confirmEnabled)
            }
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Swipe to remove: 1:1 tracking, rubber-band on the closed side, momentum decides where it lands
// ---------------------------------------------------------------------------------------------

/**
 * Swipe left to reveal Delete; keep going past 60% to remove. The row follows the finger exactly, resists
 * when pulled the wrong way, and on release lands where its velocity is heading, continuing at that
 * velocity. Grab it mid-settle and it follows the finger from where it is. Screen readers get a "Remove" action.
 */
// add to imports: import kotlinx.coroutines.CoroutineStart

@Composable
// imports: kotlinx.coroutines.CoroutineStart (LaunchedEffect is already imported)

/** Swipe left to reveal a Delete button (stops there, no full-swipe delete). Closed from outside by setting [open] to false. */
fun SwipeToRemove(
    open: Boolean, onOpenChange: (Boolean) -> Unit, onRemove: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var raw by remember { mutableFloatStateOf(0f) }
    val actionPx = with(density) { 96.dp.toPx() }
    val spec = motionSpec<Float>(1f, 0.34f)
    val shape = MaterialTheme.shapes.medium

    // Closed from outside (another row opened, or the list scrolled): slide back
    LaunchedEffect(open) { if (!open) offset.animateTo(0f, spec) }

    val drag = rememberDraggableState { dx ->
        raw += dx
        val shown = when {
            raw > 0f -> rubberband(raw, 160f)                                   // wrong way: resists
            raw < -actionPx -> -(actionPx + rubberband(-raw - actionPx, 160f))  // past the button: resists
            else -> raw
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(shown) }
    }

    fun settle(v: Float) {
        scope.launch {
            if (offset.value + project(v, 0.99f) < -actionPx * 0.5f) {
                onOpenChange(true)
                offset.animateTo(-actionPx, spec, initialVelocity = v)
            } else {
                offset.animateTo(0f, spec, initialVelocity = v)
                onOpenChange(false)
            }
        }
    }

    Box(modifier) {
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = (-offset.value / 16f).coerceIn(0f, 1f) }
                .clip(shape).background(c.error)
                .clickable(role = Role.Button) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRemove() // instant: the list's own item animation fades the row out
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, null, tint = c.onError, modifier = Modifier.size(20.dp))
                Text("  Delete", color = c.onError, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Box(
            Modifier.graphicsLayer { translationX = offset.value }
                .semantics { customActions = listOf(CustomAccessibilityAction("Remove") { onRemove(); true }) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = drag,
                    onDragStarted = { offset.stop(); raw = offset.value },
                    onDragStopped = { v -> settle(v) },
                ),
        ) {
            content()
            // While open, a tap anywhere on the row closes it instead of opening the row
            if (open) {
                Box(
                    Modifier.matchParentSize().pointerInput(Unit) {
                        detectTapGestures { onOpenChange(false) }
                    },
                )
            }
        }
    }
}
// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

/** Fades content into the background behind a floating button, instead of a hard edge. */
@Composable
fun BottomFade(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = ButtonZone + 24.dp) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier.fillMaxWidth().height(height)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0f), bg.copy(alpha = 0.94f), bg))),
    )
}

/** Undo toast: floats above the primary button, inverted so it reads over anything. */
@Composable
fun UndoHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    SnackbarHost(state, modifier.padding(horizontal = 12.dp)) { data ->
        Snackbar(
            data, shape = RoundedCornerShape(16.dp),
            containerColor = c.inverseSurface, contentColor = c.inverseOnSurface, actionColor = c.inversePrimary,
        )
    }
}

/** Focuses the field when it first appears, so the keyboard opens straight away. */
@Composable
fun Modifier.autoFocus(): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return focusRequester(focus)
}

/** Says what is missing and offers the one action that fixes it. */
@Composable
fun EmptyState(title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        PillButton(action, onAction, prominent = true)
    }
}
