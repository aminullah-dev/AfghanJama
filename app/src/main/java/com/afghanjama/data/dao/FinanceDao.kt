package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FinanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTx(tx: Transaction)

    @Query(
        """
        SELECT id, orderId, type, source, amount, note, createdAt
        FROM finance_transactions
        ORDER BY createdAt DESC
        """
    )
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
}
