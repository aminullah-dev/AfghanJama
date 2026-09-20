package com.afghanjama.ui.format

import com.afghanjama.util.withThousands


/** فرمت مبلغ با جداکننده هزارگان، ارقام فارسی و نشان افغانی. مثال: ۱۲٬۵۰۰ ؋ */
fun Long.afn(): String = "${this.withThousands()} ؋".toPersianDigits()

/**
 * همان مبلغ ولی بدونِ نشانِ ؋ — برای خانه‌های جدولِ فاکتور که واحد یک بار
 * در سرستون نوشته می‌شود. نشانِ تکراری در هر خانه ستون را پهن می‌کند و
 * روی کاغذِ کوچک جا نمی‌شود.
 */
fun Long.afnBare(): String = this.withThousands().toPersianDigits()
