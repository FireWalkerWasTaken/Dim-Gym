package com.dimgym

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dimgym.data.DimGymDb
import com.dimgym.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings tab: weight unit, appearance, daily reminder, backup. */
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val dao = DimGymDb.get(ctx).dao()
    val scope = rememberCoroutineScope()
    val settings by remember { dao.settings() }.collectAsState(null)
    var confirmImport by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val collapse = rememberCollapse(scroll)
    val barInset = LocalBarInset.current
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

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(top = navBarHeight(), bottom = barInset + 24.dp)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            LargeTitle("Settings", Modifier.staggerIn(0))
            settings?.let { s ->
                Section("Weight unit", Modifier.staggerIn(1)) {
                    Segmented(listOf("kg", "lb"), s.unit, { it }) { u -> scope.launch { dao.save(s.copy(unit = u)) } }
                }
                Section("Appearance", Modifier.staggerIn(2), "System follows your phone's light and dark setting.") {
                    Segmented(listOf("system", "dark", "light"), s.theme, { it.replaceFirstChar(Char::uppercase) }) { t ->
                        scope.launch { dao.save(s.copy(theme = t)) }
                    }
                }
                Section("Daily reminder", Modifier.staggerIn(3), "A nudge at your chosen time. Skipped on rest days.") {
                    CardGroup {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Remind me", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            AppSwitch(s.reminderMinutes != null, { on ->
                                if (on) {
                                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    setReminder(s, 18 * 60) // default 6 PM, change below
                                } else setReminder(s, null)
                            })
                        }
                        AnimatedVisibility(
                            visible = s.reminderMinutes != null,
                            enter = expandVertically(motionSpec<IntSize>(1f, 0.4f)) + fadeIn(tween(160)),
                            exit = shrinkVertically(motionSpec<IntSize>(1f, 0.4f)) + fadeOut(tween(120)),
                        ) {
                            Column {
                                Hairline()
                                val m = s.reminderMinutes ?: (18 * 60)
                                ValueRow("Time", "%02d:%02d".format(m / 60, m % 60)) {
                                    TimePickerDialog(ctx, { _, h, min -> setReminder(s, h * 60 + min) }, m / 60, m % 60, true).show()
                                }
                            }
                        }
                    }
                }
                Section("Backup", Modifier.staggerIn(4), "One file with your plan, weights, notes and settings.") {
                    CardGroup {
                        ValueRow("Export backup", null) { exportFile.launch("dimgym-backup.json") }
                        Hairline()
                        ValueRow("Import backup", null) { confirmImport = true }
                    }
                }
            }
        }
        NavBar(title = "Settings", collapse = { collapse.value })
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

/** A small caps header above its controls, and an optional one-line footnote below. */
@Composable
private fun Section(title: String, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), color = c.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
        content()
        if (footer != null) Text(footer, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** A tappable row inside a [CardGroup]: label on the left, optional value and chevron on the right. Highlights on touch. */
@Composable
private fun ValueRow(label: String, value: String?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .pressHighlight(src, c.surfaceContainerHigh)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = if (value == null) c.primary else c.onSurface, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = c.primary, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
    }
}

/**
 * Two-or-three way switch. One thumb slides between the segments on a critically damped spring, so tapping
 * quickly across them just retargets it instead of queueing animations.
 */
@Composable
private fun Segmented(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val pos by animateFloatAsState(index.toFloat(), motionSpec(1f, 0.32f), label = "segment")
    val track = c.surfaceContainerHighest
    val thumb = if (c.background.luminance() < 0.5f) lerp(track, c.onSurface, 0.16f) else c.surface
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).drawBehind { drawRect(track) }.padding(3.dp)
            .drawBehind {
                val w = size.width / options.size
                drawRoundRect(thumb, Offset(pos * w, 0f), Size(w, size.height), CornerRadius(corner))
            }
            .selectableGroup(),
    ) {
        options.forEach { o ->
            val on = o == selected
            val src = remember { MutableInteractionSource() }
            val text by animateColorAsState(if (on) c.onSurface else c.onSurfaceVariant, tween(150), label = "segmentText")
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp)
                    .selectable(
                        selected = on, interactionSource = src, indication = null, role = Role.RadioButton,
                        onClick = { if (!on) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(o) } },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), color = text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

