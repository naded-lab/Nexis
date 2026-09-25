package com.nadidstudio.nexis.ui.navigation

import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nadidstudio.nexis.ui.screens.login.NexisLoginScreen
import com.nadidstudio.nexis.ui.screens.shell.NexisShell

object NexisRoutes {
    const val LOGIN = "login"
    const val SHELL = "shell"
}

// The old branded (teal, animated-logo) splash screen has been removed
// entirely — no custom Compose splash, no OS-level branded splash either.
// The app opens straight into Login the instant the first frame renders.
@Composable
fun NexisNavHost(navController: NavHostController = rememberNavController()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val start = if (com.nadidstudio.nexis.data.ProfileStore.isSignedIn(context)) NexisRoutes.SHELL else NexisRoutes.LOGIN
    NavHost(navController = navController, startDestination = start) {

        composable(NexisRoutes.LOGIN) {
            NexisLoginScreen(
                onGoogleSignIn = {
                    if (com.nadidstudio.nexis.auth.GoogleAuth.WEB_CLIENT_ID.isBlank()) {
                        // Not configured yet: keep the temporary bypass so the app stays usable.
                        navController.navigate(NexisRoutes.SHELL) {
                            popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                        }
                        return@NexisLoginScreen
                    }
                    scope.launch {
                        com.nadidstudio.nexis.auth.GoogleAuth.signIn(context).fold(
                            onSuccess = { acc ->
                                com.nadidstudio.nexis.data.ProfileStore.signIn(context, acc.name, acc.email)
                                navController.navigate(NexisRoutes.SHELL) {
                                    popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                                }
                            },
                            onFailure = { e ->
                                android.widget.Toast.makeText(context, e.message ?: "فشل تسجيل الدخول", android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
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
                    com.nadidstudio.nexis.data.ProfileStore.signOut(context)
                    navController.navigate(NexisRoutes.LOGIN) {
                        popUpTo(NexisRoutes.SHELL) { inclusive = true }
                    }
                }
            )
        }
    }
}
