package com.pixel.intelligentsearch.feature.search
import com.pixel.intelligentsearch.MainActivity
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SearchActivity", "onCreate called. Intent action: ${intent?.action}")
        val query = intent.getStringExtra(SearchManager.QUERY) ?: ""
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("query", query)
            action = this@SearchActivity.intent.action
        }
        startActivity(mainIntent)
        finish()
    }
}
