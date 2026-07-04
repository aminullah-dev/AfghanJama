package com.afghanjama.ui.format

import java.util.Locale

/** فرمت مبلغ با جداکننده هزارگان + نشان افغانی. مثال: 12,500 ؋ */
fun Long.afn(): String = String.format(Locale.US, "%,d ؋", this)
