package com.holdoff.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holdoff.app.domain.companion.CompanionCatalog
import com.holdoff.app.domain.companion.CompanionVariant

/**
 * Lets the user choose one of 8 companions: Sadie × 4 and Dan × 4.
 * Each card shows the same character with a different attachment style
 * so the difference between styles is felt — not just read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionPickerScreen(
    selectedId: String?,
    onSelect: (CompanionVariant) -> Unit,
    onBack: () -> Unit
) {
    var character by remember { mutableStateOf(CompanionVariant.Character.SADIE) }

    Scaffold(
        containerColor = Color(0xFF0B0420),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Choose your companion", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = Color(0xFFB48BFF))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF0B0420)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Same person. Four attachment styles.",
                color = Color(0xFFE6DEFF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "You'll feel the difference. That's the point.",
                color = Color(0xFF9C8FD6),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            // Character toggle (Sadie / Dan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CharacterTab("Sadie", character == CompanionVariant.Character.SADIE) {
                    character = CompanionVariant.Character.SADIE
                }
                CharacterTab("Dan", character == CompanionVariant.Character.DAN) {
                    character = CompanionVariant.Character.DAN
                }
            }

            Spacer(Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(CompanionCatalog.forCharacter(character)) { variant ->
                    CompanionCard(
                        variant = variant,
                        selected = variant.id == selectedId,
                        onSelect = { onSelect(variant) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.CharacterTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) Color(0xFF2A1A5E) else Color(0xFF160A33)
    val fg = if (selected) Color.White else Color(0xFF9C8FD6)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .background(bg, RoundedCornerShape(22.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompanionCard(
    variant: CompanionVariant,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (selected) Color(variant.accentColor) else Color(0xFF2A1A5E)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF160A33), RoundedCornerShape(16.dp))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(variant.accentColor), RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                variant.displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(variant.tagline, color = Color(0xFFE6DEFF), fontSize = 13.sp)

        Spacer(Modifier.height(10.dp))
        Text(
            "How this version sounds",
            color = Color(0xFFB48BFF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        variant.examplePhrases.forEach { phrase ->
            Text("\u201C$phrase\u201D", color = Color(0xFFD9CFFF), fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Style traits",
            color = Color(0xFFB48BFF),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        variant.styleTraits.forEach { trait ->
            Text("• $trait", color = Color(0xFFE6DEFF), fontSize = 13.sp)
        }

        if (selected) {
            Spacer(Modifier.height(12.dp))
            Text(
                "✓ Selected as your companion",
                color = Color(variant.accentColor),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}
