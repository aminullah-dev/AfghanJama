// app/src/main/java/com/afghanjama/data/CodeGen.kt
package com.afghanjama.data

import java.time.LocalDate
import kotlin.random.Random

object CodeGen {

    private fun year(): Int = LocalDate.now().year

    fun makeOrderCode(nextNumber: Int): String {
        val y = year()
        return "AJ-$y-" + nextNumber.toString().padStart(6, '0')
    }

    /** کد فاکتور خرید مواد خام (KH = خرید). */
    fun makePurchaseCode(): String {
        val y = year()
        val tail = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
        return "KH-$y-$tail"
    }

    fun makeShortCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // بدون O/0 و I/1
        fun pick(n: Int) = (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "${pick(3)}-${pick(3)}"
    }
}
