package com.afghanjama.desktop.data

import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.lan.LanHub
import com.afghanjama.ui.format.fa
import com.afghanjama.util.BackupArchive
import com.afghanjama.util.BackupArchiveIo
import java.io.File

/**
 * پشتیبان‌گیری و بازیابیِ دفتر روی ویندوز.
 *
 * **چرا لازم بود.** تصمیمِ کارفرما این است که «پی‌سی دفترِ حساب می‌شود».
 * ولی تا امروز `grep -i backup desktop/src` هیچ نمی‌داد: ماشینی که قرار
 * است دفترِ کارگاه رویش بنشیند، هیچ راهی برای پشتیبان گرفتن نداشت. یک
 * دیسکِ سوخته یعنی کلِ حسابِ کارگاه.
 *
 * **قالب همان قالبِ گوشی است، و این مهم‌ترین تصمیمِ اینجاست.**
 * `BackupArchive` از قبل در `:core` بود و هیچ ایمپورتِ اندرویدی نداشت —
 * پس همان کد اینجا هم اجرا می‌شود. یعنی پشتیبانِ گوشی روی پی‌سی باز
 * می‌شود و برعکس. همان انتقالی که کلِ نقشهٔ «پی‌سی میزبان شود» رویش
 * سوار است، حالا یک مسیرِ واقعی دارد.
 *
 * **عکس‌ها هم هستند (از ۱.۱۰).** تا ۱.۹ اینجا `photoDir = null` بود:
 * پشتیبانِ کامپیوتر عکسِ سفارش‌ها و لوگو را نداشت، و پشتیبانِ گوشی که
 * عکس داشت روی کامپیوتر باز می‌شد و عکس‌هایش دور ریخته می‌شد — در حالی که
 * کامپیوتر از اول عکس‌ها را در `order_photos` کنارِ دفتر نگه می‌داشت.
 * حالا هر دو سو عکس دارند، پس «انتقالِ دفتر از گوشی به کامپیوتر» چیزی را
 * جا نمی‌گذارد.
 */
object DesktopBackup {

    /**
     * دفتر پس از بازیابی بسته است تا برنامه دوباره باز شود. پشتیبانِ خودکار
     * در این فاصله کاری نمی‌کند — دفتری برای پشتیبان‌گیری باز نیست.
     */
    @Volatile
    var ledgerClosed: Boolean = false
        private set

    /** نامِ پیشنهادی؛ تاریخ در نام است تا پشتیبان‌ها روی هم نیفتند. */
    fun suggestedName(stamp: String): String = "KhayatYar-backup-$stamp.ajb"

    /**
     * پشتیبان‌گیری: WAL یکپارچه می‌شود و بعد فایل نوشته.
     *
     * بی `checkpoint` آخرین نوشته‌ها هنوز در `-wal`اند و پشتیبان ناقص
     * می‌شود — دقیقاً همان چند فاکتوری که تازه ثبت شده‌اند.
     */
    fun backupTo(target: File): Result<Unit> = backupWithPhotos(target).map { }

    /** همان، با شمارِ عکس‌هایی که در پشتیبان رفت. */
    fun backupWithPhotos(target: File): Result<Int> = runCatching {
        if (ledgerClosed) error("دفتر پس از بازیابی بسته است؛ برنامه را ببندید و دوباره باز کنید.")
        val repo = DesktopLedger.repo().getOrThrow()
        repo.checkpoint()
        writeBackup(databaseFile(), target, photosDir())
    }

    /**
     * همان کار، ولی روی فایلی که به آن داده می‌شود.
     *
     * جدا شده تا **آزمودنی** باشد: `backupTo` به دفترِ واقعیِ کارگاه
     * گره خورده و آزمونی که آن را صدا بزند روی دادهٔ واقعی کار می‌کند.
     * ریسکِ این کد همه در همین‌جاست، پس همین‌جا باید آزموده شود.
     */
    internal fun writeBackup(db: File, target: File, photoDir: File? = null): Int {
        if (!db.exists() || db.length() <= 0L) error("دفتری برای پشتیبان‌گیری نیست.")
        // اول در فایلِ کنار، بعد جابه‌جا: پشتیبانِ نیمه‌نوشته (دیسکِ پر، برق)
        // نباید با نامِ پشتیبانِ سالم روی دیسک بماند.
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        try {
            val photos = part.outputStream().use { out ->
                BackupArchiveIo.write(db, photoDir, out)
            }
            target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            return photos
        } finally {
            part.delete()
        }
    }

    /**
     * بازیابی — با همان احتیاط‌هایی که نسخهٔ اندروید یاد گرفته بود.
     *
     * ترتیب عمدی است و هر بندش یک خرابیِ واقعی را می‌بندد:
     *
     * 1. **اول در فایلِ کنار** باز می‌شود، نه روی دفترِ زنده. اگر وسطِ
     *    کار قطع شود، دادهٔ سالم دست‌نخورده می‌ماند.
     * 2. **قالب از چند بایتِ اول** تشخیص داده می‌شود نه از پسوند —
     *    کاربر ممکن است نامِ فایل را عوض کرده باشد.
     * 3. **نسخه و سلامت پیش از دست زدن به دفترِ زنده سنجیده می‌شوند**
     *    ([stageAndValidate]). پشتیبانِ جلوتر از این نسخه رد می‌شود، و
     *    پشتیبانی که نسخه دارد ولی صفحه‌هایش خراب است (`quick_check`) هم.
     * 4. **عکس‌ها پیش از دفتر می‌نشینند، و هر کدام راهِ برگشت دارد**
     *    ([placePhotos]). اگر یکی ننشیند، عکس‌های قبلی برمی‌گردند و دفتر
     *    اصلاً دست نمی‌خورد. تا ۱.۱۰ عکس‌ها پس از جایگزینیِ دفتر کپی
     *    می‌شدند؛ اگر آنجا می‌شکست، پیام «بازیابی نشد» می‌گفت در حالی که دفتر
     *    عوض شده بود.
     * 5. اتصالِ باز بسته می‌شود و `-wal`/`-shm`ِ قدیمی پاک — وگرنه
     *    صفحه‌های کش‌شدهٔ اتصالِ قبلی روی دیتابیسِ تازه می‌نشیند و خرابش
     *    می‌کند.
     * 6. نسخهٔ قبلی کنار گذاشته می‌شود (`.prev`) و فقط پس از موفقیت پاک
     *    — اگر کپی بشکند (دیسکِ پر)، دفتر برمی‌گردد، و عکس‌ها هم.
     *
     * یعنی بازیابی یا کامل است یا هیچ. پس از موفقیت باید برنامه بسته و باز
     * شود: `DesktopLedger` دفتر را یک بار باز می‌کند و همان را نگه می‌دارد.
     */
    fun restoreFrom(source: File): Result<Int> = runCatching {
        val db = databaseFile()
        restoreInto(source, db, photosDir(), closeLedger = {
            // سرورِ اشتراکِ کارگاه همین دفتر را می‌نویسد؛ پیش از بستنِ دفتر
            // خاموش می‌شود تا درخواستِ گوشیِ کارگری وسطِ جایگزینی ننشیند.
            LanHub.shared.stop()
            val repo = DesktopLedger.repo().getOrThrow()
            repo.checkpoint()
            repo.closeDatabase()
            ledgerClosed = true
            File(db.path + "-wal").delete()
            File(db.path + "-shm").delete()
        })
    }

    /**
     * کارِ [restoreFrom] روی فایل‌هایی که به آن داده می‌شود — جدا تا
     * آزمودنی باشد، همان دلیلِ [writeBackup].
     *
     * [closeLedger] فقط وقتی صدا زده می‌شود که پشتیبان سنجیده و عکس‌هایش
     * نشسته باشند؛ هر شکستِ پیش از آن دفترِ باز را دست نمی‌زند.
     *
     * @return شمارِ عکس‌هایی که برگشتند.
     */
    internal fun restoreInto(
        source: File,
        db: File,
        photoDir: File,
        closeLedger: () -> Unit,
        swap: (staged: File, db: File) -> Unit = ::swapIn,
    ): Int {
        val dir = db.parentFile
        val staged = stageAndValidate(source, dir)

        // نامِ تازه برای هر بار: اگر بازیابیِ پیشینی عکسی را برنگردانده بود،
        // تنها نسخهٔ آن عکس در پوشهٔ برگشتِ همان بار است و نباید پاک شود.
        val undo = File(dir, "$UNDO_DIR-${System.currentTimeMillis()}")
        val photos = try {
            placePhotos(stagedPhotos(dir), photoDir, undo)
        } catch (e: PhotosNotPlaced) {
            staged.delete()
            throw IllegalStateException(
                "عکس‌های پشتیبان سرِ جایشان ننشستند (${e.message}). " +
                    if (e.notBack == 0) {
                        "دفتر و عکس‌های این کامپیوتر دست نخوردند."
                    } else {
                        "دفتر دست نخورد، ولی ${e.notBack.fa()} عکسِ این کامپیوتر به حالِ قبل " +
                            "برنگشت؛ نسخهٔ قبلی‌شان در پوشهٔ «${undo.name}» کنارِ دفتر است."
                    },
                e
            )
        }

        try {
            closeLedger()
            swap(staged, db)
        } catch (t: Throwable) {
            staged.delete()
            val notBack = photos.rollback()
            val ledger = if (t is LedgerNotRestored) {
                "دفتر جایگزین نشد (${t.message}) و دفترِ قبلی هم برنگشت؛ نسخهٔ قبلی‌اش " +
                    "«${t.previous.name}» کنارِ دفتر است. پیش از هر کاری با پشتیبانی تماس بگیرید"
            } else {
                "دفتر جایگزین نشد (${t.message}) و دفترِ قبلی سرِ جایش ماند"
            }
            val pictures = when {
                photos.count == 0 -> "."
                notBack == 0 -> "؛ عکس‌ها هم به حالِ قبل برگشتند."
                else -> "؛ ولی ${notBack.fa()} عکس به حالِ قبل برنگشت و نسخهٔ قبلی‌شان در " +
                    "پوشهٔ «${undo.name}» کنارِ دفتر است."
            }
            throw IllegalStateException(ledger + pictures, t)
        }
        photos.commit()
        return photos.count
    }

    /** پوشه‌ای که عکس‌های پشتیبان پیش از سنجش در آن باز می‌شوند. */
    internal fun stagedPhotos(dir: File): File = File(dir, "restore-photos")

    /** پیشوندِ پوشهٔ عکس‌های قبلیِ هم‌نام، تا بازیابی تمام شود — راهِ برگشتِ [placePhotos]. */
    internal const val UNDO_DIR = "restore-photos-undo"

    /**
     * عکس‌هایی که [placePhotos] نشاند، با راهِ برگشت.
     *
     * عکسِ هم‌نامِ کامپیوتر جایش را به عکسِ پشتیبان می‌دهد و بقیه می‌مانند
     * (همان کارِ گوشی)؛ ولی عکسِ قبلی تا پایانِ بازیابی در [undo] می‌ماند.
     */
    internal class PlacedPhotos(private val to: File, private val undo: File) {
        /** نام‌هایی که در [to] نوشته شد (یا نوشتنش شروع شد). */
        private val written = mutableListOf<String>()

        /** از [written]، آن‌هایی که نسخهٔ قبلی‌شان در [undo] است. */
        private val saved = mutableSetOf<String>()

        val count: Int get() = written.size

        /** نسخهٔ قبلیِ عکسِ هم‌نام به [undo] می‌رود — جابه‌جا، نه کپی، تا جای دیسک دو برابر نشود. */
        fun keepOld(name: String) {
            val target = File(to, name)
            if (!target.exists()) return
            if (!undo.isDirectory && !undo.mkdirs()) error("پوشهٔ برگشتِ عکس‌ها ساخته نشد")
            val keep = File(undo, name)
            if (!target.renameTo(keep)) {
                target.copyTo(keep, overwrite = true)
            }
            saved += name
        }

        fun markWritten(name: String) {
            written += name
        }

        /** همه چیز به حالِ پیش از [placePhotos]. @return شمارِ عکس‌هایی که برنگشتند. */
        fun rollback(): Int {
            var failed = 0
            written.forEach { name ->
                val target = File(to, name)
                val back = runCatching {
                    if (name in saved) {
                        val keep = File(undo, name)
                        target.delete()
                        if (!keep.renameTo(target)) {
                            keep.copyTo(target, overwrite = true)
                            keep.delete()
                        }
                    } else if (target.exists() && !target.delete()) {
                        error("پاک نشد")
                    }
                }.isSuccess
                if (!back) failed++
            }
            // اگر چیزی برنگشت، پوشهٔ برگشت می‌ماند — تنها نسخهٔ آن عکس‌ها.
            if (failed == 0) undo.deleteRecursively()
            return failed
        }

        /** بازیابی تمام شد؛ نسخه‌های قبلی دیگر لازم نیستند. */
        fun commit() {
            undo.deleteRecursively()
        }
    }

    /** جایگزینیِ دفتر شکست و دفترِ قبلی هم برنگشت؛ نسخهٔ قبلی در [previous] مانده. */
    internal class LedgerNotRestored(val previous: File, cause: Throwable) : Exception(cause.message, cause)

    /** عکس‌ها ننشستند. [notBack] شمارِ عکس‌های قبلی که به جایشان برنگشتند. */
    internal class PhotosNotPlaced(val notBack: Int, cause: Throwable) : Exception(cause.message, cause)

    /**
     * عکس‌های بازشده را سرِ جایشان می‌گذارد و پوشهٔ موقت را پاک می‌کند —
     * **پیش از** جایگزینیِ دفتر. اگر یکی ننشیند، همه برمی‌گردند و
     * [PhotosNotPlaced] بالا می‌رود.
     *
     * [copy] فقط برای آزمون عوض می‌شود — تا شکستِ وسطِ کار (دیسکِ پر پس از
     * چند عکس) سنجیدنی باشد.
     */
    internal fun placePhotos(
        from: File,
        to: File,
        undo: File,
        copy: (src: File, dst: File) -> Unit = { src, dst -> src.copyTo(dst, overwrite = true) },
    ): PlacedPhotos {
        val placed = PlacedPhotos(to, undo)
        try {
            val files = from.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
            if (files.isNotEmpty() && !to.isDirectory && !to.mkdirs()) {
                error("پوشهٔ عکس‌ها ساخته نشد: ${to.absolutePath}")
            }
            files.forEach { f ->
                placed.keepOld(f.name)
                placed.markWritten(f.name)
                copy(f, File(to, f.name))
            }
        } catch (t: Throwable) {
            throw PhotosNotPlaced(placed.rollback(), t)
        } finally {
            from.deleteRecursively()
        }
        return placed
    }

    /**
     * فایلِ پشتیبان را کنارِ دفتر باز و **سنجیده** می‌کند؛ دفترِ زنده
     * دست نمی‌خورد.
     *
     * جدا از `restoreFrom` است تا آزمودنی باشد — همهٔ تصمیم‌های خطرناک
     * (قالب، خرابی، نسخهٔ جلوتر، نسخهٔ عقب‌تر) اینجایند.
     */
    internal fun stageAndValidate(source: File, dir: File): File {
        val staged = File(dir, "$DB_NAME.restore")
        staged.delete()

        if (!source.exists() || source.length() <= 0L) error("فایلِ پشتیبان خالی است.")

        val head = source.inputStream().use { input ->
            ByteArray(BackupArchive.HEAD_BYTES).let { buf ->
                val n = input.read(buf).coerceAtLeast(0)
                buf.copyOf(n)
            }
        }
        val format = BackupArchive.detect(head)
        if (format == BackupArchive.Format.UNKNOWN) {
            error("این فایل پشتیبانِ خیاط‌یار نیست.")
        }

        // عکس‌ها اول در پوشهٔ موقت باز می‌شوند و فقط وقتی دفتر سنجیده و
        // جایگزین شد سرِ جایشان می‌روند ([adoptPhotos]). اگر پشتیبان رد شود،
        // پوشهٔ موقت پاک می‌شود و عکس‌های کامپیوتر دست نمی‌خورند.
        val photoStage = stagedPhotos(dir)
        photoStage.deleteRecursively()
        try {
            source.inputStream().use { input ->
                if (format == BackupArchive.Format.ZIP) {
                    val res = BackupArchiveIo.extract(input, staged, photoStage)
                    if (!res.dbWritten) error("فایلِ پشتیبان دیتابیس ندارد.")
                } else {
                    staged.outputStream().use { input.copyTo(it) }
                }
            }
            if (staged.length() <= 0L) error("فایلِ پشتیبان خالی است.")
            validateStaged(staged)
        } catch (t: Throwable) {
            photoStage.deleteRecursively()
            throw t
        }
        return staged
    }

    /**
     * نسخه و سلامتِ فایلِ بازشده — پیش از هر دست‌زدنی به دفترِ زنده.
     *
     * **سلامت هم، نه فقط نسخه.** تا ۱.۱۰ فقط `user_version` خوانده می‌شد،
     * که در سرِ فایل است. پشتیبانی که سرش سالم و صفحه‌هایش خراب بود (کپیِ
     * بریده، دیسکِ خراب) پذیرفته می‌شد و جای دفترِ سالم را می‌گرفت. حالا
     * همان `quick_check`ی اینجاست که پشتیبانِ ایمنیِ «پاک‌کردن» و پشتیبانِ
     * خودکار با آن سنجیده می‌شدند ([ledgerFileHealthy])؛ هر سه از همین
     * یک در می‌گذرند. CRCِ هر ورودیِ zip را خودِ بازکردن
     * (`BackupArchiveIo.extract`) می‌سنجد.
     */
    private fun validateStaged(staged: File) {
        val version = ledgerFileVersion(staged)
        when {
            version == null || version <= 0 || !ledgerFileHealthy(staged) -> {
                staged.delete()
                error(
                    BackupArchive.verdictMessage(
                        BackupArchive.Verdict.CORRUPT, 0, DB_VERSION
                    )
                )
            }

            version > DB_VERSION -> {
                val v = version
                staged.delete()
                error(
                    BackupArchive.verdictMessage(
                        BackupArchive.Verdict.TOO_NEW, v, DB_VERSION
                    )
                )
            }

            version < DESKTOP_BASELINE -> {
                val v = version
                staged.delete()
                error(
                    "این پشتیبان نسخهٔ $v است و نسخهٔ ویندوز از " +
                        "$DESKTOP_BASELINE به بعد را باز می‌کند. اول روی گوشی " +
                        "به‌روزرسانی کنید. دادهٔ فعلی دست‌نخورده ماند."
                )
            }
        }
    }

    /**
     * جایگزینیِ دفتر با فایلِ سنجیده‌شده — با نسخهٔ برگشت.
     *
     * اگر کپی وسطِ کار بشکند (دیسکِ پر)، دفترِ قبلی برمی‌گردد. بی این،
     * نه فایلِ سالم می‌ماند و نه بازیابی تمام می‌شود.
     */
    internal fun swapIn(staged: File, db: File) {
        val dir = db.parentFile
        val previous = File(dir, "$DB_NAME.prev")
        previous.delete()
        if (db.exists() && !db.renameTo(previous)) {
            db.inputStream().use { input -> previous.outputStream().use { input.copyTo(it) } }
        }
        try {
            staged.inputStream().use { input -> db.outputStream().use { input.copyTo(it) } }
        } catch (e: Throwable) {
            // دادهٔ سالم برمی‌گردد؛ بازیابی نشد ولی چیزی از دست نرفت.
            val back = runCatching {
                db.delete()
                previous.inputStream().use { input ->
                    db.outputStream().use { input.copyTo(it) }
                }
            }.isSuccess
            // اگر برنگشت، `.prev` پاک نمی‌شود و پیام همین را می‌گوید.
            if (!back) throw LedgerNotRestored(previous, e)
            throw e
        }
        previous.delete()
        staged.delete()
        Unit
    }
}
