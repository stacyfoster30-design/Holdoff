package com.holdoff.app.ui.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.holdoff.app.ui.theme.HoldOffColors

// ── Data models ───────────────────────────────────────────────────────────────

data class SmsThread(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int
)

data class SmsMessage(
    val id: Long,
    val body: String,
    val isIncoming: Boolean,
    val date: Long
)

// ── Inbox ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(
    onThreadClick: (SmsThread) -> Unit,
    onNewMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val threads = remember { loadSmsThreads(context) }
    val filtered = remember(searchQuery, threads) {
        if (searchQuery.isBlank()) threads
        else threads.filter {
            (it.name ?: it.address).contains(searchQuery, ignoreCase = true) ||
                    it.snippet.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = HoldOffColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Messages", color = HoldOffColors.Text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HoldOffColors.Surface),
                actions = {
                    IconButton(
                        onClick = onNewMessage,
                        modifier = Modifier.semantics { contentDescription = "Compose new message" }
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = HoldOffColors.Purple)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMessage,
                containerColor = HoldOffColors.Purple,
                modifier = Modifier.semantics { contentDescription = "New message" }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations", color = HoldOffColors.TextMuted) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = HoldOffColors.TextMuted)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = HoldOffColors.TextMuted)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HoldOffColors.Purple,
                    unfocusedBorderColor = HoldOffColors.SurfaceVariant,
                    focusedContainerColor = HoldOffColors.Surface,
                    unfocusedContainerColor = HoldOffColors.Surface,
                    focusedTextColor = HoldOffColors.Text,
                    unfocusedTextColor = HoldOffColors.Text
                ),
                singleLine = true
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = HoldOffColors.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isBlank()) "No messages yet" else "No results for \"$searchQuery\"",
                            color = HoldOffColors.TextMuted
                        )
                    }
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.threadId }) { thread ->
                        SmsThreadItem(thread = thread, onClick = { onThreadClick(thread) })
                        HorizontalDivider(color = HoldOffColors.Divider, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsThreadItem(thread: SmsThread, onClick: () -> Unit) {
    val displayName = thread.name ?: thread.address
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics {
                contentDescription = buildString {
                    append(displayName)
                    if (thread.unreadCount > 0) append(", ${thread.unreadCount} unread")
                    append(", ${thread.snippet}")
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HoldOffColors.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.firstOrNull()?.uppercase() ?: "?",
                color = HoldOffColors.Purple,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayName,
                    color = HoldOffColors.Text,
                    fontWeight = if (thread.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSmsTime(thread.date),
                    color = if (thread.unreadCount > 0) HoldOffColors.Purple else HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = thread.snippet,
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (thread.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(HoldOffColors.Purple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = thread.unreadCount.coerceAtMost(99).toString(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// ── Conversation thread ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConversationScreen(
    thread: SmsThread,
    onBack: () -> Unit,
    onAnalyzeMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var composeText by remember { mutableStateOf("") }
    val messages = remember(thread.threadId) { loadMessages(context, thread.threadId) }
    val listState = rememberLazyListState()
    var showFilterHint by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        modifier = modifier,
        containerColor = HoldOffColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(HoldOffColors.SurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (thread.name ?: thread.address).firstOrNull()?.uppercase() ?: "?",
                                color = HoldOffColors.Purple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                thread.name ?: thread.address,
                                color = HoldOffColors.Text,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (thread.name != null) {
                                Text(
                                    thread.address,
                                    color = HoldOffColors.TextMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Go back" }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = HoldOffColors.Text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HoldOffColors.Surface)
            )
        },
        bottomBar = {
            ComposeBar(
                text = composeText,
                onTextChange = {
                    composeText = it
                    showFilterHint = it.length > 10
                },
                onSend = {
                    if (composeText.isNotBlank()) {
                        sendSms(context, thread.address, composeText)
                        composeText = ""
                        showFilterHint = false
                    }
                },
                onAnalyze = {
                    if (composeText.isNotBlank()) onAnalyzeMessage(composeText)
                },
                showFilterHint = showFilterHint
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(message = msg)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: SmsMessage) {
    val alignment = if (message.isIncoming) Alignment.Start else Alignment.End
    val bubbleColor = if (message.isIncoming) HoldOffColors.SurfaceVariant else HoldOffColors.Purple
    val textColor = HoldOffColors.Text
    val shape = if (message.isIncoming)
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "${if (message.isIncoming) "Received" else "Sent"}: ${message.body}"
                }
        ) {
            Text(
                text = message.body,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatSmsTime(message.date),
            color = HoldOffColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ComposeBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAnalyze: () -> Unit,
    showFilterHint: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HoldOffColors.Surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // HoldOff filter hint
        AnimatedVisibility(visible = showFilterHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HoldOffColors.PurpleContainer)
                    .clickable(onClick = onAnalyze)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Analyze message with HoldOff AI before sending" },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = HoldOffColors.Purple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Run HoldOff filter before sending",
                    color = HoldOffColors.Purple,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Type a message…", color = HoldOffColors.TextMuted) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HoldOffColors.Purple,
                    unfocusedBorderColor = HoldOffColors.SurfaceVariant,
                    focusedContainerColor = HoldOffColors.SurfaceVariant,
                    unfocusedContainerColor = HoldOffColors.SurfaceVariant,
                    focusedTextColor = HoldOffColors.Text,
                    unfocusedTextColor = HoldOffColors.Text
                ),
                maxLines = 5
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (text.isNotBlank()) HoldOffColors.Purple else HoldOffColors.SurfaceVariant)
                    .clickable(enabled = text.isNotBlank(), onClick = onSend)
                    .semantics { contentDescription = "Send message" },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ── SmsComposeActivity ────────────────────────────────────────────────────────

// ── Data loading helpers ──────────────────────────────────────────────────────

private fun loadSmsThreads(context: Context): List<SmsThread> {
    val threads = mutableListOf<SmsThread>()
    try {
        val cursor = context.contentResolver.query(
            Uri.parse("content://mms-sms/conversations?simple=true"),
            arrayOf("thread_id", "address", "body", "date", "read"),
            null, null, "date DESC"
        )
        cursor?.use {
            val threadIdIdx = it.getColumnIndex("thread_id")
            val addressIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                val address = it.getString(addressIdx) ?: continue
                val name = lookupContactName(context, address)
                threads.add(SmsThread(
                    threadId = it.getLong(threadIdIdx),
                    address = address,
                    name = name,
                    snippet = it.getString(bodyIdx) ?: "",
                    date = it.getLong(dateIdx),
                    unreadCount = 0
                ))
            }
        }
    } catch (e: Exception) { /* permission not granted */ }
    return threads
}

private fun loadMessages(context: Context, threadId: Long): List<SmsMessage> {
    val messages = mutableListOf<SmsMessage>()
    try {
        val cursor = context.contentResolver.query(
            Uri.parse("content://sms"),
            arrayOf("_id", "body", "type", "date"),
            "thread_id = ?", arrayOf(threadId.toString()),
            "date ASC"
        )
        cursor?.use {
            val idIdx = it.getColumnIndex("_id")
            val bodyIdx = it.getColumnIndex("body")
            val typeIdx = it.getColumnIndex("type")
            val dateIdx = it.getColumnIndex("date")
            while (it.moveToNext()) {
                messages.add(SmsMessage(
                    id = it.getLong(idIdx),
                    body = it.getString(bodyIdx) ?: "",
                    isIncoming = it.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                    date = it.getLong(dateIdx)
                ))
            }
        }
    } catch (e: Exception) { /* permission not granted */ }
    return messages
}

private fun lookupContactName(context: Context, address: String): String? {
    return try {
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address)
        )
        val cursor = context.contentResolver.query(
            uri, arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) { null }
}

private fun sendSms(context: Context, address: String, body: String) {
    try {
        val smsManager = android.telephony.SmsManager.getDefault()
        val parts = smsManager.divideMessage(body)
        if (parts.size == 1) {
            smsManager.sendTextMessage(address, null, body, null, null)
        } else {
            smsManager.sendMultipartTextMessage(address, null, parts, null, null)
        }
    } catch (e: Exception) {
        android.util.Log.e("SmsScreen", "Failed to send SMS: ${e.message}")
    }
}

private fun formatSmsTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
    }
}
