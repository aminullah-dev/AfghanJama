package com.afghanjama.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import com.afghanjama.ui.nav.Routes

/*
 * پشتهٔ برگشت برای iOS.
 *
 * **چرا دستی و نه کتابخانهٔ Navigation.** نسخهٔ چندسکوییِ
 * `androidx.navigation` تازه است و ماژول‌های iOSش زیرِ گروهِ
 * `org.jetbrains.androidx` منتشر می‌شوند، نه همان مختصاتی که `:app`
 * دارد. آوردنش یعنی دو کتابخانهٔ ناوبریِ متفاوت در یک مخزن.
 *
 * و آنچه اینجا لازم است، کوچک است: مقصدها رشته‌اند (`Routes`، مشترک با
 * اندروید)، پشته یک فهرست است، و برگشت یعنی برداشتنِ آخری. همین چند
 * خط، جای یک وابستگیِ دیگر.
 *
 * **ویندوز این را ندارد و درست است:** آنجا نوارِ کناری هست و صفحه‌ها
 * کنارِ هم می‌نشینند، پس «برگشت» معنیِ دیگری دارد. اینجا گوشی است.
 */
class IosNavStack(start: String) {
    private val stack = mutableStateListOf(start)

    /** مقصدی که همین حالا روی صفحه است. */
    val current: String get() = stack.last()

    /** آیا چیزی برای برگشتن هست؟ */
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * رفتن به [route].
     *
     * اگر همان صفحه‌ای است که روی آن هستیم، هیچ اتفاقی نمی‌افتد —
     * وگرنه زدنِ دوبارهٔ یک خانه در نوارِ پایین پشته را بی‌دلیل بلند
     * می‌کرد و بعد کاربر باید چند بار برمی‌گشت تا به خانه برسد.
     */
    fun go(route: String) {
        if (stack.last() != route) stack.add(route)
    }

    /**
     * رفتن به یکی از خانه‌های نوارِ پایین.
     *
     * **با `go` فرق دارد و این فرق عمدی است.** خانه‌های نوارِ پایین
     * هم‌ترازند، نه تودرتو: رفتن از «مالی» به «فروش» پشته نمی‌سازد.
     * بی این، کاربری که چند بار بینِ خانه‌ها بچرخد پشته‌ای می‌سازد که
     * برگشت از آن ده بار طول می‌کشد.
     */
    fun switchTab(route: String) {
        stack.clear()
        stack.add(route)
    }

    /** برگشت. اگر چیزی زیر نباشد، هیچ — نه بستنِ برنامه. */
    fun back() {
        if (canGoBack) stack.removeAt(stack.size - 1)
    }
}

@Composable
fun rememberNavStack(start: String = Routes.HOME): IosNavStack =
    remember { IosNavStack(start) }
