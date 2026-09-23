package com.nadidstudio.nexis.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.assistants.Project
import com.nadidstudio.nexis.data.InMemoryAppStore
import com.nadidstudio.nexis.data.ProjectFiles
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
fun ProjectScreen(onBack: () -> Unit, onOpenProject: (Project) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var filesOf by remember { mutableStateOf<Project?>(null) }
    val role = NexisSessionStore.selectedRole
    val projects = InMemoryAppStore.projectsFor(role)

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
                Text("المشاريع", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, "مشروع جديد") }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("كل مشروع يحتفظ بسياقه ومحادثاته وملفاته الخاصة.", color = NexisPalette.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 7.dp)) }
                items(projects) { project -> ProjectCard(project, onOpenProject, onFiles = { filesOf = project }) }
            }
        }
    }
    filesOf?.let { ProjectFilesDialog(it, onDismiss = { filesOf = null }) }
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("مشروع جديد") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المشروع") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) InMemoryAppStore.createProject(role, name.trim())
                    showCreate = false
                }) { Text("إنشاء") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("إلغاء") } }
        )
    }
}

@Composable private fun ProjectCard(project: Project, onOpen: (Project) -> Unit, onFiles: () -> Unit) {
    Surface(onClick = { onOpen(project) }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null, tint = NexisPalette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${project.uploadedFilePaths.size} ملفات · ${project.conversations.size} محادثات", fontSize = 11.sp, color = NexisPalette.Muted)
            }
            IconButton(onClick = onFiles, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.AttachFile, "ملفات المشروع", tint = NexisPalette.Accent, modifier = Modifier.size(19.dp)) }
            Icon(Icons.Outlined.ChevronLeft, null, tint = NexisPalette.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun ProjectFilesDialog(project: Project, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) error = ProjectFiles.import(context, project, uri)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ملفات ${project.name}") },
        text = {
            Column {
                if (project.uploadedFilePaths.isEmpty()) {
                    Text("لا توجد ملفات بعد. الملفات المرفوعة تتوفر لكل محادثات هذا المشروع.", color = NexisPalette.Muted, fontSize = 13.sp)
                }
                project.uploadedFilePaths.toList().forEach { path ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = NexisPalette.Accent)
                        Spacer(Modifier.width(8.dp))
                        Text(ProjectFiles.displayName(path), fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { ProjectFiles.remove(project, path) }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Outlined.Delete, "حذف", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = { error = null; pick.launch(arrayOf("*/*")) }) { Text("إضافة ملف") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
