package edu.ap.citytrip.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val createdBy: String,
    val createdAt: Date?,
    val cityId: String, // Added for mapping locations to cities
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toLocation(): Location {
        return Location(
            id = id,
            name = name,
            description = description,
            category = category,
            latitude = latitude,
            longitude = longitude,
            imageUrl = imageUrl,
            createdBy = createdBy,
            createdAt = createdAt
        )
    }
    
    companion object {
        fun fromLocation(location: Location, cityId: String): LocationEntity {
            return LocationEntity(
                id = location.id,
                name = location.name,
                description = location.description,
                category = location.category,
                latitude = location.latitude,
                longitude = location.longitude,
                imageUrl = location.imageUrl,
                createdBy = location.createdBy,
                createdAt = location.createdAt,
                cityId = cityId,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}

