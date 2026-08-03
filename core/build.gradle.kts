/*
 * :core — منطقِ کارگاه، بی هیچ اندرویدی.
 *
 * این ماژول عمداً `kotlin("jvm")` است نه کتابخانهٔ اندروید. یعنی
 * کامپایلر خودش نگهبان است: اگر کسی اینجا `android.*` یا `androidx.*`
 * وارد کند، ساخت می‌شکند. بدونِ این مرز، «مستقل بودن» فقط یک ادعا در
 * توضیحاتِ کد می‌مانْد.
 *
 * همین است که نسخهٔ ویندوز را ممکن می‌کند: این کد بدونِ تغییر هم روی
 * گوشی اجرا می‌شود هم روی پی‌سی.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    /*
     * `api` است چون ViewModelهای این ماژول `StateFlow` را در امضای
     * عمومی‌شان برمی‌گردانند؛ مصرف‌کننده باید آن نوع را ببیند.
     */
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    /*
     * فقط حاشیه‌نویسی‌های Room (@Entity, @Dao, @Query…) — نه موتورش.
     *
     * `room-common` یک jarِ خالصِ جاواست و هیچ چیزِ اندرویدی ندارد؛
     * موتورِ Room (`room-runtime`) و پردازشگرش در `:app` می‌مانند.
     * یعنی جدولِ داده‌ها اینجا **توصیف** می‌شود و هر سکو خودش موتورش را
     * می‌آورد — همان چیزی که نسخهٔ ویندوز لازم دارد.
     */
    api("androidx.room:room-common:2.7.1")

    /*
     * `ViewModel` و `viewModelScope` — و بله، این هم اندرویدی نیست.
     *
     * از نسخهٔ ۲.۸ کتابخانهٔ lifecycle چندسکویی شد و برای JVMِ رومیزی
     * هم منتشر می‌شود. Gradle خودش برای هر مصرف‌کننده نسخهٔ درست را
     * برمی‌دارد: `:app` نسخهٔ اندروید و `:desktop` نسخهٔ رومیزی.
     *
     * چرا مهم است: ۲۷ تا از ۳۷ ViewModel این پروژه به هیچ چیزِ
     * اندرویدی جز همین دو نام وابسته نبودند. با آمدنِ این خط، همان‌ها
     * بی یک کلمه تغییر روی ویندوز هم کامپایل می‌شوند.
     *
     * `api` است نه `implementation`، چون ViewModelهای اینجا از این
     * کلاس ارث می‌برند و مصرف‌کننده باید نوعِ پدر را ببیند.
     */
    api("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
}

kotlin {
    compilerOptions {
        jvmToolchain(17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
