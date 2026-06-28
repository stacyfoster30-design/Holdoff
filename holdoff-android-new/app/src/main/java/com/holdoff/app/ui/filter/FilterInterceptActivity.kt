package com.holdoff.app.ui.filter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.holdoff.app.ui.theme.HoldOffTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FilterInterceptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val message = intent?.getStringExtra("message") ?: ""
        val recipient = intent?.getStringExtra("recipient") ?: ""

        setContent {
            HoldOffTheme {
                FilterScreen(
                    initialMessage = message,
                    onSendAnyway = { text ->
                        // Return result to calling activity
                        setResult(RESULT_OK, android.content.Intent().apply {
                            putExtra("send_message", text)
                            putExtra("recipient", recipient)
                        })
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
