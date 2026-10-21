package com.afghanjama.util

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * «چه کسی» برای حسابرسی، **وقتی کسی که پشتِ دستگاه نشسته نیست.**
 *
 * **چرا لازم شد (نقدِ ۱۱).** دفترِ حسابرسی و تاریخچهٔ مرحله‌ها «چه کسی» را از
 * [CurrentUser] می‌خوانند — همان کسی که روی کامپیوتر وارد شده. وقتی صاحب‌کار
 * از گوشی‌اش درخواستی را تأیید می‌کند، کامپیوتر همان کار را انجام می‌دهد، ولی
 * کسی که پشتِ کامپیوتر نشسته (شاید فروشنده) آن را نکرده. بی این، دفتر
 * می‌گفت «فروشنده تأیید کرد».
 *
 * فرمانِ گوشی درونِ `withContext(AuditActor(...))` اجرا می‌شود و هر سه
 * نویسندهٔ «چه کسی» در `Repo` اول همین را می‌پرسند. بیرون از آن، همان
 * [CurrentUser] است و هیچ چیزی عوض نمی‌شود.
 */
class AuditActor(val name: String, val role: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AuditActor>
}

/** نام و نقشِ کسی که این کار را کرده: [AuditActor] اگر هست، وگرنه [CurrentUser]. */
suspend fun auditWho(): Pair<String, String> =
    currentCoroutineContext()[AuditActor]?.let { it.name to it.role } ?: (CurrentUser.name to CurrentUser.role)
