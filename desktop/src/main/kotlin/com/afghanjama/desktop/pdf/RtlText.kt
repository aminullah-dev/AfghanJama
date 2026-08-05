package com.afghanjama.desktop.pdf

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedString

/**
 * چیدنِ یک سطرِ فارسی برای کاغذ — **یک تعریف، برای هم اندازه‌گیری و هم
 * رسم**.
 *
 * دو اشکالِ واقعی این فایل را لازم کرد و هر دو از یک ریشه بودند:
 * اندازه‌گیر و رسم‌کننده متن را جور دیگری می‌چیدند.
 *
 * **۱. جهتِ پایه.** `AwtTextMeasurer.wrap` صریح `RUN_DIRECTION_RTL`
 * می‌داد ولی `SheetPdf.drawText` از سازندهٔ `TextLayout(String, …)`
 * استفاده می‌کرد که جهت را از اولین حرفِ جهت‌دار **حدس** می‌زند. سطری
 * مثلِ `AFN 1200 برای علی` با یک جهت شکسته و با جهتِ دیگری کشیده می‌شد
 * (روی همین رشته اندازه گرفته شد و واقعاً فرق داشت).
 *
 * **۲. نویسه‌هایی که قلم ندارد.** وزیرمتنِ همراه **نشانهٔ افغانی (؋،
 * U+060B) را ندارد** — با `canDisplay` سنجیده شد، نه حدس. یعنی روی هر
 * سندِ پول به‌جای آن یک مربعِ خالی چاپ می‌شد و تا اولین چاپ کسی
 * نمی‌فهمید.
 *
 * جاوا برای قلمِ **فیزیکی** جایگزین نمی‌آورد؛ فقط قلم‌های منطقی
 * (`SansSerif` و…) ترکیبی‌اند و روی این ماشین ؋ را دارند. پس هر بازه‌ای
 * که وزیرمتن نمی‌تواند نشان دهد، به قلمِ منطقی سپرده می‌شود و بقیهٔ
 * سطر وزیرمتن می‌ماند.
 */
internal object RtlText {

    val FRC = FontRenderContext(null, true, true)

    /**
     * متنِ آماده برای چیدن: جهتِ راست‌به‌چپِ صریح، و قلمِ جایگزین برای
     * نویسه‌هایی که قلمِ اصلی ندارد.
     */
    fun attributed(text: String, font: Font): AttributedString {
        val attr = AttributedString(text)
        attr.addAttribute(TextAttribute.FONT, font)
        attr.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL)

        // بازه‌های پشتِ‌سرهمی که قلمِ اصلی نمی‌تواند نشان دهد.
        var i = 0
        while (i < text.length) {
            if (font.canDisplay(text[i])) {
                i++
                continue
            }
            var j = i
            while (j < text.length && !font.canDisplay(text[j])) j++
            attr.addAttribute(TextAttribute.FONT, fallback(font), i, j)
            i = j
        }
        return attr
    }

    fun layout(text: String, font: Font): TextLayout =
        TextLayout(attributed(text, font).iterator, FRC)

    /**
     * قلمِ جایگزین — **منطقی**، نه فیزیکی.
     *
     * `Font.SANS_SERIF` در جاوا به یک قلمِ ترکیبی نگاشت می‌شود که خودش
     * بینِ قلم‌های سیستم می‌گردد. قلمِ فیزیکی (مثلِ خودِ وزیرمتن) این کار
     * را نمی‌کند. اندازه و ضخامت از قلمِ اصلی می‌آید تا سطر یک‌دست بماند.
     */
    private fun fallback(font: Font): Font =
        Font(Font.SANS_SERIF, font.style, font.size).deriveFont(font.size2D)
}
