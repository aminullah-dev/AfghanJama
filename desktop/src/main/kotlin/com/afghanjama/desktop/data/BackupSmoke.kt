package com.afghanjama.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.BackupPrefs
import com.afghanjama.prefs.CompanyPrefs
import com.afghanjama.util.BackupArchive
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

    // ۸ — عکس‌ها با پشتیبان می‌روند و برمی‌گردند (از ۱.۱۰).
    //
    // تا ۱.۹ پشتیبانِ کامپیوتر عکس نداشت و عکس‌های پشتیبانِ گوشی سرِ
    // بازیابی دور ریخته می‌شدند.
    ok = check("photos travel in the archive and come back on restore") {
        val photos = File(dir, "photos-src").apply { mkdirs() }
        val a = File(photos, "p_1_1111.jpg").apply { writeBytes(ByteArray(2048) { (it % 251).toByte() }) }
        val b = File(photos, "logo.png").apply { writeBytes(ByteArray(512) { 7 }) }
        val arc = File(dir, "with-photos.ajb")
        val written = DesktopBackup.writeBackup(live, arc, photos)
        need(written == 2, "two photos should be written, got $written")
        need(!File(arc.path + ".part").exists(), "no half-written .part file may remain")

        val stageDir = File(dir, "s8").apply { mkdirs() }
        DesktopBackup.stageAndValidate(arc, stageDir)
        val staged = DesktopBackup.stagedPhotos(stageDir)
        need(File(staged, a.name).readBytes().contentEquals(a.readBytes()), "photo bytes changed in the archive")

        val target = File(dir, "photos-dst").apply { mkdirs() }
        val keep = File(target, "already-here.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val adopted = DesktopBackup.adoptPhotos(staged, target)
        need(adopted == 2, "two photos should be adopted, got $adopted")
        need(File(target, b.name).readBytes().contentEquals(b.readBytes()), "logo bytes changed")
        need(keep.exists(), "a photo the computer already had must stay")
        need(!staged.exists(), "the photo staging folder should be removed")
    } && ok

    // ۹ — پشتیبانِ ردشده عکس‌هایش را هم پشتِ سر نمی‌گذارد.
    ok = check("a refused backup leaves no staged photos behind") {
        val newer = File(dir, "newer2.db")
        makeLedger(newer)
        forceVersion(newer, DB_VERSION + 1)
        val photos = File(dir, "photos-newer").apply { mkdirs() }
        File(photos, "p_2.jpg").writeBytes(ByteArray(64) { 3 })
        val arc = File(dir, "newer2.ajb")
        DesktopBackup.writeBackup(newer, arc, photos)
        val stageDir = File(dir, "s9").apply { mkdirs() }
        val e = runCatching { DesktopBackup.stageAndValidate(arc, stageDir) }.exceptionOrNull()
        need(e != null, "a newer backup should have been refused")
        need(!DesktopBackup.stagedPhotos(stageDir).exists(), "staged photos must be cleaned up on rejection")
    } && ok

    // ۱۰ — پشتیبانِ خودکار فقط ۱۴ تای آخرِ خودش را نگه می‌دارد.
    ok = check("auto-backup retention keeps the newest ${DesktopAutoBackup.KEEP} and nothing else is touched") {
        val folder = File(dir, "autos").apply { mkdirs() }
        val day = 24L * 60 * 60 * 1000
        val base = 1_760_000_000_000L
        val names = (0 until 20).map { DesktopAutoBackup.fileName(base + it * day) }
        names.forEach { File(folder, it).writeText("x") }
        val manual = File(folder, DesktopBackup.suggestedName("2026-01-01-0000")).apply { writeText("m") }
        val safety = File(folder, DesktopReset.safetyName()).apply { writeText("s") }
        val pruned = DesktopAutoBackup.prune(folder)
        need(pruned == 6, "six old auto backups should go, $pruned went")
        val left = folder.listFiles()!!.filter(DesktopAutoBackup::isAutoFile).map { it.name }.sorted()
        need(left == names.takeLast(DesktopAutoBackup.KEEP).sorted(), "the newest ${DesktopAutoBackup.KEEP} should stay: $left")
        need(manual.exists() && safety.exists(), "manual and pre-reset backups are not the auto backup's to delete")
    } && ok

    // ۱۱ — «وقتش رسیده؟» و نوشتنِ lastAuto — همان عددی که هشدار می‌خوانَد.
    ok = check("auto backup runs only when 24 hours have passed, then records lastAuto") {
        val settings = FileSettings(File(dir, "prefs11"))
        val folder = File(dir, "auto11")
        val now = 1_760_000_000_000L
        val hour = 60L * 60 * 1000
        val photos = File(dir, "photos11").apply { mkdirs() }
        File(photos, "p.jpg").writeBytes(ByteArray(100) { 9 })
        val backup = { f: File -> runCatching { DesktopBackup.writeBackup(live, f, photos) } }

        BackupPrefs.setLastAuto(settings, now - hour)
        val early = DesktopAutoBackup.runIfDue(settings, now, fallbackDir = folder, backup = backup)
        need(early == null, "one hour after the last one it should not run")
        need(folder.listFiles().isNullOrEmpty(), "nothing should be written when it is not due")

        BackupPrefs.setLastAuto(settings, now - 25 * hour)
        val run = DesktopAutoBackup.runIfDue(settings, now, fallbackDir = folder, backup = backup)!!.getOrThrow()
        need(run.file.isFile && DesktopAutoBackup.isAutoFile(run.file), "the auto backup file is missing")
        need(run.photos == 1, "the auto backup should carry the photo, carried ${run.photos}")
        need(DesktopAutoBackup.verify(run.file) == null, "the written auto backup should verify")
        need(BackupPrefs.lastAuto(settings) == now, "lastAuto should be the run time")
        need(!run.usedFallback, "the default folder is not a fallback")

        // هرگز گرفته نشده (صفر) هم یعنی وقتش است.
        val fresh = FileSettings(File(dir, "prefs11b"))
        need(DesktopAutoBackup.isDue(BackupPrefs.lastAuto(fresh), now), "a ledger never backed up is due")
    } && ok

    // ۱۲ — پوشهٔ انتخاب‌شده در دسترس نیست (iCloudِ وصل‌نشده) → پیش‌فرض، و گفته می‌شود.
    ok = check("an unreachable chosen folder falls back to the default folder") {
        val settings = FileSettings(File(dir, "prefs12"))
        val blocker = File(dir, "not-a-folder").apply { writeText("file, not a folder") }
        DeskPrefs.setBackupFolder(settings, File(blocker, "sub").absolutePath)
        val fallback = File(dir, "auto12")
        val run = DesktopAutoBackup.runIfDue(
            settings, 1_760_000_000_000L, force = true, fallbackDir = fallback,
            backup = { f -> runCatching { DesktopBackup.writeBackup(live, f) } }
        )!!.getOrThrow()
        need(run.usedFallback, "the run should say it fell back")
        need(run.file.parentFile == fallback, "the backup should be in the default folder")
    } && ok

    // ۱۳ — پشتیبانِ خراب پذیرفته نمی‌شود و lastAuto جلو نمی‌رود.
    //
    // کپیِ دفترِ زنده می‌تواند وسطِ یک نوشتن بریده شود. پشتیبانی که «گرفته
    // شد» ولی باز نمی‌شود، بدتر از نبودنش است.
    ok = check("a broken auto backup is caught, deleted, and lastAuto stays") {
        val settings = FileSettings(File(dir, "prefs13"))
        val folder = File(dir, "auto13")
        val torn = File(dir, "torn.db").apply {
            writeBytes(live.readBytes().copyOf((live.length() / 3).toInt()))
        }
        val r = DesktopAutoBackup.runIfDue(
            settings, 1_760_000_000_000L, force = true, fallbackDir = folder,
            backup = { f -> runCatching { DesktopBackup.writeBackup(torn, f) } }
        )!!
        need(r.isFailure, "a torn ledger copy must not pass as a backup")
        need(BackupPrefs.lastAuto(settings) == 0L, "lastAuto must not move on a failed backup")
        need(folder.listFiles()?.none(DesktopAutoBackup::isAutoFile) ?: true, "the broken file must be deleted")
    } && ok

    // ۱۴ — «پاک‌کردنِ کارها و حساب‌ها»: بی پشتیبانِ ایمنی هیچ چیز پاک نمی‌شود؛
    // با آن، کارها پاک و لوگو می‌ماند.
    ok = check("reset clears nothing without its safety backup, and keeps the logo when it runs") {
        val ledger = File(dir, "reset.db")
        val db = openDatabase(ledger)
        val repo = Repo(db)
        val settings = FileSettings(File(dir, "prefs14"))
        val photos = File(dir, "photos14").apply { mkdirs() }
        val logo = File(photos, "logo_1.png").apply { writeBytes(byteArrayOf(1)) }
        val orderPhoto = File(photos, "p_9.jpg").apply { writeBytes(byteArrayOf(2)) }
        CompanyPrefs.saveLogo(settings, logo.name)
        val backups = File(dir, "backups14")
        try {
            runBlocking {
                repo.checkIn("کارمندِ آزمون")
                need(repo.observeAttendance().first().size == 1, "setup: one attendance row")

                val refused = DesktopReset.run(repo, settings, backups, photos) { Result.failure(IllegalStateException("دیسک پر است")) }
                need(refused.isFailure, "reset must refuse when the safety backup fails")
                need(repo.observeAttendance().first().size == 1, "nothing may be cleared without the safety backup")
                need(orderPhoto.exists() && logo.exists(), "no photo may be swept without the safety backup")

                val done = DesktopReset.run(repo, settings, backups, photos) { f ->
                    runCatching { db.checkpoint(); DesktopBackup.writeBackup(ledger, f, photos) }
                }.getOrThrow()
                need(done.safetyBackup.isFile, "the safety backup should exist")
                need(DesktopAutoBackup.verify(done.safetyBackup) == null, "the safety backup should verify")
                need(repo.observeAttendance().first().isEmpty(), "attendance should be cleared")
                need(logo.exists(), "the workshop logo must survive a reset")
                need(!orderPhoto.exists(), "order photos go with the orders")
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
