package edu.ap.citytrip.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE locationId = :locationId ORDER BY createdAt DESC")
    fun getReviewsByLocationId(locationId: String): Flow<List<ReviewEntity>>
    
    @Query("SELECT * FROM reviews WHERE locationId = :locationId ORDER BY createdAt DESC")
    suspend fun getReviewsByLocationIdSync(locationId: String): List<ReviewEntity>
    
    @Query("SELECT * FROM reviews")
    suspend fun getAllReviewsSync(): List<ReviewEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reviews: List<ReviewEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity)
    
    @Query("DELETE FROM reviews WHERE locationId = :locationId")
    suspend fun deleteByLocationId(locationId: String)
    
    @Query("DELETE FROM reviews")
    suspend fun deleteAll()
}



