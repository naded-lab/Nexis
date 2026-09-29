package com.nadidstudio.nexis.ui.screens

import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.assistants.AssistantRole
import com.nadidstudio.nexis.orchestration.ModelRegistry
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisPalette

/** Display strings for the real [AssistantRole] enum (kept out of the enum itself on purpose). */
fun AssistantRole.title(): String = when (this) {
    AssistantRole.CODING -> "مساعد البرمجة"
    AssistantRole.CHAT -> "المساعد العام"
    AssistantRole.LOCAL -> "مساعد ONX"
}

fun AssistantRole.subtitle(): String = when (this) {
    AssistantRole.CODING -> "تخطيط، بناء، تصحيح وشحن المشاريع"
    AssistantRole.CHAT -> "محادثة وأفكار ومساعدة يومية"
    AssistantRole.LOCAL -> "يعمل بالكامل على جهازك، بدون إنترنت ولا مفاتيح"
}

/** Display name for a provider id (falls back to the id itself for a custom-added model). */
fun providerDisplayName(providerId: String): String = when (providerId) {
    "claude" -> "Claude"
    "chatgpt" -> "ChatGPT"
    "gemini" -> "Gemini"
    "kimi" -> "KiMi"
    "local" -> "Qwen (محلي)"
    else -> ModelRegistry.adapterFor(providerId)?.displayName ?: providerId.replaceFirstChar { it.uppercase() }
}

@Composable
fun Brand(modifier: Modifier = Modifier) {
    // Text-only wordmark: the old sparkle/particles icon box was removed on purpose.
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("NEXIS", fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 11.sp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
    )
}

/** Picks the assistant ROLE (Coding / Chat) — not the AI model. */
@Composable
fun AssistantSheet(selected: AssistantRole, onSelect: (AssistantRole) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text("اختيار المساعد", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            AssistantSheetRow(AssistantRole.CODING, selected, onSelect)
            AssistantSheetRow(AssistantRole.CHAT, selected, onSelect)
            AssistantSheetRow(AssistantRole.LOCAL, selected, onSelect)
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun AssistantSheetRow(role: AssistantRole, selected: AssistantRole, onSelect: (AssistantRole) -> Unit) {
    val active = role == selected
    Surface(onClick = { onSelect(role) }, color = if (active) NexisPalette.Accent.copy(alpha = .09f) else Color.Transparent, shape = RoundedCornerShape(15.dp)) {
        // Right-to-left reading order: icon first (right edge under RTL),
        // then the name+description block, then the checkmark last (left edge).
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(if (active) NexisPalette.Accent else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(when (role) { AssistantRole.CODING -> Icons.Outlined.Code; AssistantRole.CHAT -> Icons.Outlined.ChatBubbleOutline; AssistantRole.LOCAL -> Icons.Outlined.PhoneAndroid }, null, tint = if (active) Color.White else NexisPalette.Accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(role.title(), fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Text(role.subtitle(), fontSize = 10.sp, color = NexisPalette.LightSecondary)
            }
            if (active) Icon(Icons.Outlined.Check, null, tint = NexisPalette.Accent, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * Picks which AI MODEL(S) are enabled for the current assistant role's
 * fallback chain — separate from [AssistantSheet]. Same visual language.
 *
 * This is the single entry point for AI models: it now also owns key
 * management directly (tap the key icon on a row), so nothing here requires
 * leaving the drawer to open general Settings.
 */
@Composable
fun ModelSheet(role: AssistantRole) {
    var keysDialogProvider by remember { mutableStateOf<String?>(null) }
    var showAddCustom by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text("نماذج الذكاء الاصطناعي", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
            Text("فعّل أو عطّل نموذجًا ضمن سلسلة ${role.title()}، وأدر مفاتيح API مباشرة", fontSize = 11.sp, color = NexisPalette.LightSecondary, modifier = Modifier.padding(bottom = 12.dp))
            refresh.let { }
            ModelRegistry.allProviderIds().filter { it != "local" }.forEach { providerId ->
                val keyCount = NexisSessionStore.keyStoreForSettings.getKeys(providerId).size
                ModelSheetRow(
                    providerId = providerId,
                    keyCount = keyCount,
                    enabled = NexisSessionStore.isProviderEnabled(role, providerId),
                    onToggle = { enabled -> NexisSessionStore.toggleProvider(role, providerId, enabled) },
                    onManageKeys = { keysDialogProvider = providerId }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { showAddCustom = true }.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(20.dp), tint = NexisPalette.Accent)
                Spacer(Modifier.width(12.dp))
                Text("إضافة نموذج مخصص", fontSize = 14.sp, color = NexisPalette.Accent)
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun AssistantSheetRow(role: AssistantRole, selected: AssistantRole, onSelect: (AssistantRole) -> Unit) {
    val active = role == selected
    Surface(onClick = { onSelect(role) }, color = if (active) NexisPalette.Accent.copy(alpha = .09f) else Color.Transparent, shape = RoundedCornerShape(15.dp)) {
        // Right-to-left reading order: icon first (right edge under RTL),
        // then the name+description block, then the checkmark last (left edge).
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(if (active) NexisPalette.Accent else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(when (role) { AssistantRole.CODING -> Icons.Outlined.Code; AssistantRole.CHAT -> Icons.Outlined.ChatBubbleOutline; AssistantRole.LOCAL -> Icons.Outlined.PhoneAndroid }, null, tint = if (active) Color.White else NexisPalette.Accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(role.title(), fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Text(role.subtitle(), fontSize = 10.sp, color = NexisPalette.LightSecondary)
            }
            if (active) Icon(Icons.Outlined.Check, null, tint = NexisPalette.Accent, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * Picks which AI MODEL(S) are enabled for the current assistant role's
 * fallback chain — separate from [AssistantSheet]. Same visual language.
 *
 * This is the single entry point for AI models: it now also owns key
 * management directly (tap the key icon on a row), so nothing here requires
 * leaving the drawer to open general Settings.
 */
@Composable
fun ModelSheet(role: AssistantRole) {
    var keysDialogProvider by remember { mutableStateOf<String?>(null) }
    var showAddCustom by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp)) {
            Text("نماذج الذكاء الاصطناعي", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
            Text("فعّل أو عطّل نموذجًا ضمن سلسلة ${role.title()}، وأدر مفاتيح API مباشرة", fontSize = 11.sp, color = NexisPalette.LightSecondary, modifier = Modifier.padding(bottom = 12.dp))
            refresh.let { }
            ModelRegistry.allProviderIds().filter { it != "local" }.forEach { providerId ->
                val keyCount = NexisSessionStore.keyStoreForSettings.getKeys(providerId).size
                ModelSheetRow(
                    providerId = providerId,
                    keyCount = keyCount,
                    enabled = NexisSessionStore.isProviderEnabled(role, providerId),
                    onToggle = { enabled -> NexisSessionStore.toggleProvider(role, providerId, enabled) },
                    onManageKeys = { keysDialogProvider = providerId }
                )
            }
            Spacer(Modifier.height(8.dp))
            Surface(onClick = { showAddCustom = true }, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(19.dp), tint = NexisPalette.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("إضافة نموذج مخصص", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
        }
    }

    val provider = keysDialogProvider
    if (provider != null) {
        ApiKeyDialog(providerId = provider, onDismiss = { keysDialogProvider = null })
    }

    if (showAddCustom) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("https://") }
        var model by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val isOpenRouter = url.contains("openrouter.ai")
        AlertDialog(
            onDismissRequest = { showAddCustom = false },
            title = { Text("إضافة نموذج مخصص") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أي خدمة متوافقة مع صيغة OpenAI (chat/completions)", fontSize = 11.sp, color = NexisPalette.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            name = "Groq"; url = "https://api.groq.com/openai/v1/chat/completions"; model = "llama-3.3-70b-versatile"
                        }) { Text("Groq", fontSize = 12.sp) }
                        OutlinedButton(onClick = {
                            name = "OpenRouter"; url = "https://openrouter.ai/api/v1/chat/completions"; model = ""
                        }) { Text("OpenRouter", fontSize = 12.sp) }
                    }
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("الاسم") })
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("رابط الـ endpoint") })
                    OutlinedTextField(model, { model = it }, singleLine = true, label = { Text(if (isOpenRouter) "النموذج (اختر واحدًا ينتهي بـ :free)" else "اسم النموذج") })
                    if (isOpenRouter) {
                        OutlinedButton(onClick = {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.nadidstudio.nexis.auth.OpenRouterAuth.startUrl())).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("ربط حساب OpenRouter", fontSize = 12.sp) }
                        OutlinedTextField(code, { code = it }, singleLine = true, label = { Text("الصق الكود الظاهر بالمتصفح (اختياري)") })
                    }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val id = com.nadidstudio.nexis.data.CustomModelStore.add(ctx, name, url, model) { err = it }
                    if (id != null) {
                        AssistantRole.entries.forEach { NexisSessionStore.toggleProvider(it, id, true) }
                        refresh++
                        if (isOpenRouter && code.isNotBlank()) {
                            busy = true
                            scope.launch {
                                com.nadidstudio.nexis.auth.OpenRouterAuth.exchange(code).fold(
                                    onSuccess = { key ->
                                        NexisSessionStore.keyStoreForSettings.addKey(id, key)
                                        refresh++
                                        showAddCustom = false
                                    },
                                    onFailure = { e ->
                                        err = "أُضيف النموذج، لكن الربط فشل: ${e.message}"
                                        busy = false
                                        keysDialogProvider = id
                                    }
                                )
                            }
                        } else {
                            showAddCustom = false
                            keysDialogProvider = id
                        }
                    }
                }) { Text(if (busy) "..." else "إضافة") }
            },
            dismissButton = { TextButton(onClick = { showAddCustom = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ModelSheetRow(
    providerId: String,
    keyCount: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onManageKeys: () -> Unit
) {
    // Flat row (no card, no icon badge): name + "cloud · key status" line,
    // accent check when enabled, key button at the end.
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onToggle(!enabled) }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(providerDisplayName(providerId), fontSize = 15.sp, fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal)
            Text(
                "سحابي · " + if (keyCount > 0) "$keyCount مفتاح مضاف" else "لا يوجد مفتاح",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) Icon(Icons.Outlined.Check, null, tint = NexisPalette.Accent, modifier = Modifier.size(20.dp).padding(end = 2.dp))
        IconButton(onClick = onManageKeys, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.VpnKey, "إدارة مفاتيح ${providerDisplayName(providerId)}", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** Shared key-management dialog — reachable both from the AI-models picker
 *  in the drawer (see [ModelSheet]) and from the GitHub row in Settings. */
@Composable
fun ApiKeyDialog(providerId: String, onDismiss: () -> Unit) {
    var newKey by remember { mutableStateOf("") }
    var keys by remember { mutableStateOf(NexisSessionStore.keyStoreForSettings.getKeys(providerId)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (providerId == "github") "توكن GitHub" else "مفاتيح ${providerDisplayName(providerId)}") },
        text = {
            Column {
                keys.forEach { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("•••• ${entry.keyValue.takeLast(4)}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            NexisSessionStore.keyStoreForSettings.removeKey(providerId, entry.id)
                            keys = NexisSessionStore.keyStoreForSettings.getKeys(providerId)
                        }) { Icon(Icons.Outlined.Delete, "حذف") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = newKey, onValueChange = { newKey = it }, label = { Text("مفتاح جديد") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newKey.isNotBlank()) {
                    NexisSessionStore.keyStoreForSettings.addKey(providerId, newKey.trim())
                    keys = NexisSessionStore.keyStoreForSettings.getKeys(providerId)
                    newKey = ""
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}


/**
 * Nexis confirmation dialog (Material 3, RTL-correct): rounded card, icon badge,
 * start-aligned title/body and two equal-width buttons — cancel on the start
 * (right) edge, the confirming action on the end (left) edge.
 */
@Composable
fun NexisConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "إلغاء",
    destructive: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.DeleteOutline,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        // Softer, darker scrim behind the card.
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)
            ?.window?.setDimAmount(0.62f)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val accent = if (destructive) MaterialTheme.colorScheme.error else NexisPalette.Accent
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)
            ) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp)) {
                    Box(
                        Modifier.size(42.dp).background(accent.copy(alpha = .14f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp)) }
                    Spacer(Modifier.height(14.dp))
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        message, fontSize = 13.sp, lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f),
                        maxLines = 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) { Text(dismissText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
                        ) { Text(confirmText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
