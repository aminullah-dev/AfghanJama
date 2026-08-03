import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/*
 * :desktop — نسخهٔ ویندوزِ خیاط‌یار.
 *
 * فازِ ۳: هدف **اثباتِ زنجیرهٔ ساخت** است، نه برنامهٔ کامل. یک پنجرهٔ
 * واقعی باز می‌شود، فارسیِ راست‌به‌چپ را با فونتِ خودِ اپ می‌نویسد، و
 * منطقِ حسابداریِ `:core` را همان‌جا اجرا می‌کند.
 *
 * دیتابیس هنوز اینجا نیست — آن فازِ بعد است. تا آن وقت، چیزی که این
 * پنجره نشان می‌دهد از بررسی‌هایی می‌آید که به دفتر کاری ندارند و فقط
 * ریاضیِ پول را می‌سنجند.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    // پردازشگرِ Room برای این ماژول هم باید اجرا شود؛ `@Database`ِ
    // ویندوز اینجاست.
    id("com.google.devtools.ksp")
}

dependencies {
    implementation(project(":core"))

    // موتورِ Compose برای سیستم‌عاملِ همین ماشین (روی رانر لینوکس،
    // روی کارگاه ویندوز). این خط `runtime`, `foundation` و `ui` را
    // می‌آورد — **ولی Material را نه**.
    implementation(compose.desktop.currentOs)

    // Material 3 بستهٔ جداست و باید صریح خواسته شود. برخلافِ اندروید
    // که `androidx.compose.material3` را در وابستگی‌های اپ داریم، اینجا
    // `currentOs` آن را با خودش نمی‌آورد و همهٔ `Text` و `MaterialTheme`ها
    // «Unresolved reference» می‌شوند.
    implementation(compose.material3)

    // `viewModel { }` برای Compose — همان تابعی که روی اندروید هم
    // ViewModel را می‌سازد و در بازترکیب‌ها نگه می‌دارد. خودِ کلاسِ
    // `ViewModel` از `:core` می‌آید (آنجا `api` است).
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Compose روی دسکتاپ روی حلقهٔ رویدادِ Swing می‌نشیند
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    /*
     * موتورِ Room برای ویندوز.
     *
     * `:core` فقط حاشیه‌نویسی‌ها را دارد (`room-common`) — یعنی
     * **توصیفِ** جدول‌ها. موتور را هر سکو خودش می‌آورد: گوشی
     * `room-runtime`ِ اندروید، و اینجا نسخهٔ JVM با درایورِ SQLiteِ
     * همراه (`sqlite-bundled`) که کتابخانهٔ بومی را خودش می‌آورد و
     * نیازی به SQLiteِ نصب‌شده روی ویندوز ندارد.
     */
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.sqlite:sqlite-bundled:2.5.1")
    ksp("androidx.room:room-compiler:$room")
}

/*
 * فونتِ وزیرمتن از همان‌جایی می‌آید که اپِ اندروید برمی‌دارد — کپی
 * نمی‌شود. دو نسخه از یک فونت یعنی روزی یکی به‌روز می‌شود و آن یکی نه،
 * و بعد کاغذِ گوشی با کاغذِ پی‌سی فرق می‌کند.
 */
sourceSets {
    named("main") {
        resources.srcDir(rootProject.file("app/src/main/res/font"))
    }
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.afghanjama.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KhayatYar"
            packageVersion = "1.0.0"
            windows {
                menuGroup = "KhayatYar"
                // با هر نسخهٔ تازه عوض نشود، وگرنه ویندوز به‌جای
                // به‌روزرسانی یک برنامهٔ دوم نصب می‌کند.
                upgradeUuid = "6E7B1F2C-9A54-4B8E-97C6-3D2A5B41E0F7"
            }
        }
    }
}
