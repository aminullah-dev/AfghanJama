package com.afghanjama.desktop.platform

import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.DeskNotices
import com.afghanjama.platform.Reminders
import com.afghanjama.work.ReminderPlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime

/**
 * [Reminders] روی مک و ویندوز — یادآورِ پایانِ شیفت و وقتِ نان و چای، تا
 * وقتی برنامه باز است.
 *
 * **چرا لازم شد.** تا ۱.۹ کامپیوتر `LocalReminders` نمی‌داد (`null`). گوشی
 * با `WorkManager` حتی وقتی بسته است یادآوری می‌کند؛ کامپیوترِ دفتر که
 * همهٔ روز باز است هیچ یادآوری نداشت.
 *
 * **زمان‌بندی نمی‌کند، از دفتر می‌پرسد.** هر ۳۰ ثانیه (و فوراً پس از هر
 * «ورود»، «خروج» یا تغییرِ وقت‌ها) شیفت‌های باز و وقت‌های استراحت از دفتر
 * خوانده می‌شوند و [ReminderPlan] می‌گوید چه چیزی وقتش است. پس ورودی که از
 * گوشیِ کارگر یا پیش از باز شدنِ برنامه آمده هم یادآوری می‌شود.
 *
 * یادآور در خودِ پنجره می‌ماند تا کسی رویش بزند، و اگر سیستم اجازه دهد پیامِ
 * سینی (نوارِ وظیفه یا نوارِ منوی مک) هم می‌آید.
 *
 * **کاری که نمی‌کند:** وقتی برنامه بسته است هیچ یادآوری نمی‌آید. هشدارهای
 * هفتگیِ کارمزد و کمبودِ انبار در «مرکزِ هشدار» هستند و با باز شدنِ برنامه
 * دیده می‌شوند.
 */
class DesktopReminders : Reminders {
    override fun scheduleShift(employee: String) = DeskReminderLoop.poke()
    override fun cancelShift(employee: String) = DeskReminderLoop.poke()
    override fun scheduleBreak(id: Long, hour: Int, minute: Int) = DeskReminderLoop.poke()
    override fun cancelBreak(id: Long) = DeskReminderLoop.poke()
}

/** حلقهٔ یادآور؛ یکی برای کلِ برنامه. */
object DeskReminderLoop {

    private const val EVERY_MS = 30_000L

    /** شیفتِ بازی که بیش از این کهنه است، یادآوری نمی‌شود (فراموشیِ دیروز را «مرکزِ هشدار» دارد). */
    private const val LOOKBACK_MS = 24L * 60 * 60 * 1000

    private val pokes = Channel<Unit>(Channel.CONFLATED)
    private val done = mutableSetOf<String>()

    private val _fired = MutableSharedFlow<ReminderPlan.Due>(extraBufferCapacity = 16)

    /** هر یادآوری که گفته شد — پنجره با آن پیامِ سینی می‌فرستد. */
    val fired: SharedFlow<ReminderPlan.Due> = _fired.asSharedFlow()

    fun poke() {
        pokes.trySend(Unit)
    }

    suspend fun run(repo: Repo) {
        while (true) {
            runCatching { check(repo) }
            withTimeoutOrNull(EVERY_MS) { pokes.receive() }
        }
    }

    private suspend fun check(repo: Repo) {
        val now = System.currentTimeMillis()
        val open = repo.observeAttendanceSince(now - LOOKBACK_MS).first().filter { it.checkOut == null }
        val breaks = repo.observeBreakTimes().first()
        val local = LocalDateTime.now()
        val inside = if (breaks.any { it.enabled }) repo.insideCount() else 0
        val due = ReminderPlan.shifts(open, now, done) +
            ReminderPlan.breaks(breaks, local.hour * 60 + local.minute, local.toLocalDate().toEpochDay(), inside, done)
        due.forEach { d ->
            done += d.key
            DeskNotices.show("${d.title}: ${d.text}", sticky = true)
            _fired.tryEmit(d)
        }
    }
}
