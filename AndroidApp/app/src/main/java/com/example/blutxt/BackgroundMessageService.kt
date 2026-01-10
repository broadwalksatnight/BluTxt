package com.example.blutxt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import android.R // Import for standard Android resources

object BleManagerProvider {
    var instance: BleManager? = null
}

// Constants for the Foreground Service
private const val FOREGROUND_CHANNEL_ID = "blutxt_foreground_channel" // ID for ongoing service status (LOW importance)
private const val CHAT_CHANNEL_ID = "blutxt_chat_alerts_channel"      // ID for incoming message alerts (HIGH importance)
private const val NOTIFICATION_ID = 101 // ID for the ongoing Foreground Service notification
private const val CHAT_NOTIFICATION_ID = 102 // ID for the new incoming message notification
private const val NOTIFICATION_TITLE = "BluTxt Active"

// --- INTENT ACTIONS/EXTRAS FOR PUSH NOTIFICATIONS ---
const val ACTION_NEW_MESSAGE = "com.example.blutxt.ACTION_NEW_MESSAGE"
const val EXTRA_MESSAGE_CONTENT = "EXTRA_MESSAGE_CONTENT"
// --------------------------------------------------------

class BackgroundMessageService : Service() {

    // 💥 NEW: Companion Object to track if the app is currently visible 💥
    companion object {
        var isAppForeground: Boolean = false // This flag will be set by MainActivity
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("Service", "Service created")

        // Ensure both the LOW and HIGH importance channels are created
        createNotificationChannels()

        val notification = createForegroundNotification()

        // ✅ Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // ✅ NEW: Use shared BleManager instance (keeps scan data across app states)
        if (BleManagerProvider.instance == null) {
            BleManagerProvider.instance = BleManager(applicationContext)
            Log.d("Service", "BleManager instance created in service.")
        } else {
            Log.d("Service", "Using existing BleManager instance.")
        }

        // ✅ NEW: Start background BLE scanning if not already scanning
        try {
            BleManagerProvider.instance?.startScan()
            Log.d("Service", "Background BLE scan started.")
        } catch (e: SecurityException) {
            Log.e("Service", "Permission denied starting BLE scan: ${e.message}")
        }
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Service", "Service started. Connection maintained.")

        // Check for incoming message action
        when (intent?.action) {
            ACTION_NEW_MESSAGE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE_CONTENT)
                if (message != null) {
                    // 💥 CRITICAL CHECK: Only show notification if the app is NOT in the foreground 💥
                    if (!isAppForeground) {
                        sendChatNotification(message)
                    } else {
                        Log.d("Service", "App is foreground, skipping notification for: $message")
                    }
                }
            }
        }

        // This command ensures that if the service is killed by the OS, it will be recreated.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // This is a started service, not a bound service
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("Service", "Service destroyed. Connection lost or manually closed.")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("BackgroundService", "App was swiped away")

        // ✅ Use the global instance
        val bleManager = BleManagerProvider.instance
        bleManager?.sendDisconnectSignal()
        bleManager?.closeGatt()

        stopSelf()
    }



    /**
     * Creates the ongoing notification required for the foreground service status.
     * This uses the LOW importance channel (FOREGROUND_CHANNEL_ID).
     */
    private fun createForegroundNotification() = run {
        // 1. Create the Intent to launch the MainActivity when the notification is tapped
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            // Flags needed to ensure the existing activity is brought to the front
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for modern Android standards
        )

        // 2. Build the Notification using the low-importance foreground channel
        NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText("Maintaining BLE chat connection...")
            .setContentIntent(pendingIntent)
            .setTicker("BluTxt connection established.")
            .build()
    }

    /**
     * Creates and shows a new notification for an incoming chat message.
     * This uses the HIGH importance channel (CHAT_CHANNEL_ID) for banner alerts.
     */
    private fun sendChatNotification(message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ✅ Create an intent that opens MainActivity and passes context about the chat
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_OPEN_CHAT", true)
            putExtra("EXTRA_MESSAGE_CONTENT", message)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1, // unique requestCode (different from proximity notification)
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle("BluTxt Message:")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // ✅ opens chat when tapped
            .build()

        notificationManager.notify(CHAT_NOTIFICATION_ID, notification)
        Log.d("Notification", "New message notification sent: $message")
    }


    /**
     * Creates two distinct Notification Channels (one for foreground status, one for chat alerts).
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Foreground Status Channel (LOW importance, non-intrusive)
            val fgChannelName = "BluTxt Service Status"
            val fgChannelDescription = "Ongoing notification for the foreground service."
            val fgImportance = NotificationManager.IMPORTANCE_LOW

            val fgChannel = NotificationChannel(FOREGROUND_CHANNEL_ID, fgChannelName, fgImportance).apply {
                description = fgChannelDescription
                setShowBadge(false) // Status notification shouldn't count as unread
            }
            notificationManager.createNotificationChannel(fgChannel)


            // 2. Chat Message Alert Channel (HIGH importance, banner/pop-up alert)
            val chatChannelName = "BluTxt Message Alerts"
            val chatChannelDescription = "High importance alerts for incoming chat messages."
            val chatImportance = NotificationManager.IMPORTANCE_HIGH

            val chatChannel = NotificationChannel(CHAT_CHANNEL_ID, chatChannelName, chatImportance).apply {
                description = chatChannelDescription
            }
            notificationManager.createNotificationChannel(chatChannel)
        }
    }
}
