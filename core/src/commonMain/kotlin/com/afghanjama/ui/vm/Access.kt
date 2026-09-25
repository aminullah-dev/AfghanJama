package com.afghanjama.ui.vm

import com.afghanjama.prefs.Settings
import com.afghanjama.ui.nav.Routes
import com.afghanjama.util.nowMillis
import kotlin.concurrent.Volatile

/**
 * بخش‌ها و کارهایی که مدیر برای هر نفر تیک می‌زند.
 *
 * **چرا شخص‌محور و نه نقش‌محور.** نقش‌های ثابت (خرید، دوخت، نظارت، فروش)
 * جوابِ کارگاهِ واقعی نبودند: یک نفر هم برش می‌زند هم دوخت، دیگری فروش
 * می‌کند ولی نباید صندوق را ببیند. پس مدیر برای **هر نفر** مشخص می‌کند
 * به کدام بخش راه دارد و کدام کارِ حساس را می‌تواند بکند.
 *
 * [group] فقط برای چیدنِ تیک‌ها در صفحهٔ کاربران است.
 */
enum class Feature(val label: String, val group: String) {
    ORDERS("سفارش‌ها و خط تولید", GROUP_WORK),
    CUTTING("برش", GROUP_WORK),
    SEWING("دوخت", GROUP_WORK),
    REVIEW("نظارت", GROUP_WORK),
    DELIVERY("آمادهٔ تحویل", GROUP_WORK),
    BOARD("تابلوی دوخت و بارِ کارگاه", GROUP_WORK),

    SALES("فروش و انبار محصول", GROUP_SALES),
    CUSTOMERS("مشتریان", GROUP_SALES),

    WAREHOUSE("انبار مواد", GROUP_STOCK),
    PROCUREMENT("خرید مواد", GROUP_STOCK),

    FINANCE("مالی، صندوق، گزارش‌ها و اسناد", GROUP_MONEY),
    LEDGER("دفتر کل", GROUP_MONEY),
    PAYROLL("حقوق و کارنامهٔ کارکنان", GROUP_MONEY),
    ATTENDANCE("حضور و غیاب", GROUP_MONEY),

    MASTER("اطلاعات پایه", GROUP_ADMIN),
    AUDIT("مرکز هشدار و رویدادها", GROUP_ADMIN),

    EDIT_ORDER("ویرایش و حذفِ سفارش", GROUP_ACTIONS),
    RETURN_SALE("برگشتِ فروش", GROUP_ACTIONS),
    SETTLE_WAGES("تسویهٔ کارمزدِ خیاط", GROUP_ACTIONS),
    ADJUST_STOCK("اصلاحِ دستیِ موجودیِ انبار", GROUP_ACTIONS),
    BACKUP("پشتیبان‌گیری و خروجی", GROUP_ACTIONS);

    companion object {
        /** ترتیبِ گروه‌ها در صفحهٔ کاربران. */
        val GROUPS: List<String> =
            listOf(GROUP_WORK, GROUP_SALES, GROUP_STOCK, GROUP_MONEY, GROUP_ADMIN, GROUP_ACTIONS)

        /**
         * پیش‌تنظیم‌ها — یک لمس به‌جای ده تیک. بعدش هر تیکی قابلِ تغییر است.
         */
        val PRESETS: List<Pair<String, Set<Feature>>> = listOf(
            "برشکار" to setOf(CUTTING, BOARD),
            "خیاط" to setOf(SEWING, BOARD),
            "ناظر" to setOf(REVIEW, BOARD),
            "فروشنده" to setOf(SALES, CUSTOMERS),
            "خرید و انبار" to setOf(WAREHOUSE, PROCUREMENT)
        )
    }
}

private const val GROUP_WORK = "تولید"
private const val GROUP_SALES = "فروش و مشتری"
private const val GROUP_STOCK = "انبار و خرید"
private const val GROUP_MONEY = "پول و آدم‌ها"
private const val GROUP_ADMIN = "مدیریت"
private const val GROUP_ACTIONS = "کارهای حساس"

/**
 * آنچه کاربرِ واردشده اجازه دارد.
 *
 * مدیر همه‌چیز دارد و این قابلِ تغییر نیست — مدیری که خودش را قفل کند
 * راهِ برگشت ندارد.
 */
data class Access(val isManager: Boolean, val features: Set<Feature>) {

    fun has(f: Feature): Boolean = isManager || f in features

    companion object {
        val MANAGER = Access(true, emptySet())
        val NONE = Access(false, emptySet())

        /**
         * دسترسیِ نقش‌های قدیمی — همان رفتاری که پیش از کاربرانِ شخصی بود.
         *
         * دستگاهی که با نقشِ «دوخت» یا «فروش» راه افتاده، بی‌آنکه کسی
         * برایش کاربر بسازد، همان چیزی را می‌بیند که دیروز می‌دید.
         */
        fun forRole(role: UserRole): Access = when (role) {
            UserRole.MANAGER -> MANAGER
            UserRole.PURCHASE -> Access(false, setOf(Feature.WAREHOUSE, Feature.PROCUREMENT, Feature.BOARD))
            UserRole.SEWING -> Access(
                false, setOf(Feature.CUTTING, Feature.SEWING, Feature.BOARD, Feature.WAREHOUSE)
            )
            UserRole.REVIEW -> Access(false, setOf(Feature.REVIEW, Feature.BOARD, Feature.WAREHOUSE))
            UserRole.SALES -> Access(
                false, setOf(Feature.SALES, Feature.CUSTOMERS, Feature.BOARD, Feature.WAREHOUSE)
            )
        }
    }
}

/**
 * دسترسیِ همین حالا — برای [Permissions]، که از ده‌ها صفحه با `role`
 * صدا زده می‌شود. ورود و خروج (`AuthViewModel`) تنظیمش می‌کنند.
 *
 * پیش‌فرض «هیچ» است نه «همه»: اگر جایی فراموش شود، کسی بیش از حقش نمی‌بیند.
 */
object CurrentAccess {
    @Volatile
    var value: Access = Access.NONE
}

/**
 * کدام صفحه چه تیکی لازم دارد.
 *
 * صفحه‌ای که اینجا نیست و در [ALWAYS] هم نیست فقط مالِ مدیر است
 * (خودآزمایی، کارگاهِ نمونه، کاربران) — پیش‌فرض بسته است.
 */
object RouteAccess {

    /** برای هر کسی که وارد شده. */
    private val ALWAYS = setOf(
        Routes.LOGIN, Routes.POST_LOGIN, Routes.HOME, Routes.SETTINGS, Routes.GUIDE,
        Routes.MY_WORK, Routes.QUOTE_CALC, Routes.WORKSHOP_LINK,
        // جست‌وجو و جزئیاتِ سفارش از قبل برای همه بود؛ ویرایش و حذفش
        // جداگانه تیک می‌خواهد (`EDIT_ORDER`).
        Routes.SEARCH, Routes.ORDER_DETAIL
    )

    private val NEEDS: Map<String, Feature> = mapOf(
        Routes.INVENTORY to Feature.ORDERS,
        Routes.PRODUCTION_ORDER to Feature.ORDERS,
        Routes.CUTTING to Feature.CUTTING,
        Routes.SEWING to Feature.SEWING,
        Routes.REVIEW to Feature.REVIEW,
        Routes.DELIVERY_QUEUE to Feature.DELIVERY,
        Routes.BOARD to Feature.BOARD,
        Routes.WORKSHOP_LOAD to Feature.BOARD,
        Routes.FINISHED_SALES to Feature.SALES,
        Routes.NEW_SALE to Feature.SALES,
        Routes.CUSTOMERS to Feature.CUSTOMERS,
        Routes.CUSTOMER_DETAIL to Feature.CUSTOMERS,
        Routes.WAREHOUSE to Feature.WAREHOUSE,
        Routes.STOCK_LEDGER to Feature.WAREHOUSE,
        Routes.PROCUREMENT to Feature.PROCUREMENT,
        Routes.PURCHASE_PLAN to Feature.PROCUREMENT,
        Routes.PURCHASE_RETURN to Feature.PROCUREMENT,
        Routes.FINANCE to Feature.FINANCE,
        Routes.REPORTS to Feature.FINANCE,
        Routes.JOURNAL to Feature.FINANCE,
        Routes.RECURRING to Feature.FINANCE,
        Routes.DOCUMENTS to Feature.FINANCE,
        Routes.PAY to Feature.FINANCE,
        Routes.RECEIVE to Feature.FINANCE,
        Routes.DAILY_TRADE to Feature.FINANCE,
        Routes.LEDGER to Feature.LEDGER,
        Routes.PAYROLL to Feature.PAYROLL,
        Routes.PERFORMANCE to Feature.PAYROLL,
        Routes.ATTENDANCE to Feature.ATTENDANCE,
        Routes.MASTER to Feature.MASTER,
        Routes.ACTION_CENTER to Feature.AUDIT,
        Routes.AUDIT to Feature.AUDIT
    )

    /** [route] ممکن است آرگومان داشته باشد («order_detail/12»). */
    fun canOpen(access: Access, route: String?): Boolean {
        if (route == null || access.isManager) return true
        val base = route.substringBefore('/').substringBefore('?')
        if (base in ALWAYS) return true
        val needed = NEEDS[base] ?: return false
        return access.has(needed)
    }
}

/**
 * یک نفر با رمزِ خودش و تیک‌هایش.
 *
 * [name] روی دفترِ رویدادها می‌نشیند — «چه کسی چه کرد». اگر همان نامِ
 * خیاط باشد، «کارِ من» هم کارهای خودِ او را نشان می‌دهد.
 */
data class AppUser(
    val id: String,
    val name: String,
    val pinHash: String,
    val features: Set<Feature>
) {
    val access: Access get() = Access(false, features)

    /**
     * نزدیک‌ترین نقشِ قدیمی — فقط برای جاهایی که هنوز با نقش کار
     * می‌کنند (برچسبِ رویداد). هرگز مدیر نیست.
     */
    fun legacyRole(): UserRole = when {
        Feature.SEWING in features || Feature.CUTTING in features -> UserRole.SEWING
        Feature.REVIEW in features -> UserRole.REVIEW
        Feature.PROCUREMENT in features -> UserRole.PURCHASE
        else -> UserRole.SALES
    }
}

/**
 * کاربرانِ همین دستگاه، در تنظیماتِ همین دستگاه.
 *
 * **روی همین دستگاه.** مدیر روی گوشی یا پی‌سی‌ای که چند نفر با آن کار
 * می‌کنند کاربر می‌سازد؛ گوشیِ شخصیِ یک کارگر جداست و این فهرست را ندارد.
 */
object UserStore {
    private const val FILE = "app_users"
    private const val KEY = "list"

    fun load(settings: Settings): List<AppUser> = decode(settings.getString(FILE, KEY))

    fun save(settings: Settings, users: List<AppUser>) =
        settings.putString(FILE, KEY, encode(users))

    fun find(settings: Settings, id: String): AppUser? = load(settings).firstOrNull { it.id == id }

    fun newId(): String = "u" + nowMillis().toString(36)

    /** یک سطر برای هر نفر، خانه‌ها با تب. تب و خطِ تازه از نام برداشته می‌شوند. */
    fun encode(users: List<AppUser>): String = users.joinToString("\n") { u ->
        listOf(
            u.id,
            u.name.replace('\t', ' ').replace('\n', ' ').trim(),
            u.pinHash,
            u.features.joinToString(",") { it.name }
        ).joinToString("\t")
    }

    fun decode(raw: String): List<AppUser> = raw.lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4 || f[0].isBlank() || f[2].isBlank()) return@mapNotNull null
        val features = f[3].split(',')
            .mapNotNull { n -> Feature.entries.firstOrNull { it.name == n.trim() } }
            .toSet()
        AppUser(id = f[0], name = f[1], pinHash = f[2], features = features)
    }
}
