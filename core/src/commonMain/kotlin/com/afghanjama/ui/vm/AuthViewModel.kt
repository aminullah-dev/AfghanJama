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
    val isError: Boolean = false,
    /**
     * شناسهٔ کاربرِ شخصی‌ای که وارد شده؛ `null` یعنی صاحبِ دستگاه (مدیر، یا
     * نقشی که دستگاه با آن راه افتاده).
     */
    val userId: String? = null,
    /** آنچه این نفر اجازه دارد — نوار، خانه و هر صفحه از این می‌خوانند. */
    val access: Access = Access.NONE
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

        var logged = loggedIn && pin.isNotBlank()

        // کاربرِ شخصی که پیش از بسته شدنِ اپ وارد بود. اگر مدیر در این
        // فاصله حذفش کرده، دیگر وارد نیست.
        val userId = settings.getString(FILE, KEY_USER_ID)
        val person = if (logged && userId.isNotBlank()) UserStore.find(settings, userId) else null
        if (logged && userId.isNotBlank() && person == null) {
            settings.putBoolean(FILE, KEY_LOGGED_IN, false)
            settings.remove(FILE, KEY_USER_ID)
            logged = false
        }

        val access = when {
            !logged -> Access.NONE
            person != null -> person.access
            else -> Access.forRole(role)
        }
        CurrentAccess.value = access
        if (logged) {
            if (person != null) CurrentUser.set(person.name, PERSON_ROLE)
            else CurrentUser.set(userName, role.name)
        }

        _ui.update {
            it.copy(
                isSetupDone = pin.isNotBlank(),
                isLoggedIn = logged,
                role = person?.legacyRole() ?: role,
                userName = person?.name ?: userName,
                message = null,
                isError = false,
                userId = person?.id,
                access = access
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
        settings.remove(FILE, KEY_USER_ID)
        val access = Access.forRole(role)
        CurrentAccess.value = access

        _ui.update {
            it.copy(
                isSetupDone = true,
                isLoggedIn = true,
                role = role,
                message = "رمز تنظیم شد.",
                isError = false,
                userId = null,
                access = access
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
            // رمزِ صاحبِ دستگاه نبود؛ شاید یکی از کاربرانی که مدیر ساخته.
            // هر کس رمزِ خودش را دارد، پس رمز خودش می‌گوید کیست.
            val person = UserStore.load(settings).firstOrNull { PinHash.verify(pin, it.pinHash) }
            if (person == null) {
                _ui.update { it.copy(message = "رمز اشتباه است.", isError = true) }
                return
            }
            settings.putBoolean(FILE, KEY_LOGGED_IN, true)
            settings.putString(FILE, KEY_USER_ID, person.id)
            CurrentUser.set(person.name, PERSON_ROLE)
            CurrentAccess.value = person.access
            _ui.update {
                it.copy(
                    isLoggedIn = true,
                    role = person.legacyRole(),
                    userName = person.name,
                    message = null,
                    isError = false,
                    isSetupDone = true,
                    userId = person.id,
                    access = person.access
                )
            }
            return
        }
        // گوشی‌هایی که رمزشان خام ذخیره شده بود همچنان باز می‌شوند، و
        // همین‌جا بی‌صدا ارتقا می‌گیرند. تنها لحظه‌ای که ممکن است: رمزِ
        // درست دمِ دست است.
        if (PinHash.needsUpgrade(saved)) settings.putString(FILE, KEY_PIN, PinHash.hash(pin))

        val role = role()
        settings.putBoolean(FILE, KEY_LOGGED_IN, true)
        settings.remove(FILE, KEY_USER_ID)
        val userName = settings.getString(FILE, KEY_USER)
        CurrentUser.set(userName, role.name)
        val access = Access.forRole(role)
        CurrentAccess.value = access
        _ui.update {
            it.copy(
                isLoggedIn = true,
                role = role,
                userName = userName,
                message = null,
                isError = false,
                isSetupDone = true,
                userId = null,
                access = access
            )
        }
    }

    /**
     * تغییر رمز با تأیید رمز فعلی.
     *
     * کاربرِ شخصی رمزِ **خودش** را عوض می‌کند، نه رمزِ دستگاه را؛ و رمزِ
     * تازه نباید مالِ کسِ دیگری باشد، چون رمز است که می‌گوید کیست.
     */
    fun changePin(oldPin: String, newPin: String) {
        val personId = _ui.value.userId
        if (personId != null) {
            changePersonPin(personId, oldPin, newPin)
            return
        }
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
        _ui.update { it.copy(message = "رمز با موفقیت تغییر کرد.", isError = false) }
    }

    private fun changePersonPin(id: String, oldPin: String, newPin: String) {
        val users = UserStore.load(settings)
        val me = users.firstOrNull { it.id == id }
        if (me == null || !PinHash.verify(oldPin, me.pinHash)) {
            _ui.update { it.copy(message = "رمز فعلی اشتباه است.", isError = true) }
            return
        }
        val p = newPin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز جدید باید حداقل ۴ رقم باشد.", isError = true) }
            return
        }
        val taken = verifyPin(settings, p) ||
            users.any { it.id != id && PinHash.verify(p, it.pinHash) }
        if (taken) {
            _ui.update { it.copy(message = "این رمز را کسِ دیگری دارد؛ رمزِ دیگری بگذارید.", isError = true) }
            return
        }
        UserStore.save(settings, users.map { if (it.id == id) it.copy(pinHash = PinHash.hash(p)) else it })
        _ui.update { it.copy(message = "رمز با موفقیت تغییر کرد.", isError = false) }
    }

    fun logout() {
        settings.putBoolean(FILE, KEY_LOGGED_IN, false)
        settings.remove(FILE, KEY_USER_ID)
        CurrentUser.clear()
        CurrentAccess.value = Access.NONE
        _ui.update {
            it.copy(isLoggedIn = false, message = null, isError = false, userId = null, access = Access.NONE)
        }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null, isError = false) }
    }

    companion object {
        // همان نام‌هایی که `SharedPreferences`ِ اندروید امروز دارد.
        private const val FILE = "auth_prefs"
        private const val KEY_PIN = "pin"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_ROLE = "role"
        private const val KEY_USER = "user_name"
        private const val KEY_USER_ID = "user_id"

        /** برچسبِ نقش روی رویدادهای کاربرِ شخصی. */
        private const val PERSON_ROLE = "USER"

        /**
         * رمزِ ورودِ همین دستگاه درست است؟ — برای کاری که پیش از انجام،
         * دوباره رمز می‌خواهد (مثلِ ساختنِ دوبارهٔ کارگاهِ نمونه).
         *
         * همان وارسیِ [login]، بی ورود و بی ارتقای قالب: اینجا فقط «بله یا
         * نه» لازم است.
         */
        fun verifyPin(settings: Settings, pin: String): Boolean {
            val saved = settings.getString(FILE, KEY_PIN)
            return saved.isNotBlank() && PinHash.verify(pin, saved)
        }
    }
}
