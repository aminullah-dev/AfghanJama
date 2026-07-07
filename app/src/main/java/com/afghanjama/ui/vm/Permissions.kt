package com.afghanjama.ui.vm

/**
 * سطح دسترسی متمرکز نقش‌ها.
 * هر عمل حساس از اینجا کنترل می‌شود تا قوانین یک‌جا و قابل‌تغییر باشند.
 */
object Permissions {

    /** پشتیبان‌گیری، بازیابی و خروجی CSV. */
    fun canBackup(role: UserRole) = role == UserRole.MANAGER

    /** تسویه کارمزد خیاط. */
    fun canSettleWages(role: UserRole) = role == UserRole.MANAGER

    /** ثبت هزینه عمومی و انتقال بین صندوق‌ها. */
    fun canManageFinance(role: UserRole) = role == UserRole.MANAGER

    /** برگشت فروش (سفارش تحویل‌شده). */
    fun canReturnSale(role: UserRole) = role == UserRole.MANAGER

    /** ویرایش مشخصات و حذف سفارش. */
    fun canEditOrder(role: UserRole) = role == UserRole.MANAGER

    /** اصلاح دستی موجودی پارچه (شمارش انبار). */
    fun canAdjustStock(role: UserRole) = role == UserRole.MANAGER

    /** خرید پارچه برای انبار. */
    fun canBuyFabric(role: UserRole) =
        role == UserRole.MANAGER || role == UserRole.PURCHASE

    /** مدیریت اطلاعات پایه. */
    fun canManageMaster(role: UserRole) = role == UserRole.MANAGER
}
