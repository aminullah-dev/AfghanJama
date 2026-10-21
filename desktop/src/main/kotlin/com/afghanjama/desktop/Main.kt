package com.afghanjama.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Wifi
import com.afghanjama.desktop.platform.DesktopHubSystem
import com.afghanjama.desktop.platform.HubDeskPrefs
import com.afghanjama.prefs.DeviceMode
import com.afghanjama.prefs.LanPrefs
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.afghanjama.desktop.platform.DeskReminderLoop
import com.afghanjama.desktop.platform.DesktopReminders
import com.afghanjama.platform.LocalReminders
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.afghanjama.AppInfo
import com.afghanjama.AppVersion
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.desktop.data.DesktopAutoBackup
import com.afghanjama.desktop.data.DesktopLanHost
import com.afghanjama.desktop.data.DesktopLedger
import com.afghanjama.desktop.data.desktopSettings
import com.afghanjama.desktop.platform.DesktopDocsBridge
import com.afghanjama.desktop.platform.DesktopFileExport
import com.afghanjama.desktop.platform.DesktopPhotos
import com.afghanjama.desktop.platform.DesktopSystemActions
import com.afghanjama.lan.LanHub
import com.afghanjama.licence.Licensing
import com.afghanjama.platform.LocalDocs
import com.afghanjama.platform.LocalFileExport
import com.afghanjama.platform.LocalPhotos
import com.afghanjama.platform.LocalSystemActions
import com.afghanjama.prefs.LocalSettings
import com.afghanjama.prefs.SalePrefs
import com.afghanjama.prefs.Settings
import com.afghanjama.ui.components.LicenceHeartbeat
import com.afghanjama.ui.nav.allowedFor
import com.afghanjama.ui.platform.LocalWidgets
import com.afghanjama.ui.screens.LoginScreen
import com.afghanjama.ui.theme.AppTheme
import com.afghanjama.ui.vm.AuthUi
import com.afghanjama.ui.vm.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * جایی که ViewModelهای کلِ برنامه زندگی می‌کنند — فقط «ورود».
 *
 * روی اندروید این را خودِ اندروید می‌دهد (Activity یک
 * `ViewModelStoreOwner` است). روی دسکتاپ چنین چیزی از آسمان نمی‌آید و
 * باید صریح داده شود، وگرنه `viewModel { }` سرِ اجرا می‌ترکد. صفحه‌های پسِ
 * ورود صاحبِ جلسهٔ خودشان را دارند (`SignedInGate`).
 */
private object DesktopViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/**
 * نامی که روی نوارِ پنجره می‌نشیند — «نسخهٔ مک» یا «نسخهٔ ویندوز».
 *
 * تا دیروز اینجا «نسخهٔ ویندوز» سفت نوشته شده بود و پنجرهٔ مک خودش را
 * «نسخهٔ ویندوز» معرفی می‌کرد.
 */
internal val platformName: String = run {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        os.contains("mac") || os.contains("darwin") -> "نسخهٔ مک"
        os.contains("win") -> "نسخهٔ ویندوز"
        else -> "نسخهٔ رومیزی"
    }
}

/** منوی بالای صفحه فقط روی مک: روی ویندوز `MenuBar` داخلِ خودِ پنجره می‌نشیند. */
internal val isMac: Boolean =
    System.getProperty("os.name").orEmpty().lowercase().let { it.contains("mac") || it.contains("darwin") }

/** اندازهٔ پیش‌فرضِ پنجره، و کمترین اندازه‌ای که نوارِ کنار و کار با هم جا شوند. */
private val DEFAULT_SIZE = DpSize(1440.dp, 900.dp)
private const val MIN_W = 1100
private const val MIN_H = 700

fun main() {
    // **یک** نمونه از تنظیمات برای کلِ برنامه. `FileSettings` هر فایل را در
    // حافظهٔ خودش نگه می‌دارد؛ اگر صفحه‌ها و اشتراکِ کارگاه هر کدام نمونهٔ
    // خودشان را داشتند، چیزی که یکی به یاد می‌سپرد دیگری نمی‌دید.
    val settings = desktopSettings()

    // سیاستِ فروش («فروشِ بیشتر از موجودی») از همان اول، پیش از هر ثبتی —
    // همان کاری که `App.onCreate` روی گوشی می‌کند. تا ۱.۹ کامپیوتر این را
    // نمی‌خواند و تا صفحه‌ای آن را نمی‌خواند، پیش‌فرض را به کار می‌برد.
    SalePrefs.allowNegativeStock(settings)
    DeskIdle.minutes.value = DeskPrefs.idleLockMinutes(settings)

    // اشتراکِ کارگاه — اگر این کامپیوتر دستگاهِ اصلی است، همین که لایسنس
    // خوانده شد و کامپیوتر روی شبکهٔ کارگاه بود، خودش روشن می‌شود.
    LanHub.shared.autoStart(settings, DesktopLanHost())

    application { MainWindow(settings) }
}

/**
 * فضای قابلِ استفادهٔ صفحه (بی نوارِ وظیفه و Dock). روی رانرِ بی‌صفحه
 * استثنا می‌دهد؛ آنجا اندازهٔ پیش‌فرض.
 */
private fun usableScreen(): Rectangle = runCatching {
    GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
}.getOrElse { Rectangle(0, 0, DEFAULT_SIZE.width.value.toInt(), DEFAULT_SIZE.height.value.toInt()) }

/**
 * اندازه و جای پنجره در شروع.
 *
 * - **اندازهٔ پیش‌فرضِ Compose کوچک است** و تا ۱.۹ همان به کار می‌رفت؛
 *   «اشتراکِ کارگاه» زیرِ لبهٔ پنجره می‌ماند.
 * - ولی ۱۴۴۰×۹۰۰ از صفحهٔ لپ‌تاپِ ۱۳۶۶×۷۶۸ بزرگ‌تر است. پس اندازه به
 *   صفحه بریده می‌شود، و اگر بارِ اول جا نشد، پنجره تمام‌صفحه باز می‌شود.
 * - اندازه و جای آخرین بار به یاد می‌ماند؛ جایی که دیگر روی هیچ صفحه‌ای
 *   نیست (مانیتورِ کنده‌شده) نادیده گرفته می‌شود.
 */
private fun initialWindowState(settings: Settings): WindowState {
    val screen = usableScreen()
    val saved = DeskPrefs.bounds(settings)
    val wantW = saved?.width ?: DEFAULT_SIZE.width.value.toInt()
    val wantH = saved?.height ?: DEFAULT_SIZE.height.value.toInt()
    val fits = wantW <= screen.width && wantH <= screen.height
    val size = DpSize(wantW.coerceAtMost(screen.width).dp, wantH.coerceAtMost(screen.height).dp)
    val position = saved?.let { b ->
        val x = b.x
        val y = b.y
        if (x != null && y != null && onSomeScreen(x, y, b.width, b.height)) WindowPosition(x.dp, y.dp)
        else null
    } ?: WindowPosition(Alignment.Center)
    val placement = when {
        saved?.maximized == true -> WindowPlacement.Maximized
        saved == null && !fits -> WindowPlacement.Maximized
        else -> WindowPlacement.Floating
    }
    return WindowState(placement = placement, position = position, size = size)
}

/** دست‌کم ۱۰۰ نقطه از پنجره روی یکی از صفحه‌ها می‌افتد؟ */
private fun onSomeScreen(x: Int, y: Int, w: Int, h: Int): Boolean = runCatching {
    val win = Rectangle(x, y, w, h)
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { d ->
        val r = d.defaultConfiguration.bounds.intersection(win)
        !r.isEmpty && r.width >= 100 && r.height >= 100
    }
}.getOrDefault(false)

@Composable
private fun ApplicationScope.MainWindow(settings: Settings) {
    val windowState = remember { initialWindowState(settings) }
    // «کارگاهِ وصل»: پنجره می‌تواند پنهان شود و کارگاه در سینی روشن بماند.
    var visible by remember { mutableStateOf(true) }
    var askClose by remember { mutableStateOf(false) }
    val hub by LanHub.shared.state.collectAsState()
    // جای کاربر در پنجره — بیرونِ `Window`، تا میان‌برهای صفحه‌کلید (⌘/Ctrl+K و D)
    // هم به آن برسند.
    val nav = remember { DeskNav() }
    ReminderTray(windowState)
    HubTray(
        hidden = !visible,
        serving = hub.serving,
        onOpen = {
            visible = true
            windowState.isMinimized = false
        },
        onQuit = {
            LanHub.shared.stop()
            exitApplication()
        },
    )
    // کارگاه در پنهانی خاموش شد (شبکهٔ دیگر، دفترِ تازه): پنجره دوباره پیدا شود.
    LaunchedEffect(hub.serving) {
        if (!hub.serving && !visible) visible = true
        DesktopHubSystem.keepAwake(hub.serving && HubDeskPrefs.keepAwake(settings))
    }
    Window(
        onCloseRequest = {
            val hosting = LanPrefs.mode(settings) == DeviceMode.MAIN && LanHub.shared.state.value.serving
            when {
                !hosting -> exitApplication()
                HubDeskPrefs.closeChoice(settings) == HubDeskPrefs.KEEP && isTraySupported -> visible = false
                HubDeskPrefs.closeChoice(settings) == HubDeskPrefs.QUIT -> exitApplication()
                else -> askClose = true
            }
        },
        visible = visible,
        state = windowState,
        // نسخه و کامیت روی نوارِ پنجره — همان چیزی که پشتیبانی اول می‌پرسد.
        title = "${AppInfo.NAME} — ${AppVersion.short()} — $platformName",
        // هر کلیدی یعنی کسی پشتِ کامپیوتر است (قفلِ خودکار). رویداد را
        // نمی‌خورد؛ فقط می‌بیند.
        onPreviewKeyEvent = { e ->
            DeskIdle.touch()
            deskShortcut(e, nav)
        }
    ) {
        LaunchedEffect(Unit) {
            val screen = usableScreen()
            window.minimumSize = Dimension(MIN_W.coerceAtMost(screen.width), MIN_H.coerceAtMost(screen.height))
        }
        RememberBounds(windowState, settings)

        // **همان تمِ گوشی و آیفون** ([AppTheme]): پالت، قلم، گوشه‌ها و
        // راست‌به‌چپ از `:core`. کامپیوتر فعلاً فقط روشن است.
        AppTheme(fontFamily = Vazirmatn) {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides DesktopViewModelStoreOwner,
                // صفحه‌های مشترک تنظیمات را از اینجا می‌گیرند، همان‌طور
                // که روی اندروید از `MainActivity`.
                LocalSettings provides settings,
                LocalSystemActions provides DesktopSystemActions(),
                LocalDocs provides DesktopDocsBridge(settings),
                LocalFileExport provides DesktopFileExport(),
                LocalPhotos provides DesktopPhotos(),
                // یادآورِ شیفت و وقتِ نان و چای تا وقتی برنامه باز است.
                LocalReminders provides DesktopReminders(),
                LocalWidgets provides DesktopWidgets
            ) {
                // وضعیتِ لایسنس — پیش از ورود، تا درِ نوشتن از همان ثانیهٔ
                // اول درست بداند.
                LicenceHeartbeat(remember { Licensing(settings, DesktopLedger.repo().getOrNull()) })

                if (askClose) {
                    CloseWhileHosting(
                        settings,
                        onKeep = {
                            askClose = false
                            if (isTraySupported) visible = false else windowState.isMinimized = true
                        },
                        onQuit = {
                            askClose = false
                            exitApplication()
                        },
                        onCancel = { askClose = false },
                    )
                }

                // پشتیبانِ خودکارِ روزانه — از همان باز شدنِ پنجره، پیش از
                // ورودِ کسی. شکست یا افتادن به پوشهٔ پیش‌فرض گفته می‌شود؛
                // بارِ موفقِ عادی بی‌صدا است (هر روز یک بار، خبر نیست).
                LaunchedEffect(Unit) {
                    DesktopAutoBackup.loop(settings) { r ->
                        r.onSuccess { run ->
                            if (run.usedFallback) {
                                DeskNotices.show(
                                    "پوشهٔ پشتیبانِ خودکار در دسترس نبود؛ پشتیبان در پوشهٔ پیش‌فرض نوشته شد."
                                )
                            }
                        }.onFailure {
                            DeskNotices.show("پشتیبانِ خودکار گرفته نشد: ${it.message}", sticky = true)
                        }
                    }
                }

                // یادآورها از دفتر می‌پرسند چه کسی داخل است؛ بی دفتر کاری ندارند.
                // پشتِ صفحهٔ ورود می‌ایستند (`SignedInGate`).
                LaunchedEffect(Unit) {
                    DesktopLedger.repo().getOrNull()?.let { DeskReminderLoop.run(it, settings) }
                }

                // درِ ورود: نقش از `AuthViewModel` می‌آید، نه از فرض.
                val authVm: AuthViewModel = viewModel { AuthViewModel(settings) }
                val auth by authVm.ui.collectAsState()

                if (isMac) DeskMenuBar(nav, auth, authVm, windowState)

                // کسی که دوباره وارد می‌شود سرِ جای قبلی‌اش برمی‌گردد (مثلاً
                // پس از قفلِ خودکار)؛ کسِ دیگری از «داشبورد» شروع می‌کند.
                var lastUser by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(auth.isLoggedIn, auth.userId, auth.role) {
                    nav.signedIn = auth.isLoggedIn
                    if (!auth.isLoggedIn) nav.palette = false
                    if (auth.isLoggedIn) {
                        val who = "${auth.userId}|${auth.role}"
                        if (who != lastUser) nav.go(Section.Dashboard)
                        lastUser = who
                    }
                }

                IdleLock(active = auth.isLoggedIn, onLock = { authVm.logout() })

                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // هر حرکت و کلیکِ موس — بی آنکه رویداد خورده شود.
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    DeskIdle.touch()
                                }
                            }
                        }
                ) {
                    // هر ورود یک جلسهٔ تازه: فاکتورِ نیمه‌کاره و بقیهٔ حالِ صفحه‌ها
                    // با «خروج» و قفلِ خودکار می‌رود و به نفرِ بعدی نمی‌رسد.
                    SignedInGate(
                        signedIn = auth.isLoggedIn,
                        // `onLoggedIn` لازم نیست کاری کند: حالت خودش عوض
                        // می‌شود و بازترکیب صفحه را رد می‌کند.
                        login = { LoginScreen(authVm, onLoggedIn = {}) },
                    ) {
                        Shell(DesktopLedger.repo().getOrNull(), auth.role, auth.access, nav, authVm)
                    }
                }
            }
        }
    }
}

/**
 * اندازه و جای پنجره را پس از هر جابه‌جایی (با کمی درنگ) به یاد می‌سپارد.
 *
 * در حالتِ تمام‌صفحه فقط «تمام‌صفحه بود» نوشته می‌شود و اندازهٔ شناورِ
 * قبلی دست نمی‌خورد، تا برگشتن از تمام‌صفحه به همان اندازه برگردد.
 */
@Composable
private fun RememberBounds(state: WindowState, settings: Settings) {
    LaunchedEffect(state) {
        snapshotFlow { Triple(state.size, state.position, state.placement) }
            .collectLatest { (size, pos, placement) ->
                delay(600)
                val prev = DeskPrefs.bounds(settings)
                when (placement) {
                    WindowPlacement.Floating -> DeskPrefs.saveBounds(
                        settings,
                        DeskPrefs.Bounds(
                            width = size.width.value.toInt(),
                            height = size.height.value.toInt(),
                            x = if (pos.isSpecified) pos.x.value.toInt() else null,
                            y = if (pos.isSpecified) pos.y.value.toInt() else null,
                            maximized = false,
                        )
                    )
                    else -> DeskPrefs.saveBounds(
                        settings,
                        (prev ?: DeskPrefs.Bounds(
                            DEFAULT_SIZE.width.value.toInt(), DEFAULT_SIZE.height.value.toInt(),
                            null, null, false
                        )).copy(maximized = true)
                    )
                }
            }
    }
}

/**
 * قفلِ خودکار: اگر [DeskIdle.minutes] دقیقه هیچ کلید و موسی نیامد، از
 * حساب بیرون می‌رود. صفر یعنی خاموش (پیش‌فرض).
 */
@Composable
private fun IdleLock(active: Boolean, onLock: () -> Unit) {
    val minutes by DeskIdle.minutes.collectAsState()
    LaunchedEffect(active, minutes) {
        if (!active || minutes <= 0) return@LaunchedEffect
        DeskIdle.touch()
        while (true) {
            delay(15_000)
            if (System.currentTimeMillis() - DeskIdle.last >= minutes * 60_000L) {
                onLock()
                break
            }
        }
    }
}

/**
 * منوی بالای صفحهٔ مک — «پرونده»، «نما»، «پنجره»، «راهنما».
 *
 * فقط مک: روی ویندوز `MenuBar` نوارِ جدایی داخلِ خودِ پنجره می‌سازد که با
 * نوارِ کنار تکراری است. هر بند همان بندِ نوارِ کنار است و همان دسترسی را
 * می‌خواهد.
 */
@Composable
private fun FrameWindowScope.DeskMenuBar(
    nav: DeskNav,
    auth: AuthUi,
    authVm: AuthViewModel,
    state: WindowState,
) {
    val inside = auth.isLoggedIn
    fun can(s: Section) = inside && s.allowedFor(auth.access)
    MenuBar {
        Menu("پرونده") {
            Item("پشتیبان و بازیابی", enabled = can(Section.Backup), onClick = { nav.go(Section.Backup) })
            Item("تنظیمات", enabled = can(Section.Settings), onClick = { nav.go(Section.Settings) })
            Separator()
            Item("خروج از حساب", enabled = inside, onClick = { authVm.logout() })
        }
        Menu("نما") {
            Item(
                "جست‌وجو…", enabled = inside,
                shortcut = androidx.compose.ui.input.key.KeyShortcut(androidx.compose.ui.input.key.Key.K, meta = true),
                onClick = { nav.palette = true },
            )
            Separator()
            listOf(Section.Dashboard, Section.Home, Section.ActionCenter, Section.WorkshopLink, Section.OrderSearch).forEach { s ->
                Item(s.title, enabled = can(s), onClick = { nav.go(s) })
            }
        }
        Menu("پنجره") {
            Item("کوچک کردن", onClick = { state.isMinimized = true })
            Item(
                if (state.placement == WindowPlacement.Maximized) "اندازهٔ قبلی" else "تمام‌صفحه",
                onClick = {
                    state.placement =
                        if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating
                        else WindowPlacement.Maximized
                }
            )
        }
        Menu("راهنما") {
            Item(Section.Guide.title, enabled = can(Section.Guide), onClick = { nav.go(Section.Guide) })
            Item(Section.About.title, enabled = can(Section.About), onClick = { nav.go(Section.About) })
        }
    }
}

/**
 * نشانِ سینی (نوارِ وظیفهٔ ویندوز، نوارِ منوی مک) — فقط وقتی یادآوری
 * خوانده‌نشده هست، و پیامِ سیستم با هر یادآورِ تازه.
 *
 * یادآور در خودِ پنجره هم هست؛ این برای وقتی است که پنجره کوچک شده یا
 * پشتِ برنامهٔ دیگری است. اگر سیستم سینی ندارد، فقط همان پیامِ پنجره.
 */
@Composable
private fun ApplicationScope.ReminderTray(windowState: WindowState) {
    if (!isTraySupported) return
    val notices by DeskNotices.items.collectAsState()
    val pending = notices.any { it.sticky }
    val trayState = rememberTrayState()
    val showing by rememberUpdatedState(pending)
    LaunchedEffect(Unit) {
        DeskReminderLoop.fired.collect { due ->
            // نشانِ سینی تازه ساخته می‌شود؛ پیام پیش از ساخته شدنش گم می‌شد.
            snapshotFlow { showing }.first { it }
            delay(300)
            trayState.sendNotification(Notification(due.title, due.text, Notification.Type.Info))
        }
    }
    if (pending) {
        Tray(
            icon = rememberVectorPainter(Icons.Default.NotificationsActive),
            state = trayState,
            tooltip = "${AppInfo.NAME} — یادآور",
            onAction = { windowState.isMinimized = false },
            menu = {
                Item("باز کردنِ ${AppInfo.NAME}", onClick = { windowState.isMinimized = false })
                Item("خواندم", onClick = { DeskNotices.dismissSticky() })
            }
        )
    }
}

/**
 * «بسته شود یا کارگاه روشن بماند؟» — بارِ اولی که پنجرهٔ مرکزِ روشن بسته
 * می‌شود (طرح، B.9؛ سؤالِ ۱۰). پیشنهاد «روشن بماند» است: گوشی‌ها به این
 * کامپیوتر وصل‌اند و با بسته شدنش کارشان در صفِ گوشی می‌ماند.
 */
@Composable
private fun CloseWhileHosting(settings: Settings, onKeep: () -> Unit, onQuit: () -> Unit, onCancel: () -> Unit) {
    var remember by remember { mutableStateOf(false) }
    com.afghanjama.ui.platform.AppAlertDialog(
        onDismissRequest = onCancel,
        title = { androidx.compose.material3.Text("بسته شود یا کارگاه روشن بماند؟") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text(
                    "گوشی‌های کارگاه به این کامپیوتر وصل‌اند. اگر برنامه بسته شود، تا دوباره باز نشود کارهای گوشی‌ها در صفِ " +
                        "خودشان می‌ماند. «روشن بماند» پنجره را می‌بندد و کارگاه را در " +
                        (if (isMac) "نوارِ منو" else "سینیِ نوارِ وظیفه") + " روشن نگه می‌دارد."
                )
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = remember, onCheckedChange = { remember = it })
                    androidx.compose.material3.Text("دیگر نپرس")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (remember) HubDeskPrefs.setCloseChoice(settings, HubDeskPrefs.KEEP)
                onKeep()
            }) { androidx.compose.material3.Text("کارگاه روشن بماند") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                if (remember) HubDeskPrefs.setCloseChoice(settings, HubDeskPrefs.QUIT)
                onQuit()
            }) { androidx.compose.material3.Text("بسته شود") }
        },
    )
}

/** نشانِ «کارگاهِ وصل» در سینی/نوارِ منو — فقط وقتی پنجره پنهان است و کارگاه روشن. */
@Composable
private fun ApplicationScope.HubTray(hidden: Boolean, serving: Boolean, onOpen: () -> Unit, onQuit: () -> Unit) {
    if (!isTraySupported || !hidden || !serving) return
    Tray(
        icon = rememberVectorPainter(Icons.Default.Wifi),
        tooltip = "${AppInfo.NAME} — کارگاهِ وصل روشن است",
        onAction = onOpen,
        menu = {
            Item("باز کردنِ ${AppInfo.NAME}", onClick = onOpen)
            Item("خاموش کردنِ کارگاه و بستن", onClick = onQuit)
        },
    )
}
