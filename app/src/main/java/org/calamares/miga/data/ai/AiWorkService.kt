package org.calamares.miga.data.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.calamares.miga.L10n
import org.calamares.miga.MainActivity
import org.calamares.miga.R

private const val CHANNEL_ID = "ai_work"
private const val NOTIFICATION_ID = 7301

/**
 * Keeps AI requests alive when the user leaves the app or turns the screen off.
 *
 * Android cuts the network of apps in the background, so a recipe being generated failed with a
 * connection error as soon as the user switched apps. While any AI work is running, a foreground
 * service with a quiet notification keeps the app in the foreground state. Work is counted, so
 * nested or parallel operations share one service, which stops when the last one finishes.
 */
object AiKeepAlive {
    private val lock = Any()
    private var appContext: Context? = null
    private var activeCount = 0
    private var service: AiWorkService? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Runs [block] with the foreground service active. */
    suspend fun <T> hold(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            release()
        }
    }

    private fun acquire() {
        val context = appContext ?: return
        synchronized(lock) {
            activeCount++
            if (activeCount == 1 && service == null) {
                // Starting a foreground service is only allowed while the app is visible, which is
                // the case when the user starts an AI operation. If it fails, the work still runs;
                // it just has no protection in the background.
                runCatching { ContextCompat.startForegroundService(context, Intent(context, AiWorkService::class.java)) }
            }
        }
    }

    private fun release() {
        if (appContext == null) return
        synchronized(lock) {
            activeCount = (activeCount - 1).coerceAtLeast(0)
            if (activeCount == 0) {
                service?.finish()
                service = null
            }
        }
    }

    /** Called by the service once it is in the foreground; it stops at once if the work is done. */
    internal fun onServiceStarted(started: AiWorkService) {
        synchronized(lock) {
            if (activeCount == 0) started.finish() else service = started
        }
    }

    internal fun onServiceStopped(stopped: AiWorkService) {
        synchronized(lock) {
            if (service === stopped) service = null
        }
    }
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
                NOTIFICATION_ID,
                buildNotification(),
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

    internal fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification() = run {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, L10n.str(R.string.ai_work_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_ai)
            .setContentTitle(L10n.str(R.string.ai_work_notification))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
