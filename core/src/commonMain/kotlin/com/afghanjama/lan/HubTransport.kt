package com.afghanjama.lan

/**
 * یک رفت‌وبرگشتِ HTTP به کامپیوترِ کارگاه — تنها جایی که گوشیِ قراردادِ ۲
 * به شبکه دست می‌زند.
 *
 * دو قاعده در هر پیاده‌سازی، **پیش از هر اتصالی** ([workshopHostRefusal]):
 *
 * - فقط نشانیِ شبکهٔ کارگاه ([HostAddress.isPrivate]). loopback فقط برای
 *   آزمونِ دودی با پرچمِ جدا.
 * - **حواله دنبال نمی‌شود.** چیزی که روی نشانیِ کارگاه جوابِ ۳۰۲ بدهد،
 *   گوشی را به میزبانِ دیگری نمی‌فرستد.
 *
 * کامپیوتر و اندروید: `HttpURLConnection`. آیفون: `NSURLSession`.
 */
interface HubTransport {
    /**
     * [body] `null` یعنی GET. [timeoutMs] سقفِ خواندنِ جواب است —
     * انتظارِ «اجازه» ۳۲ ثانیه، بقیه هشت.
     */
    suspend fun exchange(
        host: String,
        port: Int,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
        timeoutMs: Int,
        connectTimeoutMs: Int = 6_000,
    ): LanResult<HttpReply>
}

class HttpReply(val status: Int, val body: String)

/** مشتریِ HTTPِ همین سکو. [allowLoopback] فقط برای آزمونِ دودی. */
expect fun hubTransport(allowLoopback: Boolean = false): HubTransport

/** پیامِ «به کامپیوتر نرسید» — هر شکستِ شبکه همین است، نه متنِ خامِ سیستم. */
const val HUB_UNREACHABLE =
    "به کامپیوترِ کارگاه نرسید. مطمئن شوید این گوشی و کامپیوتر روی یک وای‌فایِ کارگاه‌اند و " +
        "روی کامپیوتر «کارگاهِ وصل» روشن است."

/** `null` اگر [host] نشانیِ کارگاه است؛ وگرنه دلیلِ رد، پیش از هر اتصال. */
fun workshopHostRefusal(host: String, allowLoopback: Boolean): String? =
    if (HostAddress.isPrivate(host) || (allowLoopback && HostAddress.isLoopback(host))) null
    else HostAddress.NOT_WORKSHOP
