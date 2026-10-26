package com.afghanjama.desktop

import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.unit.Density
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
import com.afghanjama.platform.LocalReminders
import com.afghanjama.desktop.platform.DesktopReminders
import com.afghanjama.desktop.platform.DesktopPhotos
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.ui.screens.CustomerDetailScreen
import com.afghanjama.ui.theme.AppTheme
import com.afghanjama.ui.vm.CustomerDetailViewModel
import com.afghanjama.ui.vm.AuthViewModel
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
    width: Int = 1280,
    height: Int = 800,
    /**
     * چند فریم. یک فریم یعنی «ترکیب شد»؛ ولی نشان‌های نوار (شمارِ هشدار)
     * از جریانی می‌آیند که پس از فریمِ اول می‌رسد. برای عکسِ پنجرهٔ کامل
     * فریمِ دوم هم رسم می‌شود تا عکس همان چیزی باشد که کاربر می‌بیند.
     */
    frames: Int = 1,
    body: @Composable () -> Unit
) {
    ImageComposeScene(
        width = width,
        height = height,
        density = Density(1f)
    ) {
        AppTheme(fontFamily = Vazirmatn, dark = System.getenv("KY_SMOKE_DARK") == "1") {
            CompositionLocalProvider(
                // هر صفحه صاحبِ تازه می‌گیرد تا حالتِ یکی به دیگری نشت
                // نکند و هر شکست به خودِ همان صفحه مربوط باشد.
                LocalViewModelStoreOwner provides Store(),
                LocalSettings provides settings,
                LocalSystemActions provides DesktopSystemActions(),
                LocalDocs provides DesktopDocsBridge(settings),
                LocalFileExport provides DesktopFileExport(),
                LocalPhotos provides DesktopPhotos(),
                LocalReminders provides DesktopReminders(),
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
        var image = scene.render()
        repeat(frames - 1) { i ->
            Thread.sleep(400)
            image = scene.render((i + 1) * 500_000_000L)
        }

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

    // `KY_SMOKE_SAMPLE=1`: پیش از رسم، کارگاهِ نمونه در همین دفترِ موقت —
    // تا عکس‌ها داشبورد و خانه را با عدد نشان دهند، نه فقط حالتِ خالی.
    // فقط روی دفترِ خالی؛ دفترِ آزمونِ قبلی دوباره پر نمی‌شود.
    if (System.getenv("KY_SMOKE_SAMPLE") == "1") {
        val made = kotlinx.coroutines.runBlocking {
            if (repo.observeAllOrders().first().isEmpty()) com.afghanjama.data.SampleWorkshop.create(repo) else null
        }
        println("OK    sample workshop ${if (made == null) "already there" else "created: ${made.orders} orders"}")
    }

    fun check(
        id: String,
        title: String,
        width: Int = 1280,
        height: Int = 800,
        frames: Int = 1,
        body: @Composable () -> Unit
    ) {
        try {
            renderOnce(settings, id, width, height, frames, body)
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

    // کلِ پنجره با نوارِ کنار — گردشِ بالا فقط محتوای هر بخش را رسم می‌کند،
    // پس جای بندها در نوار (مثلاً «اشتراکِ کارگاه» بالای آن) و کاشیِ صفحهٔ
    // اول هیچ‌جا دیده نمی‌شد.
    //
    // **در دو پهنا:** ۱۱۰۰ (کمترین اندازهٔ پنجره) و ۱۴۴۰ (پیش‌فرض). نوارِ
    // گروه‌بندی‌شده و خانهٔ چندستونه باید در هر دو جا شوند؛ عکسِ هر دو در
    // `build/screens` می‌ماند.
    val shellSizes = listOf(1100 to 700, 1440 to 900)
    for ((w, h) in shellSizes) {
        check("shell-$w", "نوارِ کنار و خانه در پهنای $w", w, h, frames = 2) {
            // با حساب، تا پایینِ نوار (نام، نقش، تغییرِ رمز، خروج) هم رسم شود.
            val auth: AuthViewModel = viewModel { AuthViewModel(settings) }
            Shell(repo, UserRole.MANAGER, auth = auth)
        }
    }

    // داشبورد (صفحهٔ اولِ ۱.۱۲) در پهنای بزرگِ ۱۹۲۰ — چهار ستون؛ ۱۱۰۰ و ۱۴۴۰
    // همان «shell-*» بالا است که حالا با داشبورد باز می‌شود.
    check("shell-1920", "داشبورد در پهنای 1920", 1920, 1080, frames = 3) {
        val auth: AuthViewModel = viewModel { AuthViewModel(settings) }
        Shell(repo, UserRole.MANAGER, auth = auth)
    }
    // «خانه» (کاشی‌های گوشی) هنوز هست — همان صفحه‌ای که پیش از ۱.۱۲ صفحهٔ اول بود.
    check("home-1440", "خانه در پهنای 1440", 1440, 900, frames = 2) {
        val auth: AuthViewModel = viewModel { AuthViewModel(settings) }
        val nav = androidx.compose.runtime.remember { DeskNav().also { it.go(Section.Home) } }
        Shell(repo, UserRole.MANAGER, com.afghanjama.ui.vm.Access.MANAGER, nav, auth)
    }
    // جست‌وجوی همه‌جا (⌘/Ctrl+K) روی داشبورد.
    check("palette-1440", "جست‌وجوی همه‌جا", 1440, 900, frames = 2) {
        val auth: AuthViewModel = viewModel { AuthViewModel(settings) }
        val nav = androidx.compose.runtime.remember { DeskNav().also { it.palette = true } }
        Shell(repo, UserRole.MANAGER, com.afghanjama.ui.vm.Access.MANAGER, nav, auth)
    }
    // داشبورد برای فروشنده: فقط کارت‌هایی که به صفحه‌شان راه دارد.
    check("dash-sales", "داشبورد برای فروشنده", 1440, 900, frames = 3) {
        SectionContent(Section.Dashboard, repo, role = UserRole.SALES, access = com.afghanjama.ui.vm.Access.forRole(UserRole.SALES), go = {}, onOpenCustomer = {})
    }

    // نوار برای نقشی که بیشترِ بخش‌ها را ندارد — گروه‌های خالی نباید سرگروهِ
    // تنها بکشند.
    check("shell-sales", "نوارِ کنار برای فروشنده", 1100, 700, frames = 2) {
        Shell(repo, UserRole.SALES)
    }

    // «کارگاهِ وصل» در حالِ کار: کامپیوترِ مرکز روی loopback، یک گوشی منتظرِ
    // «اجازه»، سه دستگاهِ جفت‌شده و درخواست‌های منتظر (یکی با ساعتِ نامعتبر).
    // گردشِ بالا همین بخش را بی مرکز رسم کرده؛ این آنچه کارفرما هر روز می‌بیند.
    val hubShots = hubScenario(repo, settings)
    for ((w, h) in shellSizes) {
        check("hub-$w", "کارگاهِ وصل در پهنای $w", w, h, frames = 3) {
            HubSection(repo, com.afghanjama.ui.vm.Access.MANAGER, onBack = {}, hub = hubShots.hub, lan = hubShots.host)
        }
    }
    hubShots.hub.stop()

    // صفحهٔ ورود، با دستگاهی که رمزِ مدیر و سه کاربر دارد — در هر دو پهنا.
    // تنظیماتِ جدا (پوشهٔ موقت)، تا رمزِ آزمونی به تنظیماتِ صفحه‌های بالا نرسد.
    val loginSettings = loginScenario()
    for ((w, h) in shellSizes) {
        check("login-$w", "ورود در پهنای $w", w, h, frames = 2) {
            val auth: AuthViewModel = viewModel { AuthViewModel(loginSettings) }
            com.afghanjama.ui.screens.LoginScreen(auth, onLoggedIn = {})
        }
    }

    // پس از زدنِ کاشیِ «احمد»: رمزِ خودش، با صفحه‌کلیدِ عددی و خطِ نقش.
    check("login-pin-1440", "رمزِ یک نفر پس از زدنِ کاشی", 1440, 900, frames = 2) {
        val auth: AuthViewModel = viewModel { AuthViewModel(loginSettings).also { it.select("u1") } }
        com.afghanjama.ui.screens.LoginScreen(auth, onLoggedIn = {})
    }
    // بارِ اولِ دستگاه: «نخستین مدیرِ این دستگاه» — نام و رمز، بی پرسیدنِ نقش.
    check("login-first-1100", "بارِ اول: نخستین مدیر", 1100, 700, frames = 2) {
        val fresh = com.afghanjama.desktop.data.FileSettings(java.nio.file.Files.createTempDirectory("ky-first-shot").toFile())
        val auth: AuthViewModel = viewModel { AuthViewModel(fresh) }
        com.afghanjama.ui.screens.LoginScreen(auth, onLoggedIn = {})
    }

    val total = Section.entries.size + 1 + shellSizes.size + 1 + shellSizes.size + shellSizes.size + 2 + 4
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

private class ShotHost(private val db: com.afghanjama.data.Db) : com.afghanjama.lan.LanHost {
    override val kind: com.afghanjama.lan.DeviceKind get() = com.afghanjama.lan.DeviceKind.COMPUTER
    override val deviceName: String = "کامپیوترِ کارگاه"
    override fun localIp(): String = "192.168.77.7"
    override fun localIps(): List<String> = listOf("192.168.77.7", "10.0.0.12")
    override fun networks(): Map<String, com.afghanjama.lan.LanNetwork> =
        mapOf("192.168.77.7" to com.afghanjama.lan.LanNetwork("192.168.77.0/24", "192.168.77.1"))
    override fun ledger(): com.afghanjama.data.Db = db
}

private class HubShots(val hub: com.afghanjama.lan.LanHub, val host: ShotHost)

/** کامپیوترِ مرکزِ آزمایشی روی loopback، با دستگاه و درخواست و گوشیِ منتظر. */
private fun hubScenario(repo: com.afghanjama.data.repo.Repo, settings: com.afghanjama.prefs.Settings): HubShots =
    kotlinx.coroutines.runBlocking {
        val db = DesktopLedger.db().getOrThrow()
        val host = ShotHost(db)
        com.afghanjama.prefs.LanPrefs.setMode(settings, com.afghanjama.prefs.DeviceMode.MAIN)
        com.afghanjama.prefs.LanPrefs.setCode(settings, "482913")
        com.afghanjama.prefs.LanPrefs.setNetwork(settings, host.networks().values.first().encode())
        val port = java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { it.localPort }
        val hub = com.afghanjama.lan.LanHub(java.net.InetAddress.getLoopbackAddress(), port, licenceLoaded = {}, machine = { "screen" })
        hub.start(settings, host)
        repeat(100) { if (hub.hubService()?.ready != true) kotlinx.coroutines.delay(30) }
        val now = System.currentTimeMillis()
        fun dev(id: String, name: String, role: com.afghanjama.lan.HubRole, person: String, seen: Long?, ver: String) =
            com.afghanjama.data.entities.LanDevice(
                deviceId = id, name = name, platform = "android", role = role.wire, personLabel = person,
                keyB64 = com.afghanjama.lan.LanCrypto.b64(com.afghanjama.lan.LanPrims.random(32)),
                createdAt = now - 86_400_000L, lastSeenAt = seen, lastIp = "192.168.77.21", appVersion = ver,
            )
        if (repo.lanDevice("shot-owner-0001") == null) {
            repo.addLanDevice(dev("shot-owner-0001", "گوشیِ صاحب‌کار", com.afghanjama.lan.HubRole.OWNER, "", now - 5_000, com.afghanjama.AppVersion.current))
            repo.addLanDevice(dev("shot-worker-001", "گوشیِ احمد", com.afghanjama.lan.HubRole.WORKER, "[T1] احمد", now - 40 * 60_000L, "1.10.0"))
            repo.addLanDevice(dev("shot-wall-00001", "تابلوی سالن", com.afghanjama.lan.HubRole.WALL, "", now - 3_000, com.afghanjama.AppVersion.current))
            db.syncRequestDao().insert(
                com.afghanjama.data.entities.SyncRequest(
                    deviceName = "گوشیِ احمد", worker = "[T1] احمد", type = "ATTENDANCE_IN", summary = "درخواستِ ثبتِ ورود",
                    createdAt = now - 60_000, receivedAt = now - 60_000, clientAt = now - 3 * 86_400_000L, protocol = 2, clientKey = "shot-k1",
                )
            )
            db.syncRequestDao().insert(
                com.afghanjama.data.entities.SyncRequest(
                    deviceName = "گوشیِ احمد", worker = "[T1] احمد", type = "NOTE", summary = "پیام", note = "فردا دیرتر می‌آیم.",
                    createdAt = now - 30_000, receivedAt = now - 30_000, protocol = 2, clientKey = "shot-k2",
                )
            )
        }
        // دفترِ موقتِ آزمون از بارِ پیش می‌ماند: «آخرین دیدار» هر بار تازه.
        repo.touchLanDevice("shot-owner-0001", now - 5_000, "192.168.77.21", com.afghanjama.AppVersion.current)
        repo.touchLanDevice("shot-wall-00001", now - 3_000, "192.168.77.22", com.afghanjama.AppVersion.current)
        // یک گوشی همین حالا «افزودنِ گوشی» را اسکن کرده و منتظرِ «اجازه» است.
        val view = hub.openPairing()
        if (view != null) {
            val link = com.afghanjama.lan.PairingV2.parse(view.qrText)!!
            com.afghanjama.lan.HubClient(com.afghanjama.lan.hubTransport(allowLoopback = true)).requestPair(
                "127.0.0.1", port, com.afghanjama.lan.PairVia.QR, link.secret,
                com.afghanjama.lan.PhoneInfo("Galaxy A15", "android", com.afghanjama.AppVersion.current, com.afghanjama.lan.HubRole.WORKER), link,
            )
        }
        HubShots(hub, host)
    }

/**
 * دستگاهی با رمزِ مدیر («کریم») و سه نفر که مدیر ساخته — برای عکسِ صفحهٔ ورود.
 * پوشهٔ موقت، تا هیچ رمزی به تنظیماتِ واقعی یا صفحه‌های دیگرِ آزمون نرسد.
 */
private fun loginScenario(): com.afghanjama.prefs.Settings {
    val dir = java.nio.file.Files.createTempDirectory("ky-login-shot").toFile()
    val s = com.afghanjama.desktop.data.FileSettings(dir)
    s.putString("auth_prefs", "pin", com.afghanjama.util.PinHash.hash("1379"))
    s.putString("auth_prefs", "role", UserRole.MANAGER.name)
    s.putString("auth_prefs", "user_name", "کریم")
    s.putBoolean("auth_prefs", "logged_in", false)
    fun u(id: String, name: String, vararg f: com.afghanjama.ui.vm.Feature) =
        com.afghanjama.ui.vm.AppUser(id, name, com.afghanjama.util.PinHash.hash("2$id".takeLast(4).padStart(4, '7')), f.toSet())
    com.afghanjama.ui.vm.UserStore.save(
        s,
        listOf(
            u("u1", "احمد", com.afghanjama.ui.vm.Feature.SEWING, com.afghanjama.ui.vm.Feature.BOARD),
            u("u2", "فریده", com.afghanjama.ui.vm.Feature.SALES, com.afghanjama.ui.vm.Feature.CUSTOMERS),
            u("u3", "سمیع", com.afghanjama.ui.vm.Feature.CUTTING, com.afghanjama.ui.vm.Feature.BOARD),
        ),
    )
    return s
}
