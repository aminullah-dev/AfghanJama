package com.afghanjama.platform

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * [SystemActions] روی اندروید — همان `Intent`هایی که تا دیروز در پنج
 * صفحه تکرار شده بودند، حالا یک جا.
 *
 * `FLAG_ACTIVITY_NEW_TASK` لازم است چون این کلاس با `applicationContext`
 * ساخته می‌شود، نه با Activity. بدونش اندروید سرِ اجرا اعتراض می‌کند —
 * و چون فقط لحظهٔ زدنِ دکمه پیش می‌آید، در آزمونِ ساخت دیده نمی‌شود.
 */
class AndroidSystemActions(private val ctx: Context) : SystemActions {

    override fun shareText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(
            Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun dial(phone: String) {
        // `runCatching` چون گوشی‌ای که برنامهٔ تماس ندارد (تبلت) وگرنه
        // اپ را می‌بندد. رفتارِ قبلی در `DeliveryQueueScreen` هم همین بود.
        runCatching {
            ctx.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
