@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.Alert
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.AlertSeverity

/**
 * مرکزِ هشدار: نمای فرماندهیِ کارگاه. هر چیزی که نیاز به توجه دارد
 * اولویت‌بندی‌شده اینجاست و با یک ضربه به محلِ رفعش می‌رود.
 */
@Composable
fun ActionCenterScreen(
    vm: ActionCenterViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مرکز هشدار") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryHeader(urgent = ui.urgent, total = ui.total)

            if (ui.allClear) {
                AllClear()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(ui.alerts, key = { it.id }) { alert ->
                        AlertCard(alert = alert, onClick = { alert.route?.let(onNavigate) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(urgent: Int, total: Int) {
    val text = when {
        total == 0 -> "کارگاه آرام است"
        urgent > 0 -> "${urgent.fa()} موردِ بحرانی نیاز به رسیدگی دارد"
        else -> "${total.fa()} موردِ نیازمندِ توجه"
    }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (urgent > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun AllClear() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("✅", fontSize = 48.sp)
            Text(
                "همه‌چیز مرتب است",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "هیچ کارِ معطل، کمبود یا بدهیِ نیازمندِ توجهی وجود ندارد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AlertCard(alert: Alert, onClick: () -> Unit) {
    val container = when (alert.severity) {
        AlertSeverity.URGENT -> MaterialTheme.colorScheme.errorContainer
        AlertSeverity.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        AlertSeverity.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when (alert.severity) {
        AlertSeverity.URGENT -> MaterialTheme.colorScheme.onErrorContainer
        AlertSeverity.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        AlertSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val stripe = when (alert.severity) {
        AlertSeverity.URGENT -> MaterialTheme.colorScheme.error
        AlertSeverity.WARN -> MaterialTheme.colorScheme.tertiary
        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .background(stripe)
            )
            Text(alert.icon, fontSize = 22.sp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    alert.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer
                )
                if (alert.detail.isNotBlank()) {
                    Text(
                        alert.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.8f)
                    )
                }
            }
            if (alert.route != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "رفتن",
                    tint = onContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
