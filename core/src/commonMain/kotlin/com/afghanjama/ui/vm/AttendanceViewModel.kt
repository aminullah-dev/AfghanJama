package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.CardScan
import com.afghanjama.data.EmployeeCard
import com.afghanjama.data.entities.AttendanceRecord
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.PersianDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** وضعیت حضورِ یک کارمند. */
data class EmployeeAttendance(
    val name: String,
    val isIn: Boolean,
    val since: Long?          // زمان ورودِ بازهٔ باز (اگر داخل باشد)
)

/** کارکردِ یک کارمند در بازهٔ گزارش (۳۰ روز اخیر). */
data class WorkSummary(
    val name: String,
    val totalMinutes: Long,   // مجموع دقیقه‌های بازه‌های بسته
    val daysWorked: Int       // تعداد روزهای دارای حضور
)

/**
 * صاحبِ یک کارتِ کارمند.
 *
 * [detail] سمت (کارکنان) یا کدِ کاتالوگ (خیاط و ناظر) است؛ روی کارت زیرِ
 * نام می‌آید تا دو «احمد» از هم جدا شوند.
 */
data class CardHolder(
    val name: String,
    val kind: EmployeeCard.Kind,
    val detail: String,
    val code: String,
)

/**
 * پاسخِ آخرین اسکن.
 *
 * [seq] فقط برای این است که دو اسکنِ پشتِ‌سرِ‌همِ یک نتیجه (مثلاً دو
 * بار «کارت نیست») دو رویداد باشند نه یکی — `StateFlow` مقدارِ برابر را
 * دوباره نمی‌فرستد و بنرِ نتیجه دومی را نشان نمی‌داد.
 */
data class ScanFeedback(val result: CardScan, val seq: Long)

/** حضور و غیاب کارمند با تأیید اثر انگشت یا اسکنِ کارت. */
class AttendanceViewModel(private val repo: Repo) : ViewModel() {

    val records: StateFlow<List<AttendanceRecord>> =
        repo.observeAttendance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** فهرست کارمندان (خیاط‌ها + ناظرها + کارکنان) با وضعیت حضور. */
    val employees: StateFlow<List<EmployeeAttendance>> =
        combine(
            repo.observeTailors(),
            repo.observeInspectors(),
            repo.observeStaff(),
            repo.observeAttendance()
        ) { tailors, inspectors, staff, recs ->
            val names = (tailors.map { it.name } + inspectors.map { it.name } + staff.map { it.name })
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
            names.map { name ->
                val open = recs.firstOrNull { it.employee == name && it.checkOut == null }
                EmployeeAttendance(name = name, isIn = open != null, since = open?.checkIn)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** کارکردِ ۳۰ روز اخیرِ هر کارمند: جمعِ ساعتِ بازه‌های بسته + تعداد روز. */
    val monthlyWork: StateFlow<List<WorkSummary>> =
        repo.observeAttendanceSince(nowMillis() - 30L * 24 * 3600 * 1000)
            .map { recs ->
                recs.filter { it.checkOut != null }
                    .groupBy { it.employee }
                    .map { (name, list) ->
                        WorkSummary(
                            name = name,
                            // صفرکَف روی **هر بازه**، نه روی جمع: یک سطرِ خرابِ
                            // منفی (ساعتِ گوشی عقب رفته) نباید کارکردِ واقعیِ
                            // بازه‌های دیگر را کم کند.
                            totalMinutes = list.sumOf {
                                (((it.checkOut ?: 0L) - it.checkIn) / 60_000L).coerceAtLeast(0)
                            },
                            // روزِ تقویمیِ محلی، نه تقسیمِ ساده بر ۸۶۴۰۰۰۰۰ که
                            // مرزش UTC است — یعنی ۰۴:۳۰ به وقتِ کابل، و هر
                            // ورودِ سرِ صبح روزِ قبل شمرده می‌شد.
                            daysWorked = list
                                .map { PersianDate.todayJalali(it.checkIn).toList() }
                                .distinct().size
                        )
                    }
                    .sortedByDescending { it.totalMinutes }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * اصلاحِ ساعتِ یک بازهٔ ثبت‌شده.
     *
     * اگر خروج پیش از ورود باشد `Repo` چیزی نمی‌نویسد و اینجا پیام
     * می‌آید — ساکت رد شدن یعنی کاربر فکر می‌کند ذخیره شده.
     */
    fun editTimes(record: AttendanceRecord, checkIn: Long, checkOut: Long?) =
        viewModelScope.launch {
            val ok = repo.editAttendance(record, checkIn, checkOut)
            _editMessage.value =
                if (ok) "ساعت اصلاح شد." else "ساعتِ خروج نمی‌تواند پیش از ورود باشد."
        }

    private val _editMessage = MutableStateFlow<String?>(null)
    val editMessage: StateFlow<String?> = _editMessage

    fun clearEditMessage() { _editMessage.value = null }

    fun checkIn(name: String) = viewModelScope.launch { repo.checkIn(name) }
    fun checkOut(name: String) = viewModelScope.launch { repo.checkOut(name) }

    /** هر کسی که می‌تواند کارت بگیرد — به ترتیبِ نام. */
    val cardHolders: StateFlow<List<CardHolder>> =
        combine(
            repo.observeTailors(),
            repo.observeInspectors(),
            repo.observeStaff(),
        ) { tailors, inspectors, staff ->
            val t = tailors.filter { it.id > 0 }.map {
                CardHolder(it.name.trim(), EmployeeCard.Kind.TAILOR, "کد ${it.code}",
                    EmployeeCard.encode(EmployeeCard.Kind.TAILOR, it.id))
            }
            val i = inspectors.filter { it.id > 0 }.map {
                CardHolder(it.name.trim(), EmployeeCard.Kind.INSPECTOR, "کد ${it.code}",
                    EmployeeCard.encode(EmployeeCard.Kind.INSPECTOR, it.id))
            }
            val st = staff.filter { it.id > 0 }.map {
                CardHolder(it.name.trim(), EmployeeCard.Kind.STAFF, it.role.trim(),
                    EmployeeCard.encode(EmployeeCard.Kind.STAFF, it.id))
            }
            (t + i + st).filter { it.name.isNotEmpty() }.sortedBy { it.name }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scan = MutableStateFlow<ScanFeedback?>(null)
    val scan: StateFlow<ScanFeedback?> = _scan
    private var scanSeq = 0L

    /**
     * کدِ خوانده‌شده از دوربین، اسکنرِ USB یا تایپِ دستی.
     *
     * [onRecorded] بعد از ثبتِ واقعی صدا زده می‌شود — صفحه با آن یادآورِ
     * پایانِ شیفت را می‌گذارد یا برمی‌دارد، همان کاری که دکمه‌های ورود و
     * خروج می‌کنند.
     */
    fun scanCard(raw: String, onRecorded: (CardScan.Recorded) -> Unit = {}) =
        viewModelScope.launch {
            if (raw.isBlank()) return@launch
            val r = repo.scanCard(raw)
            _scan.value = ScanFeedback(r, ++scanSeq)
            if (r is CardScan.Recorded) onRecorded(r)
        }

    fun clearScan() { _scan.value = null }
}
