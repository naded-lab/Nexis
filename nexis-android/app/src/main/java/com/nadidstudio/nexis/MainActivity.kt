package com.nadidstudio.nexis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nadidstudio.nexis.ui.navigation.NexisNavHost
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate(): draws the branded (teal +
        // logo) launch screen immediately, back to API 21, so the old blank
        // white flash on first install/open is gone.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // init() itself only sets up in-memory state — fast and safe to run
        // synchronously here. Anything that touches the Android Keystore
        // (encrypted API-key storage) is warmed up separately, off the main
        // thread, which is the actual fix for the old multi-second cold-start
        // freeze — it never blocks the UI thread now.
        NexisSessionStore.init(applicationContext)
        lifecycleScope.launch { NexisSessionStore.warmUpSecureStorage() }

        enableEdgeToEdge()
        setContent {
            NexisTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NexisNavHost()
                }
            }
        }
    }
}
