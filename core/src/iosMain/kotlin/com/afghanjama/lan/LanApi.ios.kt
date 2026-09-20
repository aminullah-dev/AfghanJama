package com.afghanjama.lan

import com.afghanjama.ui.vm.BoardRow

/*
 * سهمِ iOS — **هنوز نیست، و صریح می‌گوید نیست.**
 *
 * اشتراکِ کارگاه روی وای‌فای با `HttpURLConnection` و `org.json` نوشته
 * شده و هیچ‌کدام روی iOS نیستند؛ نسخهٔ بومی‌اش (`NSURLSession` و
 * `kotlinx.serialization`) کارِ جداگانه‌ای است.
 *
 * **چرا `Err` و نه `TODO()`:** آن یکی برنامه را وسطِ کار می‌کشت. و چرا
 * `Err` و نه فهرستِ خالی: فهرستِ خالی روی صفحهٔ تخته شبیهِ «کارگاه امروز
 * کاری ندارد» دیده می‌شود، و کارگر منتظرِ کاری می‌مانَد که هرگز
 * نمی‌آید. پیامِ صریح بدترین خبر است ولی درست‌ترینش.
 */
private const val NOT_YET =
    "اشتراکِ کارگاه در نسخهٔ آیفون هنوز نیست — فعلاً از گوشیِ اندرویدی یا کامپیوتر استفاده کنید."

private object IosLanApi : LanApi {
    override suspend fun ping(): LanResult<Unit> = LanResult.Err(NOT_YET)

    override suspend fun myWork(worker: String): LanResult<List<RemoteWork>> =
        LanResult.Err(NOT_YET)

    override suspend fun board(): LanResult<List<BoardRow>> = LanResult.Err(NOT_YET)

    override suspend fun sendRequest(
        device: String,
        worker: String,
        type: String,
        summary: String,
        refId: Long,
        amount: Int,
        note: String,
    ): LanResult<String> = LanResult.Err(NOT_YET)
}

actual fun lanClient(host: String, code: String): LanApi = IosLanApi
