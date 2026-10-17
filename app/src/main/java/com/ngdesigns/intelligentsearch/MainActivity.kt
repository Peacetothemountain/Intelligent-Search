package com.ngdesigns.intelligentsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ngdesigns.intelligentsearch.ui.SearchShell
import com.ngdesigns.intelligentsearch.ui.theme.SearchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SearchTheme {
                SearchShell()
            }
        }
    }
}
