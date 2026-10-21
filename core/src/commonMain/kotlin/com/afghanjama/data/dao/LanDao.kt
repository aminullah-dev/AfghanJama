package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.LanCommand
import com.afghanjama.data.entities.LanDevice
import com.afghanjama.data.entities.LanHubRow
import kotlinx.coroutines.flow.Flow

/** گوشی‌های جفت‌شده، فرمان‌های صاحب‌کار، و شناسنامهٔ کارگاه (دفترِ ۶۷). */
@Dao
interface LanDao {

    @Query("SELECT * FROM lan_devices ORDER BY createdAt ASC")
    fun observeDevices(): Flow<List<LanDevice>>

    @Query("SELECT * FROM lan_devices WHERE deviceId = :id")
    suspend fun device(id: String): LanDevice?

    @Insert
    suspend fun insertDevice(d: LanDevice)

    @Update
    suspend fun updateDevice(d: LanDevice)

    @Query(
        "UPDATE lan_devices SET lastSeenAt = :at, lastIp = :ip, appVersion = :version " +
            "WHERE deviceId = :id AND revokedAt IS NULL"
    )
    suspend fun touch(id: String, at: Long, ip: String, version: String)

    @Query("SELECT * FROM lan_commands WHERE clientKey = :key")
    suspend fun command(key: String): LanCommand?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommand(c: LanCommand)

    @Query("SELECT * FROM lan_hub WHERE id = 1")
    suspend fun hub(): LanHubRow?

    @Query("SELECT * FROM lan_hub WHERE id = 1")
    fun observeHub(): Flow<LanHubRow?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHub(h: LanHubRow): Long

    @Update
    suspend fun updateHub(h: LanHubRow)
}
