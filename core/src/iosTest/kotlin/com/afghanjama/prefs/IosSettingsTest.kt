package com.afghanjama.prefs

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.Foundation.NSUserDefaults

/**
 * تنظیماتِ آیفون روی `NSUserDefaults`.
 *
 * هر آزمون روی یک «سوییتِ» جدا کار می‌کند، نه `standardUserDefaults`،
 * تا به تنظیماتِ واقعیِ برنامه دست نخورد. قاعده‌هایی که می‌سنجیم همان‌هایی
 * است که توضیحِ `IosSettings` می‌گوید: کلیدِ نبوده مقدارِ پیش‌فرض را
 * می‌دهد (حتی برای `true`)، `file` فضای‌نام است، و `remove` به پیش‌فرض
 * برمی‌گرداند.
 */
class IosSettingsTest {

    private val suite = "com.afghanjama.test.IosSettingsTest"
    private lateinit var settings: IosSettings

    @BeforeTest
    fun setUp() {
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.removePersistentDomainForName(suite)
        settings = IosSettings(defaults)
    }

    @AfterTest
    fun tearDown() {
        NSUserDefaults(suiteName = suite).removePersistentDomainForName(suite)
    }

    @Test
    fun missingKeysReturnTheDefault() {
        assertEquals("x", settings.getString("f", "k", "x"))
        assertEquals("", settings.getString("f", "k"))
        assertEquals(42L, settings.getLong("f", "k", 42L))
    }

    @Test
    fun missingBooleanReturnsDefaultTrueAndFalse() {
        assertTrue(settings.getBoolean("f", "k", true))
        assertFalse(settings.getBoolean("f", "k", false))
    }

    @Test
    fun storedFalseWinsOverDefaultTrue() {
        settings.putBoolean("f", "k", false)
        assertFalse(settings.getBoolean("f", "k", true))
    }

    @Test
    fun stringRoundTripKeepsDariText() {
        settings.putString("f", "k", "خیاط‌یار ۱۲۳")
        assertEquals("خیاط‌یار ۱۲۳", settings.getString("f", "k", "x"))
    }

    @Test
    fun longKeepsMillisecondTimestamps() {
        val ms = 1_793_000_000_000L
        settings.putLong("f", "k", ms)
        assertEquals(ms, settings.getLong("f", "k", 0L))
    }

    @Test
    fun storedZeroIsNotTreatedAsMissing() {
        settings.putLong("f", "k", 0L)
        assertEquals(0L, settings.getLong("f", "k", 99L))
    }

    @Test
    fun fileIsANamespace() {
        settings.putString("a", "k", "one")
        settings.putString("b", "k", "two")
        assertEquals("one", settings.getString("a", "k", "x"))
        assertEquals("two", settings.getString("b", "k", "x"))
    }

    @Test
    fun removeRestoresTheDefault() {
        settings.putString("f", "k", "v")
        settings.putBoolean("f", "b", false)
        settings.putLong("f", "n", 7L)
        settings.remove("f", "k")
        settings.remove("f", "b")
        settings.remove("f", "n")
        assertEquals("x", settings.getString("f", "k", "x"))
        assertTrue(settings.getBoolean("f", "b", true))
        assertEquals(9L, settings.getLong("f", "n", 9L))
    }
}
