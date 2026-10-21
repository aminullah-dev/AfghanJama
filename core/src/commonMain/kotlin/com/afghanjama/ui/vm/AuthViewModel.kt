package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import com.afghanjama.prefs.Settings
import com.afghanjama.util.CurrentUser
import com.afghanjama.util.PinHash
import com.afghanjama.util.nowMillis
import com.afghanjama.ui.format.fa
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
    val access: Access = Access.NONE,
    /** کاشی‌های صفحهٔ ورود: صاحبِ دستگاه اول، بعد کسانی که مدیر ساخته. */
    val people: List<LoginPerson> = emptyList(),
    /** کاشی‌ای که زده شده و منتظرِ رمز است؛ `null` یعنی فهرست. */
    val selectedId: String? = null,
    /** کاشیِ انتخاب‌شده تا این لحظه بسته است (رمزِ اشتباهِ پیاپی)؛ صفر یعنی باز. */
    val lockedUntil: Long = 0L,
    /** قرصِ نقشِ کسِ واردشده — همان که روی کاشیِ ورودش بود («مدیر»، «خیاط»…). */
    val roleLabel: String = "",
) {
    val selected: LoginPerson? get() = people.firstOrNull { it.id == selectedId }
}

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
class AuthViewModel(
    private val settings: Settings,
    /** ساعتِ قفلِ رمز ([PinLockout]) — آزمون ساعتِ خودش را می‌دهد. */
    private val clock: () -> Long = { nowMillis() },
) : ViewModel() {

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
                access = access,
                people = loadPeople(),
                roleLabel = if (!logged) "" else person?.roleLabel ?: role.personLabel,
            )
        }
    }

    /**
     * کاشی‌های صفحهٔ ورود: صاحبِ دستگاه، بعد کاربرانِ مدیر به ترتیبِ نام.
     *
     * پیش از تنظیمِ رمزِ اول هیچ کاشی‌ای نیست — آن‌وقت صفحه «نخستین مدیر»
     * را می‌سازد.
     */
    private fun loadPeople(): List<LoginPerson> {
        if (pin().isBlank()) return emptyList()
        val role = role()
        val owner = LoginPerson(
            id = LoginPerson.OWNER_ID,
            name = settings.getString(FILE, KEY_USER).trim().ifBlank { "صاحبِ دستگاه" },
            role = role.personLabel,
            isManager = role == UserRole.MANAGER,
        )
        val users = UserStore.load(settings)
            .sortedBy { it.name }
            .map { LoginPerson(it.id, it.name, it.roleLabel, isManager = false) }
        return listOf(owner) + users
    }

    /** فهرستِ کاشی‌ها را از نو بخوان — مثلاً پس از اینکه مدیر کسی را ساخت. */
    fun refreshPeople() {
        _ui.update { it.copy(people = loadPeople()) }
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

    /**
     * رمزِ اولِ دستگاه — و این نفر **مدیر** است.
     *
     * تا ۱.۱۱ اینجا نقش هم انتخاب می‌شد و پس از آن هیچ‌جا عوض نمی‌شد؛ کسی
     * که سرِ راه‌اندازی «مدیریتِ دوخت» را برمی‌داشت، دستگاهی بی مدیر می‌ساخت
     * و دیگر کسی نمی‌توانست کاربر بسازد. حالا نخستین نفر مدیر است و نقشِ
     * بقیه را او در «کاربران» می‌گذارد. دستگاه‌هایی که پیش‌تر با نقشِ دیگری
     * راه افتاده‌اند همان نقش را نگه می‌دارند — کسی بی‌صدا مدیر نمی‌شود.
     */
    fun setupPin(pin: String) {
        if (pin().isNotBlank()) {
            // رمزِ دستگاه یک بار گذاشته می‌شود؛ عوض کردنش راهِ خودش را دارد.
            _ui.update { it.copy(message = "رمزِ این دستگاه گذاشته شده است؛ وارد شوید.", isError = true) }
            return
        }
        val p = pin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز باید دست‌کم ۴ رقم باشد.", isError = true) }
            return
        }

        val role = UserRole.MANAGER
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
                access = access,
                people = loadPeople(),
                selectedId = null,
                roleLabel = role.personLabel,
            )
        }
    }

    /** کاشیِ یک نفر زده شد — حالا رمزِ همان نفر. `null` یعنی برگشت به فهرست. */
    fun select(id: String?) {
        val until = if (id == null) 0L else PinLockout.lockedUntil(settings, id, clock())
        _ui.update { it.copy(selectedId = id, lockedUntil = until, message = null, isError = false) }
    }

    /**
     * ورودِ [id] با رمزِ **خودش**.
     *
     * رمز فقط با همان نفر سنجیده می‌شود، نه با همه: رمزی که مالِ کسِ دیگری
     * است این کاشی را باز نمی‌کند. اشتباه‌ها شمرده می‌شوند ([PinLockout]).
     */
    fun loginAs(id: String, pin: String) {
        val now = clock()
        val locked = PinLockout.lockedUntil(settings, id, now)
        if (locked > 0L) {
            _ui.update {
                it.copy(selectedId = id, lockedUntil = locked, message = lockMessage(locked - now), isError = true)
            }
            return
        }
        if (id == LoginPerson.OWNER_ID) {
            val saved = pin()
            if (saved.isNotBlank() && PinHash.verify(pin, saved)) {
                PinLockout.clear(settings, id)
                enterOwner(pin, saved)
                return
            }
        } else {
            val person = UserStore.find(settings, id)
            if (person != null && PinHash.verify(pin, person.pinHash)) {
                PinLockout.clear(settings, id)
                enterPerson(person)
                return
            }
            if (person == null) {
                // مدیر همین حالا حذفش کرده؛ کاشی‌ها تازه شوند.
                _ui.update {
                    it.copy(people = loadPeople(), selectedId = null, message = "این نفر دیگر روی این دستگاه نیست.", isError = true)
                }
                return
            }
        }
        val until = PinLockout.fail(settings, id, now)
        _ui.update {
            it.copy(
                selectedId = id,
                lockedUntil = until,
                message = if (until > 0L) lockMessage(until - now) else "رمز اشتباه است.",
                isError = true,
            )
        }
    }

    /**
     * ورود با رمزِ تنها — رمز خودش می‌گوید کیست.
     *
     * صفحهٔ ورود دیگر این راه را ندارد (کاشی، بعد رمز)؛ برای آزمون‌ها و
     * سازگاری مانده، و **همان سقفِ اشتباه** را دارد تا راهِ دومی برای حدس
     * زدنِ بی‌شمار نباشد.
     *
     * **قفلِ کاشیِ مدیر اینجا هم قفل است.** این راه رمزِ مدیر را هم می‌سنجد،
     * پس اگر شمارِ جدای خودش را داشت، کسی که کاشیِ مدیر را بسته بود از اینجا
     * دوباره حدس می‌زد. هر اشتباهِ اینجا به کاشیِ مدیر هم شمرده می‌شود، و
     * ورودِ درستِ **کسِ دیگری** شمارِ کاشیِ مدیر را صفر نمی‌کند.
     *
     * از ۱.۱۲ دو نفر می‌توانند یک رمز داشته باشند (کاشی می‌گوید کیست)؛ رمزی
     * که مالِ بیش از یک نفر است اینجا کسی را وارد نمی‌کند.
     */
    fun login(pin: String) {
        val now = clock()
        val locked = maxOf(
            PinLockout.lockedUntil(settings, ANY_ID, now),
            PinLockout.lockedUntil(settings, LoginPerson.OWNER_ID, now),
        )
        if (locked > 0L) {
            _ui.update { it.copy(lockedUntil = locked, message = lockMessage(locked - now), isError = true) }
            return
        }
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

        val isOwner = PinHash.verify(pin, saved)
        val persons = UserStore.load(settings).filter { PinHash.verify(pin, it.pinHash) }
        val matches = (if (isOwner) 1 else 0) + persons.size
        if (matches == 0) {
            val until = maxOf(
                PinLockout.fail(settings, ANY_ID, now),
                PinLockout.fail(settings, LoginPerson.OWNER_ID, now),
            )
            _ui.update {
                it.copy(
                    lockedUntil = until,
                    message = if (until > 0L) lockMessage(until - now) else "رمز اشتباه است.",
                    isError = true,
                )
            }
            return
        }
        if (matches > 1) {
            _ui.update { it.copy(message = "این رمز مالِ بیش از یک نفر است؛ کاشیِ نامِ خود را بزنید.", isError = true) }
            return
        }
        PinLockout.clear(settings, ANY_ID)
        if (isOwner) {
            PinLockout.clear(settings, LoginPerson.OWNER_ID)
            enterOwner(pin, saved)
        } else {
            val person = persons.single()
            PinLockout.clear(settings, person.id)
            enterPerson(person)
        }
    }

    private fun enterPerson(person: AppUser) {
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
                access = person.access,
                selectedId = null,
                lockedUntil = 0L,
                roleLabel = person.roleLabel,
            )
        }
    }

    private fun enterOwner(pin: String, saved: String) {
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
                access = access,
                selectedId = null,
                lockedUntil = 0L,
                roleLabel = role.personLabel,
            )
        }
    }

    private fun lockMessage(leftMs: Long): String {
        val sec = ((leftMs + 999) / 1000).coerceAtLeast(1)
        val wait = if (sec >= 60) "${((sec + 59) / 60).fa()} دقیقه" else "${sec.fa()} ثانیه"
        return "چند بار رمزِ اشتباه زده شد. $wait صبر کنید و دوباره بزنید."
    }

    /**
     * تغییر رمز با تأیید رمز فعلی.
     *
     * کاربرِ شخصی رمزِ **خودش** را عوض می‌کند، نه رمزِ دستگاه را.
     *
     * **«رمزِ فعلی» همان سقفِ اشتباهِ کاشی را دارد** ([PinLockout]): کسی که
     * پشتِ جلسهٔ بازِ دیگری نشسته، نباید از این پنجره رمزِ او را بی‌شمار حدس
     * بزند و بعداً با آن وارد شود.
     */
    fun changePin(oldPin: String, newPin: String) {
        val personId = _ui.value.userId
        val tile = personId ?: LoginPerson.OWNER_ID
        val now = clock()
        val locked = PinLockout.lockedUntil(settings, tile, now)
        if (locked > 0L) {
            _ui.update { it.copy(message = lockMessage(locked - now), isError = true) }
            return
        }
        if (personId != null) {
            changePersonPin(personId, oldPin, newPin, now)
            return
        }
        val saved = pin()
        if (saved.isBlank() || !PinHash.verify(oldPin, saved)) {
            wrongCurrentPin(tile, now)
            return
        }
        PinLockout.clear(settings, tile)
        val p = newPin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمزِ تازه باید دست‌کم ۴ رقم باشد.", isError = true) }
            return
        }
        settings.putString(FILE, KEY_PIN, PinHash.hash(p))
        _ui.update { it.copy(message = "رمز با موفقیت تغییر کرد.", isError = false) }
    }

    private fun wrongCurrentPin(tile: String, now: Long) {
        val until = PinLockout.fail(settings, tile, now)
        _ui.update {
            it.copy(message = if (until > 0L) lockMessage(until - now) else "رمز فعلی اشتباه است.", isError = true)
        }
    }

    /**
     * **رمزِ تازه با رمزِ دیگران سنجیده نمی‌شود — عمداً.** تا پیش از این، پیامِ
     * «این رمز را کسِ دیگری دارد» به هر کارگرِ واردشده می‌گفت کدام رمز مالِ
     * کسِ دیگری است؛ بی هیچ سقفی، از همین پنجره می‌شد همهٔ ده‌هزار رمز را
     * آزمود، رمزِ مدیر را یافت و روی کاشیِ مدیر زد — یعنی سقفِ اشتباهِ صفحهٔ
     * ورود دورزده می‌شد. حالا که کاشی می‌گوید کیست، یکی بودنِ دو رمز زیانی
     * ندارد: هر کاشی فقط با هشِ همان نفر سنجیده می‌شود ([loginAs]).
     */
    private fun changePersonPin(id: String, oldPin: String, newPin: String, now: Long) {
        val users = UserStore.load(settings)
        val me = users.firstOrNull { it.id == id }
        if (me == null || !PinHash.verify(oldPin, me.pinHash)) {
            wrongCurrentPin(id, now)
            return
        }
        PinLockout.clear(settings, id)
        val p = newPin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمزِ تازه باید دست‌کم ۴ رقم باشد.", isError = true) }
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
        // «خروج» به فهرستِ کاشی‌ها برمی‌گردد، تا نفرِ بعدی روی همین دستگاه
        // نامِ خودش را بزند. فهرست تازه خوانده می‌شود: شاید مدیر در این
        // فاصله کسی را ساخته یا نقشش را عوض کرده.
        _ui.update {
            it.copy(
                isLoggedIn = false, message = null, isError = false, userId = null, access = Access.NONE,
                people = loadPeople(), selectedId = null, lockedUntil = 0L, roleLabel = "",
            )
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

        /** شمارِ اشتباهِ راهِ «فقط رمز» ([login]) — جدا از کاشیِ هر نفر. */
        private const val ANY_ID = "any"

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
