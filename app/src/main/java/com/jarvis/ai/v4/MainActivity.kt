package com.jarvis.ai.v4

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.jarvis.ai.v4.ui.JarvisScreen
import com.jarvis.ai.v4.ui.AivoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AivoTheme {
                JarvisScreen(viewModel)
            }
        }
    }
}
