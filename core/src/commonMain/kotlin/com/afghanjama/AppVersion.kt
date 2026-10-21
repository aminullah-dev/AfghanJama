package com.afghanjama

import com.afghanjama.ui.format.fa
import com.afghanjama.ui.format.toPersianDigits

/**
 * نسخهٔ همین برنامه، به زبانی که کارگاه و پشتیبانی می‌فهمند.
 *
 * **چرا هست.** تا ۱.۹.۰ هر ساختی خودش را «۱.۹.۰» می‌نامید، ولی DMGِ مک،
 * APKِ اندروید و ساختِ آیفون از کامیت‌های متفاوت بودند و هیچ‌کدام این را
 * نمی‌گفت. حالا هر سه سکو از یک [BuildInfo] می‌خوانند که از
 * `gradle.properties` و گیت ساخته می‌شود (`core/build.gradle.kts`).
 *
 * **«رسمی» یعنی چه.** فقط ساختی که HEADش دقیقاً روی تگِ `v<نسخه>` است و
 * درختش تمیز. بقیه «ساختِ آزمایشی»اند، حتی اگر عددِ نسخه‌شان همان باشد —
 * و همین تفاوت بود که تا امروز دیده نمی‌شد.
 *
 * تابع‌ها ورودی می‌گیرند (با پیش‌فرضِ [BuildInfo]) تا آزمون بتواند هر دو
 * کانال را بسنجد، نه فقط کانالِ همین ساخت.
 */
object AppVersion {

    /** کانالِ ساختی که از روی تگِ نسخه و با درختِ تمیز ساخته شده. */
    const val RELEASE = "release"

    /** هر ساختِ دیگر. */
    const val TEST = "test build"

    val isRelease: Boolean get() = BuildInfo.CHANNEL == RELEASE

    /**
     * شمارهٔ نسخهٔ همین برنامه («۱.۱۱.۰» به لاتین) — برای بسته‌هایی بیرون از
     * ریشه. `BuildInfo` ساختهٔ Gradle است و بررسی‌های ساختاری آن را نمی‌بینند؛
     * همین یک در کافی است.
     */
    val current: String get() = BuildInfo.VERSION

    /** «نسخهٔ ۱.۹.۱» — و برای ساختِ آزمایشی «نسخهٔ ۱.۹.۱، آزمایشی». */
    fun title(
        version: String = BuildInfo.VERSION,
        channel: String = BuildInfo.CHANNEL,
    ): String {
        val base = "نسخهٔ ${version.toPersianDigits()}"
        return if (channel == RELEASE) base else "$base، آزمایشی"
    }

    /** «شمارهٔ ساخت ۱۵ · کامیت 0e109d9» — کامیت لاتین می‌مانَد تا با گیت جور شود. */
    fun detail(
        code: Int = BuildInfo.CODE,
        commit: String = BuildInfo.COMMIT,
    ): String = "شمارهٔ ساخت ${code.fa()} · کامیت $commit"

    /**
     * یک جمله که می‌گوید این ساخت رسمی است یا چرا نیست.
     *
     * ساختِ آزمایشی را نباید به کارگاه داد: ممکن است چیزی داشته باشد که
     * هیچ نسخهٔ منتشرشده‌ای ندارد، یا چیزی نداشته باشد که دارد.
     */
    fun channelNote(
        version: String = BuildInfo.VERSION,
        channel: String = BuildInfo.CHANNEL,
        dirty: Boolean = BuildInfo.DIRTY,
    ): String = when {
        channel == RELEASE ->
            "ساختِ رسمی — از روی تگِ v$version ساخته شده."
        dirty ->
            "ساختِ آزمایشی — تغییرِ ثبت‌نشده دارد. به کارگاه ندهید."
        else ->
            "ساختِ آزمایشی — از روی تگِ v$version ساخته نشده. به کارگاه ندهید."
    }

    /**
     * یک سطرِ لاتین برای پشتیبانی: `KhayatYar 1.9.1 (15) 0e109d9 release`.
     *
     * لاتین است چون در پیام و ایمیل کپی می‌شود و باید همان‌طور که هست با
     * تگ و کامیت مقایسه شود. تغییرِ ثبت‌نشده با `-dirty` می‌آید، همان
     * قراردادِ `git describe --dirty`.
     */
    fun supportLine(
        version: String = BuildInfo.VERSION,
        code: Int = BuildInfo.CODE,
        commit: String = BuildInfo.COMMIT,
        dirty: Boolean = BuildInfo.DIRTY,
        channel: String = BuildInfo.CHANNEL,
    ): String {
        val sha = if (dirty) "$commit-dirty" else commit
        return "${AppInfo.NAME_LATIN} $version ($code) $sha $channel"
    }

    /** کوتاه، برای نوارِ کنار و عنوانِ پنجره: «نسخهٔ ۱.۹.۱ · 0e109d9». */
    fun short(
        version: String = BuildInfo.VERSION,
        commit: String = BuildInfo.COMMIT,
        channel: String = BuildInfo.CHANNEL,
    ): String = "${title(version, channel)} · $commit"
}
