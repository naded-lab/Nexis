package com.nadidstudio.nexis.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.R
import com.nadidstudio.nexis.ui.theme.NexisColors

/** Shared shape/height/border so Google and GitHub buttons are visually identical
 * (this is the fix approved earlier for the sign-in buttons). */
private val AuthButtonShape = RoundedCornerShape(14.dp)
private val AuthButtonHeight = 52.dp

@Composable
fun NexisLoginScreen(
    onGoogleSignIn: () -> Unit,
    onGithubSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(colors = listOf(NexisColors.Teal, NexisColors.TealLight, NexisColors.White))
            )
            .padding(horizontal = 28.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Logo + wordmark float in the open space above the buttons instead of
        // sitting dead-center, so the sign-in actions can anchor near the bottom.
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Approved hexagon-ribbon logo (white cutout, on the teal background).
            Image(
                painter = painterResource(id = R.drawable.ic_nexis_logo),
                contentDescription = "Nexis logo",
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "NEXIS",
                color = NexisColors.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        AuthButton(
            label = "Continue with Google",
            iconRes = R.drawable.ic_google,
            onClick = onGoogleSignIn
        )
        Spacer(modifier = Modifier.height(14.dp))
        AuthButton(
            label = "Continue with GitHub",
            iconRes = R.drawable.ic_github,
            onClick = onGithubSignIn
        )
    }
}

@Composable
private fun AuthButton(
    label: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(AuthButtonHeight),
        shape = AuthButtonShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, NexisColors.ButtonBorderGray),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = NexisColors.White,
            contentColor = NexisColors.TextDark
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
