package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Order
import com.afghanjama.data.entities.OrderStatus
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class CuttingViewModel(private val repo: Repo) : ViewModel() {

    val ordersCutting: StateFlow<List<Order>> =
        repo.observeOrdersByStatus(OrderStatus.CUTTING.name)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * پیشنهادِ «مسئول برش»: کسانی که قبلاً برش زده‌اند + کارکنانِ با سمتِ
     * برش. نامی که تایپ شود همراهِ رکوردِ برش ذخیره می‌شود و از دفعهٔ بعد
     * خودش اینجا می‌آید.
     */
    val cutters: StateFlow<List<String>> =
        repo.observeCutterNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * اندازه‌های مشتری به تفکیکِ نام. برشکار دقیقاً همین را لازم دارد و
     * تا حالا مجبور بود صفحهٔ مشتری را جدا باز کند.
     */
    val measurements: StateFlow<Map<String, List<com.afghanjama.data.dao.NamedMeasurement>>> =
        repo.observeMeasurementsByCustomer()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** عکس‌های هر سفارش، کلید: شناسهٔ سفارش — برشکار طرح را می‌بیند نه فقط نامش. */
    val photos: StateFlow<Map<String, List<com.afghanjama.data.entities.OrderPhoto>>> =
        repo.observeAllOrderPhotos()
            .map { rows -> rows.groupBy { it.orderId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** ثبت رکورد برش (مسئول/تعداد/ضایعات) و انتقال به «برش تمام». */
    fun markCutDone(
        orderId: UUID,
        cutter: String,
        pieces: Int,
        waste: String,
        note: String
    ) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.completeCutting(o, cutter, pieces.coerceAtLeast(0), waste, note)
    }

    /** برگشت به انبار (اصلاح اشتباه). */
    fun backToStock(orderId: UUID) = viewModelScope.launch {
        val o = repo.getOrder(orderId) ?: return@launch
        repo.changeOrderStatus(o, OrderStatus.IN_STOCK.name)
    }
}
