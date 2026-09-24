package com.nadidstudio.nexis.ui.screens.shell

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.nadidstudio.nexis.ui.screens.AssistantSheet
import com.nadidstudio.nexis.ui.screens.ChatScreen
import com.nadidstudio.nexis.ui.screens.ModelSheet
import com.nadidstudio.nexis.ui.screens.NexisDrawer
import com.nadidstudio.nexis.ui.screens.ProjectScreen
import com.nadidstudio.nexis.ui.screens.SettingsScreen
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisPalette
import kotlinx.coroutines.launch

/**
 * The post-login shell for the design in nexis-all-screens-light-dark.html:
 * a Drawer wrapping Chat/Projects/Settings as local pages (not separate
 * NavHost destinations), exactly as the uploaded UI package structured it —
 * only the data underneath (NexisSessionStore) is real. Reached from
 * NexisNavHost after Login, so Splash/Login keep their own real back-stack.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NexisShell(onLogout: () -> Unit = {}) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        var page by remember { mutableStateOf("chat") }
        var sheet by remember { mutableStateOf<String?>(null) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            // The drawer opens ONLY from the hamburger button (drawerState.open()).
            // Swipe-from-edge is off while closed; gestures are enabled only once it
            // is open so swipe-to-close and tapping the scrim still dismiss it.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                NexisDrawer(
                    onClose = { scope.launch { drawerState.close() } },
                    onChat = { page = "chat"; scope.launch { drawerState.close() } },
                    onProjects = { page = "projects"; scope.launch { drawerState.close() } },
                    onSettings = { page = "settings"; scope.launch { drawerState.close() } },
                    onPlugins = {
                        // TODO: real "plugins/add-ons" install screen — not built yet.
                        scope.launch { drawerState.close() }
                    },
                    onPickAssistant = { sheet = "assistant" },
                    onPickModels = { sheet = "models" }
                )
            }
        ) {
            when (page) {
                "projects" -> ProjectScreen(
                    onBack = { page = "chat" },
                    onOpenProject = { NexisSessionStore.openProject(it); page = "chat" }
                )
                "settings" -> SettingsScreen(
                    onBack = { page = "chat" },
                    onLogout = onLogout
                )
                else -> ChatScreen(
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onAssistant = { sheet = "assistant" }
                )
            }
        }

        if (sheet == "assistant") {
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NexisPalette.LightMuted) }
            ) {
                AssistantSheet(
                    selected = NexisSessionStore.selectedRole,
                    onSelect = { NexisSessionStore.selectRole(it); sheet = null }
                )
            }
        }

        if (sheet == "models") {
            ModalBottomSheet(
                onDismissRequest = { sheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle(color = NexisPalette.LightMuted) }
            ) {
                ModelSheet(role = NexisSessionStore.selectedRole)
            }
        }
    }
}
