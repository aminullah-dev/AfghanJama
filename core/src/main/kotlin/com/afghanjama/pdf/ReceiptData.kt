package com.afghanjama.pdf

/** دادهٔ یک رسیدِ پول — پرداخت یا دریافت. */
data class ReceiptData(
    val title: String,
    val number: String,
    val at: Long,
    /** کسی که پول را داده. */
    val payer: String,
    /** کسی که پول را گرفته. */
    val payee: String,
    val amount: Long,
    /** ماندهٔ حساب بعد از این رسید؛ صفر یعنی تسویه. */
    val remainingDue: Long = 0,
    val note: String = ""
)
