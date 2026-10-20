package com.dimgym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dimgym.data.Day
import com.dimgym.data.DayExercise
import com.dimgym.data.DimGymDb
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.launch

/** Plan tab: the list of days, or the editor for one day, with a slide between them. */
@Composable
fun PlanScreen() {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val scope = rememberCoroutineScope() // outlives the editor, so a delete always finishes
    val days by remember { dao.days() }.collectAsState(null)
    val today by remember { dao.today() }.collectAsState(null)
    var open by remember { mutableStateOf<Day?>(null) }
    AnimatedContent(
        targetState = open,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { drill(forward = targetState != null) },
        contentKey = { it?.id },
        label = "plan",
    ) { day ->
        if (day == null) DayList(dao, days, today?.id) { open = it }
        else DayEditor(dao, day, onBack = { open = null }, onDelete = { scope.launch { dao.delete(day) }; open = null })
    }
}

@Composable
private fun DayList(dao: PlanDao, days: List<Day>?, todayId: Long?, onOpen: (Day) -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("Plan", style = MaterialTheme.typography.headlineLarge)
        Text("Your cycle repeats in this order.", color = c.onSurfaceVariant)
        when {
            days == null -> Spacer(Modifier.weight(1f))
            days.isEmpty() -> EmptyState(
                "No days yet", "A cycle is the list of workouts you repeat. Start with your first day.",
                "Add day", { adding = true }, Modifier.weight(1f),
            )
            else -> {
                LazyColumn(
                    Modifier.weight(1f).padding(top = 16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(days, key = { _, d -> d.id }) { i, d ->
                        AppCard(Modifier.fillMaxWidth().animateItem(), onClick = { onOpen(d) }) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${i + 1}", color = c.onSurfaceVariant, modifier = Modifier.width(28.dp),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleLarge)
                                    if (d.isRest) Text("Rest day", color = c.onSurfaceVariant)
                                }
                                if (d.id == todayId) {
                                    Text(
                                        "Today", color = c.onPrimaryContainer, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.background(c.primaryContainer, MaterialTheme.shapes.extraSmall)
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                PrimaryButton("Add day", { adding = true })
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var rest by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add day") },
            text = {
                Column {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Name, e.g. Push A") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().autoFocus(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rest, { rest = it })
                        Text("Rest day")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { dao.save(Day(name = name.trim(), position = (days?.maxOfOrNull { it.position } ?: -1) + 1, isRest = rest)) }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayEditor(dao: PlanDao, day: Day, onBack: () -> Unit, onDelete: () -> Unit) {
    BackHandler(enabled = LocalActive.current, onBack = onBack)
    val scope = rememberCoroutineScope()
    val rows by remember(day.id) { dao.exercisesOf(day.id) }.collectAsState(null)
    val names by remember { dao.exerciseNames() }.collectAsState(emptyList())
    val snack = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val loaded = rows
    var editing by remember { mutableStateOf<Long?>(null) }
    val editRow = loaded?.firstOrNull { it.dayExerciseId == editing }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // While a drag is on, the list shows this working order; otherwise it shows the database order
    var shown by remember { mutableStateOf(emptyList<ExerciseRow>()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val list = (if (draggingId != null) shown else loaded).orEmpty()

    /** Accessibility route (and tests): moves one exercise up (-1) or down (+1) without dragging. */
    fun move(id: Long, by: Int) {
        val cur = rows ?: return
        val from = cur.indexOfFirst { it.dayExerciseId == id }
        val to = from + by
        if (from < 0 || to !in cur.indices) return
        val ids = cur.map { it.dayExerciseId }.toMutableList().apply { add(to, removeAt(from)) }
        scope.launch { dao.reorder(ids) }
    }

    fun dragStart(id: Long) {
        val cur = rows
        if (draggingId != null || cur == null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        shown = cur
        dragOffset = 0f
        draggingId = id
    }

    fun dragBy(id: Long, dy: Float) {
        if (draggingId != id) return
        dragOffset += dy
        val info = listState.layoutInfo
        val cur = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        // After a swap, wait for the list to lay it out before looking for the next one
        if (shown.indexOfFirst { it.dayExerciseId == id } != cur.index) return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size }
        if (target != null) {
            val gap = with(density) { 10.dp.toPx() } // the list's item spacing
            val newOffset = if (target.index > cur.index) cur.offset + target.size + gap else target.offset.toFloat()
            dragOffset += cur.offset - newOffset // keep the card under the finger after it changes slot
            shown = shown.toMutableList().apply { add(target.index, removeAt(cur.index)) }
        }
        // Near the top or bottom edge: scroll the list and keep the card under the finger
        val edge = with(density) { 64.dp.toPx() }
        val step = with(density) { 14.dp.toPx() }
        val top = cur.offset + dragOffset
        val push = when {
            top < info.viewportStartOffset + edge -> -step
            top + cur.size > info.viewportEndOffset - edge -> step
            else -> 0f
        }
        if (push != 0f) scope.launch { dragOffset += listState.scrollBy(push) }
    }

    fun dragEnd(id: Long) {
        if (draggingId != id) return
        val ids = shown.map { it.dayExerciseId }
        scope.launch {
            if (ids != rows?.map { it.dayExerciseId }) dao.reorder(ids)
            animate(dragOffset, 0f, animationSpec = spring(stiffness = 600f)) { v, _ -> dragOffset = v } // settle into the slot
            draggingId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(day.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete day") }
            }
            if (!day.isRest && !loaded.isNullOrEmpty()) {
                Text("Drag the handle to reorder. Tap to edit sets and reps.", color = c.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
            }
            when {
                day.isRest -> Text("Rest day. Nothing to add here.", color = c.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp).weight(1f))
                loaded == null -> Spacer(Modifier.weight(1f))
                loaded.isEmpty() -> EmptyState(
                    "No exercises yet", "Add what you do on this day. Each exercise remembers its weight.",
                    "Add exercise", { adding = true }, Modifier.weight(1f),
                )
                else -> {
                    LazyColumn(
                        Modifier.weight(1f).padding(top = 16.dp),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(list, key = { _, r -> r.dayExerciseId }) { i, r ->
                            val dragging = r.dayExerciseId == draggingId
                            val lift by animateFloatAsState(if (dragging) 1.03f else 1f, label = "lift")
                            AppCard(
                                Modifier.fillMaxWidth()
                                    .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem()) // the dragged card follows the finger, the rest glide
                                    .graphicsLayer { if (dragging) translationY = dragOffset; scaleX = lift; scaleY = lift },
                                onClick = { editing = r.dayExerciseId },
                            ) {
                                Row(Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    DragHandle(
                                        r.name, onMove = { move(r.dayExerciseId, it) },
                                        modifier = Modifier.pointerInput(r.dayExerciseId) {
                                            detectVerticalDragGestures(
                                                onDragStart = { dragStart(r.dayExerciseId) },
                                                onDragEnd = { dragEnd(r.dayExerciseId) },
                                                onDragCancel = { dragEnd(r.dayExerciseId) },
                                                onVerticalDrag = { change, dy -> change.consume(); dragBy(r.dayExerciseId, dy) },
                                            )
                                        },
                                    )
                                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${r.sets} × ${r.reps}", color = c.onSurfaceVariant)
                                    }
                                    IconButton({
                                        val item = DayExercise(r.dayExerciseId, day.id, r.exerciseId, i, r.sets, r.reps)
                                        scope.launch {
                                            dao.delete(item)
                                            snack.currentSnackbarData?.dismiss()
                                            if (snack.showSnackbar("Removed ${r.name}", actionLabel = "Undo") == SnackbarResult.ActionPerformed) dao.save(item)
                                        }
                                    }) { Icon(Icons.Default.Delete, "Remove ${r.name}") }
                                }
                            }
                        }
                    }
                    PrimaryButton("Add exercise", { adding = true })
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp))
    }

    if (editRow != null) {
        val idx = loaded?.indexOfFirst { it.dayExerciseId == editRow.dayExerciseId } ?: 0
        EditExerciseDialog(
            editRow,
            onSave = { s, p ->
                scope.launch { dao.save(DayExercise(editRow.dayExerciseId, day.id, editRow.exerciseId, idx, s, p)) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${day.name}?") },
            text = { Text("It leaves your cycle and its exercise list is removed. Your weights and notes are kept.") },
            confirmButton = {
                TextButton({ confirmDelete = false; onDelete() }) { Text("Delete", color = c.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10") }
        val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
        val typed = name.trim()
        // Existing exercises matching what is typed: picking one keeps its weight shared
        val hits = names.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(6)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        name, { name = it }, label = { Text("Exercise") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth().autoFocus(),
                    )
                    if (hits.isNotEmpty()) {
                        Text("Use an existing exercise", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hits) { h -> SuggestionChip(onClick = { name = h }, label = { Text(h) }) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(sets, { sets = it.filter(Char::isDigit).take(2) }, label = { Text("Sets") },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                        OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, label = { Text("Reps") },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                val s = sets.toIntOrNull() ?: 0
                val r = reps.toIntOrNull() ?: 0
                TextButton(enabled = name.isNotBlank() && s > 0 && r > 0, onClick = {
                    scope.launch {
                        val id = dao.exerciseId(name) // reuses an existing exercise, so its weight stays shared
                        dao.save(DayExercise(dayId = day.id, exerciseId = id, position = loaded?.size ?: 0, sets = s, reps = r))
                    }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}

/** Two parallel lines on the left of a card: press and drag to reorder. Screen readers get Move up / Move down instead. */
@Composable
private fun DragHandle(name: String, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier.size(48.dp).semantics {
            contentDescription = "Reorder $name"
            customActions = listOf(
                CustomAccessibilityAction("Move up") { onMove(-1); true },
                CustomAccessibilityAction("Move down") { onMove(1); true },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(2) { Box(Modifier.size(20.dp, 2.dp).background(c.onSurfaceVariant, RoundedCornerShape(1.dp))) }
        }
    }
}

/** Tap an exercise: change its sets and reps. */
@Composable
private fun EditExerciseDialog(r: ExerciseRow, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var sets by remember(r.dayExerciseId) { mutableStateOf(r.sets.toString()) }
    var reps by remember(r.dayExerciseId) { mutableStateOf(r.reps.toString()) }
    val s = sets.toIntOrNull() ?: 0
    val p = reps.toIntOrNull() ?: 0
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(r.name) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sets, { sets = it.filter(Char::isDigit).take(2) }, label = { Text("Sets") },
                    singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, label = { Text("Reps") },
                    singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
            }
        },
        confirmButton = { TextButton(enabled = s > 0 && p > 0, onClick = { onSave(s, p) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}