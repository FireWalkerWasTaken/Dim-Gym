package com.dimgym

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.LocalContext
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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var confirmImport by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = Backup.export(dao)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
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

    fun setReminder(s: Settings, minutes: Int?) {
        scope.launch { dao.save(s.copy(reminderMinutes = minutes)) }
        Reminder.schedule(ctx, minutes)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)
        settings?.let { s ->
            Text("Weight unit", color = muted, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (u in listOf("kg", "lb")) {
                    FilterChip(s.unit == u, { scope.launch { dao.save(s.copy(unit = u)) } }, { Text(u) })
                }
            }
            Text("Theme", color = muted, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in listOf("dark", "light")) {
                    FilterChip(s.theme == t, { scope.launch { dao.save(s.copy(theme = t)) } }, { Text(t.replaceFirstChar { it.uppercase() }) })
                }
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Daily reminder", color = muted, modifier = Modifier.weight(1f))
                Switch(checked = s.reminderMinutes != null, onCheckedChange = { on ->
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        setReminder(s, 18 * 60) // default 6 PM, change below
                    } else setReminder(s, null)
                })
            }
            s.reminderMinutes?.let { m ->
                OutlinedButton({
                    TimePickerDialog(ctx, { _, h, min -> setReminder(s, h * 60 + min) }, m / 60, m % 60, true).show()
                }) { Text("Remind me at %02d:%02d".format(m / 60, m % 60)) }
            }
            Text("Backup", color = muted, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ exportFile.launch("dimgym-backup.json") }) { Text("Export backup") }
                OutlinedButton({ confirmImport = true }) { Text("Import backup") }
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