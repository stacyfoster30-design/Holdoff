package com.holdoff.app.ui.dialer

import android.os.Bundle
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holdoff.app.service.HoldOffInCallService
import com.holdoff.app.ui.theme.HoldOffColors
import com.holdoff.app.ui.theme.HoldOffTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HoldOffTheme {
                InCallScreen(
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
fun InCallScreen(onFinish: () -> Unit) {
    val call by HoldOffInCallService.currentCall.collectAsState()
    val callState by HoldOffInCallService.callState.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var callDuration by remember { mutableLongStateOf(0L) }

    // Timer
    LaunchedEffect(callState) {
        if (callState == HoldOffInCallService.CallState.ACTIVE) {
            while (true) {
                delay(1000)
                callDuration++
            }
        }
    }

    // Auto-close when call ends
    LaunchedEffect(callState) {
        if (callState == HoldOffInCallService.CallState.DISCONNECTED ||
            callState == HoldOffInCallService.CallState.IDLE) {
            delay(1500)
            onFinish()
        }
    }

    val callerName = remember(call) {
        call?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: call?.details?.handle?.schemeSpecificPart
            ?: "Unknown"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HoldOffColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            // Caller avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(HoldOffColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callerName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 40.sp,
                    color = HoldOffColors.Purple,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Caller name
            Text(
                text = callerName,
                style = MaterialTheme.typography.headlineMedium,
                color = HoldOffColors.Text,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            // Status / timer
            Text(
                text = when (callState) {
                    HoldOffInCallService.CallState.RINGING -> "Incoming call"
                    HoldOffInCallService.CallState.DIALING -> "Calling…"
                    HoldOffInCallService.CallState.ACTIVE -> formatDuration(callDuration)
                    HoldOffInCallService.CallState.HOLDING -> "On hold"
                    HoldOffInCallService.CallState.DISCONNECTING,
                    HoldOffInCallService.CallState.DISCONNECTED -> "Call ended"
                    else -> ""
                },
                color = HoldOffColors.TextMuted,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(Modifier.weight(1f))

            // Incoming call — Answer / Decline
            if (callState == HoldOffInCallService.CallState.RINGING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Decline
                    CallActionButton(
                        icon = Icons.Default.CallEnd,
                        label = "Decline",
                        backgroundColor = HoldOffColors.Rose,
                        contentDesc = "Decline incoming call",
                        onClick = { call?.reject(false, null) }
                    )
                    // Answer
                    CallActionButton(
                        icon = Icons.Default.Call,
                        label = "Answer",
                        backgroundColor = HoldOffColors.Green,
                        contentDesc = "Answer incoming call",
                        onClick = { call?.answer(0) }
                    )
                }
            } else {
                // Active call controls
                AnimatedVisibility(visible = !showKeypad) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CallActionButton(
                                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = if (isMuted) "Unmute" else "Mute",
                                backgroundColor = if (isMuted) HoldOffColors.Purple else HoldOffColors.SurfaceVariant,
                                contentDesc = if (isMuted) "Unmute microphone" else "Mute microphone",
                                onClick = {
                                    isMuted = !isMuted
                                    call?.let { c ->
                                        if (isMuted) c.hold() else c.unhold()
                                    }
                                }
                            )
                            CallActionButton(
                                icon = if (isSpeaker) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                label = "Speaker",
                                backgroundColor = if (isSpeaker) HoldOffColors.Purple else HoldOffColors.SurfaceVariant,
                                contentDesc = "Toggle speakerphone",
                                onClick = { isSpeaker = !isSpeaker }
                            )
                            CallActionButton(
                                icon = Icons.Default.Dialpad,
                                label = "Keypad",
                                backgroundColor = HoldOffColors.SurfaceVariant,
                                contentDesc = "Show keypad",
                                onClick = { showKeypad = true }
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            CallActionButton(
                                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (isOnHold) "Resume" else "Hold",
                                backgroundColor = HoldOffColors.SurfaceVariant,
                                contentDesc = if (isOnHold) "Resume call" else "Hold call",
                                onClick = {
                                    isOnHold = !isOnHold
                                    if (isOnHold) call?.hold() else call?.unhold()
                                }
                            )
                            // Spacer
                            Spacer(Modifier.size(72.dp))
                            Spacer(Modifier.size(72.dp))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // End call button
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(HoldOffColors.Rose)
                        .clickable {
                            call?.disconnect()
                            onFinish()
                        }
                        .semantics { contentDescription = "End call" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    contentDesc: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(onClick = onClick)
                .semantics { contentDescription = contentDesc },
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = HoldOffColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
