package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DashboardScreen(
    onPurchase: () -> Unit,
    onInventory: () -> Unit,
    onCutting: () -> Unit,
    onCutDone: () -> Unit,
    onSewing: () -> Unit,
    onSales: () -> Unit,
    onWallet: () -> Unit,
    onMaster: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("داشبورد", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(6.dp))

        Button(onClick = onPurchase, modifier = Modifier.fillMaxWidth()) {
            Text("پلان خرید")
        }

        Button(onClick = onInventory, modifier = Modifier.fillMaxWidth()) {
            Text("انبار")
        }

        Button(onClick = onCutting, modifier = Modifier.fillMaxWidth()) {
            Text("اتاق برشکاری (در حال برش)")
        }

        Button(onClick = onCutDone, modifier = Modifier.fillMaxWidth()) {
            Text("برش شده")
        }

        Button(onClick = onSewing, modifier = Modifier.fillMaxWidth()) {
            Text("مدیریت دوخت")
        }

        Button(onClick = onSales, modifier = Modifier.fillMaxWidth()) {
            Text("مدیریت فروش")
        }

        Button(onClick = onWallet, modifier = Modifier.fillMaxWidth()) {
            Text("کیف پول و فایده")
        }

        Button(onClick = onMaster, modifier = Modifier.fillMaxWidth()) {
            Text("مدیریت اطلاعات پایه")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("خروج از حساب")
        }
    }
}
