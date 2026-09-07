@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.afghanjama.data.ShiftPolicy
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.components.BrandCard
import com.afghanjama.ui.components.StageChip
import com.afghanjama.ui.components.WarningLine
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.components.IconBadge
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.data.StockForecast
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.elapsedHm
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.ActionCenterViewModel
import com.afghanjama.ui.vm.HomeViewModel
import kotlinx.coroutines.delay

private data class HomeAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private fun fullSpan() = androidx.compose.foundation.lazy.grid.GridItemSpan(2)

@Composable
fun HomeDashboardScreen(
    vm: HomeViewModel,
    actionVm: ActionCenterViewModel,
    isManager: Boolean,
    onGoActionCenter: () -> Unit,
    onGoProcurement: () -> Unit,
    onGoWarehouse: () -> Unit,
    onGoStockLedger: () -> Unit,
    onGoProduction: () -> Unit,
    onGoFinishedSales: () -> Unit,
    onGoDeliveryQueue: () -> Unit,
    onGoAttendance: () -> Unit,
    onGoPayroll: () -> Unit,
    onGoPerformance: () -> Unit,
    onGoPurchasePlan: () -> Unit,
    onGoMyWork: () -> Unit,
    onGoPay: () -> Unit,
    onGoReceive: () -> Unit,
    onGoDailyTrade: () -> Unit,
    canSeeCustomers: Boolean,
    canBuyMaterial: Boolean,
    onGoCustomers: () -> Unit,
    onGoFinance: () -> Unit,
    onGoLedger: () -> Unit,
    onGoDocuments: () -> Unit,
    onGoReports: () -> Unit,
    onGoAudit: () -> Unit,
    onGoSearch: () -> Unit,
    onGoSettings: () -> Unit,
    onGoQuoteCalc: () -> Unit,
    onGoGuide: () -> Unit,
    onGoWorkshopLink: () -> Unit,
    onGoBoard: () -> Unit
) {
    val s by vm.summary.collectAsState()
    val runningOut by vm.runningOut.collectAsState()
    val insideNow by vm.insideNow.collectAsState()
    val action by actionVm.ui.collectAsState()

    // سنِ آخرین بکاپ برای بنرِ هشدار
    val backupSettings = LocalSettings.current
    LaunchedEffect(Unit) { actionVm.setLastBackup(BackupPrefs.lastAuto(backupSettings)) }

    // ساعتِ شیفت: هر ۳۰ ثانیه تیک می‌خورد تا مدتِ حضورِ باز دیده شود
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(insideNow.isNotEmpty()) {
        while (insideNow.isNotEmpty()) {
            nowTick = System.currentTimeMillis()
            delay(30_000)
        }
    }

    // انبار: موجودی، گردش و پیشنهادِ خرید — همه‌چیزِ «چه داریم»
    val warehouse = buildList {
        add(HomeAction("انبار مواد", Icons.Default.Warehouse, onGoWarehouse))
        if (isManager) add(HomeAction("انبار محصول", Icons.Default.Sell, onGoFinishedSales))
        if (isManager) add(HomeAction("گردش انبار", Icons.Default.History, onGoStockLedger))
        if (isManager) add(HomeAction("پیشنهاد خرید", Icons.Default.AddShoppingCart, onGoPurchasePlan))
    }

    // تولید: از سفارش تا تحویل
    val production = buildList {
        if (isManager) add(HomeAction("خط تولید", Icons.Default.Checkroom, onGoProduction))
        add(HomeAction("تابلوی دوخت", Icons.Default.Monitor, onGoBoard))
        if (canBuyMaterial) add(HomeAction("خرید مواد", Icons.Default.ShoppingCart, onGoProcurement))
    }

    // مشتریان و سفارش
    val customerFlow = buildList {
        if (canSeeCustomers) add(HomeAction("مشتریان", Icons.Default.Group, onGoCustomers))
        if (isManager) add(HomeAction("آمادهٔ تحویل", Icons.Default.LocalShipping, onGoDeliveryQueue))
    }

    // عمومی
    val general = buildList {
        if (!isManager) add(HomeAction("کارِ من", Icons.Default.AssignmentInd, onGoMyWork))
        if (isManager) add(HomeAction("مرکز هشدار", Icons.Default.NotificationsActive, onGoActionCenter))
        if (isManager) add(HomeAction("حضور و غیاب", Icons.Default.Fingerprint, onGoAttendance))
        if (isManager) add(HomeAction("حقوق کارکنان", Icons.Default.Badge, onGoPayroll))
        if (isManager) add(HomeAction("کارنامهٔ کارکنان", Icons.Default.WorkspacePremium, onGoPerformance))
        if (isManager) add(HomeAction("مالی", Icons.Default.Payments, onGoFinance))
        if (isManager) add(HomeAction("دفتر کل", Icons.Default.AccountBalance, onGoLedger))
        if (isManager) add(HomeAction("اسناد", Icons.Default.Description, onGoDocuments))
        if (isManager) add(HomeAction("گزارش‌ها", Icons.Default.Assessment, onGoReports))
        if (isManager) add(HomeAction("رویدادها", Icons.Default.FactCheck, onGoAudit))
        add(HomeAction("جستجو", Icons.Default.Search, onGoSearch))
        add(HomeAction("تنظیمات", Icons.Default.Settings, onGoSettings))
        add(HomeAction("قیمت‌دهی", Icons.Default.Calculate, onGoQuoteCalc))
        add(HomeAction("راهنما", Icons.AutoMirrored.Filled.HelpOutline, onGoGuide))
        add(HomeAction("اشتراک کارگاه", Icons.Default.Wifi, onGoWorkshopLink))
    }

    val sections = listOf(
        "انبار" to warehouse,
        "تولید" to production,
        "مشتریان و سفارش" to customerFlow,
        "عمومی" to general
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- سرصفحه ----------
        //
        // اولین چیزی که چشم می‌بیند باید بگوید «کجا هستم و امروز چه
        // روزی است»، نه یک دکمهٔ پول. تا دیروز سلام بعد از دکمه‌های
        // پرداخت و بنرهای هشدار می‌آمد، یعنی وسطِ صفحه — جایی که هیچ
        // سرصفحه‌ای نباید باشد.
        item(span = { fullSpan() }) {
            val settings = LocalSettings.current
            val coName = remember { CompanyPrefs.shopName(settings) }
            val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
            val greeting = when {
                hour < 12 -> "صبح بخیر"
                hour < 17 -> "روز بخیر"
                else -> "عصر بخیر"
            }
            Column(
                Modifier.padding(top = 4.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    coName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    PersianDate.long(System.currentTimeMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ---------- پرداخت و دریافتِ سریع ----------
        if (isManager) {
            item(span = { fullSpan() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MoneyButton(
                        label = "پرداخت",
                        sub = "به کارمند، خیاط، فروشنده…",
                        icon = Icons.AutoMirrored.Filled.CallMade,
                        container = MaterialTheme.colorScheme.errorContainer,
                        onContainer = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onGoPay,
                        modifier = Modifier.weight(1f)
                    )
                    MoneyButton(
                        label = "دریافت",
                        sub = "از مشتری یا هر کسِ دیگر",
                        icon = Icons.AutoMirrored.Filled.CallReceived,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        onContainer = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onGoReceive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(span = { fullSpan() }) {
                MoneyButton(
                    label = "معاملات روزمره",
                    sub = "خرید • فروش • برگشتی‌ها",
                    icon = Icons.Default.SwapHoriz,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onGoDailyTrade
                )
            }
        }

        // ---------- بنر مرکز هشدار (وقتی موردی نیاز به رسیدگی دارد) ----------
        if (isManager && !action.allClear) {
            item(span = { fullSpan() }) {
                val urgent = action.urgent > 0
                Card(
                    onClick = onGoActionCenter,
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (urgent) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val onColor = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${if (urgent) "🚨" else "🔔"} مرکز هشدار",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onColor
                            )
                            Text(
                                if (urgent)
                                    "${action.urgent.fa()} موردِ بحرانی و ${action.total.fa()} مورد در مجموع نیاز به رسیدگی دارد"
                                else "${action.total.fa()} مورد نیاز به رسیدگی دارد",
                                style = MaterialTheme.typography.bodySmall,
                                color = onColor
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "باز کردن مرکز هشدار",
                            tint = onColor
                        )
                    }
                }
            }
        }

        // ---------- ساعتِ شیفت (وقتی ورودی باز است) ----------
        if (insideNow.isNotEmpty()) {
            item(span = { fullSpan() }) {
                val oldest = insideNow.minByOrNull { it.checkIn }!!
                val nearEnd = insideNow.any {
                    (nowTick - it.checkIn) >= ShiftPolicy.WARN_AFTER_MS
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (nearEnd) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        WarningLine(
                            "${insideNow.size.fa()} کارمند در کارگاه — قدیمی‌ترین ورود: " +
                                "${elapsedHm(oldest.checkIn, nowTick)} ساعت پیش",
                            icon = Icons.Default.Schedule,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (nearEnd) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (nearEnd) WarningLine(
                            "شیفت ۸ ساعته رو به پایان است — خروج‌ها را ثبت کنید",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        ) else Text(
                            "یادآور پایان شیفت (۸ ساعت) فعال است؛ ۳۰ دقیقه قبل با لرزش خبر می‌دهد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (nearEnd) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // ---------- کارت‌های خلاصه ----------
        item(span = { fullSpan() }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "انبار مواد",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "${s.materialItems.fa()} قلم • ارزش ${s.materialValue.afn()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (s.lowStockCount > 0) {
                        WarningLine("${s.lowStockCount.fa()} قلم موجودی کم دارد")
                    }
                }
            }
        }

        // ---------- موادی که دارند تمام می‌شوند ----------
        // «حدِ هشدار» یک عددِ ثابت است و نمی‌داند پارچه‌ای که هفته‌ای ده متر
        // می‌رود با پارچه‌ای که ماهی یک بار مصرف می‌شود فرق دارد. اینجا از
        // سرعتِ مصرفِ واقعی گفته می‌شود چند روز دیگر وقت هست.
        if (runningOut.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "مواد رو به اتمام",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        runningOut.forEach { f ->
                            Text(
                                buildString {
                                    append("• ${f.name}: ")
                                    append("${f.amount.toLong().fa()} ${f.unit} مانده")
                                    // در یک متغیرِ محلی گرفته می‌شود چون
                                    // کاتلین خاصیتِ ماژولِ دیگر را smart-cast
                                    // نمی‌کند — از دیدِ کامپایلر، آن ماژول
                                    // می‌تواند جدا عوض شود.
                                    val left = f.daysLeft
                                    if (left != null) {
                                        append(" — حدود ${left.fa()} روز دیگر تمام می‌شود")
                                    } else {
                                        append(" — زیرِ حدِ هشدار")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "بر اساسِ مصرفِ ${StockForecast.WINDOW_DAYS.fa()} روزِ گذشته",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        item { StatCard("در تولید", s.inProduction.fa(), "سفارش در جریان") }
        item { StatCard("انبار محصول", s.finishedPieces.fa(), "عدد آماده فروش") }
        item { StatCard("کیف پول", s.wallet.afn(), "موجودی نقد", money = true) }
        item { StatCard("بانک", s.bank.afn(), "موجودی بانک", money = true) }

        // ---------- ضربان خط تولید ----------
        if (isManager && s.inProduction > 0) {
            item(span = { fullSpan() }) {
                Card(
                    onClick = onGoProduction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ضربان خط تولید",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StageChip(Icons.Default.ContentCut, "برش", s.cutting.fa())
                            StageChip(Icons.Default.Checkroom, "دوخت", s.sewing.fa())
                            StageChip(Icons.Default.FactCheck, "نظارت", s.review.fa())
                        }
                        if (s.stuck > 0) {
                            WarningLine(
                                "${s.stuck.fa()} سفارش بیش از حد معطل مانده — بررسی کنید"
                            )
                        }
                    }
                }
            }
        }

        // ---------- بخش‌های دسترسی سریع ----------
        sections.forEach { (title, list) ->
            if (list.isNotEmpty()) {
                item(span = { fullSpan() }) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    )
                }
                items(list, key = { it.label }) { action -> ActionCard(action) }
            }
        }

        item(span = { fullSpan() }) {
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** دکمهٔ بزرگِ پرداخت/دریافت — عمداً ساده: یک عنوان، یک زیرنویس، یک آیکن. */
@Composable
private fun MoneyButton(
    label: String,
    sub: String,
    icon: ImageVector,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = onContainer)
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer
                )
            }
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                // زیرنویس نباید هم‌وزنِ عنوان باشد وگرنه هر دو با هم
                // خوانده می‌شوند و هیچ‌کدام دیده نمی‌شود
                color = onContainer.copy(alpha = 0.75f)
            )
        }
    }
}

/**
 * خانهٔ یک بخش در شبکهٔ دسترسیِ سریع.
 *
 * آیکن داخلِ یک دایرهٔ کم‌رنگ می‌نشیند نه لخت روی سفید: با یک نگاه از
 * دور هم بخش‌ها از هم جدا می‌شوند، و شبکهٔ چهارده‌خانه‌ای به‌جای یک
 * توده، ریتم پیدا می‌کند.
 */
@Composable
private fun ActionCard(action: HomeAction) {
    Card(
        onClick = action.onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(action.icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, sub: String, money: Boolean = false) {
    /*
     * کارتِ پول رویهٔ مسی می‌گیرد، کارتِ شمارش نه.
     *
     * چهار کارت کنارِ هم‌اند و هر چهار تا یک شکل بودند، پس چشم برای
     * پیدا کردنِ «چقدر پول داریم» باید هر بار عنوان‌ها را می‌خواند.
     * دو تای پولی که مسی شوند، همان نگاهِ اول جواب می‌دهد.
     *
     * اگر هر چهار تا مسی می‌شدند دوباره هیچ‌کدام برجسته نبود — تأکید
     * فقط وقتی کار می‌کند که اقلیت باشد.
     */
    if (money) {
        BrandCard(contentPadding = 14.dp) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = Brand.OnCopperMuted)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Brand.OnCopper
            )
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Brand.OnCopperMuted)
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(sub, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
