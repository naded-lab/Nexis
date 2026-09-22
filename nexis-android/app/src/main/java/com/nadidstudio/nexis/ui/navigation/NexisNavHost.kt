package com.nadidstudio.nexis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nadidstudio.nexis.ui.screens.login.NexisLoginScreen
import com.nadidstudio.nexis.ui.screens.shell.NexisShell
import com.nadidstudio.nexis.ui.screens.splash.NexisSplashScreen

object NexisRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SHELL = "shell"
}

@Composable
fun NexisNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = NexisRoutes.SPLASH) {

        composable(NexisRoutes.SPLASH) {
            NexisSplashScreen(
                onFinished = {
                    // TODO: once the "head" connectivity/model-health check can persist
                    // a logged-in session, branch here instead of always going to Login.
                    navController.navigate(NexisRoutes.LOGIN) {
                        popUpTo(NexisRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(NexisRoutes.LOGIN) {
            NexisLoginScreen(
                onGoogleSignIn = {
                    // TEMPORARY BYPASS: real Google OAuth is a separate, not-yet-built task.
                    navController.navigate(NexisRoutes.SHELL) {
                        popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                    }
                },
                onGithubSignIn = {
                    // TEMPORARY BYPASS — same reasoning; GitHub OAuth (incl. PAT
                    // capture for the backup feature) is still a separate task.
                    navController.navigate(NexisRoutes.SHELL) {
                        popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // Post-login: Drawer + Chat/Projects/Settings, per the approved
        // nexis-all-screens-light-dark.html design — see NexisShell.
        composable(NexisRoutes.SHELL) {
            NexisShell(
                onLogout = {
                    navController.navigate(NexisRoutes.LOGIN) {
                        popUpTo(NexisRoutes.SHELL) { inclusive = true }
                    }
                }
            )
        }
    }
}
