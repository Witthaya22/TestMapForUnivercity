package th.ac.kmutnb.prachin.map

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.ui.AppNavHost
import th.ac.kmutnb.prachin.map.ui.Routes
import th.ac.kmutnb.prachin.map.ui.theme.KmutnbMapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MapApplication).container

        // The settings switch only stores the preference; the window flag is what actually
        // keeps the display awake. Follow it for the life of the activity so flipping the
        // switch takes effect immediately, without a restart.
        lifecycleScope.launch {
            container.preferences.keepScreenOn.collect { keepOn ->
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            KmutnbMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Onboarding is skipped once a pack has been downloaded. Reading the flag
                    // is a disk hop, so hold an empty surface for the frame or two it takes
                    // rather than starting on the wrong screen and jumping.
                    val offlineReady by produceState<Boolean?>(initialValue = null) {
                        value = container.preferences.offlineMapReady.first()
                    }

                    when (offlineReady) {
                        null -> Box(Modifier.fillMaxSize())
                        true -> AppNavHost(startDestination = Routes.MAP)
                        false -> AppNavHost(startDestination = Routes.ONBOARDING)
                    }
                }
            }
        }
    }
}
