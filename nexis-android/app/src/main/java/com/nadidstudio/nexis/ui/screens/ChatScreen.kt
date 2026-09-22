package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.nadidstudio.nexis.R
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.session.UiMessage
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
fun ChatScreen(onOpenDrawer: () -> Unit, onAssistant: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var showTools by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val messages = NexisSessionStore.messages
    val thinking = NexisSessionStore.sending
    val error = NexisSessionStore.lastError

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopBar(onOpenDrawer = onOpenDrawer, onAssistant = onAssistant)

            if (messages.isEmpty()) Box(Modifier.weight(1f)) { EmptyChat() } else LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(messages) { MessageBubble(it) }
                if (thinking) item { ThinkingBubble() }
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            if (showTools) ToolRow(onClose = { showTools = false })
            Composer(
                value = input,
                onValueChange = { input = it },
                onTools = { showTools = !showTools },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !thinking) {
                        NexisSessionStore.addUserMessageOptimistically(text)
                        input = ""
                        scope.launch { NexisSessionStore.send(text) }
                    }
                }
            )
        }
    }
}

// Header per the reference screenshot: no hard-edged shapes anywhere. Menu
// and new-chat each sit in their own soft circular button, and the
// assistant selector is a full pill (rounded ends) in dead-center — a Box
// (not a Row) so centering holds regardless of how wide the two side
// buttons are. No divider under the header either, matching the reference.
@Composable private fun TopBar(onOpenDrawer: () -> Unit, onAssistant: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        AssistantPill(onClick = onAssistant, modifier = Modifier.align(Alignment.Center))
        CircleIconButton(onClick = onOpenDrawer, modifier = Modifier.align(Alignment.CenterStart)) {
            MenuGlyph(tint = MaterialTheme.colorScheme.onSurface)
        }
        // New-chat: pencil/compose glyph — reads clearly as "start a new
        // chat", unlike a refresh/loop icon which was being read as "redo".
        CircleIconButton(onClick = { NexisSessionStore.newChat() }, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Outlined.Create, "محادثة جديدة", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun CircleIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

// Full pill (rounded ends), a hairline accent border, a dropdown chevron,
// the role name, then a small role icon — matching the reference capsule.
@Composable private fun AssistantPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, NexisPalette.Accent.copy(alpha = .28f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = NexisPalette.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(NexisSessionStore.selectedRole.title(), fontSize = 12.sp, color = NexisPalette.Accent, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Outlined.Code, null, tint = NexisPalette.Accent, modifier = Modifier.size(14.dp))
        }
    }
}

// Three equal-width bars, evenly spaced — a single consistent glyph rather
// than the previous tapered/hamburger look.
@Composable private fun MenuGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val strokeH = 2.dp.toPx()
        val corner = CornerRadius(strokeH / 2f)
        val gap = (size.height - strokeH * 3f) / 2f
        repeat(3) { i ->
            val y = i * (strokeH + gap)
            drawRoundRect(color = tint, topLeft = Offset(0f, y), size = Size(size.width, strokeH), cornerRadius = corner)
        }
    }
}

// A small, quiet watermark of the real brand mark, centered — per the
// reference screenshot, nothing else (no heading, no subtext). Kept small
// and very low-opacity on purpose so it reads as a subtle texture, not a
// dominant logo taking over the empty state.
@Composable private fun EmptyChat() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_nexis_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(108.dp),
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
    }
}

@Composable private fun MessageBubble(message: UiMessage) {
    val user = message.fromUser
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.Start else Arrangement.End) {
        Surface(color = if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, contentColor = if (user) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, shape = if (user) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp), modifier = Modifier.widthIn(max = 330.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(message.text, fontSize = 14.sp, lineHeight = 22.sp)
                if (message.time.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Text(message.time, fontSize = 10.sp, color = LocalContentColor.current.copy(alpha = .52f))
                }
            }
        }
    }
}

@Composable private fun ThinkingBubble() { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)) { Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { repeat(3) { Box(Modifier.size(5.dp).background(NexisPalette.Muted, RoundedCornerShape(50))) } } } } }

// Input bar per the reference: a green circular voice button and a plain
// mic icon sit OUTSIDE the field, on its far physical-left edge; the white
// pill itself only holds the leading action (add, or send once there's
// text) and the "اسأل" placeholder. All three elements share one fixed
// height so they line up cleanly instead of the pill looking taller.
//
// Code order matters here: under the app's RTL layout direction, a Row's
// first child lands at the physical-right edge and its last child at the
// physical-left edge. The pill is coded first (→ right, where Arabic text
// naturally starts), and the green circle last (→ far left), matching the
// reference exactly — getting this order backwards is what made the bar
// look mirrored.
private val ComposerElementHeight = 46.dp

@Composable private fun Composer(value: String, onValueChange: (String) -> Unit, onTools: () -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(modifier = Modifier.weight(1f).height(ComposerElementHeight), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainer, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotBlank()) {
                    FilledIconButton(onClick = onSend, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.ArrowUpward, "إرسال", Modifier.size(17.dp)) }
                } else {
                    IconButton(onClick = onTools, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, "إضافة") }
                }
                TextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = { Text("اسأل", color = NexisPalette.Muted) }, maxLines = 1, singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
            }
        }
        IconButton(onClick = {}, modifier = Modifier.size(ComposerElementHeight)) {
            Icon(Icons.Outlined.MicNone, "تسجيل صوتي", tint = MaterialTheme.colorScheme.onSurface)
        }
        Surface(onClick = {}, shape = CircleShape, color = NexisPalette.Accent, modifier = Modifier.size(ComposerElementHeight)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.GraphicEq, "رسالة صوتية", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable private fun ToolRow(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("إرفاق", fontSize = 12.sp, color = NexisPalette.Muted, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.Close, "إغلاق", Modifier.size(15.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AttachTile("ملف", Icons.Outlined.AttachFile, Modifier.weight(1f))
            AttachTile("صورة", Icons.Outlined.Image, Modifier.weight(1f))
            AttachTile("كاميرا", Icons.Outlined.PhotoCamera, Modifier.weight(1f))
        }
    }
}

@Composable private fun AttachTile(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(onClick = {}, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(38.dp).background(NexisPalette.Accent.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = NexisPalette.Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}
