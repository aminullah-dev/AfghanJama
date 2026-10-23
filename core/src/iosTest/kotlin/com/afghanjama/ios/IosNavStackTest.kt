package com.afghanjama.ios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * پشتهٔ برگشتِ آیفون — منطقِ خالص، بی هیچ UI.
 *
 * هر آزمون یکی از قاعده‌های توضیحِ `IosNavStack` را می‌سنجد: `go` برای
 * همان صفحه چیزی نمی‌افزاید، `switchTab` پشته را خالی می‌کند، `replace`
 * پشته را بلند نمی‌کند و `back` از ریشه برنامه را نمی‌بندد.
 */
class IosNavStackTest {

    @Test
    fun startsOnGivenRouteWithNothingToGoBackTo() {
        val nav = IosNavStack("a")
        assertEquals("a", nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun goPushesAndBackPops() {
        val nav = IosNavStack("a")
        nav.go("b")
        assertEquals("b", nav.current)
        assertTrue(nav.canGoBack)
        nav.back()
        assertEquals("a", nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun goToCurrentRouteIsIgnored() {
        val nav = IosNavStack("a")
        nav.go("a")
        assertFalse(nav.canGoBack)
        nav.go("b")
        nav.go("b")
        nav.back()
        assertEquals("a", nav.current)
    }

    @Test
    fun backOnRootDoesNothing() {
        val nav = IosNavStack("a")
        nav.back()
        nav.back()
        assertEquals("a", nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun switchTabClearsTheStack() {
        val nav = IosNavStack("a")
        nav.go("b")
        nav.go("c")
        nav.switchTab("d")
        assertEquals("d", nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun replaceSwapsTopWithoutGrowingTheStack() {
        val nav = IosNavStack("a")
        nav.go("b")
        nav.replace("c")
        assertEquals("c", nav.current)
        nav.back()
        assertEquals("a", nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun replaceWithCurrentRouteIsIgnored() {
        val nav = IosNavStack("a")
        nav.go("b")
        nav.replace("b")
        nav.back()
        assertEquals("a", nav.current)
    }
}
