package com.holdoff.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.holdoff.app.ui.components.SadieAvatar
import com.holdoff.app.ui.components.SadieSize
import com.holdoff.app.ui.theme.*
import com.holdoff.app.viewmodel.ChatMessage
import com.holdoff.app.viewmodel.CompanionViewModel

/** Sadie chat — your AI companion (and AI Stacy / AI Danny if premium). */
@Composable
fun CompanionScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit,
    isPremium: Boolean,
    vm: CompanionViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        containerColor = MidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SadieAvatar(size = SadieSize.SMALL, isThinking = state.isTyping)
                        Column {
                            Text(
                                when (state.activeCompanion) {
                                    "ai_danny" -> "AI Danny"
                                    "ai_stacy" -> "AI Stacy"
                                    else -> "Sadie"
                                },
                                fontWeight = FontWeight.Bold, color = OnDarkText
                            )
                            Text(
                                if (state.isTyping) "typing\u2026" else "your companion",
                                fontSize = 12.sp, color = OnDarkTextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftLavender)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepPurple)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isPremium) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(SurfaceVariant).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("sadie" to "Sadie \uD83D\uDC9C", "ai_stacy" to "AI Stacy", "ai_danny" to "AI Danny").forEach { (id, label) ->
                        FilterChip(
                            selected = state.activeCompanion == id,
                            onClick = { vm.switchCompanion(id) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VelvetPurple,
                                selectedLabelColor = OnDarkText
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().background(VelvetPurple.copy(alpha = 0.2f)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("\u2728 Unlock AI Stacy + AI Danny", color = OnDarkText, fontSize = 13.sp)
                    TextButton(onClick = onUpgradeClick) { Text("Upgrade", color = GlowPurple) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { CompanionChatBubble(it) }
                if (state.isTyping) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SadieAvatar(size = SadieSize.SMALL, isThinking = true)
                            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(SurfaceVariant).padding(12.dp)) {
                                Text("\u2026", color = OnDarkTextMuted)
                            }
                        }
                    }
                }
            }

            Surface(color = DeepPurple) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = vm::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Talk to Sadie\u2026", color = OnDarkTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GlowPurple,
                            unfocusedBorderColor = DividerColor,
                            focusedTextColor = OnDarkText,
                            unfocusedTextColor = OnDarkText
                        ),
                        maxLines = 5
                    )
                    IconButton(
                        onClick = { vm.sendMessage(state.inputText) },
                        enabled = state.inputText.isNotBlank(),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = VelvetPurple)
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = OnDarkText) }
                }
            }
        }
    }
}

@Composable
private fun CompanionChatBubble(m: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.isFromCompanion) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (m.isFromCompanion) {
            SadieAvatar(size = SadieSize.SMALL)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (m.isFromCompanion) 4.dp else 16.dp,
                    bottomEnd = if (m.isFromCompanion) 16.dp else 4.dp
                ))
                .background(
                    if (m.isFromCompanion) Brush.horizontalGradient(listOf(SurfaceVariant, RoyalPurple))
                    else Brush.horizontalGradient(listOf(VelvetPurple, GlowPurple))
                )
                .padding(14.dp)
        ) {
            Text(m.text, color = OnDarkText, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}
