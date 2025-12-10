package edu.ap.citytrip.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationRepository(
    private val locationDao: LocationDao,
    private val firestore: FirebaseFirestore
) {
    /**
     * Get all locations with state information (cache vs Firebase)
     */
    fun getAllLocationsWithState(): Flow<LocationDataState> = flow {
        // First emit cached data
        val cached = locationDao.getAllLocationsSync()
        if (cached.isNotEmpty()) {
            Log.d("LocationRepository", "📦 Loading ${cached.size} locations from CACHE (fast)")
            emit(LocationDataState.Success(
                locations = cached.map { it.toLocation() },
                isFromCache = true,
                isRefreshing = true
            ))
        } else {
            Log.d("LocationRepository", "⏳ No cache found, loading from Firebase...")
            emit(LocationDataState.Loading(isFromCache = false))
        }
        
        // Then fetch from Firebase and update cache
        try {
            fetchLocationsFromFirebase()
            // After updating cache, emit the new data from Firebase
            val updated = locationDao.getAllLocationsSync()
            Log.d("LocationRepository", "✨ Updated with ${updated.size} locations from FIREBASE")
            emit(LocationDataState.Success(
                locations = updated.map { it.toLocation() },
                isFromCache = false,
                isRefreshing = false
            ))
        } catch (e: Exception) {
            Log.e("LocationRepository", "❌ Error fetching from Firebase: ${e.message}", e)
            // If Firebase fails and we have cache, emit cache with error state
            if (cached.isNotEmpty()) {
                Log.d("LocationRepository", "📦 Using cached data (${cached.size} locations) because Firebase failed")
                emit(LocationDataState.Error(
                    message = "Kan niet verbinden met Firebase. Gecachte data getoond.",
                    cachedLocations = cached.map { it.toLocation() }
                ))
            } else {
                Log.w("LocationRepository", "⚠️ No cache available and Firebase failed - showing empty list")
                emit(LocationDataState.Error(
                    message = "Geen data beschikbaar (geen internet en geen cache)",
                    cachedLocations = null
                ))
            }
        }
    }
    
    /**
     * Get all locations - first from cache, then sync with Firebase (backward compatibility)
     */
    fun getAllLocations(): Flow<List<Location>> = flow {
        // First emit cached data
        val cached = locationDao.getAllLocationsSync()
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toLocation() })
        }
        
        // Then fetch from Firebase and update cache
        try {
            fetchLocationsFromFirebase()
            // After updating cache, emit the new data
            val updated = locationDao.getAllLocationsSync()
            emit(updated.map { it.toLocation() })
        } catch (e: Exception) {
            Log.e("LocationRepository", "Error fetching from Firebase", e)
            // If Firebase fails and we have cache, emit cache again
            if (cached.isNotEmpty()) {
                emit(cached.map { it.toLocation() })
            } else {
                emit(emptyList())
            }
        }
    }
    
    /**
     * Get locations for a specific city
     */
    fun getLocationsByCityId(cityId: String): Flow<List<Location>> = flow {
        // First emit cached data
        val cached = locationDao.getLocationsByCityId(cityId)
        cached.collect { cachedLocations ->
            if (cachedLocations.isNotEmpty()) {
                emit(cachedLocations.map { it.toLocation() })
            }
        }
        
        // Then fetch from Firebase and update cache
        try {
            fetchLocationsFromFirebase()
            // After updating cache, get the city locations from database
            val updated = locationDao.getLocationsByCityId(cityId)
            updated.collect { cityLocations ->
                emit(cityLocations.map { it.toLocation() })
            }
        } catch (e: Exception) {
            Log.e("LocationRepository", "Error fetching from Firebase", e)
        }
    }
    
    /**
     * Force refresh from Firebase with state
     */
    suspend fun refreshLocationsWithState(): LocationDataState = withContext(Dispatchers.IO) {
        try {
            fetchLocationsFromFirebase()
            val updated = locationDao.getAllLocationsSync()
            LocationDataState.Success(
                locations = updated.map { it.toLocation() },
                isFromCache = false,
                isRefreshing = false
            )
        } catch (e: Exception) {
            Log.e("LocationRepository", "Error refreshing locations", e)
            val cached = locationDao.getAllLocationsSync()
            LocationDataState.Error(
                message = "Fout bij vernieuwen: ${e.message}",
                cachedLocations = if (cached.isNotEmpty()) cached.map { it.toLocation() } else null
            )
        }
    }
    
    /**
     * Force refresh from Firebase (backward compatibility)
     */
    suspend fun refreshLocations(): Result<List<Location>> = withContext(Dispatchers.IO) {
        try {
            val locations = fetchLocationsFromFirebase()
            Result.success(locations)
        } catch (e: Exception) {
            Log.e("LocationRepository", "Error refreshing locations", e)
            Result.failure(e)
        }
    }
    
    /**
     * Fetch all locations from Firebase and update cache
     */
    private suspend fun fetchLocationsFromFirebase(): List<Location> = withContext(Dispatchers.IO) {
        val allLocations = mutableListOf<Pair<Location, String>>() // Location + cityId pair
        
        Log.d("LocationRepository", "🔄 Fetching locations from Firebase...")
        val snapshot = firestore.collectionGroup("locations")
            .get()
            .await()
        Log.d("LocationRepository", "✅ Fetched ${snapshot.documents.size} locations from Firebase")
        
        snapshot.documents.forEach { locDoc ->
            val data = locDoc.data ?: emptyMap()
            val latAny = data["latitude"]
            val lonAny = data["longitude"]
            val latitude = when (latAny) {
                is Number -> latAny.toDouble()
                else -> 0.0
            }
            val longitude = when (lonAny) {
                is Number -> lonAny.toDouble()
                else -> 0.0
            }
            
            // Get cityId from document reference
            val cityRef = locDoc.reference.parent.parent
            val cityId = cityRef?.id ?: ""
            
            val location = Location(
                id = locDoc.id,
                name = data["name"] as? String ?: "",
                description = data["description"] as? String ?: "",
                category = data["category"] as? String ?: "",
                latitude = latitude,
                longitude = longitude,
                imageUrl = (data["imageUrl"] as? String)?.takeIf { it.isNotBlank() },
                createdBy = data["createdBy"] as? String ?: ""
            )
            
            allLocations.add(Pair(location, cityId))
        }
        
        // Only update cache if we got locations from Firebase
        // Don't overwrite cache with empty data if Firebase returns 0 (could be offline or error)
        if (allLocations.isNotEmpty()) {
            val entities = allLocations.map { (location, cityId) ->
                LocationEntity.fromLocation(location, cityId)
            }
            locationDao.insertAll(entities)
            Log.d("LocationRepository", "💾 Cached ${entities.size} locations in Room database")
        } else {
            Log.w("LocationRepository", "⚠️ Firebase returned 0 locations - keeping existing cache (not overwriting)")
        }
        
        // Return locations (or empty if Firebase had none)
        allLocations.map { it.first }
    }
    
    /**
     * Get cached locations count
     */
    suspend fun getCachedLocationCount(): Int = withContext(Dispatchers.IO) {
        locationDao.getLocationCount()
    }
}

