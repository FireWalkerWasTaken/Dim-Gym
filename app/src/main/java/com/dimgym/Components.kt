package com.dimgym

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** False for a tab that is kept alive but hidden, so its back handlers stay quiet. */
val LocalActive = compositionLocalOf { true }

/** Tactile press: the element dips to 97% while held and springs back. Reads the scale in the draw phase. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** The one card style: flat surface, hairline border, 16dp corners. Tappable when onClick is given. */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val colors = CardDefaults.cardColors(containerColor = c.surface)
    val border = BorderStroke(1.dp, c.outlineVariant)
    val flat = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val m = modifier.pressScale(src)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, colors = colors, elevation = flat, border = border, interactionSource = src, content = content)
    } else {
        Card(modifier = modifier, colors = colors, elevation = flat, border = border, content = content)
    }
}

/** The one primary action per screen: full width, 56dp, presses in. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp).pressScale(src),
        interactionSource = src,
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Drill in: the new screen slides a short way from the right while fading. Back is the reverse. */
fun <S> AnimatedContentTransitionScope<S>.drill(forward: Boolean): ContentTransform {
    val dir = if (forward) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
    val move = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    return (slideIntoContainer(dir, move, initialOffset = { it / 5 }) + fadeIn(tween(300))) togetherWith
            (slideOutOfContainer(dir, move, targetOffset = { it / 5 }) + fadeOut(tween(160)))
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
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}