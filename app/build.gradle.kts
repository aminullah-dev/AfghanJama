// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.afghanjama"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.afghanjama"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    testOptions {
        unitTests.all {
            // بدونِ این، Gradle وقتی تست‌ها قبول می‌شوند هیچ چیزی چاپ نمی‌کند
            // و لاگِ CI با «هیچ تستی پیدا نشد» یک شکل درمی‌آید.
            it.testLogging {
                events("passed", "failed", "skipped")
                showStandardStreams = false
            }
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1"
        )
    }
}

dependencies {
    /*
     * Room باید با نسخهٔ Kotlin هم‌قدم بماند.
     *
     * Room 2.6.1 نسخهٔ kotlinx-metadata را در خودش بسته‌بندی کرده و آن
     * نسخه متادیتای Kotlin را فقط تا 2.0 می‌فهمد. با Kotlin 2.1 که
     * متادیتای 2.1.0 تولید می‌کند، پردازشگرِ Room همان اولِ ساخت با
     * «maximum supported version is 2.0.0» می‌ایستد.
     *
     * پس هر بار که Kotlin بالا می‌رود، این هم باید برود.
     */
    val room = "2.7.1"
    val nav = "2.8.9"
    val datastore = "1.1.1"
    val lifecycle = "2.8.7"

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // تولید QR برای اسناد (کتابخانهٔ خالص جاوا، بدون وابستگی اندرویدی).
    // نسخه باید با zxing-android-embedded هم‌خوان بماند (4.3.0 ← core 3.4.1)؛
    // نسخهٔ بالاتر باعث ناسازگاری باینری و کرش هنگام اسکن می‌شود.
    implementation("com.google.zxing:core:3.4.1")
    // اسکنِ QR با دوربین (Activityِ آماده + مدیریت دسترسی دوربین)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // ✅ Compose BOM (نسخه‌ها را یکدست می‌کند)
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))

    // Compose core
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ✅ لازم برای rememberSaveable
    implementation("androidx.compose.runtime:runtime-saveable")

    // Navigation
    implementation("androidx.navigation:navigation-compose:$nav")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:$datastore")

    // Room
    implementation("androidx.room:room-runtime:$room")
    kapt("androidx.room:room-compiler:$room")
    implementation("androidx.room:room-ktx:$room")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WorkManager (یادآوری هفتگی تسویه کارمزد)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Biometric (تأیید اثر انگشت برای حضور و غیاب) — FragmentActivity هم می‌آورد
    implementation("androidx.biometric:biometric:1.1.0")

    /*
     * fragment را صریحاً بالا می‌بریم و به نسخهٔ قدیمیِ biometric واگذارش
     * نمی‌کنیم.
     *
     * androidx.biometric:1.1.0 نسخهٔ fragment 1.2.5 را می‌آورد. در آن
     * نسخه، FragmentActivity هر requestCode بزرگ‌تر از ۱۶ بیت را رد
     * می‌کند — هم در startActivityForResult و هم در requestPermissions.
     * اما ActivityResultRegistry عمداً کدهای بزرگ‌تر می‌سازد تا با
     * کدهای قدیمی تداخل نکند.
     *
     * نتیجه: هر launch از MainActivity (که FragmentActivity است) با
     * «Can only use lower 16 bits for requestCode» شکست می‌خورد —
     * اسکنر QR، انتخابگرِ فایل برای بکاپ و بازیابی، و خروجیِ CSV.
     * علتِ اصلیِ «اسکنر کار نمی‌کند» همین بود، نه نبودِ مجوز.
     *
     * fragment 1.3.0 به بعد این اعتبارسنجی را برداشته است.
     */
    implementation("androidx.fragment:fragment:1.8.4")

    // Desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
