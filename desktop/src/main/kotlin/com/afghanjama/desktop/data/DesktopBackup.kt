package com.afghanjama.desktop.data

import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.util.BackupArchive
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
 * عکس‌ها اینجا نیستند (`photoDir = null`): دوربین و `PhotoStore`
 * اندرویدی‌اند. پشتیبانِ گوشی که عکس دارد روی پی‌سی باز می‌شود و
 * عکس‌هایش کنار گذاشته می‌شوند — دیتابیس کامل می‌آید و همان چیزی است که
 * دفترِ حساب را می‌سازد.
 */
object DesktopBackup {

    /** نامِ پیشنهادی؛ تاریخ در نام است تا پشتیبان‌ها روی هم نیفتند. */
    fun suggestedName(stamp: String): String = "KhayatYar-backup-$stamp.ajb"

    /**
     * پشتیبان‌گیری: WAL یکپارچه می‌شود و بعد فایل نوشته.
     *
     * بی `checkpoint` آخرین نوشته‌ها هنوز در `-wal`اند و پشتیبان ناقص
     * می‌شود — دقیقاً همان چند فاکتوری که تازه ثبت شده‌اند.
     */
    fun backupTo(target: File): Result<Unit> = runCatching {
        val repo = DesktopLedger.repo().getOrThrow()
        repo.checkpoint()
        writeBackup(databaseFile(), target)
    }

    /**
     * همان کار، ولی روی فایلی که به آن داده می‌شود.
     *
     * جدا شده تا **آزمودنی** باشد: `backupTo` به دفترِ واقعیِ کارگاه
     * گره خورده و آزمونی که آن را صدا بزند روی دادهٔ واقعی کار می‌کند.
     * ریسکِ این کد همه در همین‌جاست، پس همین‌جا باید آزموده شود.
     */
    internal fun writeBackup(db: File, target: File) {
        if (!db.exists() || db.length() <= 0L) error("دفتری برای پشتیبان‌گیری نیست.")
        target.outputStream().use { out ->
            BackupArchive.write(db, null, out)
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
     * 3. **نسخه پیش از دست زدن به دفترِ زنده سنجیده می‌شود.** پشتیبانِ
     *    جلوتر از این نسخه رد می‌شود؛ اگر رد نشود Room بعداً نمی‌تواند
     *    بازش کند.
     * 4. اتصالِ باز بسته می‌شود و `-wal`/`-shm`ِ قدیمی پاک — وگرنه
     *    صفحه‌های کش‌شدهٔ اتصالِ قبلی روی دیتابیسِ تازه می‌نشیند و خرابش
     *    می‌کند.
     * 5. نسخهٔ قبلی کنار گذاشته می‌شود (`.prev`) و فقط پس از موفقیت پاک
     *    — اگر کپی بشکند (دیسکِ پر)، دفتر برمی‌گردد.
     *
     * پس از موفقیت باید برنامه بسته و باز شود: `DesktopLedger` دفتر را
     * یک بار باز می‌کند و همان را نگه می‌دارد.
     */
    fun restoreFrom(source: File): Result<Unit> = runCatching {
        val db = databaseFile()
        val staged = stageAndValidate(source, db.parentFile)

        val repo = DesktopLedger.repo().getOrThrow()
        repo.checkpoint()
        repo.closeDatabase()
        File(db.path + "-wal").delete()
        File(db.path + "-shm").delete()

        swapIn(staged, db)
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

        // عکس‌ها روی ویندوز جایی ندارند؛ به پوشهٔ دورانداختنی می‌روند تا
        // `extract` کارش را بکند و بعد پاک می‌شود.
        val scratchPhotos = File(dir, "restore-photos")
        try {
            source.inputStream().use { input ->
                if (format == BackupArchive.Format.ZIP) {
                    val res = BackupArchive.extract(input, staged, scratchPhotos)
                    if (!res.dbWritten) error("فایلِ پشتیبان دیتابیس ندارد.")
                } else {
                    staged.outputStream().use { input.copyTo(it) }
                }
            }
        } finally {
            scratchPhotos.deleteRecursively()
        }

        if (staged.length() <= 0L) error("فایلِ پشتیبان خالی است.")

        // ---- سنجش، پیش از هر دست‌زدنی به دفترِ زنده ----
        val version = ledgerFileVersion(staged)
        when {
            version == null || version <= 0 -> {
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

        return staged
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
            runCatching {
                db.delete()
                previous.inputStream().use { input ->
                    db.outputStream().use { input.copyTo(it) }
                }
            }
            throw e
        }
        previous.delete()
        staged.delete()
        Unit
    }
}
