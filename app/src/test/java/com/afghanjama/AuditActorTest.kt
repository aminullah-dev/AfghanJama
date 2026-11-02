package com.afghanjama

import com.afghanjama.util.AuditActor
import com.afghanjama.util.CurrentUser
import com.afghanjama.util.auditWho
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * «چه کسی» برای دفترِ حسابرسی (نقدِ ۱۱): وقتی صاحب‌کار از گوشی تأیید می‌کند،
 * دفتر باید نامِ او را بنویسد، نه کسی را که پشتِ کامپیوتر وارد شده.
 */
class AuditActorTest {

    @Before
    fun setUp() = CurrentUser.clear()

    @After
    fun tearDown() = CurrentUser.clear()

    @Test
    fun `without an AuditActor the signed-in user is used`() = runBlocking {
        CurrentUser.set("Seller", "SELLER")
        assertEquals("Seller" to "SELLER", auditWho())
    }

    @Test
    fun `an AuditActor in the context overrides the signed-in user`() = runBlocking {
        CurrentUser.set("Seller", "SELLER")
        val who = withContext(AuditActor("Owner", "OWNER")) { auditWho() }
        assertEquals("Owner" to "OWNER", who)
    }

    @Test
    fun `the override ends when the block ends`() = runBlocking {
        CurrentUser.set("Seller", "SELLER")
        withContext(AuditActor("Owner", "OWNER")) { auditWho() }
        assertEquals("Seller" to "SELLER", auditWho())
    }

    @Test
    fun `nobody signed in and no actor gives empty strings`() = runBlocking {
        assertEquals("" to "", auditWho())
    }

    @Test
    fun `CurrentUser set trims the name and clear empties both fields`() {
        CurrentUser.set("  Ali  ", "OWNER")
        assertEquals("Ali", CurrentUser.name)
        assertEquals("OWNER", CurrentUser.role)
        CurrentUser.clear()
        assertEquals("", CurrentUser.name)
        assertEquals("", CurrentUser.role)
    }
}
