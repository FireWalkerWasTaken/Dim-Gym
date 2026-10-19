package com.dimgym

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.dimgym.data.DayExercise
import com.dimgym.data.DimGymDb
import com.dimgym.data.PlanDao
import kotlinx.coroutines.launch

/** Plan tab: shows the list of days, or the editor for one day. */
@Composable
fun PlanScreen() {
    val dao = DimGymDb.get(LocalContext.current).dao()
    val days by remember { dao.days() }.collectAsState(emptyList())
    var open by remember { mutableStateOf<Day?>(null) }
    val day = open
    if (day == null) DayList(dao, days) { open = it } else DayEditor(dao, day) { open = null }
}

@Composable
private fun DayList(dao: PlanDao, days: List<Day>, onOpen: (Day) -> Unit) {
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Text("Plan", style = MaterialTheme.typography.headlineLarge)
        Text("Your workout cycle, in order", color = muted)
        LazyColumn(
            Modifier.weight(1f).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.id }) { d ->
                Card(
                    onClick = { onOpen(d) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Text(if (d.isRest) "Rest day" else "", color = muted)
                    }
                }
            }
        }
        Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add day") }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var rest by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add day") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Push A") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rest, { rest = it })
                        Text("Rest day")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { dao.save(Day(name = name.trim(), position = (days.maxOfOrNull { it.position } ?: -1) + 1, isRest = rest)) }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayEditor(dao: PlanDao, day: Day, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val rows by remember(day.id) { dao.exercisesOf(day.id) }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(day.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton({ scope.launch { dao.delete(day) }; onBack() }) { Icon(Icons.Default.Delete, "Delete day") }
        }
        if (day.isRest) {
            Text("Rest day: nothing to add.", color = muted, modifier = Modifier.padding(top = 16.dp).weight(1f))
        } else {
            LazyColumn(
                Modifier.weight(1f).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.dayExerciseId }) { r ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(start = 18.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleMedium)
                                Text("${r.sets} × ${r.reps}", color = muted)
                            }
                            IconButton({
                                scope.launch {
                                    dao.delete(DayExercise(r.dayExerciseId, day.id, r.exerciseId, 0, r.sets, r.reps))
                                }
                            }) { Icon(Icons.Default.Delete, "Remove exercise") }
                        }
                    }
                }
            }
            Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text("Add exercise") }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var sets by remember { mutableStateOf("3") }
        var reps by remember { mutableStateOf("10") }
        val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Exercise") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(sets, { sets = it.filter(Char::isDigit) }, label = { Text("Sets") },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                        OutlinedTextField(reps, { reps = it.filter(Char::isDigit) }, label = { Text("Reps") },
                            singleLine = true, keyboardOptions = numbers, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && sets.isNotEmpty() && reps.isNotEmpty(), onClick = {
                    scope.launch {
                        val id = dao.exerciseId(name) // reuses an existing exercise, so its weight stays shared
                        dao.save(DayExercise(dayId = day.id, exerciseId = id, position = rows.size, sets = sets.toInt(), reps = reps.toInt()))
                    }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}