package com.dimgym

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dimgym.data.Day
import com.dimgym.data.DimGymDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/** Schedules one daily reminder at the chosen time (minutes after midnight). Pass null to turn it off. */
object Reminder {
    private const val CHANNEL = "daily"

    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun schedule(ctx: Context, minutes: Int?) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(intent(ctx))
        if (minutes == null) return
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Not exact to the second (no special permission needed), but close enough for a reminder
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.timeInMillis, intent(ctx))
    }

    fun notify(ctx: Context, day: Day) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily workout reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to train")
            .setContentText(day.name)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}

/** Fires at the reminder time (and after a phone reboot): shows the notification and schedules tomorrow's. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DimGymDb.get(ctx).dao()
                val minutes = dao.settings().first().reminderMinutes ?: return@launch
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
                    val day = dao.today().first()
                    if (day != null && !day.isRest) Reminder.notify(ctx, day) // no reminder on rest days
                }
                Reminder.schedule(ctx, minutes)
            } finally {
                pending.finish()
            }
        }
    }
}