package edu.ap.citytrip.utils

import android.content.Context
import android.util.Log
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.util.DebugLogger
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import edu.ap.citytrip.utils.NetworkUtils

object ImageCache {
    private var imageLoader: ImageLoader? = null
    
    /**
     * Create an ImageRequest with appropriate cache policies based on network status
     * - If online: Use cache if available, download if not cached
     * - If offline: Only use cache, don't try to download
     */
    fun createImageRequest(context: Context, imageUrl: String?): ImageRequest {
        val isOnline = NetworkUtils.isOnline(context)
        val url = imageUrl ?: "" // Handle null case
        
        // When offline, only use cache - don't attempt network
        // When online, use cache if available, otherwise download
        return ImageRequest.Builder(context)
            .data(url)
            .diskCacheKey(url)
            .memoryCacheKey(url)
            .allowHardware(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            // If offline, disable network completely - Coil will only check cache
            // If online, allow network to download if not in cache
            .networkCachePolicy(if (isOnline) CachePolicy.ENABLED else CachePolicy.DISABLED)
            // When offline, don't allow network fetcher at all
            .allowRgb565(false)
            .build()
    }
    
    fun getImageLoader(context: Context): ImageLoader {
        if (imageLoader == null) {
            val cacheDir = context.cacheDir.resolve("image_cache")
            
            // Create interceptor that blocks network requests when offline
            val networkInterceptor = Interceptor { chain ->
                if (!NetworkUtils.isOnline(context)) {
                    // When offline, block network requests - Coil will fall back to cache
                    Log.d("ImageCache", "🚫 Blocking network request (offline) - Coil will use cache: ${chain.request().url}")
                    // This will make Coil fall back to cache
                    throw IOException("Network unavailable - using cache only")
                }
                // When online, proceed with network request (images will be cached automatically)
                val response = chain.proceed(chain.request())
                if (response.isSuccessful) {
                    Log.d("ImageCache", "✅ Network request successful - image will be cached: ${chain.request().url}")
                }
                response
            }
            
            // Create OkHttpClient with network interceptor
            val okHttpClient = OkHttpClient.Builder()
                .addNetworkInterceptor(networkInterceptor)
                .build()
            
            imageLoader = ImageLoader.Builder(context)
                .memoryCache {
                    MemoryCache.Builder(context)
                        .maxSizePercent(0.25) // 25% of available memory
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir)
                        .maxSizeBytes(100 * 1024 * 1024) // 100 MB disk cache
                        .build()
                }
                .respectCacheHeaders(false) // Always use cache if available, even if expired
                .allowHardware(false) // Ensure images work offline
                .crossfade(true)
                .logger(DebugLogger()) // Enable debug logging to see cache hits
                .okHttpClient(okHttpClient)
                .build()
            Log.d("ImageCache", "✅ ImageLoader initialized with 100MB disk cache at: ${cacheDir.absolutePath}")
        }
        return imageLoader!!
    }
    
    /**
     * Prefetch images to disk cache (works in background, only if online)
     * Note: Images must be loaded with internet first before they work offline
     * This will silently fail if offline - that's OK, images will load from cache when available
     */
    fun prefetchImages(context: Context, imageUrls: List<String?>) {
        // Only prefetch if we have internet connection
        if (!NetworkUtils.isOnline(context)) {
            Log.d("ImageCache", "⚠️ Skipping prefetch - device is offline. Images will load from cache if available.")
            return
        }
        
        val loader = getImageLoader(context)
        val scope = CoroutineScope(Dispatchers.IO)
        
        imageUrls.filterNotNull().filter { it.isNotBlank() }.forEach { url ->
            scope.launch {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .diskCacheKey(url)
                        .memoryCacheKey(url)
                        .networkCachePolicy(CachePolicy.ENABLED) // Allow network if online
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build()
                    loader.enqueue(request)
                    Log.d("ImageCache", "📥 Prefetching image: $url")
                } catch (e: Exception) {
                    // Silently fail if offline - images will load from cache when available
                    Log.d("ImageCache", "Prefetch failed: $url - ${e.message}")
                }
            }
        }
    }
}

