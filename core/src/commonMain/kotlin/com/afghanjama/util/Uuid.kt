package com.afghanjama.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * شناسهٔ یکتا — **بی وابستگی به JVM.**
 *
 * تا دیروز همه‌جای `:core` نوشته بود `java.util.UUID`. روی اندروید و
 * ویندوز مشکلی نبود، ولی iOS جاوا ندارد و همان یک نام ۲۲ فایل را از
 * کدِ مشترک بیرون می‌انداخت — از `Order` و `Transaction` گرفته تا
 * امضای هفت ViewModel.
 *
 * `kotlin.uuid.Uuid` از کاتلین ۲.۰.۲۰ در کدِ مشترک هست و روی هر سه سکو
 * کار می‌کند.
 *
 * **و چرا این تغییر به دفترِ کارگاه‌های امروزی دست نمی‌زند:** آنچه در
 * دیتابیس می‌نشیند رشته است، نه شیء. `Uuid.toString()` همان قالبِ
 * هشت-چهار-چهار-چهار-دوازدهِ حروفِ کوچک را می‌دهد که
 * `java.util.UUID.toString()` می‌داد، و `Uuid.parse` همان را می‌خوانَد.
 * پس پروندهٔ سفارش‌هایی که تا امروز ثبت شده‌اند همان‌جور خوانده می‌شود.
 *
 * نامِ `UUID` عمداً نگه داشته شد: با `typealias`، هر ۲۲ فایل فقط خطِ
 * `import`شان عوض می‌شود و بدنه‌شان دست‌نخورده می‌مانَد. چیزی که تغییر
 * می‌کند فقط دو تابعِ سازنده است، چون `typealias` نمی‌تواند توابعِ
 * همراهِ نوعِ اصلی را جابه‌جا کند.
 */
@OptIn(ExperimentalUuidApi::class)
typealias UUID = Uuid

/** جایگزینِ `UUID.randomUUID()`. */
@OptIn(ExperimentalUuidApi::class)
fun randomUuid(): UUID = Uuid.random()

/**
 * جایگزینِ `UUID.fromString(…)`.
 *
 * مثلِ آن، روی رشتهٔ نامعتبر استثنا می‌اندازد — پس `runCatching`هایی که
 * دورش نوشته شده‌اند همان‌جور کار می‌کنند.
 */
@OptIn(ExperimentalUuidApi::class)
fun uuidOf(text: String): UUID = Uuid.parse(text)
