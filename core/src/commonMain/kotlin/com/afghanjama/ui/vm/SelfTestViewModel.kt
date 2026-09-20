package com.afghanjama.ui.vm

import com.afghanjama.util.nowMillis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Accounts
import com.afghanjama.data.repo.Repo
import com.afghanjama.selftest.CashPath
import com.afghanjama.selftest.CheckResult
import com.afghanjama.selftest.CheckSink
import com.afghanjama.selftest.CheckStatus
import com.afghanjama.selftest.checkCashOutflowPolicy
import com.afghanjama.selftest.checkCustomerLedger
import com.afghanjama.selftest.checkJalali
import com.afghanjama.selftest.checkMoneySplit
import com.afghanjama.selftest.checkMultiLineInvoice
import com.afghanjama.selftest.checkBackupArchive
import com.afghanjama.selftest.checkBreakSchedule
import com.afghanjama.selftest.checkCashFlow
import com.afghanjama.selftest.checkInvoiceTotals
import com.afghanjama.selftest.checkDiscountMath
import com.afghanjama.selftest.checkOrderCycle
import com.afghanjama.selftest.checkPartialReview
import com.afghanjama.selftest.checkPaperGeometry
import com.afghanjama.selftest.checkSalaryAdvance
import com.afghanjama.selftest.checkResetPlan
import com.afghanjama.selftest.checkRestoreVerdict
import com.afghanjama.selftest.FolderRow
import com.afghanjama.selftest.FolderSummary
import com.afghanjama.selftest.checkShortage
import com.afghanjama.selftest.FcDraw
import com.afghanjama.selftest.FcItem
import com.afghanjama.selftest.FcOut
import com.afghanjama.selftest.checkStockFolders
import com.afghanjama.selftest.MgInvoice
import com.afghanjama.selftest.MgLine
import com.afghanjama.selftest.StRow
import com.afghanjama.selftest.checkMargin
import com.afghanjama.selftest.checkStatement
import com.afghanjama.selftest.checkStockForecast
import com.afghanjama.selftest.checkWorkSummary
import com.afghanjama.selftest.checkWorkerName
import com.afghanjama.selftest.checkStockValuation
import com.afghanjama.selftest.checkTailorAttribution
import com.afghanjama.selftest.PaperSpec
import com.afghanjama.data.CashPolicy
import com.afghanjama.data.DB_VERSION
import com.afghanjama.data.PartialFlow
import com.afghanjama.data.ResetPlan
import com.afghanjama.data.StockFolders
import com.afghanjama.data.Margin
import com.afghanjama.pdf.StatementData
import com.afghanjama.pdf.StatementRow
import com.afghanjama.data.StockForecast
import com.afghanjama.pdf.Paper
import com.afghanjama.util.BackupArchive
import com.afghanjama.pdf.columnWidths
import com.afghanjama.pdf.invoiceColumns
import com.afghanjama.ui.format.PersianDate
import com.afghanjama.ui.format.bareWorkerName
import com.afghanjama.work.BreakSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * از `:app` به `:core` آمد — و هزینه‌اش یک خطِ ایمپورت بود.
 *
 * نقشه می‌گفت این ViewModel به `BackupArchive` و `BreakReminderWorker`
 * گره خورده و نمی‌آید. وقتی شمرده شد، هیچ‌کدام درست نبود:
 *
 *   • `BackupArchive` مدت‌هاست در `:core` است.
 *   • `BreakReminderWorker.delayUntilNext` فقط یک پوستهٔ سازگاری است؛
 *     خودِ حساب در `BreakSchedule` است و آن هم در `:core`. یعنی این
 *     ViewModel هرگز به WorkManager وابسته نبود، فقط از راهِ اسمِ آن
 *     صدایش می‌زد.
 *
 * ارزشش این است که فهرستِ تحویل (`DELIVERY.md`) می‌گوید پیش از دادنِ
 * نسخه باید «خودآزمایی و سلامتِ داده» اجرا شود و همه سبز باشد — و تا
 * امروز این روی ویندوز اصلاً ممکن نبود.
 */
data class SelfTestUi(
    val running: Boolean = false,
    val results: List<CheckResult> = emptyList(),
    val ranAt: Long = 0L
) {
    val passed: Int get() = results.count { it.status == CheckStatus.PASS }
    val failed: Int get() = results.count { it.status == CheckStatus.FAIL }
    val skipped: Int get() = results.count { it.status == CheckStatus.SKIP }
    val allGood: Boolean get() = results.isNotEmpty() && failed == 0
}

/**
 * خودآزماییِ اپ روی همین گوشی و همین داده.
 *
 * دو دسته بررسی انجام می‌شود:
 *  - ریاضیِ خالص (تفکیکِ پول، دفترِ مشتری، انتسابِ برگشت، تقویم) که به
 *    داده کاری ندارد و همیشه یک جواب دارد.
 *  - وارسیِ دادهٔ واقعیِ کارگاه، **فقط خواندنی**. هیچ سفارش، فروش یا سندِ
 *    آزمایشی ساخته نمی‌شود؛ اجرا کردنش روی دفترِ واقعی بی‌خطر است.
 */
class SelfTestViewModel(private val repo: Repo) : ViewModel() {

    private val _ui = MutableStateFlow(SelfTestUi())
    val ui: StateFlow<SelfTestUi> = _ui.asStateFlow()

    fun run() = viewModelScope.launch {
        _ui.value = SelfTestUi(running = true)
        // `Dispatchers.IO` عمداً نیست: تا coroutines 1.8 روی
        // Kotlin/Native وجود ندارد و کدِ مشترک باید برای iOS هم ساخته
        // شود. این بررسی‌ها هم محاسبه‌اند نه ورودی/خروجیِ مسدودکننده،
        // پس `Default` جای درستشان است.
        val results = withContext(Dispatchers.Default) {
            buildList {
                addAll(checkMoneySplit())
                addAll(checkCustomerLedger())
                addAll(checkTailorAttribution())
                addAll(checkMultiLineInvoice())
                addAll(checkStockValuation())
                addAll(checkInvoiceTotals())
                addAll(checkShortage())
                addAll(checkDiscountMath())
                addAll(checkOrderCycle())
                addAll(
                    checkPartialReview(
                        readyToSend = { qty, sewn, inReview, stored ->
                            PartialFlow.readyToSend(qty, sewn, inReview, stored)
                        },
                        depositValue = { qty, stored, batch, fixed, sewnCost, batches, storedCost ->
                            PartialFlow.depositValue(
                                qty = qty,
                                stored = stored,
                                batch = batch,
                                fixedCost = fixed,
                                sewnCost = sewnCost,
                                batches = batches.map {
                                    PartialFlow.Sewn(it.qty, it.unitWage, it.doneAt)
                                },
                                storedCost = storedCost
                            )
                        }
                    )
                )
                addAll(checkSalaryAdvance())
                addAll(checkWorkerName { it.bareWorkerName() })
                addAll(checkWorkSummary())
                addAll(
                    checkStatement(
                        totals = { rows ->
                            val d = StatementData("x", rows = rows.map { StatementRow("", "", it.debit, it.credit) })
                            Triple(d.totalDebit, d.totalCredit, d.balance)
                        },
                        runningBalances = { rows ->
                            var acc = 0L
                            rows.map { acc += it.debit - it.credit; acc }
                        }
                    )
                )
                addAll(
                    checkMargin(
                        line = { c, p, q ->
                            val l = Margin.Line(c, p, q)
                            MgLine(l.profit, l.percent, l.losing, l.unknownCost)
                        },
                        invoice = { rows, disc ->
                            val inv = Margin.Invoice(rows.map { Margin.Line(it.first, it.second, it.third) }, disc)
                            MgInvoice(
                                inv.costTotal, inv.revenue, inv.profit,
                                inv.percent, inv.losing, inv.hasUnknownCost
                            )
                        }
                    )
                )
                addAll(
                    checkStockForecast(
                        warnDays = StockForecast.WARN_DAYS,
                        forecast = { items, draws, w ->
                            StockForecast.forecast(
                                items.map { StockForecast.Item(it.name, it.unit, it.amount, it.minLevel) },
                                draws.map { StockForecast.Draw(it.name, it.unit, it.qty, it.atDay) },
                                w
                            ).map { FcOut(it.name, it.perDay, it.daysLeft, it.belowMin, it.urgent) }
                        },
                        needsAttention = { list ->
                            StockForecast.needsAttention(
                                list.map {
                                    StockForecast.Forecast(it.name, "", 0.0, it.perDay, it.daysLeft, it.belowMin)
                                }
                            ).map { FcOut(it.name, it.perDay, it.daysLeft, it.belowMin, it.urgent) }
                        }
                    )
                )
                addAll(
                    checkStockFolders(
                        uncategorised = StockFolders.UNCATEGORISED,
                        folderOf = { n, m -> StockFolders.folderOf(n, m) },
                        folders = { rows, m ->
                            StockFolders.folders(
                                rows.map { StockFolders.StockRow(it.name, it.size, it.qty) }, m
                            ).map { FolderSummary(it.name, it.designs, it.totalQty) }
                        },
                        itemsOf = { folder, rows, m ->
                            StockFolders.itemsOf(
                                folder, rows.map { StockFolders.StockRow(it.name, it.size, it.qty) }, m
                            ).map { FolderRow(it.name, it.size, it.qty) }
                        }
                    )
                )
                addAll(
                    checkRestoreVerdict(
                        verdict = { o, i, fv, av -> BackupArchive.verdict(o, i, fv, av) },
                        labelOf = { (it as BackupArchive.Verdict).name },
                        schemaVersion = DB_VERSION
                    )
                )
                addAll(
                    checkCashFlow(
                        internalMoveCategory = CashPolicy.INTERNAL_MOVE,
                        isInternal = { CashPolicy.isInternalMove(it) }
                    )
                )
                addAll(
                    checkCashOutflowPolicy(
                        canSpend = { balance, amount -> CashPolicy.canSpend(balance, amount) },
                        isCashSource = { CashPolicy.isCashSource(it) },
                        paths = CashPolicy.OUTFLOWS.map { (name, guard, reports) ->
                            CashPath(name, guard, reports)
                        }
                    )
                )
                addAll(
                    checkBackupArchive(
                        safePhotoName = { BackupArchive.safePhotoName(it) },
                        detect = { BackupArchive.detect(it).name }
                    )
                )
                addAll(
                    checkPaperGeometry(
                        Paper.ALL.map { p ->
                            val cols = invoiceColumns(p)
                            PaperSpec(
                                label = p.label,
                                contentW = p.contentW,
                                cellTextSize = p.cellTextSize,
                                columnTitles = cols.titles,
                                columnWidths = columnWidths(p, cols.weights)
                            )
                        }
                    )
                )
                addAll(
                    checkBreakSchedule { h, m, now ->
                        BreakSchedule.delayUntilNext(h, m, now)
                    }
                )
                addAll(
                    checkJalali(
                        toJalali = { millis ->
                            val j = PersianDate.todayJalali(millis)
                            Triple(j[0], j[1], j[2])
                        },
                        atMillis = { y, m, d -> PersianDate.startOfJalaliDay(y, m, d) }
                    )
                )
                addAll(auditLiveData())
            }
        }
        _ui.value = SelfTestUi(running = false, results = results, ranAt = nowMillis())
    }

    /** وارسیِ دفترِ واقعی — هیچ نوشتنی در کار نیست. */
    private suspend fun auditLiveData(): List<CheckResult> {
        val s = CheckSink("دفترِ واقعیِ کارگاه")

        // ۱) هر سندِ ژورنال باید خودش تراز باشد
        val totals = repo.auditEntryTotals()
        if (totals.isEmpty()) {
            s.skip("هر سند تراز است", "هنوز سندی ثبت نشده")
        } else {
            val broken = totals.filter { it.debit != it.credit }
            s.isTrue(
                "هر سند تراز است",
                broken.isEmpty(),
                "${broken.size} سندِ ناتراز، اولی #${broken.firstOrNull()?.entryId}: " +
                    "بدهکار ${broken.firstOrNull()?.debit} ≠ بستانکار ${broken.firstOrNull()?.credit}",
                "${totals.size} سند"
            )
        }

        // ۲) کلِ دفتر تراز است
        val balances = repo.auditAccountBalances()
        val td = balances.sumOf { it.debit }
        val tc = balances.sumOf { it.credit }
        if (balances.isEmpty()) s.skip("کلِ دفتر تراز است", "هنوز حسابی حرکت نکرده")
        else s.eq("کلِ دفتر تراز است", td, tc, "بدهکار و بستانکار هر دو ${td} ؋")

        // ۳) حساب‌هایی که نباید در سمتِ اشتباه بنشینند
        val byAccount = balances.associateBy { it.account }
        // هر سه صندوقِ نقد اینجا می‌آیند. پیش‌تر فقط «صندوق» بود، یعنی ماندهٔ
        // منفیِ بانک یا صندوقِ فایده هرگز گزارش نمی‌شد — همان جایی که
        // نگهبانِ موجودی هم غایب بود. دو اشکال که همدیگر را پنهان می‌کردند.
        val assets = listOf(
            Accounts.CASH to "صندوق",
            Accounts.BANK to "بانک",
            Accounts.PROFIT_BOX to "صندوق فایده",
            Accounts.RECEIVABLE to "طلب از مشتریان",
            // پیش‌پرداختِ کارکنان عمداً اینجا نیست. پرداختِ حقوق اکنون
            // تهاترش می‌کند، ولی این حساب سرجمعِ همهٔ کارکنان و بازرسان است
            // و کسر بر مبنای ماندهٔ **هر کارمند** بریده می‌شود؛ پس ماندهٔ
            // منفیِ سرجمع در حالت‌های نامتعارف می‌تواند واقعی باشد و هشدارِ
            // بی‌مورد اعتمادِ کاربر به این دکمه را از بین می‌برد.
            Accounts.MATERIALS to "موجودی مواد",
            Accounts.FINISHED to "موجودی محصول",
            Accounts.WIP to "کار در جریان"
        )
        val wrongSide = assets.filter { (code, _) -> (byAccount[code]?.net ?: 0L) < 0L }
        s.isTrue(
            "هیچ دارایی ماندهٔ منفی ندارد",
            wrongSide.isEmpty(),
            wrongSide.joinToString("، ") { (code, label) -> "$label: ${byAccount[code]?.net} ؋" },
            "${assets.size} حساب بررسی شد"
        )

        // ۴) سطرهای «اعمال بیعانه» باید خنثی باشند (اشکالِ اصلاح‌شده)
        val ledger = repo.auditLedgerEntries()
        val prepayRows = ledger.filter { it.refType == "PREPAY_APPLIED" }
        if (prepayRows.isEmpty()) {
            s.skip("اعمالِ بیعانه ماندهٔ حساب را تکان نمی‌دهد", "هنوز بیعانه‌ای اعمال نشده")
        } else {
            val lopsided = prepayRows.filter { it.debit != it.credit }
            s.isTrue(
                "اعمالِ بیعانه ماندهٔ حساب را تکان نمی‌دهد",
                lopsided.isEmpty(),
                "${lopsided.size} سطر یک‌طرفه مانده — مشتری دو بار بیعانه بدهکار شده. " +
                    "اگر این پیغام را می‌بینید یعنی مهاجرت اجرا نشده؛ خبر بدهید.",
                "${prepayRows.size} سطر"
            )
        }

        // ۵) هیچ سطرِ دفترِ کل نباید هم‌زمان بدهکار و بستانکارِ نابرابر و منفی داشته باشد
        val negative = ledger.filter { it.debit < 0 || it.credit < 0 }
        s.isTrue(
            "هیچ سطرِ دفتر مبلغِ منفی ندارد",
            negative.isEmpty(),
            "${negative.size} سطرِ منفی، اولی: ${negative.firstOrNull()?.refType}",
            "${ledger.size} سطر"
        )

        // ۶) حسابِ «موجودی محصول» باید دقیقاً برابرِ ارزشِ واقعیِ انبار باشد
        val stockValue = repo.auditFinishedStock().sumOf { it.totalValue }
        val finishedAccount = byAccount[Accounts.FINISHED]?.net ?: 0L
        if (finishedAccount == 0L && stockValue == 0L) {
            s.skip("حسابِ موجودی محصول = ارزشِ واقعیِ انبار", "هنوز محصولی وارد انبار نشده")
        } else {
            // اختلاف مستقیم گفته می‌شود، چون «انتظار/دیده شد» تنها
            // نمی‌گوید چقدر فاصله هست. اختلافِ ثابتی که با کارِ تازه
            // بزرگ نمی‌شود، معمولاً ته‌ماندهٔ دادهٔ قدیمی است: تا نسخهٔ
            // ۵۰ ارزشِ انبار «تعداد × میانگینِ گردشده» بود و ته‌ماندهٔ
            // تقسیم از سمتِ انبار می‌افتاد در حالی که دفتر مبلغِ کامل را
            // داشت. با «پاک کردنِ داده» پیش از تحویل از بین می‌رود.
            s.isTrue(
                "حسابِ موجودی محصول = ارزشِ واقعیِ انبار",
                stockValue == finishedAccount,
                "اختلافِ ${finishedAccount - stockValue} ؋ — " +
                    "دفتر ${finishedAccount} ؋ ولی انبار ${stockValue} ؋",
                "هر دو ${stockValue} ؋"
            )
        }

        // ۶ب) حسابِ «موجودی مواد» باید برابرِ ارزشِ واقعیِ انبار باشد.
        // این قاعده تا امروز سنجیده نمی‌شد و همان‌جا بود که حسابِ مواد
        // با هر خریدِ میانیِ تازه از انبار جدا می‌افتاد.
        val materials = repo.auditMaterialStock()
        val materialValue = materials.sumOf { it.amount * it.avgPrice }
        val materialAccount = byAccount[Accounts.MATERIALS]?.net ?: 0L
        if (materials.isEmpty() && materialAccount == 0L) {
            s.skip("حسابِ موجودی مواد = ارزشِ واقعیِ انبار", "هنوز موادی وارد نشده")
        } else {
            // مقدارِ مواد اعشاری است، پس اختلافِ کمترِ از یک افغانی طبیعی
            // است و به‌ازای هر ردیف یک افغانی روداری داده می‌شود.
            val tolerance = 1L + materials.size
            val drift = materialAccount - materialValue.toLong()
            s.isTrue(
                "حسابِ موجودی مواد = ارزشِ واقعیِ انبار",
                kotlin.math.abs(drift) <= tolerance,
                "اختلافِ ${drift} ؋ — حساب ${materialAccount}، انبار ${materialValue.toLong()}",
                "هر دو حدودِ ${materialAccount} ؋ (${materials.size} ردیف)"
            )
        }

        // ۶ج) هیچ ردیفِ موادی نباید مقدار یا قیمتِ منفی داشته باشد
        val badMaterial = materials.filter { it.amount < 0.0 || it.avgPrice < 0.0 }
        s.isTrue(
            "هیچ ردیفِ موادی مقدار یا قیمتِ منفی ندارد",
            badMaterial.isEmpty(),
            badMaterial.joinToString("، ") { "${it.name}: ${it.amount} × ${it.avgPrice}" }
        )

        // ۷) کسریِ انبار: خودِ منفی‌بودن اشکال نیست — گزارش می‌شود تا کارگاه
        // بداند چه چیزی را باید وارد کند. اشکال آنجاست که ارزشِ ردیفِ کسری
        // با برآوردش نخواند، چون همان یعنی حسابِ موجودی کج شده.
        val allFinished = repo.auditFinishedStock()
        val short = allFinished.filter { it.qty < 0 }
        if (short.isEmpty()) {
            s.pass("هیچ طرحی کسری ندارد", "${allFinished.size} ردیفِ انبار")
        } else {
            s.pass(
                "کسری‌های ثبت‌شده",
                short.joinToString("، ") { "${it.name}: ${-it.qty} عدد" }
            )
        }
        val brokenShort = short.filter { it.totalValue != it.qty * it.avgCost }
        s.isTrue(
            "ارزشِ هر ردیفِ کسری با برآوردش می‌خواند",
            brokenShort.isEmpty(),
            brokenShort.joinToString("، ") {
                "${it.name}: ارزش ${it.totalValue} ولی ${it.qty}×${it.avgCost}"
            }
        )
        val negMaterial = repo.auditMaterialStock().filter { it.amount < 0.0 }
        s.isTrue(
            "موجودیِ مواد منفی نیست",
            negMaterial.isEmpty(),
            negMaterial.joinToString("، ") { "${it.name}: ${it.amount}" }
        )

        // ۸) تطبیقِ جابه‌جاییِ داخلی: سندِ ژورنال با سطرِ صندوق
        //
        // همین تطبیق بود که غایب بودنش گذاشت سودِ هر فروش در گزارشِ «جریان
        // نقد» هزینه شمرده شود: ژورنال درست بود (`PROFIT_MOVE` سندِ خودش را
        // داشت) ولی سطرهای صندوق بی‌برچسب می‌ماندند و هیچ‌کس این دو را با
        // هم نمی‌سنجید.
        val moveRows = repo.auditTransactions()
            .filter { CashPolicy.isInternalMove(it.category) }
        val movedOut = moveRows.filter { it.type == "OUT" }.sumOf { it.amount }
        val movedIn = moveRows.filter { it.type == "IN" }.sumOf { it.amount }
        if (moveRows.isEmpty()) {
            s.skip("جابه‌جاییِ داخلی جفت‌به‌جفت است", "هنوز انتقالِ داخلی‌ای ثبت نشده")
        } else {
            s.isTrue(
                "جابه‌جاییِ داخلی جفت‌به‌جفت است",
                movedOut == movedIn,
                "خروج ${movedOut} ؋ ولی ورود ${movedIn} ؋ — اختلاف " +
                    "${movedOut - movedIn} ؋. محتمل‌ترین دلیل: یک سمتِ یک " +
                    "انتقال از فهرستِ تراکنش‌ها حذف شده. صندوق‌ها را با " +
                    "دفتر تطبیق بدهید.",
                "${moveRows.size} سطر، هر سمت ${movedOut} ؋"
            )
        }

        return s.results + checkResetPlan(
            clear = ResetPlan.CLEAR,
            keep = ResetPlan.KEEP,
            actualTables = runCatching { repo.tableNames() }.getOrDefault(emptyList())
        )
    }
}
