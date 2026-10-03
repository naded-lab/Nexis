package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.nadidstudio.nexis.assistants.Conversation
import com.nadidstudio.nexis.assistants.Project
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
fun NexisDrawer(
    onClose: () -> Unit,
    onChat: () -> Unit,
    onProjects: () -> Unit,
    onSettings: () -> Unit,
    onPlugins: () -> Unit,
    onSearchFiles: (String) -> Unit = {},
    onPickAssistant: () -> Unit,
    onPickModels: () -> Unit,
    onNewChat: () -> Unit = {},
    onOpenConversation: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxHeight().widthIn(max = 340.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            var query by remember { mutableStateOf("") }
            var searching by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nexis", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    onClick = { searching = !searching; if (!searching) query = "" },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(if (searching) Icons.Outlined.Close else Icons.Outlined.Search, "بحث", Modifier.size(20.dp))
                    }
                }
            }
            if (searching) { Spacer(Modifier.height(8.dp)); SearchRow(query) { query = it } }
            Spacer(Modifier.height(10.dp))
            DrawerRow("نماذج الذكاء الاصطناعي", NexisSparkle, onPickModels)
            DrawerRow("المشاريع", Icons.Outlined.FolderOpen, onProjects)
            DrawerRow("المهام المجدولة", Icons.Outlined.Schedule, onChat)
            DrawerRow("المكونات الإضافية", Icons.Outlined.Extension, onPlugins)
            HorizontalDivider(Modifier.padding(vertical = 10.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            val fileHits = if (query.isBlank()) 0 else countFilesMatching(query.trim())
            if (fileHits > 0) DrawerRow("ملفات مطابقة ($fileHits)", Icons.Outlined.Description) { onSearchFiles(query.trim()) }
            val all = NexisSessionStore.allConversations()
            val chats = if (query.isBlank()) all else all.filter { (_, c) ->
                c.title.contains(query.trim(), true) || c.messages.any { it.text.contains(query.trim(), true) }
            }
            var renaming by remember { mutableStateOf<Pair<Project, Conversation>?>(null) }
            var deleting by remember { mutableStateOf<Pair<Project, Conversation>?>(null) }
            if (chats.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (all.isEmpty()) "لا توجد محادثات محفوظة" else "لا نتائج", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), fontSize = 12.sp)
                }
            } else {
                val ordered = groupChats(chats).flatMap { it.second }
                var expanded by remember { mutableStateOf(false) }
                val collapsed = !expanded && query.isBlank() && ordered.size > 8
                val shown = if (collapsed) ordered.take(8) else ordered
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(shown.size, key = { shown[it].second.id }) { i ->
                        val pair = shown[i]
                        ConversationRow(
                            project = pair.first, convo = pair.second,
                            onOpen = { NexisSessionStore.openConversation(pair.first, pair.second); onOpenConversation() },
                            onRename = { renaming = pair },
                            onPin = { NexisSessionStore.togglePin(pair.second) },
                            onDelete = { deleting = pair }
                        )
                    }
                    if (collapsed) item {
                        Text(
                            "عرض الكل...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 10.dp, vertical = 12.dp)
                        )
                    }
                }
            }
            renaming?.let { (_, c) ->
                var name by remember(c.id) { mutableStateOf(c.title) }
                AlertDialog(
                    onDismissRequest = { renaming = null },
                    title = { Text("إعادة تسمية المحادثة") },
                    text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
                    confirmButton = { TextButton(onClick = { NexisSessionStore.renameConversation(c, name); renaming = null }) { Text("حفظ") } },
                    dismissButton = { TextButton(onClick = { renaming = null }) { Text("إلغاء") } }
                )
            }
            deleting?.let { (p, c) ->
                NexisConfirmDialog(
                    title = "حذف المحادثة؟",
                    message = "«${c.title}» ستُحذف نهائيًا ولا يمكن استرجاعها.",
                    confirmText = "حذف",
                    destructive = true,
                    onConfirm = { NexisSessionStore.deleteConversation(p, c); deleting = null },
                    onDismiss = { deleting = null }
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onNewChat, shape = RoundedCornerShape(50), color = NexisPalette.Accent, contentColor = Color.White) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("محادثة جديدة", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(onClick = onSettings, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(48.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Settings, "الإعدادات", Modifier.size(22.dp)) }
                }
            }
        }
    }
}

@Composable private fun SearchRow(query: String, onQuery: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("بحث في المحادثات والملفات", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                androidx.compose.foundation.text.BasicTextField(
                    value = query, onValueChange = onQuery, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(NexisPalette.Accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) Icon(Icons.Outlined.Close, "مسح", Modifier.size(16.dp).clickable { onQuery("") }, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
    }
}

/** Full-width pill, green-gradient, sparkles + label — the new top-of-drawer
 * entry point into the AI model picker (see ModelSheet). Fully rounded
 * corners on purpose, distinct from every other (rectangular) drawer row. */
@Composable private fun AiModelsPillButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(NexisSparkle, null, Modifier.size(20.dp), tint = NexisPalette.Accent)
            Spacer(Modifier.width(10.dp))
            Text("نماذج الذكاء الاصطناعي", fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        }
    }
}

/** Nexis sparkle mark (vector). */
private val NexisSparkle: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    androidx.compose.ui.graphics.vector.ImageVector.Builder(
        name = "NexisSparkle", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathBuilder().apply {
            moveTo(12f, 2f)
            curveTo(12.6f, 7.5f, 16.5f, 11.4f, 22f, 12f)
            curveTo(16.5f, 12.6f, 12.6f, 16.5f, 12f, 22f)
            curveTo(11.4f, 16.5f, 7.5f, 12.6f, 2f, 12f)
            curveTo(7.5f, 11.4f, 11.4f, 7.5f, 12f, 2f)
            close()
        }.nodes,
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ).build()
}

@Composable private fun DrawerRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(16.dp))
            Text(text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable private fun SettingsPill(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(onClick = onClick, shape = RoundedCornerShape(15.dp), color = NexisPalette.Accent, contentColor = Color.White) {
            Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("الإعدادات", fontSize = 13.sp)
            }
        }
    }
}

/** Pinned first, then by recency buckets. */
private fun groupChats(chats: List<Pair<Project, Conversation>>): List<Pair<String, List<Pair<Project, Conversation>>>> {
    val dayMs = 24L * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }
    val today = cal.timeInMillis
    val out = linkedMapOf<String, MutableList<Pair<Project, Conversation>>>()
    fun add(k: String, v: Pair<Project, Conversation>) { out.getOrPut(k) { mutableListOf() }.add(v) }
    for (p in chats) {
        val t = p.second.messages.last().timestampMillis
        when {
            p.second.pinned -> add("المثبّتة", p)
            t >= today -> add("اليوم", p)
            t >= today - dayMs -> add("أمس", p)
            t >= today - 7 * dayMs -> add("آخر 7 أيام", p)
            else -> add("أقدم", p)
        }
    }
    val order = listOf("المثبّتة", "اليوم", "أمس", "آخر 7 أيام", "أقدم")
    return order.mapNotNull { k -> out[k]?.let { k to it } }
}

@Composable private fun ConversationRow(
    project: Project, convo: Conversation,
    onOpen: () -> Unit, onRename: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit
) {
    val active = convo.id == NexisSessionStore.currentConversationId
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        color = if (active) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (convo.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(13.dp).padding(end = 0.dp), tint = NexisPalette.Accent)
            if (convo.pinned) Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(convo.title, fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Text(
                    when (project.assistantRole) {
                        AssistantRole.CODING -> "مساعد البرمجة"
                        AssistantRole.CHAT -> "محادثة"
                        AssistantRole.LOCAL -> "محلي"
                    },
                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                )
            }
            if (NexisSessionStore.isConversationSending(convo.id)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = NexisPalette.Accent)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, "خيارات", Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("إعادة تسمية", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(if (convo.pinned) "إلغاء التثبيت" else "تثبيت", fontSize = 13.sp) }, leadingIcon = { Icon(Icons.Outlined.PushPin, null, Modifier.size(18.dp)) }, onClick = { menu = false; onPin() })
                    DropdownMenuItem(text = { Text("حذف", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
