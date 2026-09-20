// build.gradle.kts (Project)
plugins {
    id("com.android.application") version "8.9.1" apply false
    // `:core` از وقتی چندسکویی شد هدفِ اندروید هم دارد — بی این،
    // `:app` نمی‌تواند از آن وابستگی بردارد.
    id("com.android.library") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
    // `:core` — یک کدِ مشترک برای گوشی، پی‌سی و آیفون.
    id("org.jetbrains.kotlin.multiplatform") version "2.1.20" apply false
    /*
     * KSP جای kapt را گرفت.
     *
     * kapt در این پروژه **فقط** برای پردازشگرِ Room بود، پس این تغییر
     * به هیچ چیزِ دیگری دست نمی‌زند. دلیلش هم آینده است: نسخهٔ ویندوز
     * باید همان `@Database` را برای سکوی خودش بسازد، و Roomِ چندسکویی
     * با kapt کار نمی‌کند.
     *
     * خطِ ۱.۰.۳۲ عمداً انتخاب شد نه ۲.۰.۱: با Room 2.7.1 پرآزموده‌تر
     * است و اپِ تحویل‌شده جای آزمایش نیست.
     */
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Compose Multiplatform — نسخهٔ همگام با Kotlin 2.1.20
    id("org.jetbrains.compose") version "1.8.0" apply false
}
