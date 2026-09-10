package com.afghanjama.lan

import com.afghanjama.ui.vm.BoardRow

/*
 * قراردادِ سمتِ گوشیِ کارگر — **جدا از پیاده‌سازی‌اش.**
 *
 * تا دیروز `BoardViewModel` و `WorkshopLinkViewModel` مستقیم
 * `LanClient(host, code)` می‌ساختند، و آن کلاس با `HttpURLConnection` و
 * `org.json` نوشته شده که هیچ‌کدام روی iOS نیستند. یعنی دو ViewModel
 * که هیچ ربطی به شبکه ندارند، کلِ صفحهٔ تخته را از کدِ مشترک بیرون
 * می‌انداختند.
 *
 * این همان درزی است که `LanHost` از قبل برای سمتِ سرور باز کرده بود؛
 * اینجا برای سمتِ مشتری تکرار می‌شود.
 *
 * `RemoteWork` و `LanResult` هم به اینجا آمدند: داده‌اند و قرارداد، نه
 * شبکه.
 */

data class RemoteWork(
    val id: Long,
    val orderCode: String,
    val qty: Int,
    val unitWage: Long,
    val createdAt: Long,
)

sealed interface LanResult<out T> {
    data class Ok<T>(val value: T) : LanResult<T>
    data class Err(val message: String) : LanResult<Nothing>
}

/** آنچه گوشیِ کارگر از دفترِ کارگاه می‌خواهد. */
interface LanApi {
    suspend fun ping(): LanResult<Unit>
    suspend fun myWork(worker: String): LanResult<List<RemoteWork>>
    suspend fun board(): LanResult<List<BoardRow>>
    suspend fun sendRequest(
        device: String,
        worker: String,
        type: String,
        summary: String,
        refId: Long = 0,
        amount: Int = 0,
        note: String = "",
    ): LanResult<String>
}

/**
 * ساختِ مشتری برای [host] با رمزِ [code].
 *
 * روی JVM و اندروید همان `LanClient`ِ HTTPی همیشگی است. روی iOS هنوز
 * پیاده‌سازی ندارد و صریح `Err` برمی‌گرداند — نه `TODO()` که برنامه را
 * بکشد و نه `Ok`ِ خالی که شبیهِ «کارگاه چیزی ندارد» به نظر برسد.
 */
expect fun lanClient(host: String, code: String): LanApi
