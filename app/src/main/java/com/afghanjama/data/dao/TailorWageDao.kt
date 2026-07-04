package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.TailorWage
import kotlinx.coroutines.flow.Flow

@Dao
interface TailorWageDao {

    /** کارمزدهای تسویه‌نشده (برای تسویه هفتگی). */
    @Query("SELECT * FROM tailor_wages WHERE settled = 0 ORDER BY createdAt DESC")
    fun observePending(): Flow<List<TailorWage>>

    /** تاریخچه کامل کارمزدها. */
    @Query("SELECT * FROM tailor_wages ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TailorWage>>

    /**
     * ثبت کارمزد. orderId ایندکس یکتا دارد تا اگر سفارشی چند بار
     * بین دوخت و بازرسی رفت‌وآمد کند، کارمزد تکراری ثبت نشود.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(wage: TailorWage)

    /** تسویه همه کارمزدهای باز یک خیاط. */
    @Query("UPDATE tailor_wages SET settled = 1, settledAt = :settledAt WHERE tailorLabel = :tailorLabel AND settled = 0")
    suspend fun settleForTailor(tailorLabel: String, settledAt: Long)
}
