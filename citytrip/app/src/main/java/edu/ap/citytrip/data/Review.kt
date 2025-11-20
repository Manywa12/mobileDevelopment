package edu.ap.citytrip.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Review(
    val id: String = "",
    val rating: Float = 0.0f,
    val comment: String = "",
    val userId: String = "",
    val userName: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)