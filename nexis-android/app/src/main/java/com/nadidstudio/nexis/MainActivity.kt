package com.nadidstudio.nexis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Nexis brand color (approved hexagon-logo teal)
private val NexisTeal = Color(0xFF0F6E56)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NexisHome()
                }
            }
        }
    }
}

/**
 * First-run placeholder screen.
 * Purpose right now: confirm the Termux -> GitHub -> GitHub Actions -> APK
 * pipeline works end to end before any assistant/model logic is added.
 */
@Composable
fun NexisHome() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nexis",
            color = NexisTeal,
            fontSize = 32.sp
        )
        Text(
            text = "Build pipeline OK — assistants coming next",
            fontSize = 14.sp
        )
    }
}
