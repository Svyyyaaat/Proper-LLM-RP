package com.properrp.app.story

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.properrp.app.R

/**
 * Keeps the app running while a turn is being written. Without it Android freezes or kills a backgrounded
 * app after a short while and the connection to Claude drops; a foreground service (with a quiet
 * notification) tells Android this is work the user is waiting for. Started and stopped by StoryController.
 */
class TurnService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val app = (application as com.properrp.app.ProperRpApp).state
        val n = notification(this, intent?.getStringExtra(EXTRA_TEXT) ?: app.text(R.string.step_writer), app)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "story"
        private const val ID = 7
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TEXT = "text"

        /** While a turn runs (the app is in the foreground when it starts: the user just tapped). */
        fun start(context: Context, text: String) {
            runCatching {
                context.startForegroundService(Intent(context, TurnService::class.java).putExtra(EXTRA_TEXT, text))
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, TurnService::class.java).setAction(ACTION_STOP)) }
        }

        private fun notification(context: Context, text: String, app: com.properrp.app.AppState): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, app.text(R.string.notify_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = app.text(R.string.notify_channel_about)
                    setShowBadge(false)
                })
            }
            val open = PendingIntent.getActivity(context, 0,
                context.packageManager.getLaunchIntentForPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
            return Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(app.text(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
        }
    }
}
