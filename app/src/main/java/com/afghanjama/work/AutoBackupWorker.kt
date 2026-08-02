package com.afghanjama.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afghanjama.AppInfo
import com.afghanjama.data.buildAppDatabase
import com.afghanjama.util.DownloadsWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * بکاپ خودکار روزانهٔ دیتابیس.
 *
 * فایل در `Downloads/KhayatYar` می‌نشیند (یا در گوشی‌های قدیمی‌تر در
 * پوشهٔ خارجیِ اپ) و فقط ۷ نسخهٔ آخر نگه داشته می‌شود.
 *
 * عمداً **فقط دیتابیس** است، بدونِ عکس‌ها: هفت نسخه × عکس‌های کارگاه
 * می‌تواند صدها مگابایت از گوشیِ ارزان بخورد. این بکاپ در برابرِ پاک‌شدنِ
 * دادهٔ اپ محافظت می‌کند و گوشی همان‌جاست؛ پشتیبانِ دستی است که کامل است
 * و در برابرِ گم‌شدنِ گوشی کار می‌کند.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WAL را یکپارچه کن تا فایل اصلی دیتابیس کامل باشد
        val db = buildAppDatabase(applicationContext)
        try {
            db.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(FULL)")
                .use { it.moveToFirst() }
        } finally {
            db.close()
        }

        val dbFile = applicationContext.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return Result.success()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val ok = DownloadsWriter.write(applicationContext, "$PREFIX$stamp.db") { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        }
        if (ok) {
            DownloadsWriter.prune(applicationContext, PREFIX, KEEP)
            applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST, System.currentTimeMillis())
                .apply()
        }
        // بکاپ ناموفق نباید صف کار را بشکند؛ روز بعد دوباره تلاش می‌شود
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "daily_auto_backup"
        const val PREFS = "backup_prefs"
        const val KEY_LAST = "last_auto_backup"
        private const val DB_NAME = "afghanjama.db"
        private val PREFIX = "${AppInfo.NAME_LATIN}-auto-"
        private const val KEEP = 7
    }
}
