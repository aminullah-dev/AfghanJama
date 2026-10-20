// app/src/main/java/com/afghanjama/ui/vm/UserRole.kt
package com.afghanjama.ui.vm

enum class UserRole {
    MANAGER,
    PURCHASE,
    SEWING,
    REVIEW,
    SALES
}

/**
 * نامِ دریِ نقش — صفحهٔ ورود و پایینِ نوارِ کنارِ کامپیوتر از همین
 * می‌خوانند، تا یک نقش در دو جا دو نام نداشته باشد.
 */
val UserRole.label: String
    get() = when (this) {
        UserRole.MANAGER -> "مدیریت"
        UserRole.PURCHASE -> "مسئول خرید"
        UserRole.SEWING -> "مدیریت دوخت"
        UserRole.REVIEW -> "نظارت"
        UserRole.SALES -> "مدیریت فروش"
    }
