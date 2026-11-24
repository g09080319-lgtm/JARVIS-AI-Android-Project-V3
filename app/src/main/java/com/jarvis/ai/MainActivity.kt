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
            var pendingPermissionAction by remember { mutableStateOf("") }
            val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
                val action = pendingPermissionAction
                pendingPermissionAction = ""
                val audioGranted = r[Manifest.permission.RECORD_AUDIO] == true || vm.engine.hasMic()
                val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                    r[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                when {
                    action == "background" && audioGranted && notificationsGranted -> {
                        vm.setBackground(true)
                        startForegroundService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START))
                    }
                    action == "background" -> {
                        vm.engine.error.value = "Microphone and notification permissions are needed for background listening."
                    }
                    action == "always" && audioGranted -> vm.setAlwaysListening(true)
                    action == "always" -> vm.engine.error.value = "Microphone permission is needed for Always Listening."
                    action == "mic" && audioGranted -> vm.engine.toggleListen()
                    action == "mic" -> vm.engine.error.value = "Microphone permission is needed to speak to JARVIS."
                }
            }
            val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            fun requestPermissions(action: String, includeNotifications: Boolean) {
                pendingPermissionAction = action
                perms.launch(buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (includeNotifications && Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray())
            }
            JarvisTheme {
                JarvisScreen(
                    vm = vm,
                    onMic = {
                        if (vm.engine.hasMic()) vm.engine.toggleListen()
                        else requestPermissions("mic", includeNotifications = false)
                    },
                    onAlwaysListening = { enabled ->
                        if (!enabled) vm.setAlwaysListening(false)
                        else if (vm.engine.hasMic()) vm.setAlwaysListening(true)
                        else requestPermissions("always", includeNotifications = false)
                    },
                    onBackground = { enabled ->
                        if (enabled) {
                            val notificationMissing = Build.VERSION.SDK_INT >= 33 &&
                                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (!vm.engine.hasMic() || notificationMissing) {
                                requestPermissions("background", includeNotifications = true)
                            } else {
                                vm.setBackground(true)
                                startForegroundService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START))
                            }
                        } else {
                            vm.setBackground(false)
                            startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_STOP_BACKGROUND))
                        }
                    },
                    onContacts = { contacts.launch(Manifest.permission.READ_CONTACTS) }
                )
            }
        }
    }
}
