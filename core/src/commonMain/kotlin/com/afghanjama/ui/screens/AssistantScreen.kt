@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.AccentCard
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.vm.AssistantViewModel
import com.afghanjama.ui.vm.Insight
import com.afghanjama.ui.vm.InsightTone
import com.afghanjama.ui.vm.answerOffline
import com.afghanjama.ui.vm.assistantSuggestions

/**
 * «دستیار» — خلاصهٔ کسب‌وکار و پاسخ به پرسش‌ها به زبانِ ساده، از مغزهای
 * همین اپ. **کاملاً آفلاین** — هیچ‌چیز از دستگاه بیرون نمی‌رود.
 */
@Composable
fun AssistantScreen(vm: AssistantViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "خلاصهٔ کسب‌وکارت و پاسخ به پرسش‌هایت، از روی دفترِ خودت — کاملاً آفلاین، چیزی بیرون نمی‌رود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // جعبهٔ پرسش + پیشنهادها.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("به زبانِ خودت بپرس") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotBlank()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "پاک کردن")
                                }
                            }
                        },
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        assistantSuggestions.forEach { s ->
                            AssistChip(onClick = { query = s }, label = { Text(s) })
                        }
                    }
                }
            }

            // پاسخِ پرسش (اگر چیزی تایپ شده).
            if (query.isNotBlank()) {
                item {
                    val answer = answerOffline(query, data)
                    AccentCard(container = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            answer ?: "این را نفهمیدم. یکی از پیشنهادهای بالا را بزن.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            item {
                Text(
                    "خلاصهٔ امروز",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(data.insights) { InsightCard(it) }
        }
    }
}

@Composable
private fun InsightCard(i: Insight) {
    val tone = when (i.tone) {
        InsightTone.GOOD -> MaterialTheme.colorScheme.primary
        InsightTone.WATCH -> MaterialTheme.colorScheme.secondary
        InsightTone.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AppCard(spacing = 6.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(tone))
            Text(i.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(i.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
