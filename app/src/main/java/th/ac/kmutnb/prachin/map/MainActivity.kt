package th.ac.kmutnb.prachin.map

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiApp
import th.ac.kmutnb.prachin.map.ui.theme.KhaoYaiTheme

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
            KhaoYaiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Sign-in first; the walking navigation is one destination inside.
                    KhaoYaiApp()
                }
            }
        }
    }
}
