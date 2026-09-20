package com.afghanjama.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.ui.graphics.vector.ImageVector
import com.afghanjama.ui.vm.UserRole

/*
 * نوارِ پایین — **یک تعریف برای هر سه سکو.**
 *
 * تا دیروز این دو در `AppNav.kt` خصوصی بودند، و درست بود: فقط اندروید
 * نوارِ پایین داشت (ویندوز نوارِ کناری دارد). با آمدنِ آیفون دومین
 * سکوی گوشی‌شکل پیدا شد، و همان لحظه انتخاب این بود: یا پنج خط کپی
 * شود، یا تعریف یک جا بیاید.
 *
 * کپی‌کردنش یعنی روزی کسی خانه‌ای به اندروید اضافه کند و آیفون همان
 * صفحه را داشته باشد ولی راهِ رسیدنش را نه — همان جنس اشکالی که
 * `dbtwin` و `corecompose` برای گرفتنش نوشته شده‌اند، این بار در
 * ناوبری.
 */
/** آیتم نوار پایین. */
data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /**
     * مسیرهای دیگری که همین خانه نمایندگی‌شان می‌کند.
     *
     * «کارگاه» سه مرحله را زیرِ خودش دارد؛ بی این، کاربر روی دوخت
     * می‌رفت و نوارِ پایین هیچ خانه‌ای را روشن نشان نمی‌داد — یعنی
     * «کجا هستم؟» بی‌جواب می‌مانْد.
     */
    val alsoOwns: List<String> = emptyList()
) {
    fun owns(route: String?): Boolean = route == this.route || route in alsoOwns
}

/** آیتم‌های نوار پایین بر اساس نقش کاربر. */
fun bottomItemsFor(role: UserRole): List<BottomItem> = when (role) {
    // **پنج خانه، نه هفت.**
    //
    // تا امروز هفت تا بود: خانه، تولید، برش، دوخت، نظارت، فروش، مالی.
    // متریال سه تا پنج می‌گوید. اول فکر کردم دلیلش هدفِ لمس است، ولی
    // اندازه گرفتم و نبود: روی باریک‌ترین گوشیِ رایج (۳۶۰dp) هفت خانه
    // ۵۱٫۴dp می‌دهد، بالای حدِ ۴۸dp.
    //
    // مسئله خواندن است. برچسبِ فارسی در ۵۱ نقطه جا می‌شود ولی جایی
    // برای نفس کشیدن ندارد، و چشم برای پیدا کردنِ یکی همهٔ هفت تا را
    // می‌خواند. با پنج خانه همان برچسب‌ها ۷۲ نقطه دارند.
    //
    // برش و دوخت و نظارت یک کارند که پشتِ سرِ هم می‌آیند. حالا زیرِ
    // «کارگاه» جمع شده‌اند و در خودِ آن صفحه‌ها با چیپ از هم جدا
    // می‌شوند. هیچ صفحه‌ای حذف نشد — فقط راهِ رسیدن یکی شد.
    UserRole.MANAGER -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.INVENTORY, "سفارش‌ها", Icons.Default.Inventory2),
        BottomItem(
            Routes.CUTTING, "کارگاه", Icons.Default.ContentCut,
            alsoOwns = listOf(Routes.SEWING, Routes.REVIEW)
        ),
        BottomItem(Routes.FINISHED_SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.FINANCE, "مالی", Icons.Default.Payments)
    )

    UserRole.PURCHASE -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.PROCUREMENT, "خرید مواد", Icons.Default.ShoppingCart),
        BottomItem(Routes.WAREHOUSE, "انبار", Icons.Default.Warehouse),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SEWING -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.CUTTING, "برش", Icons.Default.ContentCut),
        BottomItem(Routes.SEWING, "دوخت", Icons.Default.Checkroom),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.REVIEW -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.REVIEW, "نظارت", Icons.Default.VerifiedUser),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )

    UserRole.SALES -> listOf(
        BottomItem(Routes.HOME, "خانه", Icons.Default.Home),
        BottomItem(Routes.FINISHED_SALES, "فروش", Icons.Default.Storefront),
        BottomItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )
}
