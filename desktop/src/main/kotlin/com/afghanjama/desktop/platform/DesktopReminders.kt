package com.afghanjama.desktop.platform

import com.afghanjama.data.repo.Repo
import com.afghanjama.desktop.DeskNotices
import com.afghanjama.desktop.data.DeskPrefs
import com.afghanjama.platform.Reminders
import com.afghanjama.prefs.Settings
import com.afghanjama.work.ReminderPlan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * حلقهٔ یادآور؛ یکی برای کلِ برنامه.
 *
 * **یک بار، حتی پس از بستن و باز کردن.** یادآورهای گفته‌شده روی دیسک‌اند
 * ([DeskPrefs.shownReminders])، نه فقط در حافظه — تا ۱.۱۰ هر بار که
 * برنامه دوباره باز می‌شد، یادآورِ شیفت و استراحت دوباره می‌آمد.
 *
 * **فقط وقتی کسی وارد شده ([signedIn]).** یادآور نامِ کارکنان را دارد؛ پشتِ
 * صفحهٔ ورود (یا پس از قفلِ خودکار) نه از دفتر پرسیده می‌شود، نه پیامِ
 * سینی می‌آید. یادآوری که در این فاصله وقتش شد، با ورودِ بعدی گفته می‌شود.
 */
object DeskReminderLoop {

    private const val EVERY_MS = 30_000L

    /** شیفتِ بازی که بیش از این کهنه است، یادآوری نمی‌شود (فراموشیِ دیروز را «مرکزِ هشدار» دارد). */
    private const val LOOKBACK_MS = 24L * 60 * 60 * 1000

    private val pokes = Channel<Unit>(Channel.CONFLATED)

    private val _fired = MutableSharedFlow<ReminderPlan.Due>(extraBufferCapacity = 16)

    /** هر یادآوری که گفته شد — پنجره با آن پیامِ سینی می‌فرستد. */
    val fired: SharedFlow<ReminderPlan.Due> = _fired.asSharedFlow()

    /**
     * کسی وارد شده؟ — جلسهٔ ورود (`DeskSession`) روشن و خاموشش می‌کند؛ پیش‌فرض
     * خاموش، تا صفحهٔ ورودِ اول هم بی‌یادآور باشد.
     */
    val signedIn = MutableStateFlow(false)

    fun poke() {
        pokes.trySend(Unit)
    }

    suspend fun run(repo: Repo, settings: Settings) {
        while (true) {
            // پشتِ صفحهٔ ورود همین‌جا می‌ایستد؛ با ورود فوراً ادامه می‌دهد.
            signedIn.first { it }
            runCatching { check(repo, settings) }
            withTimeoutOrNull(EVERY_MS) { pokes.receive() }
        }
    }

    /**
     * یک نگاه: یادآورهایی که همین حالا وقتشان است و هنوز گفته نشده‌اند،
     * گفته و روی دیسک نوشته می‌شوند. جدا و با ساعتِ دادنی، تا `deskSmoke`
     * بسنجدش.
     */
    internal suspend fun check(
        repo: Repo,
        settings: Settings,
        now: Long = System.currentTimeMillis(),
        local: LocalDateTime = LocalDateTime.now(),
    ): List<ReminderPlan.Due> {
        if (!signedIn.value) return emptyList()
        val shown = DeskPrefs.shownReminders(settings, now).keys
        val open = repo.observeAttendanceSince(now - LOOKBACK_MS).first().filter { it.checkOut == null }
        val breaks = repo.observeBreakTimes().first()
        val inside = if (breaks.any { it.enabled }) repo.insideCount() else 0
        val due = ReminderPlan.shifts(open, now, shown) +
            ReminderPlan.breaks(breaks, local.hour * 60 + local.minute, local.toLocalDate().toEpochDay(), inside, shown)
        if (due.isEmpty()) return due
        // اول روی دیسک، بعد گفتن: اگر برنامه همین لحظه بسته شود، یادآور
        // دوباره نمی‌آید. یک یادآورِ گم‌شده از ده یادآورِ تکراری کم‌آزارتر است.
        DeskPrefs.markRemindersShown(settings, due.map { it.key }, now)
        due.forEach { d ->
            DeskNotices.show("${d.title}: ${d.text}", sticky = true)
            _fired.tryEmit(d)
        }
        return due
    }
}
