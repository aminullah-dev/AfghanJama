package com.afghanjama.desktop.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.afghanjama.data.SCHEMA_STEPS
import com.afghanjama.data.SampleWorkshop
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * دفترِ ۶۶ → ۶۷ («کارگاهِ وصل») — روی **کپیِ یک دفترِ پُر**، نه روی دفترِ خالی.
 *
 * دفترِ ۶۶ دقیقاً همان‌طور ساخته می‌شود که برنامهٔ ۱.۱۰ می‌ساخت: از
 * `66.json`ِ ثبت‌شده (همان SQLِ Room و همان مهرِ هویت). دادهٔ ساختگی ولی
 * واقعی‌نما از «کارگاهِ نمونه» می‌آید، به‌اضافهٔ درخواست‌های گوشیِ قراردادِ ۱
 * و حضور. بعد از آن فایل یک **کپی** گرفته می‌شود و فقط کپی با برنامهٔ امروز
 * باز می‌شود؛ اصلش باید ۶۶ بماند.
 *
 * و چون اندروید `fallbackToDestructiveMigration` دارد (نقدِ ۱۷) — یعنی اگر
 * طرحِ پس از گام با انتظارِ Room نخوانَد، دفترِ گوشی **پاک** می‌شود — همان
 * گام روی طرحِ ۶۶ِ هر سه دیتابیس (اندروید، ویندوز، آیفون) اجرا و نتیجه با
 * `67.json`ِ خودشان مقایسه می‌شود.
 */

private fun repoRoot(): File {
    var d: File? = File("").absoluteFile
    while (d != null) {
        if (File(d, "gradle.properties").exists() && File(d, "core").isDirectory) return d
        d = d.parentFile
    }
    error("ریشهٔ مخزن پیدا نشد")
}

/** جای نامِ جدول در SQLِ طرحِ Room. */
private val TABLE_NAME_SLOT = "$" + "{TABLE_NAME}"

private fun schemaJson(module: String, db: String, version: Int): JSONObject =
    JSONObject(File(repoRoot(), "$module/schemas/$db/$version.json").readText(Charsets.UTF_8)).getJSONObject("database")

/** فایلی دقیقاً با طرحِ [schema] (همان SQL که Room نوشته). */
private fun createFrom(schema: JSONObject, file: File) {
    val c = BundledSQLiteDriver().open(file.absolutePath)
    try {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val t = e.getString("tableName")
            c.execSQL(e.getString("createSql").replace(TABLE_NAME_SLOT, t))
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) c.execSQL(idx.getJSONObject(j).getString("createSql").replace(TABLE_NAME_SLOT, t))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) c.execSQL(setup.getString(i))
        c.execSQL("PRAGMA user_version = ${schema.getInt("version")}")
    } finally {
        c.close()
    }
}

private fun <T> withConn(file: File, body: (SQLiteConnection) -> T): T {
    val c = BundledSQLiteDriver().open(file.absolutePath)
    try {
        return body(c)
    } finally {
        c.close()
    }
}

private fun SQLiteConnection.rows(sql: String): List<List<String?>> {
    val st = prepare(sql)
    try {
        val out = mutableListOf<List<String?>>()
        while (st.step()) out += (0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) }
        return out
    } finally {
        st.close()
    }
}

private fun SQLiteConnection.long(sql: String): Long = rows(sql).single().single()!!.toLong()

private fun tables(c: SQLiteConnection): List<String> =
    c.rows("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name <> 'room_master_table' ORDER BY name")
        .map { it[0]!! }

/**
 * طرح به همان شکلی که Room می‌سنجد: ستون‌ها با نام (ترتیب مهم نیست)، نوع،
 * تهی‌پذیری، پیش‌فرض و کلید؛ و ایندکس‌ها با نام، یکتایی و ستون‌ها.
 */
private fun shape(c: SQLiteConnection): Map<String, String> = tables(c).associateWith { t ->
    val cols = c.rows("PRAGMA table_info(`$t`)").map { r -> "${r[1]}:${r[2]?.uppercase()}:nn=${r[3]}:d=${r[4]}:pk=${r[5]}" }.sorted()
    val idx = c.rows("PRAGMA index_list(`$t`)").filter { it[1]?.startsWith("sqlite_autoindex") == false }.map { r ->
        val on = c.rows("PRAGMA index_info(`${r[1]}`)").map { it[2] }
        "${r[1]}:u=${r[2]}:$on"
    }.sorted()
    (cols + idx).joinToString("\n")
}

fun runHubMigration(dir: File, check: (String, () -> Unit) -> Boolean): Boolean {
    var ok = true
    val step = SCHEMA_STEPS.first { it.from == 66 && it.to == 67 }

    ok = check("66->67 on a copy of a realistic ledger keeps every row and opens as Room expects") {
        // ۱ — دادهٔ واقعی‌نما در یک دفترِ امروزی…
        val seedFile = File(dir, "seed67.db")
        val seed = openDatabase(seedFile)
        runBlocking {
            val repo = Repo(seed)
            SampleWorkshop.create(repo)
            val now = System.currentTimeMillis()
            seed.syncRequestDao().insert(SyncRequest(deviceName = "گوشیِ احمد", worker = "[T1] احمد", type = "ATTENDANCE_IN", summary = "درخواستِ ثبتِ ورود", createdAt = now - 3_600_000))
            seed.syncRequestDao().insert(SyncRequest(deviceName = "گوشیِ احمد", worker = "[T1] احمد", type = "NOTE", summary = "پیام", note = "آستین‌ها دوباره دوخته شد.", status = "APPROVED", decidedAt = now))
            seed.attendanceDao().insert(AttendanceRecord(employee = "احمد", checkIn = now - 9 * 3_600_000, checkOut = now - 3_600_000))
        }
        seed.closeConnection()

        // ۲ — …در فایلی با طرحِ دقیقِ ۶۶.
        val old66 = File(dir, "realistic66.db")
        val schema66 = schemaJson("desktop", "com.afghanjama.desktop.data.DesktopDatabase", 66)
        createFrom(schema66, old66)
        val expected = withConn(old66) { c ->
            c.prepare("ATTACH DATABASE ? AS seed").also { st ->
                try {
                    st.bindText(1, seedFile.absolutePath)
                    st.step()
                } finally {
                    st.close()
                }
            }
            val ents = schema66.getJSONArray("entities")
            for (i in 0 until ents.length()) {
                val e = ents.getJSONObject(i)
                val t = e.getString("tableName")
                val f = e.getJSONArray("fields")
                val cols = (0 until f.length()).joinToString(", ") { "`" + f.getJSONObject(it).getString("columnName") + "`" }
                c.execSQL("INSERT INTO main.`$t` ($cols) SELECT $cols FROM seed.`$t`")
            }
            c.execSQL("DETACH DATABASE seed")
            tables(c).associateWith { c.long("SELECT COUNT(*) FROM `$it`") }
        }
        smokeNeed(expected.values.sum() > 50, "the realistic ledger is too thin: $expected")
        smokeNeed((expected["orders"] ?: 0) > 0 && (expected["sync_requests"] ?: 0) == 2L, "seed $expected")

        // ۳ — کپی، و فقط کپی با برنامهٔ امروز.
        val copy = File(dir, "realistic66-copy.db")
        Files.copy(old66.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
        val migrated = openDatabase(copy)
        runBlocking {
            val repo = Repo(migrated)
            val pending = repo.observePendingRequests().first()
            smokeNeed(pending.size == 1 && pending[0].protocol == 1 && pending[0].clientKey == null, "pending after migration $pending")
            // درخواستِ قراردادِ ۱ پس از مهاجرت مثلِ قبل تأیید می‌شود — با ساعتِ رسیدنش.
            smokeNeed(repo.approveRequest(pending[0].id) == null, "a v1 request could not be approved after the migration")
            smokeNeed(migrated.attendanceDao().findOpen("احمد")?.checkIn == pending[0].createdAt, "v1 time changed meaning")
            smokeNeed(migrated.lanDao().observeDevices().first().isEmpty() && migrated.lanDao().hub() == null, "new tables not empty")
        }
        migrated.closeConnection()

        withConn(copy) { c ->
            smokeNeed(c.long("PRAGMA user_version") == 67L, "copy at version ${c.long("PRAGMA user_version")}")
            smokeNeed(c.rows("PRAGMA integrity_check").single().single() == "ok", "integrity_check failed")
            smokeNeed(c.rows("PRAGMA foreign_key_check").isEmpty(), "foreign keys broken")
            for ((t, n) in expected) {
                // تأییدِ بالا یک ردیفِ حضور و یک حسابرسی افزود؛ بقیه دقیقاً همان.
                val now = c.long("SELECT COUNT(*) FROM `$t`")
                val extra = when (t) { "audit_log" -> 1L; else -> 0L }
                smokeNeed(now == n + extra || (t == "attendance" && now == n + 1), "$t: $n rows before, $now after")
            }
            smokeNeed(c.long("SELECT COUNT(*) FROM sync_requests WHERE protocol <> 1 OR clientKey IS NOT NULL") == 0L, "old rows changed")
            val idx = c.rows("PRAGMA index_list(`sync_requests`)").firstOrNull { it[1] == "index_sync_requests_clientKey" }
            smokeNeed(idx != null && idx[2] == "1", "the clientKey index is missing or not unique: $idx")
            // یکتایی واقعاً کار می‌کند؛ NULLهای قدیمی با هم برخورد نمی‌کنند.
            c.execSQL("INSERT INTO sync_requests (deviceName, worker, type, summary, refId, amount, note, status, createdAt, decidedNote, clientKey, protocol) VALUES ('x','y','NOTE','s',0,0,'','PENDING',1,'','k-1',2)")
            val dup = runCatching {
                c.execSQL("INSERT INTO sync_requests (deviceName, worker, type, summary, refId, amount, note, status, createdAt, decidedNote, clientKey, protocol) VALUES ('x','y','NOTE','s',0,0,'','PENDING',1,'','k-1',2)")
            }
            smokeNeed(dup.isFailure, "a duplicate clientKey was accepted")
        }
        withConn(old66) { c -> smokeNeed(c.long("PRAGMA user_version") == 66L, "the original was touched") }
    } && ok

    for ((module, db) in listOf(
        "app" to "com.afghanjama.data.AppDatabase",
        "desktop" to "com.afghanjama.desktop.data.DesktopDatabase",
        "core" to "com.afghanjama.ios.data.IosDatabase",
    )) {
        ok = check("the 66->67 step gives exactly the 67 schema Room expects on $module") {
            val a = File(dir, "$module-66-stepped.db")
            val b = File(dir, "$module-67-fresh.db")
            createFrom(schemaJson(module, db, 66), a)
            withConn(a) { c -> step.sql.forEach { c.execSQL(it) } }
            createFrom(schemaJson(module, db, 67), b)
            val got = withConn(a) { shape(it) }
            val want = withConn(b) { shape(it) }
            smokeNeed(got.keys == want.keys, "tables differ: ${got.keys xor want.keys}")
            for (t in want.keys) smokeNeed(got[t] == want[t], "$t differs:\n  stepped: ${got[t]}\n  room:    ${want[t]}")
        } && ok
    }
    return ok
}

private infix fun Set<String>.xor(other: Set<String>): Set<String> = (this - other) + (other - this)

private fun smokeNeed(cond: Boolean, msg: String) {
    if (!cond) throw IllegalStateException(msg)
}
