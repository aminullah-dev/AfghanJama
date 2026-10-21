package com.afghanjama.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/*
 * سهمِ اندروید و کامپیوتر — همان `HttpURLConnection`ی که `LanClient`ِ
 * قراردادِ ۱ دارد، با همان دو قاعده (نشانیِ کارگاه، بی حواله).
 */
actual fun hubTransport(allowLoopback: Boolean): HubTransport = JvmHubTransport(allowLoopback)

/** روی اندروید و کامپیوتر همان بهترین نشانیِ کارت‌های شبکه ([Lan.localIp]). */
actual fun phoneLanIp(): String? = Lan.localIp()

private class JvmHubTransport(private val allowLoopback: Boolean) : HubTransport {

    override suspend fun exchange(
        host: String,
        port: Int,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int,
        connectTimeoutMs: Int,
    ): LanResult<HttpReply> {
        // پیش از هر اتصال، و بیرون از `runCatching`؛ پیامش گم نشود.
        workshopHostRefusal(host, allowLoopback)?.let { return LanResult.Err(it) }
        if (port !in 1..65535 || !path.startsWith("/")) return LanResult.Err(HUB_UNREACHABLE)
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("http://$host:$port$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = connectTimeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = false
                    useCaches = false
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                try {
                    val status = conn.responseCode
                    if (status in 300..399) return@runCatching LanResult.Err(HUB_UNREACHABLE)
                    val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                    val bytes = stream?.use { it.readNBytesCompat(LanWire.MAX_BODY_BYTES * 4) } ?: ByteArray(0)
                    LanResult.Ok(HttpReply(status, String(bytes, Charsets.UTF_8)))
                } finally {
                    conn.disconnect()
                }
            }.getOrElse { LanResult.Err(HUB_UNREACHABLE) }
        }
    }
}

/** بیش از [max] بایت خوانده نمی‌شود — جوابِ بی‌انتها گوشی را پر نمی‌کند. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
