package com.afghanjama.desktop.data

import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
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

        /*
         * سه قاعدهٔ پولی که تا ۱.۱۳ روی «عکسِ کهنه» بنا شده بودند.
         *
         * هر سه با `Repo`ِ واقعی روی همین دیتابیسِ واقعی سنجیده می‌شوند، چون
         * خرابی‌شان در مرزِ میانِ دو نوشتن بود — جایی که آزمونِ واحد با
         * دیتابیسِ ساختگی نمی‌بیند.
         */
        val repo = Repo(db)
        val stock = db.finishedStockDao()

        check("invoice sale writes stock from the row as it is now, not from the snapshot") {
            runBlocking {
                stock.upsert(FinishedStock(name = "tx-shirt", size = "M", qty = 10, avgCost = 100, totalValue = 1_000, updatedAt = 1L))
                val snapshot = stock.find("tx-shirt", "M")!!
                need(repo.sellInvoice(listOf(Repo.SaleLine(snapshot, 3, 200)), ""), "first sale should pass")
                // همان عکسِ اول، که هنوز ۱۰ می‌گوید.
                need(repo.sellInvoice(listOf(Repo.SaleLine(snapshot, 1, 200)), ""), "second sale should pass")
                val left = stock.find("tx-shirt", "M")!!
                need(left.qty == 6, "10 - 3 - 1 should leave 6, got ${left.qty}")
                need(left.totalValue == 600L, "value should follow the stock, got ${left.totalValue}")
            }
        }

        fun order(code: String, title: String, status: OrderStatus, price: Long) = Order(
            orderCode = code, shortCode = code, designTitle = title, qty = 2,
            fabricType = "کتان", fabricColor = "سفید", size = "L", fabricUnit = "متر",
            fabricAmount = 4.0, fabricPrice = 0, workCost = 0,
            customerName = "tx-customer", customerPhone = "", agreedPrice = price, status = status.name,
        )

        check("delivering twice from the same stale order delivers once") {
            runBlocking {
                stock.upsert(FinishedStock(name = "tx-suit", size = "L", qty = 5, avgCost = 100, totalValue = 500, updatedAt = 1L))
                val o = order("TX-DELIVER", "tx-suit", OrderStatus.STORED, 1_000)
                db.orderDao().insert(o)
                val first = repo.deliverOrderToCustomer(o, qty = 2, unitPrice = 500, receivedNow = 1_000, applyPrepay = 0)
                need(first == null, "first delivery should pass, got: $first")
                // ضربهٔ دوم با همان نسخهٔ سفارش که هنوز «تحویل‌نشده» می‌گوید.
                val second = repo.deliverOrderToCustomer(o, qty = 2, unitPrice = 500, receivedNow = 1_000, applyPrepay = 0)
                need(second != null, "second delivery of the same order must be refused")
                val left = stock.find("tx-suit", "L")!!.qty
                need(left == 3, "stock should leave once: 5 - 2 = 3, got $left")
                val saved = db.orderDao().getById(o.id)!!
                need(saved.status == OrderStatus.SENT.name && saved.deliveredQty == 2, "order should be SENT with 2 delivered, got ${saved.status}/${saved.deliveredQty}")
            }
        }

        check("order edit: price change syncs the ledger; a stored order keeps its title") {
            runBlocking {
                val open = order("TX-EDIT", "tx-coat", OrderStatus.SEWING, 8_000)
                db.orderDao().insert(open)
                val err = repo.editOrderDetails(open.id, "tx-coat", "L", "tx-customer", "", 9_000, 0L)
                need(err == null, "editing an open order should pass, got: $err")
                val rows = db.ledgerDao().observeAllEntries().first().filter { it.refId == "TX-EDIT" }
                need(rows.any { it.refType == "SALE_ADJUST" && it.credit == 8_000L }, "old billing should be neutralised: $rows")
                need(rows.any { it.refType == "SALE_BILLING" && it.debit == 9_000L }, "new billing should be posted: $rows")
                need(db.orderDao().getById(open.id)!!.agreedPrice == 9_000L, "price should be saved")

                val stored = order("TX-STORED", "tx-vest", OrderStatus.STORED, 5_000)
                db.orderDao().insert(stored)
                val refused = repo.editOrderDetails(stored.id, "tx-vest-renamed", "L", "tx-customer", "", 5_000, 0L)
                need(refused != null, "renaming a stored order must be refused")
                need(db.orderDao().getById(stored.id)!!.designTitle == "tx-vest", "title must be unchanged")
                // قیمتِ سفارشِ رسیده به انبار عوض می‌شود، ولی دفتر نه.
                need(repo.editOrderDetails(stored.id, "tx-vest", "L", "tx-customer", "", 6_000, 0L) == null, "price edit should pass")
                val after = db.ledgerDao().observeAllEntries().first().filter { it.refId == "TX-STORED" }
                need(after.isEmpty(), "a stored order's edit must not post to the ledger: $after")
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
