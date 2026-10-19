package com.dimgym

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dimgym.data.Day
import com.dimgym.data.DimGymDb
import com.dimgym.data.Exercise
import com.dimgym.data.ExerciseRow
import com.dimgym.data.PlanDao
import kotlinx.coroutines.launch

private fun fmt(w: Double) = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

/** Today tab: today's workout as cards, a Done button, and a menu to switch workout. */
@Composable
fun TodayScreen() {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val day by remember { dao.today() }.collectAsState(null)
    val days by remember { dao.days() }.collectAsState(emptyList())
    val settings by remember { dao.settings() }.collectAsState(null)
    var open by remember { mutableStateOf<ExerciseRow?>(null) }
    val d = day
    val r = open
    val unit = settings?.unit ?: "kg"
    when {
        r != null -> ExerciseDetail(dao, r, unit) { open = null }
        d == null -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No workout yet", style = MaterialTheme.typography.headlineLarge)
            Text("Build your cycle in the Plan tab.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        else -> DayScreen(dao, d, days, unit) { open = it }
    }
}

@Composable
private fun DayScreen(dao: PlanDao, day: Day, days: List<Day>, unit: String, onOpen: (ExerciseRow) -> Unit) {
    val scope = rememberCoroutineScope()
    val rows by remember(day.id) { dao.exercisesOf(day.id) }.collectAsState(emptyList())
    var menu by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text("TODAY", color = c.primary, style = MaterialTheme.typography.labelLarge)
                Text(day.name, style = MaterialTheme.typography.headlineLarge)
                Text(if (day.isRest) "Rest day" else "${rows.size} exercises", color = c.onSurfaceVariant)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Switch workout") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Switch to", color = c.onSurfaceVariant) }, onClick = {}, enabled = false)
                    days.forEachIndexed { i, x ->
                        DropdownMenuItem(
                            text = { Text(if (x.id == day.id) "${x.name} (today)" else x.name) },
                            onClick = { scope.launch { dao.setCycle(i) }; menu = false },
                        )
                    }
                }
            }
        }

        if (day.isRest) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nothing to do today.", color = c.onSurfaceVariant)
            }
        } else {
            if (rows.isEmpty()) Text("No exercises yet. Add some in the Plan tab.", color = c.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            LazyColumn(Modifier.weight(1f).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.dayExerciseId }) { r ->
                    Card(
                        onClick = { onOpen(r) },
                        colors = CardDefaults.cardColors(containerColor = c.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text("${r.sets} × ${r.reps}", color = c.onSurfaceVariant)
                            }
                            Text(r.weight?.let { "${fmt(it)} $unit" } ?: "—", color = c.primary, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
        Button(onClick = { scope.launch { dao.advance() } }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun ExerciseDetail(dao: PlanDao, r: ExerciseRow, unit: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var weight by remember { mutableStateOf(r.weight?.let(::fmt) ?: "") }
    var notes by remember { mutableStateOf(r.notes) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(r.name, style = MaterialTheme.typography.headlineLarge)
        Text("${r.sets} × ${r.reps}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            weight, { weight = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("Weight ($unit), leave empty if none") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            notes, { notes = it }, label = { Text("My notes (form, cues)") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                scope.launch { dao.update(Exercise(r.exerciseId, r.name, weight.toDoubleOrNull(), notes)) }
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
    }
}