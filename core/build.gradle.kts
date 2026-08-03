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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
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
