package com.afghanjama.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.DB_NAME
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.data.ResetPlan
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.BackupPrefs
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

/** یک جدولِ نشانه، تا بشود دید کدام دفتر جای کدام نشست. */
private fun addProbe(f: File) {
    val c = BundledSQLiteDriver().open(f.absolutePath)
    try {
        c.execSQL("CREATE TABLE IF NOT EXISTS `_swap_probe` (`x` INTEGER NOT NULL)")
    } finally {
        c.close()
    }
}

private fun hasProbe(f: File): Boolean {
    val c = BundledSQLiteDriver().open(f.absolutePath)
    return try {
        val st = c.prepare("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='_swap_probe'")
        try {
            st.step() && st.getLong(0) > 0
        } finally {
            st.close()
        }
    } finally {
        c.close()
    }
}

/**
 * صفحهٔ ریشهٔ یک جدولِ داده را خراب می‌کند و سرِ فایل (صفحهٔ ۱، با
 * `user_version` و طرح) را سالم می‌گذارد — همان کپیِ «سرِ سالم، تنِ خراب».
 */
private fun damageTablePage(f: File) {
    val c = BundledSQLiteDriver().open(f.absolutePath)
    val (pageSize, root) = try {
        val ps = c.prepare("PRAGMA page_size").let { st ->
            try { st.step(); st.getLong(0) } finally { st.close() }
        }
        val rp = c.prepare(
            "SELECT MAX(rootpage) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ).let { st ->
            try { st.step(); st.getLong(0) } finally { st.close() }
        }
        ps to rp
    } finally {
        c.close()
    }
    need(root > 1, "setup: no table page to damage")
    java.io.RandomAccessFile(f, "rw").use { raf ->
        raf.seek((root - 1) * pageSize)
        raf.write(ByteArray(pageSize.toInt()) { 0x5A })
    }
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
        val undo = File(stageDir, DesktopBackup.UNDO_DIR)
        val placed = DesktopBackup.placePhotos(staged, target, undo)
        placed.commit()
        need(placed.count == 2, "two photos should be placed, got ${placed.count}")
        need(File(target, b.name).readBytes().contentEquals(b.readBytes()), "logo bytes changed")
        need(keep.exists(), "a photo the computer already had must stay")
        need(!staged.exists(), "the photo staging folder should be removed")
        need(!undo.exists(), "the undo folder should be removed once the restore is done")
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

    // ۹ب — پشتیبانی که نسخه دارد ولی صفحه‌هایش خراب است رد می‌شود (از ۱.۱۰.۰).
    //
    // تا اینجا فقط `user_version` خوانده می‌شد، که در سرِ فایل است. کپیِ
    // بریده یا دیسکِ خراب سرِ سالم و تنِ خراب دارد؛ پذیرفته می‌شد و جای دفترِ
    // سالم را می‌گرفت.
    ok = check("a backup with a valid version but damaged pages is refused before anything is touched") {
        val broken = File(dir, "damaged.db")
        makeLedger(broken)
        damageTablePage(broken)
        need(ledgerFileVersion(broken) == DB_VERSION, "setup: the header must still read as version $DB_VERSION")
        need(!ledgerFileHealthy(broken), "setup: quick_check must see the damage")
        val arc = File(dir, "damaged.ajb")
        DesktopBackup.writeBackup(broken, arc)

        val stageDir = File(dir, "s9b").apply { mkdirs() }
        val e = runCatching { DesktopBackup.stageAndValidate(arc, stageDir) }.exceptionOrNull()
        need(e != null, "a damaged backup must be refused")
        need(!File(stageDir, "$DB_NAME.restore").exists(), "the staged file must be cleaned up on rejection")

        // و از راهِ کاملِ بازیابی: دفترِ زنده دست نمی‌خورد و بسته هم نمی‌شود.
        val home = File(dir, "restore-damaged").apply { mkdirs() }
        val db = File(home, DB_NAME)
        makeLedger(db)
        val before = db.readBytes()
        var closed = false
        val r = runCatching { DesktopBackup.restoreInto(arc, db, File(home, "photos"), closeLedger = { closed = true }) }
        need(r.isFailure, "restoring a damaged backup must fail")
        need(!closed, "the open ledger must not even be closed for a damaged backup")
        need(db.readBytes().contentEquals(before), "the live ledger must not be touched")
    } && ok

    // ۹ج — بازیابی یا کامل است یا هیچ: عکس‌ها پیش از دفتر می‌نشینند و اگر
    // ننشینند، دفتر دست نمی‌خورد (از ۱.۱۰.۰). تا اینجا عکس‌ها پس از جایگزینیِ
    // دفتر کپی می‌شدند و شکستشان «بازیابی نشد» می‌گفت با دفترِ عوض‌شده.
    val photoArc = File(dir, "probe-with-photos.ajb")
    val incomingPhoto = ByteArray(1500) { (it % 199).toByte() }
    run {
        val probe = File(dir, "probe.db")
        makeLedger(probe)
        addProbe(probe)
        val src = File(dir, "photos-probe").apply { mkdirs() }
        File(src, "p_same.jpg").writeBytes(incomingPhoto)
        File(src, "p_new.jpg").writeBytes(ByteArray(700) { 5 })
        DesktopBackup.writeBackup(probe, photoArc, src)
    }
    /** یک پوشهٔ دفترِ تازه با یک عکسِ هم‌نام، برای هر بند. */
    fun home(name: String): Triple<File, File, File> {
        val h = File(dir, name).apply { mkdirs() }
        val db = File(h, DB_NAME).also { makeLedger(it) }
        val photos = File(h, "order_photos").apply { mkdirs() }
        File(photos, "p_same.jpg").writeBytes(byteArrayOf(9, 9, 9))
        return Triple(h, db, photos)
    }
    fun noLitter(h: File, why: String) {
        listOf("$DB_NAME.restore", "$DB_NAME.prev", "restore-photos").forEach {
            need(!File(h, it).exists(), "$why: «$it» must not be left behind")
        }
        val undo = h.listFiles().orEmpty().filter { it.name.startsWith(DesktopBackup.UNDO_DIR) }
        need(undo.isEmpty(), "$why: the undo folder must not be left behind: ${undo.map { it.name }}")
    }

    ok = check("restore puts the ledger and photos in place together, and leaves no litter") {
        val (h, db, photos) = home("restore-ok")
        var closed = false
        val n = DesktopBackup.restoreInto(photoArc, db, photos, closeLedger = { closed = true })
        need(closed, "the ledger must be closed before it is replaced")
        need(hasProbe(db), "the backup's ledger should now be the live one")
        need(n == 2, "two photos should come back, got $n")
        need(File(photos, "p_same.jpg").readBytes().contentEquals(incomingPhoto), "a same-name photo takes the backup's version")
        need(File(photos, "p_new.jpg").isFile, "the backup's new photo should be there")
        noLitter(h, "after a good restore")
    } && ok

    // پوشهٔ برگشتی که بازیابیِ ناکامِ پیشین جا گذاشته، تنها نسخهٔ عکس‌هایی است
    // که برنگشتند؛ بازیابیِ بعدی نباید پاکش کند.
    ok = check("an undo folder left by an earlier failed restore is never deleted") {
        val (h, db, photos) = home("restore-old-undo")
        val old = File(h, "${DesktopBackup.UNDO_DIR}-1700000000000").apply { mkdirs() }
        val only = File(old, "p_only_copy.jpg").apply { writeBytes(byteArrayOf(4, 4, 4)) }
        DesktopBackup.restoreInto(photoArc, db, photos, closeLedger = {})
        need(only.isFile && only.readBytes().contentEquals(byteArrayOf(4, 4, 4)), "the earlier undo folder must stay as it was")
        val fresh = h.listFiles().orEmpty().filter { it.name.startsWith(DesktopBackup.UNDO_DIR) && it != old }
        need(fresh.isEmpty(), "this restore's own undo folder should be gone: ${fresh.map { it.name }}")
    } && ok

    ok = check("restore whose photos cannot be placed leaves the ledger untouched and says so") {
        val (h, db, _) = home("restore-photo-blocked")
        val blocked = File(h, "photos-is-a-file").apply { writeText("not a folder") }
        val before = db.readBytes()
        var closed = false
        val e = runCatching {
            DesktopBackup.restoreInto(photoArc, db, blocked, closeLedger = { closed = true })
        }.exceptionOrNull()
        need(e != null, "the restore must fail when the photos have nowhere to go")
        need(!closed, "the open ledger must not be closed when the photos failed first")
        need(db.readBytes().contentEquals(before), "the live ledger must not be replaced")
        need(e!!.message.orEmpty().contains("دست نخوردند"), "the message must say nothing changed: ${e.message}")
        noLitter(h, "after a photo failure")
    } && ok

    ok = check("a photo failure half-way puts every earlier photo back") {
        val (h, _, photos) = home("photo-halfway")
        val from = File(h, "restore-photos").apply { mkdirs() }
        File(from, "p_same.jpg").writeBytes(incomingPhoto)
        File(from, "p_zz_new.jpg").writeBytes(ByteArray(64) { 1 })
        val undo = File(h, DesktopBackup.UNDO_DIR)
        var copies = 0
        val e = runCatching {
            DesktopBackup.placePhotos(from, photos, undo) { src, dst ->
                if (++copies == 2) throw java.io.IOException("دیسک پر است")
                src.copyTo(dst, overwrite = true)
            }
        }.exceptionOrNull()
        need(e is DesktopBackup.PhotosNotPlaced, "the failure should come back as PhotosNotPlaced, got $e")
        need((e as DesktopBackup.PhotosNotPlaced).notBack == 0, "every photo should have been put back")
        need(File(photos, "p_same.jpg").readBytes().contentEquals(byteArrayOf(9, 9, 9)), "the computer's own photo must be back")
        need(photos.listFiles()!!.map { it.name }.sorted() == listOf("p_same.jpg"), "no photo from the backup may stay")
        noLitter(h, "after a half-way photo failure")
    } && ok

    ok = check("restore whose ledger swap fails puts the photos back and says the old ledger stayed") {
        val (h, db, photos) = home("restore-swap-fails")
        val before = db.readBytes()
        val e = runCatching {
            DesktopBackup.restoreInto(
                photoArc, db, photos, closeLedger = {},
                swap = { _, _ -> throw java.io.IOException("دیسک پر است") }
            )
        }.exceptionOrNull()
        need(e != null, "the restore must fail when the swap fails")
        need(db.readBytes().contentEquals(before), "the live ledger must stay")
        need(File(photos, "p_same.jpg").readBytes().contentEquals(byteArrayOf(9, 9, 9)), "the computer's own photo must be back")
        need(!File(photos, "p_new.jpg").exists(), "the backup's photo must not stay without its ledger")
        need(e!!.message.orEmpty().contains("دفترِ قبلی سرِ جایش ماند"), "the message must say the old ledger stayed: ${e.message}")
        noLitter(h, "after a failed swap")
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

    // ۱۰ب — پوشهٔ کاربر (iCloud، OneDrive) چیزهای دیگری هم دارد: کپیِ ویندوز و
    // مک، فایلِ نیمه‌نوشته، یادداشت با همان پیشوند، میان‌بُر. هیچ‌کدام مالِ
    // پشتیبانِ خودکار نیست.
    ok = check("auto-backup retention never touches copies, .part files, other files or links") {
        val folder = File(dir, "autos-foreign").apply { mkdirs() }
        val day = 24L * 60 * 60 * 1000
        val base = 1_760_000_000_000L
        val autos = (0 until 3).map { File(folder, DesktopAutoBackup.fileName(base + it * day)).apply { writeText("a") } }
        val stem = DesktopAutoBackup.fileName(base - 10 * day).removeSuffix(".ajb")
        val foreign = listOf(
            "$stem - Copy.ajb", "$stem copy.ajb", "$stem.ajb.part", "KhayatYar-auto-notes.ajb",
            "KhayatYar-auto-20251301-000000.ajb", "${stem}.AJB", "${stem}.ajb.txt"
        ).map { File(folder, it).apply { writeText("user") } }
        val outside = File(dir, "outside-target.ajb").apply { writeText("keep me") }
        val link = File(folder, DesktopAutoBackup.fileName(base - 20 * day))
        val linked = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        val folderLike = File(folder, DesktopAutoBackup.fileName(base - 30 * day)).apply { mkdirs() }

        val pruned = DesktopAutoBackup.prune(folder, keep = 1)
        need(pruned == 2, "only the two older real auto backups should go, $pruned went")
        need(autos.last().exists() && !autos[0].exists() && !autos[1].exists(), "the newest real auto backup should stay")
        foreign.forEach { need(it.exists(), "${it.name} is not an auto backup and must stay") }
        need(folderLike.isDirectory, "a folder is not an auto backup")
        need(outside.readText() == "keep me", "a link's target must never be touched")
        if (linked) need(Files.isSymbolicLink(link.toPath()), "a link named like an auto backup must stay")
    } && ok

    // ۱۰ج — ساعتِ نادرست. کامپیوتری که باتریِ ساعتش تمام شده به سالِ ۲۰۰۰
    // برمی‌گردد؛ پشتیبانِ تازه نباید درست پس از نوشتنش پاک شود.
    ok = check("a wrong clock never deletes the backup just written, nor stalls the next one") {
        val day = 24L * 60 * 60 * 1000
        val hour = 60L * 60 * 1000
        val base = 1_790_000_000_000L
        val backup = { f: File -> runCatching { DesktopBackup.writeBackup(live, f) } }

        // ساعت به ۲۰۰۰ برگشت.
        val folderA = File(dir, "skew-back").apply { mkdirs() }
        val oldOnes = (0 until DesktopAutoBackup.KEEP).map {
            File(folderA, DesktopAutoBackup.fileName(base + it * day)).apply { writeText("a") }
        }
        val settingsA = FileSettings(File(dir, "prefs-skew-a"))
        BackupPrefs.setLastAuto(settingsA, base + 13 * day)
        val y2000 = 946_684_800_000L
        need(DesktopAutoBackup.isDue(BackupPrefs.lastAuto(settingsA), y2000), "a last backup 'in the future' must not stall the next one")
        val runA = DesktopAutoBackup.runIfDue(settingsA, y2000, fallbackDir = folderA, backup = backup)!!.getOrThrow()
        need(runA.file.isFile, "the backup just written must not be pruned")
        need(oldOnes.all { it.exists() }, "backups from before the clock went wrong must stay")
        need(BackupPrefs.lastAuto(settingsA) == y2000, "lastAuto should follow the clock")

        // ساعت ۱۲ ساعت عقب رفت: تازه‌ترین نامِ روی دیسک از «حالا» جلوتر است.
        val folderB = File(dir, "skew-12h").apply { mkdirs() }
        val dayOnes = (0 until DesktopAutoBackup.KEEP).map {
            File(folderB, DesktopAutoBackup.fileName(base + it * day)).apply { writeText("b") }
        }
        val runB = DesktopAutoBackup.runIfDue(
            FileSettings(File(dir, "prefs-skew-b")), base + 13 * day - 12 * hour,
            force = true, fallbackDir = folderB, backup = backup
        )!!.getOrThrow()
        need(runB.file.isFile && runB.pruned == 0, "nothing may be pruned while the clock is behind the newest backup")
        need(dayOnes.all { it.exists() }, "no older backup may go while the clock is behind")

        // ساعت روزی جلو بود: پشتیبانِ «۲۰۹۹» نه شمرده می‌شود نه پاک.
        val folderC = File(dir, "skew-ahead").apply { mkdirs() }
        val future = File(folderC, DesktopAutoBackup.fileName(4_070_908_800_000L)).apply { writeText("f") }
        val normal = (0 until 3).map { File(folderC, DesktopAutoBackup.fileName(base + it * day)).apply { writeText("c") } }
        val runC = DesktopAutoBackup.runIfDue(
            FileSettings(File(dir, "prefs-skew-c")), base + 5 * day,
            force = true, fallbackDir = folderC, backup = backup
        )!!.getOrThrow()
        need(runC.file.isFile, "today's backup must stay even with a 'future' one on disk")
        need(future.exists(), "a backup dated in the future is not pruned")
        need(normal.all { it.exists() }, "under the limit nothing is pruned")
        need(DesktopAutoBackup.prune(folderC, keep = 1, newest = runC.file, now = base + 5 * day) == 3,
            "with the future file set aside, normal pruning resumes")
        need(runC.file.exists() && future.exists(), "the newest and the future one stay")
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

                val refused = DesktopReset.run(repo, settings, UserRole.MANAGER, ResetPlan.CONFIRM_PHRASE, backups, photos) {
                    Result.failure(IllegalStateException("دیسک پر است"))
                }
                need(refused.isFailure, "reset must refuse when the safety backup fails")
                need(repo.observeAttendance().first().size == 1, "nothing may be cleared without the safety backup")
                need(orderPhoto.exists() && logo.exists(), "no photo may be swept without the safety backup")

                val done = DesktopReset.run(repo, settings, UserRole.MANAGER, ResetPlan.CONFIRM_PHRASE, backups, photos) { f ->
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
        val good: (File) -> Result<Unit> = { f -> runCatching { db.checkpoint(); DesktopBackup.writeBackup(ledger, f) } }
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
