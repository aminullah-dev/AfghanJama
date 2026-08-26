package com.afghanjama.desktop

import com.afghanjama.desktop.data.DesktopLedger
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.ui.vm.SelfTestViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/*
 * خودآزمایی روی ویندوز — واقعاً اجرا می‌شود، نه فقط رسم.
 *
 * **چرا این جدا از `screenSmoke` است.** آن آزمون ثابت می‌کند صفحهٔ
 * خودآزمایی *رسم* می‌شود؛ یعنی کلاسی گم نیست و چیدمان می‌نشیند. ولی
 * صفحه‌ای که رسم شود و دکمه‌اش کارِ اشتباه بکند هم سبز می‌ماند.
 *
 * `DELIVERY.md` می‌گوید پیش از هر تحویل باید «خودآزمایی و سلامتِ داده»
 * اجرا شود و **همه سبز** باشد. تا امروز آن بند فقط برای گوشی معنی
 * داشت، چون روی ویندوز راهی برای اجرایش نبود.
 *
 * اینجا همان ViewModelِ گوشی با همان `Repo` اجرا می‌شود و نتیجه شمرده
 * می‌شود. اگر یک بند قرمز باشد، ساخت قرمز می‌شود.
 *
 * **چرا شمارشِ صفر هم شکست است:** اگر روزی فهرستِ بررسی‌ها خالی برگردد
 * — مثلاً چون استثنایی وسطِ کار بلعیده شد — «هیچ شکستی نبود» درست ولی
 * بی‌معنا می‌شود. آزمونی که با صفر بند سبز بماند همان بررسیِ همیشه‌سبزی
 * است که `tools/checks/README.md` از آن پرهیز می‌دهد.
 */
fun main() {
    println("self-test run - KhayatYar Windows")
    println("=".repeat(52))

    val repo = DesktopLedger.repo().getOrNull()
    if (repo == null) {
        println("FAIL  ledger did not open")
        kotlin.system.exitProcess(1)
    }

    val vm = SelfTestViewModel(repo)
    vm.run()

    val ui = runBlocking {
        // یک سقفِ زمانی لازم است وگرنه شکستِ خاموش به یک ساختِ معلق
        // تبدیل می‌شود و کسی نمی‌فهمد چرا.
        withTimeoutOrNull(120_000) {
            while (vm.ui.value.running) delay(100)
            vm.ui.value
        }
    }

    if (ui == null) {
        println("FAIL  self-test did not finish within 120s")
        kotlin.system.exitProcess(1)
    }

    val failed = ui.results.filter { it.status == CheckStatus.FAIL }
    val byGroup = ui.results.groupBy { it.group }

    byGroup.forEach { (group, rows) ->
        val bad = rows.count { it.status == CheckStatus.FAIL }
        val mark = if (bad == 0) "OK  " else "FAIL"
        println("$mark  ${group.take(38).padEnd(40)} ${rows.size - bad}/${rows.size}")
    }

    println("=".repeat(52))

    if (ui.results.isEmpty()) {
        println("FAIL  self-test produced no checks at all")
        kotlin.system.exitProcess(1)
    }

    if (failed.isEmpty()) {
        println("all ${ui.results.size} self-test checks passed on Windows")
        kotlin.system.exitProcess(0)
    }

    println("${failed.size} of ${ui.results.size} checks FAILED")
    failed.take(20).forEach { println("  ${it.group} - ${it.name}: ${it.detail}") }
    kotlin.system.exitProcess(1)
}
