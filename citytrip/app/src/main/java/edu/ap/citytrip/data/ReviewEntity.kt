package edu.ap.citytrip.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey
    val id: String,
    val locationId: String,
    val rating: Float,
    val comment: String,
    val userId: String,
    val userName: String,
    val createdAt: Date?
) {
    fun toReview(): Review {
        return Review(
            id = id,
            rating = rating,
            comment = comment,
            userId = userId,
            userName = userName,
            createdAt = createdAt
        )
    }
    
    companion object {
        fun fromReview(review: Review, locationId: String): ReviewEntity {
            return ReviewEntity(
                id = review.id,
                locationId = locationId,
                rating = review.rating,
                comment = review.comment,
                userId = review.userId,
                userName = review.userName,
                createdAt = review.createdAt
            )
        }
    }
}


