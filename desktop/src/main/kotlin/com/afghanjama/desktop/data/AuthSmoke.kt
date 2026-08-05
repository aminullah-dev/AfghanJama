package com.afghanjama.desktop.data

import com.afghanjama.ui.vm.AuthViewModel
import com.afghanjama.ui.vm.UserRole
import java.nio.file.Files

/*
 * دودآزماییِ ورود روی ویندوز.
 *
 * **چرا لازم است.** `AuthViewModel` از `SharedPreferences` به `Settings`
 * منتقل شد تا ویندوز هم درِ ورود داشته باشد. منطق دست نخورد ولی **لایهٔ
 * ذخیره‌سازی عوض شد** — و این همان چیزی است که یک گاردِ امنیتی رویش
 * سوار است. اگر `FileSettings` مثلاً `Boolean` را درست برنگرداند،
 * `logged_in` همیشه `false` می‌ماند (کاربر هر بار پشتِ در) یا بدتر،
 * همیشه `true`.
 *
 * روی پوشهٔ موقت کار می‌کند، نه تنظیماتِ واقعیِ کارگاه.
 *
 * سمتِ اندروید نگرانی ندارد و **آزمون هم نمی‌خواهد**: `AndroidSettings`
 * همان `getSharedPreferences(file, MODE_PRIVATE)` را صدا می‌زند و نامِ
 * فایل و کلیدها دست نخوردند، پس گوشی‌هایی که امروز رمز دارند همان رمز
 * را می‌خوانند.
 */

private class AuthFailure(msg: String) : RuntimeException(msg)

private fun authNeed(cond: Boolean, msg: String) {
    if (!cond) throw AuthFailure(msg)
}

private fun authCheck(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

fun main() {
    println("auth smoke - KhayatYar Windows")
    println("=".repeat(52))
    val dir = Files.createTempDirectory("khayatyar-auth").toFile()
    val settings = FileSettings(dir)
    var ok = true

    ok = authCheck("a fresh machine is not logged in and has no PIN") {
        val vm = AuthViewModel(settings)
        authNeed(!vm.ui.value.isLoggedIn, "must not start logged in")
        authNeed(!vm.ui.value.isSetupDone, "must not start with a PIN set")
    } && ok

    ok = authCheck("a PIN shorter than 4 digits is refused") {
        val vm = AuthViewModel(settings)
        vm.setupPin("12")
        authNeed(vm.ui.value.isError, "short PIN should be an error")
        authNeed(!vm.ui.value.isSetupDone, "short PIN must not be stored")
    } && ok

    ok = authCheck("setting a PIN logs in and survives a restart") {
        AuthViewModel(settings).setupPin("1379")
        // نمونهٔ تازه = خواندن دوباره از دیسک، یعنی همان چیزی که پس از
        // بستن و باز کردنِ برنامه اتفاق می‌افتد.
        val again = AuthViewModel(settings)
        authNeed(again.ui.value.isSetupDone, "PIN should have been stored")
        authNeed(again.ui.value.isLoggedIn, "should still be logged in after restart")
    } && ok

    ok = authCheck("a wrong PIN is refused and does not log in") {
        AuthViewModel(settings).logout()
        val vm = AuthViewModel(settings)
        authNeed(!vm.ui.value.isLoggedIn, "logout should have taken effect")
        vm.login("0000")
        authNeed(vm.ui.value.isError, "wrong PIN should be an error")
        authNeed(!vm.ui.value.isLoggedIn, "wrong PIN must not log in")
    } && ok

    ok = authCheck("the correct PIN logs in") {
        val vm = AuthViewModel(settings)
        vm.login("1379")
        authNeed(!vm.ui.value.isError, "correct PIN should not error: ${vm.ui.value.message}")
        authNeed(vm.ui.value.isLoggedIn, "correct PIN should log in")
    } && ok

    ok = authCheck("the role round-trips and drives permissions") {
        AuthViewModel(settings).setRole(UserRole.SEWING)
        val vm = AuthViewModel(settings)
        authNeed(
            vm.ui.value.role == UserRole.SEWING,
            "role should persist, got ${vm.ui.value.role}"
        )
        // همان قاعده‌ای که پنجره برای «اصلاحِ موجودی» به کار می‌برد.
        authNeed(
            !com.afghanjama.ui.vm.Permissions.canAdjustMaterial(vm.ui.value.role),
            "a SEWING role must not be allowed to adjust stock"
        )
        AuthViewModel(settings).setRole(UserRole.MANAGER)
        authNeed(
            com.afghanjama.ui.vm.Permissions.canAdjustMaterial(
                AuthViewModel(settings).ui.value.role
            ),
            "a MANAGER must be allowed to adjust stock"
        )
    } && ok

    ok = authCheck("changing the PIN requires the current one") {
        val vm = AuthViewModel(settings)
        vm.changePin("9999", "2468")
        authNeed(vm.ui.value.isError, "a wrong current PIN must be refused")
        vm.changePin("1379", "2468")
        authNeed(!vm.ui.value.isError, "correct current PIN should be accepted")

        val fresh = AuthViewModel(settings)
        fresh.logout()
        val after = AuthViewModel(settings)
        after.login("1379")
        authNeed(!after.ui.value.isLoggedIn, "the old PIN must stop working")
        after.login("2468")
        authNeed(after.ui.value.isLoggedIn, "the new PIN must work")
    } && ok

    dir.deleteRecursively()
    println("=".repeat(52))
    println(if (ok) "auth path verified" else "auth smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
