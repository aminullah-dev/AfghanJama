@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.afghanjama.desktop

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.CashForecast
import com.afghanjama.data.DigestSource
import com.afghanjama.data.Figure
import com.afghanjama.data.FinancialHealth
import com.afghanjama.data.HubDigest
import com.afghanjama.data.OrderPipeline
import com.afghanjama.data.PriceAdvisor
import com.afghanjama.data.entities.clockSuspect
import com.afghanjama.data.repo.Repo
import com.afghanjama.lan.LanHub
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.components.BarRow
import com.afghanjama.ui.components.CardPadding
import com.afghanjama.ui.components.ColumnBars
import com.afghanjama.ui.components.ColumnDatum
import com.afghanjama.ui.components.DonutChart
import com.afghanjama.ui.components.DonutLegend
import com.afghanjama.ui.components.DonutSlice
import com.afghanjama.ui.components.Pill
import com.afghanjama.ui.components.PillTone
import com.afghanjama.ui.components.Space
import com.afghanjama.ui.components.Sparkline
import com.afghanjama.ui.components.glass
import com.afghanjama.ui.components.levelLabel
import com.afghanjama.ui.components.signalText
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.afn
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.theme.Brand
import com.afghanjama.ui.theme.Radius
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.HubDashboardViewModel
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

/*
 * داشبوردِ کامپیوتر — «مادرِ» کارگاه (طرح، فازِ C).
 *
 * چهار ستون، راست‌به‌چپ، به ترتیبی که چشم می‌خوانَد: **امروز** (هشدار،
 * درخواستِ گوشی‌ها، سفارش‌های امروز، آمادهٔ تحویل، چه کسی داخل است)، **تولید**
 * (خطِ تولید، در خطرِ دیرکرد، بارِ خیاط‌ها)، **پول** (صندوق، وضعِ مالی، فروش،
 * پیگیریِ طلب، کارمزدِ باز) و **انبار و پیشنهاد** (مواد، کالا، زیرِ قیمتِ
 * تمام‌شده). صفحهٔ باریک‌تر ستون‌ها را روی هم می‌گذارد.
 *
 * **سه قاعده که هر کارت نگه می‌دارد:**
 *  - هر عدد منبعش را می‌گوید («از: …») و تازگی‌اش را؛ «از کجا آمده؟» حسابش را.
 *  - نامعلوم هرگز صفر نیست: `Figure.Unknown` با دلیلِ دری.
 *  - هر کارت فقط برای کسی است که به صفحهٔ همان کارت راه دارد
 *    ([allowedFor]) — همان قاعدهٔ نوار و خانهٔ گوشی (بازبینی، بندِ ۱۹-و).
 */

/**
 * یک کارت و جایش. [priority] جایِ کارت را می‌سازد — کوچک‌تر یعنی مهم‌تر و
 * بالاتر. کارت‌ها به ترتیبِ همین عدد چیده و نوبتی در ستون‌ها پخش می‌شوند، پس
 * مهم‌ترین‌ها همیشه بالای صفحه‌اند (خواستِ صاحبِ کار: «الویت‌ها بالای صفحه، به
 * هر صورت»). [section] صفحه‌ای است که کارت به آن می‌برد و دسترسی را از آن می‌گیرد.
 */
private class DashCard(
    val priority: Int,
    val section: Section,
    val extraGate: Boolean = true,
    val body: @Composable () -> Unit,
)

/**
 * همهٔ کارت‌ها یک قد — شبکهٔ یک‌دست و آرام. کارتی که داده‌اش بیشتر است
 * داخلش بریده می‌شود و با دکمهٔ «بزرگ کردن» کامل دیده می‌شود.
 */
private val DashCardHeight = 300.dp

/** یک کارتِ بزرگ‌شده: عنوان، منبع، و همان بدنهٔ کارت که حالا به‌اندازهٔ داده باز می‌شود. */
private class DashExpand(
    val title: String,
    val source: DigestSource,
    val fresh: String,
    val body: @Composable ColumnScope.() -> Unit,
)

/** هر `DashFrame` از این می‌خواند تا بگوید «مرا بزرگ کن»؛ داشبورد روی خودش پوششِ بزرگ را می‌کشد. */
private val LocalDashExpand = compositionLocalOf<(DashExpand) -> Unit> { {} }

/** کارت برای این نفر هست؟ — همان دسترسیِ صفحه‌ای که کارت به آن می‌برد (بازبینی، ۱۹-و). */
internal fun dashVisible(section: Section, extraGate: Boolean, access: Access): Boolean =
    extraGate && section.allowedFor(access)

/** ستون‌ها بر پایهٔ پهنای خودِ ناحیهٔ کار، نه پنجره. */
internal fun dashColumns(widthDp: Int): Int = when {
    widthDp >= 1500 -> 4
    widthDp >= 1100 -> 3
    widthDp >= 760 -> 2
    else -> 1
}

/**
 * کارتِ شمارهٔ [order] (به ترتیبِ اولویت، از ۰) در کدام ستون می‌نشیند — پخشِ
 * نوبتی. چون همهٔ کارت‌ها یک قد دارند، نوبتی‌چیدن یعنی ستون‌ها در شمار با هم
 * حداکثر یکی فرق می‌کنند (فاصلهٔ خالیِ ته ستون‌ها کم می‌شود)، و چون به ترتیبِ
 * اولویت است، ردیفِ اولِ هر ستون همیشه مهم‌ترین کارت‌هاست. ستونِ ۰ در RTL
 * راست‌ترین است، پس مهم‌ترین کارت بالا-راست می‌نشیند، همان‌جا که چشم شروع می‌کند.
 */
internal fun dashColumn(order: Int, columns: Int): Int =
    if (columns <= 1) 0 else order % columns

@Composable
internal fun HubDashboard(
    repo: Repo,
    access: Access,
    go: (Section) -> Unit,
    onOpenOrder: (String) -> Unit,
) {
    val settings = LocalSettings.current
    val vm: HubDashboardViewModel = viewModel {
        HubDashboardViewModel(
            repo, settings, access = { access },
            hubServing = LanHub.shared.state.map { st ->
                if (LanPrefs.mode(settings) == DeviceMode.MAIN) st.serving else null
            },
        )
    }
    val d by vm.digest.collectAsState()
    val msg by vm.message.collectAsState()

    // ساعتِ «تازه: …» — هر نیم دقیقه، بی آنکه چکیده دوباره حساب شود.
    var clock by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            clock = nowMillis()
        }
    }
    val fresh = freshness(d.at, clock)

    // ترتیبِ اولویت (خواستِ صاحبِ کار): اول هشدارِ فوری، بعد پول و پیش‌بینی و
    // وضعِ مالی، بعد کارِ امروز (سفارش‌ها، آمادهٔ تحویل، درخواستِ گوشی‌ها)، بعد
    // تولید و طلب و حقوق، و در آخر انبار و حضور. عددِ اولویت جای ستون و منطق را
    // نمی‌سازد؛ فقط بالا-به-پایین‌بودن را. پخشِ نوبتی ([dashColumn]) ستون‌ها را
    // هم‌قد نگه می‌دارد.
    val cards = buildList {
        add(DashCard(0, Section.ActionCenter) { AlertsCard(d, fresh, go) })
        add(DashCard(1, Section.Finance) { ForecastCard(d, fresh, go) })
        add(DashCard(2, Section.Finance) { HealthCard(d, fresh, go) })
        add(DashCard(3, Section.Finance) { MoneyCard(d, fresh, go) })
        add(DashCard(4, Section.Inventory) { TodayCard(d, fresh, go, onOpenOrder) })
        add(DashCard(5, Section.DeliveryQueue) { ReadyCard(d, fresh, go) })
        add(DashCard(6, Section.WorkshopLink, WorkshopLinkAccess.seesRequests(access)) { RequestsCard(d, fresh, vm, go) })
        add(DashCard(7, Section.WorkshopLoad) { RiskCard(d, fresh, go, onOpenOrder) })
        add(DashCard(8, Section.FollowUp) { DebtsCard(d, fresh, go) })
        add(DashCard(9, Section.Inventory) { PipelineCard(d, fresh, go) })
        add(DashCard(10, Section.WorkshopLoad) { TailorsCard(d, fresh, go) })
        add(DashCard(11, Section.Finance) { SalesCard(d, fresh, go) })
        add(DashCard(12, Section.Payroll) { WagesCard(d, fresh, go) })
        add(DashCard(13, Section.PurchasePlan) { MaterialsCard(d, fresh, go) })
        add(DashCard(14, Section.FinishedWarehouse) { ProductsCard(d, fresh, go) })
        add(DashCard(15, Section.FinishedWarehouse) { BelowCostCard(d, fresh, go) })
        add(DashCard(16, Section.Attendance) { InsideCard(d, fresh, go, clock) })
    }.filter { dashVisible(it.section, it.extraGate, access) }
        .sortedBy { it.priority }

    var expanded by remember { mutableStateOf<DashExpand?>(null) }
    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalDashExpand provides { expanded = it }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Header(d, fresh, access, go)
        msg?.let { (text, error) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                    .background(if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text, Modifier.weight(1f), color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = vm::clearMessage) { Text("باشه") }
            }
        }
        if (cards.isEmpty()) {
            Column(Modifier.fillMaxWidth().glass().padding(CardPadding), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("داشبورد برای کارهای شما کارتی ندارد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("کارهای خودتان در «خانه» است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { go(Section.Home) }) { Text("رفتن به خانه") }
            }
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // ستون‌ها به اندازهٔ کارت‌های همین نفر؛ کسی که کم کارت دارد ستونِ
            // خالی نمی‌بیند. کارت‌ها به ترتیبِ اولویت نوبتی در ستون‌ها می‌افتند،
            // پس ردیفِ بالا همیشه مهم‌ترین‌هاست و ته ستون‌ها هم‌قد می‌ماند.
            val columns = minOf(dashColumns(maxWidth.value.toInt()), cards.size).coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalAlignment = Alignment.Top) {
                for (col in 0 until columns) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                        cards.filterIndexed { i, _ -> dashColumn(i, columns) == col }.forEach { it.body() }
                    }
                }
            }
        }
    }
      }
      expanded?.let { ex -> DashExpandOverlay(ex, onClose = { expanded = null }) }
    }
}

// ═════════════════════════════════════════════════════════════════ سربرگ

@Composable
private fun Header(d: HubDigest.Digest, fresh: String, access: Access, go: (Section) -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(Radius.xxl)).padding(horizontal = Space.xl, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(Modifier.weight(1f)) {
                Text("داشبورد", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(PersianDate.long(d.at), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill("تازه: $fresh", tone = PillTone.Neutral)
            val dev = d.devices.known
            when (dev?.serving) {
                true -> Pill("کارگاه روشن — ${dev.online.fa()} دستگاه وصل", tone = PillTone.Strong)
                false -> Pill("کارگاه خاموش است", tone = PillTone.Coral)
                null -> Unit
            }
        }
        // دستگاه‌ها: فقط برای کسی که «کارگاهِ وصل» را می‌بیند و فقط اگر چیزی هست.
        val dev = d.devices.known
        if (dev != null && dev.serving != null && Section.WorkshopLink.allowedFor(access)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("دستگاه‌ها:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                if (dev.list.isEmpty()) {
                    TextButton(onClick = { go(Section.WorkshopLink) }) { Text("هیچ دستگاهی وصل نیست — افزودنِ گوشی") }
                }
                dev.list.take(8).forEach { x ->
                    val who = listOf(x.name, x.person).filter { it.isNotBlank() }.joinToString(" — ")
                    val seen = x.lastSeen?.let { if (x.online) "آنلاین" else freshness(it, d.at) } ?: "هرگز"
                    Pill(
                        "$who • $seen" + if (x.outdated) " • نسخهٔ کهنه" else "",
                        tone = when {
                            x.outdated -> PillTone.Coral
                            x.online -> PillTone.Brand
                            else -> PillTone.Neutral
                        },
                        modifier = Modifier.clickable { go(Section.WorkshopLink) },
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════ قابِ کارت

/**
 * قابِ هر کارت: عنوان، «همه»، بدنه، و زیرش «از: منبع • تازه: …» با «از کجا
 * آمده؟» که حسابِ عدد را به دری می‌گوید.
 */
@Composable
private fun DashFrame(
    title: String,
    source: DigestSource,
    fresh: String,
    onAll: (() -> Unit)?,
    allLabel: String = "همه",
    content: @Composable ColumnScope.() -> Unit,
) {
    val expand = LocalDashExpand.current
    Column(
        Modifier.fillMaxWidth().widthIn(min = 240.dp).height(DashCardHeight).glass().padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { expand(DashExpand(title, source, fresh, content)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "بزرگ کردن", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            if (onAll != null) TextButton(onClick = onAll) { Text(allLabel) }
        }
        // ناحیهٔ بدنه قدِ باقی‌مانده را می‌گیرد؛ بیشترش بریده می‌شود (با «بزرگ کردن» کامل).
        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            content()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(
                "از: ${source.label} • تازه: $fresh",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            SourceTip(source)
        }
    }
}

/**
 * کارتِ بزرگ‌شده روی کلِ داشبورد — زمینهٔ تیره، و کارتی که به‌اندازهٔ داده‌اش
 * باز می‌شود (تا ۸۵٪ قد، بیشترش اسکرول). کلیکِ بیرون یا ✕ می‌بندد.
 */
@Composable
private fun DashExpandOverlay(ex: DashExpand, onClose: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.padding(Space.xl)) {
            val maxH = maxHeight * 0.85f
            Column(
                Modifier.fillMaxWidth(0.62f).widthIn(min = 360.dp, max = 760.dp).heightIn(max = maxH)
                    .glass(RoundedCornerShape(Radius.xxl))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    ex.body(this)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(
                        "از: ${ex.source.label} • تازه: ${ex.fresh}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    SourceTip(ex.source)
                }
            }
        }
    }
}

/** «از کجا آمده؟» — روی آیکن، حسابِ عدد به دری. */
@Composable
private fun SourceTip(source: DigestSource) {
    TooltipArea(
        tooltip = {
            Text(
                "از کجا آمده؟ ${source.formula}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(Space.sm),
            )
        },
        delayMillis = 300,
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "از کجا آمده؟ ${source.formula}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * عددِ نامعلوم: قرصِ «نامعلوم» و دلیلش، نه صفر. تا وقتی منبع هنوز نرسیده،
 * فقط «در حالِ خواندن».
 */
@Composable
private fun Unknown(reason: String) {
    if (reason == HubDigest.READING) {
        Calm(reason)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Pill("نامعلوم", tone = PillTone.Neutral)
        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> Show(f: Figure<T>, known: @Composable (T) -> Unit) {
    when (f) {
        is Figure.Known -> known(f.value)
        is Figure.Unknown -> Unknown(f.reason)
    }
}

@Composable
private fun Calm(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Big(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

@Composable
private fun Line(title: String, sub: String = "", onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}

// ═════════════════════════════════════════════════════════════════ امروز

@Composable
private fun AlertsCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    val total = d.alerts.known?.total
    DashFrame(
        "هشدارهای فوری", DigestSource.ALERTS, fresh,
        onAll = { go(Section.ActionCenter) }, allLabel = if (total != null && total > 0) "همه (${total.fa()})" else "همه",
    ) {
        Show(d.alerts) { a ->
            if (a.total == 0) Calm("همه‌چیز آرام است.")
            else if (a.top.isEmpty()) Calm("هشدارِ فوری نیست؛ ${a.total.fa()} یادآوری در «مرکز هشدار».")
            a.top.forEach { r ->
                Line(
                    r.title, r.detail,
                    trailing = { Pill(if (r.urgent) "فوری" else "هشدار", tone = if (r.urgent) PillTone.Bad else PillTone.Coral) },
                    onClick = { r.route?.let { sectionForRoute(it) }?.let(go) },
                )
            }
        }
    }
}

@Composable
private fun RequestsCard(d: HubDigest.Digest, fresh: String, vm: HubDashboardViewModel, go: (Section) -> Unit) {
    DashFrame("درخواست‌های گوشی‌ها", DigestSource.REQUESTS, fresh, onAll = { go(Section.WorkshopLink) }) {
        Show(d.requests) { list ->
            if (list.isEmpty()) Calm("چیزی منتظرِ تأیید نیست.")
            list.take(HubDigest.TOP).forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Line("${r.worker} — ${r.summary}", PersianDate.shortWithTime(r.createdAt) + if (r.note.isNotBlank()) " • ${r.note}" else "")
                    when {
                        r.clockSuspect() -> Text(
                            "ساعتِ گوشی معتبر نیست — در «کارگاهِ وصل» زمانش را انتخاب کنید.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                        )
                        vm.canDecide(r) -> Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            OutlinedButton(onClick = { vm.approve(r) }) { Text("تأیید و ثبت") }
                            TextButton(onClick = { vm.reject(r) }) { Text("رد") }
                        }
                        else -> Unit
                    }
                }
            }
            if (list.size > HubDigest.TOP) Calm("و ${(list.size - HubDigest.TOP).fa()} درخواستِ دیگر.")
        }
    }
}

@Composable
private fun TodayCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit, onOpenOrder: (String) -> Unit) {
    DashFrame("سفارش‌های امروز", DigestSource.ORDERS, fresh, onAll = { go(Section.Inventory) }) {
        Show(d.today) { t ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Pill("سررسیدِ امروز: ${t.dueToday.size.fa()}", tone = PillTone.Brand)
                Pill("عقب‌افتاده: ${t.late.size.fa()}", tone = if (t.late.isEmpty()) PillTone.Neutral else PillTone.Coral)
                Pill("ثبتِ امروز: ${t.createdToday.fa()}", tone = PillTone.Neutral)
            }
            (t.late + t.dueToday).take(HubDigest.TOP).forEach { o ->
                Line(
                    "${o.code} — ${o.title}",
                    listOf(o.customer, "مهلت: ${PersianDate.short(o.due)}").filter { it.isNotBlank() }.joinToString(" • "),
                    onClick = { onOpenOrder(o.id) },
                )
            }
        }
    }
}

@Composable
private fun ReadyCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("آمادهٔ تحویل", DigestSource.DELIVERY, fresh, onAll = { go(Section.DeliveryQueue) }) {
        Show(d.ready) { r ->
            Big("${r.count.fa()} سفارش")
            r.oldestDays?.let { Calm("قدیمی‌ترین ${it.fa()} روز است منتظرِ مشتری مانده.") }
        }
    }
}

@Composable
private fun InsideCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit, clock: Long) {
    DashFrame("چه کسی داخل است", DigestSource.ATTENDANCE, fresh, onAll = { go(Section.Attendance) }) {
        Show(d.inside) { list ->
            if (list.isEmpty()) Calm("کسی داخل نیست.")
            list.take(8).forEach { p ->
                val hours = ((clock - p.since) / 3_600_000L).toInt().coerceAtLeast(0)
                Line(
                    p.name, "از ساعتِ ${PersianDate.time(p.since)}",
                    trailing = { Pill("${hours.fa()} ساعت", tone = if (hours >= HubDigest.LONG_SHIFT_HOURS) PillTone.Coral else PillTone.Neutral) },
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════ تولید

@Composable
private fun PipelineCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("خطِ تولید", DigestSource.PIPELINE, fresh, onAll = { go(Section.Inventory) }) {
        Show(d.pipeline) { p ->
            ColumnBars(
                bars = OrderPipeline.STAGES.mapIndexed { i, st -> ColumnDatum(st.label, p.counts[i].toLong(), p.counts[i].fa()) },
                color = MaterialTheme.colorScheme.primary,
            )
            Pill("معطل: ${p.stuck.fa()}", tone = if (p.stuck > 0) PillTone.Coral else PillTone.Neutral)
        }
    }
}

@Composable
private fun RiskCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit, onOpenOrder: (String) -> Unit) {
    DashFrame("در خطرِ دیرکرد", DigestSource.FORECAST, fresh, onAll = { go(Section.WorkshopLoad) }) {
        Show(d.atRisk) { list ->
            if (list.isEmpty()) Calm("با سرعتِ فعلی هیچ سفارشی از مهلتش دیر نمی‌شود.")
            list.forEach { r ->
                Line(
                    r.code + if (r.customer.isNotBlank()) " — ${r.customer}" else "",
                    "آماده حدودِ ${PersianDate.short(r.readyAt)}",
                    trailing = { Pill("${r.lateDays.fa()} روز دیر", tone = PillTone.Coral) },
                )
            }
        }
    }
}

@Composable
private fun TailorsCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("بارِ خیاط‌ها", DigestSource.FORECAST, fresh, onAll = { go(Section.WorkshopLoad) }) {
        Show(d.tailors) { list ->
            if (list.isEmpty()) Calm("کاری زیرِ دستِ خیاط‌ها نیست.")
            val max = (list.maxOfOrNull { it.inHand } ?: 0).coerceAtLeast(1)
            list.forEach { t ->
                BarRow(
                    label = t.name,
                    value = "${t.inHand.fa()} دست",
                    fraction = t.inHand.toFloat() / max,
                    color = MaterialTheme.colorScheme.primary,
                    sub = when (val f = t.days) {
                        is Figure.Known -> "${kotlin.math.ceil(f.value).toInt().fa()} روز کار"
                        is Figure.Unknown -> "روزِ کار نامعلوم"
                    },
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════ پول

@Composable
private fun MoneyCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("پول", DigestSource.MONEY, fresh, onAll = { go(Section.Finance) }) {
        Show(d.money) { m ->
            val slices = listOf(
                DonutSlice("صندوق", m.wallet, Brand.Coral),
                DonutSlice("بانک", m.bank, MaterialTheme.colorScheme.primary),
                DonutSlice("صندوقِ فایده", m.profit, MaterialTheme.colorScheme.tertiary),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                DonutChart(slices, diameter = 104.dp, ring = 15.dp)
                DonutLegend(slices, valueText = { it.afn() }, modifier = Modifier.weight(1f))
            }
            Calm("کل: ${(m.wallet + m.bank + m.profit).afn()}")
        }
    }
}

@Composable
private fun HealthCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("وضعِ مالی", DigestSource.HEALTH, fresh, onAll = { go(Section.Finance) }) {
        Show(d.health) { h ->
            Pill(
                levelLabel(h.level),
                tone = when (h.level) {
                    FinancialHealth.Level.GOOD -> PillTone.Brand
                    FinancialHealth.Level.WATCH -> PillTone.Coral
                    FinancialHealth.Level.RISK -> PillTone.Bad
                },
            )
            Show(h.cover) { days ->
                Calm(if (h.coverCapped) "پولِ نقد برای بیش از ${FinancialHealth.MAX_COVER_DAYS.fa()} روز هزینه بس است." else "پولِ نقد برای ${days.fa()} روز هزینه بس است.")
            }
            h.signals.take(2).forEach { Text("• ${signalText(it)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ForecastCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("پیش‌بینیِ پول", DigestSource.CASH_FORECAST, fresh, onAll = { go(Section.Finance) }) {
        Show(d.forecast) { f ->
            val color = when (f.level) {
                CashForecast.Level.RISK -> MaterialTheme.colorScheme.error
                CashForecast.Level.WATCH -> MaterialTheme.colorScheme.secondary
                CashForecast.Level.GOOD -> MaterialTheme.colorScheme.primary
            }
            val tone = when (f.level) {
                CashForecast.Level.RISK -> PillTone.Bad
                CashForecast.Level.WATCH -> PillTone.Coral
                CashForecast.Level.GOOD -> PillTone.Brand
            }
            Pill(forecastHeadline(f), tone = tone)
            Sparkline(values = f.curve, color = color, markIndex = f.shortDay ?: f.lowestDay)
            Line("امروز", trailing = { Text(f.start.afn(), fontWeight = FontWeight.SemiBold) })
            Line("تهِ ماه (پیش‌بینی)", trailing = { Text(f.end.afn(), fontWeight = FontWeight.SemiBold) })
        }
    }
}

/** سرتیترِ کارتِ پیش‌بینی — از بدترین نشانه. */
private fun forecastHeadline(f: CashForecast.Snapshot): String = when (f.signals.firstOrNull()?.kind) {
    CashForecast.Kind.SHORT_EVEN_WITH_COLLECTING ->
        f.shortDay?.let { "حدودِ ${it.fa()} روزِ دیگر پول کم می‌آید" } ?: "پول کم می‌آید"
    CashForecast.Kind.SHORT_UNLESS_COLLECTING -> "اگر طلب را به‌موقع وصول نکنی، پول کم می‌آید"
    CashForecast.Kind.DECLINING -> "پولِ نقد رو به کاهش است"
    else -> "پول تا تهِ ماه می‌رسد"
}

@Composable
private fun SalesCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("فروشِ ۳۰ روز", DigestSource.SALES, fresh, onAll = { go(Section.Finance) }) {
        Show(d.sales) { s ->
            Big(s.last30.afn())
            Show(s.changePct) { pct ->
                val up = pct >= 0
                Pill(
                    (if (up) "بیشتر" else "کمتر") + " از ۳۰ روزِ قبل: ${kotlin.math.abs(pct).fa()}٪",
                    tone = if (up) PillTone.Brand else PillTone.Coral,
                )
            }
        }
    }
}

@Composable
private fun DebtsCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("پیگیریِ طلبِ امروز", DigestSource.DEBTS, fresh, onAll = { go(Section.FollowUp) }) {
        Show(d.debts) { list ->
            if (list.isEmpty()) Calm("امروز کسی برای پیگیری نیست.")
            else d.debtsTotal.known?.let { Calm("جمعاً ${it.afn()}") }
            val max = (list.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1L)
            list.forEach { x ->
                BarRow(
                    label = x.name,
                    value = x.amount.afn(),
                    fraction = x.amount.toFloat() / max,
                    color = MaterialTheme.colorScheme.secondary,
                    sub = x.reason,
                    trailing = if (x.contactedToday) ({ Pill("امروز تماس شد", tone = PillTone.Neutral) }) else null,
                    onClick = { go(Section.FollowUp) },
                )
            }
        }
    }
}

@Composable
private fun WagesCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("کارمزدِ باز", DigestSource.WAGES, fresh, onAll = { go(Section.Payroll) }) {
        Show(d.wages) { w ->
            if (w.count == 0) Calm("کارمزدِ تسویه‌نشده‌ای نیست.")
            else {
                Big(w.total.afn())
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Pill("${w.count.fa()} کار", tone = PillTone.Neutral)
                    w.oldestDays?.let { Pill("قدیمی‌ترین ${it.fa()} روز", tone = if (w.old) PillTone.Coral else PillTone.Neutral) }
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════ انبار

@Composable
private fun MaterialsCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("مواد رو به اتمام", DigestSource.MATERIALS, fresh, onAll = { go(Section.PurchasePlan) }, allLabel = "پیشنهادِ خرید") {
        Show(d.materials) { rows ->
            if (rows.isEmpty()) Calm("هیچ ماده‌ای رو به اتمام نیست.")
            rows.take(HubDigest.TOP).forEach { m ->
                val why = (m.days as? Figure.Unknown)?.reason?.let { " • $it" }.orEmpty()
                Line(m.name, "مانده: ${qty(m.stock)} ${m.unit}$why") {
                    when (val f = m.days) {
                        is Figure.Known -> Pill("${f.value.fa()} روز", tone = PillTone.Coral)
                        is Figure.Unknown -> Pill("روز: نامعلوم", tone = PillTone.Neutral)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("پرفروشِ رو به اتمام و راکد", DigestSource.PRODUCTS, fresh, onAll = { go(Section.FinishedWarehouse) }) {
        Show(d.products) { p ->
            if (p.restock.isEmpty() && p.dead.isEmpty()) Calm("نه پرفروشی رو به اتمام است، نه کالایی راکد.")
            p.restock.forEach { r ->
                Line(
                    r.name + if (r.size.isNotBlank()) " • ${r.size}" else "",
                    "پیشنهادِ دوخت: ${r.make.fa()} عدد",
                ) {
                    when (val f = r.cover) {
                        is Figure.Known -> Pill(if (f.value <= 0) "تمام شده" else "${f.value.fa()} روز فروش", tone = PillTone.Coral)
                        is Figure.Unknown -> Pill("نامعلوم", tone = PillTone.Neutral)
                    }
                }
            }
            if (p.dead.isNotEmpty()) Calm("${p.dead.size.fa()} کالای راکد — ${p.deadValue.afn()} خوابیده.")
        }
    }
}

@Composable
private fun BelowCostCard(d: HubDigest.Digest, fresh: String, go: (Section) -> Unit) {
    DashFrame("زیرِ قیمتِ تمام‌شده", DigestSource.PRICES, fresh, onAll = { go(Section.FinishedWarehouse) }) {
        Show(d.belowCost) { list ->
            if (list.isEmpty()) Calm("هیچ کالایی معمولاً زیرِ بهای تمام‌شده‌اش فروخته نمی‌شود.")
            list.forEach { b ->
                Line(
                    b.name + if (b.size.isNotBlank()) " • ${b.size}" else "",
                    "قیمتِ معمول ${b.usualPrice.afn()} — بها ${b.cost.afn()} (${b.samples.fa()} فروش)",
                )
            }
            Text(
                "فقط کالاهایی با دست‌کم ${PriceAdvisor.MIN_SAMPLES.fa()} فروش در ${PriceAdvisor.WINDOW_DAYS.fa()} روز.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════ کمکی

/** «همین حالا»، «۳ دقیقه پیش»، «۲ ساعت پیش» یا تاریخ. */
internal fun freshness(at: Long, now: Long): String {
    val m = ((now - at) / 60_000L).coerceAtLeast(0)
    return when {
        m < 1 -> "همین حالا"
        m < 60 -> "${m.fa()} دقیقه پیش"
        m < 24 * 60 -> "${(m / 60).fa()} ساعت پیش"
        else -> PersianDate.shortWithTime(at)
    }
}

private fun qty(v: Double): String =
    (if (v == kotlin.math.floor(v)) v.toLong().toString() else ((v * 10).toLong() / 10.0).toString()).toPersianDigits()
