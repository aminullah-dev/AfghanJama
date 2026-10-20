package com.afghanjama.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
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
import com.afghanjama.ui.theme.LightColors
import com.afghanjama.ui.vm.AuthUi
import com.afghanjama.ui.vm.AuthViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle

/**
 * جایی که ViewModelها زندگی می‌کنند.
 *
 * روی اندروید این را خودِ اندروید می‌دهد (Activity یک
 * `ViewModelStoreOwner` است). روی دسکتاپ چنین چیزی از آسمان نمی‌آید و
 * باید صریح داده شود، وگرنه `viewModel { }` سرِ اجرا می‌ترکد.
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

/** دستِ‌کم ۱۰۰ نقطه از پنجره روی یکی از صفحه‌ها می‌افتد؟ */
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
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        // نسخه و کامیت روی نوارِ پنجره — همان چیزی که پشتیبانی اول می‌پرسد.
        title = "${AppInfo.NAME} — ${AppVersion.short()} — $platformName",
        // هر کلیدی یعنی کسی پشتِ کامپیوتر است (قفلِ خودکار). رویداد را
        // نمی‌خورد؛ فقط می‌بیند.
        onPreviewKeyEvent = {
            DeskIdle.touch()
            false
        }
    ) {
        LaunchedEffect(Unit) {
            val screen = usableScreen()
            window.minimumSize = Dimension(MIN_W.coerceAtMost(screen.width), MIN_H.coerceAtMost(screen.height))
        }
        RememberBounds(windowState, settings)

        MaterialTheme(
            // **همان پالتِ گوشی**، از `:core`.
            colorScheme = LightColors,
            // صفحه‌های مشترک قلم را هم از تم می‌گیرند، نه از خودشان.
            typography = vazirTypography()
        ) {
            // کلِ برنامه راست‌به‌چپ، مستقلِ از زبانِ سیستم.
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalViewModelStoreOwner provides DesktopViewModelStoreOwner,
                // صفحه‌های مشترک تنظیمات را از اینجا می‌گیرند، همان‌طور
                // که روی اندروید از `MainActivity`.
                LocalSettings provides settings,
                LocalSystemActions provides DesktopSystemActions(),
                LocalDocs provides DesktopDocsBridge(settings),
                LocalFileExport provides DesktopFileExport(),
                LocalPhotos provides DesktopPhotos(),
                LocalWidgets provides DesktopWidgets
            ) {
                // وضعیتِ لایسنس — پیش از ورود، تا درِ نوشتن از همان ثانیهٔ
                // اول درست بداند.
                LicenceHeartbeat(remember { Licensing(settings, DesktopLedger.repo().getOrNull()) })

                // درِ ورود: نقش از `AuthViewModel` می‌آید، نه از فرض.
                val authVm: AuthViewModel = viewModel { AuthViewModel(settings) }
                val auth by authVm.ui.collectAsState()
                val nav = remember { DeskNav() }

                if (isMac) DeskMenuBar(nav, auth, authVm, windowState)

                // کسی که دوباره وارد می‌شود سرِ جای قبلی‌اش برمی‌گردد (مثلاً
                // پس از قفلِ خودکار)؛ کسِ دیگری از «خانه» شروع می‌کند.
                var lastUser by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(auth.isLoggedIn, auth.userId, auth.role) {
                    if (auth.isLoggedIn) {
                        val who = "${auth.userId}|${auth.role}"
                        if (who != lastUser) nav.go(Section.Home)
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
                    if (!auth.isLoggedIn) {
                        // `onLoggedIn` لازم نیست کاری کند: حالت خودش عوض
                        // می‌شود و بازترکیب صفحه را رد می‌کند.
                        LoginScreen(authVm, onLoggedIn = {})
                    } else {
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
            listOf(Section.Home, Section.ActionCenter, Section.WorkshopLink, Section.OrderSearch).forEach { s ->
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
