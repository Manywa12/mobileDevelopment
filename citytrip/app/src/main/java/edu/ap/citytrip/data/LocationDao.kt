package edu.ap.citytrip.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations")
    fun getAllLocations(): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE cityId = :cityId")
    fun getLocationsByCityId(cityId: String): Flow<List<LocationEntity>>
    
    @Query("SELECT * FROM locations WHERE cityId = :cityId")
    suspend fun getLocationsByCityIdSync(cityId: String): List<LocationEntity>
    
    @Query("SELECT * FROM locations")
    suspend fun getAllLocationsSync(): List<LocationEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)
    
    @Query("DELETE FROM locations")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM locations")
    suspend fun getLocationCount(): Int
}

