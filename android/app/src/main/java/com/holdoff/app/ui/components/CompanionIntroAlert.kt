package com.holdoff.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-launch alert explaining who Sadie and Dan are,
 * why they're in the app, and what "attachment style variant" means.
 */
@Composable
fun CompanionIntroAlert(
    onChoose: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = Color(0xFF160A33),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE6DEFF),
        title = {
            Text(
                "Meet Sadie & Dan",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    "Sadie is Stacy's alter ego. Dan is Danny's. They're inside HoldOff to keep the founder's story honest — and to give you a companion who actually understands what it's like in those moments before you send the text you'll regret.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "You'll pick one of 8 companions: Sadie × 4 attachment styles, and Dan × 4. Same person each time — the attachment style is what changes. The idea is to feel, viscerally, how attachment shapes the way the same heart shows up.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Not therapy. Not diagnosis. A companion.",
                    color = Color(0xFFB48BFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onChoose) {
                Text("Choose my companion", color = Color(0xFFB48BFF), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Later", color = Color(0xFF9C8FD6))
            }
        }
    )
}
