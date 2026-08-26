package com.afghanjama.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.util.BackupArchive
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

    dir.deleteRecursively()
    println("=".repeat(52))
    println(if (ok) "backup path verified" else "backup smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
