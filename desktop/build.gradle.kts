import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// در اسکریپتِ Kotlin DSL نامِ `java` به افزونهٔ جاوا اشاره می‌کند، نه به
// بستهٔ جاوا؛ پس `java.util.zip.ZipFile` حل نمی‌شود و باید ایمپورت شود.
import java.io.File as JFile
import java.util.zip.ZipFile

/*
 * :desktop — نسخهٔ رومیزیِ خیاط‌یار: ویندوز و مک از همین یک ماژول.
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

/*
 * جایی که Room طرحِ هر نسخه را می‌نویسد.
 *
 * `exportSchema = true` بدونِ این، ساخت را با «Schema export directory
 * is not provided» می‌شکند.
 *
 * پوشه **در گیت می‌مانَد** و این نکتهٔ اصلی است: فایلِ نسخهٔ قبلی تنها
 * چیزی است که آزمونِ واقعیِ مهاجرت را ممکن می‌کند. اگر ساخته شود ولی
 * کامیت نشود، دفعهٔ بعد که `DB_VERSION` جلو برود باز هم دستمان خالی
 * است — همان جایی که امروز برای نسخه‌های ۱۹ تا ۶۰ ایستاده‌ایم.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    /*
     * آیکن‌ها — همان دامِ Material3، ولی یک قدم دیرتر پیدا می‌شود.
     *
     * سی صفحهٔ مشترک در `:core` از `Icons.*` استفاده می‌کنند. آنجا این
     * وابستگی `compileOnly` است — عمداً، تا گرافِ وابستگیِ اپِ اندروید
     * دست‌نخورده بماند — یعنی **به گرافِ اجرا نمی‌رسد**. و `material3`
     * هم `material-icons-core` را با خودش نمی‌آورد (در `runtimeClasspath`
     * وارسی شد: نه core، نه extended).
     *
     * نتیجه‌اش بدترین شکل را داشت: کامپایل سبز، پنجره باز، و سرِ اجرا
     * `NoClassDefFoundError` روی اولین صفحه‌ای که آیکن دارد.
     *
     * ۱.۷.۳ و نه ۱.۸.۰ — برای دسکتاپ بعد از ۱.۷.۳ منتشر نشده. همان
     * نسخه‌ای که `:core` با آن کامپایل می‌شود.
     */
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3")

    /*
     * `viewModel { }` برای Compose — همان تابعی که روی اندروید هم
     * ViewModel را می‌سازد و در بازترکیب‌ها نگه می‌دارد. خودِ کلاسِ
     * `ViewModel` از `:core` می‌آید (آنجا `api` است).
     *
     * **چرا `exclude`: دو Composeِ متفاوت در یک بسته.**
     *
     * این وابستگیِ گوگل است و `androidx.compose.runtime:runtime:1.6.0`ِ
     * خودش را می‌آورد. Composeِ ما مالِ جِت‌برینز است (۱.۸.۰) و **همان
     * بسته‌ٔ جاوا** را پر می‌کند: `androidx.compose.runtime`. نتیجه ۴۸۸
     * کلاسِ تکراری در بسته بود (شمرده شد، حدس نیست).
     *
     * Gradle این دو را یکی نمی‌کند چون `group`شان فرق دارد، پس هر دو jar
     * در بسته می‌نشینند و **ترتیبِ classpath تصمیم می‌گیرد کدام برنده
     * شود** — نه نسخه. اجرا از Gradle ۱.۸.۰ را اول می‌دید و کار می‌کرد؛
     * بستهٔ `jpackage` ۱.۶.۰ را اول می‌دید و برنامه سرِ شروع می‌مرد:
     *
     *     NoSuchMethodError: Composer.startReplaceGroup(int)
     *
     * (`startReplaceGroup` از ۱.۷ به بعد هست؛ ۱.۶ ندارد.)
     *
     * پس Composeِ گوگل برداشته می‌شود و فقط یکی می‌ماند. کدِ lifecycle
     * روی ۱.۸.۰ می‌نشیند چون همان androidx است، فقط چندسکویی منتشر شده.
     */
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7") {
        exclude(group = "androidx.compose.runtime", module = "runtime")
    }

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

    /*
     * ساختِ PDF روی ویندوز. جاوا خودش نویسندهٔ PDF ندارد.
     *
     * PDFBox شکل‌دهیِ متن ندارد و فارسی را نمی‌چسباند؛ برای همین متن
     * به‌صورتِ خطوطِ برداری کشیده می‌شود — توضیحش در `SheetPdf`.
     */
    implementation("org.apache.pdfbox:pdfbox:3.0.8")

    // روی اندروید `org.json` در خودِ سیستم است؛ اینجا نیست و باید
    // آورده شود، وگرنه سرورِ کارگاه سرِ اجرا می‌ترکد.
    implementation("org.json:json:20260719")
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

// همان دلیلِ `:core`: هدفِ بایت‌کد ۱۷ بدونِ اصرار بر نصبِ JDK 17، تا
// سینکِ Android Studio (که با JDK 21 می‌آید) نشکند.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

compose.desktop {
    application {
        mainClass = "com.afghanjama.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "KhayatYar"
            /*
             * با `versionName`ِ اندروید یکی می‌ماند: یک محصول است و اگر
             * دو عدد داشته باشد، پرسیدنِ «کدام نسخه را داری؟» بی‌جواب
             * می‌ماند.
             *
             * حالا هر دو از `gradle.properties` می‌آیند، پس «یکی ماندن»
             * دیگر چیزی نیست که کسی باید یادش بماند — یک عدد بیشتر
             * نیست. (پیش‌تر دو عددِ دستی بود و `versions.py` هم‌خوانی را
             * می‌سنجید؛ آن بررسی جای خود را به `appversion.py` داد.)
             *
             * **و نکته‌ای که آن بررسی نمی‌گرفت:** این عدد باید بالا
             * برود. ویندوز MSIِ تازه را وقتی جایگزینِ نصب‌شده می‌کند که
             * `ProductVersion` بیشتر باشد؛ با عددِ یکسان، نصب بی هیچ
             * خطایی رد می‌شود — روی پی‌سی‌ای که دفترِ حسابِ کارگاه رویش
             * است.
             */
            packageVersion = providers.gradleProperty("appVersion").get()
            /*
             * لاتین و بی نویسهٔ خاص، عمداً.
             *
             * `jpackage` این‌ها را در فراداده‌های خودِ فایلِ اجرایی
             * ویندوز می‌نشاند و آن‌جا نویسه‌های غیرِ ASCII می‌توانند
             * ساخت را بشکنند. نامی که کاربر می‌بیند از `AppInfo.NAME`
             * می‌آید و فارسی است؛ این فقط فراداده است.
             */
            description = "KhayatYar - tailoring workshop ledger"
            vendor = "KhayatYar"

            /*
             * **این خط جای یک فهرستِ دستی را گرفت، و دلیلش گران تمام شد.**
             *
             * قبلاً اینجا نوشته بود:
             *
             *     modules("java.sql", "java.naming", "java.logging", "jdk.unsupported")
             *
             * و `java.desktop` در آن نبود — ماژولی که `java.awt` در آن
             * است. یعنی همان ماژولی که **کلِ Compose Desktop** رویش
             * سوار است، به‌علاوهٔ چاپِ PDF و کلیپ‌بورد.
             *
             * نتیجه: `exe` ساخته می‌شد، CI سبز بود، و روی پی‌سیِ کارگاه
             * پیغامِ «Failed to launch JVM» می‌داد. توضیحِ همان خط دقیقاً
             * دربارهٔ این دام هشدار می‌داد و فهرست خودش در آن افتاده بود.
             *
             * **چرا حالا فهرستِ کامل و نه فقط افزودنِ `java.desktop`:**
             * فهرستِ دستی یعنی هر کتابخانه‌ای که فردا اضافه شود می‌تواند
             * همین را تکرار کند، و این خرابی **در CI دیده نمی‌شود** —
             * ساخت سبز است و فقط کاربر می‌فهمد. بستهٔ بزرگ‌تر (حدودِ ۵۰
             * مگابایت) بهایی است که برای حذفِ کلِ این دسته خرابی داده
             * می‌شود. برای پی‌سیِ کارگاه که یک بار دانلود می‌شود، این
             * معامله بدی نیست.
             */
            includeAllModules = true

            /*
             * مک — و چرا تقریباً هیچ کارِ تازه‌ای نخواست.
             *
             * `:desktop` همان ماژولِ JVM است که ویندوز را می‌سازد و
             * `:core` هیچ وابستگیِ اندرویدی ندارد، پس DMG فقط باید
             * **خواسته** می‌شد. `jpackage` روی مک بستهٔ `.app` را
             * می‌سازد و در یک DMG می‌گذارد.
             *
             * `bundleID` شناسهٔ برنامه نزدِ خودِ سیستم‌عامل است: مک با
             * همین تنظیمات، مجوزها و امضا را به برنامه گره می‌زند. اگر
             * روزی عوض شود، سیستم آن را برنامه‌ای **دیگر** می‌بیند —
             * همان نقشی که `upgradeUuid` روی ویندوز دارد. پس ثابت
             * می‌مانَد.
             *
             * لاتین و نقطه‌دار، عمداً: اپل قالبِ dns معکوس می‌خواهد و
             * نویسهٔ غیرِ ASCII را نمی‌پذیرد.
             *
             * **امضا اینجا نیست، و این عمدی است.** گواهیِ Developer ID
             * روی کمپیوترِ کارفرما می‌مانَد؛ نه در مخزن، نه در Secrets —
             * همان قاعده‌ای که برای کلیدِ اندروید و `.pfx`ِ ویندوز
             * گذاشته شده. امضا و notarize با
             * `tools/macos-signing/sign-and-notarize.sh` روی همان
             * کمپیوتر انجام می‌شود.
             */
            macOS {
                bundleID = "com.afghanjama.khayatyar"
                // نامی که در Finder و Launchpad دیده می‌شود. لاتین است
                // چون در نامِ فایلِ DMG هم می‌نشیند؛ نامِ فارسیِ داخلِ
                // برنامه از `AppInfo.NAME` می‌آید.
                packageName = "KhayatYar"
                dockName = "KhayatYar"
            }

            windows {
                menuGroup = "KhayatYar"
                // میان‌بر روی دسکتاپ و منوی شروع — کارگاه نباید دنبالِ
                // پوشهٔ نصب بگردد.
                shortcut = true
                dirChooser = true
                // با هر نسخهٔ تازه عوض نشود، وگرنه ویندوز به‌جای
                // به‌روزرسانی یک برنامهٔ دوم نصب می‌کند.
                upgradeUuid = "6E7B1F2C-9A54-4B8E-97C6-3D2A5B41E0F7"
            }
        }
    }
}

/*
 * `screenSmoke` — هر صفحهٔ ویندوز واقعاً ترکیب و رسم می‌شود.
 *
 * **چرا روی بستهٔ ساخته‌شده و نه روی classpathِ Gradle.** دو خرابیِ
 * واقعی نشان دادند که این دو با هم فرق دارند:
 *
 *   - اجرا از Gradle سبز بود و `KhayatYar.exe` سرِ شروع می‌مرد، چون دو
 *     `androidx.compose.runtime` در بسته بود و **ترتیبِ classpath** —
 *     نه نسخه — تصمیم می‌گرفت کدام بار شود.
 *
 * پس آنچه آزموده می‌شود باید همان چیزی باشد که به کارگاه می‌رود.
 *
 * `classpath` در `doFirst` بسته می‌شود چون پوشهٔ بسته پیش از اجرای
 * `createDistributable` وجود ندارد.
 *
 * خودِ پوشه هم در classpath است، نه فقط jarها: `skiko-windows-x64.dll`
 * و `.sha256`ِ کنارش فایلِ آزادند و بی آن‌ها skiko بالا نمی‌آید.
 */
/*
 * `duplicateClasses` — یک کلاس، یک jar.
 *
 * **خرابی‌ای که این را لازم کرد.** بسته دو `androidx.compose.runtime`
 * داشت: یکی از جِت‌برینز (۱.۸.۰) و یکی از گوگل (۱.۶.۰، از راهِ
 * `androidx.lifecycle:lifecycle-viewmodel-compose`). چون `group`شان فرق
 * دارد Gradle آن‌ها را یکی نمی‌کند و هیچ هشداری نمی‌دهد — ۴۸۸ کلاس با
 * نامِ یکسان در دو jar نشستند.
 *
 * نتیجه از هر خرابیِ معمولی بدتر است: **نسخه تصمیم نمی‌گیرد، ترتیبِ
 * classpath تصمیم می‌گیرد.** اجرا از Gradle کار می‌کرد و همان کد در
 * بستهٔ `jpackage` سرِ شروع می‌مرد. یعنی «روی ماشینِ من کار می‌کند» به
 * شکلِ خالصش.
 *
 * `META-INF` کنار گذاشته می‌شود: `module-info.class`ِ چندنسخه‌ای
 * (`META-INF/versions/9/…`) در ده‌ها jar هست و بی‌خطر است.
 */
/*
 * `packagedJarDir` — پوشهٔ jarهای بستهٔ ساخته‌شده، هر سیستم‌عاملی که باشد.
 *
 * همهٔ دودآزمایی‌های زیر روی **بسته** اجرا می‌شوند نه روی classpathِ
 * Gradle، و دلیلش در توضیحِ `screenSmoke` آمده. ولی مسیرِ آن بسته را
 * `jpackage` تعیین می‌کند و هر سکو جورِ دیگری می‌چیندش:
 *
 *     ویندوز/لینوکس   …/main/app/KhayatYar/app
 *     مک              …/main/app/KhayatYar.app/Contents/app
 *
 * مسیرِ ثابتِ ویندوزی که پیش‌تر در هشت جا کپی شده بود یعنی روی مک هر
 * هشت بررسی با «پوشهٔ بسته خالی است» قرمز می‌شوند — و آن قرمزی خرابیِ
 * برنامه نیست، خرابیِ خودِ بررسی است. بدترین شکلش هم این بود که پیامش
 * دقیقاً شبیهِ یک خرابیِ واقعیِ بسته‌بندی است.
 *
 * پس به‌جای مسیر، **دنبالِ پوشه‌ای می‌گردد که jar دارد**. یک تعریف در
 * یک جا، و سکوی بعدی هم چیزی برای عوض کردن ندارد.
 */
/*
 * **و چرا `val` با یک لامبدا، نه `fun`.**
 *
 * در اسکریپتِ Kotlin DSL، تابعِ سطحِ بالا به کلاسِ جدایی کامپایل
 * می‌شود و به گیرندهٔ ضمنیِ اسکریپت — یعنی خودِ `Project` — دسترسی
 * ندارد. آن‌جا `layout` اصلاً حل نمی‌شود.
 *
 * ولی **اعلانِ ویژگیِ** سطحِ بالا در بدنهٔ اسکریپت ارزیابی می‌شود و
 * گیرنده را دارد. مدرکش چند خط پایین‌تر است: `val duplicateClasses by
 * tasks.registering` هم دقیقاً از همین راه به `tasks` می‌رسد.
 *
 * پس لامبدا `layout` را از دامنهٔ اسکریپت می‌گیرد، و چون تا لحظهٔ
 * صدا زدن اجرا نمی‌شود، مسیر همچنان در زمانِ **اجرا** خوانده می‌شود
 * نه پیکربندی — همان چیزی که لازم است، چون پوشه پیش از
 * `createDistributable` وجود ندارد.
 */
val packagedJarDir: () -> JFile = {
    val root = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
    val hit = root.walkTopDown().maxDepth(4).firstOrNull { d ->
        d.isDirectory && d.name == "app" &&
            (d.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()).isNotEmpty()
    }
    hit ?: throw GradleException(
        "پوشهٔ بستهٔ حاویِ jar زیرِ $root پیدا نشد — بررسی پوچ می‌شد. " +
            "یعنی `createDistributable` چیزی نساخته است."
    )
}

val duplicateClasses by tasks.registering {
    group = "verification"
    description = "هیچ کلاسی نباید از دو jar بیاید"
    dependsOn("createDistributable")
    doLast {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") }
            ?: emptyArray()

        val owners = HashMap<String, MutableSet<String>>()
        for (j in jars) {
            val zip = ZipFile(j)
            try {
                val en = zip.entries()
                while (en.hasMoreElements()) {
                    val name: String = en.nextElement().name
                    if (name.endsWith(".class") && !name.startsWith("META-INF/")) {
                        owners.getOrPut(name) { LinkedHashSet<String>() }.add(j.name)
                    }
                }
            } finally {
                zip.close()
            }
        }

        val dupes = owners.filterValues { it.size > 1 }
        if (dupes.isEmpty()) {
            println("OK    no duplicate classes (${jars.size} jars, ${owners.size} classes)")
            return@doLast
        }

        // بر اساسِ جفتِ jar گروه می‌شود، وگرنه صدها خط یک‌جور چاپ می‌شود.
        val byPair = dupes.entries.groupBy { it.value.sorted() }
        // گزارش لاتین است چون در لاگِ CI خوانده می‌شود و صفحهٔ فرمانِ
        // ویندوز لزوماً UTF-8 نیست — همان جایی که باید خوانده شود.
        val lines = StringBuilder(
            "FAIL  ${dupes.size} classes come from more than one jar\n"
        )
        byPair.forEach { (pair, classes) ->
            lines.append("\n  ${classes.size} classes shared by:\n")
            pair.forEach { lines.append("    $it\n") }
            classes.take(3).forEach { lines.append("      e.g. ${it.key}\n") }
        }
        lines.append(
            "\n  Which one loads depends on classpath ORDER, not on version." +
                "\n  Remove one with exclude(...) in desktop/build.gradle.kts."
        )
        throw GradleException(lines.toString())
    }
}

/*
 * `migrationSmoke` — راهِ بالا بردنِ دفتر روی ویندوز واقعاً کار می‌کند.
 *
 * بررسیِ `migrationgap` فقط می‌گوید گام **نوشته** شده. این می‌گوید سرِ
 * اجرا **اجرا** می‌شود — روی یک فایلِ موقتی، با همان موتورِ کارگاه.
 *
 * جدا از `screenSmoke` است چون چیزِ دیگری را می‌سنجد و باید بتواند
 * مستقل قرمز شود.
 */
val migrationSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "مهاجرتِ دیتابیس روی ویندوز واقعاً اجرا می‌شود"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.data.MigrationSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

/*
 * `txSmoke` — مرزِ تراکنش روی ویندوز واقعاً تراکنش است.
 *
 * روی اندروید این کار را `androidx.room.withTransaction` می‌کند. اینجا
 * آن تابع وجود ندارد (`room-ktx` فقط اندروید است) و با موتورِ درایوری
 * دوباره ساخته شده — و تراکنشِ دست‌ساز چیزی نیست که «کامپایل شد»
 * ثابتش کند.
 *
 * چهار بند: ماندنِ commit، برگشتِ کامل سرِ استثنا، ادغامِ تراکنشِ
 * تودرتو، و برگشتِ بیرونی که نوشتهٔ درونی را هم برمی‌گرداند.
 */
val txSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "تراکنشِ ویندوز: همه یا هیچ، و تودرتو-امن"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.data.TxSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

/*
 * `selfTestRun` — خودآزمایی روی ویندوز اجرا می‌شود و سبز است.
 *
 * `screenSmoke` ثابت می‌کند صفحهٔ خودآزمایی **رسم** می‌شود. این ثابت
 * می‌کند **اجرا** می‌شود و همهٔ بندهایش قبول‌اند.
 *
 * بندِ آخرِ فهرستِ تحویل در `DELIVERY.md` همین است — «خودآزمایی و
 * سلامتِ داده اجرا شود و همه سبز باشد» — و تا امروز فقط برای گوشی
 * قابلِ انجام بود.
 */
val selfTestRun by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "خودآزماییِ کامل روی ویندوز اجرا می‌شود و همه سبز است"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.SelfTestRunKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

/*
 * `backupSmoke` — دفتر از دست نمی‌رود.
 *
 * بازیابی تنها جایی است که دفترِ کارگاه **بازنویسی** می‌شود. اگر فایلِ
 * خراب یا فایلِ نسخهٔ جلوتر پذیرفته شود، نتیجه‌اش پاک شدنِ همان چیزی
 * است که پشتیبان قرار بود نجاتش دهد.
 *
 * جدا از `migrationSmoke` است: آن می‌گوید دفتر بالا می‌آید، این می‌گوید
 * از دست نمی‌رود. باید بتوانند جدا قرمز شوند.
 */
/*
 * `authSmoke` — درِ ورود واقعاً قفل است.
 *
 * منطقِ `AuthViewModel` دست نخورد ولی لایهٔ ذخیره‌سازی‌اش از
 * `SharedPreferences` به `Settings` رفت تا ویندوز هم ورود داشته باشد.
 * یک گاردِ امنیتی که لایهٔ زیرینش عوض شده باید آزموده شود.
 */
val pdfSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "RTL base direction and the Afghani glyph on printed sheets"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.pdf.PdfSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

val lanSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "LAN address contract and shared-ledger identity"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.data.LanSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

val authSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "ورود، نقش و تغییرِ رمز روی تنظیماتِ موقت"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.data.AuthSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

val backupSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "پشتیبان‌گیری و بازیابیِ ویندوز روی فایل‌های موقت"
    dependsOn("createDistributable")
    mainClass.set("com.afghanjama.desktop.data.BackupSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") } ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}

val screenSmoke by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "هر صفحهٔ نوارِ کناری را در بستهٔ ویندوز ترکیب و رسم می‌کند"
    dependsOn("createDistributable")
    // اول تکراری‌ها: اگر دو نسخه از یک کلاس در بسته باشد، سبز شدنِ این
    // آزمون هیچ چیزی را ثابت نمی‌کند — همان اجرا با ترتیبِ دیگر می‌مرد.
    dependsOn(duplicateClasses)
    mainClass.set("com.afghanjama.desktop.ScreenSmokeKt")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
    // عکس‌ها در `build/screens` نسبت به همین پوشه نوشته می‌شوند.
    // صریح گذاشته شد چون پیش‌فرضِ `JavaExec` تضمین‌شده نیست و اگر
    // جای دیگری بیفتد، مرحلهٔ بارگذاریِ CI بی‌صدا خالی بالا می‌رود.
    workingDir = projectDir
    // بی این، خروجیِ فارسی روی رانرِ ویندوز `?` می‌شود و گزارشِ خطا
    // ناخواناست — همان چیزی که باید خوانده شود وقتی قرمز شد.
    doFirst {
        val appDir = packagedJarDir()
        val jars = appDir.listFiles { f: JFile -> f.name.endsWith(".jar") }
            ?: emptyArray()
        classpath = files(appDir) + files(*jars)
    }
}
