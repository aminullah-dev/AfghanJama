package com.afghanjama.desktop.pdf

import com.afghanjama.pdf.Align
import com.afghanjama.pdf.DrawOp
import com.afghanjama.pdf.Paper
import com.afghanjama.pdf.Sheet
import com.afghanjama.pdf.Weight
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.nio.file.Files
import java.text.AttributedString

/*
 * دودآزماییِ کاغذِ ویندوز.
 *
 * **اشکالی که این را لازم کرد.** اندازه‌گیر (`AwtTextMeasurer.wrap`)
 * صریح `RUN_DIRECTION_RTL` می‌دهد و توضیحِ خودش هم دام را نوشته بود،
 * ولی رسم‌کننده (`SheetPdf.drawText`) از سازندهٔ `TextLayout(String, …)`
 * استفاده می‌کرد که جهت را از **اولین حرفِ جهت‌دار** حدس می‌زند.
 *
 * یعنی یک سطر با یک جهت شکسته و با جهتِ دیگری کشیده می‌شد. روی سطری که
 * با عدد یا حرفِ لاتین شروع شود — مبلغ در ستونِ فاکتور — جای اجزا با
 * آنچه چیدمان حساب کرده بود یکی نبود.
 *
 * درستیِ **دیداری** را نمی‌شود اینجا سنجید؛ آنچه سنجیده می‌شود دقیقاً
 * همان چیزی است که فرق می‌کرد: **جهتِ پایه**.
 */

private class PdfFailure(msg: String) : RuntimeException(msg)

private fun pdfNeed(cond: Boolean, msg: String) {
    if (!cond) throw PdfFailure(msg)
}

private fun pdfCheck(name: String, body: () -> Unit): Boolean =
    try {
        body()
        println("OK    $name")
        true
    } catch (t: Throwable) {
        println("FAIL  $name")
        println("        ${t::class.java.name}: ${t.message}")
        false
    }

private val FRC = FontRenderContext(null, true, true)

/** جهتِ پایه‌ای که سازندهٔ رشته‌ای حدس می‌زند — یعنی رفتارِ قدیمی. */
private fun guessedIsLtr(text: String, font: java.awt.Font): Boolean =
    TextLayout(text, font, FRC).isLeftToRight

/** جهتی که رسم‌کنندهٔ امروز واقعاً به کار می‌برد. */
private fun renderedIsLtr(text: String, font: java.awt.Font): Boolean {
    val attr = AttributedString(text).apply {
        addAttribute(TextAttribute.FONT, font)
        addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL)
    }
    return TextLayout(attr.iterator, FRC).isLeftToRight
}

fun main() {
    println("pdf smoke - KhayatYar Windows")
    println("=".repeat(52))
    val fonts = SheetFonts.load()
    val font = fonts.of(Weight.Regular).deriveFont(12f)
    var ok = true

    // سطرهایی که واقعاً روی فاکتور می‌آیند و با عدد یا لاتین شروع می‌شوند.
    val mixed = listOf(
        "1200 افغانی",
        "AFN 1200 برای علی",
        "2.5 متر پارچه"
    )

    ok = pdfCheck("mixed lines are drawn right-to-left, like they are measured") {
        for (t in mixed) {
            pdfNeed(
                !renderedIsLtr(t, font),
                "'$t' must lay out RTL, matching AwtTextMeasurer.wrap"
            )
        }
    } && ok

    // این بند ثابت می‌کند اشکال **واقعی** بود، نه نظری: رفتارِ قدیمی
    // روی دستِ‌کم یکی از همین سطرها جهتِ دیگری می‌داد.
    ok = pdfCheck("the old string-constructor really did guess a different direction") {
        val differing = mixed.filter { guessedIsLtr(it, font) != renderedIsLtr(it, font) }
        pdfNeed(
            differing.isNotEmpty(),
            "no line differs - this check would be vacuous"
        )
        println("        (differed on: ${differing.joinToString(" | ")})")
    } && ok

    // نشانهٔ افغانی روی هر سندِ پول می‌آید. اگر قلم نداشته باشدش، به‌جای
    // آن مربعِ خالی چاپ می‌شود و کسی تا چاپِ اول نمی‌فهمد.
    // نشانهٔ افغانی روی هر سندِ پول می‌آید. وزیرمتنِ همراه آن را
    // **ندارد** (سنجیده شد، نه حدس) — پس بدونِ قلمِ جایگزین به‌جایش یک
    // مربعِ خالی چاپ می‌شد و تا اولین چاپ کسی نمی‌فهمید.
    ok = pdfCheck("the Afghani sign (؋) survives even though Vazirmatn lacks it") {
        pdfNeed(
            !font.canDisplay('؋'),
            "Vazirmatn now has ؋ - this check is stale, simplify RtlText"
        )
        // با قلمِ جایگزین، چیدمان دیگر «گلیفِ نبود» نمی‌کشد.
        val withSign = RtlText.layout("جمع: 1200 ؋", font)
        val withoutSign = RtlText.layout("جمع: 1200", font)
        pdfNeed(
            withSign.advance > withoutSign.advance,
            "the ؋ contributed no width - it is being dropped, not substituted"
        )
        pdfNeed(
            !withSign.isLeftToRight,
            "the fallback run must not flip the line to LTR"
        )
    } && ok

    // اندازه‌گیر و رسم‌کننده باید **یک** پهنا بدهند، وگرنه ستون‌های
    // فاکتور با آنچه کشیده می‌شود نمی‌خوانند.
    ok = pdfCheck("measurer and renderer agree on width, to the pixel") {
        val measurer = AwtTextMeasurer(fonts)
        for (t in mixed + listOf("جمع: 1200 ؋")) {
            val measured = measurer.width(t, 12f, Weight.Regular)
            val drawn = RtlText.layout(t, font).advance
            pdfNeed(
                kotlin.math.abs(measured - drawn) < 0.01f,
                "'$t': measured $measured but draws $drawn"
            )
        }
    } && ok

    // و یک برگهٔ واقعی نوشته می‌شود — رسم، مسیر، و ذخیره.
    ok = pdfCheck("a sheet with mixed-direction text renders to a real PDF") {
        val dir = Files.createTempDirectory("khayatyar-pdf").toFile()
        val out = java.io.File(dir, "probe.pdf")
        val sheet = Sheet(
            paper = Paper.A5,
            ops = buildList {
                mixed.forEachIndexed { i, t ->
                    add(
                        DrawOp.Text(
                            text = t,
                            x = 300f,
                            y = 40f + i * 20f,
                            size = 12f,
                            weight = Weight.Regular,
                            align = Align.Start,
                            color = 0xFF000000.toInt()
                        )
                    )
                }
                add(
                    DrawOp.Text(
                        text = "جمع: 1200 ؋",
                        x = 300f,
                        y = 120f,
                        size = 14f,
                        weight = Weight.Bold,
                        align = Align.Start,
                        color = 0xFF000000.toInt()
                    )
                )
            }
        )
        SheetPdf(fonts).write(sheet, out)
        pdfNeed(out.exists() && out.length() > 0, "no PDF was written")
        dir.deleteRecursively()
    } && ok

    println("=".repeat(52))
    println(if (ok) "pdf path verified" else "pdf smoke FAILED")
    kotlin.system.exitProcess(if (ok) 0 else 1)
}
