package com.sofasports.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sofasports.tv.ui.nav.SofaNavHost
import com.sofasports.tv.ui.theme.SofaSportsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SofaSportsTheme {
                SofaNavHost()
            }
        }
    }
}
