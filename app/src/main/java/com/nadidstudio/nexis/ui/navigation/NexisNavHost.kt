package com.nadidstudio.nexis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nadidstudio.nexis.ui.screens.login.NexisLoginScreen
import com.nadidstudio.nexis.ui.screens.splash.NexisSplashScreen

object NexisRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
}

@Composable
fun NexisNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = NexisRoutes.SPLASH) {

        composable(NexisRoutes.SPLASH) {
            NexisSplashScreen(
                onFinished = {
                    navController.navigate(NexisRoutes.LOGIN) {
                        popUpTo(NexisRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(NexisRoutes.LOGIN) {
            NexisLoginScreen(
                onGoogleSignIn = { /* TODO wire Google sign-in */ },
                onGithubSignIn = { /* TODO wire GitHub OAuth + PAT capture */ }
            )
        }

        composable(NexisRoutes.HOME) {
            // TODO: your existing/next home screen
        }
    }
}
