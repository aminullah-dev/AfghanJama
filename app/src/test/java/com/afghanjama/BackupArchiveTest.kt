package com.afghanjama

import com.afghanjama.util.BackupArchive
import com.afghanjama.util.BackupArchive.Format
import com.afghanjama.util.BackupArchive.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * پشتیبانِ کامل — تشخیصِ قالب، سنجشِ پیش از بازیابی، و نامِ امنِ عکس.
 *
 * بازیابیِ فایلِ خراب یا جلوتر، با `fallbackToDestructiveMigration`، کلِ
 * داده را پاک می‌کند؛ و نامِ ورودیِ zip اگر `../` داشته باشد بیرون از
 * پوشهٔ اپ می‌نویسد (Zip Slip). این‌ها باید ثابت بمانند.
 */
class BackupArchiveTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `zip magic is detected`() {
        assertEquals(Format.ZIP, BackupArchive.detect(bytes('P'.code, 'K'.code, 3, 4, 0, 0)))
    }

    @Test
    fun `raw SQLite header is detected so old backups still restore`() {
        val head = "SQLite format 3\u0000".encodeToByteArray()
        assertEquals(BackupArchive.HEAD_BYTES, head.size)
        assertEquals(Format.RAW_DB, BackupArchive.detect(head))
    }

    @Test
    fun `short, empty and foreign heads are unknown`() {
        assertEquals(Format.UNKNOWN, BackupArchive.detect(ByteArray(0)))
        assertEquals(Format.UNKNOWN, BackupArchive.detect(bytes('P'.code, 'K'.code, 3)))
        // PK ولی نه سرِ ورودیِ محلی (مثلاً zip خالی: PK 5 6)
        assertEquals(Format.UNKNOWN, BackupArchive.detect(bytes('P'.code, 'K'.code, 5, 6)))
        assertEquals(Format.UNKNOWN, BackupArchive.detect("SQLite format".encodeToByteArray()))
        assertEquals(Format.UNKNOWN, BackupArchive.detect("not a backup at all".encodeToByteArray()))
    }

    @Test
    fun `verdict is OK for a healthy file at the current or an older schema`() {
        assertEquals(Verdict.OK, BackupArchive.verdict(true, true, 20, 20))
        assertEquals(Verdict.OK, BackupArchive.verdict(true, true, 3, 20))
    }

    @Test
    fun `verdict is CORRUPT when unopenable, failing integrity, or never a Room file`() {
        assertEquals(Verdict.CORRUPT, BackupArchive.verdict(false, true, 20, 20))
        assertEquals(Verdict.CORRUPT, BackupArchive.verdict(true, false, 20, 20))
        assertEquals(Verdict.CORRUPT, BackupArchive.verdict(true, true, 0, 20))
        assertEquals(Verdict.CORRUPT, BackupArchive.verdict(true, true, -1, 20))
    }

    @Test
    fun `corrupt wins over too new`() {
        assertEquals(Verdict.CORRUPT, BackupArchive.verdict(true, false, 99, 20))
    }

    @Test
    fun `verdict is TOO_NEW for a file from a newer app`() {
        assertEquals(Verdict.TOO_NEW, BackupArchive.verdict(true, true, 21, 20))
    }

    @Test
    fun `messages are empty for OK and promise untouched data otherwise`() {
        assertEquals("", BackupArchive.verdictMessage(Verdict.OK, 20, 20))
        assertTrue("دست‌نخورده" in BackupArchive.verdictMessage(Verdict.CORRUPT, 0, 20))
        val tooNew = BackupArchive.verdictMessage(Verdict.TOO_NEW, 25, 20)
        assertTrue("25" in tooNew && "20" in tooNew)
        assertTrue("دست‌نخورده" in tooNew)
    }

    @Test
    fun `safePhotoName accepts a bare file name under photos`() {
        assertEquals("a1.jpg", BackupArchive.safePhotoName("photos/a1.jpg"))
        assertEquals("..hidden", BackupArchive.safePhotoName("photos/..hidden"))
    }

    @Test
    fun `safePhotoName rejects zip slip, nesting and other entries`() {
        assertNull(BackupArchive.safePhotoName("photos/../evil"))
        assertNull(BackupArchive.safePhotoName("photos/.."))
        assertNull(BackupArchive.safePhotoName("photos/."))
        assertNull(BackupArchive.safePhotoName("photos/sub/a.jpg"))
        assertNull(BackupArchive.safePhotoName("photos/..\\evil"))
        assertNull(BackupArchive.safePhotoName("photos//etc/passwd"))
        assertNull(BackupArchive.safePhotoName("photos/"))
        assertNull(BackupArchive.safePhotoName("photos/   "))
        assertNull(BackupArchive.safePhotoName("/photos/a.jpg"))
        assertNull(BackupArchive.safePhotoName("../photos/a.jpg"))
        assertNull(BackupArchive.safePhotoName(BackupArchive.ENTRY_DB))
        assertNull(BackupArchive.safePhotoName(BackupArchive.ENTRY_META))
    }
}
