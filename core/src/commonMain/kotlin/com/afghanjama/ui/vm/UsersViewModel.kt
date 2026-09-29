package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.repo.Repo
import com.afghanjama.prefs.Settings
import com.afghanjama.util.NameMatch
import com.afghanjama.util.PinHash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * کاربران و دسترسی‌ها — مدیر برای هر نفر رمز و تیک می‌گذارد.
 *
 * **رمز یکتاست.** ورود با رمز می‌فهمد کیست؛ دو نفر با یک رمز یعنی یکی
 * با دسترسیِ دیگری وارد می‌شود. رمزِ مدیر هم برای کسی گذاشته نمی‌شود.
 */
class UsersViewModel(private val settings: Settings, repo: Repo) : ViewModel() {

    private val _users = MutableStateFlow(UserStore.load(settings))
    val users: StateFlow<List<AppUser>> = _users

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    /**
     * نام‌های آدم‌های کارگاه — برای پیشنهاد، تا نامِ کاربر همان نامِ
     * خیاط یا کارمند باشد و «کارِ من» و رویدادها او را بشناسند.
     */
    val people: StateFlow<List<String>> =
        combine(repo.observeStaff(), repo.observeTailors(), repo.observeInspectors()) { s, t, i ->
            (s.map { it.name } + t.map { it.name } + i.map { it.name })
                .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * ثبتِ کاربرِ تازه ([id] `null`) یا ویرایشِ موجود. رمزِ خالی در ویرایش
     * یعنی همان رمزِ قبلی. `true` یعنی ثبت شد و پنجره بسته شود.
     */
    fun save(id: String?, name: String, pin: String, features: Set<Feature>): Boolean {
        val nm = name.trim()
        val p = pin.trim()
        val all = _users.value
        val others = all.filter { it.id != id }
        val error = when {
            nm.isEmpty() -> "نامِ این نفر را بنویسید."
            features.isEmpty() -> "دست‌کم یک بخش را تیک بزنید."
            id == null && p.length < 4 -> "رمز دست‌کم ۴ رقم باشد."
            p.isNotEmpty() && p.length < 4 -> "رمز دست‌کم ۴ رقم باشد."
            others.any { NameMatch.same(it.name, nm) } -> "کاربری با همین نام هست."
            p.isNotEmpty() && AuthViewModel.verifyPin(settings, p) ->
                "این رمزِ مدیر است؛ برای این نفر رمزِ دیگری بگذارید."
            p.isNotEmpty() && others.any { PinHash.verify(p, it.pinHash) } ->
                "این رمز را کسِ دیگری دارد؛ رمزِ دیگری بگذارید."
            else -> null
        }
        if (error != null) {
            _message.value = error
            return false
        }
        val existing = all.firstOrNull { it.id == id }
        val saved = AppUser(
            id = existing?.id ?: UserStore.newId(),
            name = nm,
            pinHash = if (p.isNotEmpty()) PinHash.hash(p) else existing?.pinHash.orEmpty(),
            features = features
        )
        val next = if (existing == null) all + saved else all.map { if (it.id == saved.id) saved else it }
        UserStore.save(settings, next)
        _users.value = next
        return true
    }

    /** حذف؛ اگر همین حالا وارد است، دفعهٔ بعد که اپ باز شود بیرون است. */
    fun delete(id: String) {
        val next = _users.value.filterNot { it.id == id }
        UserStore.save(settings, next)
        _users.value = next
    }
}
