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

    /** جمعِ بستانکارِ دفترِ طرفِ حساب برای یک نوعِ سند. */
    @Query("SELECT COALESCE(SUM(credit), 0) FROM ledger_entries WHERE refType = :refType")
    suspend fun creditByRef(refType: String): Long

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

    /**
     * ماندهٔ یک طرفِ حساب پیش از یک فاکتورِ مشخص — همان «بدهی قبلی» که
     * روی فاکتور چاپ می‌شود.
     *
     * دو شرط لازم است، نه یکی: سطرهای خودِ این فاکتور با [excludeRef] کنار
     * می‌روند (چون هم‌زمان با سند ثبت شده‌اند و زمانشان جدا نمی‌شود)، و
     * [atMs] جلوی وارد شدنِ خریدهای بعدی را می‌گیرد تا چاپِ دوبارهٔ یک
     * فاکتورِ قدیمی هم همان عددِ آن روز را نشان دهد.
     */
    @Query(
        "SELECT COALESCE(SUM(debit - credit), 0) FROM ledger_entries " +
            "WHERE partyType = :type AND partyName = :name " +
            "AND refId <> :excludeRef AND at <= :atMs"
    )
    suspend fun balanceBefore(
        type: String,
        name: String,
        excludeRef: String,
        atMs: Long
    ): Long

    /** ماندهٔ طرفِ حساب تا یک لحظه، با احتسابِ همه‌چیز. */
    @Query(
        "SELECT COALESCE(SUM(debit - credit), 0) FROM ledger_entries " +
            "WHERE partyType = :type AND partyName = :name AND at <= :atMs"
    )
    suspend fun balanceUpTo(type: String, name: String, atMs: Long): Long
}
