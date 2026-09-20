package com.afghanjama.ui.vm

/** یک سطرِ تابلو — یک کارِ زیرِ دستِ یک خیاط. */
data class BoardRow(
    val tailor: String,
    val orderCode: String,
    val design: String,
    val qty: Int,
    /** چند روز است دستِ خیاط مانده. */
    val days: Int,
    /** روزِ مانده تا مهلتِ سفارش؛ منفی یعنی گذشته، null یعنی مهلت ندارد. */
    val dueIn: Int?
) {
    /** دیر شده — روی تابلو قرمز می‌شود، مثلِ پروازِ تأخیردار. */
    val late: Boolean get() = (dueIn != null && dueIn < 0)

    /** نزدیکِ مهلت یا زیادی طول کشیده. */
    val warn: Boolean get() = !late && ((dueIn != null && dueIn <= 1) || days >= 3)
}
