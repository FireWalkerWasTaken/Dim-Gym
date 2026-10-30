package com.dimgym

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimgym.data.Day
import com.dimgym.data.DimGymDb
import com.dimgym.data.Exercise
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.round

private fun fmt(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

/**
 * Today tab:
 * Apple Fitness & Health inspired activity tracker.
 * The workout view stays persistently mounted to prevent re-composition latency,
 * while exercise details open in a fluid, spring-animated Apple Sheet that glides up and down seamlessly.
 */
@Composable
fun TodayScreen(onOpenPlan: () -> Unit) {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val day by remember { dao.today() }.collectAsState(null)
    val days by remember { dao.days() }.collectAsState(null)
    val settings by remember { dao.settings() }.collectAsState(null)
    var open by remember { mutableStateOf<ExerciseRow?>(null) }
    val unit = settings?.unit ?: "kg"
    val list = days
    val d = day

    Box(Modifier.fillMaxSize()) {
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(
                title = "No Workout Cycle",
                body = "Build your training routine in the Plan tab to get started.",
                action = "Open Plan",
                onAction = onOpenPlan,
                modifier = Modifier.fillMaxSize(),
            )

            d != null -> AnimatedContent(
                targetState = d,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (fadeIn(tween(240)) + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 600f)) { it / 16 }) togetherWith fadeOut(tween(140))
                },
                contentKey = { it.id },
                label = "dayScreenTransition",
            ) { x ->
                DayScreen(
                    dao = dao,
                    day = x,
                    days = list,
                    unit = unit,
                    snack = snack,
                    scope = scope,
                    onOpenPlan = onOpenPlan,
                    onOpen = { open = it },
                )
            }
        }

        SnackbarHost(
            hostState = snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )

        // Apple-style Fluid Modal Inspector Sheet:
        // Opens instantly over the workout without tearing down the parent view,
        // delivering immediate 60/120fps fluid open & dismiss transitions.
        open?.let { row ->
            ExerciseDetailSheet(
                r = row,
                unit = unit,
                onDismiss = { open = null },
                onSave = { w, n ->
                    scope.launch { dao.update(Exercise(row.exerciseId, row.name, w, n)) }
                    open = null
                },
            )
        }
    }
}

@Composable
private fun DayScreen(
    dao: PlanDao,
    day: Day,
    days: List<Day>,
    unit: String,
    snack: SnackbarHostState,
    scope: CoroutineScope,
    onOpenPlan: () -> Unit,
    onOpen: (ExerciseRow) -> Unit,
) {
    val rows by remember(day.id) { dao.exercisesOf(day.id) }.collectAsState(null)
    var done by rememberSaveable(day.id) { mutableStateOf(emptyList<Long>()) }
    var menu by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val loaded = rows
    val count = loaded?.count { it.dayExerciseId in done } ?: 0
    val total = loaded?.size ?: 0
    val progress = if (total > 0) count.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Apple-style Header with Activity Ring Hero
        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(22.dp),
            containerColor = c.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (day.isRest) "REST DAY" else "TODAY'S WORKOUT",
                            color = c.primary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = day.name,
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            day.isRest -> "Recovery & Muscle Repair"
                            loaded.isNullOrEmpty() -> "No exercises scheduled"
                            count == total -> "All $total exercises completed!"
                            else -> "$count of $total completed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (count == total && total > 0) c.primary else c.onSurfaceVariant,
                    )
                }

                if (!day.isRest && total > 0) {
                    Spacer(Modifier.width(16.dp))
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(
                            progress = progress,
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 6.dp,
                            progressColor = c.primary,
                            trackColor = c.surfaceContainerHighest.copy(alpha = 0.5f),
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            ),
                            color = c.onSurface,
                        )
                    }
                }

                Box(modifier = Modifier.padding(start = 12.dp)) {
                    IconButton(
                        onClick = { menu = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.surfaceContainerHighest.copy(alpha = 0.6f))
                            .pressScale(),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = c.onSurfaceVariant),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Switch workout",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = c.surface,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Switch Day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = c.onSurfaceVariant,
                                )
                            },
                            onClick = {},
                            enabled = false,
                        )
                        days.forEachIndexed { i, x ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        x.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (x.id == day.id) FontWeight.Bold else FontWeight.Normal,
                                        ),
                                    )
                                },
                                trailingIcon = if (x.id == day.id) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = c.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else null,
                                onClick = {
                                    scope.launch { dao.setCycle(i) }
                                    menu = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // Workout Content
        when {
            day.isRest -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Rest & Recover",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Rest is when growth happens. Hydrate, stretch, and let your muscles rebuild for the next session.",
                            color = c.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            loaded == null -> Spacer(Modifier.weight(1f))
            loaded.isEmpty() -> EmptyState(
                title = "No Exercises Added",
                body = "Add what you plan to lift on this day in the Plan tab.",
                action = "Open Plan",
                onAction = onOpenPlan,
                modifier = Modifier.weight(1f),
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(loaded, key = { it.dayExerciseId }) { r ->
                    val isDone = r.dayExerciseId in done
                    val fade by animateFloatAsState(
                        targetValue = if (isDone) 0.5f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                        label = "doneFade",
                    )

                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onOpen(r) },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CheckDot(
                                checked = isDone,
                                name = r.name,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    done = if (isDone) done - r.dayExerciseId else done + r.dayExerciseId
                                },
                            )

                            Spacer(Modifier.width(12.dp))

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = fade },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = r.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = c.onSurface,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${r.sets} sets × ${r.reps} reps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.onSurfaceVariant,
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (r.weight != null) {
                                        Text(
                                            text = fmt(r.weight),
                                            color = c.primary,
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontFeatureSettings = "tnum",
                                            ),
                                        )
                                        Text(
                                            text = unit,
                                            color = c.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    } else {
                                        Text(
                                            text = "Bodyweight",
                                            color = c.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        PrimaryButton(
            text = if (day.isRest) "Complete Rest Day" else "Finish Workout",
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val prev = days.indexOfFirst { it.id == day.id }
                done = emptyList()
                scope.launch {
                    dao.advance()
                    snack.currentSnackbarData?.dismiss()
                    val result = snack.showSnackbar(
                        message = if (day.isRest) "Rest day marked done" else "Workout logged successfully!",
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        dao.setCycle(prev)
                    }
                }
            },
        )
    }
}

/**
 * Apple Reminders-style tactile circular checkbox.
 */
@Composable
private fun CheckDot(
    checked: Boolean,
    name: String,
    onToggle: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        targetValue = if (checked) c.primary else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "fillColor",
    )
    val ring by animateColorAsState(
        targetValue = if (checked) c.primary else c.outlineVariant.copy(alpha = 0.8f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "ringColor",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .semantics { contentDescription = "Mark $name done" }
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(fill, CircleShape)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = checked,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = c.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Apple-style Modal Exercise Inspector Sheet:
 * Opens with spring physics, lets users quickly adjust weight with tactile steppers or notes,
 * and dismisses with velocity-aware glide without unmounting the parent workout screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailSheet(
    r: ExerciseRow,
    unit: String,
    onDismiss: () -> Unit,
    onSave: (Double?, String) -> Unit,
) {
    var weight by remember(r.dayExerciseId) { mutableStateOf(r.weight?.let(::fmt) ?: "") }
    var notes by remember(r.dayExerciseId) { mutableStateOf(r.notes) }
    val step = if (unit == "kg") 2.5 else 5.0
    val haptic = LocalHapticFeedback.current
    val c = MaterialTheme.colorScheme

    fun nudge(by: Double) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val v = round(((weight.toDoubleOrNull() ?: 0.0) + by).coerceAtLeast(0.0) * 100) / 100
        weight = if (v <= 0.0) "" else fmt(v)
    }

    val commitAndDismiss = {
        onSave(weight.toDoubleOrNull(), notes)
    }

    AppleModalSheet(
        onDismissRequest = commitAndDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Column {
                Text(
                    text = r.name,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Target: ${r.sets} sets × ${r.reps} reps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurfaceVariant,
                )
            }

            // Weight Stepper Section
            InsetGroupedSection(title = "Weight ($unit)") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { nudge(-step) },
                        modifier = Modifier
                            .size(52.dp)
                            .pressScale(),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = c.surfaceContainerHighest,
                            contentColor = c.onSurface,
                        ),
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineMedium)
                    }

                    OutlinedTextField(
                        value = weight,
                        onValueChange = { v ->
                            val s = v.filter { it.isDigit() || it == '.' }
                            if (s.count { it == '.' } <= 1) weight = s
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            textAlign = TextAlign.Center,
                            fontFeatureSettings = "tnum",
                            fontWeight = FontWeight.Bold,
                            color = c.primary,
                        ),
                        placeholder = {
                            Text(
                                "0",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.headlineMedium,
                                color = c.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.primary,
                            unfocusedBorderColor = c.outlineVariant.copy(alpha = 0.5f),
                        ),
                    )

                    FilledTonalIconButton(
                        onClick = { nudge(step) },
                        modifier = Modifier
                            .size(52.dp)
                            .pressScale(),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = c.surfaceContainerHighest,
                            contentColor = c.onSurface,
                        ),
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }

            // Notes Section
            InsetGroupedSection(title = "Notes & Form Cues") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = {
                        Text(
                            "Add cues, seat height, grip width, or notes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .padding(6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton("Done", commitAndDismiss)

            Spacer(Modifier.height(16.dp))
        }
    }
}
