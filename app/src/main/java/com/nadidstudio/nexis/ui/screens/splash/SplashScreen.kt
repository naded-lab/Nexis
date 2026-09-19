package com.nadidstudio.nexis.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.R

private val NexisTeal = Color(0xFF0F6E56)
private val NexisTealLight = Color(0xFF3FA98A)
private val NexisWhite = Color(0xFFFFFFFF)

@Composable
fun NexisSplashScreen(
    holdMillis: Long = 1400,
    onFinished: () -> Unit
) {
    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, animationSpec = tween(durationMillis = 450))
        logoScale.animateTo(1f, animationSpec = tween(durationMillis = 450))
        kotlinx.coroutines.delay(holdMillis)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(NexisTeal, NexisTealLight, NexisWhite)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ic_nexis_logo),
                contentDescription = "Nexis logo",
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer(scaleX = logoScale.value, scaleY = logoScale.value, alpha = logoAlpha.value)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NEXIS",
                color = NexisWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }
    }
}
