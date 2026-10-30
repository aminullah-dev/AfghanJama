package com.afghanjama

import com.afghanjama.data.StockFolders
import com.afghanjama.data.StockFolders.StockRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * پوشه‌بندیِ انبارِ محصول: دسته از طرح می‌آید، نه از خودِ کالا.
 *
 * مرزهای مهم: فاصلهٔ اضافه در نام نباید کالا را از پوشه‌اش جدا کند، و
 * «دسته‌بندی‌نشده» همیشه آخرِ فهرست می‌آید.
 */
class StockFoldersTest {

    private val cats = mapOf("کت" to "مردانه", "پیراهن" to "مردانه", "لباس کودک" to "کودکانه")

    @Test
    fun `folderOf uses the design category`() {
        assertEquals("مردانه", StockFolders.folderOf("کت", cats))
    }

    @Test
    fun `folderOf trims the product name and the stored category`() {
        assertEquals("مردانه", StockFolders.folderOf("  کت ", cats))
        assertEquals("زنانه", StockFolders.folderOf("بلوز", mapOf("بلوز" to " زنانه ")))
    }

    @Test
    fun `unknown or blank category falls into uncategorised`() {
        assertEquals(StockFolders.UNCATEGORISED, StockFolders.folderOf("ناشناخته", cats))
        assertEquals(StockFolders.UNCATEGORISED, StockFolders.folderOf("بلوز", mapOf("بلوز" to "  ")))
    }

    @Test
    fun `categoryMap trims keys and values and the last duplicate wins`() {
        val m = StockFolders.categoryMap(
            listOf(" کت " to " مردانه ", "کت" to "رسمی")
        )
        assertEquals(mapOf("کت" to "رسمی"), m)
    }

    @Test
    fun `folders counts designs and sums quantity, uncategorised last`() {
        val items = listOf(
            StockRow("کت", "M", 3),
            StockRow("کت ", "L", 2),          // همان طرح با فاصلهٔ اضافه
            StockRow("پیراهن", "M", 5),
            StockRow("لباس کودک", "S", 4),
            StockRow("ناشناخته", "M", 1),
        )
        val f = StockFolders.folders(items, cats)
        val real = f.dropLast(1).map { it.name }
        assertEquals(setOf("کودکانه", "مردانه"), real.toSet())
        assertEquals(real.sorted(), real)   // پوشه‌های واقعی الفبایی
        assertEquals(StockFolders.UNCATEGORISED, f.last().name)
        val men = f.first { it.name == "مردانه" }
        assertEquals(2, men.designs)      // کت و پیراهن؛ «کت» با فاصله یکی شمرده می‌شود
        assertEquals(10, men.totalQty)
    }

    @Test
    fun `folders is empty for an empty stock`() {
        assertEquals(emptyList<StockFolders.Folder>(), StockFolders.folders(emptyList(), cats))
    }

    @Test
    fun `itemsOf filters by folder and sorts by name then size`() {
        val items = listOf(
            StockRow("پیراهن", "M", 5),
            StockRow("کت", "XL", 1),
            StockRow("کت", "L", 2),
            StockRow("لباس کودک", "S", 4),
        )
        val out = StockFolders.itemsOf("مردانه", items, cats)
        assertEquals(listOf("پیراهن" to "M", "کت" to "L", "کت" to "XL"), out.map { it.name to it.size })
    }
}
