package com.afghanjama.platform

import android.content.Context
import com.afghanjama.work.BreakReminderWorker
import com.afghanjama.work.ShiftReminderWorker

/**
 * یادآورهای اندروید — پوششی نازک روی `WorkManager`.
 *
 * هیچ منطقی اینجا نیست و عمدی است: زمان‌بندی کارِ سکو است و قاعده‌اش
 * (`ShiftPolicy.WARN_AFTER_MS`) در `:core` می‌مانَد.
 */
class AndroidReminders(private val context: Context) : Reminders {
    override fun scheduleShift(employee: String) =
        ShiftReminderWorker.schedule(context, employee)

    override fun cancelShift(employee: String) =
        ShiftReminderWorker.cancel(context, employee)

    override fun scheduleBreak(id: Long, hour: Int, minute: Int) =
        BreakReminderWorker.schedule(context, id, hour, minute)

    override fun cancelBreak(id: Long) =
        BreakReminderWorker.cancel(context, id)
}
