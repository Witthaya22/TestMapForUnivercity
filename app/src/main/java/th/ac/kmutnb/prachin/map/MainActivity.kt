package th.ac.kmutnb.prachin.map

import android.os.Bundle
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
import kotlinx.coroutines.flow.first
import th.ac.kmutnb.prachin.map.ui.AppNavHost
import th.ac.kmutnb.prachin.map.ui.Routes
import th.ac.kmutnb.prachin.map.ui.theme.KmutnbMapTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MapApplication).container

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
