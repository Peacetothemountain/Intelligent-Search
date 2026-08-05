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

    suspend fun fetchCurrentDoodleBitmap(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            
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
                    
                    return@withContext BitmapFactory.decodeStream(imageConnection.inputStream)
                }
            }
        } catch (e: Exception) {
            Log.e("DoodleFetcher", "Error fetching doodle", e)
        }
        return@withContext null
    }
}