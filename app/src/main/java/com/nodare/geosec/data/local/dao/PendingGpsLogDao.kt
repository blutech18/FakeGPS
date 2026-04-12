package com.nodare.geosec.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nodare.geosec.data.local.entity.PendingGpsLogEntity

@Dao
interface PendingGpsLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: PendingGpsLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<PendingGpsLogEntity>)

    @Query("SELECT * FROM pending_gps_logs WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedLogs(): List<PendingGpsLogEntity>

    @Query("UPDATE pending_gps_logs SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

    @Query("DELETE FROM pending_gps_logs WHERE isSynced = 1")
    suspend fun deleteSyncedLogs()

    @Query("SELECT COUNT(*) FROM pending_gps_logs WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
}
