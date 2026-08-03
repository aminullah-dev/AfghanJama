package com.afghanjama.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * [ScreenBehavior] روی اندروید — همان دو کاری که تا دیروز مستقیم در
 * `BoardScreen` نوشته شده بود.
 */
object AndroidScreenBehavior : ScreenBehavior {

    @Composable
    override fun KeepAwake() {
        val view = LocalView.current
        DisposableEffect(Unit) {
            view.keepScreenOn = true
            // برداشتنش هنگامِ خروج لازم است، وگرنه پرچم روی همان پنجره
            // می‌ماند و باتریِ گوشیِ کارگر بی‌دلیل تمام می‌شود.
            onDispose { view.keepScreenOn = false }
        }
    }

    @Composable
    override fun HandleBack(onBack: () -> Unit) = BackHandler { onBack() }
}
