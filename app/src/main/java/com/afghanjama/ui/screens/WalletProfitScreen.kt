// app/src/main/java/com/afghanjama/ui/screens/WalletProfitScreen.kt
package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.vm.FinanceViewModel

@Composable
fun WalletProfitScreen(vm: FinanceViewModel) {

    val walletBalance by vm.walletBalance.collectAsState(initial = 0L)
    val profitBalance by vm.profitBalance.collectAsState(initial = 0L)
    val txList by vm.tx.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { TopAppBar(title = { Text("کیف پول / فایده") }) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("کیف پول: $walletBalance ؋", style = MaterialTheme.typography.titleMedium)
                    Text("فایده: $profitBalance ؋", style = MaterialTheme.typography.titleMedium)
                }
            }

            Text("تراکنش‌ها", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(txList, key = { it.id }) { t ->
                    Card {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("${t.type} — ${t.source} — ${t.amount} ؋")
                            if (t.note.isNotBlank()) Text(t.note)
                        }
                    }
                }
            }
        }
    }
}
