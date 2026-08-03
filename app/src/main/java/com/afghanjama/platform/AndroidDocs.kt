package com.afghanjama.platform

import android.content.Context
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.pdf.AndroidTextMeasurer
import com.afghanjama.pdf.PartyStatementPdf
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.StatementPdf
import com.afghanjama.pdf.PdfKit
import com.afghanjama.pdf.SheetDoc
import com.afghanjama.pdf.SheetPdfAndroid
import com.afghanjama.pdf.TextMeasurer
import com.afghanjama.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [Docs] روی اندروید.
 *
 * فونت‌ها یک بار خوانده می‌شوند و می‌مانند: `ResourcesCompat.getFont`
 * ارزان نیست و مترِ متن در مسیرِ چیدمان **برای هر سطر** صدا زده می‌شود.
 */
class AndroidDocs(private val ctx: Context) : Docs {

    private val fonts: PdfKit.Fonts by lazy { PdfKit.fonts(ctx) }

    override val measurer: TextMeasurer by lazy { AndroidTextMeasurer(fonts) }

    override suspend fun share(doc: SheetDoc, fileName: String, title: String) {
        // ساختِ PDF دیسک می‌نویسد و روی نخِ اصلی رابط را می‌خشکاند —
        // روی فاکتورِ چندبرگه‌ای دیده می‌شود.
        val file = withContext(Dispatchers.IO) {
            SheetPdfAndroid.write(ctx, doc, fileName)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }

    // این دو عمداً از `PdfKit` می‌آیند نه از چیدمانِ مشترک — تا کاغذی که
    // کارگاه امروز چاپ می‌کند بی‌تغییر بماند. دلیلش در خودِ `Docs` نوشته شده.
    override suspend fun statement(data: StatementData, fileName: String, title: String) {
        val file = withContext(Dispatchers.IO) {
            StatementPdf.create(ctx, data, fileName = fileName)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }

    override suspend fun partyStatement(
        partyType: String,
        partyName: String,
        net: Long,
        entries: List<LedgerEntry>,
        title: String
    ) {
        val file = withContext(Dispatchers.IO) {
            PartyStatementPdf.create(ctx, partyType, partyName, net, entries)
        }
        ShareUtil.shareFile(ctx, file, "application/pdf", title)
    }
}
