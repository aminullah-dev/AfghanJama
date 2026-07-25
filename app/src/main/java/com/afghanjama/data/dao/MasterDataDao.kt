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

    @Query("DELETE FROM fabric_types WHERE id = :id")
    suspend fun deleteFabricType(id: Long)


    // ----------------------------
    // Fabric Colors
    // ----------------------------
    @Query("SELECT * FROM fabric_colors ORDER BY title ASC")
    fun observeFabricColors(): Flow<List<FabricColor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFabricColor(item: FabricColor)

    @Query("DELETE FROM fabric_colors WHERE id = :id")
    suspend fun deleteFabricColor(id: Long)


    // ----------------------------
    // Sizes
    // ----------------------------
    @Query("SELECT * FROM sizes ORDER BY title ASC")
    fun observeSizes(): Flow<List<SizeItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSize(item: SizeItem)

    @Query("DELETE FROM sizes WHERE id = :id")
    suspend fun deleteSize(id: Long)


    // ----------------------------
    // Tailors
    // ----------------------------
    @Query("SELECT * FROM tailors ORDER BY name ASC")
    fun observeTailors(): Flow<List<Tailor>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTailor(item: Tailor)

    @Query("DELETE FROM tailors WHERE id = :id")
    suspend fun deleteTailor(id: Long)


    // ----------------------------
    // Inspectors
    // ----------------------------
    @Query("SELECT * FROM inspectors ORDER BY name ASC")
    fun observeInspectors(): Flow<List<Inspector>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInspector(item: Inspector)

    @Query("DELETE FROM inspectors WHERE id = :id")
    suspend fun deleteInspector(id: Long)


    // ----------------------------
    // Designs
    // ----------------------------
    @Query("SELECT * FROM design_items ORDER BY title ASC")
    fun observeDesignItems(): Flow<List<DesignItem>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDesign(item: DesignItem): Long

    @Query("UPDATE design_items SET code = :code WHERE id = :id")
    suspend fun setDesignCode(id: Long, code: String)

    @Query("UPDATE design_items SET code = :code WHERE title = :title")
    suspend fun setDesignCodeByTitle(title: String, code: String)

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


    // ----------------------------
    // Customers
    // ----------------------------
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun observeCustomers(): Flow<List<Customer>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCustomer(item: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)
}
