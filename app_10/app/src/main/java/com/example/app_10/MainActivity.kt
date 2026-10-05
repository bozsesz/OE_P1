package com.example.app_10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.app_10.ui.MainTabletApp
import com.example.app_10.ui.theme.App_10Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App_10Theme {
                MainTabletApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
