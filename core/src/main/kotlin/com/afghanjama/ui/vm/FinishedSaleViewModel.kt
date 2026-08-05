package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.FinishedSale
import com.afghanjama.data.entities.FinishedStock
import com.afghanjama.data.StockFolders
import com.afghanjama.data.SalePolicy
import com.afghanjama.data.repo.Repo
import com.afghanjama.ui.format.fa
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FinishedSaleUi(
    val message: String? = null,
    val isError: Boolean = false,
    /** بیعانهٔ استفاده‌نشدهٔ مشتریِ انتخاب‌شده — ۰ یعنی ندارد. */
    val prepayOfCustomer: Long = 0
)

/** فروش جزئی از انبار محصول نهایی. */
class FinishedSaleViewModel(private val repo: Repo) : ViewModel() {

    /** جلوی ثبتِ دوباره با دو ضربهٔ سریع را می‌گیرد. */
    val busy = Busy()

    val items: StateFlow<List<FinishedStock>> =
        repo.observeFinishedStock()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSales: StateFlow<List<FinishedSale>> =
        repo.observeFinishedSales()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * نقشهٔ «نامِ طرح → دسته» — پوشهٔ هر کالای انبار از همین می‌آید.
     *
     * محصولی که از نظارت وارد انبار می‌شود نامِ طرحش را با خودش دارد، پس
     * بی هیچ کارِ دستی سرِ پوشهٔ درستش می‌نشیند.
     */
    private val categoryMap: StateFlow<Map<String, String>> =
        repo.observeDesignCategoryMap()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** پوشه‌های انبار با تعدادِ طرح و جمعِ موجودیِ هرکدام. */
    val folders: StateFlow<List<StockFolders.Folder>> =
        combine(items, categoryMap) { list, cat ->
            StockFolders.folders(list.map { it.toFolderRow() }, cat)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** کالاهای یک پوشه — مرتب بر اساسِ طرح و بعد سایز. */
    fun itemsOf(folder: String): List<FinishedStock> {
        val cat = categoryMap.value
        return items.value
            .filter { StockFolders.folderOf(it.name, cat) == folder }
            .sortedWith(compareBy({ it.name.trim() }, { it.size.trim() }))
    }

    private fun FinishedStock.toFolderRow() =
        StockFolders.StockRow(name = name, size = size, qty = qty)

    /** موجودیِ صندوق و بانک — برای انتخابِ محلِ پس‌دادنِ پولِ مرجوعی. */
    val wallet: StateFlow<Long> =
        repo.observeWalletBalance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val bank: StateFlow<Long> =
        repo.observeBankBalance()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val _ui = MutableStateFlow(FinishedSaleUi())
    val ui: StateFlow<FinishedSaleUi> = _ui

    fun clearMessage() = _ui.update { it.copy(message = null, isError = false) }

    /** با تایپِ نامِ مشتری، بیعانهٔ استفاده‌نشده‌اش را پیدا می‌کند. */
    fun lookupPrepay(customerName: String) = viewModelScope.launch {
        val p = if (customerName.isBlank()) 0L else repo.customerPrepayBalance(customerName)
        _ui.update { it.copy(prepayOfCustomer = p) }
    }

    /**
     * [receivedNow] نقدی که همین حالا گرفته می‌شود و [applyPrepay] بخشی از
     * بیعانهٔ قبلیِ مشتری که روی این فروش اعمال می‌شود. اگر هیچ‌کدام داده
     * نشود، مثلِ قبل کلِ مبلغ نقد فرض می‌شود.
     */
    fun sell(
        item: FinishedStock,
        qty: Int,
        unitPrice: Long,
        customerName: String,
        receivedNow: Long = -1L,
        applyPrepay: Long = 0L,
        discount: Long = 0L
    ) =
        viewModelScope.launch {
            busy.once {
                doSell(
                    item = item,
                    qty = qty,
                    unitPrice = unitPrice,
                    customerName = customerName,
                    receivedNow = receivedNow,
                    applyPrepay = applyPrepay,
                    discount = discount
                )
            }
        }

    private suspend fun doSell(
        item: FinishedStock,
        qty: Int,
        unitPrice: Long,
        customerName: String,
        receivedNow: Long = -1L,
        applyPrepay: Long = 0L,
        discount: Long = 0L
    ) {
            if (qty <= 0) {
                _ui.update { it.copy(message = "تعداد فروش را وارد کنید.", isError = true) }
                return
            }
            if (qty > item.qty && !SalePolicy.allowNegativeStock()) {
                _ui.update {
                    it.copy(
                        message = "تعداد فروش بیشتر از موجودی است. موجودی: ${item.qty.fa()} عدد",
                        isError = true
                    )
                }
                return
            }
            if (unitPrice <= 0) {
                _ui.update { it.copy(message = "قیمت هر عدد را وارد کنید.", isError = true) }
                return
            }
            val ok = repo.sellFinished(
                item, qty, unitPrice, customerName,
                receivedNow = receivedNow, applyPrepay = applyPrepay, discount = discount
            )
            _ui.update {
                if (ok) it.copy(
                    message = "فروش ثبت شد." +
                        (if (applyPrepay > 0) " بیعانه هم اعمال شد." else ""),
                    isError = false,
                    prepayOfCustomer = 0
                )
                else it.copy(message = "فروش ناموفق بود.", isError = true)
            }
        }

    /** عکسِ کالای انبار — یکی برای هر کالا. نامِ خالی یعنی برداشتنِ عکس. */
    fun setPhoto(
        item: FinishedStock,
        fileName: String,
        deleteFile: (String) -> Unit
    ) = viewModelScope.launch {
        repo.setStockPhoto(item, fileName, deleteFile)
    }

    /**
     * شمارشِ انبار: تعدادِ واقعیِ شمرده‌شده را می‌نشاند.
     *
     * برای عددهایی است که در انبار هستند ولی در واقعیت نیستند — مثلِ
     * عددهای خیالی که پیش از اصلاحِ جریانِ جزئی وارد انبار می‌شدند.
     * سندِ حسابداری‌اش را خودِ [Repo.adjustFinishedStock] می‌زند.
     */
    fun recount(item: FinishedStock, countedQty: Int, note: String = "") =
        viewModelScope.launch {
            busy.once {
                val changed = repo.adjustFinishedStock(
                    name = item.name,
                    size = item.size,
                    countedQty = countedQty,
                    note = note
                )
                _ui.update {
                    if (changed) it.copy(
                        message = "موجودی «${item.name}» به ${countedQty} عدد اصلاح شد.",
                        isError = false
                    ) else it.copy(
                        message = "چیزی برای اصلاح نبود — همین تعداد از قبل ثبت است.",
                        isError = true
                    )
                }
            }
        }

    /**
     * برگشت از فروش: کالا به انبار محصول برمی‌گردد و پولِ مشتری یا نقد
     * پس داده می‌شود یا به‌صورت بستانکاری روی حسابش می‌ماند.
     */
    fun returnSale(
        sale: FinishedSale,
        qty: Int,
        refundCash: Boolean,
        cashBox: String
    ) =
        viewModelScope.launch {
            busy.once {
                doReturnSale(
                    sale = sale,
                    qty = qty,
                    refundCash = refundCash,
                    cashBox = cashBox
                )
            }
        }

    private suspend fun doReturnSale(
        sale: FinishedSale,
        qty: Int,
        refundCash: Boolean,
        cashBox: String
    ) {
        if (qty <= 0 || qty > sale.returnableQty) {
            _ui.update {
                it.copy(
                    message = "تعداد برگشتی نامعتبر است. قابل برگشت: ${sale.returnableQty.fa()} عدد",
                    isError = true
                )
            }
            return
        }
        val ok = repo.recordSaleReturn(sale, qty, refundCash, cashBox)
        _ui.update {
            if (ok) it.copy(message = "برگشت از فروش ثبت و سندش صادر شد.", isError = false)
            else it.copy(
                message = if (refundCash) "موجودیِ صندوق برای پس‌دادنِ پول کافی نیست."
                else "ثبتِ برگشت ممکن نشد.",
                isError = true
            )
        }
    }
}
