// app/src/main/java/com/afghanjama/ui/vm/AuthViewModel.kt
package com.afghanjama.ui.vm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AuthUi(
    val isLoggedIn: Boolean = false,
    val isSetupDone: Boolean = false,
    val role: UserRole = UserRole.MANAGER,
    val message: String? = null,
    val isError: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    private val KEY_PIN = "pin"
    private val KEY_LOGGED_IN = "logged_in"
    private val KEY_ROLE = "role"

    private val _ui = MutableStateFlow(AuthUi())
    val ui: StateFlow<AuthUi> = _ui

    init {
        val pin = prefs.getString(KEY_PIN, null)
        val loggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)

        val roleStr = prefs.getString(KEY_ROLE, UserRole.MANAGER.name) ?: UserRole.MANAGER.name
        val role = runCatching { UserRole.valueOf(roleStr) }.getOrElse { UserRole.MANAGER }

        _ui.update {
            it.copy(
                isSetupDone = !pin.isNullOrBlank(),
                isLoggedIn = loggedIn && !pin.isNullOrBlank(),
                role = role,
                message = null,
                isError = false
            )
        }
    }

    fun setRole(role: UserRole) {
        prefs.edit().putString(KEY_ROLE, role.name).apply()
        _ui.update { it.copy(role = role) }
    }

    fun setupPin(pin: String) {
        val p = pin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز باید حداقل ۴ رقم باشد.", isError = true) }
            return
        }

        val roleStr = prefs.getString(KEY_ROLE, UserRole.MANAGER.name) ?: UserRole.MANAGER.name

        prefs.edit()
            .putString(KEY_PIN, p)
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_ROLE, roleStr)
            .apply()

        val role = runCatching { UserRole.valueOf(roleStr) }.getOrElse { UserRole.MANAGER }

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
        val saved = prefs.getString(KEY_PIN, null)
        if (saved.isNullOrBlank()) {
            _ui.update { it.copy(message = "ابتدا رمز را تنظیم کنید.", isError = true, isSetupDone = false) }
            return
        }

        if (pin.trim() != saved) {
            _ui.update { it.copy(message = "رمز اشتباه است.", isError = true) }
            return
        }

        val roleStr = prefs.getString(KEY_ROLE, UserRole.MANAGER.name) ?: UserRole.MANAGER.name
        val role = runCatching { UserRole.valueOf(roleStr) }.getOrElse { UserRole.MANAGER }

        prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
        _ui.update { it.copy(isLoggedIn = true, role = role, message = null, isError = false, isSetupDone = true) }
    }

    /** تغییر رمز با تأیید رمز فعلی. */
    fun changePin(oldPin: String, newPin: String) {
        val saved = prefs.getString(KEY_PIN, null)
        if (saved.isNullOrBlank() || oldPin.trim() != saved) {
            _ui.update { it.copy(message = "رمز فعلی اشتباه است.", isError = true) }
            return
        }
        val p = newPin.trim()
        if (p.length < 4) {
            _ui.update { it.copy(message = "رمز جدید باید حداقل ۴ رقم باشد.", isError = true) }
            return
        }
        prefs.edit().putString(KEY_PIN, p).apply()
        _ui.update { it.copy(message = "✅ رمز با موفقیت تغییر کرد.", isError = false) }
    }

    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _ui.update { it.copy(isLoggedIn = false, message = null, isError = false) }
    }

    fun clearMessage() {
        _ui.update { it.copy(message = null, isError = false) }
    }
}
