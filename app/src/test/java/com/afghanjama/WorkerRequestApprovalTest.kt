package com.afghanjama

import com.afghanjama.data.Db
import com.afghanjama.data.dao.AttendanceDao
import com.afghanjama.data.dao.SyncRequestDao
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.entities.REQUEST_REPEAT_WINDOW_MS
import com.afghanjama.data.entities.SewingAssignment
import com.afghanjama.data.entities.SyncRequest
import com.afghanjama.data.entities.isRepeatOf
import com.afghanjama.data.entities.ownsAssignment
import com.afghanjama.data.entities.requestWorkerLabel
import com.afghanjama.data.repo.Repo
import com.afghanjama.licence.LicenceGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * تأییدِ درخواستِ گوشیِ کارگر — زمانِ حضور و صاحبِ کار.
 *
 * **دو اشکالی که این آزمون نگه می‌دارد:**
 *
 * ۱. ورودی که از گوشی می‌رسید ساعتِ **تأییدِ کارفرما** را می‌گرفت. خیاط
 *    ساعتِ هفت می‌زد، کارفرما ساعتِ ده تأیید می‌کرد، و سه ساعت از حقوقش
 *    می‌افتاد. و اگر «خروج» پیش از «ورود» تأیید می‌شد، خروج بی‌صدا هیچ
 *    نمی‌کرد ولی «تأییدشده» می‌شد — بازهٔ آن روز گم.
 * ۲. «تحویلِ دوخت» هرگز نمی‌پرسید کار به نامِ کیست؛ گوشیِ یک خیاط
 *    می‌توانست کارِ خیاطِ دیگر را تمام کند و کارمزد جابه‌جا شود.
 *
 * `Repo` واقعی است؛ حضور و صندوقِ درخواست‌ها در حافظه‌اند و بقیهٔ
 * دیتابیس ضبط‌کننده است. برگشتِ تراکنش کارِ هر سکوست و `txSmoke` آن را
 * می‌سنجد؛ اینجا فقط تصمیم‌ها سنجیده می‌شوند.
 */
class WorkerRequestApprovalTest {

    @Before
    fun clean() = LicenceGate.resetForTest()

    @After
    fun reset() = LicenceGate.resetForTest()

    private val hour = 3_600_000L
    private val day0 = 1_790_000_000_000L // یک روزِ ثابت، تا آزمون به ساعتِ ماشین بند نباشد

    private class MemAttendance : AttendanceDao {
        val rows = mutableListOf<AttendanceRecord>()
        private var next = 1L
        override suspend fun insert(record: AttendanceRecord) {
            rows += record.copy(id = next++)
        }
        override suspend fun update(record: AttendanceRecord) {
            rows.replaceAll { if (it.id == record.id) record else it }
        }
        override fun observeRecent(): Flow<List<AttendanceRecord>> = flowOf(rows.toList())
        override fun observeSince(since: Long): Flow<List<AttendanceRecord>> =
            flowOf(rows.filter { it.checkIn >= since })
        override suspend fun findOpen(employee: String): AttendanceRecord? =
            rows.filter { it.employee == employee && it.checkOut == null }.maxByOrNull { it.checkIn }
        override suspend fun findLast(employee: String): AttendanceRecord? =
            rows.filter { it.employee == employee }.maxByOrNull { it.checkIn }
    }

    private class MemRequests : SyncRequestDao {
        val rows = mutableListOf<SyncRequest>()
        private var next = 1L
        override fun observeAll(): Flow<List<SyncRequest>> =
            flowOf(rows.sortedByDescending { it.createdAt }.take(200))
        override fun observePending(): Flow<List<SyncRequest>> =
            flowOf(rows.filter { it.status == "PENDING" }.sortedBy { it.createdAt })
        override fun observePendingCount(): Flow<Int> = flowOf(rows.count { it.status == "PENDING" })
        override suspend fun getById(id: Long): SyncRequest? = rows.firstOrNull { it.id == id }
        override suspend fun insert(row: SyncRequest): Long {
            val id = next++
            rows += row.copy(id = id)
            return id
        }
        override suspend fun update(row: SyncRequest) {
            rows.replaceAll { if (it.id == row.id) row else it }
        }
    }

    private class Fixture {
        val attendance = MemAttendance()
        val requests = MemRequests()
        val assignments = mutableMapOf<Long, SewingAssignment>()
        val sewingCalls = mutableListOf<String>()

        private fun <T> proxy(type: Class<T>, answer: (String, Array<Any?>?) -> Any?): T =
            type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, m, args -> answer(m.name, args) })

        private val sewing = proxy(com.afghanjama.data.dao.SewingAssignmentDao::class.java) { name, args ->
            sewingCalls += name
            if (name == "getById") assignments[args!![0] as Long] else null
        }

        val db: Db = proxy(Db::class.java) { name, args ->
            when {
                name == "atomic" -> {
                    @Suppress("UNCHECKED_CAST")
                    val block = args!![0] as (kotlin.coroutines.Continuation<Any?>) -> Any?
                    @Suppress("UNCHECKED_CAST")
                    block(args[1] as kotlin.coroutines.Continuation<Any?>)
                }
                name == "attendanceDao" -> attendance
                name == "syncRequestDao" -> requests
                name == "sewingAssignmentDao" -> sewing
                // حسابرسی و بقیه: بی‌اثر
                name.endsWith("Dao") -> Proxy.newProxyInstance(
                    Db::class.java.classLoader,
                    arrayOf(Db::class.java.getMethod(name).returnType)
                ) { _, _, _ -> null }
                else -> null
            }
        }
        val repo = Repo(db)

        fun request(worker: String, type: String, at: Long, refId: Long = 0, amount: Int = 0): Long =
            runBlocking {
                requests.insert(
                    SyncRequest(
                        deviceName = "گوشی", worker = worker, type = type,
                        summary = type, refId = refId, amount = amount, createdAt = at
                    )
                )
            }

        fun status(id: Long) = requests.rows.first { it.id == id }.status
        fun approve(id: Long): String? = runBlocking { repo.approveRequest(id) }
    }

    private val ahmad = "[T1] احمد"

    // ------------------------------------------------------------
    // زمانِ حضور
    // ------------------------------------------------------------

    @Test
    fun `attendance is stamped with the press time, not the approval time`() {
        val f = Fixture()
        val pressed = day0 + 7 * hour
        val id = f.request(ahmad, "ATTENDANCE_IN", pressed)
        assertNull(f.approve(id))
        assertEquals(1, f.attendance.rows.size)
        assertEquals("احمد", f.attendance.rows[0].employee)
        assertEquals("ساعتِ ورود باید ساعتِ زدنِ دکمه باشد", pressed, f.attendance.rows[0].checkIn)
        assertEquals("APPROVED", f.status(id))

        val out = f.request(ahmad, "ATTENDANCE_OUT", day0 + 16 * hour)
        assertNull(f.approve(out))
        assertEquals(day0 + 16 * hour, f.attendance.rows[0].checkOut)
    }

    @Test
    fun `an OUT approved before its IN applies the IN first`() {
        val f = Fixture()
        val inId = f.request(ahmad, "ATTENDANCE_IN", day0 + 8 * hour)
        val outId = f.request(ahmad, "ATTENDANCE_OUT", day0 + 17 * hour)
        assertNull(f.approve(outId))
        assertEquals(listOf(day0 + 8 * hour to day0 + 17 * hour),
            f.attendance.rows.map { it.checkIn to it.checkOut })
        assertEquals("APPROVED", f.status(inId))
        assertEquals("APPROVED", f.status(outId))
    }

    /** بی ترتیبِ زمان، تأییدِ خروجِ عصر اول، استراحتِ ظهر را می‌بلعید. */
    @Test
    fun `a midday break survives approvals out of order`() {
        val f = Fixture()
        val ids = listOf(
            f.request(ahmad, "ATTENDANCE_IN", day0 + 8 * hour),
            f.request(ahmad, "ATTENDANCE_OUT", day0 + 12 * hour),
            f.request(ahmad, "ATTENDANCE_IN", day0 + 13 * hour),
            f.request(ahmad, "ATTENDANCE_OUT", day0 + 17 * hour),
        )
        // درخواستِ کارگرِ دیگر دست نمی‌خورد
        val other = f.request("[T2] محمود", "ATTENDANCE_IN", day0 + 7 * hour)

        assertNull(f.approve(ids.last()))
        assertEquals(
            listOf(day0 + 8 * hour to day0 + 12 * hour, day0 + 13 * hour to day0 + 17 * hour),
            f.attendance.rows.map { it.checkIn to it.checkOut }
        )
        ids.forEach { assertEquals("APPROVED", f.status(it)) }
        assertEquals("PENDING", f.status(other))
    }

    @Test
    fun `an OUT with nothing open is refused and stays pending`() {
        val f = Fixture()
        val id = f.request(ahmad, "ATTENDANCE_OUT", day0 + 17 * hour)
        val why = f.approve(id)
        assertNotNull("خروجِ بی‌ورود نباید «تأییدشده» شود", why)
        assertTrue(why!!, why.contains("احمد"))
        assertEquals("PENDING", f.status(id))
        assertTrue(f.attendance.rows.isEmpty())
    }

    /** ورودِ گذشته روی بازهٔ دستیِ ثبت‌شده نمی‌افتد — وگرنه ساعت‌ها دو بار شمرده می‌شد. */
    @Test
    fun `a past IN that overlaps a recorded shift is refused`() {
        val f = Fixture()
        runBlocking {
            f.attendance.insert(AttendanceRecord(employee = "احمد", checkIn = day0 + 9 * hour, checkOut = day0 + 12 * hour))
        }
        val id = f.request(ahmad, "ATTENDANCE_IN", day0 + 8 * hour)
        assertNotNull(f.approve(id))
        assertEquals("PENDING", f.status(id))
        assertEquals(1, f.attendance.rows.size)
    }

    @Test
    fun `an OUT before the open IN is refused`() {
        val f = Fixture()
        runBlocking { f.attendance.insert(AttendanceRecord(employee = "احمد", checkIn = day0 + 10 * hour)) }
        val id = f.request(ahmad, "ATTENDANCE_OUT", day0 + 9 * hour)
        assertNotNull(f.approve(id))
        assertNull("بازهٔ وارونه نباید بسته شود", f.attendance.rows[0].checkOut)
        assertEquals("PENDING", f.status(id))
    }

    /** درخواستِ پیشینی که ثبت‌شدنی نیست جلوی بعدی را می‌گیرد و نامش برده می‌شود. */
    @Test
    fun `a blocking earlier request is named, nothing is approved`() {
        val f = Fixture()
        runBlocking { f.attendance.insert(AttendanceRecord(employee = "احمد", checkIn = day0 + 7 * hour)) }
        val inId = f.request(ahmad, "ATTENDANCE_IN", day0 + 8 * hour)
        val outId = f.request(ahmad, "ATTENDANCE_OUT", day0 + 17 * hour)
        val why = f.approve(outId)
        assertNotNull(why)
        assertTrue(why!!, why.contains("ثبت ورود"))
        assertEquals("PENDING", f.status(inId))
        assertEquals("PENDING", f.status(outId))
        assertNull(f.attendance.rows[0].checkOut)
    }

    @Test
    fun `the manual buttons still stamp now`() {
        val f = Fixture()
        val before = System.currentTimeMillis()
        runBlocking { f.repo.checkIn("احمد") }
        assertEquals(1, f.attendance.rows.size)
        assertTrue(f.attendance.rows[0].checkIn >= before)
        runBlocking { f.repo.checkIn("احمد") } // داخل است؛ کاری نمی‌کند
        assertEquals(1, f.attendance.rows.size)
        runBlocking { f.repo.checkOut("احمد") }
        assertNotNull(f.attendance.rows[0].checkOut)
    }

    // ------------------------------------------------------------
    // صاحبِ کار
    // ------------------------------------------------------------

    private fun assignment(id: Long, tailor: String, status: String = "SEWING") = SewingAssignment(
        id = id, orderId = "o-$id", orderCode = "AJ-$id", tailorLabel = tailor, qty = 10, unitWage = 50, status = status
    )

    @Test
    fun `a tailor cannot complete another tailor's work`() {
        val f = Fixture()
        f.assignments[7] = assignment(7, "[T2] محمود")
        val id = f.request(ahmad, "SEWING_DONE", day0, refId = 7, amount = 10)
        val why = f.approve(id)
        assertNotNull(why)
        assertTrue(why!!, why.contains("محمود") && why.contains("احمد"))
        assertEquals("PENDING", f.status(id))
        assertEquals("فقط باید خوانده می‌شد", listOf("getById"), f.sewingCalls)
    }

    @Test
    fun `ownership compares the stored label the same way the server stores it`() {
        val a = assignment(1, "  [T1] احمد ")
        assertTrue(ownsAssignment(a, "[T1] احمد"))
        assertTrue(ownsAssignment(a, " [T1] احمد  "))
        assertFalse(ownsAssignment(a, "[T10] احمد"))
        assertFalse(ownsAssignment(a, ""))
        assertFalse("برچسبِ خالیِ کار مالِ هیچ‌کس نیست", ownsAssignment(assignment(2, " "), ""))

        // برچسبِ بلندتر از سقف: سرور می‌بُرد و تأیید هم همان‌طور می‌سنجد
        val long = "[T1] " + "الف".repeat(30)
        assertTrue(ownsAssignment(assignment(3, long), requestWorkerLabel(long)))
    }

    // ------------------------------------------------------------
    // تلاشِ دوباره
    // ------------------------------------------------------------

    @Test
    fun `a resend of the same press is a repeat`() {
        val now = day0 + 8 * hour
        val last = SyncRequest(
            id = 5, deviceName = "گوشی", worker = ahmad, type = "ATTENDANCE_IN", summary = "",
            createdAt = now - 60_000
        )
        assertTrue(isRepeatOf(last, ahmad, "ATTENDANCE_IN", 0, 0, "", now))
        assertFalse("نوعِ دیگر", isRepeatOf(last, ahmad, "ATTENDANCE_OUT", 0, 0, "", now))
        assertFalse("کارگرِ دیگر", isRepeatOf(last, "[T2] محمود", "ATTENDANCE_IN", 0, 0, "", now))
        assertFalse("متنِ دیگر", isRepeatOf(last, ahmad, "ATTENDANCE_IN", 0, 0, "x", now))
        assertFalse("تعدادِ دیگر", isRepeatOf(last, ahmad, "ATTENDANCE_IN", 0, 3, "", now))
        assertFalse("رسیدگی‌شده", isRepeatOf(last.copy(status = "APPROVED"), ahmad, "ATTENDANCE_IN", 0, 0, "", now))
        assertFalse("کهنه", isRepeatOf(last, ahmad, "ATTENDANCE_IN", 0, 0, "", last.createdAt + REQUEST_REPEAT_WINDOW_MS + 1))
        assertFalse("هیچ", isRepeatOf(null, ahmad, "ATTENDANCE_IN", 0, 0, "", now))
    }
}
