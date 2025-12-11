package edu.ap.citytrip.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /**
     * Migration from version 1 to 2: Add createdAt field to locations table
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add createdAt column (as INTEGER for timestamp)
            database.execSQL("ALTER TABLE locations ADD COLUMN createdAt INTEGER")
        }
    }
    
    /**
     * Migration from version 2 to 3: Add reviews table
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create reviews table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS reviews (
                    id TEXT NOT NULL PRIMARY KEY,
                    locationId TEXT NOT NULL,
                    rating REAL NOT NULL,
                    comment TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    userName TEXT NOT NULL,
                    createdAt INTEGER
                )
            """.trimIndent())
            
            // Create index for faster queries
            database.execSQL("CREATE INDEX IF NOT EXISTS index_reviews_locationId ON reviews(locationId)")
        }
    }
    
    /**
     * Migration from version 3 to 4: Add cities table
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Drop index if it exists (from previous migration attempt)
            try {
                database.execSQL("DROP INDEX IF EXISTS index_cities_createdBy")
            } catch (e: Exception) {
                // Index might not exist, ignore
            }
            
            // Create cities table - column order must match Entity exactly
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS cities (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    imageUrl TEXT,
                    localityCount INTEGER NOT NULL,
                    createdBy TEXT NOT NULL,
                    lastUpdated INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
}

