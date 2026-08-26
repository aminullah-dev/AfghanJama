package com.afghanjama.desktop.platform

import com.afghanjama.platform.SystemActions
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * [SystemActions] روی ویندوز.
 *
 * پی‌سی نه «اشتراکِ اندرویدی» دارد نه تلفن. نزدیک‌ترین کارِ مفید در هر
 * دو مورد کلیپ‌بورد است: کاربر متن یا شماره را در واتس‌اپِ دسکتاپ یا هر
 * جای دیگری می‌چسبانَد.
 *
 * `Desktop.mail()` هم گزینه بود ولی برداشته شد: کارگاه رسید را با
 * واتس‌اپ می‌فرستد نه ایمیل، و بازکردنِ Outlook به‌جای اشتراک‌گذاری
 * بیشتر گیج می‌کند تا کمک.
 *
 * از AWT استفاده می‌شود نه Compose، چون کلیپ‌بوردِ Compose به یک
 * `CompositionLocal` نیاز دارد و این کلاس بیرونِ درختِ رابط ساخته
 * می‌شود.
 */
class DesktopSystemActions : SystemActions {

    private fun copy(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text), null)
        }
    }

    override fun shareText(title: String, text: String) = copy(text)

    override fun dial(phone: String) = copy(phone)
}
