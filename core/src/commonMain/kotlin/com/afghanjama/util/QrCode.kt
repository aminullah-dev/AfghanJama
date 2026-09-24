package com.afghanjama.util

/**
 * سازندهٔ QR — کاتلینِ خالص، برای هر سه سکو.
 *
 * **چرا دوباره نوشته شد و از ZXing گرفته نشد.** `QrGen`ِ اندروید روی
 * ZXing و `android.graphics.Bitmap` نشسته. ZXing جاواست و روی iOS نیست،
 * و `Bitmap` روی ویندوز هم نیست. نتیجه‌اش این بود که کارتِ کارمند —
 * که مدیر باید پشتِ کمپیوترِ کارگاه چاپش کند — فقط روی گوشی ساخته
 * می‌شد. این فایل ماتریسِ خانه‌ها را می‌سازد و بس؛ کشیدنش با Compose
 * (`QrImage`) یا با برگهٔ چاپی (`SheetBuilder.qr`) است و هیچ‌کدام به
 * سکو گره نمی‌خورند.
 *
 * الگوریتم همان استانداردِ ISO/IEC 18004 است، به روشِ پیاده‌سازیِ
 * مرجعِ Nayuki (MIT): حالتِ بایت، نسخهٔ ۱ تا ۴۰، و انتخابِ ماسک با
 * جریمه. فقط حالتِ بایت دارد چون هر چیزی که این اپ کُد می‌کند کوتاه
 * است و حالت‌های عددی/حرفی فقط چند خانه صرفه‌جویی می‌کنند.
 *
 * **سنجش:** خروجی خانه‌به‌خانه با یک کتابخانهٔ مستقل مقایسه شده، برای
 * هر چهار سطحِ تصحیح و هر هشت ماسک.
 */
class QrCode private constructor(
    /** شمارهٔ نسخه، ۱ تا ۴۰. */
    val version: Int,
    val ecc: Ecc,
    dataCodewords: ByteArray,
    forcedMask: Int,
) {
    /** سطحِ تصحیحِ خطا — هرچه بالاتر، QR بزرگ‌تر ولی آسیب‌پذیری کمتر. */
    enum class Ecc(internal val ordinalBits: Int, internal val formatBits: Int) {
        LOW(0, 1), MEDIUM(1, 0), QUARTILE(2, 3), HIGH(3, 2)
    }

    /** طولِ هر ضلع بر حسبِ خانه (۲۱ تا ۱۷۷). */
    val size: Int = version * 4 + 17

    private val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }

    /** ماسکی که در عمل به‌کار رفت (۰ تا ۷). */
    val mask: Int

    init {
        require(version in MIN_VERSION..MAX_VERSION)
        require(forcedMask in -1..7)
        drawFunctionPatterns()
        drawCodewords(addEccAndInterleave(dataCodewords))

        var chosen = forcedMask
        if (chosen == -1) {
            var minPenalty = Int.MAX_VALUE
            for (m in 0..7) {
                applyMask(m)
                drawFormatBits(m)
                val p = penaltyScore()
                if (p < minPenalty) {
                    chosen = m
                    minPenalty = p
                }
                applyMask(m) // XOR — دوباره زدنش برمی‌گرداند
            }
        }
        mask = chosen
        applyMask(mask)
        drawFormatBits(mask)
    }

    /** خانهٔ (x, y) تیره است؟ بیرون از ماتریس همیشه روشن. */
    fun isDark(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && modules[y][x]

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 40

        /**
         * متن را در کوچک‌ترین نسخه‌ای که جا شود کد می‌کند.
         *
         * [boostEcc]: اگر نسخهٔ انتخاب‌شده جای بیشتری دارد، سطحِ تصحیح را
         * بالا می‌برد — کارتی که در جیب تا خورده یا خط افتاده، با
         * تصحیحِ بالاتر هنوز خوانده می‌شود و این بالا بردن هیچ خانه‌ای
         * به QR اضافه نمی‌کند.
         *
         * @throws IllegalArgumentException اگر متن در نسخهٔ ۴۰ هم جا نشود.
         */
        fun encodeText(
            text: String,
            ecc: Ecc = Ecc.MEDIUM,
            boostEcc: Boolean = true,
            forcedVersion: Int = -1,
            forcedMask: Int = -1,
        ): QrCode = encodeBytes(text.encodeToByteArray(), ecc, boostEcc, forcedVersion, forcedMask)

        fun encodeBytes(
            data: ByteArray,
            ecc: Ecc = Ecc.MEDIUM,
            boostEcc: Boolean = true,
            forcedVersion: Int = -1,
            forcedMask: Int = -1,
        ): QrCode {
            var level = ecc
            var version = if (forcedVersion == -1) MIN_VERSION else forcedVersion
            val maxVersion = if (forcedVersion == -1) MAX_VERSION else forcedVersion
            var usedBits: Int
            while (true) {
                usedBits = byteSegmentBits(data.size, version)
                if (usedBits <= numDataCodewords(version, level) * 8) break
                require(version < maxVersion) { "متن برای QR بیش از حد بلند است" }
                version++
            }
            if (boostEcc) {
                for (e in listOf(Ecc.MEDIUM, Ecc.QUARTILE, Ecc.HIGH)) {
                    if (e.ordinalBits > level.ordinalBits &&
                        usedBits <= numDataCodewords(version, e) * 8
                    ) level = e
                }
            }

            val bb = BitBuffer()
            bb.append(0x4, 4) // حالتِ بایت
            bb.append(data.size, charCountBits(version))
            for (b in data) bb.append(b.toInt() and 0xFF, 8)

            val capacity = numDataCodewords(version, level) * 8
            bb.append(0, minOf(4, capacity - bb.size))
            bb.append(0, (8 - bb.size % 8) % 8)
            var pad = 0xEC
            while (bb.size < capacity) {
                bb.append(pad, 8)
                pad = pad xor 0xEC xor 0x11
            }

            val codewords = ByteArray(bb.size / 8)
            for (i in 0 until bb.size) {
                if (bb[i]) {
                    val idx = i ushr 3
                    codewords[idx] = (codewords[idx].toInt() or (1 shl (7 - (i and 7)))).toByte()
                }
            }
            return QrCode(version, level, codewords, forcedMask)
        }

        private fun charCountBits(version: Int): Int = if (version <= 9) 8 else 16

        private fun byteSegmentBits(bytes: Int, version: Int): Int {
            val ccBits = charCountBits(version)
            if (bytes >= (1 shl ccBits)) return Int.MAX_VALUE
            return 4 + ccBits + bytes * 8
        }

        fun numRawDataModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        /** ظرفیتِ داده بر حسبِ بایت — عمومی تا آزمون با جدولِ ZXing بسنجدش. */
        fun numDataCodewords(ver: Int, ecc: Ecc): Int =
            numRawDataModules(ver) / 8 -
                ECC_CODEWORDS_PER_BLOCK[ecc.ordinalBits][ver] *
                NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinalBits][ver]

        /** جایگاهِ مرکزِ الگوهای هم‌ترازی در هر محور. */
        fun alignmentPatternPositions(ver: Int): IntArray {
            if (ver == 1) return IntArray(0)
            val numAlign = ver / 7 + 2
            val step = (ver * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
            val result = IntArray(numAlign)
            result[0] = 6
            var pos = ver * 4 + 17 - 7
            for (i in numAlign - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        // ردیف = سطحِ تصحیح (L, M, Q, H)، ستون = نسخه (۰ بی‌معناست).
        val ECC_CODEWORDS_PER_BLOCK: Array<IntArray> = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        )

        val NUM_ERROR_CORRECTION_BLOCKS: Array<IntArray> = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
        )

        private const val PENALTY_N1 = 3
        private const val PENALTY_N2 = 3
        private const val PENALTY_N3 = 40
        private const val PENALTY_N4 = 10

        private fun bit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

        private fun rsMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun rsDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in result.indices) {
                    result[j] = rsMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = rsMultiply(root, 0x02)
            }
            return result
        }

        private fun rsRemainder(data: ByteArray, divisor: IntArray): IntArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                for (i in 0 until result.size - 1) result[i] = result[i + 1]
                result[result.size - 1] = 0
                for (i in result.indices) result[i] = result[i] xor rsMultiply(divisor[i], factor)
            }
            return result
        }
    }

    // ---------------- الگوهای ثابت ----------------

    private fun setFunction(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            setFunction(6, i, i % 2 == 0)
            setFunction(i, 6, i % 2 == 0)
        }
        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        val pos = alignmentPatternPositions(version)
        val n = pos.size
        for (i in 0 until n) {
            for (j in 0 until n) {
                val corner = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)
                if (!corner) drawAlignment(pos[i], pos[j])
            }
        }
        drawFormatBits(0) // جای خانه‌ها را رزرو می‌کند؛ بعداً درست نوشته می‌شود
        drawVersion()
    }

    private fun drawFinder(x: Int, y: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until size && yy in 0 until size) {
                    setFunction(xx, yy, dist != 2 && dist != 4)
                }
            }
        }
    }

    private fun drawAlignment(x: Int, y: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                setFunction(x + dx, y + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
            }
        }
    }

    private fun drawFormatBits(msk: Int) {
        val data = (ecc.formatBits shl 3) or msk
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412

        for (i in 0..5) setFunction(8, i, bit(bits, i))
        setFunction(8, 7, bit(bits, 6))
        setFunction(8, 8, bit(bits, 7))
        setFunction(7, 8, bit(bits, 8))
        for (i in 9 until 15) setFunction(14 - i, 8, bit(bits, i))

        for (i in 0 until 8) setFunction(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) setFunction(8, size - 15 + i, bit(bits, i))
        setFunction(8, size - 8, true)
    }

    private fun drawVersion() {
        if (version < 7) return
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val b = bit(bits, i)
            val a = size - 11 + i % 3
            val c = i / 3
            setFunction(a, c, b)
            setFunction(c, a, b)
        }
    }

    // ---------------- داده ----------------

    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val e = ecc.ordinalBits
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[e][version]
        val blockEccLen = ECC_CODEWORDS_PER_BLOCK[e][version]
        val rawCodewords = numRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val divisor = rsDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>(numBlocks)
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + (if (i < numShortBlocks) 0 else 1)
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val block = dat.copyOf(shortBlockLen + 1)
            val rem = rsRemainder(dat, divisor)
            for (j in rem.indices) block[block.size - blockEccLen + j] = rem[j].toByte()
            blocks += block
        }

        val result = ByteArray(rawCodewords)
        var r = 0
        for (i in 0 until blocks[0].size) {
            for (j in blocks.indices) {
                // بلوکِ کوتاه یک بایتِ خالی در این جایگاه دارد
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[r++] = blocks[j][i]
                }
            }
        }
        return result
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = bit(data[i ushr 3].toInt() and 0xFF, 7 - (i and 7))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(msk: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val invert = when (msk) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
            }
        }
    }

    // ---------------- جریمهٔ ماسک ----------------

    private fun penaltyScore(): Int {
        var result = 0

        // ۱ و ۳: رشته‌های هم‌رنگ و الگوی شبیهِ نشانگر، افقی و عمودی
        for (horizontal in listOf(true, false)) {
            for (a in 0 until size) {
                var runColor = false
                var runLen = 0
                for (b in 0 until size) {
                    val c = if (horizontal) modules[a][b] else modules[b][a]
                    if (c == runColor) {
                        runLen++
                        if (runLen == 5) result += PENALTY_N1
                        else if (runLen > 5) result++
                    } else {
                        runColor = c
                        runLen = 1
                    }
                }
            }
            for (a in 0 until size) {
                for (b in 0..size - 7) {
                    if (finderLike(horizontal, a, b)) result += PENALTY_N3
                }
            }
        }

        // ۲: بلوک‌های ۲×۲ هم‌رنگ
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += PENALTY_N2
                }
            }
        }

        // ۴: توازنِ تیره و روشن
        var dark = 0
        for (row in modules) for (c in row) if (c) dark++
        val total = size * size
        val k = ((kotlin.math.abs(dark * 20L - total * 10L) + total - 1) / total).toInt() - 1
        result += k * PENALTY_N4
        return result
    }

    /** الگوی ۱:۱:۳:۱:۱ با چهار خانهٔ روشن در یکی از دو سو. */
    private fun finderLike(horizontal: Boolean, a: Int, b: Int): Boolean {
        fun at(i: Int): Boolean {
            if (i < 0 || i >= size) return false
            return if (horizontal) modules[a][i] else modules[i][a]
        }
        val core = at(b) && !at(b + 1) && at(b + 2) && at(b + 3) && at(b + 4) && !at(b + 5) && at(b + 6)
        if (!core) return false
        val before = (1..4).all { !at(b - it) }
        val after = (1..4).all { !at(b + 6 + it) }
        return before || after
    }

    /** بافرِ بیت؛ فقط به اندازهٔ نیازِ این فایل. */
    private class BitBuffer {
        private val bits = ArrayList<Boolean>()
        val size: Int get() = bits.size
        operator fun get(i: Int): Boolean = bits[i]
        fun append(value: Int, len: Int) {
            require(len in 0..31 && (value ushr len) == 0)
            for (i in len - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }
    }
}
