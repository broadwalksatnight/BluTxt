package com.example.blutxt

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.blutxt.R

object NotificationHelper {
    private const val CHANNEL_ID = "ble_user_detected_channel"
    private const val NOTIFICATION_ID_NEARBY_USER = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nearby User Alerts"
            val descriptionText = "Notifies when a nearby BLE user is detected"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH

            val channel = android.app.NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNearbyUserNotification(context: Context, deviceName: String?) {
        val title = "New BluTxt User Nearby!"
        val message = if (!deviceName.isNullOrEmpty())
            "User '$deviceName' has been detected."
        else
            "An anonymous BluTxt user has been detected!"

        // Build Intent to open MainActivity (or handle directly)
        val intent = Intent(context, MainActivity::class.java).apply {
            // If you want MainActivity to receive this intent instead of creating a new instance,
            // use singleTop in the manifest (see below).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_DEVICE_NAME", deviceName)
        }

        // Combine flags in a compatibility-safe way
        val pendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // keep or change to your drawable
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // action when tapped

        with(NotificationManagerCompat.from(context)) {
            notify(NOTIFICATION_ID_NEARBY_USER, builder.build())
        }
    }
}
