package com.afghanjama.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.afghanjama.util.ShareUtil

/**
 * [SystemActions] روی اندروید — همان `Intent`هایی که تا دیروز در پنج
 * صفحه تکرار شده بودند، حالا یک جا.
 *
 * این کلاس با `applicationContext` ساخته می‌شود، نه با Activity، پس
 * `FLAG_ACTIVITY_NEW_TASK` لازم دارد. آن پرچم دیگر اینجا دستی گذاشته
 * نمی‌شود: [ShareUtil.launch] خودش تشخیص می‌دهد Contextی که گرفته از
 * Activity آمده یا نه.
 *
 * **چرا از دستی گذاشتن برگشتیم.** همین کلاس پرچم را می‌گذاشت و درست
 * کار می‌کرد، ولی `ShareUtil.shareFile` — که مسیرِ اشتراکِ فایل است و
 * از اینجا رد نمی‌شود — نمی‌گذاشت. نتیجه‌اش کرشِ «اشتراکِ گزارشِ مالی»
 * بود. یک قاعده که در دو جا جدا پیاده شود، دیر یا زود در یکی‌شان
 * فراموش می‌شود.
 */
class AndroidSystemActions(private val ctx: Context) : SystemActions {

    override fun shareText(title: String, text: String) {
        ShareUtil.shareText(ctx, text = text, chooserTitle = title)
    }

    override fun dial(phone: String) {
        // گوشی‌ای که برنامهٔ تماس ندارد (تبلت) وگرنه اپ را می‌بست؛
        // `launch` خودش استثنا را می‌گیرد و `false` می‌دهد.
        ShareUtil.launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }
}
