package com.nadidstudio.nexis.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import com.nadidstudio.nexis.data.InMemoryAppStore
import com.nadidstudio.nexis.data.ProjectFiles
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ───────────── shared helpers ───────────── */

private object FileFavorites {
    private fun prefs(c: Context) = c.getSharedPreferences("nexis_files", Context.MODE_PRIVATE)
    fun all(c: Context): Set<String> = prefs(c).getStringSet("fav", emptySet()) ?: emptySet()
    fun toggle(c: Context, path: String) {
        val s = all(c).toMutableSet()
        if (!s.add(path)) s.remove(path)
        prefs(c).edit().putStringSet("fav", s).apply()
    }
}

private data class FileEntry(val project: Project, val path: String) {
    val file get() = File(path)
    val name get() = file.name
}

private fun allFiles(): List<FileEntry> =
    (InMemoryAppStore.codingProjects + InMemoryAppStore.chatProjects + InMemoryAppStore.localProjects)
        .flatMap { p -> p.uploadedFilePaths.map { FileEntry(p, it) } }
        .sortedByDescending { it.file.lastModified() }

private fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> "%.1f MB".format(Locale.US, b / 1048576.0)
}

private fun formatDate(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale("ar")).format(Date(ms))

private fun fileSub(e: FileEntry): String =
    "${e.project.name} · ${formatSize(e.file.length())} · ${formatDate(e.file.lastModified())}"

@Composable
private fun ScreenTitle(text: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(20.dp)) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, "مسح", Modifier.size(18.dp)) } },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(top = 56.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FileListRow(e: FileEntry, fav: Boolean, onClick: () -> Unit) {
    SettingsRow(
        title = e.name, icon = Icons.Outlined.Description, sub = fileSub(e), onClick = onClick,
        trailing = if (fav) ({ Icon(Icons.Outlined.Star, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) }) else null
    )
}

/** Bottom sheet with info, preview and actions (favorite / share / delete) for one file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheetHost(entry: FileEntry, fav: Boolean, onToggleFav: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val preview = remember(entry.path) { runCatching { entry.file.readText().take(1500) }.getOrDefault("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(entry.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(fileSub(entry), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            if (preview.isNotBlank()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Text(preview, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 10, modifier = Modifier.fillMaxWidth().padding(12.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingsRow(if (fav) "إزالة من المفضلة" else "إضافة للمفضلة", if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder, onClick = onToggleFav)
            SettingsRow("مشاركة", Icons.Outlined.Share, onClick = {
                val text = runCatching { entry.file.readText() }.getOrDefault("").take(100_000)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, entry.name); putExtra(Intent.EXTRA_TEXT, text)
                }
                ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
            SettingsRow("حذف", Icons.Outlined.DeleteOutline, danger = true, onClick = { confirmDelete = true })
        }
    }

    if (confirmDelete) {
        NexisConfirmDialog(
            title = "حذف الملف؟",
            message = "سيُحذف «${entry.name}» من المشروع «${entry.project.name}» ولن يراه أي محادثة فيه.",
            confirmText = "حذف", destructive = true,
            onConfirm = { confirmDelete = false; ProjectFiles.remove(entry.project, entry.path); onDismiss() },
            onDismiss = { confirmDelete = false }
        )
    }
}

/* ───────────── Files ───────────── */

@Composable
fun FilesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 recent, 2 favorites
    var favs by remember { mutableStateOf(FileFavorites.all(ctx)) }
    var selected by remember { mutableStateOf<FileEntry?>(null) }

    val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    val files = allFiles().filter { e ->
        (query.isBlank() || e.name.contains(query.trim(), ignoreCase = true)) &&
            when (filter) { 1 -> e.file.lastModified() >= weekAgo; 2 -> e.path in favs; else -> true }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        ScreenTitle("الملفات", onBack)
        SearchField(query, { query = it }, "ابحث في الملفات")
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "الحديثة", "المفضلة").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        if (files.isEmpty()) {
            if (allFiles().isEmpty()) EmptyState(Icons.Outlined.FolderOpen, "لا توجد ملفات بعد", "الملفات التي ترفعها داخل أي مشروع تظهر هنا.")
            else EmptyState(Icons.Outlined.SearchOff, "لا توجد نتائج", "جرّب كلمة أخرى أو تصنيفًا مختلفًا.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(files, key = { it.path }) { e -> FileListRow(e, e.path in favs) { selected = e } }
            }
        }
    }

    selected?.let { e ->
        FileSheetHost(e, fav = e.path in favs,
            onToggleFav = { FileFavorites.toggle(ctx, e.path); favs = FileFavorites.all(ctx) },
            onDismiss = { selected = null })
    }
}

/* ───────────── Search ───────────── */

private fun snippet(text: String, q: String): String {
    val flat = text.replace("\n", " ")
    val i = flat.indexOf(q, ignoreCase = true)
    if (i < 0) return flat.take(70)
    return flat.substring((i - 20).coerceAtLeast(0), (i + 60).coerceAtMost(flat.length))
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenChat: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 chats, 2 files
    var favs by remember { mutableStateOf(FileFavorites.all(ctx)) }
    var selected by remember { mutableStateOf<FileEntry?>(null) }
    val q = query.trim()

    val chats: List<Pair<Project, Conversation>> = NexisSessionStore.allConversations().let { all ->
        if (q.isEmpty()) all.take(6)
        else all.filter { (_, c) -> c.title.contains(q, true) || c.messages.any { it.text.contains(q, true) } }
    }
    val files = if (q.isEmpty()) emptyList() else allFiles().filter { it.name.contains(q, true) }
    val showChats = filter != 2
    val showFiles = filter != 1

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        ScreenTitle("البحث", onBack)
        SearchField(query, { query = it }, "ابحث في المحادثات والملفات")
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("الكل", "المحادثات", "الملفات").forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        val nothing = (!showChats || chats.isEmpty()) && (!showFiles || files.isEmpty())
        if (nothing) {
            if (q.isEmpty()) EmptyState(Icons.Outlined.Search, "ابدأ بالكتابة", "ابحث في عناوين المحادثات ونصوصها وفي أسماء الملفات.")
            else EmptyState(Icons.Outlined.SearchOff, "لا توجد نتائج", "لم نجد شيئًا يطابق «$q».")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (showChats && chats.isNotEmpty()) {
                    item { SettingsSection(if (q.isEmpty()) "آخر المحادثات" else "المحادثات") {} }
                    items(chats, key = { it.second.id }) { (p, c) ->
                        val hit = c.messages.firstOrNull { q.isNotEmpty() && it.text.contains(q, true) }
                        SettingsRow(
                            title = c.title, icon = Icons.Outlined.ChatBubbleOutline,
                            sub = hit?.let { snippet(it.text, q) } ?: p.assistantRole.title(),
                            onClick = { NexisSessionStore.openConversation(p, c); onOpenChat() }
                        )
                    }
                }
                if (showFiles && files.isNotEmpty()) {
                    item { SettingsSection("الملفات") {} }
                    items(files, key = { it.path }) { e -> FileListRow(e, e.path in favs) { selected = e } }
                }
            }
        }
    }

    selected?.let { e ->
        FileSheetHost(e, fav = e.path in favs,
            onToggleFav = { FileFavorites.toggle(ctx, e.path); favs = FileFavorites.all(ctx) },
            onDismiss = { selected = null })
    }
}

/* ───────────── Tools ───────────── */

@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenLocalModel: () -> Unit,
    onOpenBackup: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle("الأدوات", onBack)
        SettingsSection("متاحة") {
            SettingsRow("البحث", Icons.Outlined.Search, sub = "في المحادثات وأسماء الملفات", onClick = onOpenSearch)
            SettingsRow("الملفات", Icons.Outlined.FolderOpen, sub = "ملفات المشاريع والمفضلة", onClick = onOpenFiles)
            SettingsRow("المشاريع", Icons.Outlined.Folder, sub = "محادثات وملفات كل مشروع", onClick = onOpenProjects)
            SettingsRow("النموذج المحلي", Icons.Outlined.Memory, sub = "مساعد ONX بدون إنترنت", onClick = onOpenLocalModel)
            SettingsRow("النسخ الاحتياطي", Icons.Outlined.CloudUpload, sub = "نسخ واستعادة عبر GitHub", onClick = onOpenBackup)
        }
    }
}
