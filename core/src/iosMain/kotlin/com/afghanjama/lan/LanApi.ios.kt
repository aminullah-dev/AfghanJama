package com.afghanjama.lan

import com.afghanjama.ui.vm.BoardRow

/*
 * سهمِ iOS از **قراردادِ ۱** (رمزِ شش‌رقمی، گوشی‌های ۱.۹/۱.۱۰) — که آیفون ندارد.
 *
 * آیفون از ۱.۱۱ با «کارگاهِ وصل» (قراردادِ ۲، `HubSession`) وصل می‌شود و آن
 * کار می‌کند. این مشتری فقط برای راهِ قدیم است: `BoardViewModel` وقتی
 * `lanClient` را صدا می‌زند که گوشی جفت **نشده** ولی حالتش «کارگر» است، و
 * روی آیفون حالتِ «کارگر» فقط با جفت شدن می‌آید و با «جدا کردن» برمی‌گردد
 * (`HubClientViewModel`). پس در کارِ عادی به اینجا نمی‌رسیم؛ `actual`
 * می‌ماند چون `expect`ِ مشترک آن را می‌خواهد.
 *
 * اگر روزی رسیدیم (مثلاً تنظیمی نیمه‌کاره)، پیام باید راهِ درست را بگوید —
 * تا ۱.۱۲.۰ می‌گفت «کارگاهِ وصل در نسخهٔ آیفون هنوز نیست»، که درست برعکسِ
 * صفحهٔ «کارگاهِ وصل»ِ همان آیفون بود.
 *
 * **چرا `Err` و نه `TODO()`:** آن یکی برنامه را وسطِ کار می‌کشت. و چرا
 * `Err` و نه فهرستِ خالی: فهرستِ خالی روی صفحهٔ تخته شبیهِ «کارگاه امروز
 * کاری ندارد» دیده می‌شود، و کارگر منتظرِ کاری می‌مانَد که هرگز
 * نمی‌آید.
 */
private const val LEGACY_ONLY =
    "آیفون با رمزِ شش‌رقمیِ قدیم وصل نمی‌شود. از «کارگاهِ وصل» این گوشی را با کامپیوترِ کارگاه جفت کنید."

private object IosLanApi : LanApi {
    override suspend fun ping(): LanResult<Unit> = LanResult.Err(LEGACY_ONLY)

    override suspend fun myWork(worker: String): LanResult<List<RemoteWork>> =
        LanResult.Err(LEGACY_ONLY)

    override suspend fun board(): LanResult<List<BoardRow>> = LanResult.Err(LEGACY_ONLY)

    override suspend fun sendRequest(
        device: String,
        worker: String,
        type: String,
        summary: String,
        refId: Long,
        amount: Int,
        note: String,
    ): LanResult<String> = LanResult.Err(LEGACY_ONLY)
}

actual fun lanClient(host: String, code: String): LanApi = IosLanApi

/**
 * از ۱.۱۱ آیفون **مشتریِ «کارگاهِ وصل»** است (قراردادِ ۲: کارگر، صاحب‌کار یا
 * تابلو). میزبان نمی‌شود و رمزِ شش‌رقمیِ قراردادِ ۱ را هم ندارد؛ کامپیوترِ ۱.۱۰
 * و قدیم‌تر برای آیفون «اول کامپیوتر را به‌روز کنید» است.
 */
actual fun workshopSharingAvailable(): Boolean = true
