package com.afghanjama.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.platform.LocalWidgets
import com.afghanjama.desktop.data.DesktopLedger
import com.afghanjama.desktop.data.desktopSettings
import com.afghanjama.desktop.platform.DesktopDocsBridge
import com.afghanjama.desktop.platform.DesktopFileExport
import com.afghanjama.desktop.platform.DesktopSystemActions
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.desktop.platform.DesktopPhotos
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.theme.LightColors
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.UserRole

/*
 * دودآزمایی صفحه‌های ویندوز — هر بخشِ نوارِ کناری واقعاً ترکیب می‌شود.
 *
 * **چرا این فایل هست.** بینِ «کامپایل می‌شود» و «اجرا می‌شود» یک شکاف
 * بود که این پروژه چند بار در آن افتاد و هر بار فقط کاربر پیدایش کرد:
 *
 *   ۱. `java.desktop` در فهرستِ ماژول‌ها نبود → «Failed to launch JVM».
 *   ۲. آیکن‌ها در `:core` فقط `compileOnly` بودند → کامپایل سبز، و سرِ
 *      اجرا `NoClassDefFoundError` روی اولین صفحه‌ای که آیکن داشت.
 *   ۳. دو `androidx.compose.runtime` در یک بسته → برنامه از Gradle بالا
 *      می‌آمد و همان کد در بستهٔ `jpackage` سرِ شروع می‌مرد.
 *
 * هیچ‌کدام را بررسیِ ساختاری نمی‌گرفت، و آزمونِ زنده‌بودنِ CI هم نه: آن
 * فقط می‌پرسد «آیا فرایند بعد از ۲۵ ثانیه هنوز هست؟» و جوابش برای
 * برنامه‌ای که رشتهٔ اصلی‌اش مرده ولی AWT هنوز نفس می‌کشد **بله** است.
 *
 * اینجا هر بخش واقعاً ترکیب و یک فریم از آن رسم می‌شود. هر کلاسِ نبوده،
 * هر متدِ نبوده، و هر خطای ترکیب همان‌جا می‌ترکد و کدِ خروج غیرِ صفر
 * می‌شود.
 *
 * پنجره باز نمی‌شود (`ImageComposeScene` بی‌پنجره رسم می‌کند) پس روی
 * رانرِ بی‌صفحهٔ CI هم اجرا می‌شود.
 *
 * این `main`ِ دومِ ماژول است؛ `mainClass`ِ برنامه دست نخورده و
 * `KhayatYar.exe` همچنان `MainKt` را بالا می‌آورد.
 *
 * **فهرستِ بخش‌ها اینجا نیست، و این عمدی است.** روی `Section.entries`
 * گردش می‌کند و همان `SectionContent`ی را صدا می‌زند که خودِ پنجره صدا
 * می‌زند. پس بخشی که به نوار اضافه شود خودبه‌خود آزموده می‌شود و
 * نمی‌تواند از قلم بیفتد.
 */

private class Store : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * یک فریم از یک صفحه، با همان چیزهایی که `Main.kt` بالای صفحه‌ها
 * می‌گذارد. اگر اینجا کم گذاشته شود، آزمون چیزی را می‌سنجد که برنامه
 * نیست.
 */
/**
 * پوشه‌ای که عکسِ صفحه‌ها در آن می‌نشیند.
 *
 * **چرا عکس گرفته می‌شود.** تا امروز این آزمون فقط می‌گفت «ترکیب شد و
 * ترکید یا نه». ولی صفحه‌ای می‌تواند بی‌عیب رسم شود و همچنان غلط باشد:
 * متنِ بریده، ستونِ خفه‌شده، رنگی که خوانده نمی‌شود. هیچ‌کدام استثنا
 * نمی‌دهند.
 *
 * حالا هر فریم روی دیسک می‌نشیند و CI آن را به‌عنوانِ artifact بالا
 * می‌برد. یعنی بدونِ داشتنِ یک ویندوزِ واقعی می‌شود دید پنجره **چه
 * شکلی است**، نه فقط اینکه نمرد.
 */
private val shotDir: java.io.File by lazy {
    java.io.File("build/screens").apply { mkdirs() }
}

private fun renderOnce(
    settings: com.afghanjama.prefs.Settings,
    shotName: String? = null,
    body: @Composable () -> Unit
) {
    ImageComposeScene(
        width = 1280,
        height = 800,
        density = Density(1f)
    ) {
        MaterialTheme(colorScheme = LightColors, typography = vazirTypography()) {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                // هر صفحه صاحبِ تازه می‌گیرد تا حالتِ یکی به دیگری نشت
                // نکند و هر شکست به خودِ همان صفحه مربوط باشد.
                LocalViewModelStoreOwner provides Store(),
                LocalSettings provides settings,
                LocalSystemActions provides DesktopSystemActions(),
                LocalDocs provides DesktopDocsBridge(settings),
                LocalFileExport provides DesktopFileExport(),
                LocalPhotos provides DesktopPhotos(),
                // **هر چیزی که `Main.kt` می‌دهد، اینجا هم باید داده شود.**
                //
                // `LocalWidgets` بعد از نوشتنِ این آزمون به `:core` اضافه
                // شد (مرزِ سکو برای `DropdownMenu`، که کلاسش روی گوشی و
                // پی‌سی فرق دارد). آزمون آن را نمی‌داد، پس سه صفحه سرِ
                // اجرا با «LocalWidgets داده نشده» می‌افتادند — در حالی
                // که خودِ برنامه درست کار می‌کرد.
                //
                // درسش: این آزمون تا جایی ارزش دارد که **همان محیطی** را
                // بسازد که پنجرهٔ واقعی می‌سازد. هر Localِ تازه‌ای که به
                // `Main.kt` اضافه شود باید اینجا هم بیاید.
                LocalWidgets provides DesktopWidgets
            ) {
                body()
            }
        }
    }.use { scene ->
        // رسمِ واقعی، نه فقط ترکیب: چیدمان و کشیدن هم باید جواب بدهند،
        // وگرنه نیمی از خطاها دیده نمی‌شوند.
        val image = scene.render()

        // نوشتنِ عکس عمداً داخلِ `runCatching` است: اگر رمزگذاری روی
        // رانری شکست بخورد، نباید آزمونی را قرمز کند که کارش سنجیدنِ
        // **رسم** است نه ذخیره‌سازی.
        if (shotName != null) {
            runCatching {
                image.encodeToData()?.bytes?.let { bytes ->
                    java.io.File(shotDir, "$shotName.png").writeBytes(bytes)
                }
            }
        }
    }
}

fun main() {
    println("screen smoke - KhayatYar Windows")
    println("=".repeat(52))

    val repo = DesktopLedger.repo().getOrNull()
    if (repo == null) {
        println("FAIL  ledger did not open - no screen is testable without it")
        kotlin.system.exitProcess(1)
    }
    println("OK    ledger opened")

    val settings = desktopSettings()
    var failed = 0

    fun check(id: String, title: String, body: @Composable () -> Unit) {
        try {
            renderOnce(settings, id, body)
            println("OK    ${id.padEnd(16)} ($title)")
        } catch (t: Throwable) {
            failed++
            println("FAIL  ${id.padEnd(16)} ($title)")
            println("        ${t::class.java.name}: ${t.message}")
            t.stackTrace.take(4).forEach { println("        at $it") }
        }
    }

    for (s in Section.entries) {
        check(s.name.lowercase(), s.title) {
            SectionContent(s, repo, role = UserRole.MANAGER, go = {}, onOpenCustomer = {})
        }
    }

    // جزئیاتِ مشتری بخشِ نوار نیست (از دلِ «خریداران» باز می‌شود) پس در
    // گردشِ بالا نمی‌آید و باید جدا آزموده شود. شناسهٔ ۱ ممکن است در
    // دفترِ نو وجود نداشته باشد — و همان هم آزمونِ درستی است: صفحه باید
    // با دادهٔ خالی هم رسم شود، نه اینکه بترکد.
    check("customerdetail", "جزئیاتِ مشتری") {
        val vm: CustomerDetailViewModel = viewModel { CustomerDetailViewModel(repo) }
        CustomerDetailScreen(vm, customerId = 1L, onBack = {}, onOpenOrder = {})
    }

    val total = Section.entries.size + 1
    println("=".repeat(52))
    val shots = shotDir.listFiles { f: java.io.File -> f.name.endsWith(".png") }?.size ?: 0
    println("screenshots: $shots in ${shotDir.absolutePath}")
    if (failed == 0) {
        println("all $total screens composed and rendered")
    } else {
        println("$failed of $total screens FAILED")
    }
    kotlin.system.exitProcess(if (failed == 0) 0 else 1)
}
