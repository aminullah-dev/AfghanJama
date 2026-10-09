package com.afghanjama.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * پنجرهٔ «بازکردن» برای فایلِ لایسنس (`.lnmlic`) — همان پنجرهٔ خودِ
 * ویندوز یا مک.
 *
 * `FileDialog`ِ AWT است نه `JFileChooser`: روی مک پنجرهٔ بومیِ Finder را
 * باز می‌کند و روی ویندوز پنجرهٔ آشنای Explorer را؛ کارفرما نباید با
 * پنجره‌ای غریبه دنبالِ فایلی بگردد که تازه در واتساپ گرفته.
 *
 * فیلترِ پسوند فقط پیشنهاد است (روی ویندوز اصلاً کار نمی‌کند)؛ محتوا به
 * هر حال سنجیده می‌شود و فایلِ نادرست پیامِ روشن می‌گیرد.
 *
 * @return متنِ فایل، یا `null` اگر انتخاب نشد یا خوانده نشد
 */
fun pickLicenceFile(): String? = runCatching {
    val dialog = FileDialog(null as Frame?, "فایلِ لایسنس", FileDialog.LOAD).apply {
        file = "*.lnmlic"
        setFilenameFilter { _, name -> name.endsWith(".lnmlic", ignoreCase = true) || name.endsWith(".txt", ignoreCase = true) }
        isVisible = true
    }
    val name = dialog.file ?: return@runCatching null
    val f = File(dialog.directory, name)
    // کلید چند صد بایت است؛ فایلِ بزرگ یعنی فایلِ اشتباه.
    if (f.length() > 64 * 1024) return@runCatching null
    f.readText()
}.getOrNull()
