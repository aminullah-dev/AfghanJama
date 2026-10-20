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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.AppInfo
import com.afghanjama.desktop.data.LedgerStatus
import com.afghanjama.desktop.data.LedgerStatusViewModel
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.SelfCheckViewModel

/*
 * خودآزماییِ حساب و حالِ دفتر — چیزهایی که تا ۱.۹ کلِ صفحهٔ اولِ
 * کامپیوتر بودند.
 *
 * از ۱.۱۰ صفحهٔ اول «خانه» است، همان خانهٔ گوشی. این دو کارت جابه‌جا شدند،
 * پاک نشدند:
 *
 * - کارتِ خودآزماییِ حساب ([SelfCheckCard]) بالای «خودآزمایی و سلامتِ داده».
 * - کارتِ دفتر ([LedgerCard]) بالای «پشتیبان و بازیابی» — همان جایی که
 *   کسی دنبالِ «دفتر کجاست و سالم است؟» می‌گردد.
 *
 * و وقتی دفتر اصلاً باز نشد، [LedgerProblem] تنها چیزی است که پنجره نشان
 * می‌دهد: بردنِ کاربر به صفحه‌هایی که دادهٔ خالی نشان می‌دهند بدتر از
 * نبودنِ آن‌هاست.
 */

/**
 * پنجره وقتی دفتر باز نشد: نام، تاریخ، کارتِ خطای دفتر، و خودآزماییِ
 * حساب (که به دفتر کاری ندارد و همچنان اجرا می‌شود).
 */
@Composable
internal fun LedgerProblem() {
    val ledgerVm: LedgerStatusViewModel = viewModel { LedgerStatusViewModel() }
    val ledger by ledgerVm.ui.collectAsState()
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Header()
        LedgerCard(ledger)
        SelfCheckCard(showRows = true)
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
            Text(
                "خ",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Brand
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                AppInfo.NAME,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
            Text(
                PersianDate.long(System.currentTimeMillis()),
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
    }
}

/**
 * خودآزماییِ حساب: همان ریاضیِ پول که روی گوشی اجرا می‌شود، اینجا هم.
 *
 * [showRows] تک‌تکِ بندها را هم می‌کشد — برای صفحهٔ «دفتر باز نشد». بالای
 * «خودآزمایی و سلامتِ داده» فقط خلاصه بس است؛ زیرش فهرستِ کاملِ سلامتِ داده
 * می‌آید.
 */
@Composable
internal fun SelfCheckCard(showRows: Boolean = false) {
    val vm: SelfCheckViewModel = viewModel { SelfCheckViewModel() }
    val ui by vm.ui.collectAsState()
    val ok = ui.failed == 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (ok) BrandSoft else BadSoft)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    when {
                        ui.running -> "در حالِ بررسی…"
                        ok -> "حسابداری روی این کامپیوتر همان جوابِ گوشی را می‌دهد"
                        else -> "${ui.failed.fa()} مورد اینجا جوابِ دیگری داد"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) Brand else Bad
                )
                Text(
                    "${ui.passed.fa()} قبول از ${ui.results.size.fa()} بررسی — " +
                        "کلمه به کلمه همان کدِ گوشی، از ماژولِ مشترک.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) Brand else Bad
                )
            }
            // دکمهٔ واقعی: `viewModelScope` را روی کامپیوتر به کار می‌اندازد.
            // اگر کار کند، یعنی کوروتینِ ViewModel اینجا هم زنده است.
            Button(
                onClick = { vm.run() },
                enabled = !ui.running,
                shape = PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                Text("بررسیِ دوباره", style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showRows) {
            ui.results.groupBy { it.group }.forEach { (group, rows) ->
                Text(
                    group,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                rows.forEach { ResultRow(it) }
            }
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
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (bad) Bad else Brand
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(r.name, style = MaterialTheme.typography.bodyMedium, color = Ink)
            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

/**
 * حالِ دفتر: باز شد یا نه، چند جدول، نسخهٔ دفتر، و مسیرِ فایل.
 *
 * روی دفترِ نو عددها صفرند و همان هم خبرِ خوبی است: یعنی فایل ساخته شد،
 * جدول‌ها نشستند و پرس‌وجوها جواب دادند.
 */
@Composable
internal fun LedgerCard(s: LedgerStatus) {
    val failed = s.error != null
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (failed) BadSoft else CardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            when {
                s.opening -> "در حالِ باز کردنِ دفتر…"
                failed -> "دفتر باز نشد"
                // «نسخهٔ دفتر»، نه «نسخه»: این عدد `DB_VERSION` است (اسکیمای
                // دیتابیس)، نه نسخهٔ برنامه. نسخهٔ برنامه پایینِ نوارِ کنار و
                // در «دربارهٔ خیاط‌یار» است.
                else -> "دفتر باز است — ${s.tables.fa()} جدول، نسخهٔ دفتر ${s.version.fa()}"
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (failed) Bad else Ink
        )
        if (failed) {
            Text(s.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Bad)
        } else if (!s.opening) {
            Text(
                "مشتری: ${s.customers.fa()}  •  صندوق: ${s.wallet.fa()} ؋  " +
                    "•  بانک: ${s.bank.fa()} ؋",
                style = MaterialTheme.typography.bodySmall,
                color = Muted
            )
        }
        if (s.path.isNotBlank()) {
            // مسیر چپ‌به‌راست: در پاراگرافِ راست‌به‌چپ `/`ِ اولش به آن سرِ
            // سطر می‌پرید و مسیر غلط خوانده می‌شد.
            Text(
                s.path,
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                color = Muted
            )
        }
    }
}
