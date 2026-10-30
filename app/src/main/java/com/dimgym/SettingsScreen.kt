package com.dimgym

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimgym.data.DimGymDb
import com.dimgym.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings tab:
 * Apple Inset Grouped layout (iOS Settings specification) featuring
 * spring-driven sliding segmented controls, native switch toggles, and grouped data rows.
 */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val dao = DimGymDb.get(ctx).dao()
    val scope = rememberCoroutineScope()
    val settings by remember { dao.settings() }.collectAsState(null)
    var confirmImport by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun setReminder(s: Settings, minutes: Int?) {
        scope.launch { dao.save(s.copy(reminderMinutes = minutes)) }
        Reminder.schedule(ctx, minutes)
    }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            settings?.let { setReminder(it, null) }
            toast("Notifications are blocked, so reminder is disabled")
        }
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = Backup.export(dao)
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                    }
                    toast("Backup exported successfully")
                } catch (e: Exception) {
                    toast("Could not save the backup")
                }
            }
        }
    }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    }
                    val reminder = Backup.restore(dao, text)
                    Reminder.schedule(ctx, reminder)
                    toast("Backup restored successfully")
                } catch (e: Exception) {
                    toast("File is not a valid DimGym backup")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Preferences & Data Management",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
        }

        settings?.let { s ->
            // Section 1: Preferences
            InsetGroupedSection(
                title = "Preferences",
                caption = "Choose your preferred measurement units and interface appearance.",
            ) {
                InsetRow(showDivider = true) {
                    Text("Weight Unit", style = MaterialTheme.typography.titleMedium)
                    AppleSegmentedControl(
                        options = listOf("kg", "lb"),
                        selected = s.unit,
                        onSelect = { u -> scope.launch { dao.save(s.copy(unit = u)) } },
                        modifier = Modifier.size(130.dp, 38.dp),
                        labelFormatter = { it.uppercase() },
                    )
                }

                InsetRow {
                    Text("Theme Appearance", style = MaterialTheme.typography.titleMedium)
                    AppleSegmentedControl(
                        options = listOf("dark", "light"),
                        selected = s.theme,
                        onSelect = { t -> scope.launch { dao.save(s.copy(theme = t)) } },
                        modifier = Modifier.size(150.dp, 38.dp),
                        labelFormatter = { it.replaceFirstChar(Char::uppercase) },
                    )
                }
            }

            // Section 2: Workout Reminders
            InsetGroupedSection(
                title = "Workout Reminders",
                caption = "A discreet daily reminder to train. Automatically skipped on designated rest days.",
            ) {
                InsetRow(showDivider = s.reminderMinutes != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Notification", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = s.reminderMinutes?.let { "Scheduled at %02d:%02d".format(it / 60, it % 60) } ?: "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (s.reminderMinutes != null) c.primary else c.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.reminderMinutes != null,
                        onCheckedChange = { on ->
                            if (on) {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                setReminder(s, 18 * 60)
                            } else {
                                setReminder(s, null)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = c.primary,
                        ),
                    )
                }

                AnimatedVisibility(
                    visible = s.reminderMinutes != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    InsetRow(
                        modifier = Modifier.clickable {
                            val m = s.reminderMinutes ?: (18 * 60)
                            TimePickerDialog(ctx, { _, h, min ->
                                setReminder(s, h * 60 + min)
                            }, m / 60, m % 60, true).show()
                        },
                    ) {
                        Text(
                            text = "Change Reminder Time",
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.primary,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = s.reminderMinutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = c.onSurfaceVariant,
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(16.dp),
                            )
                        }
                    }
                }
            }

            // Section 3: Data & Backup
            InsetGroupedSection(
                title = "Data & Backup",
                caption = "Your training cycle, weights, notes, and preferences in a single transferable JSON file.",
            ) {
                InsetRow(
                    showDivider = true,
                    modifier = Modifier.clickable { exportFile.launch("dimgym-backup.json") },
                ) {
                    Column {
                        Text("Export Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Save workout data to storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Export",
                        tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }

                InsetRow(
                    modifier = Modifier.clickable { confirmImport = true },
                ) {
                    Column {
                        Text("Import Backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Restore data from a backup file",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Import",
                        tint = c.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Apple-style Muted Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "DimGym 1.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Designed with Apple Human Interface Guidelines",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = c.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Import Backup?") },
            text = { Text("Restoring a backup replaces your current plan, saved weights, and exercise notes with the records in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importFile.launch(arrayOf("*/*"))
                }) {
                    Text("Select File", color = c.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
