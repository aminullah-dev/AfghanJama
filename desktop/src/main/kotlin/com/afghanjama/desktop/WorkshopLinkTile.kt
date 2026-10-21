package com.afghanjama.desktop

import com.afghanjama.ui.components.AppCard
import com.afghanjama.ui.components.Pill
import com.afghanjama.ui.components.PillTone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.data.DesktopLanHost
import com.afghanjama.lan.DeviceKind
import com.afghanjama.lan.HubOff
import com.afghanjama.lan.LanHub
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.Access
import com.afghanjama.ui.vm.WorkshopLinkAccess
import com.afghanjama.ui.vm.hubOffText
import com.afghanjama.ui.vm.phonesSeenText
import com.afghanjama.ui.vm.phonesToday
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * کاشیِ «اشتراکِ کارگاه» بالای صفحهٔ اولِ کامپیوتر.
 *
 * **چرا لازم شد.** کارفرما روی مک اشتراکِ کارگاه را پیدا نکرد: سی‌ونهمین
 * بندِ نوار بود و صفحهٔ اول هیچ نشانی از آن نداشت. حالا همین که برنامه باز
 * شود، سه پرسشِ او جواب دارند:
 *
 * - این کامپیوتر دستگاهِ اصلیِ کارگاه است، و اشتراک روشن است؟
 * - امروز چند گوشی به آن رسیده، و آخرین بار کی؟ ([phonesToday])
 * - چند درخواست منتظرِ تأیید است؟ — فقط برای کسی که می‌تواند تأییدشان کند
 *   ([WorkshopLinkAccess.seesRequests])، همان قاعدهٔ نشانِ نوار.
 *
 * و نشانیِ کامپیوتر درشت، تا کارفرما بتواند از پشتِ میز برای کارگر بخوانَد.
 * QR و رمز در خودِ صفحهٔ اشتراک‌اند، یک کلیک آن‌طرف‌تر.
 *
 * چیزی اینجا نوشته نمی‌شود و هیچ دکمه‌ای سرور را روشن یا خاموش نمی‌کند؛
 * کاشی فقط می‌خوانَد ([LanHub]) و به صفحهٔ اشتراک می‌برد.
 */
@Composable
internal fun WorkshopLinkTile(
    repo: Repo,
    access: Access,
    onOpen: () -> Unit,
    /** برنامه همیشه [LanHub.shared]؛ عکس‌برداری هابِ خودش را روی loopback می‌دهد. */
    hub: LanHub = LanHub.shared,
) {
    val settings = LocalSettings.current
    val lan = remember { DesktopLanHost() }
    // هر بار که صفحهٔ اول باز می‌شود دوباره خوانده می‌شود؛ «اصلی» شدن در
    // صفحهٔ اشتراک رخ می‌دهد و برگشتن به اینجا کاشی را از نو می‌سازد.
    val mode = remember { LanPrefs.mode(settings) }
    val state by hub.state.collectAsState()
    val seen by hub.seen.collectAsState()
    val seesRequests = WorkshopLinkAccess.seesRequests(access)
    val pending by remember(repo, seesRequests) {
        if (seesRequests) repo.observePendingRequestCount() else flowOf(0)
    }.collectAsState(0)
    // نشانی از کارت‌های شبکه خوانده می‌شود؛ نه روی رشتهٔ صفحه.
    val ip by produceState<String?>(null, state.serving) {
        value = withContext(Dispatchers.IO) { lan.localIp() }
    }
    // «امروز» با گذشتنِ نیمه‌شب هم عوض شود، نه فقط با رسیدنِ گوشیِ تازه.
    val now by produceState(nowMillis()) {
        while (true) {
            delay(60_000)
            value = nowMillis()
        }
    }

    val main = mode == DeviceMode.MAIN
    val off = state.off
    // «خاموش» به دستِ مدیر یا «در حالِ روشن شدن» مشکل نیست؛ بقیه کارِ کسی است.
    val problem = main && !state.serving && off != null &&
        off != HubOff.STOPPED && off != HubOff.WAITING

    val headline = when (mode) {
        DeviceMode.MAIN -> "این کامپیوتر دستگاهِ اصلیِ کارگاه است"
        DeviceMode.WORKER -> "این کامپیوتر به دستگاهِ اصلیِ کارگاه وصل است"
        DeviceMode.STANDALONE -> "این کامپیوتر هنوز دستگاهِ اصلیِ کارگاه نیست"
    }
    val detail = when (mode) {
        DeviceMode.MAIN ->
            if (state.serving) "کارگاهِ وصل روشن است؛ گوشیِ کارگران به همین کامپیوتر وصل می‌شوند."
            else hubOffText(off) ?: "کارگاهِ وصل خاموش است."
        DeviceMode.WORKER ->
            "نشانیِ دستگاهِ اصلی: ${LanPrefs.host(settings).ifBlank { "—" }.toPersianDigits()}"
        DeviceMode.STANDALONE ->
            "تا دستگاهِ اصلی نشود، گوشیِ کارگران به آن وصل نمی‌شوند. " +
                if (WorkshopLinkAccess.canRunHub(access)) "در «کارگاهِ وصل» راه‌اندازی‌اش کنید."
                else "راه‌اندازی‌اش با مدیر است."
    }
    // همان قرص و همان لحنِ «کارگاهِ وصل»: روشن آبی، خاموشِ مشکل‌دار مرجانی.
    val (pill, tone) = when {
        main && state.serving -> "روشن" to PillTone.Brand
        problem -> "خاموش" to PillTone.Coral
        mode == DeviceMode.WORKER -> "وصل به دستگاهِ اصلی" to PillTone.Brand
        else -> "خاموش" to PillTone.Neutral
    }

    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Section.WorkshopLink.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Muted
                )
                Text(
                    headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) Bad else Muted
                )
            }
            Pill(pill, tone = tone)
        }

        if (main) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(phonesToday(seen, now).fa(), "گوشیِ وصل‌شدهٔ امروز", Modifier.weight(1f))
                if (seesRequests) {
                    Stat(
                        pending.fa(),
                        "درخواستِ منتظرِ تأیید",
                        Modifier.weight(1f),
                        highlight = pending > 0
                    )
                }
                Stat((ip ?: "—").toPersianDigits(), "نشانیِ این کامپیوتر", Modifier.weight(1.4f))
            }
            val last = seen.lastAt
            Text(
                if (phonesToday(seen, now) > 0 && last != null) "آخرین گوشی ساعتِ ${PersianDate.time(last)} رسید."
                else phonesSeenText(seen, DeviceKind.COMPUTER, now),
                style = MaterialTheme.typography.labelSmall,
                color = Muted
            )
        }

        Button(
            onClick = onOpen,
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) {
            Text(
                when {
                    main && seesRequests && pending > 0 -> "دیدن و تأییدِ درخواست‌ها"
                    // QRِ سرورِ خاموش را صفحهٔ اشتراک نشان نمی‌دهد؛ دکمه همان را نوید ندهد.
                    main && state.serving -> "QR و رمز برای وصل کردنِ گوشی"
                    mode == DeviceMode.STANDALONE && WorkshopLinkAccess.canRunHub(access) -> "راه‌اندازیِ کارگاهِ وصل"
                    else -> "باز کردنِ کارگاهِ وصل"
                }
            )
        }
    }
}

/** یک عدد و برچسبش — گوشی‌ها، درخواست‌ها، نشانی. */
@Composable
private fun Stat(value: String, label: String, modifier: Modifier, highlight: Boolean = false) {
    val ink: Color = if (highlight) Brand else Ink
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) BrandSoft else Bg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
    }
}
