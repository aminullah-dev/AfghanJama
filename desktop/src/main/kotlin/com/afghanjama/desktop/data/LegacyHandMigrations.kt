package com.afghanjama.desktop.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL

/*
 * دو مهاجرتی که SQLِ خالص نیستند — دستی، برای ویندوز.
 *
 * **این تنها جای پروژه است که یک منطق دو بار نوشته شده، و عمدی است.**
 * چهل مهاجرتِ دیگر SQLشان از `Migrations.kt` استخراج می‌شود و یک تعریف
 * بیشتر ندارند. این دو نمی‌توانند: با کرسر ردیف می‌خوانند، حساب
 * می‌کنند و بر اساسِ نتیجه می‌نویسند. چنین چیزی رشتهٔ SQL نیست.
 *
 * **چرا با این حال نوشته شدند:** بی این دو، پایینی‌ترین نسخه‌ای که
 * ویندوز می‌توانست باز کند ۴۳ بود. یعنی دفترِ هر گوشی‌ای که از ۴۲ عقب‌تر
 * است روی پی‌سی باز نمی‌شد. حالا پایه ۱۹ است — همان‌جا که اندروید هم
 * شروع می‌کند.
 *
 * **قاعده‌ای که با آن باید زندگی کرد:** اگر روزی کسی `MIGRATION_41_42`
 * یا `MIGRATION_42_43` را در `Migrations.kt` دست بزند، این فایل هم باید
 * دست بخورد. هیچ نگهبانی نمی‌تواند این را بگیرد (منطق است، نه متن) —
 * برای همین بالای هر دو، در هر دو فایل، نوشته شده.
 *
 * **یک تفاوتِ عمدی با اندروید:** آنجا کرسر پنجره‌ای است و می‌شود حینِ
 * پیمایش روی همان جدول نوشت. اینجا `SQLiteStatement` جریانی است و
 * نوشتن روی جدولی که در حالِ خواندنش هستی رفتارِ تعریف‌نشده می‌دهد. پس
 * اول همهٔ ردیف‌ها خوانده و در حافظه ریخته می‌شوند، بعد نوشتن. نتیجه
 * یکی است؛ راهش امن‌تر.
 *
 * شماره‌ٔ پارامترها از **۱** شروع می‌شود و ستون‌ها از **۰** — از کدِ
 * تولیدشدهٔ خودِ Room وارسی شد، نه از حافظه.
 */

private fun SQLiteConnection.scalarLong(sql: String, vararg args: String): Long {
    val st = prepare(sql)
    try {
        args.forEachIndexed { i, v -> st.bindText(i + 1, v) }
        return if (st.step()) st.getLong(0) else 0L
    } finally {
        st.close()
    }
}

private fun <T> SQLiteConnection.rows(sql: String, read: (SQLiteStatement) -> T): List<T> {
    val st = prepare(sql)
    try {
        val out = mutableListOf<T>()
        while (st.step()) out += read(st)
        return out
    } finally {
        st.close()
    }
}

private fun SQLiteStatement.textOr(i: Int, fallback: String = ""): String =
    if (isNull(i)) fallback else getText(i)

/**
 * ۴۱ → ۴۲ — سفارش‌های فروخته‌شده به انبارِ محصول می‌روند.
 *
 * برابرِ `MIGRATION_41_42`ِ اندروید. هر سفارشِ `SALES` به `STORED`
 * می‌رود، بهای تمام‌شده‌اش (پارچه + کار + دوخت) تقسیم بر تعداد به
 * `finished_stock` می‌نشیند — با میانگینِ وزنی اگر همان نام و سایز از
 * قبل باشد — و اگر مشتری و مبلغ داشت، یک بستانکاری در دفترِ طرف.
 */
private val HAND_41_42 = object : Migration(41, 42) {
    override fun migrate(connection: SQLiteConnection) {
        val now = System.currentTimeMillis()

        data class Row(
            val id: String, val code: String, val title: String, val size: String,
            val qty: Int, val cost: Long, val agreed: Long, val customer: String
        )

        val orders = connection.rows(
            "SELECT id, orderCode, designTitle, size, qty, fabricPrice, workCost, " +
                "sewingCost, agreedPrice, customerName FROM orders WHERE status = 'SALES'"
        ) { s ->
            Row(
                id = s.textOr(0),
                code = s.textOr(1),
                title = s.textOr(2),
                size = s.textOr(3),
                qty = s.getInt(4),
                cost = s.getLong(5) + s.getLong(6) + s.getLong(7),
                agreed = s.getLong(8),
                customer = s.textOr(9)
            )
        }

        for (o in orders) {
            if (o.qty > 0) {
                val per = o.cost / o.qty

                // موجودیِ هم‌نام و هم‌سایز، اگر باشد.
                val find = connection.prepare(
                    "SELECT id, qty, avgCost FROM finished_stock WHERE name = ? AND size = ?"
                )
                var fid = -1L
                var fQty = 0
                var fAvg = 0L
                try {
                    find.bindText(1, o.title)
                    find.bindText(2, o.size)
                    if (find.step()) {
                        fid = find.getLong(0)
                        fQty = find.getInt(1)
                        fAvg = find.getLong(2)
                    }
                } finally {
                    find.close()
                }

                if (fid >= 0) {
                    val newQty = fQty + o.qty
                    val newAvg =
                        if (newQty > 0) (fQty * fAvg + o.qty * per) / newQty else 0L
                    val up = connection.prepare(
                        "UPDATE finished_stock SET qty = ?, avgCost = ?, updatedAt = ? WHERE id = ?"
                    )
                    try {
                        up.bindLong(1, newQty.toLong())
                        up.bindLong(2, newAvg)
                        up.bindLong(3, now)
                        up.bindLong(4, fid)
                        up.step()
                    } finally {
                        up.close()
                    }
                } else {
                    val ins = connection.prepare(
                        "INSERT INTO finished_stock (name, size, qty, avgCost, updatedAt) " +
                            "VALUES (?, ?, ?, ?, ?)"
                    )
                    try {
                        ins.bindText(1, o.title)
                        ins.bindText(2, o.size)
                        ins.bindLong(3, o.qty.toLong())
                        ins.bindLong(4, per)
                        ins.bindLong(5, now)
                        ins.step()
                    } finally {
                        ins.close()
                    }
                }
            }

            val upd = connection.prepare(
                "UPDATE orders SET status = 'STORED', stageChangedAt = ? WHERE id = ?"
            )
            try {
                upd.bindLong(1, now)
                upd.bindText(2, o.id)
                upd.step()
            } finally {
                upd.close()
            }

            if (o.customer.isNotBlank() && o.agreed > 0) {
                val led = connection.prepare(
                    "INSERT INTO ledger_entries (partyType, partyName, debit, credit, " +
                        "refType, refId, note, at) " +
                        "VALUES ('CUSTOMER', ?, 0, ?, 'SALE_TO_STOCK', ?, " +
                        "'انتقال به انبار محصول', ?)"
                )
                try {
                    led.bindText(1, o.customer)
                    led.bindLong(2, o.agreed)
                    led.bindText(3, o.code)
                    led.bindLong(4, now)
                    led.step()
                } finally {
                    led.close()
                }
            }
        }
    }
}

/**
 * ۴۲ → ۴۳ — دفترِ روزنامه و سندِ افتتاحیه.
 *
 * برابرِ `MIGRATION_42_43`ِ اندروید. جدول‌های `journal_entries` و
 * `journal_lines` ساخته می‌شوند و مانده‌های موجود (صندوق، بانک، سودِ
 * نگه‌داشته، انبارِ مواد، انبارِ محصول، دریافتنی، پرداختنی، کارمزدِ
 * نداده) در یک سندِ افتتاحیه می‌نشینند.
 *
 * اگر هیچ مانده‌ای نباشد هیچ سندی ساخته نمی‌شود — همان رفتارِ اندروید.
 */
private val HAND_42_43 = object : Migration(42, 43) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_entries` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`memo` TEXT NOT NULL, `refType` TEXT NOT NULL, " +
                "`refId` TEXT NOT NULL, `at` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `journal_lines` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, `account` TEXT NOT NULL, " +
                "`debit` INTEGER NOT NULL, `credit` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_journal_lines_entryId` " +
                "ON `journal_lines` (`entryId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_journal_lines_account` " +
                "ON `journal_lines` (`account`)"
        )

        fun box(source: String): Long = connection.scalarLong(
            "SELECT COALESCE(SUM(CASE WHEN type='IN' THEN amount ELSE -amount END),0) " +
                "FROM finance_transactions WHERE source = ?",
            source
        )

        val wallet = box("WALLET")
        val bank = box("BANK")
        val profitBox = box("PROFIT")
        val materials = connection.scalarLong(
            "SELECT COALESCE(CAST(SUM(amount * avgPrice) AS INTEGER),0) FROM material_stock"
        )
        val finished = connection.scalarLong(
            "SELECT COALESCE(SUM(qty * avgCost),0) FROM finished_stock"
        )

        var receivable = 0L
        var supplierPayable = 0L
        connection.rows(
            "SELECT partyType, SUM(debit) - SUM(credit) AS net FROM ledger_entries " +
                "GROUP BY partyType, partyName"
        ) { s -> s.textOr(0) to s.getLong(1) }
            .forEach { (type, net) ->
                if (type == "CUSTOMER" && net > 0) receivable += net
                if (type == "SUPPLIER" && net < 0) supplierPayable += -net
            }

        val wagesPayable = connection.scalarLong(
            "SELECT COALESCE(SUM(amount),0) FROM tailor_wages WHERE settled = 0"
        )

        val assets = wallet + bank + profitBox + materials + finished + receivable
        val liabilities = supplierPayable + wagesPayable
        if (assets == 0L && liabilities == 0L) return
        val equity = assets - liabilities

        val now = System.currentTimeMillis()
        val head = connection.prepare(
            "INSERT INTO journal_entries (memo, refType, refId, at) " +
                "VALUES ('سند افتتاحیه — انتقال مانده‌های موجود', 'OPENING', '', ?)"
        )
        try {
            head.bindLong(1, now)
            head.step()
        } finally {
            head.close()
        }
        val entryId = connection.scalarLong("SELECT last_insert_rowid()")

        fun line(account: String, debit: Long, credit: Long) {
            if (debit == 0L && credit == 0L) return
            val st = connection.prepare(
                "INSERT INTO journal_lines (entryId, account, debit, credit) " +
                    "VALUES (?, ?, ?, ?)"
            )
            try {
                st.bindLong(1, entryId)
                st.bindText(2, account)
                st.bindLong(3, debit)
                st.bindLong(4, credit)
                st.step()
            } finally {
                st.close()
            }
        }

        fun asset(account: String, v: Long) =
            if (v >= 0) line(account, v, 0) else line(account, 0, -v)

        asset("1010", wallet)
        asset("1020", bank)
        asset("1015", profitBox)
        asset("1040", materials)
        asset("1050", finished)
        asset("1030", receivable)
        line("2010", 0, supplierPayable)
        line("2020", 0, wagesPayable)
        if (equity >= 0) line("3010", 0, equity) else line("3010", -equity, 0)
    }
}

/** دو مهاجرتِ دستی — کنارِ گام‌های استخراج‌شده می‌نشینند. */
internal val HAND_WRITTEN: List<Migration> = listOf(HAND_41_42, HAND_42_43)
