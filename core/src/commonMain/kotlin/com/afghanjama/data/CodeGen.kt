// app/src/main/java/com/afghanjama/data/CodeGen.kt
package com.afghanjama.data

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.afghanjama.util.nowMillis
import kotlin.random.Random

object CodeGen {

    private fun year(): Int =
        Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .year

    fun makeOrderCode(nextNumber: Int): String {
        val y = year()
        return "AJ-$y-" + nextNumber.toString().padStart(6, '0')
    }

    /** کد فاکتور خرید مواد خام (KH = خرید). */
    fun makePurchaseCode(): String {
        val y = year()
        val tail = (nowMillis() % 1_000_000L).toString().padStart(6, '0')
        return "KH-$y-$tail"
    }

    fun makeShortCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // بدون O/0 و I/1
        fun pick(n: Int) = (1..n).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "${pick(3)}-${pick(3)}"
    }
}
