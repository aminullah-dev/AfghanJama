package com.afghanjama.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * اشتراک فایل از cache/shared از طریق FileProvider —
 * برای فاکتور PDF و ارسال بکاپ به Drive/واتساپ.
 */
object ShareUtil {

    /** پوشهٔ فایل‌های قابل اشتراک را (تمیزشده) برمی‌گرداند. */
    fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    fun shareFile(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
