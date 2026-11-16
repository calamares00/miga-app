package org.calamares.miga.data.cook

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.calamares.miga.L10n
import org.calamares.miga.R

/**
 * A cooking timer: [name] says what it is for ("Step 3", "Rice"...), [stepIndex] the cook mode
 * step it was started from, if any. [endsAt] is wall-clock time, as the alarm uses.
 */
data class CookTimer(val id: Int, val name: String, val stepIndex: Int?, val totalSeconds: Int, val endsAt: Long) {
    fun secondsLeft(now: Long): Int = ((endsAt - now + 999) / 1000).coerceAtLeast(0).toInt()
}

/**
 * The running cooking timers, several at once. Each one also sets a system alarm that posts a
 * notification when it ends, so the user hears it after leaving the app or with the screen off.
 * Exact alarms are used when Android allows them (see [canBeExact]); otherwise the system may
 * deliver the alarm a little late.
 */
object CookTimers {
    private const val CHANNEL_ID = "cook_timers"
    internal const val EXTRA_ID = "timer_id"
    internal const val EXTRA_NAME = "timer_name"

    private val _timers = MutableStateFlow<List<CookTimer>>(emptyList())
    val timers: StateFlow<List<CookTimer>> = _timers

    private var nextId = 1

    fun start(context: Context, name: String, seconds: Int, stepIndex: Int? = null, now: Long = System.currentTimeMillis()): CookTimer {
        val timer = CookTimer(nextId++, name.trim(), stepIndex, seconds, now + seconds * 1000L)
        _timers.update { it + timer }
        scheduleAlarm(context.applicationContext, timer)
        return timer
    }

    /** Stops a timer, or clears it once finished, with its alarm and notification. */
    fun cancel(context: Context, id: Int) {
        _timers.update { list -> list.filterNot { it.id == id } }
        val app = context.applicationContext
        alarmManager(app)?.cancel(alarmIntent(app, id, ""))
        NotificationManagerCompat.from(app).cancel(notificationId(id))
    }

    /** Whether alarms go off on time; from Android 12 the user can deny exact alarms. */
    fun canBeExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context)?.canScheduleExactAlarms() == true

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun notificationId(id: Int) = 7_000 + id

    private fun alarmIntent(context: Context, id: Int, name: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CookTimerReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_NAME, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun scheduleAlarm(context: Context, timer: CookTimer) {
        val manager = alarmManager(context) ?: return
        val intent = alarmIntent(context, timer.id, timer.name)
        try {
            if (canBeExact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAt, intent)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAt, intent)
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timer.endsAt, intent)
        }
    }

    /** Posts "<name> is ready" with the alarm sound; tapping it opens the app. */
    internal fun notifyFinished(context: Context, id: Int, name: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, L10n.str(R.string.cook_timers_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_timer)
            .setContentTitle(L10n.str(R.string.cook_timer_done_x, name.ifBlank { L10n.str(R.string.cook_timer) }))
            .setContentText(L10n.str(R.string.cook_timer_done_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply { open?.let { setContentIntent(it) } }
            .build()
        try {
            manager.notify(notificationId(id), notification)
        } catch (e: SecurityException) {
            // Notifications not allowed: the timer still shows as finished in cook mode.
        }
    }
}

/** Receives the alarm of a finished timer (see CookTimers). */
class CookTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(CookTimers.EXTRA_ID, 0)
        CookTimers.notifyFinished(context, id, intent.getStringExtra(CookTimers.EXTRA_NAME).orEmpty())
    }
}
