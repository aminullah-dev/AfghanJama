package com.afghanjama.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/**
 * نوشتنِ فایل در جایی که با حذفِ اپ از بین نرود.
 *
 * اندروید ۱۰ به بالا: `Downloads/AfghanJama` از راهِ MediaStore، بدونِ
 * نیاز به هیچ مجوزی. قدیمی‌تر: پوشهٔ خارجیِ مخصوصِ اپ.
 *
 * از `AutoBackupWorker` بیرون کشیده شد تا پشتیبانِ ایمنیِ پیش از ریست هم
 * از همین مسیرِ آزموده استفاده کند و کد دو جا تکرار نشود.
 */
object DownloadsWriter {

    const val FOLDER = "AfghanJama"

    /**
     * فایل را می‌سازد و [write] را روی جریانِ خروجی‌اش صدا می‌زند.
     * @return true اگر نوشتن کامل شد.
     */
    fun write(context: Context, fileName: String, write: (OutputStream) -> Unit): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER
                    )
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert failed")
                resolver.openOutputStream(uri)?.use(write) ?: error("openOutputStream failed")
            } else {
                val dir = context.getExternalFilesDir("backups") ?: error("no external dir")
                dir.mkdirs()
                File(dir, fileName).outputStream().use(write)
            }
        }.isSuccess

    /**
     * فقط [keep] فایلِ تازه‌ترِ دارای این پیشوند را نگه می‌دارد.
     * نام‌ها مهرِ زمان دارند، پس مرتب‌سازیِ نزولیِ متنی یعنی جدیدترین اول.
     */
    fun prune(context: Context, prefix: String, keep: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val entries = mutableListOf<Pair<Long, String>>()
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
                    MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
                    arrayOf("$prefix%"),
                    null
                )?.use { cur ->
                    val idCol = cur.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cur.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    while (cur.moveToNext()) {
                        entries.add(cur.getLong(idCol) to cur.getString(nameCol))
                    }
                }
                entries.sortedByDescending { it.second }
                    .drop(keep)
                    .forEach { (id, _) ->
                        runCatching {
                            resolver.delete(
                                ContentUris.withAppendedId(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                                ),
                                null, null
                            )
                        }
                    }
            } else {
                val dir = context.getExternalFilesDir("backups") ?: return
                dir.listFiles { f -> f.name.startsWith(prefix) }
                    ?.sortedByDescending { it.name }
                    ?.drop(keep)
                    ?.forEach { it.delete() }
            }
        }
    }
}
