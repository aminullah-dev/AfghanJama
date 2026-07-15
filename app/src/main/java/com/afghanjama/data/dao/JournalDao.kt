package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.JournalEntry
import com.afghanjama.data.entities.JournalLine
import kotlinx.coroutines.flow.Flow

/** جمعِ بدهکار/بستانکارِ یک حسابِ کل. */
data class AccountBalance(
    val account: String,
    val debit: Long,
    val credit: Long
) {
    /** ماندهٔ طبیعی: دارایی/هزینه بدهکار، بدهی/سرمایه/درآمد بستانکار. */
    val net: Long get() = debit - credit
}

@Dao
interface JournalDao {

    @Insert
    suspend fun insertEntry(entry: JournalEntry): Long

    @Insert
    suspend fun insertLines(lines: List<JournalLine>)

    @Query(
        "SELECT account, COALESCE(SUM(debit),0) AS debit, COALESCE(SUM(credit),0) AS credit " +
            "FROM journal_lines GROUP BY account ORDER BY account"
    )
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    @Query("SELECT * FROM journal_entries ORDER BY at DESC LIMIT 200")
    fun observeRecentEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_lines WHERE entryId = :entryId")
    suspend fun linesFor(entryId: Long): List<JournalLine>
}
