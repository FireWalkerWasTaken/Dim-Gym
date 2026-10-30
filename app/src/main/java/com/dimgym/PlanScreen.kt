package com.dimgym

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.dimgym.data.Day
import com.dimgym.data.DayExercise
import com.dimgym.data.DimGymDb
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.launch

/**
 * Plan tab:
 * Apple-style cycle management with Inset Grouped day cards,
 * spring-accelerated drag-to-reorder, and fluid modal sheets for adding and editing.
 */
@Composable
fun PlanScreen() {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val scope = rememberCoroutineScope()
    val days by remember { dao.days() }.collectAsState(null)
    val today by remember { dao.today() }.collectAsState(null)
    var open by remember { mutableStateOf<Day?>(null) }

    AnimatedContent(
        targetState = open,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { drill(forward = targetState != null) },
        contentKey = { it?.id },
        label = "planScreenTransition",
    ) { day ->
        if (day == null) {
            DayList(dao, days, today?.id) { open = it }
        } else {
            DayEditor(
                dao = dao,
                day = day,
                onBack = { open = null },
                onDelete = {
                    scope.launch { dao.delete(day) }
                    open = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayList(
    dao: PlanDao,
    days: List<Day>?,
    todayId: Long?,
    onOpen: (Day) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text("Workout Plan", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Your routine repeats sequentially through this cycle.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }

        when {
            days == null -> Spacer(Modifier.weight(1f))
            days.isEmpty() -> EmptyState(
                title = "No Workout Days",
                body = "A cycle is your structured schedule of lifting days and rest days. Begin by creating your first day.",
                action = "Add Day",
                onAction = { adding = true },
                modifier = Modifier.weight(1f),
            )

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(days, key = { _, d -> d.id }) { i, d ->
                        val isToday = d.id == todayId
                        AppCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            shape = RoundedCornerShape(18.dp),
                            onClick = { onOpen(d) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = String.format("%02d", i + 1),
                                    color = if (isToday) c.primary else c.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    modifier = Modifier.width(32.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = d.name,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                                        ),
                                    )
                                    if (d.isRest) {
                                        Text(
                                            text = "Rest & Recovery",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isToday) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(c.primaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = "TODAY",
                                            color = c.onPrimaryContainer,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                PrimaryButton("Add Day", { adding = true })
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var rest by remember { mutableStateOf(false) }

        AppleModalSheet(onDismissRequest = { adding = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "New Workout Day",
                    style = MaterialTheme.typography.headlineSmall,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Day Name (e.g., Push A, Legs, Pull)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                )

                InsetGroupedSection {
                    InsetRow {
                        Column {
                            Text("Rest Day", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Recovery day without active exercise logging",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rest,
                            onCheckedChange = { rest = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = c.primary,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                PrimaryButton(
                    text = "Add to Cycle",
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            dao.save(
                                Day(
                                    name = name.trim(),
                                    position = (days?.maxOfOrNull { it.position } ?: -1) + 1,
                                    isRest = rest,
                                ),
                            )
                        }
                        adding = false
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayEditor(
    dao: PlanDao,
    day: Day,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
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
    var shown by remember { mutableStateOf(emptyList<ExerciseRow>()) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val list = (if (draggingId != null) shown else loaded).orEmpty()

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
        if (shown.indexOfFirst { it.dayExerciseId == id } != cur.index) return
        val center = cur.offset + dragOffset + cur.size / 2f
        val target = info.visibleItemsInfo.firstOrNull { it.key != id && center >= it.offset && center <= it.offset + it.size }
        if (target != null) {
            val gap = with(density) { 10.dp.toPx() }
            val newOffset = if (target.index > cur.index) cur.offset + target.size + gap else target.offset.toFloat()
            dragOffset += cur.offset - newOffset
            shown = shown.toMutableList().apply { add(target.index, removeAt(cur.index)) }
        }
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
            animate(dragOffset, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f)) { v, _ ->
                dragOffset = v
            }
            draggingId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.surfaceContainerHighest.copy(alpha = 0.6f))
                        .pressScale(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.onSurface)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = day.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.error.copy(alpha = 0.12f))
                        .pressScale(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete day", tint = c.error)
                }
            }

            if (!day.isRest && !loaded.isNullOrEmpty()) {
                Text(
                    text = "Hold & drag handle to reorder • Tap card to edit sets & reps",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            when {
                day.isRest -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Rest day — No exercises are assigned to this session.",
                        color = c.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                loaded == null -> Spacer(Modifier.weight(1f))
                loaded.isEmpty() -> EmptyState(
                    title = "No Exercises Added",
                    body = "Add the movements and lifts you do on this day. Weights and notes will be saved.",
                    action = "Add Exercise",
                    onAction = { adding = true },
                    modifier = Modifier.weight(1f),
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(list, key = { _, r -> r.dayExerciseId }) { i, r ->
                            val dragging = r.dayExerciseId == draggingId
                            val lift by animateFloatAsState(
                                targetValue = if (dragging) 1.03f else 1f,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
                                label = "liftAnimation",
                            )

                            AppCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (dragging) Modifier.zIndex(2f) else Modifier.animateItem())
                                    .graphicsLayer {
                                        if (dragging) translationY = dragOffset
                                        scaleX = lift
                                        scaleY = lift
                                    },
                                shape = RoundedCornerShape(16.dp),
                                onClick = { editing = r.dayExerciseId },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Apple HIG style reorder grip
                                    DragHandle(
                                        name = r.name,
                                        onMove = { move(r.dayExerciseId, it) },
                                        modifier = Modifier.pointerInput(r.dayExerciseId) {
                                            detectVerticalDragGestures(
                                                onDragStart = { dragStart(r.dayExerciseId) },
                                                onDragEnd = { dragEnd(r.dayExerciseId) },
                                                onDragCancel = { dragEnd(r.dayExerciseId) },
                                                onVerticalDrag = { change, dy ->
                                                    change.consume()
                                                    dragBy(r.dayExerciseId, dy)
                                                },
                                            )
                                        },
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 6.dp),
                                    ) {
                                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${r.sets} sets × ${r.reps} reps",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.onSurfaceVariant,
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val item = DayExercise(r.dayExerciseId, day.id, r.exerciseId, i, r.sets, r.reps)
                                            scope.launch {
                                                dao.delete(item)
                                                snack.currentSnackbarData?.dismiss()
                                                if (snack.showSnackbar("Removed ${r.name}", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                                                    dao.save(item)
                                                }
                                            }
                                        },
                                        modifier = Modifier.pressScale(),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove ${r.name}",
                                            tint = c.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Add Exercise", { adding = true })
                }
            }
        }

        SnackbarHost(
            hostState = snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }

    if (editRow != null) {
        val idx = loaded?.indexOfFirst { it.dayExerciseId == editRow.dayExerciseId } ?: 0
        EditExerciseSheet(
            editRow = editRow,
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
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete ${day.name}?") },
            text = { Text("This workout will be removed from your cycle. Saved exercise weights and notes will be preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Delete", color = c.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10") }
        val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
        val typed = name.trim()
        val hits = names.filter { it.contains(typed, ignoreCase = true) && !it.equals(typed, ignoreCase = true) }.take(6)

        AppleModalSheet(onDismissRequest = { adding = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Add Exercise", style = MaterialTheme.typography.headlineSmall)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                )

                if (hits.isNotEmpty()) {
                    Text(
                        "Previously Used Exercises",
                        color = c.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(hits) { h ->
                            FilterChip(
                                selected = false,
                                onClick = { name = h },
                                label = { Text(h, style = MaterialTheme.typography.bodySmall) },
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = c.surfaceContainerHighest.copy(alpha = 0.6f),
                                ),
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it.filter(Char::isDigit).take(2) },
                        label = { Text("Sets") },
                        singleLine = true,
                        keyboardOptions = numbers,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = reps,
                        onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = numbers,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(4.dp))

                val s = sets.toIntOrNull() ?: 0
                val r = reps.toIntOrNull() ?: 0

                PrimaryButton(
                    text = "Add to Day",
                    enabled = name.isNotBlank() && s > 0 && r > 0,
                    onClick = {
                        scope.launch {
                            val id = dao.exerciseId(name)
                            dao.save(
                                DayExercise(
                                    dayId = day.id,
                                    exerciseId = id,
                                    position = loaded?.size ?: 0,
                                    sets = s,
                                    reps = r,
                                ),
                            )
                        }
                        adding = false
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * Apple-style Reorder Grip Handle:
 * Three delicate bars with tactile responsiveness and accessibility actions.
 */
@Composable
private fun DragHandle(name: String, onMove: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(44.dp)
            .semantics {
                contentDescription = "Reorder $name"
                customActions = listOf(
                    CustomAccessibilityAction("Move up") {
                        onMove(-1)
                        true
                    },
                    CustomAccessibilityAction("Move down") {
                        onMove(1)
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(18.dp, 2.dp)
                        .background(c.onSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

/**
 * Edit Exercise Sets & Reps Modal Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExerciseSheet(
    editRow: ExerciseRow,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember(editRow.dayExerciseId) { mutableStateOf(editRow.sets.toString()) }
    var reps by remember(editRow.dayExerciseId) { mutableStateOf(editRow.reps.toString()) }
    val s = sets.toIntOrNull() ?: 0
    val p = reps.toIntOrNull() ?: 0
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)

    AppleModalSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edit ${editRow.name}",
                style = MaterialTheme.typography.headlineSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = sets,
                    onValueChange = { sets = it.filter(Char::isDigit).take(2) },
                    label = { Text("Target Sets") },
                    singleLine = true,
                    keyboardOptions = numbers,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                    label = { Text("Target Reps") },
                    singleLine = true,
                    keyboardOptions = numbers,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = "Save Changes",
                enabled = s > 0 && p > 0,
                onClick = { onSave(s, p) },
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
