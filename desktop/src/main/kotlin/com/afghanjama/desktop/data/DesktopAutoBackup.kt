package com.afghanjama.desktop.data

import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * پشتیبانِ خودکارِ روزانه روی کامپیوتر.
 *
 * **چرا لازم شد.** تا ۱.۹ پشتیبانِ کامپیوتر فقط دستی بود. هشدارِ «هنوز هیچ
 * بکاپی گرفته نشده» عددش را از `BackupPrefs.lastAuto` می‌خواند، که فقط
 * کارگرِ زمان‌بندیِ اندروید می‌نوشت — پس روی کامپیوتر آن هشدار تا ابد
 * می‌ماند و هیچ کاری هم آن را برطرف نمی‌کرد. کامپیوتری که دفترِ کارگاه
 * رویش است بیش از گوشی به پشتیبانِ خودکار نیاز دارد.
 *
 * **قاعده:**
 * - سرِ باز شدنِ برنامه، اگر از آخرین پشتیبانِ خودکار ۲۴ ساعت گذشته باشد؛
 *   بعد هر ساعت نگاه می‌کند و هر ۲۴ ساعت یکی می‌گیرد، تا وقتی برنامه باز است.
 * - پشتیبانِ کامل، با عکس‌ها، در `backups/` کنارِ دفتر یا پوشه‌ای که
 *   کارفرما انتخاب کرده (iCloud Drive، OneDrive).
 * - هر پشتیبان پس از نوشتن **باز و سنجیده** می‌شود (نسخه و
 *   `quick_check`)؛ کپیِ دفترِ زنده می‌تواند وسطِ یک نوشتن بریده شود و
 *   پشتیبانِ خراب بدتر از نبودنش است — آدم به آن تکیه می‌کند.
 * - [KEEP] تای آخر می‌مانند؛ فقط فایل‌های خودکارِ خودش پاک می‌شوند، نه
 *   پشتیبانِ دستی یا «پیش از پاک‌کردن»، و نه هر فایلِ دیگری در پوشه‌ای که
 *   کارفرما داده (iCloud Drive، OneDrive): نام باید **دقیقاً** شکلِ
 *   [fileName] باشد و فایل معمولی باشد، نه میان‌بُر.
 * - **ساعتِ نادرست چیزی را پاک نمی‌کند** ([prune]): پشتیبانی که همین حالا
 *   نوشته شد هرگز پاک نمی‌شود، اگر ساعت از پشتیبان‌های روی دیسک عقب‌تر
 *   باشد هیچ چیز پاک نمی‌شود، و پشتیبانی که تاریخش در آینده است دست
 *   نمی‌خورد. آخرین پشتیبانِ «آینده» هم پشتیبانِ خودکار را نمی‌خواباند
 *   ([isDue]).
 * - هر بارِ موفق `BackupPrefs.setLastAuto` — همان عددی که هشدار و
 *   «تنظیمات» می‌خوانند.
 */
object DesktopAutoBackup {

    /** چند پشتیبانِ خودکار نگه داشته شود. */
    const val KEEP = 14

    /** فاصلهٔ دو پشتیبانِ خودکار. */
    const val EVERY_MS: Long = 24L * 60 * 60 * 1000

    /** هر چند وقت نگاه شود که وقتش رسیده یا نه. */
    private const val CHECK_MS: Long = 60L * 60 * 1000

    private const val PREFIX = "KhayatYar-auto-"
    private const val EXT = ".ajb"
    private const val STAMP = "yyyyMMdd-HHmmss"

    /**
     * فقط همان نامی که [fileName] می‌سازد. «… - Copy.ajb»ِ ویندوز، «… copy.ajb»ِ
     * مک، `.part` و هر فایلِ دیگری که فقط با همان پیشوند شروع شود، مالِ
     * کاربر است.
     */
    private val AUTO_NAME = Regex("""KhayatYar-auto-\d{8}-\d{6}\.ajb""")

    /** نتیجهٔ یک بار. */
    data class Run(
        val file: File,
        val photos: Int,
        val at: Long,
        /** پوشهٔ انتخاب‌شده در دسترس نبود و پیش‌فرض به کار رفت. */
        val usedFallback: Boolean,
        val pruned: Int,
    )

    /** حالی که «پشتیبان و بازیابی» و «تنظیمات» نشان می‌دهند. */
    data class Status(
        val running: Boolean = false,
        val last: Run? = null,
        /** پیامِ آخرین شکست — `null` یعنی آخرین تلاش درست بود یا هنوز نبوده. */
        val error: String? = null,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    fun isAutoFile(f: File): Boolean =
        AUTO_NAME.matches(f.name) && f.isFile && !Files.isSymbolicLink(f.toPath())

    fun fileName(at: Long): String =
        PREFIX + SimpleDateFormat(STAMP, Locale.US).format(Date(at)) + EXT

    /** زمانِ پشتیبان از نامش؛ `null` اگر نام تاریخِ درستی نیست. */
    private fun stampOf(f: File): Long? = runCatching {
        SimpleDateFormat(STAMP, Locale.US).apply { isLenient = false }
            .parse(f.name.removePrefix(PREFIX).removeSuffix(EXT))?.time
    }.getOrNull()

    /**
     * وقتش رسیده؟ صفر یعنی هرگز گرفته نشده — وقتش است.
     *
     * آخرین پشتیبانی که «در آینده» است (ساعتِ کامپیوتر روزی جلو بود و بعد
     * درست شد) هم یعنی وقتش است؛ وگرنه `now - lastAuto` منفی می‌ماند و
     * پشتیبانِ خودکار تا آن تاریخ بی‌صدا می‌خوابید.
     */
    fun isDue(lastAuto: Long, now: Long): Boolean =
        lastAuto <= 0L || now - lastAuto >= EVERY_MS || lastAuto - now > CHECK_MS

    /** پوشه‌ای که امروز در آن نوشته می‌شود. */
    fun folder(settings: Settings): File =
        DeskPrefs.backupFolder(settings).takeIf { it.isNotBlank() }?.let(::File) ?: backupsDir()

    /**
     * فقط [KEEP] تای تازه‌ترِ خودکار می‌مانند. ترتیب از نامِ فایل است (تاریخ
     * در نام)، نه از زمانِ تغییر — کپی کردنِ پوشه زمانِ تغییر را عوض می‌کند.
     *
     * **ساعتِ نادرست.** ترتیبِ نام فقط وقتی معنا دارد که ساعت درست باشد:
     * - پشتیبانی که تاریخش بیش از یک روز از [now] جلوتر است (یا تاریخش خوانده
     *   نمی‌شود) نه شمرده می‌شود نه پاک.
     * - [newest] (پشتیبانی که همین حالا نوشته شد) هرگز پاک نمی‌شود؛ و اگر در
     *   ترتیبِ نام تازه‌ترین نباشد، یعنی ساعت از پشتیبان‌های روی دیسک عقب
     *   رفته — آن‌وقت هیچ چیز پاک نمی‌شود. بی این، کامپیوتری که باتریِ
     *   ساعتش تمام شده هر روز پشتیبانِ تازه را درست پس از نوشتنش پاک می‌کرد
     *   و فقط چهارده پشتیبانِ کهنه می‌ماند.
     *
     * @return شمارِ فایل‌هایی که پاک شدند.
     */
    fun prune(
        dir: File,
        keep: Int = KEEP,
        newest: File? = null,
        now: Long = System.currentTimeMillis(),
    ): Int {
        val placed = dir.listFiles()?.filter(::isAutoFile).orEmpty()
            .filter { f -> stampOf(f)?.let { it <= now + EVERY_MS } ?: false }
            .sortedByDescending { it.name }
        if (newest != null && placed.firstOrNull()?.name != newest.name) return 0
        var n = 0
        placed.drop(keep.coerceAtLeast(1)).forEach { if (it.name != newest?.name && it.delete()) n++ }
        return n
    }

    /**
     * یک پشتیبانِ خودکار، اگر [force] یا وقتش رسیده باشد.
     *
     * [backup] نوشتنِ پشتیبان در یک فایل است و شمارِ عکس‌ها را برمی‌گرداند؛
     * برنامه [DesktopBackup.backupWithPhotos] را می‌دهد و آزمون نسخهٔ خودش
     * را، تا دفترِ واقعی هرگز در آزمون باز نشود.
     *
     * @return `null` اگر وقتش نبود.
     */
    fun runIfDue(
        settings: Settings,
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
        fallbackDir: File = backupsDir(),
        backup: (File) -> Result<Int> = DesktopBackup::backupWithPhotos,
    ): Result<Run>? {
        if (!force && !isDue(BackupPrefs.lastAuto(settings), now)) return null
        _status.value = _status.value.copy(running = true)
        val result = runCatching {
            val chosen = DeskPrefs.backupFolder(settings).takeIf { it.isNotBlank() }?.let(::File)
            val first = chosen ?: fallbackDir
            val attempt = writeVerified(first, now, backup)
            val (written, fellBack) = if (attempt.isSuccess || chosen == null) {
                attempt.getOrThrow() to false
            } else {
                // پوشهٔ انتخاب‌شده (مثلاً iCloud) در دسترس نیست. پشتیبان نباید به
                // این خاطر نیفتد؛ در پیش‌فرض نوشته می‌شود و این گفته می‌شود.
                writeVerified(fallbackDir, now, backup).getOrThrow() to true
            }
            val (file, photos) = written
            val pruned = prune(file.parentFile, newest = file, now = now)
            BackupPrefs.setLastAuto(settings, now)
            Run(file, photos, now, fellBack, pruned)
        }
        _status.value = Status(
            running = false,
            last = result.getOrNull() ?: _status.value.last,
            error = result.exceptionOrNull()?.let { it.message ?: it::class.simpleName ?: "خطای ناشناخته" },
        )
        return result
    }

    /**
     * نوشتن و سنجیدن. اگر کپی بریده درآمد، یک بار دیگر؛ اگر باز هم، شکست.
     */
    private fun writeVerified(
        dir: File,
        now: Long,
        backup: (File) -> Result<Int>,
    ): Result<Pair<File, Int>> = runCatching {
        if (!dir.exists() && !dir.mkdirs()) error("پوشهٔ پشتیبان ساخته نشد: ${dir.absolutePath}")
        if (!dir.isDirectory || !dir.canWrite()) error("در پوشهٔ پشتیبان نمی‌شود نوشت: ${dir.absolutePath}")
        val target = File(dir, fileName(now))
        var lastProblem = ""
        repeat(2) {
            val photos = backup(target).getOrThrow()
            val problem = verify(target)
            if (problem == null) return@runCatching target to photos
            lastProblem = problem
            target.delete()
        }
        error("پشتیبانِ خودکار سالم درنیامد: $lastProblem")
    }

    /**
     * پشتیبانی که نوشته شد باز می‌شود؟ همان سنجشِ بازیابی — قالب، نسخه و
     * `quick_check` روی دفترِ داخلش ([DesktopBackup.stageAndValidate]).
     * `null` یعنی سالم.
     */
    fun verify(archive: File): String? {
        val scratch = Files.createTempDirectory("ky-verify").toFile()
        return try {
            DesktopBackup.stageAndValidate(archive, scratch)
            null
        } catch (t: Throwable) {
            t.message ?: "پشتیبان باز نشد"
        } finally {
            scratch.deleteRecursively()
        }
    }

    /**
     * تا وقتی برنامه باز است: یک بار همین حالا، بعد هر ساعت نگاه.
     *
     * [onResult] پس از هر تلاش (نه وقتی وقتش نبود) — پنجره شکست یا افتادن به
     * پوشهٔ پیش‌فرض را با آن می‌گوید؛ شکستِ بی‌صدا همان چیزی است که این
     * پشتیبان برای بستنش آمده.
     */
    suspend fun loop(settings: Settings, onResult: (Result<Run>) -> Unit) {
        while (true) {
            if (!DesktopBackup.ledgerClosed && DesktopLedger.repo().isSuccess) {
                val r = withContext(Dispatchers.IO) { runIfDue(settings) }
                if (r != null) onResult(r)
            }
            delay(CHECK_MS)
        }
    }

    /** تازه‌ترین پشتیبانِ خودکاری که در پوشه هست، اگر باشد. */
    fun latestIn(dir: File): File? =
        dir.listFiles()?.filter(::isAutoFile)?.maxByOrNull { it.name }
}
