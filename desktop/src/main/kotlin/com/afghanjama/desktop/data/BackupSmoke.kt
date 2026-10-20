package com.afghanjama.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.data.ResetPlan
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.ui.vm.UserRole
import com.afghanjama.util.BackupArchive
import com.afghanjama.util.BackupArchiveIo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/*
 * دودآزماییِ پشتیبان و بازیابی.
 *
 * **چرا جدا از `migrationSmoke`.** آن یکی می‌گوید دفتر بالا می‌آید؛ این
 * می‌گوید دفتر **از دست نمی‌رود**. دو خرابیِ متفاوت با دو علت، و باید
 * بتوانند جدا قرمز شوند.
 *
 * **چرا اصلاً آزمون لازم است:** بازیابی تنها جایی در کلِ برنامه است که
 * دفترِ کارگاه را **بازنویسی** می‌کند. اگر فایلِ خراب یا فایلِ نسخهٔ
 * جلوتر پذیرفته شود، نتیجه‌اش پاک شدنِ حسابِ کارگاه است — همان چیزی که
 * قرار بود پشتیبان جلویش را بگیرد.
 *
 * هیچ بندی به دفترِ واقعی دست نمی‌زند: همه روی پوشهٔ موقت. برای همین
 * `writeBackup`/`stageAndValidate`/`swapIn` از `backupTo`/`restoreFrom`
 * جدا شدند.
 */

private class Failure(msg: String) : RuntimeException(msg)

private fun need(cond: Boolean, msg: String) {
    if (!cond) throw Failure(msg)
}

private fun check(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

/** یک دفترِ واقعی (اسکیمای امروز) در مسیرِ دلخواه. */
private fun makeLedger(f: File) {
    val db = openDatabase(f)
    db.tableNames()   // Room تنبل است؛ بی پرس‌وجو فایل ساخته نمی‌شود
    db.close()
}

private fun forceVersion(f: File, v: Int) {
    val c = BundledSQLiteDriver().open(f.absolutePath)
    try {
        c.execSQL("PRAGMA user_version = $v")
    } finally {
        c.close()
    }
}

fun main() {
    println("backup smoke - KhayatYar Windows")
    println("=".repeat(52))
    val dir = Files.createTempDirectory("khayatyar-backup").toFile()
    var ok = true

    val live = File(dir, DB_NAME)
    makeLedger(live)
    val liveSize = live.length()

    // ۱ — پشتیبان ساخته می‌شود و قالبش همان قالبِ گوشی است.
    val archive = File(dir, "backup.ajb")
    ok = check("backup writes an archive the phone's format detector accepts") {
        DesktopBackup.writeBackup(live, archive)
        need(archive.exists() && archive.length() > 0, "archive is empty")
        val head = archive.inputStream().use { it.readNBytes(BackupArchive.HEAD_BYTES) }
        need(
            BackupArchive.detect(head) == BackupArchive.Format.ZIP,
            "archive should be detected as the shared ZIP format"
        )
    } && ok

    // ۲ — همان پشتیبان دوباره باز و سنجیده می‌شود.
    ok = check("a good backup stages and validates") {
        val stageDir = File(dir, "s1").apply { mkdirs() }
        val staged = DesktopBackup.stageAndValidate(archive, stageDir)
        need(staged.exists(), "staged file missing")
        need(
            ledgerFileVersion(staged) == DB_VERSION,
            "staged version should be $DB_VERSION, got ${ledgerFileVersion(staged)}"
        )
    } && ok

    // ۳ — فایلِ بی‌ربط رد می‌شود.
    ok = check("a file that is not a backup is refused") {
        val junk = File(dir, "junk.ajb")
        junk.writeText("این یک فایلِ پشتیبان نیست")
        val stageDir = File(dir, "s2").apply { mkdirs() }
        val e = runCatching { DesktopBackup.stageAndValidate(junk, stageDir) }.exceptionOrNull()
        need(e != null, "junk should have been refused")
        need(live.length() == liveSize, "the live ledger must not be touched")
    } && ok

    // ۴ — پشتیبانِ نسخهٔ جلوتر رد می‌شود.
    //
    // این خطرناک‌ترین حالت است: اگر پذیرفته شود، Room بعداً نمی‌تواند
    // بازش کند و روی اندروید `fallbackToDestructiveMigration` کلِ دفتر
    // را پاک می‌کند.
    ok = check("a backup from a NEWER app version is refused") {
        val newer = File(dir, "newer.db")
        makeLedger(newer)
        forceVersion(newer, DB_VERSION + 1)
        val arc = File(dir, "newer.ajb")
        DesktopBackup.writeBackup(newer, arc)

        val stageDir = File(dir, "s3").apply { mkdirs() }
        val e = runCatching { DesktopBackup.stageAndValidate(arc, stageDir) }.exceptionOrNull()
        need(e != null, "a newer backup should have been refused")
        need(
            e!!.message.orEmpty().contains("${DB_VERSION + 1}"),
            "the message should name the file version: ${e.message}"
        )
        need(
            !File(stageDir, "$DB_NAME.restore").exists(),
            "the staged file must be cleaned up on rejection"
        )
        need(live.length() == liveSize, "the live ledger must not be touched")
    } && ok

    // ۵ — پشتیبانِ عقب‌تر از پایه هم رد می‌شود.
    ok = check("a backup older than DESKTOP_BASELINE is refused") {
        val older = File(dir, "older.db")
        makeLedger(older)
        forceVersion(older, DESKTOP_BASELINE - 1)
        val arc = File(dir, "older.ajb")
        DesktopBackup.writeBackup(older, arc)

        val stageDir = File(dir, "s4").apply { mkdirs() }
        val e = runCatching { DesktopBackup.stageAndValidate(arc, stageDir) }.exceptionOrNull()
        need(e != null, "an older backup should have been refused")
        need(live.length() == liveSize, "the live ledger must not be touched")
    } && ok

    // ۶ — قالبِ خامِ قدیمی (فایلِ دیتابیس، بی zip) هم پذیرفته می‌شود.
    //
    // نسخه‌های قبلیِ اپ همین را می‌ساختند و کاربر ممکن است پشتیبانِ
    // هفتهٔ پیش را داشته باشد؛ نباید بی‌مصرف شود.
    ok = check("a raw .db backup from older builds still restores") {
        val raw = File(dir, "raw.db")
        live.copyTo(raw, overwrite = true)
        val stageDir = File(dir, "s5").apply { mkdirs() }
        val staged = DesktopBackup.stageAndValidate(raw, stageDir)
        need(ledgerFileVersion(staged) == DB_VERSION, "raw backup should validate")
    } && ok

    // ۷ — جایگزینی: دفتر عوض می‌شود و آشغالی نمی‌ماند.
    ok = check("swapIn replaces the ledger and leaves no litter") {
        val target = File(dir, "swap").apply { mkdirs() }
        val db = File(target, DB_NAME)
        makeLedger(db)

        // دفترِ «تازه» با یک نشانه تا بشود تشخیص داد کدام برنده شد.
        val incoming = File(target, "incoming.db")
        makeLedger(incoming)
        val c = BundledSQLiteDriver().open(incoming.absolutePath)
        try {
            c.execSQL("CREATE TABLE IF NOT EXISTS `_swap_probe` (`x` INTEGER NOT NULL)")
        } finally {
            c.close()
        }

        DesktopBackup.swapIn(incoming, db)

        val after = BundledSQLiteDriver().open(db.absolutePath)
        val found = try {
            val st = after.prepare(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='_swap_probe'"
            )
            try {
                st.step() && st.getLong(0) > 0
            } finally {
                st.close()
            }
        } finally {
            after.close()
        }
        need(found, "the incoming ledger did not replace the live one")
        need(!incoming.exists(), "the staged file should be removed")
        need(!File(target, "$DB_NAME.prev").exists(), "the rollback copy should be removed")
    } && ok

    // «پاک‌کردنِ کارها و حساب‌ها» — هیچ راهی به پاک‌کردن بی پشتیبانِ ایمنیِ
    // **سنجیده** نیست، و نقش و واژه در خودِ DesktopReset سنجیده می‌شوند.
    ok = check("reset needs a manager, the phrase, and a safety backup that opens") {
        val ledger = File(dir, "reset-guard.db")
        val db = openDatabase(ledger)
        val repo = Repo(db)
        val settings = FileSettings(File(dir, "prefs-reset-guard"))
        val photos = File(dir, "photos-reset-guard").apply { mkdirs() }
        val logo = File(photos, "logo_1.png").apply { writeBytes(byteArrayOf(1)) }
        val orderPhoto = File(photos, "p_9.jpg").apply { writeBytes(byteArrayOf(2)) }
        CompanyPrefs.saveLogo(settings, logo.name)
        val backups = File(dir, "backups-reset-guard")
        val phrase = ResetPlan.CONFIRM_PHRASE
        val good = { f: File -> runCatching { db.checkpoint(); DesktopBackup.writeBackup(ledger, f) } }
        try {
            runBlocking {
                repo.checkIn("کارمندِ آزمون")
                suspend fun untouched(why: String) {
                    need(repo.observeAttendance().first().size == 1, "$why: nothing may be cleared")
                    need(orderPhoto.exists() && logo.exists(), "$why: no photo may be swept")
                }

                val notManager = DesktopReset.run(repo, settings, UserRole.SALES, phrase, backups, photos, good)
                need(notManager.isFailure, "a non-manager must be refused")
                untouched("non-manager")

                val noPhrase = DesktopReset.run(repo, settings, UserRole.MANAGER, "پاک", backups, photos, good)
                need(noPhrase.isFailure, "a wrong confirmation phrase must be refused")
                untouched("wrong phrase")

                val failed = DesktopReset.run(repo, settings, UserRole.MANAGER, phrase, backups, photos) {
                    Result.failure(IllegalStateException("دیسک پر است"))
                }
                need(failed.isFailure, "reset must refuse when the safety backup fails")
                untouched("failed backup")

                // «نوشته شد» ولی هیچ فایلی نیست — پشتیبانی که خطا نداد ولی چیزی ننوشت.
                val empty = DesktopReset.run(repo, settings, UserRole.MANAGER, phrase, backups, photos) {
                    Result.success(Unit)
                }
                need(empty.isFailure, "a safety backup that wrote nothing must be refused")
                untouched("empty backup")

                // نوشته شد، ولی باز نمی‌شود (برق رفت، دیسک خراب، کدِ نادرست).
                val junk = DesktopReset.run(repo, settings, UserRole.MANAGER, phrase, backups, photos) { f ->
                    runCatching { f.writeText("این پشتیبان نیست") }
                }
                need(junk.isFailure, "a safety backup that does not open must be refused")
                untouched("unreadable backup")
                need(backups.listFiles().orEmpty().none { it.length() > 0 && it.readText() == "این پشتیبان نیست" },
                    "the unreadable safety file must not be left looking like a backup")

                // پشتیبانِ سالم ولی بی عکس: دفتر پاک می‌شود، عکسی که در پشتیبان نیست می‌ماند.
                val noPhotos = DesktopReset.run(repo, settings, UserRole.MANAGER, " $phrase ", backups, photos, good)
                    .getOrThrow()
                need(DesktopReset.verify(noPhotos.safetyBackup).isSuccess, "the safety backup should verify")
                need(repo.observeAttendance().first().isEmpty(), "attendance should be cleared")
                need(orderPhoto.exists(), "a photo that is not in the safety backup must not be deleted")
                need(logo.exists(), "the workshop logo must survive a reset")

                // پشتیبانِ سالم با عکس: عکسِ سفارش می‌رود، لوگو می‌ماند.
                val withPhotos = DesktopReset.run(repo, settings, UserRole.MANAGER, phrase, backups, photos) { f ->
                    runCatching {
                        db.checkpoint()
                        f.outputStream().use { BackupArchiveIo.write(ledger, photos, it) }
                        Unit
                    }
                }.getOrThrow()
                need(DesktopReset.verify(withPhotos.safetyBackup).getOrThrow() == setOf(logo.name, orderPhoto.name),
                    "the safety backup should carry both photos")
                need(!orderPhoto.exists(), "an order photo that is in the safety backup goes with the orders")
                need(logo.exists(), "the workshop logo must survive a reset")
            }
        } finally {
            db.close()
        }
    } && ok

    dir.deleteRecursively()
    println("=".repeat(52))
    println(if (ok) "backup path verified" else "backup smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
