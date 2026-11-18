package com.holdoff.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.holdoff.app.data.model.Message
import com.holdoff.app.data.sms.DefaultSmsManager
import com.holdoff.app.ui.components.VerdictBadge
import com.holdoff.app.ui.theme.*
import com.holdoff.app.viewmodel.SendGateState
import com.holdoff.app.viewmodel.ThreadViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One SMS thread — messages, the verdict-gated composer, the Spiral
 * Lock banner, and this conversation's hold queue.
 *
 * The composer's Send button runs the pre-send gate
 * (ThreadViewModel.sendCurrentDraft): verdict first, send second.
 * The Psychology button keeps the original analyze-only flow.
 */
@Composable
fun ThreadDetailScreen(
    threadId: String,
    onVerdictClick: () -> Unit,
    onBack: () -> Unit,
    vm: ThreadViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(threadId) { vm.loadThread(threadId, "Contact") }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    // Tick the lock countdown while a lock is active.
    LaunchedEffect(state.lockState.locked) {
        while (state.lockState.locked) {
            delay(1000)
            vm.refreshLock()
        }
    }

    var isDefaultApp by remember { mutableStateOf(DefaultSmsManager.isDefaultSmsApp(context)) }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isDefaultApp = DefaultSmsManager.isDefaultSmsApp(context) }

    Scaffold(
        containerColor = MidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.contactName, fontWeight = FontWeight.Bold, color = OnDarkText)
                        state.verdict?.let { VerdictBadge(it.verdict) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftLavender)
                    }
                },
                actions = {
                    Button(
                        onClick = { vm.analyzeThread(threadId); onVerdictClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = VelvetPurple),
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) { Text("Analyze", fontSize = 12.sp) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightNavy)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages) { MessageBubble(it) }
            }

            if (!isDefaultApp) {
                DefaultAppBanner(onMakeDefault = {
                    roleLauncher.launch(DefaultSmsManager.createRoleRequestIntent(context))
                })
            }

            if (state.lockState.locked) {
                LockBanner(
                    remainingMs = state.lockState.remainingMs,
                    onRelease = vm::releaseLockWithCalmSentence
                )
            }

            GateStatus(vm = vm)

            if (state.heldDrafts.isNotEmpty()) {
                HeldDraftsList(
                    drafts = state.heldDrafts,
                    locked = state.lockState.locked,
                    onSend = vm::sendHeldDraft,
                    onDiscard = vm::discardHeldDraft
                )
            }

            Surface(color = DeepPurple) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.draftMessage,
                        onValueChange = vm::updateDraft,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Draft a message\u2026", color = OnDarkTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlowPurple,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = OnDarkText,
                            unfocusedTextColor = OnDarkText
                        ),
                        maxLines = 4
                    )
                    IconButton(
                        onClick = { vm.analyzeThread(threadId); onVerdictClick() },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceVariant)
                    ) {
                        Icon(Icons.Default.Psychology, "Ask Sadie", tint = OnDarkText)
                    }
                    IconButton(
                        onClick = { vm.sendCurrentDraft(threadId) },
                        enabled = state.draftMessage.isNotBlank() &&
                            state.gate !is SendGateState.Checking,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = VelvetPurple)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send — Sadie checks it first", tint = OnDarkText)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultAppBanner(onMakeDefault: () -> Unit) {
    Surface(color = RoyalPurple.copy(alpha = 0.5f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "HoldOff isn't your texting app yet — verdicts work, but real holding and sending need it.",
                color = OnDarkText, fontSize = 12.sp, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onMakeDefault) {
                Text("Make it my texting app", color = GlowPurple, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LockBanner(remainingMs: Long, onRelease: (String) -> Unit) {
    var calmText by remember { mutableStateOf("") }
    val minutes = (remainingMs / 60000).toInt()
    val seconds = ((remainingMs % 60000) / 1000).toInt()
    Surface(color = Color(0xFF3B0A0A)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                "\uD83D\uDD12 Spiral Lock is on — sending is paused (trusted contacts still reachable). " +
                    "Time left: ${minutes}m ${seconds}s",
                color = Color(0xFFFCA5A5), fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = calmText,
                    onValueChange = { calmText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("To unlock early, type \"unlock\" in a full, calm sentence\u2026",
                            color = OnDarkTextMuted, fontSize = 12.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowPurple,
                        unfocusedBorderColor = DividerColor,
                        focusedTextColor = OnDarkText,
                        unfocusedTextColor = OnDarkText
                    ),
                    maxLines = 2
                )
                Button(
                    onClick = { onRelease(calmText) },
                    colors = ButtonDefaults.buttonColors(containerColor = VelvetPurple)
                ) { Text("Unlock", fontSize = 12.sp) }
            }
        }
    }
}

/** Renders the send gate's current state above the composer. */
@Composable
private fun GateStatus(vm: ThreadViewModel) {
    val state by vm.state.collectAsState()
    when (val gate = state.gate) {
        is SendGateState.Idle -> Unit
        is SendGateState.Checking -> {
            Surface(color = SurfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = GlowPurple, modifier = Modifier.size(16.dp))
                    Text("Sadie is checking before this sends\u2026", color = OnDarkText, fontSize = 13.sp)
                }
            }
        }
        is SendGateState.Sent -> {
            Surface(color = Color(0xFF0D2B0D)) {
                Text(
                    "\u2705 Sent.",
                    color = Color(0xFF86EFAC), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
        is SendGateState.PausedYellow -> {
            Surface(color = Color(0xFF2B1F0D)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("\uD83E\uDD14 Sadie says pause.", color = Color(0xFFFCD34D),
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(gate.verdict.reasoning, color = OnDarkText, fontSize = 13.sp)
                    if (gate.verdict.suggestedResponse != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Sadie's version: \"${gate.verdict.suggestedResponse}\"",
                            color = SoftLavender, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::confirmSendAfterPause,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))
                        ) { Text("Send anyway", fontSize = 12.sp) }
                        if (gate.verdict.suggestedResponse != null) {
                            OutlinedButton(onClick = vm::useRewrite) {
                                Text("Use rewrite", color = GlowPurple, fontSize = 12.sp)
                            }
                        }
                        OutlinedButton(onClick = vm::holdAfterPause) {
                            Text("Hold it", color = SoftLavender, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        is SendGateState.Held -> {
            Surface(color = Color(0xFF2B1010)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("\uD83D\uDED1 Held — not sent.", color = Color(0xFFFCA5A5),
                        fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(gate.reason, color = OnDarkText, fontSize = 13.sp)
                    Text("It's saved in your hold queue below — send or discard it when you're ready.",
                        color = OnDarkTextMuted, fontSize = 12.sp)
                }
            }
        }
        is SendGateState.BlockedByLock -> {
            Surface(color = Color(0xFF3B0A0A)) {
                Text(
                    "\uD83D\uDD12 Spiral Lock is on — this send is blocked. Messages to trusted contacts still go through.",
                    color = Color(0xFFFCA5A5), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
        is SendGateState.Failed -> {
            Surface(color = Color(0xFF2B1010)) {
                Text(
                    "\u26A0\uFE0F ${gate.reason}",
                    color = Color(0xFFFCA5A5), fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun HeldDraftsList(
    drafts: List<com.holdoff.app.data.entity.DraftEntity>,
    locked: Boolean,
    onSend: (com.holdoff.app.data.entity.DraftEntity) -> Unit,
    onDiscard: (com.holdoff.app.data.entity.DraftEntity) -> Unit
) {
    Surface(color = DeepPurple) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "\uD83D\uDCE5 On hold in this conversation (${drafts.size})",
                color = SoftLavender, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            drafts.forEach { draft ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        draft.messageBody,
                        color = OnDarkText, fontSize = 13.sp,
                        modifier = Modifier.weight(1f), maxLines = 2
                    )
                    TextButton(
                        onClick = { onSend(draft) },
                        enabled = !locked
                    ) { Text("Send", color = GlowPurple, fontSize = 12.sp) }
                    TextButton(onClick = { onDiscard(draft) }) {
                        Text("Discard", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            }
            if (locked) {
                Text("Locked — held messages wait until Spiral Lock ends.",
                    color = OnDarkTextMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(m: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (m.isOutgoing) 16.dp else 4.dp,
                    bottomEnd = if (m.isOutgoing) 4.dp else 16.dp
                ))
                .background(
                    if (m.isOutgoing) Brush.horizontalGradient(listOf(VelvetPurple, GlowPurple))
                    else Brush.horizontalGradient(listOf(SurfaceVariant, RoyalPurple))
                )
                .padding(12.dp)
        ) {
            Column {
                Text(m.body, color = OnDarkText, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(m.timestamp)),
                    color = OnDarkTextMuted, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
