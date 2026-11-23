package com.jarvis.ai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jarvis.ai.ui.JarvisScreen
import com.jarvis.ai.ui.JarvisTheme
import com.jarvis.ai.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var pendingBackgroundStart by remember { mutableStateOf(false) }
            val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                if (r[Manifest.permission.RECORD_AUDIO] == true) {
                    if (pendingBackgroundStart) {
                        pendingBackgroundStart = false
                        vm.setBackground(true)
                        startForegroundService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START))
                    } else {
                        vm.engine.toggleListen()
                    }
                } else {
                    pendingBackgroundStart = false
                }
            }
            val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            fun missingAudioOrNotificationPermission(): Boolean =
                !vm.engine.hasMic() || (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            fun requestAudioAndNotifications() {
                perms.launch(buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray())
            }
            JarvisTheme {
                JarvisScreen(
                    vm = vm,
                    onMic = {
                        if (missingAudioOrNotificationPermission()) requestAudioAndNotifications()
                        else vm.engine.toggleListen()
                    },
                    onBackground = { on ->
                        if (on && missingAudioOrNotificationPermission()) {
                            pendingBackgroundStart = true
                            requestAudioAndNotifications()
                        } else {
                            vm.setBackground(on)
                            val i = Intent(this, AssistantService::class.java).setAction(
                                if (on) AssistantService.ACTION_START else AssistantService.ACTION_STOP)
                            if (on) startForegroundService(i) else startService(i)
                        }
                    },
                    onContacts = { contacts.launch(Manifest.permission.READ_CONTACTS) }
                )
            }
        }
    }
}
