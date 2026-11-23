package com.jarvis.ai

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch

/** Optional microphone foreground service, started only from the visible app by an explicit user choice. */
class AssistantService : Service() {
    companion object {
        const val ACTION_START = "com.jarvis.ai.START"
        const val ACTION_LISTEN = "com.jarvis.ai.LISTEN"
        const val ACTION_STOP = "com.jarvis.ai.STOP"
        const val ACTION_STOP_BACKGROUND = "com.jarvis.ai.STOP_BACKGROUND"
        private const val CHANNEL = "jarvis_bg"
        private const val ID = 1
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as JarvisApp).engine
        when (intent?.action) {
            ACTION_STOP -> {
                engine.setAlwaysListening(false)
                engine.setBackgroundServiceActive(false)
                engine.stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                engine.scope.launch { engine.settings.setBackground(false) }
                stopSelf()
            }
            ACTION_STOP_BACKGROUND -> {
                engine.setBackgroundServiceActive(false)
                engine.stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                engine.scope.launch { engine.settings.setBackground(false) }
                stopSelf()
            }
            ACTION_LISTEN -> engine.startListening()
            else -> {
                startAsForeground(engine)
                if (engine.hasMic()) {
                    engine.setBackgroundServiceActive(true)
                    engine.scope.launch { engine.settings.setBackground(true) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(engine: AssistantEngine) {
        if (!engine.hasMic()) {
            engine.setBackgroundServiceActive(false)
            engine.scope.launch { engine.settings.setBackground(false) }
            stopSelf()
            return
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
        val f = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun svc(a: String, code: Int) = PendingIntent.getService(this, code, Intent(this, AssistantService::class.java).setAction(a), f)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), f)
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("JARVIS AI")
            .setContentText("Always Listening is optional and can be turned off in JARVIS settings.")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE) // hides content on a secure lock screen
            .setContentIntent(open)
            .addAction(0, "Listen", svc(ACTION_LISTEN, 1))
            .addAction(0, "Stop", svc(ACTION_STOP, 2))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            // The typed overload was added in Android 10 (API 29).
            startForeground(ID, n)
        }
    }
}
