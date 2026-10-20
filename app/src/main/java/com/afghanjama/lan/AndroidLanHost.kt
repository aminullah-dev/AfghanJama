package com.afghanjama.lan

import android.content.Context
import com.afghanjama.data.Db
import com.afghanjama.data.AppDb

/**
 * [LanHost] روی اندروید — همان سه چیزی که تا دیروز مستقیم در
 * `WorkshopLinkViewModel` صدا زده می‌شدند.
 */
class AndroidLanHost(private val ctx: Context) : LanHost {

    override val deviceName: String
        get() = android.os.Build.MODEL ?: "گوشی"

    override fun localIp(): String? = androidLocalIp(ctx)

    override fun localIps(): List<String> =
        (listOfNotNull(androidLocalIp(ctx)) + Lan.localIps()).distinct()

    /** زیرشبکه‌ها از کارت‌های شبکه، و دروازهٔ وای‌فای برای نشانیِ وای‌فای. */
    override fun networks(): Map<String, LanNetwork> {
        val base = super.networks()
        val (ip, gw) = androidWifiGateway(ctx) ?: return base
        val net = base[ip] ?: return base
        if (!LanNetwork.contains(net.subnet, gw)) return base
        return base + (ip to net.copy(gateway = gw))
    }

    /**
     * **همان دفتری که صفحه‌ها می‌خوانند** ([AppDb])، نه نمونهٔ دوم.
     *
     * تا ۱.۹.۱ اینجا `buildAppDatabase` جدا صدا زده می‌شد. Room تغییرِ
     * یک نمونه را به `Flow`های نمونهٔ دیگر خبر نمی‌دهد؛ پس درخواستی که
     * گوشیِ کارگر می‌نشاند در فهرستِ «درخواست‌های منتظر» دیده نمی‌شد تا
     * کارفرما صفحه را ترک کند و برگردد.
     */
    override fun ledger(): Db = AppDb.get(ctx)
}
