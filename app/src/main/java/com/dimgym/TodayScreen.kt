package com.dimgym

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import kotlin.math.max
import kotlin.math.round

private fun fmt(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

/**
 * Today tab: today's workout with a progress ring, check-off, a finish button with undo, and a drill-in
 * editor per exercise. The editor is a page that slides over this screen and can be swiped back.
 */
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
    // The editor's draft lives here, so the swipe-back and the Back button both save the same values
    var weightText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    val unit = settings?.unit ?: "kg"
    val list = days
    val d = day
    val barInset = LocalBarInset.current

    fun openRow(r: ExerciseRow) {
        weightText = r.weight?.let(::fmt) ?: ""
        notesText = r.notes
        open = r
    }

    fun closeRow() {
        val r = open ?: return
        val w = weightText.toDoubleOrNull()
        val n = notesText
        scope.launch { dao.update(Exercise(r.exerciseId, r.name, w, n)) } // saved the moment you leave, by any route
        open = null
    }

    Box(Modifier.fillMaxSize()) {
        DrillHost(
            detail = open,
            onPop = { closeRow() },
            root = {
                when {
                    list == null -> Unit
                    list.isEmpty() -> EmptyState(
                        "No workout yet", "Build your cycle in the Plan tab.", "Open Plan", onOpenPlan,
                        Modifier.fillMaxSize().padding(bottom = barInset),
                    )
                    d != null -> AnimatedContent(
                        targetState = d,
                        modifier = Modifier.fillMaxSize(),
                        // Quick cross-fade; the rows then rise in one after another (staggerIn)
                        transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(120)) },
                        contentKey = { it.id },
                        label = "day",
                    ) { x -> DayScreen(dao, x, list, unit, snack, scope, onOpenPlan, ::openRow) }
                }
            },
            page = { r ->
                ExerciseDetail(
                    r, unit, weightText, { weightText = it }, notesText, { notesText = it },
                    onBack = { closeRow() },
                )
            },
        )
        UndoHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + ButtonZone))
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
    val barInset = LocalBarInset.current
    val loaded = rows
    val total = loaded?.size ?: 0
    val count = loaded?.count { it.dayExerciseId in done } ?: 0
    val allDone = total > 0 && count == total
    val listState = rememberLazyListState()
    val collapse = rememberCollapse(listState)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = navBarHeight(), bottom = barInset + ButtonZone),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                LargeTitle(
                    title = day.name,
                    eyebrow = "Today",
                    subtitle = when {
                        day.isRest -> "Rest day"
                        loaded.isNullOrEmpty() -> "No exercises yet"
                        else -> "$count of $total done"
                    },
                    modifier = Modifier.staggerIn(0),
                    trailing = if (!day.isRest && total > 0) ({ ProgressRing(count, total) }) else null,
                )
            }
            when {
                day.isRest -> item(key = "rest") {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp).staggerIn(1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rest day", style = MaterialTheme.typography.headlineMedium)
                        Text("Recovery is part of the plan. Nothing to log today.", color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
                loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState("No exercises yet", "Add some to this day in the Plan tab.", "Open Plan", onOpenPlan, Modifier.padding(top = 24.dp))
                }
                else -> itemsIndexed(loaded, key = { _, r -> r.dayExerciseId }) { i, r ->
                    val isDone = r.dayExerciseId in done
                    val fade by animateFloatAsState(if (isDone) 0.5f else 1f, motionSpec(1f, 0.4f), label = "fade")
                    AppCard(Modifier.fillMaxWidth().animateItem().staggerIn(i + 1), onClick = { onOpen(r) }) {
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
                                    Text("${r.sets} × ${r.reps}", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    if (r.weight != null) {
                                        Text(
                                            fmt(r.weight), color = c.primary,
                                            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                                        )
                                        Text(unit, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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

        BottomFade(Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
        // Quiet until every exercise is ticked, then it fills with the accent: the screen's one call to action
        PrimaryButton(
            if (day.isRest) "Finish rest day" else "Finish workout",
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = barInset + 12.dp),
            prominent = allDone || day.isRest,
            onClick = {
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
            },
        )

        NavBar(
            title = day.name,
            collapse = { collapse.value },
            trailing = {
                Box {
                    NavIconButton(Icons.Default.MoreVert, "Switch workout") { menu = true }
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
            },
        )
    }
}

/**
 * The screen's signature: a ring that closes as exercises are ticked. It sweeps on a spring (so ticking
 * fast just retargets it) and gives one small bounce when the last exercise is done.
 */
@Composable
private fun ProgressRing(count: Int, total: Int) {
    val c = MaterialTheme.colorScheme
    val complete = total > 0 && count == total
    val sweep by animateFloatAsState(if (total == 0) 0f else count / total.toFloat(), motionSpec(1f, 0.55f), label = "ring")
    val pulse = remember { Animatable(1f) }
    var was by remember { mutableStateOf(complete) }
    val settle = motionSpec<Float>(0.5f, 0.35f) // the one bounce: a celebration, so it earns overshoot
    LaunchedEffect(complete) {
        if (complete && !was) {
            pulse.animateTo(1.14f, tween(90))
            pulse.animateTo(1f, settle)
        }
        was = complete
    }
    val track = c.outlineVariant
    val accent = c.primary
    Box(
        Modifier.size(64.dp).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clearAndSetSemantics { contentDescription = "$count of $total done" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val w = 6.dp.toPx()
            val arc = Size(size.width - w, size.height - w)
            val at = Offset(w / 2, w / 2)
            drawArc(track, 0f, 360f, false, at, arc, style = Stroke(w))
            if (sweep > 0.001f) drawArc(accent, -90f, 360f * sweep, false, at, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("$count/$total", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"))
    }
}

/** Round check target (56dp touch area, 26dp visual). Fills with the accent and pops a tick in. */
@Composable
private fun CheckDot(checked: Boolean, name: String, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by animateThemedColor(if (checked) c.primary else Color.Transparent, tween(160))
    val ring by animateThemedColor(if (checked) c.primary else c.outline, tween(160))
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
                enter = scaleIn(motionSpec(0.7f, 0.3f), initialScale = 0.3f) + fadeIn(tween(100)),
                exit = scaleOut(tween(100)) + fadeOut(tween(100)),
            ) { Icon(Icons.Default.Check, null, tint = c.onPrimary, modifier = Modifier.size(16.dp)) }
        }
    }
}

/** Weight with quick steppers (gloves, one hand, hold to repeat) and notes. */
@Composable
private fun ExerciseDetail(
    r: ExerciseRow, unit: String,
    weight: String, onWeight: (String) -> Unit,
    notes: String, onNotes: (String) -> Unit,
    onBack: () -> Unit,
) {
    val step = if (unit == "kg") 2.5 else 5.0
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val barInset = LocalBarInset.current
    val ime = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val bottom = max(ime.value, barInset.value).dp // the keyboard covers the tab bar, so do not stack them
    val scroll = rememberScrollState()
    val collapse = rememberCollapse(scroll)

    fun nudge(by: Double) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val v = round(((weight.toDoubleOrNull() ?: 0.0) + by).coerceAtLeast(0.0) * 100) / 100
        onWeight(if (v <= 0.0) "" else fmt(v))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(top = navBarHeight(), bottom = bottom + ButtonZone)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LargeTitle(r.name, subtitle = "${r.sets} × ${r.reps}")
            Text("Weight ($unit)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", "Lower weight by $step", { nudge(-step) })
                OutlinedTextField(
                    weight,
                    { v ->
                        val s = v.filter { it.isDigit() || it == '.' }
                        if (s.count { it == '.' } <= 1) onWeight(s) // one dot only, so a typo cannot save as "no weight"
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                    placeholder = { Text("None", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    singleLine = true,
                    colors = appFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                StepButton("+", "Raise weight by $step", { nudge(step) })
            }
            OutlinedTextField(
                notes, onNotes, label = { Text("My notes (form, cues)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                colors = appFieldColors(),
            )
        }
        BottomFade(Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
        PrimaryButton("Save", onBack, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = bottom + 12.dp))
        NavBar(title = r.name, collapse = { collapse.value }, leading = { BackButton("Today", onBack) })
    }
}
