package ca.senseway.pathpaldemo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    const val CHANNEL_EVENTS   = "pathpal_events"
    const val CHANNEL_GEOFENCE = "pathpal_geofence"
    const val CHANNEL_WEATHER  = "pathpal_weather"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                "Device Events",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts and activity events from the tracked device"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_GEOFENCE,
                "Safety Zone Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when the device leaves or enters a safety zone"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WEATHER,
                "Weather Advisories",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Severe weather safety alerts for the device location"
            }
        )
    }

    private fun launchIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun postEvent(ctx: Context, title: String, message: String, notifId: Int) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        try {
            val notif = NotificationCompat.Builder(ctx, CHANNEL_EVENTS)
                .setSmallIcon(R.drawable.ic_notif_event)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchIntent(ctx))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(notifId, notif)
        } catch (_: Exception) { /* permission not granted on API 33+ */ }
    }

    fun postGeofenceExit(ctx: Context, zoneName: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        try {
            val notif = NotificationCompat.Builder(ctx, CHANNEL_GEOFENCE)
                .setSmallIcon(R.drawable.ic_notif_geofence)
                .setContentTitle("Outside Safety Zone")
                .setContentText("Device has left \"$zoneName\"")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Device has left the \"$zoneName\" safety zone. Check the app for their current location."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(launchIntent(ctx))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(10_001, notif)
        } catch (_: Exception) {}
    }

    fun postGeofenceEnter(ctx: Context, zoneName: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        try {
            val notif = NotificationCompat.Builder(ctx, CHANNEL_GEOFENCE)
                .setSmallIcon(R.drawable.ic_notif_geofence)
                .setContentTitle("Back in Safety Zone")
                .setContentText("Device has returned to \"$zoneName\"")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchIntent(ctx))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(10_002, notif)
        } catch (_: Exception) {}
    }

    fun postWeather(ctx: Context, title: String, message: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        try {
            val notif = NotificationCompat.Builder(ctx, CHANNEL_WEATHER)
                .setSmallIcon(R.drawable.ic_notif_weather)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(launchIntent(ctx))
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(20_001, notif)
        } catch (_: Exception) {}
    }
}
