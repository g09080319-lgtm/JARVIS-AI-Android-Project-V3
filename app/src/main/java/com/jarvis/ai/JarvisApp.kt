package com.jarvis.ai

import android.app.Application

class JarvisApp : Application() {
    lateinit var engine: AssistantEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = AssistantEngine(this)
    }
}
