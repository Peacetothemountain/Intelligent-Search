package com.pixel.intelligentsearch.core.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Authentic Google Fallback Palette for API 30 (No Monet)
private val RobinDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA), 
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF282A2C), // True pill background
    secondary = Color(0xFFC4C7C5),
    surface = Color(0xFF131314), // True Gemini background
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outlineVariant = Color(0xFF444746),
    background = Color(0xFF131314)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFF8F9FA)
)

@Composable
fun IntelligentSearchTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val colorScheme = if (dynamicColor) {
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context).copy(
                background = Color(0xFFF8F9FA),
                surface = Color(0xFFF8F9FA)
            )
        }
    } else {
        if (darkTheme) RobinDarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
