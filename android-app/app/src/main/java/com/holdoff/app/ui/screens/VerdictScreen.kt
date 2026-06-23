package com.holdoff.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.holdoff.app.data.model.Verdict
import com.holdoff.app.ui.components.SadieAvatar
import com.holdoff.app.ui.components.SadieSize
import com.holdoff.app.ui.components.VerdictBadge
import com.holdoff.app.ui.theme.*
import com.holdoff.app.viewmodel.ThreadViewModel

/** The Hold Off / Reach Out / Maybe verdict screen. */
@Composable
fun VerdictScreen(
    threadId: String,
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit,
    vm: ThreadViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(threadId) { if (state.verdict == null) vm.analyzeThread(threadId) }

    val infinite = rememberInfiniteTransition(label = "pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )

    Scaffold(
        containerColor = MidnightNavy,
        topBar = {
            TopAppBar(
                title = { Text("The Verdict", fontWeight = FontWeight.Bold, color = OnDarkText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = SoftLavender)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MidnightNavy)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(MidnightNavy, DeepPurple)))
        ) {
            if (state.isAnalyzing || state.verdict == null) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.scale(scale)) { SadieAvatar(size = SadieSize.LARGE, isThinking = true) }
                    Spacer(Modifier.height(24.dp))
                    Text("Reading the patterns\u2026", color = OnDarkText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Sadie is analyzing your conversation", color = OnDarkTextMuted)
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(color = GlowPurple)
                }
            } else {
                val verdict = state.verdict!!
                val (emoji, headline, bgColors) = when (verdict.verdict) {
                    Verdict.HOLD_OFF  -> Triple("\uD83D\uDED1", "Hold Off.",            listOf(DeepPurple, RomanticBlue))
                    Verdict.REACH_OUT -> Triple("\uD83D\uDC9A", "Reach Out.",           listOf(DeepPurple, Color(0xFF0D2B0D)))
                    Verdict.MAYBE     -> Triple("\uD83E\uDD14", "Proceed with Care.",   listOf(DeepPurple, Color(0xFF2B1F0D)))
                }

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(32.dp))
                            .background(Brush.radialGradient(bgColors)),
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 64.sp) }
                    Spacer(Modifier.height(20.dp))
                    Text(headline, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = OnDarkText)
                    Spacer(Modifier.height(8.dp))
                    VerdictBadge(verdict.verdict)
                    Spacer(Modifier.height(8.dp))
                    Text("Confidence: ${(verdict.confidence * 100).toInt()}%", color = OnDarkTextMuted, fontSize = 13.sp)

                    Spacer(Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("\uD83D\uDCAC What Sadie sees:", fontWeight = FontWeight.SemiBold, color = GlowPurple)
                            Spacer(Modifier.height(8.dp))
                            Text(verdict.reasoning, color = OnDarkText, lineHeight = 22.sp)
                        }
                    }

                    if (verdict.patternInsights.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = RoyalPurple.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("\uD83E\uDDE0 Patterns detected:", fontWeight = FontWeight.SemiBold, color = SoftLavender)
                                Spacer(Modifier.height(8.dp))
                                verdict.patternInsights.forEach {
                                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("\u2022  ", color = GlowPurple)
                                        Text(it, color = OnDarkText)
                                    }
                                }
                            }
                        }
                    }

                    if (verdict.suggestedResponse == null) {
                        Spacer(Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = VelvetPurple.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("\u2728 Premium", fontWeight = FontWeight.Bold, color = GlowPurple)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Get Sadie's suggested response, attachment style deep-dive, and pattern history.",
                                    color = OnDarkTextMuted, textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = onUpgradeClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = VelvetPurple),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Unlock Premium \u2014 Subscribe Now") }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(
                        "\u26A0\uFE0F HoldOff is not therapy, not a diagnosis, and not a substitute for professional mental health care.",
                        color = OnDarkTextMuted, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
