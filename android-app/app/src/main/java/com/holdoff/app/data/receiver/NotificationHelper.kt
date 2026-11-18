package com.holdoff.app.data.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.holdoff.app.MainActivity
import com.holdoff.app.R

/**
 * Posts a local notification when an incoming SMS has been picked up by HoldOff.
 * The notification tells the user that Sadie has a read on the message and
 * tapping it opens the app.
 */
object NotificationHelper {

    private const val CHANNEL_ID   = "holdoff_incoming"
    private const val CHANNEL_NAME = "Incoming Message Insights"
    private const val NOTIFICATION_ID = 1001

    fun showIncomingAlert(context: Context, contactName: String, phone: String) {
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_phone", phone)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            phone.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Sadie has a read on this")
            .setContentText("New message from $contactName. Tap to see Sadie's take.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Use a per-contact notification ID so multiple senders don't collapse into one
        nm.notify(NOTIFICATION_ID + phone.hashCode(), notification)
    }

    /** Spiral Lock was released by a trusted contact's unlock text. */
    fun showLockReleased(context: Context, contactName: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Spiral Lock released")
            .setContentText("$contactName unlocked your messaging. Take your time.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2001, notification)
    }

    /** A respond-via-message request arrived for [phone]; tapping opens the app. */
    fun showRespondPrompt(context: Context, phone: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_phone", phone)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, phone.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Reply via HoldOff")
            .setContentText("You were asked to reply to $phone. Tap to open HoldOff — your reply goes through the usual check first.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID + phone.hashCode(), notification)
    }

    /** An MMS arrived; full MMS display is not yet implemented (Phase 1 note). */
    fun showMmsAlert(context: Context) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Picture message received")
            .setContentText("HoldOff can't display MMS content yet — open your previous messaging app to view it.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(2002, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "HoldOff alerts for incoming messages Sadie has analyzed."
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
