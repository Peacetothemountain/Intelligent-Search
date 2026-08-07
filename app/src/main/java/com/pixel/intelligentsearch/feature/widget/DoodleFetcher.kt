package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

object DoodleFetcher {

    private var cachedDoodle: Bitmap? = null
    private var lastFetchTime: Long = 0
    private const val CACHE_EXPIRATION_MS = 60 * 60 * 1000L // 1 hour

    suspend fun fetchCurrentDoodleBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if (cachedDoodle != null && (currentTime - lastFetchTime) < CACHE_EXPIRATION_MS) {
            return@withContext cachedDoodle
        }

        try {
            val url = URL("https://doodles.google/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Find the first image URL in the format //www.google.com/logos/doodles/...
                val pattern = Pattern.compile("img src=\"(//www\\.google\\.com/logos/doodles/[^\"]+)\"")
                val matcher = pattern.matcher(response)
                
                if (matcher.find()) {
                    var imageUrl = matcher.group(1) ?: return@withContext cachedDoodle
                    if (imageUrl.startsWith("//")) {
                        imageUrl = "https:$imageUrl"
                    }
                    
                    val imageConnection = URL(imageUrl).openConnection() as HttpURLConnection
                    imageConnection.doInput = true
                    imageConnection.connect()
                    
                    val bitmap = BitmapFactory.decodeStream(imageConnection.inputStream)
                    cachedDoodle = bitmap
                    lastFetchTime = System.currentTimeMillis()
                    return@withContext bitmap
                }
            }
        } catch (e: Exception) {
            Log.e("DoodleFetcher", "Error fetching doodle", e)
        }
        return@withContext cachedDoodle
    }
}
