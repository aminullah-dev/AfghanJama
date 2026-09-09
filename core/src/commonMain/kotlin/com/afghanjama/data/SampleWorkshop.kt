package com.afghanjama.data

import com.afghanjama.util.nowMillis
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.CustomerMeasurement
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.first

/**
 * کارگاهِ نمونه — یک کارگاهِ کاملِ در حالِ کار، با یک لمس.
 *
 * **چرا لازم شد.** اپ خالی بالا می‌آید، همان‌طور که یک کارگاهِ واقعی از
 * روزِ اول خالی است. برای کارفرمایی که دارد اپ را می‌خرد این یعنی
 * نصب می‌کند، باز می‌کند، و **هیچ** نمی‌بیند: نه سفارشی، نه خیاطی، نه
 * پارچه‌ای. هر صفحه‌ای که باز کند یک جای خالی است. صفحهٔ دمویی هم که
 * بگوید «امتحان کنید» به همین جای خالی می‌رسد.
 *
 * این سازنده همان کارگاه را می‌سازد که در تبلیغ وعده داده می‌شود.
 *
 * **از مسیرِ واقعی می‌سازد، نه با درجِ مستقیم در جدول‌ها.** هر تکه با
 * همان تابعی ساخته می‌شود که کاربرِ واقعی صدایش می‌زند — سفارش با
 * `createOrder`، برش با `completeCutting`، تحویل به خیاط با
 * `handoutToTailor`. دو نتیجه دارد:
 *
 *  ۱. دفترِ حساب، ژورنال و انبار **به‌ناچار** با هم می‌خوانند، چون از
 *     همان مسیری آمده‌اند که در کارِ واقعی می‌آیند. دادهٔ نمونه‌ای که
 *     با `INSERT` ساخته شود می‌تواند ترازنامه‌ای بدهد که جمع نمی‌زند —
 *     و بدترین چیزی که به یک خریدار می‌شود نشان داد همین است.
 *  ۲. خودِ این سازنده یک آزمونِ سرتاسری است: اگر روزی جریانِ سفارش
 *     بشکند، اینجا می‌شکند.
 *
 * **بیرون از `Repo` است و این عمدی است.** هر تابعی که صدا می‌زند خودش
 * اتمی است؛ اگر این کد داخلِ `Repo` می‌نشست، `txcheck` آن را یک
 * عملیاتِ غول‌آسای چندناحیه‌ای می‌دید و درست هم می‌گفت.
 */
object SampleWorkshop {

    /** نامِ کارگاهِ نمونه — در گزارش‌ها و سربرگِ فاکتور دیده می‌شود. */
    const val NAME = "خیاطی نمونهٔ کابل"

    /** خلاصهٔ آنچه ساخته شد، برای پیامِ پایان. */
    data class Result(
        val orders: Int,
        val people: Int,
        val customers: Int,
        val materials: Int
    )

    private data class Person(val code: String, val name: String, val phone: String)

    private val TAILORS = listOf(
        Person("T10", "احمد نوری", "0700111222"),
        Person("T11", "کریم رحیمی", "0700333444"),
        Person("T12", "نجیبه صادقی", "0700555666"),
        Person("T13", "وحید امیری", "0700777888")
    )

    private val INSPECTORS = listOf(
        Person("Q1", "مسعود کریمی", "0701111222"),
        Person("Q2", "زهرا احمدی", "0701333444")
    )

    private val CUSTOMERS = listOf(
        Person("", "شرکت ساختمانی کابل", "0202100100"),
        Person("", "بوتیک آرزو", "0700900901"),
        Person("", "حاجی نصیر", "0700900902"),
        Person("", "مکتب نور", "0700900903"),
        Person("", "خانم فرزانه", "0700900904")
    )

    /**
     * موادِ اولیه‌ای که کارگاه از قبل دارد.
     *
     * با `setOpeningStock` وارد می‌شوند نه با خرید — چون این‌ها
     * موجودیِ روزِ اول‌اند، نه خریدی که امروز پول برده. طرفِ دیگرِ
     * سندشان سرمایه است. همان مسیری که کارفرمای واقعی هنگامِ مهاجرت
     * از دفترِ قبلی‌اش می‌رود.
     */
    private data class Stock(
        val name: String,
        val unit: String,
        val amount: Double,
        val unitPrice: Long,
        val minLevel: Double
    )

    private val MATERIALS = listOf(
        Stock("کتان سرمه‌ای", "متر", 120.0, 320, 30.0),
        Stock("مخمل مشکی", "متر", 60.0, 540, 20.0),
        Stock("آستر سفید", "متر", 200.0, 90, 40.0),
        Stock("دکمه صدفی", "عدد", 500.0, 6, 100.0),
        Stock("زیپ ۵۰ سانتی", "عدد", 300.0, 25, 80.0),
        // عمداً زیرِ حدِ هشدار: تا هشدارِ «رو به اتمام» و برنامهٔ خرید
        // از همان لحظهٔ اول چیزی برای گفتن داشته باشند.
        Stock("نخ دوخت", "عدد", 12.0, 45, 40.0)
    )

    /**
     * کارگاهِ نمونه را می‌سازد.
     *
     * روی کارگاهی که از قبل داده دارد هم اجرا می‌شود و چیزی را پاک
     * نمی‌کند — فقط اضافه می‌کند. پاک کردن کارِ «بازنشانیِ داده» در
     * تنظیمات است، که مسیرِ خودش را دارد و تأیید می‌گیرد.
     */
    suspend fun create(repo: Repo): Result {
        seedMasterData(repo)
        seedPeople(repo)
        seedCustomers(repo)
        seedMaterials(repo)
        seedMoney(repo)
        val orders = seedOrders(repo)
        return Result(
            orders = orders,
            people = TAILORS.size + INSPECTORS.size,
            customers = CUSTOMERS.size,
            materials = MATERIALS.size
        )
    }

    // ── اطلاعات پایه ─────────────────────────────────────────────
    //
    // بی این‌ها هر فرمی در اپ کشوی خالی دارد و کاربرِ دمو در همان
    // قدمِ اول گیر می‌کند.
    private suspend fun seedMasterData(repo: Repo) {
        listOf(
            "کتان" to "تابستان",
            "مخمل" to "زمستان",
            "برزنت" to "چهارفصل",
            "لینن" to "تابستان"
        ).forEach { (title, season) ->
            repo.addFabricType(FabricType(title = title, season = season))
        }

        listOf("سرمه‌ای", "استخوانی", "مشکی", "زیتونی", "سفید").forEach {
            repo.addFabricColor(FabricColor(title = it))
        }

        listOf("۳۸", "۴۰", "۴۲", "۴۴", "مدیوم").forEach {
            repo.addSize(SizeItem(title = it))
        }

        listOf(
            "پیراهن مردانه" to "پیراهن",
            "کت زنانه" to "کت",
            "واسکت" to "واسکت",
            "لباس کار" to "لباس کار"
        ).forEach { (title, category) ->
            repo.addDesign(DesignItem(title = title, category = category))
        }

        listOf(
            "دکمه‌دوزی" to 25L,
            "اتوکاری" to 15L,
            "بسته‌بندی" to 10L
        ).forEach { (title, price) ->
            repo.insertWorkCost(WorkCost(title = title, price = price))
        }
    }

    private suspend fun seedPeople(repo: Repo) {
        TAILORS.forEach { repo.addTailor(Tailor(code = it.code, name = it.name, phone = it.phone)) }
        INSPECTORS.forEach {
            repo.addInspector(Inspector(code = it.code, name = it.name, phone = it.phone))
        }
        repo.addStaff("سمیع‌الله رسولی", "مسئول برش", 18_000L)
        repo.addStaff("مرضیه حیدری", "حسابدار", 22_000L)
    }

    /**
     * مشتریان، با تلفن و اندازه.
     *
     * تلفن الکی نیست: دکمهٔ «تماس» در دفتر کل و فهرستِ مشتریان فقط
     * وقتی دیده می‌شود که شماره‌ای ثبت شده باشد. بی آن، کسی که دمو را
     * می‌بیند فکر می‌کند اپ این قابلیت را ندارد.
     */
    private suspend fun seedCustomers(repo: Repo) {
        CUSTOMERS.forEach { repo.addCustomer(Customer(name = it.name, phone = it.phone)) }

        repo.customerByName("حاجی نصیر")?.let { c ->
            listOf("دور سینه" to "۱۰۴", "قد آستین" to "۶۲", "دور کمر" to "۹۸").forEach { (l, v) ->
                repo.upsertMeasurement(CustomerMeasurement(customerId = c.id, label = l, value = v))
            }
        }
        repo.customerByName("خانم فرزانه")?.let { c ->
            listOf("دور سینه" to "۹۰", "قد" to "۱۴۰", "دور کمر" to "۷۶").forEach { (l, v) ->
                repo.upsertMeasurement(CustomerMeasurement(customerId = c.id, label = l, value = v))
            }
        }
    }

    private suspend fun seedMaterials(repo: Repo) {
        MATERIALS.forEach { m ->
            repo.setOpeningStock(
                name = m.name,
                unit = m.unit,
                amount = m.amount,
                unitPrice = m.unitPrice,
                note = "موجودیِ کارگاهِ نمونه"
            )
            repo.setMaterialMinLevel(m.name, m.unit, m.minLevel)
        }
    }

    /**
     * پولِ روزِ اول، و یک هزینهٔ عادی.
     *
     * صندوق با `recordManualCash(isIn = true)` پر می‌شود که طرفِ دیگرش
     * **سرمایه** است، نه فروش — وگرنه سودِ کارگاهِ نمونه از همان لحظهٔ
     * اول دروغ می‌شد. همان اشکالی که یک بار در همین اپ پیدا شد.
     */
    private suspend fun seedMoney(repo: Repo) {
        repo.recordManualCash("WALLET", 150_000L, isIn = true, note = "مانده اولیهٔ صندوق")
        repo.recordManualCash("BANK", 400_000L, isIn = true, note = "مانده اولیهٔ بانک")
        repo.recordExpense("WALLET", "کرایه", 12_000L, "کرایهٔ ماه")
        repo.recordExpense("WALLET", "برق و آب", 3_500L, "بلِ برق")
    }

    // ── سفارش‌ها ─────────────────────────────────────────────────

    private fun order(
        n: Int,
        design: String,
        qty: Int,
        fabric: String,
        color: String,
        size: String,
        meters: Double,
        fabricPrice: Long,
        workCost: Long,
        customer: Person,
        agreed: Long
    ) = Order(
        orderCode = CodeGen.makeOrderCode(n),
        shortCode = CodeGen.makeShortCode(),
        designTitle = design,
        qty = qty,
        fabricType = fabric,
        fabricColor = color,
        size = size,
        fabricUnit = "متر",
        fabricAmount = meters,
        // از انبار مصرف می‌شود، نه خریدِ تازه — تا برش واقعاً از موجودی
        // کم کند و کاردکس چیزی برای نشان دادن داشته باشد.
        fabricSource = "STOCK",
        fabricPrice = fabricPrice,
        workCost = workCost,
        workCostSource = "CREDIT",
        workCostPayee = "خیاطی نمونه",
        agreedPrice = agreed,
        customerName = customer.name,
        customerPhone = customer.phone,
        status = OrderStatus.IN_STOCK.name,
        stageChangedAt = nowMillis()
    )

    /**
     * شش سفارش، در شش مرحلهٔ مختلف.
     *
     * عمدی است که هیچ دو تایشان در یک مرحله نباشند: هر صفحه‌ای که
     * کاربرِ دمو باز کند — تختهٔ کار، برش، دوخت، نظارت، صفِ تحویل —
     * باید چیزی در آن باشد. یک کارگاهِ واقعی هم همین شکلی است.
     */
    private suspend fun seedOrders(repo: Repo): Int {
        var made = 0

        // ۱ تازه ثبت‌شده — هنوز دستِ کسی نرفته
        val fresh = order(
            1, "پیراهن مردانه", 20, "کتان", "سرمه‌ای", "۴۲", 44.0,
            14_000, 6_000, CUSTOMERS[0], 42_000
        )
        if (repo.createOrder(fresh)) made++
        repo.recordOrderDeposit(fresh, 15_000L)

        // ۲ در حالِ برش
        val cutting = order(
            2, "کت زنانه", 12, "مخمل", "مشکی", "۴۰", 30.0,
            16_200, 7_200, CUSTOMERS[1], 55_000
        )
        if (repo.createOrder(cutting)) made++

        // ۳ دستِ خیاط — برش تمام، بین دو خیاط پخش شده
        val sewing = order(
            3, "واسکت", 16, "برزنت", "زیتونی", "۴۴", 24.0,
            7_680, 4_800, CUSTOMERS[2], 30_000
        )
        if (repo.createOrder(sewing)) {
            made++
            repo.completeCutting(sewing, "سمیع‌الله رسولی", 16, "۱ متر", "برشِ نمونه")
            repo.getOrder(sewing.id)?.let { o ->
                repo.handoutToTailor(o, "[T10] احمد نوری", 8, 300L)
                repo.getOrder(o.id)?.let { repo.handoutToTailor(it, "[T11] کریم رحیمی", 8, 300L) }
            }
        }

        // ۴ دستِ نظارت — یک خیاط کارش را تحویل داده
        val review = order(
            4, "لباس کار", 24, "برزنت", "استخوانی", "مدیوم", 48.0,
            15_360, 9_600, CUSTOMERS[3], 72_000
        )
        if (repo.createOrder(review)) {
            made++
            repo.completeCutting(review, "سمیع‌الله رسولی", 24, "۲ متر", "")
            repo.getOrder(review.id)?.let { o ->
                repo.handoutToTailor(o, "[T12] نجیبه صادقی", 24, 280L)
            }
            finishFirstAssignment(repo, review.id.toString())
            repo.getOrder(review.id)?.let { repo.sendOrderToReview(it) }
        }

        // ۵ در انبارِ محصول — نظارت را گذرانده، آمادهٔ تحویل
        val stored = order(
            5, "پیراهن مردانه", 10, "کتان", "سفید", "۳۸", 22.0,
            7_040, 3_000, CUSTOMERS[4], 25_000
        )
        if (repo.createOrder(stored)) {
            made++
            repo.completeCutting(stored, "سمیع‌الله رسولی", 10, "", "")
            repo.getOrder(stored.id)?.let { repo.handoutToTailor(it, "[T13] وحید امیری", 10, 320L) }
            finishFirstAssignment(repo, stored.id.toString())
            repo.getOrder(stored.id)?.let { repo.sendOrderToReview(it) }
            repo.getOrder(stored.id)?.let { repo.approveQc(it, "[Q1] مسعود کریمی", "سالم") }
        }

        // ۶ تحویل‌شده — با پرداختِ ناقص، تا مشتری بدهیِ باز داشته باشد
        //   و دفتر کل و «سنِ طلب» چیزی برای نشان دادن داشته باشند.
        val sent = order(
            6, "کت زنانه", 8, "مخمل", "مشکی", "۴۰", 20.0,
            10_800, 4_800, CUSTOMERS[1], 60_000
        )
        if (repo.createOrder(sent)) {
            made++
            repo.completeCutting(sent, "سمیع‌الله رسولی", 8, "", "")
            repo.getOrder(sent.id)?.let { repo.handoutToTailor(it, "[T10] احمد نوری", 8, 350L) }
            finishFirstAssignment(repo, sent.id.toString())
            repo.getOrder(sent.id)?.let { repo.sendOrderToReview(it) }
            repo.getOrder(sent.id)?.let { repo.approveQc(it, "[Q2] زهرا احمدی", "سالم") }
            repo.getOrder(sent.id)?.let { o ->
                repo.deliverOrderToCustomer(
                    order = o,
                    qty = o.qty,
                    unitPrice = 7_500L,
                    receivedNow = 40_000L,
                    applyPrepay = 0L
                )
            }
        }

        // کارمزدِ یک خیاط تسویه می‌شود و بقیه باز می‌مانند — تا هم
        // «پرداخت‌شده» دیده شود هم «طلبِ خیاط».
        repo.settleTailorWages("[T13] وحید امیری", 3_200L)

        return made
    }

    /**
     * اولین تحویلِ دوختِ یک سفارش را «انجام‌شده» می‌کند.
     *
     * `completeAssignment` شناسهٔ تحویل می‌خواهد و آن شناسه فقط بعد از
     * ساخته شدنش معلوم است، پس از همان جریانی خوانده می‌شود که صفحهٔ
     * دوخت می‌خواند.
     */
    private suspend fun finishFirstAssignment(repo: Repo, orderId: String) {
        val rows = repo.observeAssignmentsForOrder(orderId).first()
        rows.firstOrNull()?.let { repo.completeAssignment(it.id, quality = "سالم") }
    }
}
