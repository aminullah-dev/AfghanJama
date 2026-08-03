package com.afghanjama.lan

import android.content.Context
import com.afghanjama.data.Db
import com.afghanjama.data.buildAppDatabase

/**
 * [LanHost] روی اندروید — همان سه چیزی که تا دیروز مستقیم در
 * `WorkshopLinkViewModel` صدا زده می‌شدند.
 */
class AndroidLanHost(private val ctx: Context) : LanHost {

    override val deviceName: String
        get() = android.os.Build.MODEL ?: "گوشی"

    override fun localIp(): String? = androidLocalIp(ctx)

    /**
     * دیتابیس یک بار ساخته می‌شود و می‌ماند. اگر هر بار تازه ساخته
     * می‌شد، روشن و خاموش کردنِ اشتراک چند اتصالِ باز به یک فایل
     * می‌گذاشت.
     */
    private val db: Db by lazy { buildAppDatabase(ctx.applicationContext) }

    override fun ledger(): Db = db
}
