package com.afghanjama.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import com.afghanjama.AppInfo
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.selftest.checkCustomerLedger
import com.afghanjama.selftest.checkDiscountMath
import com.afghanjama.selftest.checkInvoiceTotals
import com.afghanjama.selftest.checkMoneySplit
import com.afghanjama.selftest.checkMultiLineInvoice
import com.afghanjama.selftest.checkOrderCycle
import com.afghanjama.selftest.checkSalaryAdvance
import com.afghanjama.selftest.checkShortage
import com.afghanjama.selftest.checkStockValuation
import com.afghanjama.selftest.checkTailorAttribution
import com.afghanjama.selftest.checkWorkSummary
import com.afghanjama.ui.format.PersianDate

// رنگ‌های خودِ اپ — همان‌هایی که در Theme.kt اندروید هستند
private val Brand = Color(0xFF1F6E5C)
private val BrandSoft = Color(0xFFD3EDE3)
private val Ink = Color(0xFF1B1C1A)
private val Muted = Color(0xFF5F5E58)
private val Bg = Color(0xFFF7F6F3)
private val CardBg = Color(0xFFFFFFFF)
private val Bad = Color(0xFFB3261E)

/**
 * فونتِ اپ، از همان فایلی که اندروید برمی‌دارد.
 *
 * بدونِ این، فارسی با فونتِ پیش‌فرضِ سیستم نوشته می‌شود که روی ویندوز
 * اغلب حروف را نمی‌چسباند و اعدادِ فارسی را بد می‌کشد.
 */
private val Vazirmatn: FontFamily = runCatching {
    FontFamily(
        Font("vazirmatn_regular.ttf", FontWeight.Normal),
        Font("vazirmatn_semibold.ttf", FontWeight.SemiBold),
        Font("vazirmatn_bold.ttf", FontWeight.Bold)
    )
}.getOrElse {
    // نبودنِ فونت نباید برنامه را بیندازد؛ فارسی بدشکل بهتر از پنجرهٔ
    // بازنشده است.
    FontFamily.Default
}

/**
 * بررسی‌هایی که به دفتر کاری ندارند و فقط ریاضیِ پول را می‌سنجند.
 *
 * همین‌ها روی گوشی هم اجرا می‌شوند — کلمه به کلمه همان کد، از `:core`.
 * اگر اینجا سبز باشند، یعنی حسابداریِ کارگاه روی ویندوز همان جوابی را
 * می‌دهد که روی گوشی می‌دهد. اثباتِ اصلیِ این فاز همین است.
 */
private fun runPureChecks(): List<CheckResult> =
    checkMoneySplit() +
        checkCustomerLedger() +
        checkTailorAttribution() +
        checkMultiLineInvoice() +
        checkStockValuation() +
        checkInvoiceTotals() +
        checkShortage() +
        checkDiscountMath() +
        checkOrderCycle() +
        checkSalaryAdvance() +
        checkWorkSummary()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "${AppInfo.NAME} — نسخهٔ ویندوز"
    ) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Brand,
                background = Bg,
                surface = CardBg,
                onSurface = Ink
            )
        ) {
            // کلِ برنامه راست‌به‌چپ، مستقلِ از زبانِ ویندوز — همان
            // کاری که AfghanJamaTheme روی اندروید می‌کند.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val results = remember { runPureChecks() }
    val failed = results.count { it.status == CheckStatus.FAIL }
    val passed = results.count { it.status == CheckStatus.PASS }

    Column(
        Modifier.fillMaxSize().background(Bg).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header()

        SummaryCard(passed = passed, failed = failed, total = results.size)

        Text(
            "این‌ها همان بررسی‌هایی‌اند که روی گوشی هم اجرا می‌شوند — " +
                "کلمه به کلمه همان کد، از ماژولِ مشترک.",
            fontFamily = Vazirmatn, fontSize = 13.sp, color = Muted
        )

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            results.groupBy { it.group }.forEach { (group, rows) ->
                Text(
                    group,
                    fontFamily = Vazirmatn,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Ink,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                rows.forEach { ResultRow(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(52.dp).height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BrandSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("خ", fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = Brand)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                AppInfo.NAME,
                fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
                fontSize = 26.sp, color = Ink
            )
            Text(
                PersianDate.long(System.currentTimeMillis()),
                fontFamily = Vazirmatn, fontSize = 13.sp, color = Muted
            )
        }
    }
}

@Composable
private fun SummaryCard(passed: Int, failed: Int, total: Int) {
    val ok = failed == 0
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (ok) BrandSoft else Color(0xFFF9DEDC))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (ok) "حسابداری روی ویندوز همان جوابِ گوشی را می‌دهد"
                else "$failed مورد اینجا جوابِ دیگری داد",
                fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = if (ok) Brand else Bad
            )
            Text(
                "$passed قبول از $total بررسی",
                fontFamily = Vazirmatn, fontSize = 13.sp,
                color = if (ok) Brand else Bad
            )
        }
    }
}

@Composable
private fun ResultRow(r: CheckResult) {
    val bad = r.status == CheckStatus.FAIL
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CardBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (bad) "✕" else "✓",
            fontFamily = Vazirmatn, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, color = if (bad) Bad else Brand
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(r.name, fontFamily = Vazirmatn, fontSize = 14.sp, color = Ink)
            Text(r.detail, fontFamily = Vazirmatn, fontSize = 12.sp, color = Muted)
        }
    }
}
