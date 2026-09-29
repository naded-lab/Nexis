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
    onPickAssistant: () -> Unit,
    onPickModels: () -> Unit,
    onOpenConversation: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxHeight().widthIn(max = 340.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "إغلاق") }
            }
            Spacer(Modifier.height(14.dp))
            var query by remember { mutableStateOf("") }
            SearchRow(query) { query = it }
            Spacer(Modifier.height(14.dp))
            // Prominent entry point for picking the AI MODEL(S) — see ModelSheet.
            // Replaces the old "اختيار المساعد" block, which was dropped from
            // the drawer (still reachable from the chat top bar chip).
            AiModelsPillButton(onClick = onPickModels)
            Spacer(Modifier.height(10.dp))
            DrawerRow("المشاريع", Icons.Outlined.FolderOpen, onProjects)
            DrawerRow("المهام المجدولة", Icons.Outlined.Schedule, onChat)
            DrawerRow("المكونات الإضافية", Icons.Outlined.Extension, onPlugins)
            SectionLabel("المحادثات")
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
                val groups = groupChats(chats)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    groups.forEach { (label, rows) ->
                        item(key = "h_$label") { SectionLabel(label) }
                        items(rows.size, key = { rows[it].second.id }) { i ->
                            val pair = rows[i]
                            ConversationRow(
                                project = pair.first, convo = pair.second,
                                onOpen = { NexisSessionStore.openConversation(pair.first, pair.second); onOpenConversation() },
                                onRename = { renaming = pair },
                                onPin = { NexisSessionStore.togglePin(pair.second) },
                                onDelete = { deleting = pair }
                            )
                        }
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
                AlertDialog(
                    onDismissRequest = { deleting = null },
                    title = { Text("حذف المحادثة؟") },
                    text = { Text("«${c.title}» ستُحذف نهائيًا.") },
                    confirmButton = { TextButton(onClick = { NexisSessionStore.deleteConversation(p, c); deleting = null }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
                    dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }
                )
            }
            SettingsPill(onClick = onSettings)
        }
    }
}

@Composable private fun SearchRow(query: String, onQuery: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("بحث في المحادثات", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
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
        shape = RoundedCornerShape(50),
        color = Color.Transparent
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(listOf(NexisPalette.Accent, NexisPalette.Accent2)),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("نماذج الذكاء الاصطناعي", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(17.dp), tint = Color.White)
        }
    }
}

@Composable private fun DrawerRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(13.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
            Spacer(Modifier.width(11.dp))
            Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
