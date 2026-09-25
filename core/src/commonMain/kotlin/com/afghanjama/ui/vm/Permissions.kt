package com.afghanjama.ui.vm

/**
 * سطح دسترسی متمرکز.
 * هر عمل حساس از اینجا کنترل می‌شود تا قوانین یک‌جا و قابل‌تغییر باشند.
 *
 * **حالا از تیک‌های مدیر می‌خوانَد، نه فقط از نقش.** هر نفر (یا نقشِ
 * قدیمیِ دستگاه) یک [Access] دارد که هنگامِ ورود در [CurrentAccess]
 * می‌نشیند. `role` هنوز پارامتر است چون ده‌ها صفحه همین را صدا می‌زنند؛
 * مدیر همیشه همه‌چیز دارد.
 */
object Permissions {

    private fun allowed(role: UserRole, f: Feature): Boolean =
        role == UserRole.MANAGER || CurrentAccess.value.has(f)

    /** پشتیبان‌گیری، بازیابی و خروجی CSV. */
    fun canBackup(role: UserRole) = allowed(role, Feature.BACKUP)

    /** تسویه کارمزد خیاط. */
    fun canSettleWages(role: UserRole) = allowed(role, Feature.SETTLE_WAGES)

    /** ثبت هزینه عمومی و انتقال بین صندوق‌ها. */
    fun canManageFinance(role: UserRole) = allowed(role, Feature.FINANCE)

    /** برگشت فروش (سفارش تحویل‌شده). */
    fun canReturnSale(role: UserRole) = allowed(role, Feature.RETURN_SALE)

    /** ویرایش مشخصات و حذف سفارش. */
    fun canEditOrder(role: UserRole) = allowed(role, Feature.EDIT_ORDER)

    /** اصلاح دستی موجودی پارچه (شمارش انبار). */
    fun canAdjustStock(role: UserRole) = allowed(role, Feature.ADJUST_STOCK)

    /** خرید پارچه برای انبار. */
    fun canBuyFabric(role: UserRole) = allowed(role, Feature.PROCUREMENT)

    /** خرید مواد خام (فاکتور آزاد چند قلمی). */
    fun canBuyMaterial(role: UserRole) = allowed(role, Feature.PROCUREMENT)

    /** اصلاح دستی انبار مواد. */
    fun canAdjustMaterial(role: UserRole) = allowed(role, Feature.ADJUST_STOCK)

    /**
     * دیدنِ فهرستِ مشتریان. قبلاً هر نقشی — از جمله خیاط — فهرستِ کاملِ
     * مشتریان و شماره‌هایشان را می‌دید؛ این دسترسی هیچ‌وقت عمدی نبود.
     */
    fun canSeeCustomers(role: UserRole) = allowed(role, Feature.CUSTOMERS)

    /** مدیریت اطلاعات پایه. */
    fun canManageMaster(role: UserRole) = allowed(role, Feature.MASTER)

    /**
     * پاک‌کردنِ کارها و حساب‌ها. برگشت‌ناپذیرترین کارِ اپ، پس فقط مدیر —
     * تیکی برایش نیست.
     */
    fun canResetData(role: UserRole) = role == UserRole.MANAGER
}
