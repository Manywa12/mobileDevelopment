package edu.ap.citytrip.data

sealed class LocationDataState {
    data class Success(
        val locations: List<Location>,
        val isFromCache: Boolean,
        val isRefreshing: Boolean = false
    ) : LocationDataState()
    
    data class Loading(val isFromCache: Boolean = false) : LocationDataState()
    data class Error(val message: String, val cachedLocations: List<Location>? = null) : LocationDataState()
}

