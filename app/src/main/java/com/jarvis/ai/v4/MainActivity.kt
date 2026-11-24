package com.jarvis.ai.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.jarvis.ai.v4.ui.JarvisScreen

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
