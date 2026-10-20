import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * :core — منطقِ کارگاه، برای هر سه سکو.
 *
 * **چرا از `kotlin("jvm")` به `kotlin("multiplatform")` رفت.**
 *
 * تا دیروز این ماژول عمداً یک ماژولِ سادهٔ JVM بود و همان سادگی یک
 * نگهبانِ واقعی داشت: `android.*` اینجا اصلاً کامپایل نمی‌شد، پس
 * «مستقل بودن» ادعا نبود، قانونِ کامپایلر بود.
 *
 * ولی آن مرز فقط دو سکو را می‌شناخت. iOS جاوا ندارد — نه
 * `java.util.UUID`، نه `System.currentTimeMillis()`، نه
 * `String.format`. یک ماژولِ JVM هرگز برای آن ساخته نمی‌شود.
 *
 * پس مرز عوض شد ولی برداشته نشد: حالا `commonMain` نگهبانی می‌کند و
 * **سخت‌گیرتر از قبل** است. کدِ مشترک نه `android.*` می‌پذیرد نه
 * `java.*`؛ هر چیزی که به سکو گره خورده باید یا در `jvmMain` بنشیند یا
 * با `expect/actual` از هر سه طرف جواب بگیرد.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    /*
     * `com.android.library` — و این چیزی است که قبلاً عمداً نبود.
     *
     * لازم شد چون `:app` یک ماژولِ اندروید است و نمی‌تواند از ماژولی
     * چندسکویی که هدفِ اندروید ندارد وابستگی بردارد؛ Gradle واریانتِ
     * جوردرآمدنی پیدا نمی‌کند و ساخت با «Unable to find a matching
     * variant» می‌شکند.
     *
     * افزودنش یعنی نگهبانیِ قدیمی (کامپایلر جلوی `android.*` را
     * می‌گرفت) از دست می‌رود. جایگزینش `commonMain` است: کدِ مشترک برای
     * iOS هم ساخته می‌شود و آنجا نه اندروید هست نه جاوا، پس هر نشتی
     * همان‌جا می‌شکند — و زودتر از قبل، چون `java.*` را هم می‌گیرد.
     */
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    /*
     * افزونهٔ Compose Multiplatform.
     *
     * تا دیروز `:core` کتابخانه‌های Compose را `compileOnly` می‌گرفت —
     * ترفندی که گرافِ وابستگیِ اپِ اندروید را دست‌نخورده نگه می‌داشت.
     *
     * **آن ترفند روی iOS کار نمی‌کند.** `compileOnly` یعنی «سرِ ساخت
     * باش، سرِ اجرا نه»؛ ولی Kotlin/Native کتابخانه را در خودِ باینری
     * پیوند می‌زند و بی klibِ واقعی چیزی برای پیوند زدن نیست. پس
     * Compose اینجا وابستگیِ واقعی شد.
     *
     * نگرانیِ آن توضیحِ قدیمی (دعوای نسخه با `compose-bom` در `:app`)
     * سرِ جایش است و با عدد پاسخ داده می‌شود: نسخهٔ اندرویدیِ
     * Compose Multiplatform 1.8.0 همان androidx compose 1.8.0 است و
     * `compose-bom:2025.04.01` هم همان را می‌آورد — دو نام برای یک چیز.
     */
    id("org.jetbrains.compose")
    /*
     * KSP — برای موتورِ Room روی iOS.
     *
     * `:core` تا دیروز فقط حاشیه‌نویسی‌های Room را داشت و موتورش در
     * `:app` و `:desktop` بود. آن دو ماژول جاوایی‌اند و Room آنجا
     * می‌تواند کلاسِ `_Impl` را با بازتاب پیدا کند. Kotlin/Native بازتاب
     * ندارد، پس `@Database`ِ iOS باید همین‌جا و کنارِ کدِ خودش ساخته
     * شود.
     */
    id("com.google.devtools.ksp")
}

ksp {
    // طرحِ هر نسخه در گیت می‌ماند، مثلِ دو سکوی دیگر. بی این، تنها
    // چیزی که تضمین می‌کند مهاجرت‌ها به همان طرحی برسند که موجودیت‌ها
    // توصیف می‌کنند، آدم است.
    arg("room.schemaLocation", "$projectDir/schemas")
}

/*
 * ═══ `BuildInfo` — نسخه و کامیت، داخلِ خودِ برنامه ═══
 *
 * **چرا لازم شد.** تا ۱.۹.۰ هر ساختی خودش را «۱.۹.۰» می‌نامید و
 * ساخت‌ها با هم فرق داشتند: DMGِ مکِ ۱۵ اکتبر همه‌چیز را داشت، APKِ ۱۳
 * اکتبر اسکنِ محصول را نداشت، آیفونِ ۹ اکتبر کهنه‌تر بود. هیچ‌کدام
 * نمی‌گفت از کدام کامیت ساخته شده، پس «کدام نسخه را داری؟» جوابی
 * نداشت که به کدِ واقعی برسد.
 *
 * این تسک یک فایلِ کاتلین در `commonMain` می‌سازد، پس **هر سه سکو**
 * (اندروید، مک و ویندوز، آیفون) همان چهار عدد را می‌بینند:
 *
 *   - `VERSION` و `CODE` از `gradle.properties` — همان منبعی که APK،
 *     MSI، DMG و (از راهِ `tools/release/ios-version.sh`) آیفون می‌خوانند.
 *   - `COMMIT` هفت حرفِ اولِ کامیت (`local` اگر گیت نبود).
 *   - `CHANNEL` فقط وقتی `release` است که HEAD **دقیقاً** روی تگِ
 *     `v$appVersion` باشد (`git describe --exact-match`) و درخت تمیز. هر
 *     ساختِ دیگری `test build` است — از جمله ساختِ محلیِ کارفرما از روی
 *     شاخه، یا ساختی که یک فایلِ ثبت‌نشده دارد.
 *
 * **چرا کانال از گیت و نه از CI.** DMGِ امضاشده روی مکِ خودِ کارفرما
 * ساخته می‌شود، نه در CI (`tools/macos-signing/sign-and-notarize.sh`).
 * اگر «رسمی» یعنی «در CI و روی تگ»، همان DMGی که به کارگاه می‌رود
 * «آزمایشی» نام می‌گرفت.
 *
 * **زمانِ ساخت عمداً نیست:** دو ساخت از یک کامیت باید بایت‌به‌بایت یکی
 * باشند.
 *
 * گیت سه بار و ارزان صدا زده می‌شود. `--no-optional-locks` قفلِ
 * `index.lock` را نمی‌گیرد، تا ساخت با گیتِ IDE که هم‌زمان کار می‌کند
 * دعوا نکند.
 */
val buildInfoVersion: String = providers.gradleProperty("appVersion").get()
val buildInfoCode: String = providers.gradleProperty("appVersionCode").get()

/** خروجیِ یک فرمانِ گیت در ریشهٔ مخزن، یا `null` اگر گیت نبود یا فرمان شکست. */
fun git(vararg args: String): String? = runCatching {
    val run = providers.exec {
        commandLine(listOf("git", "-C", rootDir.absolutePath) + args)
        isIgnoreExitValue = true
    }
    if (run.result.get().exitValue == 0) run.standardOutput.asText.get().trim() else null
}.getOrNull()

val generateBuildInfo by tasks.registering {
    description = "BuildInfo.kt را از gradle.properties و گیت می‌سازد."
    val version = buildInfoVersion
    val code = buildInfoCode
    val commit = provider { git("rev-parse", "--short=7", "HEAD") ?: "local" }
    // گیتِ ناپیدا «تمیز» شمرده نمی‌شود: نامعلوم یعنی آزمایشی.
    // `--untracked-files=normal`: با `status.showUntrackedFiles=no` در تنظیمِ
    // گیت، فایلِ تازه دیده نمی‌شد و ساختی که آن را داشت «رسمی» نام می‌گرفت.
    val dirty = provider {
        (git("--no-optional-locks", "status", "--porcelain", "--untracked-files=normal") ?: "?").isNotEmpty()
    }
    val tagged = provider {
        git("describe", "--exact-match", "--tags", "--match", "v$version", "HEAD") != null
    }
    val outDir = layout.buildDirectory.dir("generated/buildinfo/commonMain/kotlin")

    inputs.property("version", version)
    inputs.property("code", code)
    inputs.property("commit", commit)
    inputs.property("dirty", dirty)
    inputs.property("tagged", tagged)
    outputs.dir(outDir)

    doLast {
        val isDirty = dirty.get()
        val channel = if (tagged.get() && !isDirty && commit.get() != "local") "release" else "test build"
        fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        val file = outDir.get().asFile.resolve("com/afghanjama/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |// تولیدشده با `:core:generateBuildInfo` از `gradle.properties` و گیت — دستی ویرایش نکنید.
            |package com.afghanjama
            |
            |/** نسخه و ساختِ همین برنامه. معنا و متنش در [AppVersion]. */
            |object BuildInfo {
            |    const val VERSION: String = ${lit(version)}
            |    const val CODE: Int = ${code.toInt()}
            |    const val COMMIT: String = ${lit(commit.get())}
            |    const val DIRTY: Boolean = $isDirty
            |    const val CHANNEL: String = ${lit(channel)}
            |}
            |""".trimMargin()
        )
    }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    /*
     * `iosX64` عمداً نیست: شبیه‌سازِ اینتلی فقط روی مکِ اینتل معنی دارد
     * و کارگاه و ما هر دو روی Apple Silicon هستیم. هر هدفِ اضافه یک
     * بارِ ساختِ کامل است که هیچ‌کس خروجی‌اش را اجرا نمی‌کند.
     */
    /*
     * هر دو هدفِ iOS یک framework می‌سازند به نامِ `KhayatYarKit`، و
     * Xcode همان را پیوند می‌زند.
     *
     * `isStatic` عمداً روشن است: framework پویا روی iOS باید سرِ اجرا
     * امضا و بار شود و برای اپی که فقط یک مصرف‌کننده دارد هیچ سودی
     * ندارد — فقط یک قدمِ دیگر که می‌تواند بشکند.
     */
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "KhayatYarKit"
            isStatic = true
        }
    }

    /*
     * **صریح، چون `dependsOn`ِ دستیِ پایین خاموشش می‌کند.**
     *
     * کاتلین معمولاً خودش `iosMain` را می‌سازد و دو هدفِ iOS را زیرش
     * می‌گذارد. ولی به‌محضِ اینکه یک `dependsOn` دستی در فایل باشد،
     * الگوی پیش‌فرض کنار می‌رود — و آن‌وقت `iosMain` فقط یک پوشهٔ
     * بی‌صاحب روی دیسک است که هیچ هدفی نمی‌بیندش. نشانه‌اش این بود:
     *
     *     Expected secureRandomBytes has no actual declaration
     *     in module <AfghanJama:core> for Native
     *
     * یعنی `actual`ها نوشته شده بودند و کامپایلر اصلاً نمی‌دیدشان.
     */
    applyDefaultHierarchyTemplate()

    /*
     * `Uuid` هنوز آزمایشی است و هر **استفاده**‌اش opt-in می‌خواهد — نه
     * فقط تعریفش. شناسهٔ سفارش و تراکنش و بیست جدولِ دیگر همین است، پس
     * بدونِ این خط باید ۳۷ جا `@OptIn` نوشته می‌شد و هر جدولِ تازه یکی
     * بیشتر. تصمیم یکی است، پس یک جا نوشته می‌شود.
     */
    sourceSets.all {
        languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
    }

    sourceSets {
        /*
         * `jvmAndroidMain` — منبعِ میانیِ ویندوز/مک و گوشی، بی iOS.
         *
         * **چرا لازم شد.** اشتراکِ کارگاه روی وای‌فای با
         * `HttpURLConnection` و `org.json` نوشته شده و رمزِ ورود با
         * `javax.crypto`. هیچ‌کدام روی iOS نیستند، ولی هر دو روی
         * اندروید **و** ویندوز هستند و هر دو سکو واقعاً از آن‌ها
         * استفاده می‌کنند.
         *
         * بی این منبع، تنها جای ماندنشان `jvmMain` بود — و آن‌وقت
         * `:app` صفحهٔ «اشتراکِ کارگاه» و کلِ قفلِ ورود را از دست
         * می‌داد، چون `androidMain` از `jvmMain` ارث نمی‌برد.
         */
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        // `BuildInfo.kt` — از تسکِ بالا. دادنِ خودِ تسک (نه مسیرش) وابستگی را
        // هم می‌سازد: هر کامپایلی که `commonMain` را می‌خواند، اول آن را
        // اجرا می‌کند.
        commonMain.configure { kotlin.srcDir(generateBuildInfo) }

        commonMain.dependencies {
            /*
             * `api` است چون ViewModelهای این ماژول `StateFlow` را در
             * امضای عمومی‌شان برمی‌گردانند؛ مصرف‌کننده باید آن نوع را
             * ببیند.
             */
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

            /*
             * تاریخ و ساعت — تازه است، و بی آن `commonMain` ساعت ندارد.
             *
             * `java.time` و `java.util.Calendar` روی iOS نیستند.
             * `PersianDate` (تبدیلِ شمسی)، `BreakSchedule` (یادآورِ نان
             * و چای) و `nowMillis` همه از این می‌آیند.
             */
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

            /*
             * فقط حاشیه‌نویسی‌های Room (@Entity, @Dao, @Query…) — نه
             * موتورش. جدولِ داده‌ها اینجا **توصیف** می‌شود و هر سکو خودش
             * موتورش را می‌آورد.
             *
             * از ۲.۷ خودِ `room-common` چندسکویی است و برای iOS هم
             * منتشر می‌شود؛ وگرنه همین یک خط کلِ لایهٔ داده را از کدِ
             * مشترک بیرون می‌انداخت.
             */
            api("androidx.room:room-common:2.7.1")

            /*
             * `ViewModel` و `viewModelScope` — از ۲.۸ چندسکویی است.
             *
             * ۲۷ تا از ۳۷ ViewModel این پروژه به هیچ چیزِ اندرویدی جز
             * همین دو نام وابسته نبودند.
             */
            api("androidx.lifecycle:lifecycle-viewmodel:2.8.7")

            /*
             * Compose — حالا وابستگیِ واقعی، نه `compileOnly`. دلیلش
             * بالا در توضیحِ افزونه آمده.
             */
            api(compose.runtime)
            // `rememberSaveable` جدا بسته‌بندی شده، نه داخلِ runtime؛
            // `MasterDataScreen` از آن استفاده می‌کند.
            api(compose.runtimeSaveable)
            api(compose.foundation)
            api(compose.animation)
            api(compose.material3)
            api(compose.ui)

            /*
             * آیکون‌ها روی ۱.۷.۳ قفل‌اند و این محدودیتِ واقعی است نه
             * سلیقه: `material-icons-extended` بعد از ۱.۷.۳ منتشر نشده
             * و از Compose Multiplatform 1.8.0 از خودِ افزونه هم
             * برداشته شده. خودِ آیکون‌ها فقط مسیرِ برداری‌اند و بینِ
             * نسخه‌ها عوض نمی‌شوند، پس این قفل بی‌خطر است؛ ولی اگر روزی
             * آیکونی پیدا نشد، علتش همین است.
             *
             * ۴۰ فایل و ۷۷ آیکونِ متمایز به این خط بسته‌اند.
             */
            api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
        }

        iosMain.dependencies {
            /*
             * موتورِ Room و درایورِ SQLite — فقط سمتِ iOS.
             *
             * `sqlite-bundled` کتابخانهٔ بومیِ SQLite را با خودش می‌آورد،
             * پس به نسخه‌ای که خودِ iOS دارد وابسته نیستیم و فایلِ دفتر
             * بینِ سکوها یکسان می‌مانَد — همان کاری که `:desktop`
             * می‌کند.
             */
            implementation("androidx.room:room-runtime:2.7.1")
            implementation("androidx.sqlite:sqlite-bundled:2.5.1")
        }

        jvmAndroidMain.dependencies {
            /*
             * JSONِ قراردادِ شبکه — فقط `lan/` استفاده‌اش می‌کند و آن
             * پوشه حالا در `jvmMain` است.
             *
             * `compileOnly` است و این عمدی است: روی اندروید این کلاس‌ها
             * در خودِ سیستم هستند، پس اگر `implementation` بود همان
             * کلاس‌ها دو بار در APK می‌نشستند. روی ویندوز jarِ واقعی
             * لازم است و `:desktop` خودش می‌آوردش.
             */
            compileOnly("org.json:json:20260719")
        }
    }
}

android {
    namespace = "com.afghanjama.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/*
 * پردازشگرِ Room برای هر دو هدفِ iOS.
 *
 * KSP برای هر هدف جدا اجرا می‌شود و پیکربندی‌اش هم نامِ همان هدف را
 * دارد؛ یک `ksp(...)`ِ ساده مثلِ ماژول‌های تک‌سکویی اینجا به هیچ هدفی
 * نمی‌رسد و بی‌صدا هیچ کاری نمی‌کند.
 */
dependencies {
    add("kspIosArm64", "androidx.room:room-compiler:2.7.1")
    add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.7.1")
}
