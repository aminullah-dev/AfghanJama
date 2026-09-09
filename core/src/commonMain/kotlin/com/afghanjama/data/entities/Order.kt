package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

@Entity(tableName = "orders")
data class Order(

    @PrimaryKey
    val id: UUID = randomUuid(),

    val orderCode: String,
    val shortCode: String,

    val designTitle: String,
    val qty: Int,

    val fabricType: String,
    val fabricColor: String,
    val size: String,

    val fabricUnit: String,
    val fabricAmount: Double,

    // منبع پارچه: NEW = خرید جدید برای همین سفارش، STOCK = مصرف از موجودی انبار
    @ColumnInfo(defaultValue = "NEW")
    val fabricSource: String = "NEW",

    val fabricPrice: Long,
    val workCost: Long,

    /**
     * خرج‌کار از کجا پرداخت شد: WALLET / BANK / PROFIT / CREDIT.
     *
     * تا امروز خرج‌کار همیشه بدهی ثبت می‌شد و هیچ طرفِ حسابی نداشت، پس
     * هرگز تسویه نمی‌شد و پول از صندوق بیرون نمی‌رفت. نتیجه: بهای
     * تمام‌شده روی کاغذ درست بود ولی صندوق واقعیت را نشان نمی‌داد.
     *
     * CREDIT پیش‌فرض است تا سفارش‌های ثبت‌شده همان رفتارِ قبلی را داشته
     * باشند؛ برای نسیه نامِ طرف هم لازم است تا بشود بعداً تسویه‌اش کرد.
     */
    @ColumnInfo(defaultValue = "CREDIT")
    val workCostSource: String = "CREDIT",

    /** طرفِ حسابِ خرج‌کار — برای نسیه لازم است، وگرنه بدهی بی‌صاحب می‌مانَد. */
    @ColumnInfo(defaultValue = "")
    val workCostPayee: String = "",

    // دستمزد دوختِ انباشته از تحویل‌های خیاط (بخشی از بهای تمام‌شده)
    @ColumnInfo(defaultValue = "0")
    val sewingCost: Long = 0,

    // قیمت فروش توافق‌شده با مشتری (۰ = توافق نشده)
    @ColumnInfo(defaultValue = "0")
    val agreedPrice: Long = 0,

    val customerName: String,
    val customerPhone: String,

    val status: String,

    val assignedTailor: String? = null,
    val assignedInspector: String? = null,

    /*
     * **این دو ستون مرده‌اند — به آن‌ها تکیه نکنید.**
     *
     * هیچ‌جای اپ نه می‌خواندشان نه می‌نویسدشان؛ جست‌وجو شد و صفر
     * مورد پیدا شد. بازمانده‌ی طرحِ قدیمیِ «دوختِ جزئی»اند که بعداً
     * با `reviewQty`/`storedQty`/`deliveredQty` بازنویسی شد
     * (`docs/KNOWN-ISSUE-partial-review.md`).
     *
     * برداشته نمی‌شوند چون حذفِ ستون یعنی یک مهاجرتِ تازه و بالا
     * بردنِ `DB_VERSION` — هزینه‌ای که برای دو عددِ همیشه-صفر
     * نمی‌ارزد، به‌ویژه که طرحِ نسخهٔ فعلی هنوز کامیت نشده. ولی
     * کسی که فردا اینجا را می‌خواند باید بداند صفر بودنشان معنایی
     * ندارد: هرگز پر نشده‌اند.
     */
    val underSewCount: Int = 0,
    val doneSewCount: Int = 0,

    /**
     * چند عدد از این سفارش تا حالا به مشتری تحویل شده.
     *
     * تحویل می‌تواند تکه‌تکه باشد: مشتری امروز ۱۲ عدد می‌برد و باقی را
     * هفتهٔ بعد. سفارش تا وقتی همه‌اش تحویل نشده در صفِ تحویل می‌ماند.
     */
    @ColumnInfo(defaultValue = "0")
    val deliveredQty: Int = 0,

    /**
     * چند عدد از این سفارش همین حالا دستِ نظارت است.
     *
     * دوخت تکه‌تکه تمام می‌شود و هر تکه به‌محضِ آماده شدن به نظارت
     * می‌رود، پس «سفارش در نظارت است» جوابِ کاملی نیست؛ باید معلوم باشد
     * چند عدد. با تأییدِ ناظر همین تعداد وارد انبار می‌شود و این صفر
     * می‌شود. شرح در docs/KNOWN-ISSUE-partial-review.md.
     */
    @ColumnInfo(defaultValue = "0")
    val reviewQty: Int = 0,

    /** چند عدد از این سفارش تا امروز نظارت را گذرانده و وارد انبار شده. */
    @ColumnInfo(defaultValue = "0")
    val storedQty: Int = 0,

    /**
     * چه مبلغی از بهای این سفارش تا امروز از «کار در جریان» بیرون رفته.
     *
     * پایهٔ تقسیمِ بها بینِ ارسال‌های جزئی: هر دسته سهمِ انباشتهٔ خودش را
     * می‌برد منهای همین عدد، تا جمعِ دسته‌ها دقیقاً برابرِ کلِ بها شود و
     * ته‌ماندهٔ تقسیم جایی گم نشود.
     */
    @ColumnInfo(defaultValue = "0")
    val storedCost: Long = 0,

    val reviewed: Boolean = false,

    // آیا موادِ این سفارش از انبار کسر شده؟ در مدل جدید کسرِ مواد هنگام
    // «برش» انجام می‌شود، نه هنگام ثبت سفارش. مبنای برگشتِ مواد هنگام حذف
    // سفارش این پرچم است (فقط اگر واقعاً کسر شده باشد).
    @ColumnInfo(defaultValue = "0")
    val materialsConsumed: Boolean = false,

    // زمان ورود به مرحله فعلی (برای هشدار معطلی)
    @ColumnInfo(defaultValue = "0")
    val stageChangedAt: Long = 0,

    // مهلتِ تحویل به مشتری (۰ = مهلتی تعیین نشده)
    @ColumnInfo(defaultValue = "0")
    val dueDate: Long = 0,

    val createdAt: Long = nowMillis()
)
