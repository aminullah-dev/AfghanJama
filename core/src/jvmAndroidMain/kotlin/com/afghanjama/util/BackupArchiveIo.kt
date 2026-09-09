package com.afghanjama.util

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * نوشتن و خواندنِ فایلِ پشتیبان — **نیمهٔ JVMِ [BackupArchive].**
 *
 * چرا جدا شد: `:core` حالا برای iOS هم کامپایل می‌شود و آنجا نه
 * `java.io.File` هست نه `java.util.zip`. ولی منطقِ خالصِ همان کلاس —
 * تشخیصِ قالب از بایت‌های جادویی، حکمِ سازگاریِ نسخه، و پالودنِ نامِ
 * عکس — هیچ ربطی به سکو ندارد و همان‌جا در کدِ مشترک ماند، جایی که
 * خودآزمایی صدایش می‌زند.
 *
 * پس این فایل فقط دو تابع دارد و هر دو با فایل و جریانِ بایت کار
 * می‌کنند. سهمِ iOS از این دو، وقتی نوبتش شد، کنارِ همین می‌نشیند.
 */
object BackupArchiveIo {

    /**
     * بسته‌بندیِ دیتابیس و عکس‌ها در [out].
     *
     * @return تعدادِ عکسی که واقعاً نوشته شد.
     */
    fun write(dbFile: File, photoDir: File?, out: OutputStream): Int {
        var photos = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupArchive.ENTRY_DB))
            dbFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            val files = photoDir?.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                .orEmpty()
            files.forEach { f ->
                zip.putNextEntry(ZipEntry(BackupArchive.PHOTO_PREFIX + f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                photos++
            }

            zip.putNextEntry(ZipEntry(BackupArchive.ENTRY_META))
            zip.write(
                buildString {
                    // این سطر عمداً با تغییرِ نامِ اپ عوض نشد.
                    //
                    // یک نشانهٔ ثابتِ قالب است، نه متنی که کسی ببیند: در
                    // پشتیبان‌هایی که همین حالا دستِ کارگاه‌هاست نوشته
                    // شده. اگر روزی بخواهیم پیش از بازیابی اعتبارش را
                    // بسنجیم، باید همین رشته باشد وگرنه فایل‌های قدیمی
                    // رد می‌شوند. (امروز اصلاً خوانده نمی‌شود؛ تشخیصِ
                    // قالب از بایت‌های جادوییِ ZIP/SQLite می‌آید.)
                    appendLine("afghanjama-backup")
                    appendLine("at=${nowMillis()}")
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
                    entry.name == BackupArchive.ENTRY_DB -> {
                        dbTarget.outputStream().use { zip.copyTo(it) }
                        dbWritten = true
                    }
                    else -> BackupArchive.safePhotoName(entry.name)?.let { name ->
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
