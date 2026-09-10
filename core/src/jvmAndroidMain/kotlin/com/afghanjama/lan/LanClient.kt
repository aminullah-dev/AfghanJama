package com.afghanjama.lan

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
    private val code: String
) : LanApi {

    private fun url(path: String) = URL("http://$host:${Lan.PORT}$path")

    private suspend fun call(
        path: String,
        method: String = "GET",
        body: String? = null,
        withCode: Boolean = true
    ): LanResult<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (url(path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6_000
                readTimeout = 8_000
                if (withCode) setRequestProperty(Lan.HEADER_CODE, code)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return@withContext LanResult.Err("جوابِ گوشیِ اصلی خوانده نشد.")
            if (json.optBoolean("ok", false)) LanResult.Ok(json)
            else LanResult.Err(json.optString("error").ifBlank { "درخواست پذیرفته نشد." })
        }.getOrElse {
            // خطای شبکه را با زبانِ خودِ کاربر می‌گوییم، نه با متنِ جاوا
            LanResult.Err(
                "به گوشیِ اصلی وصل نشد. مطمئن شوید هر دو گوشی روی وای‌فای " +
                    "کارگاه‌اند و روی گوشیِ اصلی «اشتراکِ کارگاه» روشن است."
            )
        }
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

    /** سطرهای تابلوی «در حال دوخت» از گوشیِ اصلی. */
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
