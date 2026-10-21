package com.afghanjama

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * نسخه و کامیتِ داخلِ برنامه — همان چیزی که «دربارهٔ خیاط‌یار» نشان می‌دهد.
 *
 * **خطری که این آزمون نگه می‌دارد:** تا ۱.۹.۰ سه ساختِ متفاوت هر سه
 * «۱.۹.۰» می‌گفتند. اگر `BuildInfo` روزی از `gradle.properties` جدا شود
 * (کسی عددی در فایلِ ساخت بنویسد، یا تسک از کار بیفتد و فایلِ کهنه بماند)،
 * برنامه دوباره نسخهٔ نادرست می‌گوید و هیچ ساختی نمی‌شکند.
 */
class AppVersionTest {

    private val props: Map<String, String> by lazy {
        // آزمونِ واحدِ `:app` در پوشهٔ همان ماژول اجرا می‌شود.
        val file = listOf(File("../gradle.properties"), File("gradle.properties"))
            .first { it.exists() }
        file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
            .associate { it.substringBefore("=").trim() to it.substringAfter("=").trim() }
    }

    @Test
    fun `BuildInfo is generated from gradle properties, not written by hand`() {
        assertEquals(props["appVersion"], BuildInfo.VERSION)
        assertEquals(props["appVersionCode"]?.toInt(), BuildInfo.CODE)
    }

    /**
     * `git rev-parse --short=7` **دست‌کم** هفت حرف است، نه دقیقاً هفت: اگر هفت
     * حرف در مخزن یکتا نباشد، گیت بیشتر می‌دهد. `{7}` آن روز ساختِ درست را
     * می‌شکست.
     */
    @Test
    fun `the commit is at least seven hex characters, or local without git`() {
        assertTrue(BuildInfo.COMMIT, Regex("[0-9a-f]{7,}|local").matches(BuildInfo.COMMIT))
    }

    @Test
    fun `the channel is one of the two, and release is never dirty`() {
        assertTrue(BuildInfo.CHANNEL, BuildInfo.CHANNEL in setOf(AppVersion.RELEASE, AppVersion.TEST))
        if (BuildInfo.CHANNEL == AppVersion.RELEASE) {
            assertFalse(BuildInfo.DIRTY)
            assertTrue(BuildInfo.COMMIT != "local")
        }
    }

    @Test
    fun `a release says its version plainly, a test build says it is a test`() {
        assertEquals("نسخهٔ ۱.۹.۱", AppVersion.title("1.9.1", AppVersion.RELEASE))
        assertEquals("نسخهٔ ۱.۹.۱، آزمایشی", AppVersion.title("1.9.1", AppVersion.TEST))
        // هر رشتهٔ ناشناخته آزمایشی است، نه رسمی.
        assertEquals("نسخهٔ ۱.۹.۱، آزمایشی", AppVersion.title("1.9.1", "dev"))
    }

    @Test
    fun `the commit stays latin so it can be matched against git`() {
        assertEquals("شمارهٔ ساخت ۱۵ · کامیت 0e109d9", AppVersion.detail(15, "0e109d9"))
        assertEquals("نسخهٔ ۱.۹.۱ · 0e109d9", AppVersion.short("1.9.1", "0e109d9", AppVersion.RELEASE))
    }

    @Test
    fun `the note says why a build is not a release`() {
        assertEquals(
            "ساختِ رسمی — از روی تگِ v1.9.1 ساخته شده.",
            AppVersion.channelNote("1.9.1", AppVersion.RELEASE, dirty = false)
        )
        assertTrue(AppVersion.channelNote("1.9.1", AppVersion.TEST, dirty = true).contains("تغییرِ ثبت‌نشده"))
        assertTrue(AppVersion.channelNote("1.9.1", AppVersion.TEST, dirty = false).contains("v1.9.1"))
    }

    @Test
    fun `the support line is latin and marks a dirty tree like git describe`() {
        assertEquals(
            "KhayatYar 1.9.1 (15) 0e109d9 release",
            AppVersion.supportLine("1.9.1", 15, "0e109d9", dirty = false, channel = AppVersion.RELEASE)
        )
        assertEquals(
            "KhayatYar 1.9.1 (15) 0e109d9-dirty test build",
            AppVersion.supportLine("1.9.1", 15, "0e109d9", dirty = true, channel = AppVersion.TEST)
        )
    }
}
