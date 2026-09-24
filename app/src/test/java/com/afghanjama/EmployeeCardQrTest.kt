package com.afghanjama

import com.afghanjama.data.EmployeeCard
import com.afghanjama.util.QrCode
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.decoder.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سازندهٔ QRِ چندسکویی، سنجیده با خودِ ZXing — همان کتابخانه‌ای که
 * اسکنرِ گوشی با آن می‌خوانَد.
 *
 * **چرا رمزگشاییِ واقعی و نه مقایسه با عددِ ثابت.** QRی که یک بیتِ
 * جدولش غلط باشد هنوز شبیهِ QR است؛ فقط وقتی دوربین سرِ درِ کارگاه
 * نخواندش معلوم می‌شود. اینجا ماتریس مستقیم به رمزگشای ZXing داده
 * می‌شود — بی تصویر و بی دوربین — پس هر خطا در جدولِ بلوک‌ها، تصحیحِ
 * خطا، ماسک یا بیت‌های قالب همین‌جا می‌شکند.
 */
class EmployeeCardQrTest {

    private fun decode(q: QrCode): String {
        val m = BitMatrix(q.size)
        for (y in 0 until q.size) for (x in 0 until q.size) if (q.isDark(x, y)) m.set(x, y)
        return Decoder().decode(m).text
    }

    @Test
    fun `every version and level decodes back to the same text`() {
        val rnd = java.util.Random(42)
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789:|-_."
        for (v in 1..40) {
            for (ecc in QrCode.Ecc.entries) {
                val n = 1 + rnd.nextInt(minOf(v * 8, 200))
                val text = (1..n).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
                val q = runCatching {
                    QrCode.encodeText(text, ecc, boostEcc = false, forcedVersion = v)
                }.getOrNull() ?: continue // در این نسخه جا نشد — نسخهٔ بزرگ‌تر می‌سنجدش
                assertEquals("نسخهٔ $v، سطحِ $ecc", text, decode(q))
            }
        }
    }

    @Test
    fun `block tables match zxing for all versions`() {
        val levels = mapOf(
            QrCode.Ecc.LOW to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L,
            QrCode.Ecc.MEDIUM to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
            QrCode.Ecc.QUARTILE to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.Q,
            QrCode.Ecc.HIGH to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
        )
        for (v in 1..40) {
            val zv = Version.getVersionForNumber(v)
            assertTrue(
                "هم‌ترازیِ نسخهٔ $v",
                zv.alignmentPatternCenters.contentEquals(QrCode.alignmentPatternPositions(v))
            )
            for ((ours, theirs) in levels) {
                val blocks = zv.getECBlocksForLevel(theirs)
                val total = blocks.ecBlocks.sumOf { it.count }
                val data = blocks.ecBlocks.sumOf { it.count * it.dataCodewords }
                assertEquals("بلوک‌های نسخهٔ $v/$ours", total, QrCode.NUM_ERROR_CORRECTION_BLOCKS[ours.ordinal][v])
                assertEquals("تصحیحِ نسخهٔ $v/$ours", blocks.ecCodewordsPerBlock, QrCode.ECC_CODEWORDS_PER_BLOCK[ours.ordinal][v])
                assertEquals("ظرفیتِ نسخهٔ $v/$ours", data, QrCode.numDataCodewords(v, ours))
            }
        }
    }

    @Test
    fun `a printed card scans back to the same employee`() {
        for (kind in EmployeeCard.Kind.entries) {
            for (id in listOf(1L, 9L, 57L, 4_096L, 1_000_003L)) {
                val code = EmployeeCard.encode(kind, id)
                val scanned = decode(QrCode.encodeText(code))
                assertEquals(EmployeeCard.Ref(kind, id), EmployeeCard.parse(scanned))
            }
        }
    }

    /**
     * اسکنرِ USB روی ویندوزِ فارسی: حرف‌ها با چیدمانِ فارسی تایپ می‌شوند
     * و رقم‌ها گاهی فارسی — کارت باید همچنان خوانده شود.
     */
    @Test
    fun `a keyboard-wedge scanner on a persian layout still reads the card`() {
        val code = EmployeeCard.encode(EmployeeCard.Kind.STAFF, 12)
        val typed = "نغ" + code.drop(2).map { '۰' + (it - '0') }.joinToString("")
        assertEquals(EmployeeCard.Ref(EmployeeCard.Kind.STAFF, 12), EmployeeCard.parse(typed))
    }

    @Test
    fun `a misread digit or a foreign code is never someone else`() {
        val body = EmployeeCard.encode(EmployeeCard.Kind.TAILOR, 3_517).drop(2)
        for (i in body.indices) for (d in '0'..'9') {
            if (d == body[i]) continue
            val bad = "ky" + body.substring(0, i) + d + body.substring(i + 1)
            assertNull("«$bad» نباید پذیرفته شود", EmployeeCard.parse(bad))
        }
        for (foreign in listOf("", "AJ-2026-000012", "ABC-XYZ", "0791234567", "ky", "ky0100")) {
            assertNull("«$foreign» کارت نیست", EmployeeCard.parse(foreign))
        }
    }

    @Test
    fun `a second scan within a minute is not a check-out`() {
        val now = 50_000_000L
        assertEquals(EmployeeCard.Action.CHECK_IN, EmployeeCard.decide(false, null, now))
        assertEquals(EmployeeCard.Action.TOO_SOON, EmployeeCard.decide(true, now - 3_000, now))
        assertEquals(EmployeeCard.Action.CHECK_OUT, EmployeeCard.decide(true, now - EmployeeCard.MIN_GAP_MS, now))
        // ساعتِ گوشی عقب رفته: ثبتِ «آینده» نباید کارمند را قفل کند.
        assertEquals(EmployeeCard.Action.CHECK_IN, EmployeeCard.decide(false, now + 90_000, now))
    }
}
