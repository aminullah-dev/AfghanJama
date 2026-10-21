package com.afghanjama.lan

import com.afghanjama.licence.LicenceGate
import com.afghanjama.licence.LicenceState
import com.afghanjama.licence.MainLicence
import com.afghanjama.util.nowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** یک کارِ زیرِ دستِ خیاط، همان‌طور که از گوشیِ اصلی رسیده. */
/**
 * سمتِ گوشیِ کارگر. فقط می‌خواند و درخواست می‌فرستد — هیچ نوشتنی در
 * دفترِ محلی انجام نمی‌دهد، چون گوشیِ کارگر اصلاً دفتر ندارد.
 */
class LanClient(
    private val host: String,
    private val code: String,
    /** درگاهِ سرور. برنامه همیشه [Lan.PORT] است؛ آزمونِ دودی درگاهِ خودش را دارد. */
    private val port: Int = Lan.PORT,
    /**
     * اجازهٔ وصل شدن به `127.x` — **فقط برای آزمونِ دودی** روی همین
     * ماشین. برنامه هرگز `true` نمی‌دهد؛ loopback شبکهٔ کارگاه نیست.
     */
    private val allowLoopback: Boolean = false,
) : LanApi {

    private fun url(path: String) = URL("http://$host:$port$path")

    private suspend fun call(
        path: String,
        method: String = "GET",
        body: String? = null,
        withCode: Boolean = true
    ): LanResult<JSONObject> {
        // مرزِ متنِ ساده اینجاست، نه در `network_security_config.xml`
        // (چرایش در [HostAddress.isPrivate]). **پیش از** `runCatching`
        // سنجیده می‌شود: درونِ آن، پیامِ روشنِ «این نشانی مالِ کارگاه
        // نیست» زیرِ پیامِ کلیِ «وصل نشد» گم می‌شد.
        if (!HostAddress.isPrivate(host) && !(allowLoopback && HostAddress.isLoopback(host))) {
            return LanResult.Err(HostAddress.NOT_WORKSHOP)
        }
        return withContext(Dispatchers.IO) { send(path, method, body, withCode) }
    }

    private fun send(
        path: String,
        method: String,
        body: String?,
        withCode: Boolean
    ): LanResult<JSONObject> {
        return runCatching {
            val conn = (url(path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6_000
                readTimeout = 8_000
                // حواله دنبال نمی‌شود. `HttpURLConnection` پیش‌فرض دنبالِ
                // جوابِ ۳۰۲ از http به http می‌رود و رمزِ کارگاه را هم
                // همراه می‌بَرد؛ پس هر چیزی که روی یک نشانیِ خصوصی جواب
                // بدهد می‌توانست گوشی را به هر میزبانِ اینترنتی بفرستد و
                // مرزِ [HostAddress.isPrivate] بالا بی‌اثر می‌شد. سرورِ
                // خیاط‌یار هرگز حواله نمی‌دهد.
                instanceFollowRedirects = false
                if (withCode) setRequestProperty(Lan.HEADER_CODE, code)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = conn.responseCode
            if (status in 300..399) {
                // جوابِ حواله هر بدنه‌ای داشته باشد خوانده نمی‌شود.
                conn.disconnect()
                return LanResult.Err("جوابِ دستگاهِ اصلی خوانده نشد.")
            }
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return LanResult.Err("جوابِ دستگاهِ اصلی خوانده نشد.")
            reportLicence(json)
            if (json.optBoolean("ok", false)) LanResult.Ok(json)
            else LanResult.Err(json.optString("error").ifBlank { "درخواست پذیرفته نشد." })
        }.getOrElse {
            // خطای شبکه را با زبانِ خودِ کاربر می‌گوییم، نه با متنِ جاوا.
            // «دستگاهِ اصلی»، نه «گوشیِ اصلی»: کارگر نمی‌داند آن‌سو گوشی است
            // یا کامپیوتر، و کارگاهی که مک دارد دنبالِ گوشی می‌گشت.
            LanResult.Err(
                "به دستگاهِ اصلیِ کارگاه وصل نشد. مطمئن شوید این گوشی و دستگاهِ اصلی " +
                    "(گوشی یا کامپیوتر) روی یک وای‌فایِ کارگاه‌اند و روی دستگاهِ اصلی " +
                    "«کارگاهِ وصل» روشن است."
            )
        }
    }

    /**
     * گوشیِ کارگر لایسنسِ خودش را ندارد؛ پیروِ دستگاهِ اصلی است. هر جوابی
     * که وضعیتِ لایسنسِ آن را همراه دارد، اینجا به [LicenceGate] می‌رسد
     * (و `Licensing` ماندگارش می‌کند). دستگاهِ اصلیِ نسخهٔ پیش از لایسنس
     * چیزی نمی‌فرستد و کارگر هم بی‌دلیل بسته نمی‌شود.
     */
    private fun reportLicence(json: JSONObject) {
        val l = json.optJSONObject("licence") ?: return
        val state = runCatching { LicenceState.valueOf(l.optString("state")) }.getOrNull() ?: return
        LicenceGate.onMainReport?.invoke(MainLicence(state, l.optBoolean("writable", true), nowMillis()))
    }

    override suspend fun ping(): LanResult<Unit> =
        when (val r = call(Lan.PATH_PING, withCode = false)) {
            is LanResult.Ok -> LanResult.Ok(Unit)
            is LanResult.Err -> r
        }

    override suspend fun myWork(worker: String): LanResult<List<RemoteWork>> {
        val encoded = java.net.URLEncoder.encode(worker, "UTF-8")
        return when (val r = call("${Lan.PATH_MY_WORK}?worker=$encoded")) {
            is LanResult.Err -> r
            is LanResult.Ok -> {
                val arr = r.value.optJSONArray("inHand")
                val list = buildList {
                    for (i in 0 until (arr?.length() ?: 0)) {
                        val o = arr!!.getJSONObject(i)
                        add(
                            RemoteWork(
                                id = o.optLong("id"),
                                orderCode = o.optString("orderCode"),
                                qty = o.optInt("qty"),
                                unitWage = o.optLong("unitWage"),
                                createdAt = o.optLong("createdAt")
                            )
                        )
                    }
                }
                LanResult.Ok(list)
            }
        }
    }

    /** سطرهای تابلوی «در حالِ دوخت» از گوشیِ اصلی. */
    override suspend fun board(): LanResult<List<com.afghanjama.ui.vm.BoardRow>> =
        when (val r = call(Lan.PATH_BOARD)) {
            is LanResult.Err -> r
            is LanResult.Ok -> {
                val arr = r.value.optJSONArray("rows")
                LanResult.Ok(
                    buildList {
                        for (i in 0 until (arr?.length() ?: 0)) {
                            val o = arr!!.getJSONObject(i)
                            add(
                                com.afghanjama.ui.vm.BoardRow(
                                    tailor = o.optString("tailor"),
                                    orderCode = o.optString("orderCode"),
                                    design = o.optString("design"),
                                    qty = o.optInt("qty"),
                                    days = o.optInt("days"),
                                    dueIn = if (o.has("dueIn")) o.optInt("dueIn") else null
                                )
                            )
                        }
                    }
                )
            }
        }

    override suspend fun sendRequest(
        device: String,
        worker: String,
        type: String,
        summary: String,
        // مقدارهای پیش‌فرض حالا در `LanApi` نوشته‌اند؛ کاتلین اجازه
        // نمی‌دهد پیاده‌سازی دوباره تعریفشان کند.
        refId: Long,
        amount: Int,
        note: String,
    ): LanResult<String> {
        val body = JSONObject().apply {
            put("device", device)
            put("worker", worker)
            put("type", type)
            put("summary", summary)
            put("refId", refId)
            put("amount", amount)
            put("note", note)
        }.toString()
        return when (val r = call(Lan.PATH_REQUEST, method = "POST", body = body)) {
            is LanResult.Err -> r
            is LanResult.Ok -> LanResult.Ok(
                r.value.optString("message").ifBlank { "درخواست فرستاده شد." }
            )
        }
    }
}

/**
 * سهمِ JVM و اندروید — همان مشتریِ HTTPی همیشگی.
 */
actual fun lanClient(host: String, code: String): LanApi = LanClient(host, code)

/** روی اندروید و کامپیوتر اشتراکِ کارگاه هست. */
actual fun workshopSharingAvailable(): Boolean = true
