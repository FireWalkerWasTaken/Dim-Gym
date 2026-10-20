package com.dimgym

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dimgym.data.DimGymDb
import com.dimgym.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings tab: weight unit, theme, daily reminder, backup. */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val dao = DimGymDb.get(ctx).dao()
    val scope = rememberCoroutineScope()
    val settings by remember { dao.settings() }.collectAsState(null)
    var confirmImport by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun setReminder(s: Settings, minutes: Int?) {
        scope.launch { dao.save(s.copy(reminderMinutes = minutes)) }
        Reminder.schedule(ctx, minutes)
    }

    // If notifications are blocked the reminder could never show, so switch it back off and say why
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            settings?.let { setReminder(it, null) }
            toast("Notifications are blocked, so the reminder is off")
        }
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = Backup.export(dao)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }
                toast("Backup saved")
            } catch (e: Exception) {
                toast("Could not save the backup")
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                val reminder = Backup.restore(dao, text)
                Reminder.schedule(ctx, reminder)
                toast("Backup restored")
            } catch (e: Exception) {
                toast("That file is not a valid DimGym backup")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        settings?.let { s ->
            Section("Weight unit") {
                Segmented(listOf("kg", "lb"), s.unit, { it }) { u -> scope.launch { dao.save(s.copy(unit = u)) } }
            }
            Section("Theme") {
                Segmented(listOf("dark", "light"), s.theme, { it.replaceFirstChar(Char::uppercase) }) { t ->
                    scope.launch { dao.save(s.copy(theme = t)) }
                }
            }
            Section("Daily reminder", "A nudge at your chosen time. Skipped on rest days.") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.reminderMinutes?.let { "On at %02d:%02d".format(it / 60, it % 60) } ?: "Off",
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = s.reminderMinutes != null, onCheckedChange = { on ->
                        if (on) {
                            if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                            setReminder(s, 18 * 60) // default 6 PM, change below
                        } else setReminder(s, null)
                    })
                }
                AnimatedVisibility(visible = s.reminderMinutes != null, exit = ExitTransition.None) {
                    OutlinedButton({
                        val m = s.reminderMinutes ?: (18 * 60)
                        TimePickerDialog(ctx, { _, h, min -> setReminder(s, h * 60 + min) }, m / 60, m % 60, true).show()
                    }) { Text("Change time") }
                }
            }
            Section("Backup", "One file with your plan, weights, notes and settings.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ exportFile.launch("dimgym-backup.json") }, Modifier.weight(1f)) { Text("Export backup") }
                    OutlinedButton({ confirmImport = true }, Modifier.weight(1f)) { Text("Import backup") }
                }
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import backup?") },
            text = { Text("This replaces your current plan, weights and notes with the ones in the file.") },
            confirmButton = { TextButton({ confirmImport = false; importFile.launch(arrayOf("*/*")) }) { Text("Choose file") } },
            dismissButton = { TextButton({ confirmImport = false }) { Text("Cancel") } },
        )
    }
}

/** A title (and optional one-line caption) above its controls. */
@Composable
private fun Section(title: String, caption: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (caption != null) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Two-or-three way switch in one rounded track; the selected segment eases to the accent container. */
@Composable
private fun Segmented(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val outer = MaterialTheme.shapes.medium
    Row(
        Modifier.fillMaxWidth().selectableGroup().clip(outer).background(c.surface).border(1.dp, c.outlineVariant, outer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            val bg by animateColorAsState(if (on) c.primaryContainer else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).height(44.dp).clip(MaterialTheme.shapes.small).background(bg)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(o) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.titleMedium, color = if (on) c.onPrimaryContainer else c.onSurfaceVariant)
            }
        }
    }
}