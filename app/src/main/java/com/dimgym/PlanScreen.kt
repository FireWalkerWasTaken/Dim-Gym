package com.dimgym

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dimgym.data.Day
import com.dimgym.data.DayExercise
import com.dimgym.data.DimGymDb
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.launch

/** Plan tab: the list of days, or the editor for one day, which slides over the list and can be swiped back. */
@Composable
fun PlanScreen() {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val scope = rememberCoroutineScope() // outlives the editor, so a delete always finishes
    val days by remember { dao.days() }.collectAsState(null)
    val today by remember { dao.today() }.collectAsState(null)
    var open by remember { mutableStateOf<Day?>(null) }
    // Warm cache: every day's exercises stay loaded (and live) before you tap into one
    val cache = remember { mutableStateMapOf<Long, List<ExerciseRow>>() }
    LaunchedEffect(days) { days?.forEach { d -> launch { dao.exercisesOf(d.id).collect { cache[d.id] = it } } } }
    DrillHost(
        detail = open,
        onPop = { open = null },
        root = { DayList(dao, days, today?.id) { open = it } },
        page = { day ->
            DayEditor(dao, day, cache[day.id], onBack = { open = null }, onDelete = { scope.launch { dao.delete(day) }; open = null })
        },
    )
}

@Composable
private fun DayList(dao: PlanDao, days: List<Day>?, todayId: Long?, onOpen: (Day) -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val barInset = LocalBarInset.current
    val listState = rememberLazyListState()
    val collapse = rememberCollapse(listState)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = navBarHeight(), bottom = barInset + ButtonZone),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") { LargeTitle("Plan", subtitle = "Your cycle repeats in this order.") }
            when {
                days == null -> Unit
                days.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        "No days yet", "A cycle is the list of workouts you repeat. Start with your first day.",
                        "Add day", { adding = true }, Modifier.padding(top = 48.dp),
                    )
                }
                else -> itemsIndexed(days, key = { _, d -> d.id }) { i, d ->
                    AppCard(Modifier.fillMaxWidth().animateItem(), onClick = { onOpen(d) }) {
                        Row(Modifier.heightIn(min = 64.dp).padding(start = 18.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${i + 1}", color = c.onSurfaceVariant, modifier = Modifier.width(28.dp),
                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                            )
                            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                                Text(d.name, style = MaterialTheme.typography.titleLarge)
                                if (d.isRest) Text("Rest day", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (d.id == todayId) {
                                Text(
                                    "Today", color = c.onPrimaryContainer, style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.background(c.primaryContainer, MaterialTheme.shapes.extraSmall)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (!days.isNullOrEmpty()) {
            BottomFade(Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
            PrimaryButton("Add day", { adding = true }, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = barInset + 12.dp))
        }
        NavBar(title = "Plan", collapse = { collapse.value })
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var rest by remember { mutableStateOf(false) }
        AppSheet(
            title = "Add day", confirmLabel = "Add", confirmEnabled = name.isNotBlank(),
            onConfirm = {
                scope.launch { dao.save(Day(name = name.trim(), position = (days?.maxOfOrNull { it.position } ?: -1) + 1, isRest = rest)) }
            },
            onDismiss = { adding = false },
        ) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Name, e.g. Push A") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = appFieldColors(),
                modifier = Modifier.fillMaxWidth().autoFocus(),
            )
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rest day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                AppSwitch(rest, { rest = it })
            }
        }
    }
}

/** Header is the only item before the exercises; the drag code below shifts list indices by this. */
private const val HEAD = 1

@Composable
private fun DayEditor(dao: PlanDao, day: Day, rows: List<ExerciseRow>?, onBack: () -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val loaded = rows
    var editing by remember { mutableStateOf<Long?>(null) }
    val editRow = loaded?.firstOrNull { it.dayExerciseId == editing }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val barInset = LocalBarInset.current
    val topPad = navBarHeight()
    val listState = rememberLazyListState()
    val collapse = rememberCollapse(listState)
    val cardShape = MaterialTheme.shapes.medium
    val latest by rememberUpdatedState(rows) // the long-lived drag gesture must not hold a stale list
    var openId by remember { mutableStateOf<Long?>(null) } // the one row showing its Delete button
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) openId = null }
    // While a drag is on, the list shows this working order; otherwise it shows the database order
    var shown by remember { mutableStateOf(emptyList<ExerciseRow>()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val list = (if (draggingId != null) shown else loaded).orEmpty()

    /** Accessibility route (and tests): moves one exercise up (-1) or down (+1) without dragging. */
    fun move(id: Long, by: Int) {
        val cur = latest ?: return
        val from = cur.indexOfFirst { it.dayExerciseId == id }
        val to = from + by
        if (from < 0 || to !in cur.indices) return
        val ids = cur.map { it.dayExerciseId }.toMutableList().apply { add(to, removeAt(from)) }
        scope.launch { dao.reorder(ids) }
    }

    fun remove(r: ExerciseRow, index: Int) {
        val item = DayExercise(r.dayExerciseId, day.id, r.exerciseId, index, r.sets, r.reps)
        scope.launch {
            dao.delete(item)
            snack.currentSnackbarData?.dismiss()
            if (snack.showSnackbar("Removed ${r.name}", actionLabel = "Undo") == SnackbarResult.ActionPerformed) dao.save(item)
        }
    }

    fun dragStart(id: Long) {
        val cur = latest
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
        if (shown.indexOfFirst { it.dayExerciseId == id } != cur.index - HEAD) return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != id && it.key is Long && center >= it.offset && center <= it.offset + it.size
        }
        if (target != null) {
            val gap = with(density) { 10.dp.toPx() } // the list's item spacing
            val newOffset = if (target.index > cur.index) cur.offset + target.size + gap else target.offset.toFloat()
            dragOffset += cur.offset - newOffset // keep the card under the finger after it changes slot
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            shown = shown.toMutableList().apply { add(target.index - HEAD, removeAt(cur.index - HEAD)) }
        }
        // Near the bar above or the button below: scroll the list and keep the card under the finger
        val edge = with(density) { 56.dp.toPx() }
        val step = with(density) { 14.dp.toPx() }
        val topLimit = info.viewportStartOffset + edge
        val bottomLimit = info.viewportEndOffset - with(density) { (barInset + ButtonZone).toPx() } - edge
        val top = cur.offset + dragOffset
        val push = when {
            top < topLimit -> -step
            top + cur.size > bottomLimit -> step
            else -> 0f
        }
        if (push != 0f) scope.launch { dragOffset += listState.scrollBy(push) }
    }

    fun dragEnd(id: Long) {
        if (draggingId != id) return
        val ids = shown.map { it.dayExerciseId }
        scope.launch {
            if (ids != latest?.map { it.dayExerciseId }) dao.reorder(ids)
            // Settle into the slot on a spring that starts from wherever the card is
            animate(dragOffset, 0f, animationSpec = appleSpring(1f, 0.3f)) { v, _ -> dragOffset = v }
            draggingId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topPad, bottom = barInset + ButtonZone),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                LargeTitle(
                    day.name,
                    subtitle = when {
                        day.isRest -> "Rest day. Nothing to add here."
                        loaded.isNullOrEmpty() -> null
                        else -> "Drag the handle to reorder. Swipe left to remove."
                    },
                )
            }
            when {
                day.isRest || loaded == null -> Unit
                loaded.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        "No exercises yet", "Add what you do on this day. Each exercise remembers its weight.",
                        "Add exercise", { adding = true }, Modifier.padding(top = 32.dp),
                    )
                }
                else -> itemsIndexed(list, key = { _, r -> r.dayExerciseId }) { i, r ->
                    val dragging = r.dayExerciseId == draggingId
                    val lift by animateFloatAsState(if (dragging) 1f else 0f, motionSpec(1f, 0.3f), label = "lift")
                    val shadow = with(density) { 14.dp.toPx() }
                    SwipeToRemove(
                        open = openId == r.dayExerciseId,
                        onOpenChange = { o -> openId = if (o) r.dayExerciseId else if (openId == r.dayExerciseId) null else openId },
                        onRemove = { openId = null; remove(r, i) },
                        modifier = Modifier.fillMaxWidth()
                            .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem()) // the dragged card follows the finger, the rest glide
                            .graphicsLayer {
                                if (dragging) translationY = dragOffset
                                val s = 1f + 0.03f * lift
                                scaleX = s; scaleY = s
                                shadowElevation = shadow * lift
                                shape = cardShape
                            },
                    ) {
                        AppCard(Modifier.fillMaxWidth(), onClick = { editing = r.dayExerciseId }) {
                            Row(Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                DragHandle(
                                    r.name, onMove = { move(r.dayExerciseId, it) },
                                    modifier = Modifier.pointerInput(r.dayExerciseId) {
                                        // No touch slop: the card is under the finger from the first pixel
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            dragStart(r.dayExerciseId)
                                            drag(down.id) { change ->
                                                val dy = change.positionChange().y // read before consuming: a consumed change reports zero movement
                                                change.consume()
                                                dragBy(r.dayExerciseId, dy)
                                            }

                                            dragEnd(r.dayExerciseId)
                                        }
                                    },
                                )
                                Column(Modifier.weight(1f).padding(start = 4.dp, top = 12.dp, bottom = 12.dp)) {
                                    Text(r.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${r.sets} × ${r.reps}", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (!day.isRest && !loaded.isNullOrEmpty()) {
            BottomFade(Modifier.align(Alignment.BottomCenter).padding(bottom = barInset))
            PrimaryButton("Add exercise", { adding = true }, Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = barInset + 12.dp))
        }
        NavBar(
            title = day.name,
            collapse = { collapse.value },
            leading = { BackButton("Plan", onBack) },
            trailing = { NavIconButton(Icons.Default.Delete, "Delete day") { confirmDelete = true } },
        )
        UndoHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = barInset + ButtonZone))
    }

    if (editRow != null) {
        val idx = loaded?.indexOfFirst { it.dayExerciseId == editRow.dayExerciseId } ?: 0
        EditExerciseSheet(
            editRow,
            onSave = { s, p -> scope.launch { dao.save(DayExercise(editRow.dayExerciseId, day.id, editRow.exerciseId, idx, s, p)) } },
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
        val names by remember { dao.exerciseNames() }.collectAsState(emptyList()) // only the sheet needs it
        var name by remember { mutableStateOf("") }
        var sets by remember { mutableIntStateOf(3) }
        var reps by remember { mutableIntStateOf(10) }
        val typed = name.trim()
        // Existing exercises matching what is typed: picking one keeps its weight shared
        val hits = names.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(6)
        AppSheet(
            title = "Add exercise", confirmLabel = "Add", confirmEnabled = name.isNotBlank(),
            onConfirm = {
                scope.launch {
                    val id = dao.exerciseId(name) // reuses an existing exercise, so its weight stays shared
                    dao.save(DayExercise(dayId = day.id, exerciseId = id, position = loaded?.size ?: 0, sets = sets, reps = reps))
                }
            },
            onDismiss = { adding = false },
        ) {
            OutlinedTextField(
                name, { name = it }, label = { Text("Exercise") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = appFieldColors(),
                modifier = Modifier.fillMaxWidth().autoFocus(),
            )
            if (hits.isNotEmpty()) {
                Text("Use an existing exercise", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hits) { h -> Chip(h) { name = h } }
                }
            }
            StepperRow("Sets", sets, { sets = it }, 1..99)
            StepperRow("Reps", reps, { reps = it }, 1..999)
        }
    }
}

/** Three lines on the left of a card: press and drag to reorder. Screen readers get Move up / Move down instead. */
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { Box(Modifier.size(18.dp, 2.dp).background(c.onSurfaceVariant, RoundedCornerShape(1.dp))) }
        }
    }
}

/** Tap an exercise: change its sets and reps with the steppers. */
@Composable
private fun EditExerciseSheet(r: ExerciseRow, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var sets by remember(r.dayExerciseId) { mutableIntStateOf(r.sets) }
    var reps by remember(r.dayExerciseId) { mutableIntStateOf(r.reps) }
    AppSheet(
        title = r.name, confirmLabel = "Save", confirmEnabled = true,
        onConfirm = { onSave(sets, reps) }, onDismiss = onDismiss,
    ) {
        StepperRow("Sets", sets, { sets = it }, 1..99)
        StepperRow("Reps", reps, { reps = it }, 1..999)
    }
}