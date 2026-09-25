package com.afghanjama.data

import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.util.NameMatch

/**
 * نگهبانِ خطا — «یک لحظه، دوباره نگاه کنید» پیش از ثبتِ پول.
 *
 * **چرا پیش از ثبت و نه بعدش.** اپ برای هر پرداخت هم‌زمان صندوق، دفتر،
 * ژورنال و رسید را می‌نویسد. سندِ اشتباه بعداً با سندِ برگشتی درست
 * می‌شود، ولی تا آن موقع مانده‌ها غلط‌اند و رسیدِ غلط دستِ کسی رفته.
 * یک مکث پیش از ثبت ارزان‌ترین جای گرفتنِ اشتباه است.
 *
 * **سه اشتباهِ واقعیِ پیشخوان:**
 *
 * - **تکرار** — همان مبلغ برای همان نفر به‌تازگی ثبت شده. معمولاً یعنی
 *   یک بار روی گوشی و یک بار روی پی‌سی، یا «ثبت نشد؟» و دوباره.
 * - **یک صفرِ اضافه یا کم** — ۱۵٬۰۰۰ به‌جای ۱٬۵۰۰. مبلغ چند برابرِ معمول
 *   است و با برداشتنِ یک صفر درست به معمول می‌رسد.
 * - **مبلغِ غیرعادی** — چند برابرِ معمولِ همین نفر (یا همین نوع)، بی
 *   توضیحِ ساده‌ای مثلِ صفرِ اضافه.
 *
 * **هشدار است، نه منع.** کارگاه گاهی واقعاً پولِ بزرگ جابه‌جا می‌کند؛
 * نگهبان فقط می‌پرسد، تصمیم با کاربر است.
 *
 * «معمول» میانه است نه میانگین، و بی نمونهٔ کافی هیچ ادعایی نمی‌کند —
 * همان قاعدهٔ [PriceAdvisor].
 */
object EntryGuard {

    /** «به‌تازگی» برای تکرار — یک روز. */
    const val DUP_WINDOW_MS = 24L * 60 * 60 * 1000

    /** از چند برابرِ معمول، مبلغ غیرعادی است. */
    const val FACTOR = 5L

    /** معمولِ یک نفر از کمتر از این تعداد ساخته نمی‌شود. */
    const val MIN_PARTY = 3

    /** معمولِ یک نوع (همهٔ کارمندان، همهٔ مشتریان…) از کمتر از این نه. */
    const val MIN_KIND = 5

    /** چند سطرِ آخر برای «معمول». */
    private const val RECENT = 20

    /**
     * سطرِ دفتر که **جابه‌جاییِ پول** است — پرداخت یا دریافت — نه فروشِ
     * نسیه یا کارمزدِ ثبت‌شده. «معمولِ پرداخت به احمد» باید از پرداخت‌ها
     * ساخته شود؛ اگر بدهیِ فروش هم در آن می‌آمد، معمول بی‌معنا می‌شد.
     */
    fun isCashMove(refType: String): Boolean =
        refType in CASH_MOVES || refType.startsWith("CUSTOMER_")

    private val CASH_MOVES = setOf("MANUAL", "WAGE_PAID", "SUPPLIER_PAYMENT", "SALARY_PAID")

    /**
     * پرداخت‌ها (یا دریافت‌های) گذشته به طرف‌های نوعِ [type] — از دفتر.
     * صفحهٔ «پرداخت/دریافت» و «سندِ دستی»ِ دفتر کل هر دو از همین
     * می‌خوانند، تا یکی هشدار بدهد و دیگری ساکت نماند.
     */
    fun pastMoves(rows: List<LedgerEntry>, type: String, isPayment: Boolean): List<Past> =
        rows.filter {
            it.partyType == type && isCashMove(it.refType) &&
                (if (isPayment) it.debit > 0 else it.credit > 0)
        }.map { Past(it.partyName, if (isPayment) it.debit else it.credit, it.at) }

    /** یک ثبتِ گذشته از همین جنس (همان جهت، همان نوعِ طرف). */
    data class Past(val party: String, val amount: Long, val at: Long)

    enum class Kind { DUPLICATE, EXTRA_ZERO, MISSING_ZERO, UNUSUAL }

    /** [suggested] مبلغی که احتمالاً منظور بوده — برای دکمهٔ «همین را بگذار». */
    data class Warning(val kind: Kind, val text: String, val suggested: Long? = null)

    /**
     * @param past ثبت‌های گذشته از همین جنس — صداکننده فیلتر می‌کند.
     * @param money قالبِ مبلغ برای متن (مثلاً `afn`)؛ و [digits] قالبِ
     * عددِ ساده (ارقامِ فارسی). هر دو از بیرون، تا این قاعده به لایهٔ
     * نمایش بسته نباشد.
     */
    fun check(
        party: String,
        amount: Long,
        past: List<Past>,
        now: Long,
        money: (Long) -> String,
        digits: (Long) -> String = { it.toString() },
        /** برچسبِ «معمول» وقتی از خودِ همین طرف (یا کالا) ساخته شده. */
        ownLabel: String = "معمولِ همین نفر",
        /** آنچه تکرار شده — «همین مبلغ»، «فاکتوری با همین جمع». */
        what: String = "همین مبلغ",
    ): List<Warning> {
        if (amount <= 0L) return emptyList()
        val who = party.trim()
        val mine = past.filter { NameMatch.same(it.party, who) }
        val out = ArrayList<Warning>(2)

        mine.filter { it.amount == amount && now - it.at in 0..DUP_WINDOW_MS }
            .maxByOrNull { it.at }
            ?.let { dup ->
                out += Warning(
                    Kind.DUPLICATE,
                    "$what (${money(amount)}) برای «$who» ${ago(now - dup.at, digits)} هم ثبت شده."
                )
            }

        val (m, basis) = usual(mine, past, ownLabel) ?: return out
        when {
            amount >= FACTOR * m -> {
                val tenth = amount / 10
                out += if (amount % 10 == 0L && tenth in (m / 2)..(m * 2)) {
                    Warning(
                        Kind.EXTRA_ZERO,
                        "شاید یک صفر اضافه است: ${money(tenth)}؟ $basis ${money(m)} است.",
                        suggested = tenth
                    )
                } else {
                    Warning(
                        Kind.UNUSUAL,
                        "این مبلغ حدودِ ${digits(amount / m)} برابرِ معمول است — $basis ${money(m)}."
                    )
                }
            }
            amount * FACTOR <= m && amount * 10 in (m / 2)..(m * 2) ->
                out += Warning(
                    Kind.MISSING_ZERO,
                    "شاید یک صفر کم است: ${money(amount * 10)}؟ $basis ${money(m)} است.",
                    suggested = amount * 10
                )
        }
        return out
    }

    /** معمولِ همین نفر اگر نمونهٔ کافی دارد، وگرنه معمولِ همین نوع. */
    private fun usual(mine: List<Past>, all: List<Past>, ownLabel: String): Pair<Long, String>? {
        fun recentMedian(xs: List<Past>) =
            PriceAdvisor.median(xs.sortedByDescending { it.at }.take(RECENT).map { it.amount })
        if (mine.size >= MIN_PARTY) recentMedian(mine)?.takeIf { it > 0 }?.let { return it to ownLabel }
        if (all.size >= MIN_KIND) recentMedian(all)?.takeIf { it > 0 }?.let { return it to "معمولِ همین نوع" }
        return null
    }

    private fun ago(ms: Long, digits: (Long) -> String): String {
        val min = ms / 60_000L
        return when {
            min < 2 -> "همین حالا"
            min < 60 -> "${digits(min)} دقیقه پیش"
            else -> "${digits(min / 60)} ساعت پیش"
        }
    }
}
