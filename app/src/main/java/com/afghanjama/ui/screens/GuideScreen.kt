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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.afghanjama.ui.components.AppScreen

/**
 * راهنمای کوتاهِ استفاده — همان چیزی که در RAHNAMA.md هست، ولی داخلِ اپ.
 *
 * عمداً کوتاه است: کسی وسطِ کار دفترچه نمی‌خواند. هر بخش یک کارت است تا
 * چشم بتواند فقط همان قسمتی را که لازم دارد پیدا کند.
 */
@Composable
fun GuideScreen(onBack: () -> Unit) {
    AppScreen(title = "راهنما", onBack = onBack) { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "کوتاه است، چون خودِ اپ باید واضح باشد نه دفترچه‌اش.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.RocketLaunch,
                    title = "روزِ اول",
                    lines = listOf(
                        "اطلاعات پایه: خیاط، ناظر، پارچه، رنگ، سایز.",
                        "کارکنان: هر کسی که حقوقِ ماهانه می‌گیرد، با مبلغش.",
                        "انبار مواد: آنچه همین حالا دارید، با تعداد و قیمت.",
                        "اگر چیزی جا افتاد، هر جا لازم شد همان‌جا اضافه می‌شود."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.ContentCut,
                    title = "یک سفارش از اول تا آخر",
                    lines = listOf(
                        "۱. ثبت سفارش — مشتری، طرح، تعداد، پارچه، قیمت، مهلت. بیعانه را همان‌جا ثبت کنید.",
                        "۲. برش — مسئولِ برش و تعداد. اندازه‌ها بالای صفحه است. موادِ سفارش همین‌جا از انبار کم می‌شود، نه موقعِ ثبت.",
                        "۳. دوخت — تقسیم بین خیاط‌ها با کارمزدِ فی‌عدد. دکمهٔ «رسید» اندازه‌ها و کارمزد را برای خیاط می‌فرستد.",
                        "۴. نظارت — تأیید یا برگشت. اگر برگشت خورد و کار بین چند خیاط بود، بگویید کارِ کدام بوده.",
                        "۵. آمادهٔ تحویل — کارِ تأییدشده خودکار اینجا می‌آید.",
                        "۶. تحویل — قیمت و بیعانه خودش می‌آید؛ نقد را بزنید، باقی‌مانده را نشان می‌دهد."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.Payments,
                    title = "پول کجاست",
                    lines = listOf(
                        "پرداخت به هر کسی → داشبورد، دکمهٔ «پرداخت».",
                        "دریافت از هر کسی → داشبورد، دکمهٔ «دریافت».",
                        "خرید، فروش و برگشتی‌ها → «معاملات روزمره».",
                        "کی به ما بدهکار است و ما به کی → «دفتر کل».",
                        "سود و زیان و ترازنامه → «گزارش‌ها»."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.Handshake,
                    title = "بیعانه و پیش‌پرداخت",
                    lines = listOf(
                        "بیعانه‌ای که پیش از تحویل می‌گیرید بدهیِ شماست، نه درآمد.",
                        "موقعِ تحویل خودش پیشنهاد می‌شود که روی همان فروش اعمال شود — دو بار حساب نمی‌شود.",
                        "اگر کارمندی زودتر از موعد پول خواست، از «پرداخت» ثبتش کنید؛ طلبِ شما از او می‌ماند و از حقوقش کم می‌شود."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.EventAvailable,
                    title = "عادت‌های خوب",
                    lines = listOf(
                        "هر روز صبح: «مرکز هشدار». اگر روزی فقط یک صفحه باز می‌کنید، همین باشد.",
                        "هفته‌ای یک بار: تنظیمات ← «پشتیبان‌گیری کامل»، و فایل را بیرون از گوشی نگه دارید.",
                        "پشتیبانِ خودکارِ روزانه فقط دیتابیس است و روی همین گوشی می‌ماند؛ آنکه خودتان می‌گیرید عکس‌ها را هم دارد.",
                        "اندازه‌ها را یک بار در صفحهٔ مشتری وارد کنید؛ در برش و دوخت خودش می‌آید.",
                        "قیمتِ توافقی را موقعِ ثبتِ سفارش بنویسید، نه موقعِ تحویل."
                    ),
                    highlight = true
                )
            }

            item {
                GuideCard(
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    title = "چیزهایی که ممکن است گیج‌کننده باشد",
                    lines = listOf(
                        "سفارشِ تأییدشده وارد «انبار محصول» می‌شود چون تا دستِ مشتری نرسیده مالِ کارگاه است. اگر مشتری دارد، از «آمادهٔ تحویل» تحویلش بدهید.",
                        "اگر دوربینِ اسکنر بالا نیامد، اپ کدِ سفارش را می‌پرسد. کدِ کوتاه هم قبول است.",
                        "کارنامهٔ خیاط فقط سفارشی را کنار می‌گذارد که بین چند خیاط بوده و ناظر نگفته برگشتِ کارِ کدام بوده."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.Group,
                    title = "نقش‌ها",
                    lines = listOf(
                        "هر کس فقط چیزی را می‌بیند که به کارش می‌آید.",
                        "خیاط «کارِ من» را می‌بیند: کارِ زیرِ دستش و کارمزدش.",
                        "فهرستِ مشتریان و شماره‌هایشان فقط برای مدیر و بخشِ فروش باز است.",
                        "روی گوشیِ خیاط، یک بار در تنظیمات مشخص کنید دستِ کدام خیاط است — از نامِ ورود حدس زده نمی‌شود، چون دو خیاطِ هم‌نام کارِ همدیگر را می‌دیدند."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.Print,
                    title = "چاپ فاکتور و رسید",
                    lines = listOf(
                        "هر سند از صفحهٔ «اسناد» باز می‌شود: چاپ، PDF، یا تصویر.",
                        "اندازهٔ کاغذ را خودتان می‌گویید: A4، A5، یا رولِ ۸ سانتی. انتخابتان یادش می‌ماند.",
                        "برای واتساپ «تصویر» را بزنید؛ عکس مطمئن‌تر از PDF می‌رسد.",
                        "فاکتور فروش جدولِ کالاها را دارد و پایینش بدهی قبلی، پرداخت و مبلغ قابل پرداخت — هر سه از دفتر کل."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.Warning,
                    title = "کسری",
                    lines = listOf(
                        "اگر جنس در انبار نباشد، فروش و تحویل باز هم انجام می‌شود و کمبود قرمز به‌عنوان «کسری» می‌ماند.",
                        "کسری یعنی «به این اندازه فروخته‌ایم که هنوز نداریم». با ورودِ بعدیِ همان کالا خودش تسویه می‌شود.",
                        "بهای تمام‌شده هم خودش اصلاح می‌شود؛ لازم نیست کاری بکنید.",
                        "اگر اپ را سخت‌گیر می‌خواهید: تنظیمات ← فروشِ بیشتر از موجودی را خاموش کنید."
                    )
                )
            }

            item {
                GuideCard(
                    icon = Icons.Default.DeleteForever,
                    title = "ریست داده",
                    lines = listOf(
                        "تنظیمات ← کارتِ قرمزِ پایینِ صفحه. فقط مدیر می‌بیندش.",
                        "کارها و حساب‌ها پاک می‌شوند؛ خیاط‌ها، پارچه‌ها، طرح‌ها و مشتری‌ها با اندازه‌هایشان می‌مانند.",
                        "پیش از پاک‌کردن، پشتیبانِ کامل خودش در Downloads گذاشته می‌شود — پشیمان شدید، بازیابی‌اش کنید.",
                        "برای تأیید باید «پاک کن» را بنویسید. این کار برگشت ندارد."
                    )
                )
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun GuideCard(
    icon: ImageVector,
    title: String,
    lines: List<String>,
    highlight: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (highlight) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            lines.forEach { line ->
                Text(
                    "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlight) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
