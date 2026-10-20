package com.dimgym

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dimgym.data.Day
import com.dimgym.data.DimGymDb
import com.dimgym.data.Exercise
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.round

private fun fmt(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

/** Today tab: today's workout with check-off, a finish button with undo, and a drill-in editor per exercise. */
@Composable
fun TodayScreen(onOpenPlan: () -> Unit) {
    val dao = DimGymDb.get(LocalContext.current).dao()
    // This scope lives as long as the tab, so saves and undo survive screen changes
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
        AnimatedContent(
            targetState = open,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { drill(forward = targetState != null) },
            contentKey = { it?.dayExerciseId },
            label = "today",
        ) { r ->
            when {
                r != null -> ExerciseDetail(r, unit, onBack = { open = null }) { w, n ->
                    scope.launch { dao.update(Exercise(r.exerciseId, r.name, w, n)) }
                }
                list == null -> Unit
                list.isEmpty() -> EmptyState(
                    "No workout yet", "Build your cycle in the Plan tab.", "Open Plan", onOpenPlan, Modifier.fillMaxSize(),
                )
                d != null -> AnimatedContent(
                    targetState = d,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 80)) + slideInVertically(tween(260)) { it / 16 }) togetherWith fadeOut(tween(120))
                    },
                    contentKey = { it.id },
                    label = "day",
                ) { x -> DayScreen(dao, x, list, unit, snack, scope, onOpenPlan) { open = it } }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp))
    }
}

@Composable
private fun DayScreen(
    dao: PlanDao, day: Day, days: List<Day>, unit: String,
    snack: SnackbarHostState, scope: CoroutineScope, onOpenPlan: () -> Unit, onOpen: (ExerciseRow) -> Unit,
) {
    val rows by remember(day.id) { dao.exercisesOf(day.id) }.collectAsState(null)
    // Ticks survive rotation and process death, and reset when the day is finished
    var done by rememberSaveable(day.id) { mutableStateOf(emptyList<Long>()) }
    var menu by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val loaded = rows
    val count = loaded?.count { it.dayExerciseId in done } ?: 0
    val progress by animateFloatAsState(if (loaded.isNullOrEmpty()) 0f else count / loaded.size.toFloat(), label = "progress")

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("TODAY", color = c.primary, style = MaterialTheme.typography.labelSmall)
                Text(day.name, style = MaterialTheme.typography.headlineLarge)
                Text(
                    when {
                        day.isRest -> "Rest day"
                        loaded.isNullOrEmpty() -> "No exercises yet"
                        else -> "$count of ${loaded.size} done"
                    },
                    color = c.onSurfaceVariant,
                )
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Switch workout") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Switch to", color = c.onSurfaceVariant) }, onClick = {}, enabled = false)
                    days.forEachIndexed { i, x ->
                        DropdownMenuItem(
                            text = { Text(x.name) },
                            trailingIcon = if (x.id == day.id) ({ Icon(Icons.Default.Check, "Today's workout") }) else null,
                            onClick = { scope.launch { dao.setCycle(i) }; menu = false },
                        )
                    }
                }
            }
        }

        if (!day.isRest && !loaded.isNullOrEmpty()) {
            Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(c.outlineVariant)) {
                // Scaled from the left edge, so only a transform animates
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { scaleX = progress; transformOrigin = TransformOrigin(0f, 0.5f) }
                        .background(c.primary),
                )
            }
        }

        when {
            day.isRest -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Rest day", style = MaterialTheme.typography.headlineMedium)
                    Text("Recovery is part of the plan. Nothing to log today.", color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            loaded == null -> Spacer(Modifier.weight(1f))
            loaded.isEmpty() -> EmptyState(
                "No exercises yet", "Add some to this day in the Plan tab.", "Open Plan", onOpenPlan, Modifier.weight(1f),
            )
            else -> LazyColumn(
                Modifier.weight(1f).padding(top = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(loaded, key = { it.dayExerciseId }) { r ->
                    val isDone = r.dayExerciseId in done
                    val fade by animateFloatAsState(if (isDone) 0.5f else 1f, label = "fade")
                    AppCard(Modifier.fillMaxWidth().animateItem(), onClick = { onOpen(r) }) {
                        Row(Modifier.padding(end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckDot(isDone, r.name) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                done = if (isDone) done - r.dayExerciseId else done + r.dayExerciseId
                            }
                            Row(
                                Modifier.weight(1f).graphicsLayer { alpha = fade }.padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${r.sets} × ${r.reps}", color = c.onSurfaceVariant)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (r.weight != null) {
                                        Text(
                                            fmt(r.weight), color = c.primary,
                                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                        )
                                        Text(unit, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    } else {
                                        Text("No weight", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        PrimaryButton(if (day.isRest) "Finish rest day" else "Finish workout", onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val prev = days.indexOfFirst { it.id == day.id }
            done = emptyList()
            scope.launch {
                dao.advance()
                snack.currentSnackbarData?.dismiss()
                val result = snack.showSnackbar(
                    if (day.isRest) "Rest day done" else "Workout done", actionLabel = "Undo", duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) dao.setCycle(prev)
            }
        })
    }
}

/** Round check target (56dp touch area, 26dp visual). Fills with the accent and pops a tick in. */
@Composable
private fun CheckDot(checked: Boolean, name: String, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) c.primary else Color.Transparent, label = "fill")
    val ring by animateColorAsState(if (checked) c.primary else c.outline, label = "ring")
    Box(
        Modifier.size(56.dp)
            .semantics { contentDescription = "Mark $name done" }
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).background(fill, CircleShape).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = checked,
                enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) { Icon(Icons.Default.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}

/** Weight with quick steppers (gloves, one hand) and notes. Leaving the screen by any route saves. */
@Composable
private fun ExerciseDetail(r: ExerciseRow, unit: String, onBack: () -> Unit, onSave: (Double?, String) -> Unit) {
    var weight by remember { mutableStateOf(r.weight?.let(::fmt) ?: "") }
    var notes by remember { mutableStateOf(r.notes) }
    val step = if (unit == "kg") 2.5 else 5.0
    val haptic = LocalHapticFeedback.current
    val leave = { onSave(weight.toDoubleOrNull(), notes); onBack() }
    BackHandler(enabled = LocalActive.current, onBack = leave)

    fun nudge(by: Double) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val v = round(((weight.toDoubleOrNull() ?: 0.0) + by).coerceAtLeast(0.0) * 100) / 100
        weight = if (v <= 0.0) "" else fmt(v)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(20.dp)) {
        IconButton(leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(r.name, style = MaterialTheme.typography.headlineLarge)
                Text("${r.sets} × ${r.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Weight ($unit)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton({ nudge(-step) }, Modifier.size(56.dp)) { Text("−", style = MaterialTheme.typography.titleLarge) }
                OutlinedTextField(
                    weight,
                    { v ->
                        val s = v.filter { it.isDigit() || it == '.' }
                        if (s.count { it == '.' } <= 1) weight = s // one dot only, so a typo cannot save as "no weight"
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    placeholder = { Text("None", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                FilledTonalIconButton({ nudge(step) }, Modifier.size(56.dp)) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
            OutlinedTextField(
                notes, { notes = it }, label = { Text("My notes (form, cues)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
        }
        PrimaryButton("Save", leave, Modifier.padding(top = 12.dp))
    }
}