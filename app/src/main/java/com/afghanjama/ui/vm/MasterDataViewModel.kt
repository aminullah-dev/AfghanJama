package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MasterDataViewModel(private val repo: Repo) : ViewModel() {

    val fabricTypes: StateFlow<List<FabricType>> =
        repo.observeFabricTypes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fabricColors: StateFlow<List<FabricColor>> =
        repo.observeFabricColors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sizes: StateFlow<List<SizeItem>> =
        repo.observeSizes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tailors: StateFlow<List<Tailor>> =
        repo.observeTailors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val inspectors: StateFlow<List<Inspector>> =
        repo.observeInspectors()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val designs: StateFlow<List<DesignItem>> =
        repo.observeDesignItems()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customers: StateFlow<List<Customer>> =
        repo.observeCustomers()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ✅ خرج کار
    val workCosts: StateFlow<List<WorkCost>> =
        repo.observeWorkCosts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // کارکنان (آشپز، حسابدار، مدیر، …)
    val staff: StateFlow<List<com.afghanjama.data.entities.Staff>> =
        repo.observeStaff()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addStaff(name: String, role: String) = viewModelScope.launch {
        repo.addStaff(name, role)
    }

    fun addFabricType(title: String) = viewModelScope.launch {
        repo.addFabricType(FabricType(id = 0L, title = title.trim()))
    }

    fun addFabricColor(title: String, hex: String? = null) = viewModelScope.launch {
        repo.addFabricColor(FabricColor(id = 0L, title = title.trim(), hex = hex?.trim()?.ifBlank { null }))
    }

    fun addSize(title: String) = viewModelScope.launch {
        repo.addSize(SizeItem(id = 0L, title = title.trim()))
    }

    fun addTailor(code: String, name: String, phone: String? = null) = viewModelScope.launch {
        repo.addTailor(Tailor(id = 0L, code = code.trim(), name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    }

    fun addInspector(code: String, name: String, phone: String? = null) = viewModelScope.launch {
        repo.addInspector(Inspector(id = 0L, code = code.trim(), name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    }

    /** ثبت طرح با کدِ اختصاصیِ کاربر؛ کدِ خالی = تولید خودکار. */
    /** دسته‌های به‌کاررفته — برای پیشنهاد دادن هنگامِ دسته‌بندیِ طرح. */
    val designCategories: StateFlow<List<String>> =
        repo.observeDesignCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * دستهٔ یک طرح — همان پوشهٔ انبارِ محصول. خالی یعنی «دسته‌بندی‌نشده».
     */
    fun setDesignCategory(id: Long, category: String) = viewModelScope.launch {
        repo.setDesignCategory(id, category)
    }

    fun addDesign(title: String, code: String = "") = viewModelScope.launch {
        repo.addDesign(DesignItem(id = 0L, title = title.trim(), code = code.trim()))
    }

    fun addCustomer(name: String, phone: String? = null) = viewModelScope.launch {
        repo.addCustomer(Customer(id = 0L, name = name.trim(), phone = phone?.trim()?.ifBlank { null }))
    }

    // ✅ CRUD خرج کار
    fun addWorkCost(title: String, price: Long) = viewModelScope.launch {
        val t = title.trim()
        val p = price.coerceAtLeast(0)
        if (t.isBlank() || p <= 0L) return@launch
        repo.insertWorkCost(WorkCost(id = 0L, title = t, price = p))
    }

    fun updateWorkCost(id: Long, title: String, price: Long) = viewModelScope.launch {
        val t = title.trim()
        val p = price.coerceAtLeast(0)
        if (id <= 0L || t.isBlank() || p <= 0L) return@launch
        repo.updateWorkCost(WorkCost(id = id, title = t, price = p))
    }

    fun deleteWorkCost(id: Long) = viewModelScope.launch {
        if (id <= 0L) return@launch
        repo.deleteWorkCostById(id)
    }
}
