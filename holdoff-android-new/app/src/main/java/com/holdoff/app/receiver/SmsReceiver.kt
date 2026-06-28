package com.holdoff.app.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import com.holdoff.app.HoldOffApplication
import com.holdoff.app.R
import com.holdoff.app.ui.MainActivity

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group messages by sender
        val grouped = messages.groupBy { it.originatingAddress ?: "Unknown" }

        grouped.forEach { (sender, msgs) ->
            val body = msgs.joinToString("") { it.messageBody ?: "" }
            showNotification(context, sender, body)
        }
    }

    private fun showNotification(context: Context, sender: String, body: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_sms", true)
            putExtra("sms_sender", sender)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, sender.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, HoldOffApplication.CHANNEL_SMS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify(sender.hashCode(), notification)
    }
}
