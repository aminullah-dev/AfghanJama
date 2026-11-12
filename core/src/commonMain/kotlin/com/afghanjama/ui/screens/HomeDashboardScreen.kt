@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.afghanjama.ui.screens

import com.afghanjama.ui.vm.Feature
import com.afghanjama.lan.workshopSharingAvailable
import com.afghanjama.ui.components.LicenceBanner
import com.afghanjama.licence.LicenceGate
import com.afghanjama.util.currentHour
import com.afghanjama.util.nowMillis
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.CountPill
import com.afghanjama.ui.theme.Radius
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
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EventRepeat
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
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
import com.afghanjama.ui.components.AnimatedAfn
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

/**
 * کلِ عرضِ شبکه، هر چند ستون که باشد.
 *
 * تا ۱.۹ اینجا `GridItemSpan(2)` سفت بود، چون خانه فقط روی گوشی دوستونه
 * بود. کامپیوتر همین صفحه را پهن‌تر و با ستونِ بیشتر نشان می‌دهد
 * ([HomeDashboardScreen]ِ `columns`)، و عددِ ثابت آنجا سرصفحه را نصفه می‌کرد.
 */
private fun androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.fullSpan() =
    androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan)

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
    onGoDebtFollowUp: () -> Unit,
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
    onGoWorkshopLoad: () -> Unit,
    onGoRecurring: () -> Unit,
    onGoGuide: () -> Unit,
    onGoWorkshopLink: () -> Unit,
    onGoBoard: () -> Unit,
    /**
     * آیا این نفر به این بخش راه دارد؟ — از تیک‌های مدیر.
     *
     * پیش‌فرض همان رفتارِ قبلی است (مدیر همه، بقیه هیچ) تا صداکننده‌ای
     * که هنوز این را نمی‌دهد چیزی را بی‌اجازه نشان ندهد.
     */
    can: (Feature) -> Boolean = { isManager },
    /** صفحهٔ لایسنس — از بنرِ آزمایشی/مهلت/فقط‌خواندنی. */
    onGoLicence: () -> Unit = onGoSettings,
    /**
     * چند ستون. گوشی دو؛ کامپیوتر بسته به پهنای پنجره بیشتر، تا کاشی‌ها
     * روی صفحهٔ پهن کشیده و توخالی نشوند.
     */
    columns: Int = 2,
    /**
     * سطرِ زیرِ کارتِ ساعتِ شیفت — یادآور روی هر سکو جورِ خودش خبر می‌دهد
     * (گوشی با لرزش، کامپیوتر در خودِ برنامه)، پس متنش از سکو می‌آید.
     */
    shiftReminderNote: String = "یادآور پایان شیفت (۸ ساعت) فعال است؛ ۳۰ دقیقه قبل با لرزش خبر می‌دهد.",
    /**
     * کارتی که سکو زیرِ سرصفحه می‌گذارد — کامپیوتر اینجا کاشیِ «اشتراکِ
     * کارگاه» را دارد (اصلی است؟ چند گوشی؟ چه منتظرِ تأیید است؟). گوشی
     * چیزی نمی‌دهد.
     */
    extraTop: (@Composable () -> Unit)? = null,
) {
    val s by vm.summary.collectAsState()
    val runningOut by vm.runningOut.collectAsState()
    val insideNow by vm.insideNow.collectAsState()
    val action by actionVm.ui.collectAsState()
    val licence by LicenceGate.status.collectAsState()

    // سنِ آخرین بکاپ برای بنرِ هشدار
    val backupSettings = LocalSettings.current
    LaunchedEffect(Unit) { actionVm.setLastBackup(BackupPrefs.lastAuto(backupSettings)) }

    // ساعتِ شیفت: هر ۳۰ ثانیه تیک می‌خورد تا مدتِ حضورِ باز دیده شود
    var nowTick by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(insideNow.isNotEmpty()) {
        while (insideNow.isNotEmpty()) {
            nowTick = nowMillis()
            delay(30_000)
        }
    }

    // انبار: موجودی، گردش و پیشنهادِ خرید — همه‌چیزِ «چه داریم»
    val warehouse = buildList {
        if (can(Feature.WAREHOUSE)) add(HomeAction("انبار مواد", Icons.Default.Warehouse, onGoWarehouse))
        if (can(Feature.SALES)) add(HomeAction("انبار محصول", Icons.Default.Sell, onGoFinishedSales))
        if (can(Feature.WAREHOUSE)) add(HomeAction("گردش انبار", Icons.Default.History, onGoStockLedger))
        if (can(Feature.PROCUREMENT)) add(HomeAction("پیشنهاد خرید", Icons.Default.AddShoppingCart, onGoPurchasePlan))
    }

    // تولید: از سفارش تا تحویل
    val production = buildList {
        if (can(Feature.ORDERS)) add(HomeAction("خط تولید", Icons.Default.Checkroom, onGoProduction))
        if (can(Feature.BOARD)) add(HomeAction("تابلوی دوخت", Icons.Default.Monitor, onGoBoard))
        if (canBuyMaterial) add(HomeAction("خرید مواد", Icons.Default.ShoppingCart, onGoProcurement))
    }

    // مشتریان و سفارش
    val customerFlow = buildList {
        if (canSeeCustomers) add(HomeAction("مشتریان", Icons.Default.Group, onGoCustomers))
        if (can(Feature.DELIVERY)) add(HomeAction("آمادهٔ تحویل", Icons.Default.LocalShipping, onGoDeliveryQueue))
        // کنارِ مشتریان، چون همان مانده‌ها را می‌بیند — ولی به شکلِ «امروز
        // به کی زنگ بزنم»، نه جدول.
        if (can(Feature.CUSTOMERS)) add(HomeAction("پیگیری طلب", Icons.Default.PhoneInTalk, onGoDebtFollowUp))
    }

    // عمومی
    val general = buildList {
        if (!isManager) add(HomeAction("کارِ من", Icons.Default.AssignmentInd, onGoMyWork))
        if (can(Feature.AUDIT)) add(HomeAction("مرکز هشدار", Icons.Default.NotificationsActive, onGoActionCenter))
        if (can(Feature.ATTENDANCE)) add(HomeAction("حضور و غیاب", Icons.Default.Fingerprint, onGoAttendance))
        if (can(Feature.PAYROLL)) add(HomeAction("حقوق کارکنان", Icons.Default.Badge, onGoPayroll))
        if (can(Feature.PAYROLL)) add(HomeAction("کارنامهٔ کارکنان", Icons.Default.WorkspacePremium, onGoPerformance))
        if (can(Feature.FINANCE)) add(HomeAction("مالی", Icons.Default.Payments, onGoFinance))
        if (can(Feature.LEDGER)) add(HomeAction("دفتر کل", Icons.Default.AccountBalance, onGoLedger))
        // کنارِ دفتر کل، چون هر دو دربارهٔ پولی‌اند که خودبه‌خود
        // حرکت می‌کند، نه پولی که سرِ یک معامله جابه‌جا می‌شود.
        if (can(Feature.FINANCE)) add(HomeAction("هزینه‌های ثابت", Icons.Default.EventRepeat, onGoRecurring))
        if (can(Feature.FINANCE)) add(HomeAction("اسناد", Icons.Default.Description, onGoDocuments))
        if (can(Feature.FINANCE)) add(HomeAction("گزارش‌ها", Icons.Default.Assessment, onGoReports))
        if (can(Feature.AUDIT)) add(HomeAction("رویدادها", Icons.Default.FactCheck, onGoAudit))
        add(HomeAction("جستجو", Icons.Default.Search, onGoSearch))
        add(HomeAction("تنظیمات", Icons.Default.Settings, onGoSettings))
        add(HomeAction("قیمت‌دهی", Icons.Default.Calculate, onGoQuoteCalc))
        // کنارِ قیمت‌دهی و نه جای دیگر: هر دو سرِ گرفتنِ سفارش لازم
        // می‌شوند — یکی «چند می‌گیرم» و دیگری «تا کِی می‌رسانم».
        if (can(Feature.BOARD)) add(HomeAction("بارِ کارگاه", Icons.Default.Speed, onGoWorkshopLoad))
        add(HomeAction("راهنما", Icons.AutoMirrored.Filled.HelpOutline, onGoGuide))
        // فقط جایی که اشتراکِ کارگاه واقعاً هست (اندروید/کامپیوتر). روی
        // آیفون این دکمه به بن‌بستِ «روی آیفون نیست» می‌رسید، پس نمایش
        // داده نمی‌شود.
        if (workshopSharingAvailable()) {
            add(HomeAction("کارگاهِ وصل", Icons.Default.Wifi, onGoWorkshopLink))
        }
    }

    val sections = listOf(
        "انبار" to warehouse,
        "تولید" to production,
        "مشتریان و سفارش" to customerFlow,
        "عمومی" to general
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
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
            val hour = remember { currentHour() }
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
                    PersianDate.long(nowMillis()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ---------- لایسنس ----------
        //
        // درست زیرِ سرصفحه: «چند روز مانده» یا «فقط‌خواندنی» چیزی است
        // که پیش از هر دکمه‌ای باید دیده شود. وقتی لایسنس فعال و دور از
        // تاریخ است، این بنر اصلاً جایی نمی‌گیرد.
        if (licence?.needsBanner == true) {
            item(span = { fullSpan() }) {
                LicenceBanner(onOpen = onGoLicence)
            }
        }

        if (extraTop != null) {
            item(span = { fullSpan() }) { extraTop() }
        }

        // ---------- پول: موجودی و سه کارِ هرروزه ----------
        //
        // **تا ۱.۱۳ این‌ها پنج تکهٔ جدا بودند:** دو دکمهٔ رنگیِ پرداخت و
        // دریافت، یک نوارِ «معاملات روزمره»، و پایین‌تر — زیرِ هشدارها و
        // انبار — دو کارتِ موجودی. یعنی «چقدر پول دارم» و «پول بده/بگیر»
        // که یک فکرند، با سه رنگِ مختلف در دو جای صفحه بودند.
        //
        // حالا یک کارت است: عدد بالا، سه کار زیرش. و چون اولِ صفحه است،
        // «موجودی نقد» بی اسکرول دیده می‌شود (آزمونِ `smoke.sh` هم همین را
        // می‌خواند).
        //
        // فقط برای کسی که تیکِ مالی دارد — خیاط و فروشنده صندوقِ کارگاه را
        // روی خانه نمی‌بینند.
        if (can(Feature.FINANCE)) {
            item(span = { fullSpan() }) {
                CashHero(
                    wallet = s.wallet,
                    bank = s.bank,
                    onReceive = onGoReceive,
                    onPay = onGoPay,
                    onDailyTrade = onGoDailyTrade
                )
            }
        }

        // ---------- بنر مرکز هشدار (وقتی موردی نیاز به رسیدگی دارد) ----------
        if (can(Feature.AUDIT) && !action.allClear) {
            item(span = { fullSpan() }) {
                val urgent = action.urgent > 0
                // کارتِ سفید با نشانِ رنگی، نه یک نوارِ تمام‌رنگ: رنگ فقط
                // روی آیکن و شمار می‌نشیند و همان‌قدر می‌گوید «بحرانی است»
                // بی آنکه نصفِ صفحه را سرخ کند.
                AppCard(onClick = onGoActionCenter) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBadge(
                            if (urgent) Icons.Default.NotificationsActive
                            else Icons.Default.Notifications,
                            tint = if (urgent) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            container = if (urgent) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "مرکز هشدار",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (urgent)
                                    "${action.urgent.fa()} موردِ بحرانی و ${action.total.fa()} مورد در مجموع نیاز به رسیدگی دارد"
                                else "${action.total.fa()} مورد نیاز به رسیدگی دارد",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CountPill(action.total, urgent = urgent)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "باز کردن مرکز هشدار",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                            shiftReminderNote,
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
            AppCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconBadge(Icons.Default.Warehouse)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "انبار مواد",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${s.materialItems.fa()} قلم • ارزش ${s.materialValue.afn()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (s.lowStockCount > 0) {
                    WarningLine("${s.lowStockCount.fa()} قلم موجودی کم دارد")
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

        item { StatCard("در تولید", s.inProduction.fa(), "سفارش در جریان", Icons.Default.Checkroom) }
        item { StatCard("انبار محصول", s.finishedPieces.fa(), "عدد آماده فروش", Icons.Default.Sell) }

        // ---------- ضربان خط تولید ----------
        if (can(Feature.ORDERS) && s.inProduction > 0) {
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

/**
 * کارتِ قهرمانِ خانه: موجودیِ نقد و بانک، و سه کارِ پولیِ هرروزه.
 *
 * عددِ صندوق درشت است و بانک کنارش کوچک‌تر: صندوق چیزی است که در طولِ
 * روز تکان می‌خورد و چشم دنبالش می‌گردد؛ بانک مرجع است.
 *
 * `AnimatedAfn` می‌ماند تا تغییرِ پول دیده شود — پول جهشی عوض می‌شود و
 * همان است که باید به چشم بیاید.
 */
@Composable
private fun CashHero(
    wallet: Long,
    bank: Long,
    onReceive: () -> Unit,
    onPay: () -> Unit,
    onDailyTrade: () -> Unit
) {
    BrandCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "موجودی نقد",
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.OnPetrolMuted
                )
                AnimatedAfn(
                    amount = wallet,
                    style = MaterialTheme.typography.headlineMedium
                        .copy(fontWeight = FontWeight.Bold),
                    color = Brand.OnPetrol
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "بانک",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.OnPetrolMuted
                )
                AnimatedAfn(
                    amount = bank,
                    style = MaterialTheme.typography.titleMedium
                        .copy(fontWeight = FontWeight.SemiBold),
                    color = Brand.OnPetrol
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroAction("دریافت", Icons.AutoMirrored.Filled.CallReceived, onReceive, Modifier.weight(1f))
            HeroAction("پرداخت", Icons.AutoMirrored.Filled.CallMade, onPay, Modifier.weight(1f))
            HeroAction("معاملات روزمره", Icons.Default.SwapHoriz, onDailyTrade, Modifier.weight(1f))
        }
    }
}

/**
 * یک کار روی کارتِ قهرمان: آیکن بالا، نام پایین، روی شیشهٔ کم‌رنگ.
 *
 * زمینه‌اش رنگِ متنِ همان کارت با کدریِ کم است، نه رنگِ تازه — پس با
 * هر رویه‌ای که کارت بگیرد هماهنگ می‌ماند. ارتفاعِ کمینه ۶۴dp است،
 * بالای حدِ ۴۸dpِ هدفِ لمسی.
 */
@Composable
private fun HeroAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(Brand.OnPetrol.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Icon(icon, contentDescription = null, tint = Brand.OnPetrol, modifier = Modifier.size(20.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Brand.OnPetrol,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/**
 * کارتِ شمارش (سفارش در جریان، عددِ آمادهٔ فروش).
 *
 * تا ۱.۱۳ نسخهٔ «پولی» هم داشت که مرجانی می‌شد؛ موجودی حالا در
 * [CashHero] بالای صفحه است و این کارت فقط می‌شمارد.
 */
@Composable
private fun StatCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector
) {
    AppCard(spacing = 4.dp) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            sub,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
