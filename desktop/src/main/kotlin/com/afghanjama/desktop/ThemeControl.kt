package com.afghanjama.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import com.afghanjama.prefs.ThemeMode

/**
 * ظاهرِ فعلی و راهِ عوض‌کردنش — تا کلیدِ «ظاهر» در تنظیمات بتواند تمِ کلِ
 * پنجره را (که در [MainKt] ساخته می‌شود) همان لحظه عوض کند و ماندگار کند.
 */
class ThemeControl(val mode: ThemeMode, val set: (ThemeMode) -> Unit)

val LocalThemeControl = staticCompositionLocalOf { ThemeControl(ThemeMode.LIGHT) {} }
