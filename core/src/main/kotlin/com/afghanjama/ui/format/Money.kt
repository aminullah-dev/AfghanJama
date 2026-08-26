package com.afghanjama.ui.format

import java.util.Locale

/** فرمت مبلغ با جداکننده هزارگان، ارقام فارسی و نشان افغانی. مثال: ۱۲٬۵۰۰ ؋ */
fun Long.afn(): String = String.format(Locale.US, "%,d ؋", this).toPersianDigits()

/**
 * همان مبلغ ولی بدونِ نشانِ ؋ — برای خانه‌های جدولِ فاکتور که واحد یک بار
 * در سرستون نوشته می‌شود. نشانِ تکراری در هر خانه ستون را پهن می‌کند و
 * روی کاغذِ کوچک جا نمی‌شود.
 */
fun Long.afnBare(): String = String.format(Locale.US, "%,d", this).toPersianDigits()
