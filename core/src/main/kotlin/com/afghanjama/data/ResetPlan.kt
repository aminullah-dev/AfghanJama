package com.afghanjama.data

/**
 * چه چیزی با «ریست داده» می‌رود و چه چیزی می‌ماند.
 *
 * ریست برای وقتی است که تستِ اپ تمام شده و کارگاه می‌خواهد با دفترِ تمیز
 * شروع کند — بدونِ اینکه مجبور شود خیاط‌ها، پارچه‌ها، سایزها، طرح‌ها و
 * مشتری‌ها را دوباره وارد کند.
 *
 * فهرست عمداً اینجاست و نه پراکنده در DAOها، تا یک نگاه بگوید چه اتفاقی
 * می‌افتد. خطرش این است که نامِ اشتباه بی‌سروصدا رد شود یا جدولِ تازه‌ای
 * دسته‌بندی نشود؛ هر دو با بررسی‌های `checkResetPlan` و وارسیِ زندهٔ
 * خودآزمایی گرفته می‌شوند.
 */
object ResetPlan {

    /**
     * اطلاعات پایه و مشتریان — دست نمی‌خورند.
     *
     * مشتری و اندازه‌هایش عمداً اینجاست: جمع‌کردنشان دوباره سخت است و
     * سرمایهٔ کارگاه‌اند. حسابشان با پاک‌شدنِ دفتر خودش صفر می‌شود.
     */
    val KEEP = listOf(
        "fabric_types",
        "fabric_colors",
        "sizes",
        "tailors",
        "inspectors",
        "design_items",
        "GarmentDesign",
        "WorkCost",
        "staff",
        "break_times",
        "customers",
        "customer_measurements"
    )

    /**
     * کارها و حساب‌ها — پاک می‌شوند.
     *
     * انبار هم در این فهرست است چون موجودی از خرید و تولید آمده و هر دو
     * دارند پاک می‌شوند. اگر انبار می‌ماند ولی دفتر صفر می‌شد، قاعدهٔ
     * «حسابِ موجودی محصول == جمعِ ارزشِ ردیف‌ها» می‌شکست.
     */
    val CLEAR = listOf(
        // سفارش و همهٔ فرزندانش
        "orders",
        "order_stage_logs",
        "order_fabrics",
        "order_work_items",
        "order_photos",
        "sewing_assignments",
        "cutting_records",
        "qc_records",
        // پول
        "finance_transactions",
        "customer_payments",
        "tailor_wages",
        "salary_payments",
        // دفتر کل و اسناد
        "parties",
        "ledger_entries",
        "documents",
        "journal_entries",
        "journal_lines",
        // خرید
        "purchase_invoices",
        "purchase_items",
        "supplier_ledger",
        // انبار
        "material_stock",
        "stock_movements",
        "finished_stock",
        "finished_sales",
        // بقیه
        "attendance",
        "audit_log",
        // رویدادها با دفتر می‌روند: هر رویداد به سطری اشاره می‌کند که
        // همین‌جا پاک می‌شود، پس نگه داشتنشان یعنی تاریخچه‌ای که به
        // هیچ‌چیز وصل نیست.
        "domain_events",
        "sync_requests",
        // شمارندهٔ سفارش؛ نبودنش یعنی شمارهٔ بعدی از ۱ شروع می‌شود
        "OrderCounter"
    )

    /** همهٔ جدول‌هایی که دربارهٔ سرنوشتشان تصمیم گرفته شده. */
    val ALL: List<String> get() = CLEAR + KEEP
}
