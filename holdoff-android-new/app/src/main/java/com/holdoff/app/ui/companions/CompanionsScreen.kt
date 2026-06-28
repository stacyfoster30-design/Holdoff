package com.holdoff.app.ui.companions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.holdoff.app.ui.theme.HoldOffColors

// ── Companion data ────────────────────────────────────────────────────────────

data class Companion(
    val id: String,
    val name: String,
    val tagline: String,
    val color: Color,
    val avatarUrl: String,
    val expressionUrls: Map<String, String>  // "neutral", "happy", "thinking"
)

val companions = listOf(
    Companion(
        id = "sadie",
        name = "Sadie ✨",
        tagline = "Secure-leaning guide. Warm, grounded, gently challenging.",
        color = HoldOffColors.CompanionSadie,
        avatarUrl = "https://your-holdoff-backend.manus.space/manus-storage/sadie-neutral.png",
        expressionUrls = mapOf(
            "neutral" to "https://your-holdoff-backend.manus.space/manus-storage/sadie-neutral.png",
            "happy" to "https://your-holdoff-backend.manus.space/manus-storage/sadie-happy.png",
            "thinking" to "https://your-holdoff-backend.manus.space/manus-storage/sadie-thinking.png"
        )
    ),
    Companion(
        id = "stacy",
        name = "Stacy",
        tagline = "Fearful-avoidant. Raw honesty and dark humor.",
        color = HoldOffColors.CompanionStacy,
        avatarUrl = "https://your-holdoff-backend.manus.space/manus-storage/stacy-neutral.png",
        expressionUrls = mapOf(
            "neutral" to "https://your-holdoff-backend.manus.space/manus-storage/stacy-neutral.png",
            "happy" to "https://your-holdoff-backend.manus.space/manus-storage/stacy-happy.png",
            "thinking" to "https://your-holdoff-backend.manus.space/manus-storage/stacy-thinking.png"
        )
    ),
    Companion(
        id = "danny",
        name = "Danny",
        tagline = "Dismissive-avoidant. Calm, logical, decodes the other side.",
        color = HoldOffColors.CompanionDanny,
        avatarUrl = "https://your-holdoff-backend.manus.space/manus-storage/danny-neutral.png",
        expressionUrls = mapOf(
            "neutral" to "https://your-holdoff-backend.manus.space/manus-storage/danny-neutral.png",
            "happy" to "https://your-holdoff-backend.manus.space/manus-storage/danny-happy.png",
            "thinking" to "https://your-holdoff-backend.manus.space/manus-storage/danny-thinking.png"
        )
    ),
    Companion(
        id = "dan",
        name = "Dan",
        tagline = "The hype coach. Direct, energetic, no-nonsense.",
        color = HoldOffColors.CompanionDan,
        avatarUrl = "https://your-holdoff-backend.manus.space/manus-storage/dan-neutral.png",
        expressionUrls = mapOf(
            "neutral" to "https://your-holdoff-backend.manus.space/manus-storage/dan-neutral.png",
            "happy" to "https://your-holdoff-backend.manus.space/manus-storage/dan-happy.png",
            "thinking" to "https://your-holdoff-backend.manus.space/manus-storage/dan-thinking.png"
        )
    )
)

// ── Companion selection screen ────────────────────────────────────────────────

@Composable
fun CompanionsScreen(
    onSelectCompanion: (Companion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldOffColors.Background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Companions",
            style = MaterialTheme.typography.headlineSmall,
            color = HoldOffColors.Text,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Text(
            "Choose who you want to talk to.",
            color = HoldOffColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        companions.forEach { companion ->
            CompanionCard(companion = companion, onClick = { onSelectCompanion(companion) })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CompanionCard(companion: Companion, onClick: () -> Unit) {
    // Floating animation
    val infiniteTransition = rememberInfiniteTransition(label = "float_${companion.id}")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${companion.name}: ${companion.tagline}. Tap to chat." },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = HoldOffColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated avatar
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .offset(y = floatOffset.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(companion.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${companion.name} avatar",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                // Glow ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(2.dp, companion.color.copy(alpha = 0.5f), CircleShape)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = companion.name,
                    color = companion.color,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = companion.tagline,
                    color = HoldOffColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = HoldOffColors.TextMuted
            )
        }
    }
}

// ── Companion chat screen ─────────────────────────────────────────────────────

data class ChatMessage(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionChatScreen(
    companion: Companion,
    onBack: () -> Unit,
    viewModel: CompanionChatViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldOffColors.Background)
    ) {
        // Top bar with avatar
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Animated floating avatar in header
                    AnimatedAvatarHeader(companion = companion, expression = uiState.currentExpression)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(companion.name, color = companion.color, fontWeight = FontWeight.Bold)
                        Text(
                            if (uiState.isTyping) "typing…" else companion.tagline,
                            color = HoldOffColors.TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Go back", tint = HoldOffColors.Text)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = HoldOffColors.Surface)
        )

        // Mental health disclaimer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(companion.color.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                "${companion.name.substringBefore(" ")} is an AI companion, not a licensed therapist. If you're in crisis, call or text 988.",
                color = HoldOffColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.semantics {
                    contentDescription = "Mental health disclaimer: ${companion.name} is an AI companion, not a licensed therapist. If you're in crisis, call or text 988."
                }
            )
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Welcome message
            if (uiState.messages.isEmpty()) {
                item {
                    WelcomeMessage(companion = companion)
                }
            }

            items(uiState.messages) { msg ->
                ChatBubble(message = msg, companion = companion)
            }

            if (uiState.isTyping) {
                item { TypingIndicator(companion = companion) }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HoldOffColors.Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Talk to ${companion.name.substringBefore(" ")}…",
                        color = HoldOffColors.TextMuted
                    )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = companion.color,
                    unfocusedBorderColor = HoldOffColors.SurfaceVariant,
                    focusedContainerColor = HoldOffColors.SurfaceVariant,
                    unfocusedContainerColor = HoldOffColors.SurfaceVariant,
                    focusedTextColor = HoldOffColors.Text,
                    unfocusedTextColor = HoldOffColors.Text
                ),
                maxLines = 4
            )

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (inputText.isNotBlank()) companion.color else HoldOffColors.SurfaceVariant)
                    .clickable(enabled = inputText.isNotBlank() && !uiState.isTyping) {
                        viewModel.sendMessage(companion.id, inputText)
                        inputText = ""
                    }
                    .semantics { contentDescription = "Send message to ${companion.name}" },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ── Animated avatar header ────────────────────────────────────────────────────

@Composable
private fun AnimatedAvatarHeader(companion: Companion, expression: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "header_float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    val avatarUrl = companion.expressionUrls[expression] ?: companion.avatarUrl

    Box(
        modifier = Modifier
            .size(40.dp)
            .offset(y = floatOffset.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(300)
                .build(),
            contentDescription = "${companion.name} ${expression} expression",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.5.dp, companion.color.copy(alpha = 0.6f), CircleShape)
        )
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage, companion: Companion) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) HoldOffColors.Purple else HoldOffColors.SurfaceVariant
    val shape = if (isUser)
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    else
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics {
                    contentDescription = "${if (isUser) "You" else companion.name}: ${message.content}"
                }
        ) {
            Text(
                text = message.content,
                color = HoldOffColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun WelcomeMessage(companion: Companion) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large floating avatar
        val infiniteTransition = rememberInfiniteTransition(label = "welcome_float")
        val floatOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -10f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "float"
        )
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(y = floatOffset.dp)
                .scale(scale)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(companion.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "${companion.name} avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(3.dp, companion.color.copy(alpha = 0.4f), CircleShape)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(companion.name, color = companion.color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(companion.tagline, color = HoldOffColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TypingIndicator(companion: Companion) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "d1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(400, delayMillis = 133), RepeatMode.Reverse),
        label = "d2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(400, delayMillis = 266), RepeatMode.Reverse),
        label = "d3"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            .background(HoldOffColors.SurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "${companion.name} is typing" }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(dot1, dot2, dot3).forEach { offset ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(y = offset.dp)
                        .clip(CircleShape)
                        .background(companion.color.copy(alpha = 0.7f))
                )
            }
        }
    }
}
