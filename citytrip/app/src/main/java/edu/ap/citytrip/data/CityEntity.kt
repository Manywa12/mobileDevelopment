package edu.ap.citytrip.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val imageUrl: String?,
    val localityCount: Int,
    val createdBy: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun toCity(): City {
        return City(
            id = id,
            name = name,
            imageUrl = imageUrl,
            localityCount = localityCount,
            createdBy = createdBy
        )
    }
    
    companion object {
        fun fromCity(city: City): CityEntity {
            return CityEntity(
                id = city.id,
                name = city.name,
                imageUrl = city.imageUrl,
                localityCount = city.localityCount,
                createdBy = city.createdBy,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
}




