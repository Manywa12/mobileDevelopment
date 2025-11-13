package edu.ap.citytrip.data

data class Locality(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val rating: Double? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null
)


