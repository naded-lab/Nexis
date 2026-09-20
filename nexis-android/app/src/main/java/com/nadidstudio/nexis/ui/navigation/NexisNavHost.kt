package com.nadidstudio.nexis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.data.InMemoryAppStore
import com.nadidstudio.nexis.ui.screens.home.NexisHomeScreen
import com.nadidstudio.nexis.ui.screens.login.NexisLoginScreen
import com.nadidstudio.nexis.ui.screens.projects.NexisConversationListScreen
import com.nadidstudio.nexis.ui.screens.projects.NexisProjectListScreen
import com.nadidstudio.nexis.ui.screens.splash.NexisSplashScreen

object NexisRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val PROJECTS = "projects/{role}"
    const val CONVERSATIONS = "conversations/{projectId}"

    fun projects(role: AssistantRole) = "projects/${role.name}"
    fun conversations(projectId: String) = "conversations/$projectId"
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
                    // TEMPORARY BYPASS: real Google OAuth is a separate, not-yet-built
                    // task. Going straight to Home for now so Home/Projects/
                    // Conversations are reachable and testable.
                    navController.navigate(NexisRoutes.HOME) {
                        popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                    }
                },
                onGithubSignIn = {
                    // TEMPORARY BYPASS — same reasoning; GitHub OAuth (incl. PAT
                    // capture for the backup feature) is still a separate task.
                    navController.navigate(NexisRoutes.HOME) {
                        popUpTo(NexisRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NexisRoutes.HOME) {
            NexisHomeScreen(
                onOpenAssistant = { role ->
                    navController.navigate(NexisRoutes.projects(role))
                },
                onOpenSettings = {
                    // TODO: Settings screen (API keys, per-assistant model
                    // toggles, edit profile) — next agreed piece after this one.
                }
            )
        }

        composable(
            route = NexisRoutes.PROJECTS,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = AssistantRole.valueOf(
                backStackEntry.arguments?.getString("role") ?: AssistantRole.CHAT.name
            )
            NexisProjectListScreen(
                role = role,
                onBack = { navController.popBackStack() },
                onOpenProject = { project ->
                    navController.navigate(NexisRoutes.conversations(project.id))
                }
            )
        }

        composable(
            route = NexisRoutes.CONVERSATIONS,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId")
            val project = projectId?.let { InMemoryAppStore.findProject(it) }
            if (project != null) {
                NexisConversationListScreen(
                    project = project,
                    onBack = { navController.popBackStack() },
                    onOpenConversation = {
                        // TODO: real Chat UI (send/receive through
                        // BaseAssistant.sendMessage) — next task after Settings.
                    }
                )
            }
        }
    }
}
