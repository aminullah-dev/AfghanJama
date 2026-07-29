package com.afghanjama.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * پشتیبانِ کامل: دیتابیس + عکس‌ها در یک فایل.
 *
 * تا امروز فقط فایلِ خامِ دیتابیس کپی می‌شد و عکس‌ها جا می‌ماندند؛ یعنی
 * بعد از عوض‌کردنِ گوشی، سطرِ عکس برمی‌گشت ولی خودِ عکس نه.
 *
 * قالب یک zip ساده است — `java.util.zip` در خودِ JDK هست و هیچ وابستگیِ
 * تازه‌ای اضافه نمی‌کند، مثلِ بقیهٔ اپ.
 *
 * ```
 * ├── database      ← afghanjama.db
 * ├── meta.txt      ← تاریخ و تعداد عکس، برای خواندنِ آدمیزاد
 * └── photos/…      ← محتویاتِ پوشهٔ عکس‌ها
 * ```
 */
object BackupArchive {

    const val ENTRY_DB = "database"
    const val ENTRY_META = "meta.txt"
    const val PHOTO_PREFIX = "photos/"

    /** قالبی که یک فایلِ پشتیبان دارد. */
    enum class Format { ZIP, RAW_DB, UNKNOWN }

    /**
     * تشخیصِ قالب از روی چند بایتِ اول.
     *
     * پشتیبان‌های قدیمی فایلِ خامِ SQLite بودند و باید تا همیشه قابلِ
     * بازیابی بمانند — کاربر ممکن است پشتیبانِ هفتهٔ پیش را داشته باشد.
     */
    fun detect(head: ByteArray): Format = when {
        head.size >= 4 &&
            head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            head[2] == 3.toByte() && head[3] == 4.toByte() -> Format.ZIP

        head.size >= 15 &&
            String(head, 0, 15, Charsets.US_ASCII) == "SQLite format 3" -> Format.RAW_DB

        else -> Format.UNKNOWN
    }

    /** چند بایتِ اول برای تشخیصِ قالب. */
    const val HEAD_BYTES = 16

    /**
     * نامِ امنِ یک عکس از روی نامِ ورودیِ zip، یا null اگر نباید بازش کرد.
     *
     * فایلِ پشتیبان ممکن است از هر جایی آمده باشد. ورودی‌ای با `../` در
     * نامش می‌تواند بیرون از پوشهٔ اپ بنویسد (همان Zip Slip). اینجا فقط
     * نامِ برهنهٔ فایل پذیرفته می‌شود و هر چیزِ دیگری رد.
     */
    fun safePhotoName(entryName: String): String? {
        if (!entryName.startsWith(PHOTO_PREFIX)) return null
        val raw = entryName.removePrefix(PHOTO_PREFIX)
        if (raw.isBlank()) return null
        // نه مسیر، نه بالا رفتن، نه نامِ مطلق
        if (raw.contains('/') || raw.contains('\\')) return null
        if (raw == "." || raw == "..") return null
        return raw
    }

    // ------------------------------------------------------------------
    // نوشتن
    // ------------------------------------------------------------------

    /**
     * بسته‌بندیِ دیتابیس و عکس‌ها در [out].
     *
     * @return تعدادِ عکسی که واقعاً نوشته شد.
     */
    fun write(dbFile: File, photoDir: File?, out: OutputStream): Int {
        var photos = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_DB))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            val files = photoDir?.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                .orEmpty()
            files.forEach { f ->
                zip.putNextEntry(ZipEntry(PHOTO_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                photos++
            }

            zip.putNextEntry(ZipEntry(ENTRY_META))
            zip.write(
                buildString {
                    appendLine("afghanjama-backup")
                    appendLine("at=${System.currentTimeMillis()}")
                    appendLine("photos=$photos")
                }.toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()
        }
        return photos
    }

    // ------------------------------------------------------------------
    // خواندن
    // ------------------------------------------------------------------

    /** نتیجهٔ بازکردنِ یک پشتیبان. */
    data class Extracted(val dbWritten: Boolean, val photos: Int)

    /**
     * بازکردنِ zip: دیتابیس در [dbTarget] و عکس‌ها در [photoTarget].
     *
     * [dbTarget] باید فایلِ موقت باشد نه دیتابیسِ زنده — اگر وسطِ کار قطع
     * شود، نباید دادهٔ سالمِ کارگاه نصفه‌کاره بماند. جابه‌جاییِ نهایی کارِ
     * صداکننده است.
     *
     * عکس‌ها **قبل از** دیتابیس روی دیسک می‌نشینند تا اگر کار نیمه‌تمام
     * ماند، فایلِ اضافه بماند نه سطرِ بی‌عکس؛ جاروی یتیم‌ها خودش تمیزشان
     * می‌کند.
     */
    fun extract(input: InputStream, dbTarget: File, photoTarget: File): Extracted {
        var dbWritten = false
        var photos = 0
        photoTarget.mkdirs()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                when {
                    entry.name == ENTRY_DB -> {
                        dbTarget.outputStream().use { zip.copyTo(it) }
                        dbWritten = true
                    }
                    else -> safePhotoName(entry.name)?.let { name ->
                        File(photoTarget, name).outputStream().use { zip.copyTo(it) }
                        photos++
                    }
                }
                zip.closeEntry()
            }
        }
        return Extracted(dbWritten, photos)
    }
}
