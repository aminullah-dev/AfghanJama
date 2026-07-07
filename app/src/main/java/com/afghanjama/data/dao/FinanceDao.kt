package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface FinanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTx(tx: Transaction)

    @Query("DELETE FROM finance_transactions WHERE id = :id")
    suspend fun deleteTx(id: UUID)

    @Query("SELECT * FROM finance_transactions ORDER BY createdAt DESC")
    fun observeTx(): Flow<List<Transaction>>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN source = 'WALLET' AND type = 'IN'  THEN amount
                WHEN source = 'WALLET' AND type = 'OUT' THEN -amount
                ELSE 0
            END
        ), 0)
        FROM finance_transactions
        """
    )
    fun observeWalletBalance(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN source = 'PROFIT' AND type = 'IN'  THEN amount
                WHEN source = 'PROFIT' AND type = 'OUT' THEN -amount
                ELSE 0
            END
        ), 0)
        FROM finance_transactions
        """
    )
    fun observeProfitBalance(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(
            CASE
                WHEN source = 'BANK' AND type = 'IN'  THEN amount
                WHEN source = 'BANK' AND type = 'OUT' THEN -amount
                ELSE 0
            END
        ), 0)
        FROM finance_transactions
        """
    )
    fun observeBankBalance(): Flow<Long>
}
