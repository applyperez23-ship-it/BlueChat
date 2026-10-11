package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.entities.ContactEntity
import com.example.data.local.entities.MessageEntity
import com.example.data.local.entities.PacketLogEntity

@Database(
    entities = [MessageEntity::class, ContactEntity::class, PacketLogEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class GhostMeshDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun packetLogDao(): PacketLogDao

    companion object {
        @Volatile
        private var INSTANCE: GhostMeshDatabase? = null

        fun getInstance(context: Context): GhostMeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GhostMeshDatabase::class.java,
                    "ghostmesh.db"
                ).fallbackToDestructiveMigration(true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
