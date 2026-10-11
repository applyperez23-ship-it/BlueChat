package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entities.PacketLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PacketLogDao {
    @Query("SELECT * FROM packet_logs ORDER BY timestamp DESC LIMIT 200")
    fun getRecentLogsFlow(): Flow<List<PacketLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PacketLogEntity): Long

    @Query("DELETE FROM packet_logs")
    suspend fun deleteAllLogs()
}
