package com.afghanjama.desktop.pdf

import com.afghanjama.data.entities.CustomerPayment
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderFabric
import com.afghanjama.data.entities.OrderWorkItem
import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.ReceiptData
import com.afghanjama.pdf.ShopInfo
import com.afghanjama.pdf.invoiceSheets
import com.afghanjama.pdf.receiptSheet
import java.io.File

/**
 * سندهای چاپیِ ویندوز.
 *
 * هیچ چیدمانی اینجا نیست و نباید بیاید — چیدمان در `:core` است و روی
 * گوشی هم همان اجرا می‌شود. اینجا فقط سه کار انجام می‌شود: قلم‌ها خوانده
 * می‌شوند، متر ساخته می‌شود، و برگه روی کاغذ می‌رود.
 *
 * اگر روزی کسی خواست اینجا «فقط یک خط» به فاکتور اضافه کند، همان لحظه
 * دو تعریف از فاکتور به وجود می‌آید. جایش `:core` است.
 */
object DesktopDocs {

    private val fonts by lazy { SheetFonts.load() }
    private val measurer by lazy { AwtTextMeasurer(fonts) }
    private val engine by lazy { SheetPdf(fonts) }

    /** پوشهٔ سندهای چاپ‌شده، کنارِ دفترِ کارگاه. */
    fun outputDir(): File {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
        return File(File(base, "KhayatYar"), "اسناد").apply { mkdirs() }
    }

    fun receipt(
        data: ReceiptData,
        shop: ShopInfo,
        paper: Paper = Paper.ROLL80,
        target: File = File(outputDir(), "${data.number}.pdf")
    ): File = engine.write(receiptSheet(data, shop, measurer, paper), target)

    /** فاکتورِ سفارش — چندبرگه‌ای اگر اقلام زیاد باشند. */
    fun invoice(
        order: Order,
        fabrics: List<OrderFabric>,
        workItems: List<OrderWorkItem>,
        payments: List<CustomerPayment>,
        shop: ShopInfo,
        target: File = File(outputDir(), "فاکتور-${order.orderCode}.pdf")
    ): File = engine.write(
        invoiceSheets(order, fabrics, workItems, payments, shop, measurer),
        target
    )
}
