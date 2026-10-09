package com.afghanjama.licence

/**
 * کلیدِ عمومیِ لینومیک برای خیاط‌یار — همان فایلِ
 * `Linumic/licensing/public/khayatyar-public.pem`.
 *
 * **فقط کلیدِ عمومی.** با این کلید فقط می‌شود امضا را *سنجید*، نه ساخت.
 * کلیدِ خصوصی روی کمپیوترِ لینومیک می‌مانَد و هرگز به هیچ مخزن و هیچ
 * اپی نمی‌آید.
 *
 * عوض کردنِ این کلید یعنی **همهٔ لایسنس‌های صادرشده باطل می‌شوند**؛ پس
 * فقط وقتی عوض می‌شود که کلیدِ خصوصی لو رفته باشد.
 *
 * آزمون‌ها کلیدِ آزمایشیِ `test-vectors.json` را **تزریق** می‌کنند؛ آن
 * کلید هرگز اینجا نمی‌آید.
 */
object LicenceKeys {

    const val KHAYATYAR_PUBLIC_PEM = """-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmO5oWyI7lvDaIgR1vuJvKgTDz+sM
dAYKM/BTMmoO2/5XG2M/gsAVKur9BcQQIo8CVq0UUO2NZyEIhvi+osMdIg==
-----END PUBLIC KEY-----"""

    /** همان کلید به شکلِ DER، یک بار خوانده‌شده. */
    val production: ByteArray by lazy { Lnm1.pemToDer(KHAYATYAR_PUBLIC_PEM) }
}
