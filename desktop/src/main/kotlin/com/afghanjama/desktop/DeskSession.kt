package com.afghanjama.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.afghanjama.desktop.platform.DeskReminderLoop

/**
 * درِ ورود: [signedIn] نباشد [login]، باشد [content] — در یک جلسهٔ تازه.
 *
 * **جلسه یعنی از «ورود» تا «خروج از حساب» یا قفلِ خودکار** (که همان خروج
 * است). هر چه در این فاصله در دست بود با آن می‌رود:
 *
 * - **ViewModelها.** تا ۱.۱۰ همهٔ صفحه‌ها یک صاحب برای کلِ اجرای برنامه
 *   داشتند؛ فاکتورِ نیمه‌کاره‌ای که فروشنده در «فروشِ نو» یا از «انبارِ
 *   محصول» چیده بود، پس از خروجِ او یا قفلِ خودکار به نفرِ بعدی می‌رسید.
 *   حالا هر جلسه صاحبِ خودش را دارد و با پایانش پاک می‌شود. «ورود» خودش
 *   (`AuthViewModel`) بیرون از جلسه و در صاحبِ کلِ برنامه می‌ماند.
 * - **یادآورها** ([DeskReminderLoop.signedIn]) — نامِ کارکنان پشتِ صفحهٔ ورود
 *   گفته نمی‌شود.
 *
 * جای بندِ نوار ([DeskNav]) بیرون از جلسه است و همان قاعدهٔ پیشین را دارد:
 * کسی که دوباره وارد می‌شود سرِ جای قبلی‌اش برمی‌گردد، ولی دستِ خالی.
 */
@Composable
internal fun SignedInGate(
    signedIn: Boolean,
    login: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!signedIn) {
        login()
    } else {
        DeskSession(content)
    }
}

/** صاحبِ ViewModelهای یک جلسه. */
private class SessionViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun DeskSession(content: @Composable () -> Unit) {
    val owner = remember { SessionViewModels() }
    DisposableEffect(owner) {
        DeskReminderLoop.signedIn.value = true
        // یادآوری که پشتِ صفحهٔ ورود وقتش شده بود، همین حالا گفته شود.
        DeskReminderLoop.poke()
        onDispose {
            DeskReminderLoop.signedIn.value = false
            owner.viewModelStore.clear()
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
