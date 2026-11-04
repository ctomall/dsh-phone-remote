package com.ila3.dshnative

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ila3.dshnative.ui.DshNativeTheme
import com.ila3.dshnative.ui.DshTheme
import com.ila3.dshnative.ui.DshThemeMode
import com.ila3.dshnative.ui.NativeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = getSharedPreferences("dsh_native", MODE_PRIVATE).getString("theme_mode", "paper")
        DshTheme.mode = if (saved == "aurora") DshThemeMode.AURORA else DshThemeMode.PAPER
        setContent {
            DshNativeTheme {
                NativeApp()
            }
        }
    }
}
