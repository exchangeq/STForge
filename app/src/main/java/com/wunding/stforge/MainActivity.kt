package com.wunding.stforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.wunding.stforge.ui.StForgeApp
import com.wunding.stforge.ui.StForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StForgeTheme { StForgeApp(this) } }
    }
}
