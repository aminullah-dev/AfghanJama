package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import com.afghanjama.prefs.Settings
import com.afghanjama.util.CurrentUser
import com.afghanjama.util.PinHash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AuthUi(
    val isLoggedIn: Boolean = false,
    val isSetupDone: Boolean = false,
    val role: UserRole = UserRole.MANAGER,
    val userName: String = "",
    val message: String? = null,
    val isError: Boolean = false
)

/**
 * ورود با رمز و نقشِ کاربر.
 *
 * **چرا از `:app` به اینجا آمد.** پیش‌تر یک `AndroidViewModel` بود که
 * مستقیم `SharedPreferences` می‌گرفت — و همان یک وابستگی، هم خودش و هم
 * `LoginScreen` را در `:app` نگه داشته بود (موجِ ۴ تلاش کرد و مجبور شد
 * صفحه را برگرداند). نتیجه‌اش این بود که **نسخهٔ ویندوز هیچ ورودی
 * نداشت**: پنجره مستقیم باز می‌شد و نقشِ مدیر یک فرض بود نه یک کنترل.
 *
 * حالا روی `Settings` می‌نشیند — همان مرزی که موجِ ۳ برای همین ساخت.
 *
 * **نامِ فایل و کلیدها عمداً دست نخوردند** (`auth_prefs`، `pin`،
 * `logged_in`، `role`، `user_name`). `AndroidSettings` دقیقاً به همان
 * `SharedPreferences` نگاشت می‌شود، پس **گوشی‌هایی که امروز رمز دارند
 * رمزشان را نگه می‌دارند**. تمیزکردنِ این نام‌ها هیچ سودی نداشت و فقط
 * نصب‌های موجود را از رمزشان جدا می‌کرد.
 *
 * `getString` در `Settings` به‌جای `null` رشتهٔ خالی می‌دهد، پس هرجا
 * `isNullOrBlank` بود `isBlank` شد — رفتار یکی است.
 */
class AuthViewModel(private val settings: Settings) : ViewModel() {

    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui

    init {
        val pin = pin()
        val loggedIn = settings.getBoolean(FILE, KEY_LOGGED_IN, false)
        val role = role()
        val userName = settings.getString(FILE, KEY_USER)

        val logged = loggedIn && pin.isNotBlank()
        if (logged) CurrentUser.set(userName, role.name)

        _ui.update {
            it.copy(
                isSetupDone = pin.isNotBlank(),
                isLoggedIn = logged,
                role = role,
                userName = userName,
                message = null,
                isError = false
            )
        }
    }

    private fun pin(): String = settings.getString(FILE, KEY_PIN)

    private fun role(): UserRole {
        val raw = settings.getString(FILE, KEY_ROLE, UserRole.MANAGER.name)
        return runCatching { UserRole.valueOf(raw) }.getOrElse { UserRole.MANAGER }
    }

    /** نامِ کاربرِ این دستگاه (برای لاگ حسابرسی). */
    fun setUserName(v: String) {
        settings.putString(FILE, KEY_USER, v.trim())
        _ui.update { it.copy(userName = v) }
    }

    fun setRole(role: UserRole) {
        settings.putString(FILE, KEY_ROLE, role.name)
        _ui.update { it.copy(role = role) }
    }

    fun setupPin(pin: String) {
        val p = pin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز باید حداقل ۴ رقم باشد.", isError = true) }
            return
        }

        val role = role()
        // هش، نه خودِ رمز. تا دیروز اینجا `p` نوشته می‌شد و رمزِ کارگاه
        // در فایلِ تنظیمات خواندنی بود.
        settings.putString(FILE, KEY_PIN, PinHash.hash(p))
        settings.putBoolean(FILE, KEY_LOGGED_IN, true)
        settings.putString(FILE, KEY_ROLE, role.name)

        CurrentUser.set(settings.getString(FILE, KEY_USER), role.name)

        _ui.update {
            it.copy(
                isSetupDone = true,
                isLoggedIn = true,
                role = role,
                message = "رمز تنظیم شد.",
                isError = false
            )
        }
    }

    fun login(pin: String) {
        val saved = pin()
        if (saved.isBlank()) {
            _ui.update {
                it.copy(
                    message = "ابتدا رمز را تنظیم کنید.",
                    isError = true,
                    isSetupDone = false
                )
            }
            return
        }

        if (!PinHash.verify(pin, saved)) {
            _ui.update { it.copy(message = "رمز اشتباه است.", isError = true) }
            return
        }
        // گوشی‌هایی که رمزشان خام ذخیره شده بود همچنان باز می‌شوند، و
        // همین‌جا بی‌صدا ارتقا می‌گیرند. تنها لحظه‌ای که ممکن است: رمزِ
        // درست دمِ دست است.
        if (PinHash.needsUpgrade(saved)) settings.putString(FILE, KEY_PIN, PinHash.hash(pin))

        val role = role()
        settings.putBoolean(FILE, KEY_LOGGED_IN, true)
        CurrentUser.set(settings.getString(FILE, KEY_USER), role.name)
        _ui.update {
            it.copy(
                isLoggedIn = true,
                role = role,
                message = null,
                isError = false,
                isSetupDone = true
            )
        }
    }

    /** تغییر رمز با تأیید رمز فعلی. */
    fun changePin(oldPin: String, newPin: String) {
        val saved = pin()
        if (saved.isBlank() || !PinHash.verify(oldPin, saved)) {
            _ui.update { it.copy(message = "رمز فعلی اشتباه است.", isError = true) }
            return
        }
        val p = newPin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز جدید باید حداقل ۴ رقم باشد.", isError = true) }
            return
        }
        settings.putString(FILE, KEY_PIN, PinHash.hash(p))
        _ui.update { it.copy(message = "✅ رمز با موفقیت تغییر کرد.", isError = false) }
    }

    fun logout() {
        settings.putBoolean(FILE, KEY_LOGGED_IN, false)
        CurrentUser.clear()
        _ui.update { it.copy(isLoggedIn = false, message = null, isError = false) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null, isError = false) }
    }

    private companion object {
        // همان نام‌هایی که `SharedPreferences`ِ اندروید امروز دارد.
        const val FILE = "auth_prefs"
        const val KEY_PIN = "pin"
        const val KEY_LOGGED_IN = "logged_in"
        const val KEY_ROLE = "role"
        const val KEY_USER = "user_name"
    }
}
