package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.data.InMemoryAppStore
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import java.io.File
import java.util.Locale

@Composable
fun StorageScreen(onBack: () -> Unit) {
    val projects = InMemoryAppStore.codingProjects + InMemoryAppStore.chatProjects + InMemoryAppStore.localProjects
    val chats = projects.sumOf { p -> p.conversations.count { it.messages.isNotEmpty() } }
    val paths = projects.flatMap { it.uploadedFilePaths }
    val bytes = paths.sumOf { File(it).length() }
    val size = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(Locale.US, bytes / 1048576.0)
    }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("التخزين", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        }
        SettingsSection("البيانات المحلية") {
            SettingsRow("المشاريع", Icons.Outlined.Folder, value = "${projects.size}")
            SettingsRow("المحادثات", Icons.Outlined.ChatBubbleOutline, value = "$chats")
            SettingsRow("الملفات المرفوعة", Icons.Outlined.Description, value = "${paths.size} · $size")
        }
        SettingsSection("إدارة") {
            SettingsRow("حذف كل المحادثات", Icons.Outlined.DeleteOutline, sub = "تبقى المشاريع والملفات", danger = true, onClick = { confirm = true })
        }
    }

    if (confirm) {
        NexisConfirmDialog(
            title = "حذف كل المحادثات؟",
            message = "ستُحذف كل المحادثات من كل المساعدين. لا يمكن التراجع، لكن المشاريع والملفات تبقى.",
            confirmText = "حذف الكل", destructive = true,
            onConfirm = { confirm = false; NexisSessionStore.deleteAllConversations() },
            onDismiss = { confirm = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(title: String, body: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, fontSize = 14.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
