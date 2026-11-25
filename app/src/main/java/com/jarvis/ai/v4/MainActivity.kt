package com.jarvis.ai.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jarvis.ai.v4.ui.JarvisScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color(0xFF020814).toArgb()
        window.navigationBarColor = Color(0xFF020814).toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF61E6FF),
                secondary = Color(0xFF9A8CFF),
                background = Color(0xFF07111F),
                surface = Color(0xFF101D2D),
            )) {
                JarvisScreen(viewModel)
            }
        }
    }
}
