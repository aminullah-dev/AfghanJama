package com.afghanjama.desktop

import com.afghanjama.ui.theme.PillShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * خبرهای کوتاهِ پایینِ پنجره — «نشانی کپی شد»، «پشتیبان ساخته شد».
 *
 * **چرا یک شیءِ سراسری.** بعضی از این خبرها از جایی می‌آیند که درختِ
 * Compose را نمی‌بیند (کلاس‌های سکو، کارِ پس‌زمینه). اگر هرکدام خودش راهی
 * برای گفتن می‌ساخت، کارِ بی‌صدا همان‌جا می‌ماند — مثلِ کپیِ شماره در
 * کلیپ‌بورد که تا ۱.۹ هیچ نشانی نداشت.
 *
 * خبرِ عادی چند ثانیه می‌ماند و خودش می‌رود؛ [Notice.sticky] تا کاربر
 * رویش بزند می‌ماند (یادآورها).
 *
 * **شماره و مسیر جدا، و چپ‌به‌راست ([Notice.ltr]).** «+۹۳ ۷۰۰ …» یا
 * «C:\Users\…» وسطِ جملهٔ دری با الگوریتمِ دوسویه جابه‌جا می‌شود: «+»
 * به آن سرِ شماره می‌رود و تکه‌های مسیر برعکس چیده می‌شوند. پس همان کاری
 * که صفحه‌ها با مسیر می‌کنند (متنِ جدا با `TextDirection.Ltr`) اینجا هم
 * هست.
 */
internal object DeskNotices {

    data class Notice(val id: Long, val text: String, val sticky: Boolean, val ltr: String? = null)

    private val ids = AtomicLong()
    private val _items = MutableStateFlow<List<Notice>>(emptyList())
    val items: StateFlow<List<Notice>> = _items.asStateFlow()

    /** [ltr] — شماره یا مسیری که پس از [text] و چپ‌به‌راست نشان داده می‌شود. */
    fun show(text: String, sticky: Boolean = false, ltr: String? = null) {
        val n = Notice(ids.incrementAndGet(), text, sticky, ltr?.takeIf { it.isNotBlank() })
        // بیش از سه خبر با هم خوانده نمی‌شود. اول قدیمی‌ترینِ گذرا می‌رود؛
        // یادآورِ ماندگار فقط وقتی کنار می‌رود که همه ماندگار باشند.
        _items.update { list ->
            val all = (list + n).toMutableList()
            while (all.size > MAX) {
                val i = all.indexOfFirst { !it.sticky }.takeIf { it >= 0 } ?: 0
                all.removeAt(i)
            }
            all
        }
    }

    fun dismiss(id: Long) = _items.update { list -> list.filterNot { it.id == id } }

    /** همهٔ یادآورهای ماندگار — «خواندم»ِ سینی. */
    fun dismissSticky() = _items.update { list -> list.filterNot { it.sticky } }

    private const val MAX = 3
    const val SHOW_MS = 4_500L
}

/** جای خبرها — پایینِ محتوا، قرصِ شیشه‌ای. کلیک می‌بندد. */
@Composable
internal fun NoticeHost(modifier: Modifier = Modifier) {
    val items by DeskNotices.items.collectAsState()
    Column(
        modifier.padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items.forEach { n ->
            if (!n.sticky) {
                LaunchedEffect(n.id) {
                    delay(DeskNotices.SHOW_MS)
                    DeskNotices.dismiss(n.id)
                }
            }
            Row(
                Modifier
                    .widthIn(max = 560.dp)
                    .deskGlass(PillShape, elevation = 10.dp)
                    .clickable { DeskNotices.dismiss(n.id) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    n.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (n.sticky) FontWeight.SemiBold else FontWeight.Normal,
                    color = Ink,
                    modifier = Modifier.weight(1f, fill = false)
                )
                n.ltr?.let { tail ->
                    Text(
                        tail,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                        color = Ink,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
