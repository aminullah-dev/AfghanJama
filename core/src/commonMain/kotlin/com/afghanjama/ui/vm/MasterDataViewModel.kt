package com.afghanjama.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.afghanjama.data.PersonEdit
import com.afghanjama.data.entities.Customer
import com.afghanjama.data.entities.DesignItem
import com.afghanjama.data.entities.FabricColor
import com.afghanjama.data.entities.FabricType
import com.afghanjama.data.entities.Inspector
import com.afghanjama.data.entities.SizeItem
import com.afghanjama.data.entities.Tailor
import com.afghanjama.data.entities.WorkCost
import com.afghanjama.data.repo.Repo
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun addFabricType(title: String, season: String = "") = viewModelScope.launch {
        repo.addFabricType(FabricType(id = 0L, title = title.trim(), season = season.trim()))
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

    /** کاری که انجام نشد و دلیلش — مثلاً نامِ مشتری‌ای که سابقه دارد. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    private fun report(r: PersonEdit, noun: String) {
        if (r != PersonEdit.DONE) _message.value = r.message(noun)
    }

    /** نام و تلفنِ مشتری. قاعدهٔ نام و حذف در `Repo.editCustomer`. */
    fun editCustomer(id: Long, name: String, phone: String) = viewModelScope.launch {
        report(repo.editCustomer(id, name, phone), "مشتری")
    }

    fun deleteCustomer(id: Long) = viewModelScope.launch {
        report(repo.deleteCustomer(id), "مشتری")
    }

    /**
     * نام و سمتِ کارمند. اگر نام عوض نشد، سمت هم ثبت نمی‌شود تا نیمی
     * از ویرایش بی‌صدا نماند.
     */
    fun editStaff(id: Long, oldName: String, name: String, role: String) = viewModelScope.launch {
        val nm = name.trim()
        if (nm != oldName) {
            val r = repo.renameStaff(id, nm)
            if (r != PersonEdit.DONE) {
                report(r, "کارمند")
                return@launch
            }
        }
        repo.addStaff(nm, role)
    }

    fun deleteStaff(id: Long) = viewModelScope.launch {
        report(repo.deleteStaff(id), "کارمند")
    }

    /** نامِ یک دسته را روی همهٔ طرح‌هایش عوض می‌کند — یعنی پوشهٔ انبار هم. */
    fun renameDesignCategory(from: String, to: String) = viewModelScope.launch {
        repo.renameDesignCategory(from, to)
    }

    /** کدِ طرح. نامِ طرح با [renameDesign] عوض می‌شود. */
    fun setDesignCode(id: Long, code: String) = viewModelScope.launch {
        repo.setDesignCode(id, code)
    }

    // ✅ CRUD خرج کار
    // ---- ویرایش و حذفِ نام‌ها ----
    //
    // اسنادِ گذشته دست نمی‌خورند — توضیحِ کامل در `Repo`.

    // خیاط و ناظر حساب دارند؛ تغییرِ نامشان کارمزد و دفتر و حضور را هم می‌برد.
    fun renameTailor(id: Long, name: String) = safe { report(repo.renameTailorWithHistory(id, name), "خیاط") }
    fun renameInspector(id: Long, name: String) = safe { report(repo.renameInspectorWithHistory(id, name), "ناظر") }
    fun renameFabricType(id: Long, title: String) = safe { repo.renameFabricType(id, title) }
    fun renameFabricColor(id: Long, title: String) = safe { repo.renameFabricColor(id, title) }
    fun renameSize(id: Long, title: String) = safe { repo.renameSize(id, title) }
    fun renameDesign(id: Long, title: String) = safe { repo.renameDesign(id, title) }

    /**
     * ویرایشی که به نامِ تکراری بخورد، اپ را نمی‌ترکاند.
     *
     * بیشترِ این فهرست‌ها نامِ یکتا دارند؛ تغییرِ «کتان» به «مخمل»ی که
     * از قبل هست، در دیتابیس خطا می‌داد و چون کسی نمی‌گرفتش اپ بسته
     * می‌شد. حالا پیام می‌گیرد.
     */
    private fun safe(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure {
            _message.value = "ثبت نشد — این نام از قبل در فهرست هست."
        }
    }

    /**
     * مانده افتتاحیهٔ یک شخص — «حسابِ قبلی» هنگام مهاجرت.
     *
     * `type` همان نوعِ طرفِ حساب است (`TAILOR` / `INSPECTOR` /
     * `EMPLOYEE`) تا مانده روی همان حسابی بنشیند که پرداخت‌های بعدی
     * می‌نشینند؛ وگرنه طلب برای همیشه دو تکه می‌ماند.
     */
    fun setOpeningBalance(type: String, name: String, amount: Long, owedToThem: Boolean) =
        viewModelScope.launch {
            repo.setOpeningBalance(type, name, amount, owedToThem)
        }

    fun deleteTailor(id: Long) = viewModelScope.launch { repo.deleteTailor(id) }
    fun deleteInspector(id: Long) = viewModelScope.launch { repo.deleteInspector(id) }
    fun deleteFabricType(id: Long) = viewModelScope.launch { repo.deleteFabricType(id) }
    fun deleteFabricColor(id: Long) = viewModelScope.launch { repo.deleteFabricColor(id) }
    fun deleteSize(id: Long) = viewModelScope.launch { repo.deleteSize(id) }
    fun deleteDesign(id: Long) = viewModelScope.launch { repo.deleteDesign(id) }

    fun addWorkCost(title: String, price: Long) = viewModelScope.launch {
        val t = title.trim()
        val p = price.coerceAtLeast(0)
        if (t.isBlank() || p <= 0L) return@launch
        repo.insertWorkCost(WorkCost(id = 0L, title = t, price = p))
    }

    fun updateWorkCost(id: Long, title: String, price: Long) = safe {
        val t = title.trim()
        val p = price.coerceAtLeast(0)
        if (id > 0L && t.isNotBlank() && p > 0L) {
            repo.updateWorkCost(WorkCost(id = id, title = t, price = p))
        }
    }

    fun deleteWorkCost(id: Long) = viewModelScope.launch {
        if (id <= 0L) return@launch
        repo.deleteWorkCostById(id)
    }
}
