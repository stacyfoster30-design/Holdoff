package com.holdoff.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.holdoff.app.domain.quiz.AttachmentQuiz
import com.holdoff.app.domain.quiz.QuizAnswer

@Composable
fun QuizScreen(onComplete: (com.holdoff.app.domain.quiz.QuizResult) -> Unit) {
    val answers = remember { mutableStateMapOf<Int, Int>() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Attachment-style check-in",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text("16 quick questions. No right answers. Honest > pretty.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(AttachmentQuiz.QUESTIONS) { q ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(q.text, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                (1..5).forEach { v ->
                                    FilledTonalButton(
                                        onClick = { answers[q.id] = v },
                                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                                    ) { Text(v.toString()) }
                                }
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    val list = answers.map { (id, v) -> QuizAnswer(id, v) }
                    onComplete(AttachmentQuiz.score(list))
                },
                enabled = answers.size == AttachmentQuiz.QUESTIONS.size,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(if (answers.size == AttachmentQuiz.QUESTIONS.size)
                    "See my result" else "Answer all ${AttachmentQuiz.QUESTIONS.size} to continue")
            }
        }
    }
}
