@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.WarehouseViewModel

private fun fmtDelta(v: Double): String {
    val sign = if (v >= 0) "+" else "−"
    val abs = kotlin.math.abs(v)
    val body = if (abs % 1.0 == 0.0) abs.toLong().toString() else abs.toString()
    return (sign + body).toPersianDigits()
}

@Composable
fun StockLedgerScreen(
    vm: WarehouseViewModel,
    onBack: () -> Unit
) {
    val movements by vm.movements.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گردش انبار (کاردکس)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        if (movements.isEmpty()) {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "هنوز گردشی ثبت نشده. با خرید مواد و مصرف تولید، هر تغییر اینجا با دلیلش ثبت می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(movements, key = { it.id }) { m ->
                val incoming = m.delta >= 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            m.reason + (m.note.takeIf { it.isNotBlank() }?.let { " • $it" } ?: "") +
                                " • " + PersianDate.shortWithTime(m.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${fmtDelta(m.delta)} ${m.unit}",
                        fontWeight = FontWeight.Bold,
                        color = if (incoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp)
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
