package com.afghanjama.data

/**
 * حقوقِ هوشمند — حضور و غیابی که اپ از قبل ثبت می‌کند را به حقوق وصل
 * می‌کند، **به شکلِ پیشنهاد نه کسرِ خودکار**.
 *
 * **شکافی که این پر می‌کند.** اپ ورود و خروجِ هر کارمند را می‌نویسد،
 * ولی حقوق یک عددِ ماهانهٔ ثابت است و هیچ ربطی به روزهای حاضر یا
 * ساعت‌های کار ندارد. کارمندی که چند روز غیبت کرده همان حقوقِ کامل را
 * بدهکار نشان داده می‌شود و کارفرما از حافظه‌اش کسر می‌کند.
 *
 * **چرا پیشنهاد و نه کسرِ خودکار.** در بسیاری از کارگاه‌ها حقوقِ ماهانه
 * ثابت است و غیبتِ یک روز کسر نمی‌شود؛ کسرِ خودکار عددِ غلط و گاه
 * توهین‌آمیز می‌سازد. مثلِ `PriceAdvisor` اینجا فقط چند عددِ **زدنی**
 * ساخته می‌شود؛ کارفرما می‌بیند و انتخاب می‌کند، و فیلدِ حقوق خودش پر
 * نمی‌شود.
 *
 * **چرا «روزهای حاضر» نه «غیبت».** اپ نمی‌داند کدام روزها تعطیلِ کارگاه
 * است (جمعه‌ها؟ رخصتی؟)، پس شمردنِ «غیبت» از تقویم حدس است. در عوض
 * روزهای **واقعاً حاضر** شمرده می‌شود — همان که در دفترِ حضور هست — و
 * پیشنهاد بر پایهٔ آن ساخته می‌شود. روزهای کاریِ ماه ([Config.workingDays])
 * را کارفرما می‌گذارد، چون فقط او می‌داند.
 *
 * بی دیتابیس و بی ساعت؛ زمانِ محلی از بیرون به شکلِ [dayOf] می‌آید تا
 * آزمون به منطقهٔ زمانی بسته نباشد.
 */
object PayrollSmart {

    /** روزهای کاریِ پیش‌فرضِ ماه — حدودِ یک ماهِ ۳۰ روزه منهای جمعه‌ها. */
    const val DEFAULT_WORKING_DAYS = 26

    private const val HOUR = 3_600_000L

    /** یک ثبتِ حضور در ماهِ جاری. [out] `null` یعنی هنوز داخل است. */
    data class Shift(val inAt: Long, val out: Long?)

    /**
     * @param monthlySalary حقوقِ ماهانهٔ توافقی.
     * @param workingDays روزهای کاریِ ماه — پایهٔ «حقوقِ هر روز».
     * @param shiftHours ساعتِ یک شیفتِ کامل — بیش از آن اضافه‌کاری است.
     */
    data class Config(val monthlySalary: Long, val workingDays: Int, val shiftHours: Int)

    data class Suggestion(
        /** روزهایی که دستِ‌کم یک ورود داشته‌اند. */
        val daysPresent: Int,
        val workingDays: Int,
        /** جمعِ ساعت‌های کارکرد از شیفت‌های بسته‌شده. */
        val workedHours: Long,
        /** جمعِ ساعت‌های بیش از شیفت، روز به روز. */
        val overtimeHours: Long,
        /** حقوقِ هر روز = حقوق ماهانه ÷ روزهای کاری. */
        val dailyRate: Long,
        /** دستمزدِ هر ساعت = حقوق ماهانه ÷ (روزهای کاری × ساعتِ شیفت). */
        val hourlyRate: Long,
        val monthlySalary: Long,
    ) {
        /** حقوقِ پیشنهادی بابتِ روزهای حاضر — هیچ‌وقت بیش از حقوقِ کامل. */
        val proratedSalary: Long
            get() = minOf(monthlySalary, dailyRate * daysPresent.coerceAtLeast(0))

        /** پیشنهادِ پرداختِ اضافه‌کاری. */
        val overtimePay: Long get() = hourlyRate * overtimeHours.coerceAtLeast(0)

        /** پیشنهادِ کل: حقوقِ روزهای حاضر + اضافه‌کاری. */
        val suggestedTotal: Long get() = proratedSalary + overtimePay

        /** چند روز کمتر از روزهای کاری حاضر بوده. */
        val daysShort: Int get() = (workingDays - daysPresent).coerceAtLeast(0)

        /** حقوقِ حاضر از حقوقِ کامل کمتر است. */
        val hasDeduction: Boolean get() = proratedSalary < monthlySalary

        /** اصلاً حضوری ثبت شده؟ بی آن هیچ پیشنهادی ساخته نمی‌شود. */
        val hasData: Boolean get() = daysPresent > 0
    }

    /**
     * @param shifts ثبت‌های حضورِ همین کارمند در همین ماه — صداکننده فیلتر می‌کند.
     * @param dayOf نیمه‌شبِ محلیِ یک زمان، برای شمردنِ روزهای جدا.
     */
    fun analyze(shifts: List<Shift>, cfg: Config, dayOf: (Long) -> Long): Suggestion {
        val days = cfg.workingDays.coerceAtLeast(1)
        val shiftMs = cfg.shiftHours.coerceAtLeast(1) * HOUR

        val daysPresent = shifts.map { dayOf(it.inAt) }.distinct().size

        // کارکرد و اضافه‌کاری فقط از شیفت‌های بسته؛ شیفتِ باز هنوز اندازه‌اش
        // معلوم نیست. اضافه‌کاری روز به روز حساب می‌شود، نه روی جمعِ ماه:
        // دو روزِ ۹ ساعته یعنی ۲ ساعت اضافه، نه اینکه با یک روزِ ۷ ساعته
        // خنثی شود.
        val byDay = shifts
            .filter { it.out != null && it.out >= it.inAt }
            .groupBy { dayOf(it.inAt) }
        var workedMs = 0L
        var overtimeMs = 0L
        for ((_, dayShifts) in byDay) {
            val dayMs = dayShifts.sumOf { (it.out ?: it.inAt) - it.inAt }
            workedMs += dayMs
            if (dayMs > shiftMs) overtimeMs += dayMs - shiftMs
        }

        return Suggestion(
            daysPresent = daysPresent,
            workingDays = days,
            workedHours = workedMs / HOUR,
            overtimeHours = overtimeMs / HOUR,
            dailyRate = cfg.monthlySalary / days,
            hourlyRate = cfg.monthlySalary / (days.toLong() * cfg.shiftHours.coerceAtLeast(1)),
            monthlySalary = cfg.monthlySalary,
        )
    }
}
