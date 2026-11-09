package com.densmac.compass

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.densmac.compass.data.HapticViewHolder
import com.densmac.compass.domain.ThemeMode
import com.densmac.compass.ui.AppRoot
import com.densmac.compass.ui.settings.SettingsViewModel
import com.densmac.compass.ui.theme.CompassTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var hapticView: HapticViewHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = viewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val theme = settings.theme
            DisposableEffect(theme) {
                val bars = if (theme == ThemeMode.LIGHT) {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }
            CompassTheme(theme) { AppRoot(settingsViewModel) }
        }
        hapticView.view = window.decorView
    }

    override fun onDestroy() {
        if (hapticView.view === window.decorView) hapticView.view = null
        super.onDestroy()
    }
}
