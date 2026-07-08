package com.afghanjama.work

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.afghanjama.data.AppDatabase
import com.afghanjama.data.MIGRATION_19_20
import com.afghanjama.data.MIGRATION_20_21
import com.afghanjama.data.MIGRATION_21_22
import com.afghanjama.data.MIGRATION_22_23
import com.afghanjama.data.MIGRATION_23_24
import com.afghanjama.data.MIGRATION_24_25
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * بکاپ خودکار روزانهٔ دیتابیس.
 *
 * اندروید ۱۰ به بالا: فایل در Downloads/AfghanJama ذخیره می‌شود
 * (بدون نیاز به مجوز، و با پاک‌شدن اپ هم از بین نمی‌رود).
 * قدیمی‌تر: در پوشهٔ خارجی مخصوص اپ ذخیره می‌شود.
 * فقط ۷ بکاپ آخر نگه داشته می‌شود.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // WAL را یکپارچه کن تا فایل اصلی دیتابیس کامل باشد
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            DB_NAME
        )
            .addMigrations(
                MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22,
                MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25
            )
            .fallbackToDestructiveMigration()
            .build()
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
        val fileName = "$PREFIX$stamp.db"

        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backupToDownloads(dbFile, fileName)
                pruneDownloads()
            } else {
                backupToAppExternal(dbFile, fileName)
            }
        }.isSuccess

        if (ok) {
            applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST, System.currentTimeMillis())
                .apply()
        }
        // بکاپ ناموفق نباید صف کار را بشکند؛ روز بعد دوباره تلاش می‌شود
        return Result.success()
    }

    /** اندروید ۱۰+: نوشتن در Downloads/AfghanJama از طریق MediaStore. */
    private fun backupToDownloads(dbFile: File, fileName: String) {
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { out ->
            dbFile.inputStream().use { it.copyTo(out) }
        } ?: error("openOutputStream failed")
    }

    /** حذف بکاپ‌های خودکار قدیمی‌تر از ۷ نسخهٔ آخر (فقط فایل‌های خود اپ). */
    private fun pruneDownloads() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = applicationContext.contentResolver
        val entries = mutableListOf<Pair<Long, String>>() // id, name
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
            arrayOf("$PREFIX%"),
            null
        )?.use { cur ->
            val idCol = cur.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cur.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            while (cur.moveToNext()) {
                entries.add(cur.getLong(idCol) to cur.getString(nameCol))
            }
        }
        // نام‌ها شامل مهر زمان هستند؛ مرتب‌سازی نزولی = جدیدترین اول
        entries.sortedByDescending { it.second }
            .drop(KEEP)
            .forEach { (id, _) ->
                runCatching {
                    resolver.delete(
                        ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                        null, null
                    )
                }
            }
    }

    /** اندروید ۹ و پایین‌تر: پوشهٔ خارجی مخصوص اپ. */
    private fun backupToAppExternal(dbFile: File, fileName: String) {
        val dir = applicationContext.getExternalFilesDir("backups") ?: return
        dir.mkdirs()
        dbFile.copyTo(File(dir, fileName), overwrite = true)
        dir.listFiles { f -> f.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
    }

    companion object {
        const val WORK_NAME = "daily_auto_backup"
        const val PREFS = "backup_prefs"
        const val KEY_LAST = "last_auto_backup"
        private const val DB_NAME = "afghanjama.db"
        private const val PREFIX = "afghanjama-auto-"
        private const val FOLDER = "AfghanJama"
        private const val KEEP = 7
    }
}
