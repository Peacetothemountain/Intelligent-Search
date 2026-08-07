package com.pixel.intelligentsearch.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

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
            val calendar = Calendar.getInstance()
            var year = calendar.get(Calendar.YEAR)
            var month = calendar.get(Calendar.MONTH) + 1
            
            for (i in 0..3) {
                val url = URL("https://www.google.com/doodles/json/$year/$month")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(response)
                    
                    if (jsonArray.length() > 0) {
                        val latestDoodle = jsonArray.getJSONObject(0)
                        var imageUrl = latestDoodle.getString("url")
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
                
                // Go to previous month
                month--
                if (month == 0) {
                    month = 12
                    year--
                }
            }
        } catch (e: Exception) {
            Log.e("DoodleFetcher", "Error fetching doodle", e)
        }
        return@withContext cachedDoodle
    }
}