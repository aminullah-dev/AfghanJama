package com.afghanjama.desktop.data

import com.afghanjama.data.ResetPlan
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.vm.Permissions
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.util.BackupArchive
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * «پاک‌کردنِ کارها و حساب‌ها» روی کامپیوتر — همان کارِ گوشی، با همان تور.
 *
 * ترتیب عمدی است و هر قدمش یک قاعدهٔ گوشی است (`BackupViewModel.resetData`):
 *
 * 0. **نقش و واژه همین‌جا سنجیده می‌شوند**، نه فقط در رابط: فقط مدیر، و
 *    فقط وقتی «پاک کن» نوشته شده باشد. دکمه‌ای که روزی جای دیگری گذاشته
 *    شود و دسترسی را فراموش کند، باز هم از این در نمی‌گذرد.
 * 1. **اول پشتیبانِ ایمنیِ کامل** در `backups/` کنارِ دفتر. اگر نوشته نشود،
 *    هیچ چیزی پاک نمی‌شود — این کار برگشت ندارد.
 * 2. **پشتیبان باز و سنجیده می‌شود** (قالب، نسخه، `quick_check`، و CRCِ
 *    هر عکس). فایلی که «نوشته شد» ولی باز نمی‌شود تور نیست؛ با آن هم هیچ
 *    چیزی پاک نمی‌شود.
 * 3. بعد `Repo.resetOperationalData()`: همان فهرستِ `ResetPlan`، همان
 *    سطرِ «رسیدگی» («ریست داده»).
 * 4. آخر عکس‌هایی که صاحبشان پاک شد — **فقط آن‌هایی که در پشتیبانِ سنجیده
 *    هستند**، و **جز لوگوی کارگاه**؛ لوگو هویتِ کارگاه است، نه کار. عکسی
 *    که در پشتیبان نیست پاک نمی‌شود: فایلِ اضافه فقط جا می‌گیرد، ولی عکسِ
 *    پاک‌شده‌ای که در هیچ پشتیبانی نیست برای همیشه رفته است.
 */
object DesktopReset {

    data class Outcome(val tablesCleared: Int, val safetyBackup: File)

    fun safetyName(at: Date = Date()): String =
        "KhayatYar-pish-az-reset-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(at)}.ajb"

    /**
     * [role] نقشِ کسی که وارد شده و [typed] همان چیزی که در پنجرهٔ تأیید
     * نوشت. [backup] پشتیبان را در فایلی که به آن داده می‌شود می‌نویسد.
     * برنامه [DesktopBackup.backupTo] را می‌دهد؛ آزمون می‌تواند نسخهٔ خراب
     * بدهد و ببیند چیزی پاک نمی‌شود.
     */
    suspend fun run(
        repo: Repo,
        settings: Settings,
        role: UserRole,
        typed: String,
        backupDir: File = backupsDir(),
        photoDir: File = photosDir(),
        backup: (File) -> Result<Unit> = { DesktopBackup.backupTo(it) },
    ): Result<Outcome> = runCatching {
        if (!Permissions.canResetData(role)) {
            error("فقط مدیر می‌تواند کارها و حساب‌ها را پاک کند؛ چیزی پاک نشد.")
        }
        if (typed.trim() != ResetPlan.CONFIRM_PHRASE) {
            error("برای تأیید باید «${ResetPlan.CONFIRM_PHRASE}» نوشته شود؛ چیزی پاک نشد.")
        }

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
        val photosInBackup = verify(safety).getOrElse {
            // فایلِ خراب نگه داشته نمی‌شود: کسی که بعداً در پوشه بگردد،
            // نباید آن را پشتیبانِ سالم بپندارد.
            safety.delete()
            error("پشتیبانِ ایمنی باز و سنجیده نشد، پس چیزی پاک نشد. (${it.message})")
        }

        val cleared = repo.resetOperationalData()

        // عکس‌هایی که سطرشان رفت. خطای جارو ریست را برنمی‌گرداند — دفتر
        // پاک شده و همان مهم است؛ فایلِ اضافه فقط جا می‌گیرد.
        val keep = CompanyPrefs.logo(settings).takeIf { it.isNotBlank() }
        runCatching {
            photoDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name != keep && f.name in photosInBackup) f.delete()
            }
        }
        Outcome(cleared, safety)
    }

    /**
     * پشتیبانِ ایمنی باز می‌شود؟ همان سنجشِ بازیابی (قالب و نسخه)،
     * `quick_check` روی دفترِ داخلش، و خواندنِ کاملِ هر عکس تا CRCِ zip
     * سنجیده شود.
     *
     * @return نامِ عکس‌هایی که سالم در پشتیبان هستند — فقط این‌ها جارو می‌شوند.
     */
    internal fun verify(archive: File): Result<Set<String>> = runCatching {
        val scratch = Files.createTempDirectory("ky-reset-verify").toFile()
        try {
            val staged = DesktopBackup.stageAndValidate(archive, scratch)
            if (!ledgerFileHealthy(staged)) error("دفترِ داخلِ پشتیبان سالم نیست")
        } finally {
            scratch.deleteRecursively()
        }
        photosIn(archive)
    }

    private fun photosIn(archive: File): Set<String> {
        val head = archive.inputStream().use { it.readNBytes(BackupArchive.HEAD_BYTES) }
        if (BackupArchive.detect(head) != BackupArchive.Format.ZIP) return emptySet()
        val names = mutableSetOf<String>()
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = BackupArchive.safePhotoName(entry.name)
                // تا آخر خوانده می‌شود؛ ZipInputStream با CRCِ نادرست استثنا می‌دهد.
                val size = zip.copyTo(OutputStream.nullOutputStream())
                if (name != null && size > 0L) names += name
                zip.closeEntry()
            }
        }
        return names
    }
}
