package com.afghanjama.desktop.data

import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «پاک‌کردنِ کارها و حساب‌ها» روی کامپیوتر — همان کارِ گوشی، با همان تور.
 *
 * ترتیب عمدی است و هر قدمش یک قاعدهٔ گوشی است (`BackupViewModel.resetData`):
 *
 * 1. **اول پشتیبانِ ایمنیِ کامل** در `backups/` کنارِ دفتر. اگر نوشته نشود،
 *    هیچ چیزی پاک نمی‌شود — این کار برگشت ندارد.
 * 2. بعد `Repo.resetOperationalData()`: همان فهرستِ `ResetPlan`، همان
 *    سطرِ «رسیدگی» («ریست داده»).
 * 3. آخر عکس‌هایی که صاحبشان پاک شد — **جز لوگوی کارگاه**؛ لوگو هویتِ
 *    کارگاه است، نه کار.
 */
object DesktopReset {

    data class Outcome(val tablesCleared: Int, val safetyBackup: File)

    fun safetyName(at: Date = Date()): String =
        "KhayatYar-pish-az-reset-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(at)}.ajb"

    /**
     * [backup] پشتیبان را در فایلی که به آن داده می‌شود می‌نویسد. برنامه
     * [DesktopBackup.backupTo] را می‌دهد؛ آزمون می‌تواند نسخهٔ خراب بدهد و
     * ببیند چیزی پاک نمی‌شود.
     */
    suspend fun run(
        repo: Repo,
        settings: Settings,
        backupDir: File = backupsDir(),
        photoDir: File = photosDir(),
        backup: (File) -> Result<Unit> = { DesktopBackup.backupTo(it) },
    ): Result<Outcome> = runCatching {
        backupDir.mkdirs()
        val safety = File(backupDir, safetyName())
        backup(safety).getOrElse {
            safety.delete()
            error(
                "پشتیبانِ ایمنی نوشته نشد، پس چیزی پاک نشد. " +
                    "جای خالیِ دیسک را ببینید و دوباره امتحان کنید. (${it.message})"
            )
        }
        if (!safety.isFile || safety.length() <= 0L) {
            error("پشتیبانِ ایمنی خالی ماند، پس چیزی پاک نشد.")
        }

        val cleared = repo.resetOperationalData()

        // عکس‌هایی که سطرشان رفت. خطای جارو ریست را برنمی‌گرداند — دفتر
        // پاک شده و همان مهم است؛ فایلِ اضافه فقط جا می‌گیرد.
        val keep = CompanyPrefs.logo(settings).takeIf { it.isNotBlank() }
        runCatching {
            photoDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != keep) f.delete()
            }
        }
        Outcome(cleared, safety)
    }
}
