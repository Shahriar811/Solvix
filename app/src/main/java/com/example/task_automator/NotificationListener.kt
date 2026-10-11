package com.example.task_automator.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NotificationListener : NotificationListenerService() {
    companion object {
        const val ACTION_NOTIFICATION_RECEIVED = "com.example.task_automator.NOTIFICATION_RECEIVED"
        const val EXTRA_NOTIFICATION_TEXT = "notification_text"
        const val EXTRA_NOTIFICATION_APP = "notification_app"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = sbn.packageName
            val notification = sbn.notification
            val extras = notification.extras

            // Extract notification details
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "No Title"
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "No Text"

            // Filter for specific apps
            if (packageName in listOf("com.whatsapp", "org.telegram.messenger", "com.google.android.gm")) {
                // Send notification data to ChatScreen via LocalBroadcastManager
                val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
                    putExtra(EXTRA_NOTIFICATION_APP, packageName)
                    putExtra(EXTRA_NOTIFICATION_TEXT, "$title: $text")
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Optional: Handle notification removal if needed
    }
}
