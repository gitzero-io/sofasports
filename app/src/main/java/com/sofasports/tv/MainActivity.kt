package com.sofasports.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sofasports.tv.data.config.ConfigRepository
import com.sofasports.tv.ui.KillSwitchScreen
import com.sofasports.tv.ui.nav.SofaNavHost
import com.sofasports.tv.ui.theme.SofaSportsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var config: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SofaSportsTheme {
                val cfg by config.config.collectAsStateWithLifecycle()
                if (cfg.killSwitch.appliesTo(BuildConfig.VERSION_CODE)) {
                    KillSwitchScreen(cfg.killSwitch.message)
                } else {
                    SofaNavHost()
                }
            }
        }
    }
}
