package com.afghanjama.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afghanjama.platform.LocalScreenBehavior
import com.afghanjama.ui.components.OrderCodeLine
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits
import com.afghanjama.ui.vm.BoardRow
import com.afghanjama.ui.vm.BoardViewModel
import kotlinx.coroutines.delay

// رنگ‌های تابلو عمداً ثابت‌اند و از تمِ اپ نمی‌آیند: این صفحه همیشه
// تیره است تا از آن‌طرفِ کارگاه خوانده شود، چه گوشی روشن باشد چه تاریک.
private val BoardBg = Color(0xFF0B0F14)
private val BoardHeader = Color(0xFF13202B)
private val BoardText = Color(0xFFE8EEF2)
private val BoardDim = Color(0xFF8FA3B0)
private val BoardLate = Color(0xFFFF5252)
private val BoardWarn = Color(0xFFFFC046)
private val BoardOk = Color(0xFF5BD6A6)

/** چند سطر در هر صفحهٔ تابلو؛ بیشتر از این روی گوشی ریز می‌شود. */
private const val ROWS_PER_PAGE = 7
private const val PAGE_MS = 9_000L

/**
 * تابلوی «در حال دوخت» — مثلِ تابلوی پروازِ فرودگاه.
 *
 * برای گوشی یا تبلتی که روی دیوارِ کارگاه می‌ماند: صفحه خاموش نمی‌شود،
 * خودش تازه می‌شود، و اگر کارها از یک صفحه بیشتر باشند خودش ورق می‌زند.
 * دیرشده‌ها قرمز و بالای فهرست‌اند — مثلِ پروازِ تأخیردار.
 */
@Composable
fun BoardScreen(
    vm: BoardViewModel,
    onBack: () -> Unit
) {
    val settings = LocalSettings.current
    val ui by vm.ui.collectAsState()
    val designCodeByOrder by vm.designCodeByOrder.collectAsState()
    val screen = LocalScreenBehavior.current

    LaunchedEffect(Unit) { vm.start(settings) }

    // صفحه نباید خاموش شود — تابلوی خاموش تابلو نیست.
    screen.KeepAwake()

    // برگشت با دکمهٔ سیستم هم کار کند، چون نوارِ بالا اینجا نداریم
    screen.HandleBack(onBack)

    val pages = remember(ui.rows.size) {
        if (ui.rows.isEmpty()) 1 else (ui.rows.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE
    }
    var page by remember { mutableIntStateOf(0) }

    LaunchedEffect(pages) {
        page = 0
        while (pages > 1) {
            delay(PAGE_MS)
            page = (page + 1) % pages
        }
    }

    // ساعتِ بالای تابلو باید هر ثانیه جلو برود؛ خواندنِ `clock` داخلِ
    // ترکیب همان چیزی است که بازترسیم را راه می‌اندازد.
    var clock by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(1_000); clock++ }
    }
    val nowText = remember(clock) {
        PersianDate.shortWithTime(System.currentTimeMillis()).toPersianDigits()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BoardBg)
            // ضربه روی تابلو برمی‌گرداند — بدونِ دکمه، تا چیزی روی
            // دیوار اضافه دیده نشود
            .clickable(onClick = onBack)
    ) {
        // ---------------- سرِ تابلو ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(BoardHeader)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "در حال دوخت",
                    color = BoardText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ui.rows.size.fa()} کار • ${ui.pieces.fa()} عدد • " +
                        "${ui.tailors.fa()} خیاط" +
                        if (ui.lateCount > 0) " • ${ui.lateCount.fa()} دیرشده" else "",
                    color = if (ui.lateCount > 0) BoardLate else BoardDim,
                    fontSize = 14.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    nowText,
                    color = BoardText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    when {
                        ui.error != null -> "قطع از گوشیِ اصلی"
                        ui.remote -> "از گوشیِ اصلی"
                        else -> "زنده"
                    },
                    color = if (ui.error != null) BoardLate else BoardOk,
                    fontSize = 12.sp
                )
            }
        }

        // ---------------- عنوان ستون‌ها ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            HeaderCell("خیاط", 3f)
            HeaderCell("سفارش", 2.4f)
            HeaderCell("طرح", 3f)
            HeaderCell("تعداد", 1.2f, TextAlign.Center)
            HeaderCell("وضعیت", 2.4f, TextAlign.End)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(BoardDim.copy(alpha = 0.3f))
        )

        // ---------------- سطرها ----------------
        Box(Modifier.weight(1f)) {
            if (ui.rows.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        ui.error ?: "هیچ کاری زیرِ دست نیست",
                        color = if (ui.error != null) BoardLate else BoardDim,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        fadeIn(tween(500)) togetherWith fadeOut(tween(500))
                    },
                    label = "board-page"
                ) { p ->
                    Column(Modifier.fillMaxSize()) {
                        ui.rows
                            .drop(p * ROWS_PER_PAGE)
                            .take(ROWS_PER_PAGE)
                            .forEach {
                                BoardRowView(it, designCodeByOrder[it.orderCode].orEmpty())
                            }
                    }
                }
            }
        }

        // ---------------- پای تابلو ----------------
        Row(
            Modifier
                .fillMaxWidth()
                .background(BoardHeader)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(CompanyPrefs.shopName(LocalSettings.current), color = BoardDim, fontSize = 12.sp)
            if (pages > 1) {
                Text(
                    "صفحهٔ ${(page + 1).fa()} از ${pages.fa()}",
                    color = BoardDim,
                    fontSize = 12.sp
                )
            }
            Text("برای برگشت، صفحه را لمس کنید", color = BoardDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start
) {
    Text(
        text,
        color = BoardDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = align,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun BoardRowView(row: BoardRow, designCode: String) {
    val color = when {
        row.late -> BoardLate
        row.warn -> BoardWarn
        else -> BoardOk
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            row.tailor,
            color = BoardText,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f)
        )
        // تابلو یک جدولِ ستون‌ثابت است که از فاصلهٔ چند متری خوانده
        // می‌شود؛ دو خطِ روی هم مثلِ بقیهٔ صفحه‌ها اینجا ستون‌ها را به هم
        // می‌ریزد و از دور ناخوانا می‌شود. پس یک کد بیشتر جا نمی‌شود:
        // کدِ طرح اگر هست، وگرنه کدِ اپ. نامِ طرح که ستونِ بعدی است.
        Text(
            row.orderCode.toPersianDigits().let {
                if (designCode.isNotBlank()) designCode.toPersianDigits() else it
            },
            color = BoardDim,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(2.4f)
        )
        Text(
            row.design,
            color = BoardText,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(3f)
        )
        Text(
            row.qty.fa(),
            color = BoardText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            statusText(row),
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(2.4f)
        )
    }
}

/** ستونِ وضعیت — همان چیزی که روی تابلوی فرودگاه «تأخیر» را نشان می‌دهد. */
private fun statusText(row: BoardRow): String {
    // در متغیرِ محلی گرفته می‌شود چون `BoardRow` حالا در :core است و
    // کاتلین ویژگیِ عمومیِ ماژولِ دیگر را smart-cast نمی‌کند.
    val due = row.dueIn
    return when {
        due != null && due < 0 -> "${(-due).fa()} روز تأخیر"
        due == 0 -> "مهلت امروز"
        due != null && due <= 2 -> "${due.fa()} روز مانده"
        row.days >= 3 -> "${row.days.fa()} روز زیرِ دست"
        else -> "${row.days.fa()} روز"
    }
}
