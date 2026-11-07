package com.ila3.dshnative

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.ila3.dshnative.protocol.KeepAliveService
import com.ila3.dshnative.protocol.NativeNotifications
import com.ila3.dshnative.ui.DshNativeTheme
import com.ila3.dshnative.ui.DshTheme
import com.ila3.dshnative.ui.DshThemeMode
import com.ila3.dshnative.ui.dshThemeFrom
import com.ila3.dshnative.ui.NativeApp
import com.ila3.dshnative.ui.NativeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: NativeViewModel by viewModels()
    private var askedNotifications = false
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = getSharedPreferences("dsh_native", MODE_PRIVATE).getString("theme_mode", null)
        DshTheme.mode = dshThemeFrom(saved)
        NativeNotifications(this).ensureChannels()
        handleIntent(intent)
        setContent {
            DshNativeTheme {
                NativeApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(NativeNotifications.EXTRA_TAB) == "inbox") {
            viewModel.openInbox()
        }
        val sessionId = intent?.getStringExtra(NativeNotifications.EXTRA_SESSION).orEmpty()
        if (sessionId.isNotEmpty() && viewModel.activeSessionId != sessionId) {
            viewModel.openSessionFromNotification(sessionId)
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        viewModel.setForeground(true)
        KeepAliveService.stop(this)
        viewModel.resumePendingInstall(this)
        if (!askedNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotifications = true
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
        viewModel.setForeground(false)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && viewModel.keepAliveEnabled) {
            KeepAliveService.start(this)
        }
    }

    companion object {
        @Volatile
        var isForeground = false
            private set
    }
}
