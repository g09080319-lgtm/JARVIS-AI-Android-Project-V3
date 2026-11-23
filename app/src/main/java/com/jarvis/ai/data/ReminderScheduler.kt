package com.jarvis.ai.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.jarvis.ai.MainActivity
import com.jarvis.ai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.room.Room

/** Schedules user-requested, one-shot reminders without requiring exact-alarm access. */
object ReminderScheduler {
    private const val CHANNEL_ID = "jarvis_reminders"

    fun schedule(context: Context, id: Long, triggerAt: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = reminderPendingIntent(context, id)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(triggerAt, System.currentTimeMillis() + 1_000L), pending)
    }

    fun cancel(context: Context, id: Long) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(reminderPendingIntent(context, id))
    }

    private fun reminderPendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            data = Uri.parse("jarvis://reminder/$id")
            putExtra(ReminderReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun showNotification(context: Context, reminder: Reminder) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "JARVIS reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, reminder.id.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS reminder")
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .build()
        try { NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification) }
        catch (_: SecurityException) { /* Notifications may be disabled by the user. */ }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "jarvis.db")
                    .addMigrations(AppDatabase.MIGRATION_2_3)
                    .build()
                try {
                    val reminder = db.reminders().byId(id)
                    if (reminder != null && !reminder.fired) {
                        db.reminders().update(reminder.copy(fired = true))
                        ReminderScheduler.showNotification(context, reminder)
                    }
                } finally { db.close() }
            } finally { pendingResult.finish() }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.jarvis.ai.REMINDER_FIRE"
        const val EXTRA_ID = "reminder_id"
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "jarvis.db")
                    .addMigrations(AppDatabase.MIGRATION_2_3)
                    .build()
                try {
                    db.reminders().pending().forEach {
                        ReminderScheduler.schedule(context, it.id, it.triggerAt)
                    }
                } finally { db.close() }
            } finally { pendingResult.finish() }
        }
    }
}
