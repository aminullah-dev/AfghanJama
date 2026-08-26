import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // صفحه‌های مشترک `@Composable`اند، پس پردازشگرِ Compose باید اینجا
    // هم اجرا شود. این افزونه به اندروید کاری ندارد؛ روی هر ماژولِ
    // کاتلین می‌نشیند.
    id("org.jetbrains.kotlin.plugin.compose")
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

    /*
     * JSONِ قراردادِ شبکه.
     *
     * `compileOnly` است و این عمدی است: روی **اندروید این کلاس‌ها در
     * خودِ سیستم هستند**، پس اگر اینجا `implementation` بود، همان
     * کلاس‌ها دو بار در APK می‌نشستند. روی ویندوز jarِ واقعی لازم است و
     * `:desktop` خودش می‌آوردش.
     */
    compileOnly("org.json:json:20260719")

    /*
     * Compose برای صفحه‌های مشترک — و `compileOnly` بودنش عمدی و مهم است.
     *
     * چرا اصلاً ممکن است: کلاس‌های Compose روی هر دو سکو **نامِ یکسان**
     * دارند (`androidx.compose.material3.Text` روی گوشی و روی پی‌سی یکی
     * است؛ نسخهٔ اندرویدیِ Compose Multiplatform خودش همان androidx است).
     * پس بایت‌کدی که اینجا ساخته می‌شود هر دو جا می‌نشیند.
     *
     * چرا `compileOnly` و نه `api`: با `api` این‌ها به گرافِ وابستگیِ
     * `:app` اضافه می‌شدند و می‌توانستند با `compose-bom` سرِ نسخه دعوا
     * کنند. با `compileOnly` گرافِ وابستگیِ اپِ اندروید **دست‌نخورده**
     * می‌ماند — یعنی این فاز از اساس نمی‌تواند چیزی را که در APK
     * می‌نشیند عوض کند. هر مصرف‌کننده Compose خودش را دارد: `:app` از
     * `compose-bom` و `:desktop` از `compose.desktop.currentOs`.
     *
     * چرا `-desktop`: اینها jarِ سادهٔ JVMاند و ابهامِ variant ندارند.
     * نسخهٔ بی‌پسوند فراداده‌ای چندسکویی دارد که یک ماژولِ `kotlin("jvm")`
     * بدونِ افزونهٔ Compose ممکن است نتواند درست تفکیکش کند. چون
     * `compileOnly` است، این نام هرگز به اندروید نشت نمی‌کند.
     */
    val compose = "1.8.0"
    compileOnly("org.jetbrains.compose.runtime:runtime-desktop:$compose")
    // `rememberSaveable` جدا بسته‌بندی شده، نه داخلِ runtime. `MasterDataScreen`
    // از آن استفاده می‌کند و بدونِ این خط فقط سرِ کامپایل معلوم می‌شد.
    compileOnly("org.jetbrains.compose.runtime:runtime-saveable-desktop:$compose")
    compileOnly("org.jetbrains.compose.foundation:foundation-desktop:$compose")
    compileOnly("org.jetbrains.compose.animation:animation-desktop:$compose")
    compileOnly("org.jetbrains.compose.material3:material3-desktop:$compose")
    compileOnly("org.jetbrains.compose.ui:ui-desktop:$compose")
    compileOnly("org.jetbrains.compose.ui:ui-text-desktop:$compose")
    compileOnly("org.jetbrains.compose.ui:ui-unit-desktop:$compose")
    compileOnly("org.jetbrains.compose.ui:ui-graphics-desktop:$compose")

    /*
     * آیکون‌ها روی ۱.۷.۳ قفل‌اند و این یک محدودیتِ واقعی است، نه سلیقه:
     * `material-icons-extended` برای دسکتاپ بعد از ۱.۷.۳ منتشر نشده
     * (۱.۸.۰ روی Maven Central وجود ندارد — بررسی شد). خودِ آیکون‌ها
     * فقط مسیرِ برداری‌اند و بینِ نسخه‌ها عوض نمی‌شوند، پس این قفل بی‌خطر
     * است؛ ولی اگر روزی آیکونی پیدا نشد، علتش همین است.
     */
    compileOnly("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")
}

/*
 * **هدفِ بایت‌کد ۱۷، بدونِ اینکه JDKِ خاصی طلب شود.**
 *
 * تا دیروز اینجا `jvmToolchain(17)` بود، یعنی Gradle **اصرار** داشت یک
 * نصبِ JDK 17 پیدا کند. روی CI مشکلی نبود (`setup-java` همان را
 * می‌گذارد)، ولی Android Studio با JDK 21 می‌آید و آنجا سینک می‌شکست:
 *
 *     Cannot find a Java installation on your machine
 *     Undefined Toolchain Download Repositories
 *
 * راهِ دیگر افزودنِ «foojay» بود تا Gradle خودش یک JDK 17 دانلود کند —
 * ولی آن یعنی ۱۸۰ مگابایت دانلود روی هر ماشینِ تازه، فقط برای اینکه
 * بایت‌کدِ ۱۷ بسازیم.
 *
 * این شکل همان کاری است که `:app` از قبل می‌کرد: با هر JDKی که Gradle
 * روی آن است کامپایل کن، ولی خروجی را ۱۷ بگذار. CI همچنان روی ۱۷ سنجیده
 * می‌شود، پس چیزی که تحویل می‌رود عوض نمی‌شود.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
