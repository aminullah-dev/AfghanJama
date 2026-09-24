package com.afghanjama.ios

import com.afghanjama.platform.SystemActions
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

/**
 * [SystemActions] روی آیفون — اشتراکِ متن و تماس.
 *
 * **چرا لازم شد.** `IosApp` این را فراهم نمی‌کرد، و `LocalSystemActions`
 * بی فراهم‌کننده خطا می‌دهد. یعنی هر صفحه‌ای که همان اولِ کار
 * `LocalSystemActions.current` را می‌خواند — حضور، مشتریان، دفتر کل، و
 * نُه صفحهٔ دیگر — روی آیفون سرِ باز شدن می‌ترکید. کامپایل و پیوندِ
 * framework (تنها چیزی که CIِ آیفون می‌سنجد) این را نمی‌بیند، چون
 * خطا فقط سرِ اجرا پیش می‌آید.
 */
object IosSystemActions : SystemActions {

    override fun shareText(title: String, text: String) {
        val top = topController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // روی آیپد پنجرهٔ اشتراک بی لنگر نمی‌تواند باز شود و اپ می‌ترکد.
        sheet.popoverPresentationController?.sourceView = top.view
        top.presentViewController(sheet, animated = true, completion = null)
    }

    override fun dial(phone: String) {
        val digits = phone.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return
        val url = NSURL.URLWithString("tel:$digits") ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    }

    /** بالاترین صفحهٔ باز — پنجرهٔ اشتراک باید روی همان بنشیند، نه زیرِ آن. */
    private fun topController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) {
            top = top.presentedViewController ?: break
        }
        return top
    }
}
