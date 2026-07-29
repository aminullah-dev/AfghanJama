package com.afghanjama.data

/**
 * قاعدهٔ خروجِ پول از صندوق‌های کارگاه — یک‌جا و بی‌وابستگی.
 *
 * چرا جدا از [com.afghanjama.data.repo.Repo]؟ چون قاعده یک بار دو نسخه شد:
 * چهار مسیرِ خروجِ نقد موجودی را می‌سنجیدند و چهار مسیر نه، و صفحهٔ خرید
 * نسخهٔ خودش را داشت. هر نسخه که جا بماند، صندوق منفی می‌شود.
 *
 * اینجا هیچ چیزِ اندرویدی و هیچ دیتابیسی نیست، پس هم `Repo` صدایش می‌زند
 * و هم خودآزمایی و تستِ CI مستقیم می‌سنجندش.
 */
object CashPolicy {

    /**
     * آیا این مبلغ از صندوقی با این موجودی قابلِ پرداخت است؟
     *
     * مبلغِ صفر یا منفی «قابلِ پرداخت» شمرده می‌شود چون خروجِ پولی ندارد؛
     * ردکردنش کارِ کنترلِ ورودی است، نه کارِ این قاعده. مساوی بودن قبول
     * است: صندوق می‌تواند تا صفر خالی شود، ولی نه یک افغانی زیرِ آن.
     */
    fun canSpend(balance: Long, amount: Long): Boolean =
        amount <= 0L || balance >= amount

    /**
     * آیا این منبعِ پرداخت واقعاً از صندوق پول کم می‌کند؟
     *
     * «نسیه» بدهیِ فروشنده می‌شود و «به حساب مشتری» به حسابِ او می‌رود؛
     * هیچ‌کدام به موجودیِ صندوق کار ندارند، پس نباید به کنترل گیر کنند.
     */
    fun isCashSource(source: String): Boolean =
        source != "CREDIT" && source != "CUSTOMER"

    /**
     * فهرستِ مسیرهایی که پول از صندوق بیرون می‌برند.
     *
     * این فهرست سند نیست، آزمون است: خودآزمایی و CI می‌سنجند که هر مسیر
     * نگهبان داشته باشد و ردشدنش را به کاربر بگوید. مسیرِ تازه‌ای که به
     * اینجا اضافه نشود، در بازبینیِ کد پیدا می‌شود؛ مسیری که اضافه شود
     * ولی نگهبان نداشته باشد، همان‌جا در CI قرمز می‌کند.
     *
     * هر جفت: نامِ تابع → (نگهبان، آیا ردشدن را به کاربر می‌گوید).
     */
    val OUTFLOWS: List<Triple<String, String, Boolean>> = listOf(
        Triple("transfer", "hasFunds", true),
        Triple("recordExpense", "hasFunds", true),
        Triple("recordManualCash", "hasFunds", true),
        Triple("recordPurchaseInvoice", "hasFunds", true),
        Triple("recordManualLedger", "balanceOf", true),
        Triple("recordSaleReturn", "balanceOf", true),
        Triple("paySalary", "balanceOf", true),
        // تنها صداکننده‌اش مسیرِ کنترل‌شدهٔ recordManualLedger است، پس در
        // عمل پوشیده است. اگر روزی از جای دیگری صدا زده شد، نگهبان لازم دارد.
        Triple("settleTailorWages", "recordManualLedger", true),
        // برگشتِ سود به کیف پول تابعِ جدایی نیست: داخلِ recordSaleReturn
        // است و به‌جای ردکردن، مبلغ را تا موجودیِ صندوقِ فایده می‌بُرد
        // (minOf با balanceOf). پس هرگز منفی نمی‌شود و پیامِ ردی هم ندارد.
        Triple("recordSaleReturn/برگشت سود", "minOf(balanceOf)", true)
    )
}
