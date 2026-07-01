package com.backup.telegram.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.backup.telegram.data.local.dao.MediaFileDao
import com.backup.telegram.data.local.entity.MediaFileEntity
import com.backup.telegram.data.local.entity.UploadStatus

@Database(
    entities = [MediaFileEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaFileDao(): MediaFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "telegram_backup.db"
                )
                    .fallbackToDestructiveMigration() // في الإنتاج: استبدل بـ migrations حقيقية
                    .build().also { INSTANCE = it }
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromStatus(status: UploadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): UploadStatus = UploadStatus.valueOf(value)
}
