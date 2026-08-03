@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.WorkerPrefs
import com.afghanjama.ui.components.EmptyState
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.MyWorkViewModel

/**
 * «کارِ من» — نمای خودِ خیاط/ناظر. داشبوردِ مدیریتی برای کسی که پای
 * چرخ نشسته بی‌فایده است؛ او فقط باید بداند چه چیزی زیرِ دست دارد،
 * این هفته چه تحویل داده، چقدر طلبکار است و کارش چطور بوده.
 */
@Composable
fun MyWorkScreen(
    vm: MyWorkViewModel,
    tailorLabels: List<String>,
    onBack: () -> Unit
) {
    val settings = LocalSettings.current
    val ui by vm.ui.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    var pickerOpen by remember { mutableStateOf(false) }
    var myLabel by remember { mutableStateOf(WorkerPrefs.myLabel(settings)) }

    LaunchedEffect(myLabel) { vm.setLabel(myLabel) }

    if (pickerOpen) {
        AlertDialog(
            onDismissRequest = { pickerOpen = false },
            title = { Text("شما کدام هستید؟") },
            text = {
                if (tailorLabels.isEmpty()) {
                    Text("هنوز خیاطی در اطلاعات پایه ثبت نشده است.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(tailorLabels, key = { it }) { label ->
                            TextButton(
                                onClick = {
                                    WorkerPrefs.setMyLabel(settings, label)
                                    myLabel = label
                                    pickerOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(label) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("بستن") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کارِ من") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "برگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!ui.identified) {
                item {
                    EmptyState(
                        icon = Icons.Default.PersonSearch,
                        title = "هنوز مشخص نکرده‌اید شما کدام هستید",
                        hint = "یک بار نامتان را انتخاب کنید تا از این به بعد " +
                            "فقط کارِ خودتان را ببینید."
                    )
                }
                item {
                    Button(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("انتخاب نام من")
                    }
                }
                return@LazyColumn
            }

            // ---------- خلاصهٔ من ----------
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val onColor = MaterialTheme.colorScheme.onPrimaryContainer
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                ui.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onColor
                            )
                            ui.score?.takeIf { it.stars > 0 }?.let { sc ->
                                Text(
                                    "★".repeat(sc.stars) + "☆".repeat(5 - sc.stars),
                                    color = onColor
                                )
                            }
                        }
                        Text(
                            "زیرِ دست: ${ui.inProgress.size.fa()} کار (${ui.inProgressPieces.fa()} عدد)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = onColor
                        )
                        Text(
                            "این هفته: ${ui.weekPieces.fa()} عدد تحویل • ${ui.weekWage.afn()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = onColor
                        )
                        if (ui.unpaidWage > 0) {
                            Text(
                                "💰 طلبِ تسویه‌نشدهٔ من: ${ui.unpaidWage.afn()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onColor
                            )
                        }
                    }
                }
            }

            // ---------- کارهای زیرِ دست ----------
            item {
                Text(
                    "کارهای زیرِ دست",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (ui.inProgress.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.CheckCircle,
                        title = "کارِ ناتمامی ندارید",
                        hint = "هر کاری که به شما تحویل داده شود اینجا نشان داده می‌شود."
                    )
                }
            }

            items(ui.inProgress, key = { "p-${it.id}" }) { a ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OrderCodeLine(
                                designCode = designCodeByOrder[a.orderCode].orEmpty(),
                                orderCode = a.orderCode,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${a.qty.fa()} عدد",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            "کارمزد: ${a.totalWage.afn()} (هر عدد ${a.unitWage.afn()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "تحویل‌گرفته: ${PersianDate.short(a.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------- تحویل‌های این هفته ----------
            if (ui.doneThisWeek.isNotEmpty()) {
                item {
                    Text(
                        "تحویل‌های این هفته",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                items(ui.doneThisWeek, key = { "d-${it.id}" }) { a ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OrderCodeLine(
                                designCode = designCodeByOrder[a.orderCode].orEmpty(),
                                orderCode = a.orderCode,
                                trailing = "${a.qty.fa()} عدد",
                                compact = true
                            )
                            Text(
                                a.totalWage.afn(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        a.doneAt?.let {
                            Text(
                                PersianDate.short(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // ---------- کارنامهٔ من ----------
            ui.score?.let { sc ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("کارنامهٔ من", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${sc.deliveries.fa()} تحویل • ${sc.pieces.fa()} عدد • " +
                                    "کارمزدِ کسب‌شده ${sc.wages.afn()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                sc.qualityScore?.let { "کیفیت: ${it.fa()} از ۱۰۰" }
                                    ?: "کیفیت: هنوز ثبت نشده",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                sc.rejectPercent?.let { "برگشت از نظارت: ${it.fa()}٪" }
                                    ?: "برگشت از نظارت: سفارشِ قابلِ سنجش ندارد",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                TextButton(onClick = { pickerOpen = true }) { Text("من کسِ دیگری هستم") }
            }
        }
    }
}
