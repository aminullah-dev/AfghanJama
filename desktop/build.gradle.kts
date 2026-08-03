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
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)

    // Compose روی دسکتاپ روی حلقهٔ رویدادِ Swing می‌نشیند
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
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
    compilerOptions { jvmToolchain(17) }
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
