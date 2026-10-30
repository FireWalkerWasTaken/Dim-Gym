package com.dimgym

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** False for a tab that is kept alive but hidden, so its back handlers stay quiet. */
val LocalActive = compositionLocalOf { true }

/**
 * Tactile press physics: the element compresses to 97% on finger-down and springs back on release.
 * Derived from Apple's fluid interface principles:
 * - Damping ratio 0.8 (responsive settle, zero sluggishness)
 * - Stiffness 600 (instant tracking with finger)
 */
@Composable
fun Modifier.pressScale(source: InteractionSource = remember { MutableInteractionSource() }): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Apple-style Inset Grouped Card:
 * Smooth corner radius (18dp), subtle hairline border for contrast separation,
 * and immediate spring-based tactile press feedback when clickable.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(0.75.dp, c.outlineVariant.copy(alpha = 0.5f))
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.pressScale(src),
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            interactionSource = src,
            content = content,
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content,
        )
    }
}

/**
 * Apple-style Prominent Primary Action Button:
 * Full-width, pill-shaped (28dp corners), rich background with high-contrast text,
 * spring-based tactile interaction, and optional leading icon.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val src = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .pressScale(src),
        enabled = enabled,
        shape = RoundedCornerShape(27.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        interactionSource = src,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * Secondary Action Button:
 * Frosted subtle tone with pill border.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val c = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(src),
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.surfaceContainerHighest.copy(alpha = 0.6f),
            contentColor = c.onSurface,
        ),
        interactionSource = src,
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Drill in / out screen navigation transition:
 * Authentic iOS-style spring navigation push/pop physics.
 * - Forward: New screen slides in from right (100% width), exiting screen moves left by 30% parallax.
 * - Backward: Returning screen slides forward from -30% parallax, exiting screen slides off to right (100%).
 * Critically damped spring (dampingRatio = 0.9f, stiffness = 600f) ensures instant responsiveness.
 */
fun <S> AnimatedContentTransitionScope<S>.drill(forward: Boolean): ContentTransform {
    val springSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 600f)
    val fadeSpec = tween<Float>(durationMillis = 200)

    return if (forward) {
        (slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = springSpec,
            initialOffset = { it },
        ) + fadeIn(fadeSpec)) togetherWith
        (slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = springSpec,
            targetOffset = { -it / 3 },
        ) + fadeOut(fadeSpec))
    } else {
        (slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = springSpec,
            initialOffset = { -it / 3 },
        ) + fadeIn(fadeSpec)) togetherWith
        (slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = springSpec,
            targetOffset = { it },
        ) + fadeOut(fadeSpec))
    }
}

/** Focuses the text field immediately upon presentation. */
@Composable
fun Modifier.autoFocus(): Modifier {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    return focusRequester(focus)
}

/**
 * Empty State view:
 * Minimalist typography, subtle iconography, and direct recovery action.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = onAction,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.pressScale(),
        ) {
            Text(action, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Apple Fitness-style Circular Activity Progress Ring:
 * Displays training completion with spring-accelerated smooth stroke, rounded cap,
 * and high-contrast track background.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    progressColor: Color = MaterialTheme.colorScheme.primary,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "progressRing",
    )

    Canvas(modifier = modifier) {
        val sw = strokeWidth.toPx()
        val pad = sw / 2f
        val arcSize = Size(size.width - sw, size.height - sw)
        val topLeft = Offset(pad, pad)

        // Track ring (full 360)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )

        // Progress sweep (spring animated)
        if (animatedProgress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Apple-style Segmented Control:
 * Physical sliding pill indicator that follows selection with smooth spring dynamics,
 * haptic click on toggle, and inset grouped appearance.
 */
@Composable
fun AppleSegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelFormatter: (String) -> String = { it },
) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(3.dp)
            .selectableGroup(),
    ) {
        val totalWidth = maxWidth
        val tabWidth = totalWidth / options.size.coerceAtLeast(1)
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 550f),
            label = "segmentedIndicator",
        )

        // Floating pill indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface)
                .border(0.5.dp, c.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
        )

        // Segment labels
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(option)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelFormatter(option),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (isSelected) c.onSurface else c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * iOS Inset Grouped Section:
 * Muted header text, rounded group card with subtle 0.5dp separators between rows.
 */
@Composable
fun InsetGroupedSection(
    title: String? = null,
    caption: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        AppCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            containerColor = c.surface,
        ) {
            content()
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
            )
        }
    }
}

/**
 * Inset Table Row item with optional divider.
 */
@Composable
fun InsetRow(
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
    content: @Composable RowScope_Alias.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            content()
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
        }
    }
}

typealias RowScope_Alias = androidx.compose.foundation.layout.RowScope

/**
 * Apple-style Modal Bottom Sheet:
 * Top grabber handle, rounded 24dp top corners, backdrop dimming, and smooth dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = c.surface,
        tonalElevation = 4.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(c.outlineVariant.copy(alpha = 0.7f)),
            )
        },
        modifier = modifier,
        content = content,
    )
}
