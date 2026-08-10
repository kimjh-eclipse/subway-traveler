package com.actimedi.travle

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.actimedi.travle.ui.TravleApp
import com.actimedi.travle.ui.theme.TravleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The header runs under the status bar, so its icons stay light; the bottom
        // nav is white, so the gesture bar keeps dark icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TravleTheme {
                TravleApp()
            }
        }
    }
}
