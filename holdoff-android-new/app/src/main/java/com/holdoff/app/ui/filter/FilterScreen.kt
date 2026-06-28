package com.holdoff.app.ui.filter

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.holdoff.app.api.VerdictResult
import com.holdoff.app.ui.theme.HoldOffColors
import kotlinx.coroutines.delay

// ── Filter screen ─────────────────────────────────────────────────────────────

@Composable
fun FilterScreen(
    initialMessage: String = "",
    onSendAnyway: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: FilterViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf(initialMessage) }
    var contextText by remember { mutableStateOf("") }
    var showContext by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldOffColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = HoldOffColors.Text)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "HoldOff Filter",
                    style = MaterialTheme.typography.titleLarge,
                    color = HoldOffColors.Text,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Should you send this?",
                    style = MaterialTheme.typography.bodySmall,
                    color = HoldOffColors.TextMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Spiral lock banner
        if (uiState.spiralLocked) {
            SpiralLockBanner(lockedUntilMs = uiState.spiralLockedUntilMs)
            Spacer(Modifier.height(16.dp))
        }

        // Message input
        OutlinedTextField(
            value = messageText,
            onValueChange = { messageText = it },
            placeholder = { Text("Paste or type the message you want to send…", color = HoldOffColors.TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HoldOffColors.Purple,
                unfocusedBorderColor = HoldOffColors.SurfaceVariant,
                focusedContainerColor = HoldOffColors.Surface,
                unfocusedContainerColor = HoldOffColors.Surface,
                focusedTextColor = HoldOffColors.Text,
                unfocusedTextColor = HoldOffColors.Text
            ),
            maxLines = 8,
            enabled = !uiState.spiralLocked
        )

        Spacer(Modifier.height(8.dp))

        // Optional context
        TextButton(
            onClick = { showContext = !showContext },
            colors = ButtonDefaults.textButtonColors(contentColor = HoldOffColors.TextMuted)
        ) {
            Icon(
                if (showContext) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Add context (optional)", style = MaterialTheme.typography.labelMedium)
        }

        AnimatedVisibility(visible = showContext) {
            OutlinedTextField(
                value = contextText,
                onValueChange = { contextText = it },
                placeholder = { Text("What's the situation? Who is this person?", color = HoldOffColors.TextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HoldOffColors.Purple,
                    unfocusedBorderColor = HoldOffColors.SurfaceVariant,
                    focusedContainerColor = HoldOffColors.Surface,
                    unfocusedContainerColor = HoldOffColors.Surface,
                    focusedTextColor = HoldOffColors.Text,
                    unfocusedTextColor = HoldOffColors.Text
                ),
                maxLines = 4
            )
        }

        Spacer(Modifier.height(16.dp))

        // Analyze button
        Button(
            onClick = {
                if (messageText.isNotBlank() && !uiState.spiralLocked) {
                    viewModel.analyze(messageText, contextText.takeIf { it.isNotBlank() })
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = "Analyze message with HoldOff AI" },
            enabled = messageText.isNotBlank() && !uiState.isLoading && !uiState.spiralLocked,
            colors = ButtonDefaults.buttonColors(containerColor = HoldOffColors.Purple),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
                Text("Analyzing…", color = Color.White)
            } else {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Analyze", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        // Verdict result
        AnimatedVisibility(
            visible = uiState.result != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            uiState.result?.let { result ->
                Spacer(Modifier.height(24.dp))
                VerdictCard(
                    result = result,
                    spiralCount = uiState.spiralCount,
                    onSendAnyway = { onSendAnyway(messageText) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Verdict card ──────────────────────────────────────────────────────────────

@Composable
fun VerdictCard(
    result: VerdictResult,
    spiralCount: Int,
    onSendAnyway: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    val (verdictColor, verdictBg, verdictIcon) = when (result.verdict) {
        "SEND" -> Triple(HoldOffColors.Green, HoldOffColors.GreenBg, Icons.Default.CheckCircle)
        "WAIT" -> Triple(HoldOffColors.Amber, HoldOffColors.AmberBg, Icons.Default.Schedule)
        else -> Triple(HoldOffColors.Rose, HoldOffColors.RoseBg, Icons.Default.Cancel)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(verdictBg)
            .padding(20.dp)
            .semantics { contentDescription = "Verdict: ${result.verdict}. ${result.explanation}" }
    ) {
        // Verdict badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(verdictIcon, contentDescription = null, tint = verdictColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = result.verdict,
                color = verdictColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
        }

        // Pattern name
        if (result.patternName.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(verdictColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = result.patternName,
                    color = verdictColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Explanation
        Text(
            text = result.explanation,
            color = HoldOffColors.Text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )

        // Reframe
        if (!result.reframe.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = HoldOffColors.Divider)
            Spacer(Modifier.height(12.dp))
            Text(
                "What you're actually feeling",
                color = HoldOffColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.reframe,
                color = HoldOffColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        // Rewrite
        if (!result.rewrite.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = HoldOffColors.Divider)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "A better version",
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(result.rewrite)) },
                    colors = ButtonDefaults.textButtonColors(contentColor = HoldOffColors.Purple)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HoldOffColors.Surface)
                    .padding(12.dp)
            ) {
                Text(
                    text = result.rewrite,
                    color = HoldOffColors.Text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Spiral watch indicator
        if (spiralCount > 0 && result.verdict == "DO NOT SEND") {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(HoldOffColors.Rose.copy(alpha = 0.1f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = HoldOffColors.Rose, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Spiral Watch — $spiralCount/3",
                    color = HoldOffColors.Rose,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Send anyway (only for WAIT/DO NOT SEND)
        if (result.verdict != "SEND") {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onSendAnyway,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HoldOffColors.TextMuted),
                border = BorderStroke(1.dp, HoldOffColors.SurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Send anyway", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Spiral lock banner ────────────────────────────────────────────────────────

@Composable
fun SpiralLockBanner(lockedUntilMs: Long) {
    var remainingMs by remember { mutableLongStateOf(lockedUntilMs - System.currentTimeMillis()) }

    LaunchedEffect(lockedUntilMs) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = lockedUntilMs - System.currentTimeMillis()
        }
    }

    val minutes = (remainingMs / 60_000).coerceAtLeast(0)
    val seconds = ((remainingMs % 60_000) / 1000).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HoldOffColors.RoseBg)
            .padding(16.dp)
            .semantics {
                contentDescription = "Spiral Lock active. Filter locked for $minutes minutes and $seconds seconds."
            }
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = HoldOffColors.Rose)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Spiral Lock Active",
                    color = HoldOffColors.Rose,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "You've gotten 3 DO NOT SEND verdicts in a row. Take a breath. The filter is locked for ${minutes}m ${seconds}s.",
                color = HoldOffColors.Text,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
