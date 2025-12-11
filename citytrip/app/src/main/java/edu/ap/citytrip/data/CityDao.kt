package edu.ap.citytrip.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM cities ORDER BY name ASC")
    fun getAllCities(): Flow<List<CityEntity>>
    
    @Query("SELECT * FROM cities ORDER BY name ASC")
    suspend fun getAllCitiesSync(): List<CityEntity>
    
    @Query("SELECT * FROM cities WHERE id = :cityId")
    suspend fun getCityById(cityId: String): CityEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cities: List<CityEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(city: CityEntity)
    
    @Query("DELETE FROM cities")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM cities")
    suspend fun getCityCount(): Int
}


