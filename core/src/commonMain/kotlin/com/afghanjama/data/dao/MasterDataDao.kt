package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDataDao {

    // ----------------------------
    // Fabric Types
    // ----------------------------
    @Query("SELECT * FROM fabric_types ORDER BY title ASC")
    fun observeFabricTypes(): Flow<List<FabricType>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFabricType(item: FabricType)

    @Query("UPDATE fabric_types SET title = :title WHERE id = :id")
    suspend fun renameFabricType(id: Long, title: String)

    @Query("DELETE FROM fabric_types WHERE id = :id")
    suspend fun deleteFabricType(id: Long)


    // ----------------------------
    // Fabric Colors
    // ----------------------------
    @Query("SELECT * FROM fabric_colors ORDER BY title ASC")
    fun observeFabricColors(): Flow<List<FabricColor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFabricColor(item: FabricColor)

    @Query("UPDATE fabric_colors SET title = :title WHERE id = :id")
    suspend fun renameFabricColor(id: Long, title: String)

    @Query("DELETE FROM fabric_colors WHERE id = :id")
    suspend fun deleteFabricColor(id: Long)


    // ----------------------------
    // Sizes
    // ----------------------------
    @Query("SELECT * FROM sizes ORDER BY title ASC")
    fun observeSizes(): Flow<List<SizeItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSize(item: SizeItem)

    @Query("UPDATE sizes SET title = :title WHERE id = :id")
    suspend fun renameSize(id: Long, title: String)

    @Query("DELETE FROM sizes WHERE id = :id")
    suspend fun deleteSize(id: Long)


    // ----------------------------
    // Tailors
    // ----------------------------
    @Query("SELECT * FROM tailors ORDER BY name ASC")
    fun observeTailors(): Flow<List<Tailor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTailor(item: Tailor)

    @Query("UPDATE tailors SET name = :name WHERE id = :id")
    suspend fun renameTailor(id: Long, name: String)

    @Query("DELETE FROM tailors WHERE id = :id")
    suspend fun deleteTailor(id: Long)


    // ----------------------------
    // Inspectors
    // ----------------------------
    @Query("SELECT * FROM inspectors ORDER BY name ASC")
    fun observeInspectors(): Flow<List<Inspector>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInspector(item: Inspector)

    @Query("UPDATE inspectors SET name = :name WHERE id = :id")
    suspend fun renameInspector(id: Long, name: String)

    @Query("DELETE FROM inspectors WHERE id = :id")
    suspend fun deleteInspector(id: Long)


    // ----------------------------
    // Designs
    // ----------------------------
    @Query("SELECT * FROM design_items ORDER BY title ASC")
    fun observeDesignItems(): Flow<List<DesignItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDesign(item: DesignItem): Long

    @Query("UPDATE design_items SET title = :title WHERE id = :id")
    suspend fun renameDesign(id: Long, title: String)

    @Query("UPDATE design_items SET code = :code WHERE id = :id")
    suspend fun setDesignCode(id: Long, code: String)

    @Query("UPDATE design_items SET code = :code WHERE title = :title")
    suspend fun setDesignCodeByTitle(title: String, code: String)

    /** دستهٔ طرح — پوشهٔ انبارِ محصول. خالی یعنی «دسته‌بندی‌نشده». */
    /** کدِ اختصاصیِ طرح از روی نامش — برای چاپ روی فاکتور. */
    @Query("SELECT code FROM design_items WHERE TRIM(title) = TRIM(:title) LIMIT 1")
    suspend fun designCodeByTitle(title: String): String?

    @Query("UPDATE design_items SET category = :category WHERE id = :id")
    suspend fun setDesignCategory(id: Long, category: String)

    /** تغییرِ نامِ یک دسته روی همهٔ طرح‌هایش — پوشهٔ انبار هم با آن می‌رود. */
    @Query("UPDATE design_items SET category = :to WHERE category = :from")
    suspend fun renameDesignCategory(from: String, to: String): Int

    /** همهٔ دسته‌های به‌کاررفته، برای پیشنهاد دادن به کاربر. */
    @Query(
        "SELECT DISTINCT category FROM design_items " +
            "WHERE category <> '' ORDER BY category"
    )
    fun observeDesignCategories(): Flow<List<String>>

    @Query("DELETE FROM design_items WHERE id = :id")
    suspend fun deleteDesign(id: Long)

    // ----------------------------
    // Staff (کارکنان: آشپز، حسابدار، مدیر، …)
    // ----------------------------
    @Query("SELECT * FROM staff ORDER BY name ASC")
    fun observeStaff(): Flow<List<com.afghanjama.data.entities.Staff>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStaff(item: com.afghanjama.data.entities.Staff)

    /**
     * به‌روزرسانی سمتِ کارمندِ موجود. سمتِ خالی مقدارِ قبلی را پاک نمی‌کند
     * تا افزودنِ دوبارهٔ یک نام هرگز اطلاعاتِ موجود را از بین نبرد.
     */
    @Query(
        "UPDATE staff SET role = CASE WHEN :role = '' THEN role ELSE :role END " +
            "WHERE name = :name"
    )
    suspend fun updateStaffRole(name: String, role: String)

    /** به‌روزرسانی سمت و حقوقِ ماهانه — فقط وقتی حقوق صریحاً داده شده باشد. */
    @Query(
        "UPDATE staff SET role = CASE WHEN :role = '' THEN role ELSE :role END, " +
            "monthlySalary = :monthlySalary WHERE name = :name"
    )
    suspend fun updateStaffTerms(name: String, role: String, monthlySalary: Long)

    @Query("SELECT * FROM staff WHERE id = :id LIMIT 1")
    suspend fun findStaffById(id: Long): com.afghanjama.data.entities.Staff?

    @Query("SELECT EXISTS(SELECT 1 FROM staff WHERE name = :name)")
    suspend fun staffNameTaken(name: String): Boolean

    /**
     * این کارمند سابقه دارد؟ — حضور، حقوقِ پرداخت‌شده، برش، یا سطرِ دفتر.
     * همه با نام، پس کارمندِ باسابقه حذف نمی‌شود.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM attendance WHERE employee = :name) " +
            "OR EXISTS(SELECT 1 FROM cutting_records WHERE cutter = :name) " +
            "OR EXISTS(SELECT 1 FROM salary_payments WHERE employee = :name) " +
            "OR EXISTS(SELECT 1 FROM ledger_entries WHERE partyType = 'EMPLOYEE' AND partyName = :name) " +
            "OR EXISTS(SELECT 1 FROM parties WHERE type = 'EMPLOYEE' AND name = :name)"
    )
    suspend fun staffHasHistory(name: String): Boolean

    @Query("UPDATE staff SET name = :name WHERE id = :id")
    suspend fun renameStaff(id: Long, name: String)

    @Query("DELETE FROM staff WHERE id = :id")
    suspend fun deleteStaff(id: Long)


    // ----------------------------
    // Customers
    // ----------------------------
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeCustomers(): Flow<List<Customer>>

    /** برای بلوکِ «خریدار» روی فاکتور — تلفن از اینجا می‌آید. */
    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun findCustomerByName(name: String): Customer?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomer(item: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun findCustomerById(id: Long): Customer?

    @Query("UPDATE customers SET name = :name, phone = :phone WHERE id = :id")
    suspend fun updateCustomer(id: Long, name: String, phone: String?)

    /**
     * این نام در کار و حساب آمده است؟
     *
     * سفارش، دفتر، دریافت، قسط و فروش مشتری را با **نام** می‌شناسند نه با
     * شناسه. مشتریِ باسابقه حذف نمی‌شود، وگرنه مانده‌اش زیرِ نامی می‌ماند
     * که دیگر در فهرست نیست. (تغییرِ نامش سابقه را با خودش می‌برد.)
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM orders WHERE customerName = :name) " +
            "OR EXISTS(SELECT 1 FROM ledger_entries WHERE partyType = 'CUSTOMER' AND partyName = :name) " +
            "OR EXISTS(SELECT 1 FROM parties WHERE type = 'CUSTOMER' AND name = :name) " +
            "OR EXISTS(SELECT 1 FROM customer_payments WHERE customerName = :name) " +
            "OR EXISTS(SELECT 1 FROM customer_installments WHERE customerName = :name) " +
            "OR EXISTS(SELECT 1 FROM finished_sales WHERE customerName = :name)"
    )
    suspend fun customerHasHistory(name: String): Boolean

    /**
     * این کارگاه چیزی دارد؟ — برای شروعِ اجباری با کارگاهِ نمونه و قفلش.
     *
     * هم اطلاعاتِ پایه و آدم‌ها را می‌پرسد، هم کار و پول را. اطلاعاتِ پایه
     * لازم است چون «بازنشانیِ داده» آن‌ها را نگه می‌دارد: کارگاهی که دفترش
     * را پاک کرده ولی خیاط‌ها و طرح‌هایش را شخصی کرده، خالی نیست و نباید
     * دوباره به نمونه برگردد.
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM orders) OR EXISTS(SELECT 1 FROM customers) " +
            "OR EXISTS(SELECT 1 FROM tailors) OR EXISTS(SELECT 1 FROM inspectors) " +
            "OR EXISTS(SELECT 1 FROM staff) OR EXISTS(SELECT 1 FROM fabric_types) " +
            "OR EXISTS(SELECT 1 FROM design_items) OR EXISTS(SELECT 1 FROM WorkCost) " +
            "OR EXISTS(SELECT 1 FROM material_stock) OR EXISTS(SELECT 1 FROM journal_entries) " +
            "OR EXISTS(SELECT 1 FROM finance_transactions) OR EXISTS(SELECT 1 FROM ledger_entries) " +
            "OR EXISTS(SELECT 1 FROM parties) OR EXISTS(SELECT 1 FROM finished_stock) " +
            "OR EXISTS(SELECT 1 FROM purchase_invoices) OR EXISTS(SELECT 1 FROM fabric_colors) " +
            "OR EXISTS(SELECT 1 FROM sizes) OR EXISTS(SELECT 1 FROM recurring_expenses)"
    )
    suspend fun hasAnyWorkshopData(): Boolean

    // ----------------------------
    // تغییرِ نامِ شخص، همراهِ سابقه‌اش
    //
    // سفارش، دفتر، دریافت، حضور و کارمزد شخص را با **نام** می‌شناسند. تغییرِ
    // نام فقط در فهرست، مانده و سابقه را زیرِ نامِ قبلی جا می‌گذاشت و حسابِ
    // یک نفر دو تکه می‌شد. این‌ها همه را با هم، در یک تراکنش، می‌برند.
    // اسناد (`documents`) عمداً نه: فاکتورِ چاپ‌شده همان است که دستِ مشتری
    // رفت.
    // ----------------------------

    /** این نام در دفتر، به عنوانِ طرفِ این نوع، هست؟ */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM parties WHERE type = :type AND name = :name) " +
            "OR EXISTS(SELECT 1 FROM ledger_entries WHERE partyType = :type AND partyName = :name)"
    )
    suspend fun partyNameInUse(type: String, name: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM orders WHERE customerName = :name)")
    suspend fun customerNameInOrders(name: String): Boolean

    /** نامی که در حضور هست — خیاط، ناظر و کارمند با نامِ خالی ثبت می‌شوند. */
    @Query("SELECT EXISTS(SELECT 1 FROM attendance WHERE employee = :name)")
    suspend fun nameInAttendance(name: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM tailors WHERE name = :name) " +
            "OR EXISTS(SELECT 1 FROM inspectors WHERE name = :name) " +
            "OR EXISTS(SELECT 1 FROM staff WHERE name = :name)"
    )
    suspend fun workerNameTaken(name: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM purchase_invoices WHERE supplier = :name) " +
        "OR EXISTS(SELECT 1 FROM supplier_ledger WHERE supplier = :name)")
    suspend fun supplierNameInPurchases(name: String): Boolean

    @Query("SELECT * FROM tailors WHERE id = :id LIMIT 1")
    suspend fun findTailorById(id: Long): Tailor?

    @Query("SELECT * FROM inspectors WHERE id = :id LIMIT 1")
    suspend fun findInspectorById(id: Long): Inspector?

    @Query("UPDATE parties SET name = :to WHERE type = :type AND name = :from")
    suspend fun movePartyRow(type: String, from: String, to: String): Int

    @Query("UPDATE ledger_entries SET partyName = :to WHERE partyType = :type AND partyName = :from")
    suspend fun moveLedger(type: String, from: String, to: String): Int

    @Query("UPDATE orders SET customerName = :to WHERE customerName = :from")
    suspend fun moveCustomerOrders(from: String, to: String): Int

    @Query("UPDATE customer_payments SET customerName = :to WHERE customerName = :from")
    suspend fun moveCustomerPayments(from: String, to: String): Int

    @Query("UPDATE customer_installments SET customerName = :to WHERE customerName = :from")
    suspend fun moveCustomerInstallments(from: String, to: String): Int

    @Query("UPDATE finished_sales SET customerName = :to WHERE customerName = :from")
    suspend fun moveCustomerFinishedSales(from: String, to: String): Int

    @Query("UPDATE attendance SET employee = :to WHERE employee = :from")
    suspend fun moveAttendance(from: String, to: String): Int

    @Query("UPDATE salary_payments SET employee = :to WHERE employee = :from")
    suspend fun moveSalaryPayments(from: String, to: String): Int

    @Query("UPDATE cutting_records SET cutter = :to WHERE cutter = :from")
    suspend fun moveCuttingRecords(from: String, to: String): Int

    @Query("UPDATE purchase_invoices SET supplier = :to WHERE supplier = :from")
    suspend fun movePurchaseInvoices(from: String, to: String): Int

    @Query("UPDATE supplier_ledger SET supplier = :to WHERE supplier = :from")
    suspend fun moveSupplierLedger(from: String, to: String): Int

    @Query("UPDATE tailor_wages SET tailorLabel = :to WHERE tailorLabel = :from")
    suspend fun moveTailorWages(from: String, to: String): Int

    @Query("UPDATE sewing_assignments SET tailorLabel = :to WHERE tailorLabel = :from")
    suspend fun moveSewingAssignments(from: String, to: String): Int

    @Query("UPDATE qc_records SET tailor = :to WHERE tailor = :from")
    suspend fun moveQcTailor(from: String, to: String): Int

    @Query("UPDATE qc_records SET inspector = :to WHERE inspector = :from")
    suspend fun moveQcInspector(from: String, to: String): Int

    @Query("UPDATE orders SET assignedTailor = :to WHERE assignedTailor = :from")
    suspend fun moveOrderTailor(from: String, to: String): Int

    @Query("UPDATE orders SET assignedInspector = :to WHERE assignedInspector = :from")
    suspend fun moveOrderInspector(from: String, to: String): Int
}
