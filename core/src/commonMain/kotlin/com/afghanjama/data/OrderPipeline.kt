package com.afghanjama.data

import com.afghanjama.data.entities.OrderStatus

/**
 * چرخهٔ یک سفارش در پنج قدمی که مشتری و کارگاه با آن حرف می‌زنند:
 * ثبت ← برش ← دوخت ← نظارت ← تحویل.
 *
 * **چرا جداست از [OrderStatus].** آن enum هشت حالت دارد چون کار و انبار
 * به جزئیاتِ بیشتری نیاز دارند: `CUT_DONE` می‌گوید برش تمام شده ولی دوخت
 * شروع نشده، و `SALES`/`STORED` مرزِ محصولِ آمادهٔ فروش را نگه می‌دارند.
 * اما صفحهٔ سفارش نباید هشت نقطه نشان دهد؛ صاحبِ سفارش کارش را در پنج
 * قدم می‌فهمد. این شیء آن هشت حالت را به همین پنج قدم می‌نگارد، **در یک
 * جا**، تا هر صفحه‌ای که «کجای کار است» را نشان می‌دهد یک داستان بگوید
 * نه چند نگاشتِ پراکنده که کم‌کم از هم جدا بیفتند.
 *
 * هیچ چیزِ اندرویدی و هیچ دیتابیسی اینجا نیست، پس CI می‌سنجدش.
 */
object OrderPipeline {

    /** یک قدم از چرخه: کلیدِ پایدار (برای کد) و برچسبِ فارسی (برای چشم). */
    data class Stage(val key: String, val label: String)

    /** حالتِ یک قدم نسبت به جایی که کار همین حالا هست. */
    enum class State { DONE, CURRENT, UPCOMING }

    /** یک قدم همراهِ حالتش، برای کشیدنِ نوار. */
    data class Step(val stage: Stage, val state: State)

    val STAGES: List<Stage> = listOf(
        Stage("INTAKE", "ثبت"),
        Stage("CUT", "برش"),
        Stage("SEW", "دوخت"),
        Stage("REVIEW", "نظارت"),
        Stage("DELIVER", "تحویل"),
    )

    /**
     * شمارهٔ قدمِ جاری برای یک وضعیت (۰ تا ۴)، یا ۱- اگر ناشناخته باشد.
     *
     * `CUT_DONE` هنوز در قدمِ برش است (تمام‌شده، اما هنوز دوخت نشده).
     * `SALES`/`STORED`/`SENT` همه در قدمِ تحویل‌اند: کالا ساخته شده و
     * دارد از کارگاه بیرون می‌رود، چه به مشتری برسد چه فروخته شود.
     */
    fun indexOf(status: String): Int = when (status) {
        "NEW", OrderStatus.IN_STOCK.name -> 0
        OrderStatus.CUTTING.name, OrderStatus.CUT_DONE.name -> 1
        OrderStatus.SEWING.name -> 2
        OrderStatus.REVIEW.name -> 3
        OrderStatus.SALES.name, OrderStatus.STORED.name, OrderStatus.SENT.name -> 4
        else -> -1
    }

    /**
     * آیا کار واقعاً از کارگاه بیرون رفته.
     *
     * `SENT` به مشتری رسیده و `SALES` فروخته شده — هر دو پایانِ چرخه‌اند.
     * اما `STORED` «آمادهٔ تحویل» است نه «تحویل‌شده»: محصول ساخته شده و در
     * انبار است، ولی هنوز دستِ کسی نرسیده. پس قدمِ تحویل برایش «اکنون»
     * می‌مانَد نه «انجام‌شده»، وگرنه نوار دروغ می‌گوید کار تمام است.
     */
    fun isComplete(status: String): Boolean =
        status == OrderStatus.SENT.name || status == OrderStatus.SALES.name

    /**
     * پنج قدم با حالتِ هرکدام برای وضعیتِ داده‌شده.
     *
     * قدم‌های پیش از قدمِ جاری «انجام‌شده»اند، قدمِ جاری «اکنون»، و بقیه
     * «پیشِ‌رو». اگر کار کامل شده باشد، قدمِ آخر هم انجام‌شده است نه اکنون.
     * وضعیتِ ناشناخته همه را پیشِ‌رو نشان می‌دهد تا هیچ قدمی به‌دروغ سبز
     * نشود.
     */
    fun steps(status: String): List<Step> {
        val cur = indexOf(status)
        val complete = isComplete(status)
        return STAGES.mapIndexed { i, stage ->
            val state = when {
                cur < 0 -> State.UPCOMING
                i < cur -> State.DONE
                i == cur -> if (complete) State.DONE else State.CURRENT
                else -> State.UPCOMING
            }
            Step(stage, state)
        }
    }

    /**
     * شمارِ سفارش‌های **باز** در هر کدام از پنج قدم — برای نوارِ خطِ تولیدِ
     * داشبورد.
     *
     * سفارشِ تمام‌شده (`SENT`، `SALES`) شمرده نمی‌شود، وگرنه قدمِ «تحویل» همهٔ
     * تاریخچهٔ کارگاه را می‌شمرد. `STORED` (ساخته‌شده و منتظرِ مشتری) در قدمِ
     * تحویل می‌ماند — کارِ کارگاه با آن هنوز تمام نیست. وضعیتِ ناشناخته جایی
     * شمرده نمی‌شود.
     */
    fun counts(statuses: List<String>): List<Int> {
        val out = IntArray(STAGES.size)
        for (s in statuses) {
            if (isComplete(s)) continue
            val i = indexOf(s)
            if (i >= 0) out[i]++
        }
        return out.toList()
    }
}
