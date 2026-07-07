package com.afghanjama.ui.format

import java.util.Locale

/** فرمت مبلغ با جداکننده هزارگان، ارقام فارسی و نشان افغانی. مثال: ۱۲٬۵۰۰ ؋ */
fun Long.afn(): String = String.format(Locale.US, "%,d ؋", this).toPersianDigits()
