package com.afghanjama.ui.vm

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.AppInfo
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.repo.Repo
import com.afghanjama.util.BackupArchive
import com.afghanjama.util.PhotoStore
import com.afghanjama.ui.format.fa
import com.afghanjama.util.ShareUtil
import com.afghanjama.work.AutoBackupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.afghanjama.util.DownloadsWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.OutputStream

data class BackupUi(
    val message: String? = null,
    val isError: Boolean = false,
    val restartRequired: Boolean = false
)

/** پشتیبان‌گیری/بازیابی دیتابیس و خروجی CSV (سازگار با اکسل). */
class BackupViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui

    /** ریست چند ثانیه طول می‌کشد؛ دو بار زدن نباید دو بار اجرایش کند. */
    private val busy = Busy()
    val working: StateFlow<Boolean> = busy.state

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    // ------------------------------------------------
    // پشتیبان‌گیری: کپی فایل دیتابیس به محل انتخابی کاربر
    // ------------------------------------------------
    fun backupTo(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            repo.checkpoint() // یکپارچه‌سازی WAL تا فایل اصلی کامل باشد
            val dbFile = context.getDatabasePath(DB_NAME)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                BackupArchive.write(dbFile, PhotoStore.dir(context), out)
            } ?: error("openOutputStream returned null")
        }.onSuccess { photos ->
            _ui.update {
                it.copy(
                    message = "✅ پشتیبان‌گیری کامل شد" +
                        (if (photos > 0) " — همراهِ ${photos.fa()} عکس." else "."),
                    isError = false
                )
            }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در پشتیبان‌گیری: ${e.message}", isError = true) }
        }
    }

    // ------------------------------------------------
    // ارسال بکاپ به Drive/واتساپ/تلگرام از طریق صفحه اشتراک
    // ------------------------------------------------
    fun shareBackup(context: Context) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            repo.checkpoint()
            val dbFile = context.getDatabasePath(DB_NAME)
            val out = java.io.File(
                ShareUtil.sharedDir(context),
                "afghanjama-backup.ajb"
            )
            out.outputStream().use {
                BackupArchive.write(dbFile, PhotoStore.dir(context), it)
            }
            out
        }.onSuccess { file ->
            withContext(Dispatchers.Main) {
                ShareUtil.shareFile(
                    context, file,
                    mime = "application/octet-stream",
                    chooserTitle = "ارسال بکاپ (Drive، واتساپ، ...)"
                )
            }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در آماده‌سازی بکاپ: ${e.message}", isError = true) }
        }
    }

    /** زمان آخرین بکاپ خودکار (۰ = هنوز اجرا نشده). */
    fun lastAutoBackupTime(context: Context): Long =
        context.getSharedPreferences(AutoBackupWorker.PREFS, Context.MODE_PRIVATE)
            .getLong(AutoBackupWorker.KEY_LAST, 0L)

    // ------------------------------------------------
    // بازیابی: جایگزینی فایل دیتابیس + نیاز به راه‌اندازی دوباره اپ
    // ------------------------------------------------
    /**
     * بازیابی. دو قالب پذیرفته می‌شود: پشتیبانِ کاملِ تازه (zip با عکس‌ها)
     * و فایلِ خامِ دیتابیس که نسخه‌های قبلی می‌ساختند — کاربر ممکن است
     * پشتیبانِ هفتهٔ پیش را داشته باشد و آن نباید بی‌مصرف شود.
     *
     * دیتابیس **اول در فایلِ موقت** باز می‌شود و فقط وقتی کامل شد جای
     * اصلی را می‌گیرد. اگر وسطِ کار قطع شود، دادهٔ سالمِ کارگاه دست‌نخورده
     * می‌ماند.
     */
    fun restoreFrom(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(DB_NAME)
            val staged = java.io.File(dbFile.parentFile, "$DB_NAME.restore")
            staged.delete()

            // قالب از چند بایتِ اول تشخیص داده می‌شود، نه از پسوندِ فایل —
            // کاربر ممکن است اسمِ فایل را عوض کرده باشد.
            val head = context.contentResolver.openInputStream(uri)?.use { input ->
                ByteArray(BackupArchive.HEAD_BYTES).let { buf ->
                    val n = input.read(buf).coerceAtLeast(0)
                    buf.copyOf(n)
                }
            } ?: error("openInputStream returned null")

            val format = BackupArchive.detect(head)
            if (format == BackupArchive.Format.UNKNOWN) {
                error("این فایل پشتیبانِ ${AppInfo.NAME} نیست.")
            }

            var photos = 0
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (format) {
                    BackupArchive.Format.ZIP -> {
                        val res = BackupArchive.extract(
                            input, staged, PhotoStore.dir(context)
                        )
                        if (!res.dbWritten) error("فایلِ پشتیبان دیتابیس ندارد.")
                        photos = res.photos
                    }
                    else -> staged.outputStream().use { input.copyTo(it) }
                }
            } ?: error("openInputStream returned null")

            if (staged.length() <= 0L) error("فایلِ پشتیبان خالی است.")

            // ---- سنجشِ فایل **پیش از** اینکه به دیتابیسِ زنده دست بزنیم ----
            // بی این، یک فایلِ خراب یا ساخته‌شده با نسخهٔ جلوترِ اپ جایگزین
            // می‌شد و Room در اجرای بعدی نمی‌توانست بازش کند — و چون
            // fallbackToDestructiveMigration روشن است، کلِ دادهٔ کارگاه بی
            // هیچ پیامی پاک می‌شد. یعنی خودِ بازیابی داده را نابود می‌کرد.
            val probe = probeDatabase(staged)
            if (probe.verdict != BackupArchive.Verdict.OK) {
                // نسخه پیش از پاک‌کردنِ فایل خوانده شده، وگرنه پیام عددِ صفر
                // نشان می‌داد
                staged.delete()
                error(
                    BackupArchive.verdictMessage(probe.verdict, probe.version, DB_VERSION)
                )
            }

            repo.checkpoint()
            // اتصالِ باز باید قبل از بازنویسیِ فایل بسته شود؛ وگرنه صفحه‌های
            // کش‌شدهٔ آن اتصال روی دیتابیسِ تازه می‌نشیند و خرابش می‌کند.
            repo.closeDatabase()
            // فایل‌های WAL/SHM قدیمی نباید با دیتابیس بازیابی‌شده قاطی شوند
            java.io.File(dbFile.path + "-wal").delete()
            java.io.File(dbFile.path + "-shm").delete()

            // ---- نسخهٔ برگشت ----
            // تا امروز دیتابیسِ زنده مستقیم بازنویسی می‌شد. اگر کپی وسطِ کار
            // می‌شکست (دیسکِ پر)، نه فایلِ سالم می‌ماند و نه بازیابی تمام
            // می‌شد. حالا نسخهٔ قبلی کنار گذاشته می‌شود و فقط پس از موفقیت
            // پاک می‌گردد.
            val previous = java.io.File(dbFile.parentFile, "$DB_NAME.prev")
            previous.delete()
            if (dbFile.exists() && !dbFile.renameTo(previous)) {
                dbFile.inputStream().use { input ->
                    previous.outputStream().use { input.copyTo(it) }
                }
            }
            try {
                staged.inputStream().use { input ->
                    dbFile.outputStream().use { input.copyTo(it) }
                }
            } catch (e: Throwable) {
                // برگرداندنِ دادهٔ سالم؛ بازیابی نشد ولی چیزی هم از دست نرفت
                runCatching {
                    dbFile.delete()
                    previous.inputStream().use { input ->
                        dbFile.outputStream().use { input.copyTo(it) }
                    }
                }
                throw e
            }
            previous.delete()
            staged.delete()
            photos
        }.onSuccess { photos ->
            _ui.update {
                it.copy(
                    message = "✅ بازیابی انجام شد" +
                        (if (photos > 0) " — ${photos.fa()} عکس هم برگشت" else "") +
                        ". اپ را ببندید و دوباره باز کنید.",
                    isError = false,
                    restartRequired = true
                )
            }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در بازیابی: ${e.message}", isError = true) }
        }
    }

    /** سرنوشتِ فایل به‌همراهِ نسخه‌اش — نسخه برای پیامِ کاربر لازم است. */
    private data class Probe(val verdict: BackupArchive.Verdict, val version: Int)

    /**
     * آیا این فایل واقعاً یک دیتابیسِ سالم و قابلِ بازکردن است؟
     *
     * `detect` فقط چند بایتِ اولِ **فایلِ ورودی** را می‌دید؛ محتوای zip
     * اصلاً سنجیده نمی‌شد. یک zip با ورودیِ `database` پر از آشغال از آن
     * کنترل رد می‌شد و مستقیم جای دیتابیسِ زنده می‌نشست.
     *
     * Room نسخهٔ اسکیما را در `user_version` می‌گذارد، پس بی بازکردنِ کاملِ
     * Room هم خواندنی است.
     */
    private fun probeDatabase(file: java.io.File): Probe {
        var openable = false
        var integrityOk = false
        var version = 0
        runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.path, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                openable = true
                version = db.version
                // quick_check از integrity_check سبک‌تر است و برای فایلِ
                // بریده یا آشغال کافی؛ روی گوشیِ کارگاه نباید دقیقه‌ها طول بکشد
                db.rawQuery("PRAGMA quick_check", null).use { c ->
                    integrityOk = c.moveToFirst() &&
                        c.getString(0).equals("ok", ignoreCase = true)
                }
            }
        }
        return Probe(
            BackupArchive.verdict(openable, integrityOk, version, DB_VERSION),
            version
        )
    }

    // ------------------------------------------------
    // ریست داده
    // ------------------------------------------------

    /** واژه‌ای که کاربر باید بنویسد تا دکمهٔ ریست فعال شود. */
    val resetPhrase: String get() = "پاک کن"

    /**
     * پاک‌کردنِ کارها و حساب‌ها. اطلاعات پایه و مشتریان می‌مانند.
     *
     * پیش از هر پاک‌کردنی یک پشتیبانِ **کامل** در Downloads نوشته می‌شود و
     * اگر آن نوشته نشود، ریست **انجام نمی‌شود**. کاربر نباید بتواند بدونِ
     * تور پرواز کند؛ این کار برگشت ندارد.
     */
    fun resetData(context: Context) = viewModelScope.launch {
        busy.once {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repo.checkpoint()
                    val dbFile = context.getDatabasePath(DB_NAME)
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

                    val saved = DownloadsWriter.write(
                        context, "afghanjama-pish-az-reset-$stamp.ajb"
                    ) { out -> BackupArchive.write(dbFile, PhotoStore.dir(context), out) }

                    if (!saved) {
                        error(
                            "پشتیبانِ ایمنی نوشته نشد، پس چیزی پاک نشد. " +
                                "فضای گوشی را بررسی کنید و دوباره امتحان کنید."
                        )
                    }

                    val cleared = repo.resetOperationalData()
                    // عکس‌ها صاحبشان را از دست داده‌اند؛ پوشه یک‌جا پاک می‌شود
                    runCatching { PhotoStore.dir(context).listFiles()?.forEach { it.delete() } }
                    cleared
                }
            }
            result.onSuccess { cleared ->
                _ui.update {
                    it.copy(
                        message = "✅ ${cleared.fa()} جدول پاک شد. اطلاعات پایه و " +
                            "مشتریان سرِ جایشان‌اند. پشتیبانِ پیش از ریست در " +
                            "Downloads/${DownloadsWriter.FOLDER} است.",
                        isError = false
                    )
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(message = e.message ?: "ریست انجام نشد.", isError = true)
                }
            }
        }
    }

    // ------------------------------------------------
    // خروجی CSV — با BOM تا اکسل متن فارسی را درست نشان دهد
    // ------------------------------------------------
    fun exportOrdersCsv(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val orders = repo.observeAllOrders().first()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeBom(out)
                val w = out.writer(Charsets.UTF_8)
                w.appendLine(
                    listOf(
                        "کد سفارش", "کد کوتاه", "طرح", "تعداد", "نوع پارچه", "رنگ", "سایز",
                        "مقدار پارچه", "واحد", "منبع پارچه", "قیمت پارچه", "خرج کار",
                        "قیمت توافقی", "مشتری", "تلفن", "خیاط", "وضعیت", "تاریخ ایجاد"
                    ).joinToString(",") { csv(it) }
                )
                orders.forEach { o ->
                    w.appendLine(
                        listOf(
                            o.orderCode, o.shortCode, o.designTitle, o.qty.toString(),
                            o.fabricType, o.fabricColor, o.size,
                            o.fabricAmount.toString(), o.fabricUnit, o.fabricSource,
                            o.fabricPrice.toString(), o.workCost.toString(),
                            o.agreedPrice.toString(), o.customerName, o.customerPhone,
                            o.assignedTailor ?: "", o.status, formatDate(o.createdAt)
                        ).joinToString(",") { csv(it) }
                    )
                }
                w.flush()
            } ?: error("openOutputStream returned null")
        }.onSuccess {
            _ui.update { it.copy(message = "✅ خروجی سفارش‌ها ذخیره شد.", isError = false) }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در خروجی: ${e.message}", isError = true) }
        }
    }

    fun exportTransactionsCsv(context: Context, uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val txList = repo.observeTx().first()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeBom(out)
                val w = out.writer(Charsets.UTF_8)
                w.appendLine(
                    listOf("نوع", "صندوق", "مبلغ", "دسته", "یادداشت", "تاریخ")
                        .joinToString(",") { csv(it) }
                )
                txList.forEach { t ->
                    w.appendLine(
                        listOf(
                            if (t.type == "IN") "دریافت" else "پرداخت",
                            t.source, t.amount.toString(), t.category, t.note,
                            formatDate(t.createdAt)
                        ).joinToString(",") { csv(it) }
                    )
                }
                w.flush()
            } ?: error("openOutputStream returned null")
        }.onSuccess {
            _ui.update { it.copy(message = "✅ خروجی تراکنش‌ها ذخیره شد.", isError = false) }
        }.onFailure { e ->
            _ui.update { it.copy(message = "خطا در خروجی: ${e.message}", isError = true) }
        }
    }

    private fun writeBom(out: OutputStream) {
        out.write(0xEF)
        out.write(0xBB)
        out.write(0xBF)
    }

    private fun csv(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    // تاریخ شمسی با ارقام لاتین — قابل مرتب‌سازی در اکسل
    private fun formatDate(millis: Long): String =
        com.afghanjama.ui.format.PersianDate.csv(millis)

}
