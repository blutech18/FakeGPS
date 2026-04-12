package com.nodare.geosec.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nodare.geosec.data.local.dao.PendingGpsLogDao
import com.nodare.geosec.data.local.entity.PendingGpsLogEntity

@Database(
    entities = [PendingGpsLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NodareDatabase : RoomDatabase() {
    abstract fun pendingGpsLogDao(): PendingGpsLogDao
}
