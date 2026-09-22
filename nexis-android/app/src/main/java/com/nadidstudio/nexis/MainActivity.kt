package com.nadidstudio.nexis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)

        // No branded splash screen at all — not the old animated Compose one,
        // not an OS-level teal/logo one either. App shows the real UI
        // (Login) the instant the first frame is ready.
        //
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
