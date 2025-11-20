package edu.ap.citytrip.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Location(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUrl: String? = null,
    val createdBy: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)