package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.LedgerEntry
import com.afghanjama.data.entities.Party
import kotlinx.coroutines.flow.Flow

/** ماندهٔ یک طرف حساب: بدهکار − بستانکار. */
data class PartyBalance(
    val type: String,
    val name: String,
    val debit: Long,
    val credit: Long
) {
    val net: Long get() = debit - credit
}

@Dao
interface LedgerDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParty(party: Party)

    @Insert
    suspend fun insertEntry(entry: LedgerEntry)

    @Query("SELECT * FROM parties ORDER BY type, name")
    fun observeParties(): Flow<List<Party>>

    @Query("SELECT * FROM ledger_entries ORDER BY at DESC")
    fun observeAllEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE partyType = :type AND partyName = :name ORDER BY at DESC")
    fun observeEntriesForParty(type: String, name: String): Flow<List<LedgerEntry>>

    @Query(
        "SELECT partyType AS type, partyName AS name, " +
            "SUM(debit) AS debit, SUM(credit) AS credit " +
            "FROM ledger_entries GROUP BY partyType, partyName ORDER BY partyType, partyName"
    )
    fun observeBalances(): Flow<List<PartyBalance>>
}
