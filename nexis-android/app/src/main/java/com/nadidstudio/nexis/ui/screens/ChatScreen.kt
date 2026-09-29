package com.nadidstudio.nexis.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val streaming = NexisSessionStore.streamingText
    val clipboard = LocalClipboardManager.current
    val liked = remember { mutableStateMapOf<Int, Boolean>() }
    val error = NexisSessionStore.lastError
    val notice = NexisSessionStore.lastNotice
    val context = LocalContext.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) NexisSessionStore.importFile(context, uri)
    }

    LaunchedEffect(messages.size, thinking) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1 + if (thinking) 1 else 0)
    }
    LaunchedEffect(streaming?.length?.div(40)) {
        if (streaming != null && messages.isNotEmpty()) listState.scrollToItem(messages.size)
    }

    // Only a boolean flip (not every animation frame of the keyboard) triggers this,
    // so the screen doesn't recompose while the keyboard slides.
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val imeOpen by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    LaunchedEffect(imeOpen) {
        if (imeOpen && messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Scaffold must NOT apply any system/keyboard insets: the header handles the
        // status bar itself and only the bottom input area reacts to the keyboard.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TopBar(onOpenDrawer = onOpenDrawer, onAssistant = onAssistant)

            if (messages.isEmpty() && !thinking) Box(Modifier.weight(1f)) { EmptyChat(onPick = { input = it }) } else LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                itemsIndexed(messages) { pos, m ->
                    if (m.fromUser) {
                        UserMessage(
                            m, canEdit = !thinking,
                            onCopy = { clipboard.setText(AnnotatedString(m.text)) },
                            onEdit = { NexisSessionStore.takeForEdit(m.index)?.let { input = it } }
                        )
                    } else {
                        AssistantMessage(
                            m.text,
                            isLast = pos == messages.lastIndex && !thinking,
                            liked = liked[m.index],
                            onCopy = { clipboard.setText(AnnotatedString(m.text)) },
                            onRegenerate = { NexisSessionStore.regenerate() },
                            onLike = { if (liked[m.index] == true) liked.remove(m.index) else liked[m.index] = true },
                            onDislike = { if (liked[m.index] == false) liked.remove(m.index) else liked[m.index] = false },
                            onShare = {
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, m.text)
                                }
                                context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        )
                    }
                }
                if (thinking) item {
                    if (streaming.isNullOrEmpty()) ThinkingBubble() else StreamingMessage(streaming)
                }
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            if (notice != null) {
                Text(notice, color = NexisPalette.Accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            // The ONLY element that follows the keyboard. navigationBarsPadding() first,
            // then imePadding(): the gesture/nav-bar inset isn't counted twice, and the
            // whole bar (incl. the model pill) always stays fully on screen.
            Column(Modifier.navigationBarsPadding().imePadding()) {
                if (showTools) ToolRow(onClose = { showTools = false }, onFile = { showTools = false; pickFile.launch(arrayOf("*/*")) })
                Composer(
                    value = input,
                    busy = thinking,
                    onStop = { NexisSessionStore.stop() },
                    onValueChange = { input = it },
                    onTools = { showTools = !showTools },
                    onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty() && !thinking) {
                            NexisSessionStore.addUserMessageOptimistically(text)
                            input = ""
                            NexisSessionStore.send(text)
                        }
                    }
                )
            }
        }
    }
}

// Header per the reference screenshot: no hard-edged shapes anywhere. Menu
// and new-chat each sit in their own soft circular button, and the
// assistant selector is a full pill (rounded ends) in dead-center — a Box
// (not a Row) so centering holds regardless of how wide the two side
// buttons are. No divider under the header either, matching the reference.
@Composable private fun TopBar(onOpenDrawer: () -> Unit, onAssistant: () -> Unit) {
    // Compact bar: it owns the status-bar inset itself (Scaffold applies none) and
    // never reacts to the keyboard, so it stays pinned while typing.
    // Layout close to the reference bar: new-chat sits alone on the leading
    // side; the assistant selector and the drawer menu are grouped together
    // on the trailing side, right next to each other.
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleIconButton(onClick = { NexisSessionStore.newChat() }) {
            NewChatGlyph(tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.weight(1f))
        AssistantPill(onClick = onAssistant)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(onClick = onOpenDrawer) {
            MenuGlyph(tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable private fun CircleIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, border: BorderStroke? = null, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer, border = border) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

// Full pill (rounded ends) with a hairline accent border. A single dropdown
// chevron is the only "this opens a picker" cue — the earlier version also
// had a trailing code-brackets icon, which read as a second, redundant
// affordance next to the chevron.
@Composable private fun AssistantPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, NexisPalette.Accent.copy(alpha = .28f))
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = NexisPalette.Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(NexisSessionStore.selectedRole.title(), fontSize = 12.sp, color = NexisPalette.Accent, fontWeight = FontWeight.Medium)
        }
    }
}

// Two stacked bars, close together: the top one is longer than the bottom one.
@Composable private fun MenuGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 10.dp)) {
        val strokeH = 2.2.dp.toPx()
        val corner = CornerRadius(strokeH / 2f)
        val gap = 3.dp.toPx()
        drawRoundRect(color = tint, topLeft = Offset(0f, 0f), size = Size(size.width, strokeH), cornerRadius = corner)
        drawRoundRect(color = tint, topLeft = Offset(0f, strokeH + gap), size = Size(size.width * 0.6f, strokeH), cornerRadius = corner)
    }
}

// New chat: a rounded square (open at the top-right corner) with a pencil
// sticking out diagonally from that corner.
@Composable private fun NewChatGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val u = size.width / 20f
        val sw = 1.7f * u
        val box = androidx.compose.ui.graphics.Path().apply {
            moveTo(11f * u, 3.5f * u)
            lineTo(6f * u, 3.5f * u)
            quadraticBezierTo(3.5f * u, 3.5f * u, 3.5f * u, 6f * u)
            lineTo(3.5f * u, 14f * u)
            quadraticBezierTo(3.5f * u, 16.5f * u, 6f * u, 16.5f * u)
            lineTo(14f * u, 16.5f * u)
            quadraticBezierTo(16.5f * u, 16.5f * u, 16.5f * u, 14f * u)
            lineTo(16.5f * u, 10f * u)
        }
        drawPath(box, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        // pencil body
        drawLine(tint, Offset(10.5f * u, 9.5f * u), Offset(16.8f * u, 3.2f * u), strokeWidth = 3f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // pencil tip
        val tip = androidx.compose.ui.graphics.Path().apply {
            moveTo(8.2f * u, 11.8f * u)
            lineTo(9.2f * u, 9.4f * u)
            lineTo(10.6f * u, 10.8f * u)
            close()
        }
        drawPath(tip, tint)
    }
}

// Empty state: the brand mark, a greeting, and tappable starter prompts.
@Composable private fun EmptyChat(onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val name = remember { com.nadidstudio.nexis.data.ProfileStore.name(ctx) }
    val role = NexisSessionStore.selectedRole
    val suggestions = when (role) {
        com.nadidstudio.nexis.assistants.AssistantRole.CODING -> listOf(
            "اشرح لي هذا الكود: " to Icons.Outlined.Code,
            "ساعدني في إصلاح هذا الخطأ: " to Icons.Outlined.BugReport,
            "اقترح لي فكرة تطبيق وصمّمها" to Icons.Outlined.Lightbulb,
            "راجع هذا الكود وحسّنه: " to Icons.Outlined.FactCheck
        )
        else -> listOf(
            "اشرح لي مفهومًا ببساطة: " to Icons.Outlined.School,
            "لخّص لي هذا النص: " to Icons.Outlined.Notes,
            "ترجم لي إلى الإنجليزية: " to Icons.Outlined.Translate,
            "اقترح لي أفكارًا لـ " to Icons.Outlined.Lightbulb
        )
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(
            painter = painterResource(R.drawable.ic_nexis_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            colorFilter = ColorFilter.tint(NexisPalette.Accent)
        )
        Spacer(Modifier.height(18.dp))
        Text(if (name.isNotBlank()) "أهلاً $name" else "أهلاً بك", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("كيف أقدر أساعدك اليوم؟", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (text, icon) ->
                        Surface(
                            onClick = { onPick(text) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(12.dp).heightIn(min = 64.dp)) {
                                Icon(icon, null, tint = NexisPalette.Accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(text.trim().removeSuffix(":"), fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3)
                            }
                        }
                    }
                }
            }
        }
    }
}

// User: soft bubble. Under it: copy + edit.
@Composable private fun UserMessage(message: UiMessage, canEdit: Boolean, onCopy: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                message.text, fontSize = 15.sp, lineHeight = 23.sp,
                style = LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
        Row {
            ActionIcon(Icons.Outlined.ContentCopy, "نسخ", onCopy)
            if (canEdit) ActionIcon(Icons.Outlined.Edit, "تعديل", onEdit)
        }
    }
}

// Assistant: no bubble, full width, rendered Markdown. Actions underneath.
@Composable private fun AssistantMessage(
    text: String, isLast: Boolean, liked: Boolean?,
    onCopy: () -> Unit, onRegenerate: () -> Unit, onLike: () -> Unit, onDislike: () -> Unit, onShare: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        SelectionContainer { MarkdownText(text) }
        Row(Modifier.padding(top = 2.dp)) {
            ActionIcon(Icons.Outlined.ContentCopy, "نسخ", onCopy)
            if (isLast) ActionIcon(Icons.Outlined.Refresh, "إعادة توليد", onRegenerate)
            ActionIcon(if (liked == true) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpOffAlt, "أعجبني", onLike, active = liked == true)
            ActionIcon(if (liked == false) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDownOffAlt, "لم يعجبني", onDislike, active = liked == false)
            ActionIcon(Icons.Outlined.IosShare, "مشاركة", onShare)
        }
    }
}

// Reply that is still being produced.
@Composable private fun StreamingMessage(text: String) {
    Column(Modifier.fillMaxWidth()) { MarkdownText(text) }
}

@Composable private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, active: Boolean = false) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, label, Modifier.size(17.dp), tint = if (active) NexisPalette.Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}

@Composable private fun ThinkingBubble() {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                androidx.compose.animation.core.infiniteRepeatable(
                    androidx.compose.animation.core.tween(600, delayMillis = i * 150),
                    androidx.compose.animation.core.RepeatMode.Reverse
                ), label = "d$i"
            )
            Box(Modifier.size(7.dp).background(NexisPalette.Accent.copy(alpha = a), CircleShape))
        }
    }
}

// Input bar per the newest reference: one rounded card holding the text
// field on top and a control row underneath — attach, the active-model
// pill (English provider name, opens a plain model dropdown), mic, and send.
// Replaces the earlier separate-outer-circles layout.
@Composable private fun Composer(value: String, busy: Boolean, onStop: () -> Unit, onValueChange: (String) -> Unit, onTools: () -> Unit, onSend: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("اسأل", color = NexisPalette.Muted) },
                maxLines = 5,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onTools, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Add, "إرفاق") }
                Spacer(Modifier.width(2.dp))
                ModelPill()
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.MicNone, "تسجيل صوتي", tint = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.width(2.dp))
                Surface(
                    onClick = if (busy) onStop else onSend,
                    shape = CircleShape,
                    color = if (busy || value.isNotBlank()) NexisPalette.Accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (busy) Box(Modifier.size(12.dp).background(Color.White, RoundedCornerShape(3.dp)))
                        else Icon(Icons.Outlined.ArrowUpward, "إرسال", tint = if (value.isNotBlank()) Color.White else NexisPalette.Muted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// Shows the model actually in use right now — the first entry in the
// current role's fallback chain — by its plain English name (Claude,
// ChatGPT, Gemini…). Tapping it opens a plain dropdown to switch the active
// model: names only — no API-key status, no checkmarks, no custom-model
// button. Full model/key management lives only in the drawer's AI-models sheet.
@Composable private fun ModelPill() {
    val role = NexisSessionStore.selectedRole
    val active = NexisSessionStore.chainFor(role).firstOrNull() ?: "claude"
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(providerDisplayName(active), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Outlined.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NexisSessionStore.providerIds.forEach { id ->
                val selected = id == active
                DropdownMenuItem(
                    text = { Text(providerDisplayName(id), fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = { NexisSessionStore.setActiveProvider(role, id); expanded = false },
                    modifier = Modifier.background(if (selected) NexisPalette.Accent.copy(alpha = .10f) else Color.Transparent)
                )
            }
        }
    }
}

@Composable private fun ToolRow(onClose: () -> Unit, onFile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("إرفاق", fontSize = 12.sp, color = NexisPalette.Muted, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(26.dp)) { Icon(Icons.Outlined.Close, "إغلاق", Modifier.size(15.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AttachTile("ملف", Icons.Outlined.AttachFile, Modifier.weight(1f), onFile)
            AttachTile("صورة", Icons.Outlined.Image, Modifier.weight(1f))
            AttachTile("كاميرا", Icons.Outlined.PhotoCamera, Modifier.weight(1f))
        }
    }
}

@Composable private fun AttachTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(38.dp).background(NexisPalette.Accent.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = NexisPalette.Accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}
