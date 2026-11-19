package com.example

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.DarkNavyBackground
import com.example.ui.theme.MaDInternetTVTheme
import com.example.ui.theme.ObsidianBlack

/**
 * Main activity for MaD Internet TV V5.5.
 * Applies and consumes systemBars + displayCutout insets once at the root in Portrait mode
 * to prevent double/triple status bar & navigation bar padding on Android 15/16/17 (e.g. Samsung S25 Ultra).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val barColor = Color.parseColor("#FF050811")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(barColor),
            navigationBarStyle = SystemBarStyle.dark(barColor)
        )
        updateWindowSystemBarsForOrientation(resources.configuration)

        setContent {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val portraitSafeInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

            MaDInternetTVTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBlack)
                        .then(
                            if (isPortrait) {
                                Modifier
                                    .windowInsetsPadding(portraitSafeInsets)
                                    .consumeWindowInsets(portraitSafeInsets)
                            } else {
                                Modifier
                            }
                        ),
                    color = DarkNavyBackground
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateWindowSystemBarsForOrientation(newConfig)
        window?.decorView?.requestLayout()
    }

    private fun updateWindowSystemBarsForOrientation(config: Configuration) {
        val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (isPortrait) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                window.attributes = params
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                window.attributes = params
            }
        }
    }
}
