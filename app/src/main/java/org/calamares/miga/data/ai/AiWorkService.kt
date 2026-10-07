package org.calamares.miga.data.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.calamares.miga.L10n
import org.calamares.miga.MainActivity
import org.calamares.miga.R

/**
 * Channel of the ongoing "AI is working" notification. Its importance is DEFAULT, not LOW: many
 * phones hide the status bar icon of silent (low importance) notifications, and the icon is how
 * the user knows something is running in the background. The channel itself makes no sound and
 * does not vibrate, so it stays unobtrusive. A channel's importance cannot be raised once it
 * exists, hence the new id ("ai_work" was LOW).
 */
private const val WORK_CHANNEL_ID = "ai_work_visible"
private const val OLD_WORK_CHANNEL_ID = "ai_work"
private const val DONE_CHANNEL_ID = "ai_done"
private const val WORK_NOTIFICATION_ID = 7301
private const val DONE_NOTIFICATION_ID = 7302

/**
 * One piece of AI work shown in the notification. [title] null means it adds nothing to show (a
 * single request inside a larger, titled task).
 */
class AiTask internal constructor(val title: String?) {
    @Volatile internal var current: Int = 0
    @Volatile internal var total: Int = 0
    @Volatile internal var status: String? = null

    /** Shows what is happening now ("Asking Google Gemini…") under the title of the notification. */
    fun status(text: String?) {
        status = text
        AiKeepAlive.refresh()
    }

    /** Shows "[current] of [total]" and a progress bar in the notification. */
    fun progress(current: Int, total: Int) {
        this.current = current
        this.total = total
        AiKeepAlive.refresh()
    }
}

/**
 * Keeps AI requests alive when the user leaves the app or turns the screen off, and shows what is
 * running.
 *
 * Android cuts the network of apps in the background, so a recipe being generated failed as soon
 * as the user switched apps. While any AI work is running, a foreground service with a quiet
 * notification keeps the app in the foreground state. The notification shows the current task and
 * its progress, and appears straight away when the app goes to the background (Android otherwise
 * delays it by about ten seconds). Work is counted, so nested or parallel tasks share one service,
 * which stops when the last one finishes.
 */
object AiKeepAlive {
    private val lock = Any()
    private var appContext: Context? = null
    private val tasks = mutableListOf<AiTask>()
    private var service: AiWorkService? = null
    private var startedActivities = 0

    private val _running = MutableStateFlow(false)

    /** True while any AI work runs; MainActivity uses it to ask for the notification permission. */
    val running: StateFlow<Boolean> = _running

    /** True while no activity of the app is visible. */
    @Volatile var inBackground: Boolean = true
        private set

    fun init(app: Application) {
        appContext = app
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) {
                    inBackground = false
                    refresh()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) {
                    inBackground = true
                    refresh()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /**
     * Runs [block] with the foreground service active. A [title] (e.g. "Importing recipes") is
     * shown in the notification; [AiTask.progress] adds a progress bar.
     */
    suspend fun <T> hold(title: String? = null, block: suspend AiTask.() -> T): T {
        val task = AiTask(title)
        acquire(task)
        try {
            return task.block()
        } finally {
            release(task)
        }
    }

    /**
     * Posts [message] as a regular notification when the app is in the background, so the user
     * learns that a task they left running has finished. Does nothing while the app is visible.
     */
    @SuppressLint("MissingPermission")
    fun announceIfInBackground(message: String) {
        val context = appContext ?: return
        if (!inBackground || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannels(context)
        val notification = NotificationCompat.Builder(context, DONE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(L10n.str(R.string.app_name))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(DONE_NOTIFICATION_ID, notification) }
    }

    private fun acquire(task: AiTask) {
        val context = appContext ?: return
        synchronized(lock) {
            tasks += task
            _running.value = true
            if (tasks.size == 1 && service == null) {
                // Starting a foreground service is only allowed while the app is visible, which is
                // the case when the user starts an AI operation. If it fails, the work still runs;
                // it just has no protection in the background.
                runCatching { ContextCompat.startForegroundService(context, Intent(context, AiWorkService::class.java)) }
            } else {
                service?.refresh()
            }
        }
    }

    private fun release(task: AiTask) {
        if (appContext == null) return
        synchronized(lock) {
            tasks -= task
            if (tasks.isEmpty()) {
                _running.value = false
                service?.finish()
                service = null
            } else {
                service?.refresh()
            }
        }
    }

    internal fun refresh() {
        synchronized(lock) { service?.refresh() }
    }

    /** Called by the service once it is in the foreground; it stops at once if the work is done. */
    internal fun onServiceStarted(started: AiWorkService) {
        synchronized(lock) {
            if (tasks.isEmpty()) {
                started.finish()
            } else {
                service = started
                started.refresh()
            }
        }
    }

    internal fun onServiceStopped(stopped: AiWorkService) {
        synchronized(lock) {
            if (service === stopped) service = null
        }
    }

    /** Notification for the running work: the latest titled task, with its progress if any. */
    internal fun buildWorkNotification(context: Context): Notification {
        createChannels(context)
        val task = synchronized(lock) { tasks.lastOrNull { it.title != null } }
        val title = task?.title ?: L10n.str(R.string.ai_work_notification)
        // The latest status of any running task, nested ones included.
        val status = synchronized(lock) { tasks.lastOrNull { it.status != null }?.status }
        val builder = NotificationCompat.Builder(context, WORK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(title)
            .setContentText(status ?: L10n.str(R.string.app_name))
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(
                if (inBackground) NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE else NotificationCompat.FOREGROUND_SERVICE_DEFAULT
            )
        if (task != null && task.total > 0) {
            builder.setSubText(L10n.str(R.string.ai_progress_x_of_y, task.current, task.total))
            builder.setProgress(task.total, task.current, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_WORK_CHANNEL_ID)
        if (manager.getNotificationChannel(WORK_CHANNEL_ID) == null) {
            val channel = NotificationChannel(WORK_CHANNEL_ID, L10n.str(R.string.ai_work_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(DONE_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DONE_CHANNEL_ID, L10n.str(R.string.ai_done_channel), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Foreground service with no work of its own; see [AiKeepAlive]. */
class AiWorkService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground must always be called after startForegroundService, even when the work
        // has already finished, or the system kills the app.
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                WORK_NOTIFICATION_ID,
                AiKeepAlive.buildWorkNotification(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        }.isSuccess
        if (started) AiKeepAlive.onServiceStarted(this) else stopSelf()
        return START_NOT_STICKY
    }

    /** Android 15 limits how long a data sync service may run; stop quietly when that is reached. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        finish()
    }

    override fun onDestroy() {
        AiKeepAlive.onServiceStopped(this)
        super.onDestroy()
    }

    /** Updates the notification with the current task and progress. */
    @SuppressLint("MissingPermission")
    internal fun refresh() {
        runCatching { NotificationManagerCompat.from(this).notify(WORK_NOTIFICATION_ID, AiKeepAlive.buildWorkNotification(this)) }
    }

    internal fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
