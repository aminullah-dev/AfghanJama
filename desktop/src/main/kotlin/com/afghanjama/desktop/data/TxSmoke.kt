package com.afghanjama.desktop.data

import com.afghanjama.data.entities.FabricType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files

/*
 * مرزِ تراکنش روی ویندوز واقعاً تراکنش است.
 *
 * **چرا این آزمون لازم بود.** `Tx.atomic` قرارداد می‌دهد که یا همه‌چیز
 * بماند یا هیچ‌چیز، و اینکه تودرتو بودن مجاز است. روی اندروید این را
 * `androidx.room.withTransaction` می‌دهد. روی ویندوز آن تابع **وجود
 * ندارد** — `room-ktx` فقط اندروید منتشر می‌شود — پس اینجا با موتورِ
 * درایوری دوباره ساخته شده.
 *
 * پیاده‌سازیِ دستیِ یک تراکنش چیزی نیست که با «کامپایل شد» ثابت شود.
 * یک `atomic` که هیچ‌وقت برنگرداند هم بی‌صدا کامپایل می‌شود، و
 * خرابی‌اش فقط وقتی دیده می‌شود که برقِ کارگاه وسطِ ثبتِ یک فاکتور
 * برود — یعنی دقیقاً همان روزی که هیچ‌کس نمی‌خواهد بفهمد دفتر
 * نیمه‌نوشته مانده.
 *
 * روی یک فایلِ موقتی اجرا می‌شود، نه دفترِ واقعی.
 */

private class Boom : RuntimeException("عمدی — برای آزمونِ برگشت")

private var failed = 0

private fun check(name: String, body: () -> Unit) {
    try {
        body()
        println("OK    $name")
    } catch (t: Throwable) {
        failed++
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
    }
}

private fun need(cond: Boolean, msg: String) {
    if (!cond) throw IllegalStateException(msg)
}

fun main() {
    println("transaction smoke - KhayatYar Windows")
    println("=".repeat(52))

    val dir = Files.createTempDirectory("khayatyar-tx").toFile()
    val db = openDatabase(File(dir, "tx.db"))

    try {
        val dao = db.masterDataDao()

        // فقط `observe…` هست، پس اولین انتشارِ همان Flow خوانده می‌شود.
        suspend fun names(): List<String> = dao.observeFabricTypes().first().map { it.title }

        check("commit: both rows survive a successful atomic") {
            runBlocking {
                db.atomic {
                    dao.insertFabricType(FabricType(title = "tx-a"))
                    dao.insertFabricType(FabricType(title = "tx-b"))
                }
                val n = names()
                need("tx-a" in n && "tx-b" in n, "both rows should be present, got $n")
            }
        }

        check("rollback: a throw mid-atomic leaves nothing behind") {
            runBlocking {
                val before = names().size
                try {
                    db.atomic {
                        dao.insertFabricType(FabricType(title = "tx-rollback-1"))
                        dao.insertFabricType(FabricType(title = "tx-rollback-2"))
                        throw Boom()
                    }
                    need(false, "the exception should have propagated")
                } catch (_: Boom) {
                    // انتظارِ همین است
                }
                val n = names()
                need(n.size == before, "row count should be unchanged, was $before now ${n.size}")
                need(n.none { it.startsWith("tx-rollback") }, "no partial row may survive: $n")
            }
        }

        check("nesting: an inner atomic merges into the outer one") {
            runBlocking {
                db.atomic {
                    dao.insertFabricType(FabricType(title = "tx-outer"))
                    db.atomic {
                        dao.insertFabricType(FabricType(title = "tx-inner"))
                    }
                }
                val n = names()
                need("tx-outer" in n && "tx-inner" in n, "both nested rows should commit, got $n")
            }
        }

        check("nesting: the outer rollback also undoes the inner write") {
            runBlocking {
                val before = names().size
                try {
                    db.atomic {
                        dao.insertFabricType(FabricType(title = "tx-n-outer"))
                        db.atomic {
                            dao.insertFabricType(FabricType(title = "tx-n-inner"))
                        }
                        throw Boom()
                    }
                    need(false, "the exception should have propagated")
                } catch (_: Boom) {
                }
                val n = names()
                need(n.size == before, "nothing may survive an outer rollback, was $before now ${n.size}")
                need(n.none { it.startsWith("tx-n-") }, "inner write must roll back too: $n")
            }
        }
    } finally {
        runCatching { db.closeConnection() }
        dir.deleteRecursively()
    }

    println("=".repeat(52))
    if (failed == 0) {
        println("transaction boundary verified")
        kotlin.system.exitProcess(0)
    }
    println("$failed transaction checks FAILED")
    kotlin.system.exitProcess(1)
}
