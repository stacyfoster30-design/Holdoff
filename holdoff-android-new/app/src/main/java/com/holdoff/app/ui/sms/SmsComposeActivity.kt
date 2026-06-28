package com.holdoff.app.ui.sms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.holdoff.app.ui.MainActivity

/**
 * SmsComposeActivity — handles android.intent.action.SENDTO intents (sms:, smsto: schemes).
 * Required to be declared as the default SMS app. Delegates to MainActivity.
 */
class SmsComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Extract the recipient from the intent URI and forward to MainActivity
        val recipient = intent?.data?.schemeSpecificPart?.trimStart('/')
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_compose", true)
            if (!recipient.isNullOrBlank()) putExtra("compose_recipient", recipient)
        }
        startActivity(mainIntent)
        finish()
    }
}
