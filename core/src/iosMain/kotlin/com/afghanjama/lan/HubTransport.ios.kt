package com.afghanjama.lan

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.CPointerVar
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.AF_INET
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.sockaddr_in
import kotlin.coroutines.resume

/*
 * سهمِ آیفون — `NSURLSession`، با همان دو قاعدهٔ اندروید: پیش از هر اتصال
 * نشانیِ کارگاه ([workshopHostRefusal])، و حواله دنبال نمی‌شود
 * ([NoRedirect]).
 *
 * `Info.plist`ِ برنامه `NSAllowsLocalNetworking` و
 * `NSLocalNetworkUsageDescription` دارد؛ بی دومی، iOS ۱۴ به بعد اولین اتصال
 * به شبکهٔ محلی را بی‌صدا می‌بندد.
 */
actual fun hubTransport(allowLoopback: Boolean): HubTransport = IosHubTransport(allowLoopback)

/** حواله را رد می‌کند: `completionHandler(null)` یعنی «جوابِ ۳۰۲ را همان‌طور بده». */
private class NoRedirect : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        completionHandler(null)
    }
}

private class IosHubTransport(private val allowLoopback: Boolean) : HubTransport {

    private val session: NSURLSession by lazy {
        val cfg = NSURLSessionConfiguration.ephemeralSessionConfiguration
        NSURLSession.sessionWithConfiguration(cfg, NoRedirect(), null)
    }

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
        workshopHostRefusal(host, allowLoopback)?.let { return LanResult.Err(it) }
        if (port !in 1..65535 || !path.startsWith("/")) return LanResult.Err(HUB_UNREACHABLE)
        val url = NSURL.URLWithString("http://$host:$port$path") ?: return LanResult.Err(HUB_UNREACHABLE)
        val req = NSMutableURLRequest.requestWithURL(url, NSURLRequestReloadIgnoringLocalCacheData, timeoutMs / 1000.0)
        req.setHTTPMethod(method)
        headers.forEach { (k, v) -> req.setValue(v, forHTTPHeaderField = k) }
        if (body != null) {
            req.setValue("application/json; charset=utf-8", forHTTPHeaderField = "Content-Type")
            req.setHTTPBody(body.encodeToByteArray().toNSData())
        }
        return suspendCancellableCoroutine { cont ->
            val task = session.dataTaskWithRequest(req, completionHandler = { data: NSData?, response: NSURLResponse?, error: NSError? ->
                val http = response as? NSHTTPURLResponse
                val result: LanResult<HttpReply> = when {
                    error != null || http == null -> LanResult.Err(HUB_UNREACHABLE)
                    http.statusCode in 300L..399L -> LanResult.Err(HUB_UNREACHABLE)
                    else -> {
                        val bytes = data?.nsBytes() ?: ByteArray(0)
                        if (bytes.size > LanWireLimits.MAX_REPLY_BYTES) LanResult.Err(HUB_UNREACHABLE)
                        else LanResult.Ok(HttpReply(http.statusCode.toInt(), bytes.decodeToString()))
                    }
                }
                if (cont.isActive) cont.resume(result)
            })
            cont.invokeOnCancellation { task.cancel() }
            task.resume()
        }
    }
}

/** سقفِ جوابی که گوشی می‌خوانَد. */
internal object LanWireLimits {
    const val MAX_REPLY_BYTES = 256 * 1024
}

/**
 * نشانیِ IPv4ِ وای‌فایِ آیفون (`en0`) از `getifaddrs`؛ اگر نبود، اولین
 * نشانیِ خصوصیِ دیگر.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun phoneLanIp(): String? = memScoped {
    val head = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(head.ptr) != 0) return@memScoped null
    val found = mutableListOf<Pair<String, String>>()
    try {
        var cur = head.value
        while (cur != null) {
            val ifa = cur.pointed
            val addr = ifa.ifa_addr
            if (addr != null && addr.pointed.sa_family.toInt() == AF_INET) {
                val sin = addr.reinterpret<sockaddr_in>().pointed
                // `s_addr` به ترتیبِ شبکه است: بایتِ اولِ حافظه بخشِ اولِ نشانی.
                val v = sin.sin_addr.s_addr.toLong()
                val text = listOf(v and 0xFF, (v shr 8) and 0xFF, (v shr 16) and 0xFF, (v shr 24) and 0xFF)
                    .joinToString(".")
                val name = ifa.ifa_name?.toKString().orEmpty()
                if (HostAddress.isPrivate(text)) found += name to text
            }
            cur = ifa.ifa_next
        }
    } finally {
        freeifaddrs(head.value)
    }
    found.firstOrNull { it.first == "en0" }?.second ?: found.firstOrNull()?.second
}
