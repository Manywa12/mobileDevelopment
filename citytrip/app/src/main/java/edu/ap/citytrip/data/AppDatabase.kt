package edu.ap.citytrip.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LocationEntity::class, ReviewEntity::class, CityEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun reviewDao(): ReviewDao
    abstract fun cityDao(): CityDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                android.util.Log.d("AppDatabase", "🔷 Initializing Room database (PERSISTENT - data will be preserved)")
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "citytrip_database"
                )
                // Add migrations to preserve data when schema changes
                .addMigrations(
                    Migrations.MIGRATION_1_2,
                    Migrations.MIGRATION_2_3,
                    Migrations.MIGRATION_3_4
                )
                // DO NOT use fallbackToDestructiveMigration - it wipes data!
                // If migration fails, the app will crash instead of losing data
                .build()
                android.util.Log.d("AppDatabase", "✅ Room database initialized - data is PERSISTENT across app restarts")
                INSTANCE = instance
                instance
            }
        }
    }
}

