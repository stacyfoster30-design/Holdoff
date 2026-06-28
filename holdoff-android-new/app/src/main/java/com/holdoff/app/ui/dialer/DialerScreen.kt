package com.holdoff.app.ui.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holdoff.app.R
import com.holdoff.app.ui.theme.HoldOffColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(modifier: Modifier = Modifier) {
    var dialInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldOffColors.Background)
    ) {
        // ── Tab row: Keypad | Recent ──────────────────────────────────────────
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = HoldOffColors.Surface,
            contentColor = HoldOffColors.Purple,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = HoldOffColors.Purple
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        stringResource(R.string.dialer_title),
                        color = if (selectedTab == 0) HoldOffColors.Purple else HoldOffColors.TextMuted
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        stringResource(R.string.call_history),
                        color = if (selectedTab == 1) HoldOffColors.Purple else HoldOffColors.TextMuted
                    )
                }
            )
        }

        when (selectedTab) {
            0 -> KeypadTab(
                dialInput = dialInput,
                onInputChange = { dialInput = it },
                onCall = { placeCall(context, dialInput) }
            )
            1 -> CallHistoryTab(onCallBack = { number -> placeCall(context, number) })
        }
    }
}

// ── Keypad ────────────────────────────────────────────────────────────────────

@Composable
private fun KeypadTab(
    dialInput: String,
    onInputChange: (String) -> Unit,
    onCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Display
        Text(
            text = formatPhoneNumber(dialInput),
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp
            ),
            color = HoldOffColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics { contentDescription = "Dialed number: $dialInput" }
        )

        Spacer(Modifier.height(8.dp))

        // Delete button
        AnimatedVisibility(visible = dialInput.isNotEmpty()) {
            IconButton(
                onClick = { onInputChange(dialInput.dropLast(1)) },
                modifier = Modifier.semantics {
                    contentDescription = "Delete last digit"
                }
            ) {
                Icon(
                    Icons.Default.Backspace,
                    contentDescription = null,
                    tint = HoldOffColors.TextMuted
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Keypad grid
        val keys = listOf(
            Triple("1", "", ""),
            Triple("2", "ABC", ""),
            Triple("3", "DEF", ""),
            Triple("4", "GHI", ""),
            Triple("5", "JKL", ""),
            Triple("6", "MNO", ""),
            Triple("7", "PQRS", ""),
            Triple("8", "TUV", ""),
            Triple("9", "WXYZ", ""),
            Triple("*", "", ""),
            Triple("0", "+", ""),
            Triple("#", "", "")
        )

        val rows = keys.chunked(3)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters, _) ->
                    DialKey(
                        digit = digit,
                        letters = letters,
                        onClick = {
                            if (dialInput.length < 15) onInputChange(dialInput + digit)
                        },
                        onLongClick = {
                            if (digit == "0") onInputChange(dialInput + "+")
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))

        // Call button
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (dialInput.isNotEmpty()) HoldOffColors.Green else HoldOffColors.SurfaceVariant)
                .clickable(enabled = dialInput.isNotEmpty()) { onCall() }
                .semantics { contentDescription = "Call ${dialInput.ifEmpty { "enter a number first" }}" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = "Dial $digit" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light),
                color = HoldOffColors.Text
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = HoldOffColors.TextMuted,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Call History ──────────────────────────────────────────────────────────────

@Composable
private fun CallHistoryTab(onCallBack: (String) -> Unit) {
    val context = LocalContext.current
    val callLogs = remember { loadCallLogs(context) }

    if (callLogs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.PhoneMissed,
                    contentDescription = null,
                    tint = HoldOffColors.TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "No recent calls",
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(callLogs) { log ->
                CallLogItem(log = log, onCallBack = onCallBack)
            }
        }
    }
}

@Composable
private fun CallLogItem(log: CallLogEntry, onCallBack: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCallBack(log.number) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "${log.name ?: log.number}, ${log.typeLabel}, tap to call back" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(HoldOffColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (log.name?.firstOrNull() ?: log.number.firstOrNull() ?: '?').toString().uppercase(),
                color = HoldOffColors.Purple,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.name ?: formatPhoneNumber(log.number),
                color = HoldOffColors.Text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (log.type) {
                        CallType.INCOMING -> Icons.Default.CallReceived
                        CallType.OUTGOING -> Icons.Default.CallMade
                        CallType.MISSED -> Icons.Default.CallMissed
                    },
                    contentDescription = null,
                    tint = when (log.type) {
                        CallType.MISSED -> HoldOffColors.Rose
                        else -> HoldOffColors.TextMuted
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = log.timeLabel,
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        IconButton(
            onClick = { onCallBack(log.number) },
            modifier = Modifier.semantics { contentDescription = "Call back ${log.name ?: log.number}" }
        ) {
            Icon(Icons.Default.Call, contentDescription = null, tint = HoldOffColors.Purple)
        }
    }
}

// ── Data models and helpers ───────────────────────────────────────────────────

enum class CallType { INCOMING, OUTGOING, MISSED }

data class CallLogEntry(
    val number: String,
    val name: String?,
    val type: CallType,
    val timeLabel: String,
    val typeLabel: String
)

private fun loadCallLogs(context: Context): List<CallLogEntry> {
    val logs = mutableListOf<CallLogEntry>()
    try {
        val cursor = context.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            arrayOf(
                android.provider.CallLog.Calls.NUMBER,
                android.provider.CallLog.Calls.CACHED_NAME,
                android.provider.CallLog.Calls.TYPE,
                android.provider.CallLog.Calls.DATE
            ),
            null, null,
            "${android.provider.CallLog.Calls.DATE} DESC"
        )
        cursor?.use {
            val numberIdx = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
            val nameIdx = it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
            val typeIdx = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
            val dateIdx = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
            var count = 0
            while (it.moveToNext() && count < 50) {
                val number = it.getString(numberIdx) ?: continue
                val name = it.getString(nameIdx)?.takeIf { n -> n.isNotBlank() }
                val type = when (it.getInt(typeIdx)) {
                    android.provider.CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                    android.provider.CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                    else -> CallType.MISSED
                }
                val date = it.getLong(dateIdx)
                logs.add(CallLogEntry(
                    number = number,
                    name = name,
                    type = type,
                    timeLabel = formatCallTime(date),
                    typeLabel = type.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                ))
                count++
            }
        }
    } catch (e: Exception) {
        // Permission not granted yet
    }
    return logs
}

private fun formatCallTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 604_800_000 -> "${diff / 86_400_000}d ago"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

private fun formatPhoneNumber(number: String): String {
    val digits = number.filter { it.isDigit() || it == '+' }
    return when {
        digits.startsWith("+1") && digits.length == 12 ->
            "+1 (${digits.substring(2, 5)}) ${digits.substring(5, 8)}-${digits.substring(8)}"
        digits.length == 10 ->
            "(${digits.substring(0, 3)}) ${digits.substring(3, 6)}-${digits.substring(6)}"
        else -> number
    }
}

private fun placeCall(context: Context, number: String) {
    if (number.isBlank()) return
    val telecomManager = context.getSystemService(TelecomManager::class.java)
    val uri = Uri.fromParts("tel", number, null)
    try {
        telecomManager.placeCall(uri, null)
    } catch (e: SecurityException) {
        // Fallback to intent
        val intent = Intent(Intent.ACTION_CALL, uri)
        context.startActivity(intent)
    }
}
