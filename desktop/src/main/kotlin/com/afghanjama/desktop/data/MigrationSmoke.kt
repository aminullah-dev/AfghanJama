package com.afghanjama.desktop.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.DESKTOP_BASELINE
import com.afghanjama.data.SchemaStep
import com.afghanjama.data.schemaChain
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files

/*
 * دودآزماییِ مهاجرتِ ویندوز.
 *
 * **چرا این فایل هست.** بررسیِ `migrationgap` فقط می‌گوید گامی *نوشته*
 * شده یا نه. نمی‌گوید آن گام سرِ اجرا *کار می‌کند* یا نه — و در این
 * پروژه فاصلهٔ بینِ این دو، هر بار که اندازه گرفته شد، خالی نبود.
 *
 * اینجا روی یک فایلِ موقتی و با همان موتوری که کارگاه استفاده می‌کند:
 *
 *   ۱. نسخهٔ فایل خوانده می‌شود (`ledgerFileVersion`).
 *   ۲. فایلِ قدیمی‌تر از پایه با پیامِ روشن رد می‌شود، نه با خطای خامِ
 *      Room و نه با پاک شدن.
 *   ۳. **Room واقعاً گامِ ما را اجرا می‌کند** — فایل دستی به نسخهٔ قبل
 *      برده می‌شود و با یک گامِ ساختگی باز می‌شود؛ اگر Room آن را صدا
 *      نزند، اثرش در فایل نیست و آزمون قرمز می‌شود.
 *   ۴. زنجیرهٔ پاره خطا می‌دهد.
 *
 * بندِ ۳ مهم‌ترین است: تنها جایی که ثابت می‌شود پوستهٔ
 * `desktopMigrations()` به‌درستی به Room وصل شده. بقیه را می‌شد با
 * چشم هم دید.
 */

private class SmokeFailure(msg: String) : RuntimeException(msg)

private fun check(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

private fun require(cond: Boolean, msg: String) {
    if (!cond) throw SmokeFailure(msg)
}

/** نوشتنِ مستقیمِ نسخه روی فایل — برای ساختنِ «فایلِ قدیمی». */
private fun forceVersion(file: File, version: Int) {
    val conn = BundledSQLiteDriver().open(file.absolutePath)
    try {
        conn.execSQL("PRAGMA user_version = $version")
    } finally {
        conn.close()
    }
}

private fun tableExists(file: File, table: String): Boolean {
    val conn = BundledSQLiteDriver().open(file.absolutePath)
    try {
        val st = conn.prepare(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'"
        )
        try {
            return st.step() && st.getLong(0) > 0
        } finally {
            st.close()
        }
    } finally {
        conn.close()
    }
}

fun main() {
    println("migration smoke - KhayatYar Windows")
    println("=".repeat(52))

    val dir = Files.createTempDirectory("khayatyar-migration").toFile()
    var ok = true

    // ۱ — دفترِ نو ساخته می‌شود و نسخه‌اش خوانده می‌شود.
    val fresh = File(dir, "fresh.db")
    ok = check("fresh ledger opens at DB_VERSION=$DB_VERSION") {
        val db = openDatabase(fresh)
        // Room تنبل است: بی یک پرس‌وجوی واقعی فایل ساخته هم نمی‌شود.
        // (اولین بار همین‌جا افتاد — `ledgerFileVersion` روی فایلِ
        // نساخته `null` داد.)
        db.tableNames()
        db.close()
        val v = ledgerFileVersion(fresh)
        require(v == DB_VERSION, "expected $DB_VERSION, got $v")
    } && ok

    // ۲ — فایلِ قدیمی‌تر از پایه: پیامِ روشن، بی دست خوردنِ داده.
    val old = File(dir, "old.db")
    fresh.copyTo(old, overwrite = true)
    // یکی زیرِ پایه — **از خودِ ثابت** حساب می‌شود نه دستی.
    //
    // دو بار همین‌جا شکست: اول ۵۵ نوشته شده بود و با آمدنِ گام‌های
    // تاریخی دیگر رد نمی‌شد، بعد ۳۰ و با پایین آمدنِ پایه به ۱۹ همان
    // اتفاق افتاد. هر بار «شکست» درست بود — فایل واقعاً بالا برده
    // می‌شد — ولی آزمون باید خودش را با پایه جلو ببرد نه اینکه دستی
    // به‌روز شود.
    val belowBaseline = DESKTOP_BASELINE - 1
    ok = check("pre-baseline file is refused with guidance, not wiped") {
        forceVersion(old, belowBaseline)
        val before = old.length()
        val err = runCatching { openDatabase(old) }.exceptionOrNull()
        require(err != null, "opening a v$belowBaseline file should have failed")
        val m = err!!.message.orEmpty()
        require(m.contains("$belowBaseline"), "message should name the file version: $m")
        require(m.contains("$DESKTOP_BASELINE"), "message should name the baseline: $m")
        require(old.length() == before, "the file was modified - data must not be touched")
        require(
            ledgerFileVersion(old) == belowBaseline,
            "version must stay $belowBaseline"
        )
    } && ok

    /*
     * گام‌های تاریخی واقعاً به Room داده می‌شوند.
     *
     * این جای آزمونِ end-to-endِ ۴۳→۶۱ را **نمی‌گیرد** و ادعایش را هم
     * ندارد: برای آن یک فایلِ واقعیِ نسخهٔ ۴۳ لازم است و چنین فایلی
     * اینجا نیست. (ساختنش با دست‌کاریِ `user_version` روی فایلِ ۶۱ جواب
     * نمی‌دهد — اسکیما از قبل امروزی است و اولین
     * `ALTER TABLE … ADD COLUMN` با «ستون تکراری» می‌ترکد.)
     *
     * آنچه اینجا ثابت می‌شود این است که زنجیره **کامل و وصل** است: برای
     * هر نسخه از پایه تا امروز یک `Migration` واقعی وجود دارد. درستیِ
     * خودِ SQL را `legacysql` تضمین می‌کند — با استخراجِ دوباره از
     * `Migrations.kt` و مقایسهٔ حرف‌به‌حرف.
     */
    ok = check("legacy chain $DESKTOP_BASELINE..$DB_VERSION is wired into Room") {
        val versions = desktopMigrations().map { it.startVersion to it.endVersion }.toMap()
        var v = DESKTOP_BASELINE
        while (v < DB_VERSION) {
            require(versions.containsKey(v), "no Migration registered for $v -> ${v + 1}")
            v = versions.getValue(v)
        }
        require(
            desktopMigrations().isNotEmpty(),
            "no migrations at all - LEGACY_STEPS is not reaching the adapter"
        )
    } && ok

    /*
     * ۳ — **Room گامِ ما را واقعاً اجرا می‌کند.**
     *
     * فایل دستی به `DB_VERSION - 1` برده می‌شود. اسکیمای درونش همان
     * اسکیمای امروز است، پس وارسیِ Room بعد از مهاجرت قبول می‌کند و
     * تنها چیزی که سنجیده می‌شود همان است که باید: آیا `migrate` صدا
     * زده شد.
     *
     * SQLِ گام یک جدولِ نشانه می‌سازد. اگر Room پوسته را صدا نزند، آن
     * جدول ساخته نمی‌شود و اینجا قرمز می‌شود.
     */
    val upgraded = File(dir, "upgrade.db")
    fresh.copyTo(upgraded, overwrite = true)
    ok = check("Room actually invokes a SchemaStep on version bump") {
        val from = DB_VERSION - 1
        forceVersion(upgraded, from)
        require(!tableExists(upgraded, "_migration_probe"), "probe table should not exist yet")

        val step = SchemaStep(
            from, DB_VERSION,
            listOf("CREATE TABLE IF NOT EXISTS `_migration_probe` (`x` INTEGER NOT NULL)")
        )
        val db = Room.databaseBuilder<DesktopDatabase>(name = upgraded.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(*desktopMigrations(listOf(step)))
            .build()
        // Room تنبل است: تا اولین پرس‌وجو فایل را باز نمی‌کند و مهاجرت
        // اجرا نمی‌شود. پس یک پرس‌وجوی واقعی لازم است.
        db.tableNames()
        db.close()

        require(
            tableExists(upgraded, "_migration_probe"),
            "Room did not run the migration - desktopMigrations() is not wired to the builder"
        )
        require(
            ledgerFileVersion(upgraded) == DB_VERSION,
            "version should be $DB_VERSION after upgrade, got ${ledgerFileVersion(upgraded)}"
        )
    } && ok

    /*
     * **آنچه اینجا آزموده نمی‌شود، و چرا — این را نخوانده رد نکنید.**
     *
     * یک بندِ «`openDatabase` فایلِ ۴۳ را به ۶۱ می‌برد» نوشته شد و
     * **برداشته شد**، چون ناسالم بود.
     *
     * برای ساختنِ فایلِ آزمایشی، تنها راهِ در دسترس این است که یک دفترِ
     * نو ساخته شود و `user_version`ش دستی پایین برده شود. ولی اسکیمای
     * آن فایل از قبل امروزی است، پس اولین
     * `ALTER TABLE … ADD COLUMN` با «ستون تکراری» می‌ترکد — و آزمون
     * قرمز می‌شود بی آنکه چیزی خراب باشد.
     *
     * بدتر: همین ایراد برای مهاجرت‌های **آینده** هم پیش می‌آید. هر
     * گامی که ستون اضافه کند روی فایلی که آن ستون را دارد شکست
     * می‌خورد. یعنی آن بند برای مهاجرت‌های واقعی هشدارِ دروغ می‌داد —
     * و آزمونی که دروغ بگوید، دفعهٔ بعد خاموش می‌شود.
     *
     * پس صادقانه: **زنجیرهٔ ۴۳→۶۱ روی یک فایلِ واقعیِ قدیمی اجرا
     * نشده.** برای آن یک دفترِ واقعیِ نسخهٔ ۴۳ لازم است، و اولین
     * انتقالِ پشتیبانِ گوشی به پی‌سی همان آزمون است.
     *
     * آنچه *هست*: SQL حرف‌به‌حرف همان است که اندروید اجرا می‌کند
     * (`legacysql`)، زنجیره پیوسته است (`migrationgap`)، همهٔ گام‌ها به
     * Room داده شده‌اند (بندِ بالا)، و خودِ سازوکار روی یک گامِ واقعی
     * اجرا شده (بندِ پایین).
     */

    /*
     * دو مهاجرتِ دستی، روی دادهٔ ساختگیِ معلوم.
     *
     * **اینها تنها جایی‌اند که منطق دو بار نوشته شده** (کرسر دارند و
     * SQLِ خالص نمی‌شوند)، پس تنها جایی‌اند که کپی‌برداریِ متن کافی
     * نیست و باید *رفتار* سنجیده شود.
     *
     * برخلافِ زنجیرهٔ کامل، اینجا آزمون شدنی است: این دو فقط به چند
     * جدولِ مشخص دست می‌زنند، پس همان‌ها ساخته و با عددهای معلوم پر
     * می‌شوند و نتیجه با حسابِ دستی سنجیده می‌شود.
     */
    ok = check("hand-written 41->42 moves SALES orders into finished stock") {
        val f = File(dir, "h4142.db")
        val conn = BundledSQLiteDriver().open(f.absolutePath)
        try {
            conn.execSQL(
                "CREATE TABLE orders (id TEXT, orderCode TEXT, designTitle TEXT, " +
                    "size TEXT, qty INTEGER, fabricPrice INTEGER, workCost INTEGER, " +
                    "sewingCost INTEGER, agreedPrice INTEGER, customerName TEXT, " +
                    "status TEXT, stageChangedAt INTEGER)"
            )
            conn.execSQL(
                "CREATE TABLE finished_stock (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT, size TEXT, qty INTEGER, avgCost INTEGER, updatedAt INTEGER)"
            )
            conn.execSQL(
                "CREATE TABLE ledger_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "partyType TEXT, partyName TEXT, debit INTEGER, credit INTEGER, " +
                    "refType TEXT, refId TEXT, note TEXT, at INTEGER)"
            )
            // موجودیِ قبلی: ۲ عدد با بهای ۱۰۰
            conn.execSQL(
                "INSERT INTO finished_stock (name, size, qty, avgCost, updatedAt) " +
                    "VALUES ('Shirt', 'M', 2, 100, 0)"
            )
            // سفارشِ ۱: ۲ عدد، بهای کل ۴۰۰ ⇒ هر عدد ۲۰۰ ⇒ میانگینِ تازه
            //   (۲×۱۰۰ + ۲×۲۰۰) ÷ ۴ = ۱۵۰
            conn.execSQL(
                "INSERT INTO orders VALUES ('o1','C1','Shirt','M',2,200,100,100,500," +
                    "'علی','SALES',0)"
            )
            // سفارشِ ۲: نامِ تازه ⇒ ردیفِ تازه، بی مشتری ⇒ بی سطرِ دفتر
            conn.execSQL(
                "INSERT INTO orders VALUES ('o2','C2','Coat','L',1,300,0,0,0,'','SALES',0)"
            )

            HAND_WRITTEN.first { it.startVersion == 41 }.migrate(conn)

            fun q(sql: String): Long {
                val st = conn.prepare(sql)
                try {
                    return if (st.step()) st.getLong(0) else -1L
                } finally {
                    st.close()
                }
            }

            require(
                q("SELECT qty FROM finished_stock WHERE name='Shirt' AND size='M'") == 4L,
                "Shirt qty should be 4"
            )
            require(
                q("SELECT avgCost FROM finished_stock WHERE name='Shirt' AND size='M'") == 150L,
                "weighted average should be 150, got " +
                    q("SELECT avgCost FROM finished_stock WHERE name='Shirt' AND size='M'")
            )
            require(
                q("SELECT qty FROM finished_stock WHERE name='Coat'") == 1L,
                "Coat should have been inserted"
            )
            require(
                q("SELECT avgCost FROM finished_stock WHERE name='Coat'") == 300L,
                "Coat unit cost should be 300"
            )
            require(
                q("SELECT COUNT(*) FROM orders WHERE status='STORED'") == 2L,
                "both orders should be STORED"
            )
            require(
                q("SELECT COUNT(*) FROM ledger_entries") == 1L,
                "only the order with a customer and a price posts to the ledger"
            )
            require(
                q("SELECT credit FROM ledger_entries WHERE partyName='علی'") == 500L,
                "customer should be credited the agreed price"
            )
        } finally {
            conn.close()
        }
    } && ok

    ok = check("hand-written 42->43 opens a balanced journal") {
        val f = File(dir, "h4243.db")
        val conn = BundledSQLiteDriver().open(f.absolutePath)
        try {
            conn.execSQL("CREATE TABLE finance_transactions (type TEXT, amount INTEGER, source TEXT)")
            conn.execSQL("CREATE TABLE material_stock (amount REAL, avgPrice REAL)")
            conn.execSQL("CREATE TABLE finished_stock (qty INTEGER, avgCost INTEGER)")
            conn.execSQL(
                "CREATE TABLE ledger_entries (partyType TEXT, partyName TEXT, " +
                    "debit INTEGER, credit INTEGER)"
            )
            conn.execSQL("CREATE TABLE tailor_wages (amount INTEGER, settled INTEGER)")

            // صندوق ۱۰۰۰−۲۰۰=۸۰۰ ، بانک ۵۰۰ ، مواد ۲×۵۰=۱۰۰ ، محصول ۴×۱۵۰=۶۰۰
            conn.execSQL("INSERT INTO finance_transactions VALUES ('IN',1000,'WALLET')")
            conn.execSQL("INSERT INTO finance_transactions VALUES ('OUT',200,'WALLET')")
            conn.execSQL("INSERT INTO finance_transactions VALUES ('IN',500,'BANK')")
            conn.execSQL("INSERT INTO material_stock VALUES (2.0, 50.0)")
            conn.execSQL("INSERT INTO finished_stock VALUES (4, 150)")
            // دریافتنی ۳۰۰ ، پرداختنیِ فروشنده ۲۰۰
            conn.execSQL("INSERT INTO ledger_entries VALUES ('CUSTOMER','علی',300,0)")
            conn.execSQL("INSERT INTO ledger_entries VALUES ('SUPPLIER','سلیم',0,200)")
            conn.execSQL("INSERT INTO tailor_wages VALUES (100, 0)")

            HAND_WRITTEN.first { it.startVersion == 42 }.migrate(conn)

            fun q(sql: String): Long {
                val st = conn.prepare(sql)
                try {
                    return if (st.step()) st.getLong(0) else -1L
                } finally {
                    st.close()
                }
            }

            require(q("SELECT COUNT(*) FROM journal_entries") == 1L, "one opening entry")
            require(
                q("SELECT COUNT(*) FROM journal_entries WHERE refType='OPENING'") == 1L,
                "entry must be marked OPENING"
            )

            // دارایی ۸۰۰+۵۰۰+۱۰۰+۶۰۰+۳۰۰ = ۲۳۰۰ ، بدهی ۲۰۰+۱۰۰ = ۳۰۰
            require(q("SELECT debit FROM journal_lines WHERE account='1010'") == 800L, "wallet 800")
            require(q("SELECT debit FROM journal_lines WHERE account='1020'") == 500L, "bank 500")
            require(q("SELECT debit FROM journal_lines WHERE account='1040'") == 100L, "materials 100")
            require(q("SELECT debit FROM journal_lines WHERE account='1050'") == 600L, "finished 600")
            require(q("SELECT debit FROM journal_lines WHERE account='1030'") == 300L, "receivable 300")
            require(q("SELECT credit FROM journal_lines WHERE account='2010'") == 200L, "supplier 200")
            require(q("SELECT credit FROM journal_lines WHERE account='2020'") == 100L, "wages 100")
            require(q("SELECT credit FROM journal_lines WHERE account='3010'") == 2000L, "equity 2000")

            // صفرِ خالی نباید سطر بسازد.
            require(
                q("SELECT COUNT(*) FROM journal_lines WHERE account='1015'") == 0L,
                "a zero balance must not create a line"
            )

            // **قاعدهٔ حسابداری:** بدهکار و بستانکار باید برابر باشند.
            val d = q("SELECT COALESCE(SUM(debit),0) FROM journal_lines")
            val c = q("SELECT COALESCE(SUM(credit),0) FROM journal_lines")
            require(d == c, "journal must balance: debit=$d credit=$c")
            require(d == 2300L, "total should be 2300, got $d")
        } finally {
            conn.close()
        }
    } && ok

    ok = check("hand-written 42->43 writes nothing when the ledger is empty") {
        val f = File(dir, "h4243empty.db")
        val conn = BundledSQLiteDriver().open(f.absolutePath)
        try {
            conn.execSQL("CREATE TABLE finance_transactions (type TEXT, amount INTEGER, source TEXT)")
            conn.execSQL("CREATE TABLE material_stock (amount REAL, avgPrice REAL)")
            conn.execSQL("CREATE TABLE finished_stock (qty INTEGER, avgCost INTEGER)")
            conn.execSQL(
                "CREATE TABLE ledger_entries (partyType TEXT, partyName TEXT, " +
                    "debit INTEGER, credit INTEGER)"
            )
            conn.execSQL("CREATE TABLE tailor_wages (amount INTEGER, settled INTEGER)")

            HAND_WRITTEN.first { it.startVersion == 42 }.migrate(conn)

            val st = conn.prepare("SELECT COUNT(*) FROM journal_entries")
            try {
                require(st.step() && st.getLong(0) == 0L, "no opening entry for an empty ledger")
            } finally {
                st.close()
            }
        } finally {
            conn.close()
        }
    } && ok

    // ۵ — زنجیرهٔ پاره باید بلند خطا بدهد، نه بی‌صدا رد شود.
    ok = check("a broken chain is rejected loudly") {
        // بالاتر از `DB_VERSION` هیچ گامی نیست، پس اینجا حتماً پاره است.
        // (پیش‌تر `DESKTOP_BASELINE + 3` بود؛ با آمدنِ گام‌های تاریخی آن
        // بازه دیگر پاره نیست و آزمون بی‌صدا بی‌معنی می‌شد.)
        val err = runCatching { schemaChain(DB_VERSION, DB_VERSION + 2) }
            .exceptionOrNull()
        require(err != null, "a gap should have thrown")
    } && ok

    dir.deleteRecursively()

    println("=".repeat(52))
    println(if (ok) "migration path verified" else "migration smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
