package com.holdoff.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HoldOffApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Incoming call channel
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_INCOMING_CALL,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming phone calls"
                    setShowBadge(false)
                }
            )

            // SMS channel
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SMS,
                    "Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for incoming SMS messages"
                }
            )

            // HoldOff filter channel
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_FILTER,
                    "HoldOff Filter",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "AI verdict notifications"
                }
            )
        }
    }

    companion object {
        const val CHANNEL_INCOMING_CALL = "holdoff_incoming_call"
        const val CHANNEL_CALLS = "holdoff_incoming_call"  // alias
        const val CHANNEL_SMS = "holdoff_sms"
        const val CHANNEL_FILTER = "holdoff_filter"
    }
}
