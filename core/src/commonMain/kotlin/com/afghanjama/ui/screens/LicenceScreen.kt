package com.afghanjama.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.afghanjama.licence.LicencePolicy
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.LicenceText
import com.afghanjama.licence.Lnm1
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.ui.components.AccentCard
import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.AppScreen
import com.afghanjama.ui.components.InfoRow
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.vm.LicenceViewModel

/**
 * لایسنس — وضعیت، کدِ این دستگاه، و جای واردکردنِ کلید.
 *
 * روند همان است که پروتکل می‌گوید و هیچ‌جایش اینترنت نمی‌خواهد:
 *
 * ۱. کارگاه کدِ دستگاه را (تلفنی، واتساپ، یا حضوری) به لینومیک می‌دهد.
 * ۲. لینومیک کلید را صادر می‌کند.
 * ۳. کارگاه کلید را اینجا می‌چسباند یا فایلِ `.lnmlic` را باز می‌کند.
 *
 * [onOpenFile] جایی است که سکو فایل‌گزین دارد (گوشی و ویندوز/مک)؛ `null`
 * یعنی این سکو فایل‌گزین ندارد و فقط چسباندن هست.
 *
 * بی آیکن، عمداً: همهٔ کارها با متن گفته می‌شوند.
 */
@Composable
fun LicenceScreen(
    vm: LicenceViewModel,
    onBack: () -> Unit,
    onOpenFile: (() -> Unit)? = null,
) {
    val ui by vm.ui.collectAsState()
    val s = ui.status
    val clipboard = LocalClipboardManager.current
    val system = LocalSystemActions.current

    AppScreen(title = "لایسنس", onBack = onBack) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- وضعیت ----------
            if (s != null) {
                val readOnly = s.state == LicenceState.READ_ONLY
                val good = s.state == LicenceState.LICENSED && !s.needsBanner
                AccentCard(
                    container = when {
                        readOnly -> MaterialTheme.colorScheme.errorContainer
                        good -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    val on = when {
                        readOnly -> MaterialTheme.colorScheme.onErrorContainer
                        good -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onTertiaryContainer
                    }
                    Text(
                        LicenceText.title(s),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = on
                    )
                    Text(LicenceText.reason(s), style = MaterialTheme.typography.bodyMedium, color = on)
                    LicenceText.remedy(s)?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = on)
                    }
                    if (s.clockRolledBack) {
                        Text(
                            LicenceText.CLOCK_NOTICE + " تا درست نشود، دیرترین تاریخی که اپ دیده مبنای حساب است.",
                            style = MaterialTheme.typography.bodySmall,
                            color = on
                        )
                    }
                    if (s.storedKeyRejected) {
                        Text(
                            "کلیدی که پیش‌تر نصب شده بود دیگر پذیرفته نمی‌شود؛ کلیدِ تازه بگیرید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = on
                        )
                    }
                    if (s.state != LicenceState.LICENSED || readOnly) {
                        Text(LicenceText.ALWAYS_WORKS, style = MaterialTheme.typography.bodySmall, color = on)
                    }
                }
            }

            // ---------- جزئیاتِ لایسنس ----------
            s?.licence?.let { lic ->
                AppCard {
                    Text("لایسنسِ نصب‌شده", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    InfoRow("کارگاه", lic.customer)
                    InfoRow("شمارهٔ لایسنس", lic.id)
                    InfoRow("تاریخِ صدور", LicenceText.date(lic.issued))
                    InfoRow("اعتبار تا", lic.expires?.let { LicenceText.date(it) } ?: "همیشگی")
                    InfoRow("دستگاه", if (lic.anyMachine) "هر دستگاه" else lic.machine)
                }
            }

            // ---------- کدِ دستگاه ----------
            AppCard {
                Text("کدِ این دستگاه", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (s?.coveredByMain == true)
                        "این گوشی کارگرِ کارگاه است و لایسنسِ جدا نمی‌خواهد. کد فقط برای وقتی است که روزی گوشیِ اصلی یا تنها شود."
                    else "این کد را به لینومیک بدهید — تلفنی، در واتساپ، یا حضوری. کلیدی که می‌گیرید فقط روی همین دستگاه کار می‌کند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // کد لاتین است و باید چپ‌به‌راست و تکه‌تکه خوانده شود.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        Text(
                            ui.machineCode.ifBlank { "…" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }
                if (ui.machineFallback) {
                    Text(
                        "شناسهٔ سخت‌افزارِ این دستگاه خوانده نشد؛ این کد از یک شناسهٔ ذخیره‌شده در همین نصب آمده. " +
                            "اگر برنامه پاک و دوباره نصب شود، کد عوض می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(ui.machineCode)) },
                        enabled = ui.machineCode.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("کپی") }
                    OutlinedButton(
                        onClick = {
                            system.shareText(
                                "کدِ دستگاهِ خیاط‌یار",
                                "کدِ دستگاهِ خیاط‌یار برای لایسنس: ${ui.machineCode}"
                            )
                        },
                        enabled = ui.machineCode.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("فرستادن") }
                }
            }

            // ---------- واردکردنِ کلید ----------
            AppCard {
                Text("واردکردنِ کلید", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "کلید با «${Lnm1.PREFIX}.» شروع می‌شود. همه‌اش را بچسبانید؛ فاصله و خطِ تازه مهم نیست.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = ui.keyText,
                        onValueChange = vm::setKey,
                        label = { Text("کلیدِ لایسنس") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = { vm.activate() },
                    enabled = ui.keyText.isNotBlank() && !ui.working,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (ui.working) "در حالِ بررسی…" else "فعال‌سازی") }
                if (onOpenFile != null) {
                    OutlinedButton(
                        onClick = onOpenFile,
                        enabled = !ui.working,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("باز کردنِ فایلِ لایسنس") }
                }
                ui.message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (ui.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ---------- قاعده‌ها، کوتاه ----------
            AppCard {
                Text("چطور کار می‌کند", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                listOf(
                    "بی‌لایسنس، ${LicencePolicy.TRIAL_DAYS.fa()} روز و تا ${LicencePolicy.TRIAL_ORDER_CAP.fa()} سفارشِ تازه همه‌چیز کامل کار می‌کند؛ روی کاغذ نشانِ «${LicenceText.WATERMARK}» می‌نشیند.",
                    "پس از آن برنامه فقط‌خواندنی می‌شود: هیچ داده‌ای پاک یا پنهان نمی‌شود، و دیدن، جست‌وجو، چاپ، PDF، واتساپ و پشتیبان کار می‌کنند.",
                    "کلید همان لحظه همهٔ محدودیت‌ها را برمی‌دارد — بی نصبِ دوباره و بی واردکردنِ دوبارهٔ داده.",
                    "یک لایسنس برای کلِ کارگاه: روی دستگاهِ اصلی (یا تنها) نصب می‌شود و گوشی‌های کارگر با همان کار می‌کنند.",
                    "اگر دستگاه عوض شد، ${LicencePolicy.GRACE_DAYS.fa()} روز مهلت هست تا کلیدِ تازه بگیرید. اینترنت هیچ‌جا لازم نیست.",
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
