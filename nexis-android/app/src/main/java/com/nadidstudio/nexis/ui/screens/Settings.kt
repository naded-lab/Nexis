package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.backup.BackupState
import com.nadidstudio.nexis.data.AssistantPrefs
import com.nadidstudio.nexis.data.ProfileStore
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.AppearanceMode
import com.nadidstudio.nexis.ui.theme.NexisAppearance
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenLocalModel: () -> Unit = {},
    onOpenModels: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenStorage: () -> Unit = {}
) {
    var keysDialogProvider by remember { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var profileName by remember { mutableStateOf(ProfileStore.name(ctx)) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showInstructions by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(AssistantPrefs.notificationsOn) }
    var showLanguage by remember { mutableStateOf(false) }
    var showTermux by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text("الإعدادات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Outlined.Close, "إغلاق") }
            }
            Spacer(Modifier.height(8.dp))
        }

        item { AccountHeader(name = profileName, onClick = { showAccountSheet = true }) }

        item {
            SettingsSection("عام") {
                SettingsSwitchRow("الإشعارات", Icons.Outlined.Notifications, "تنبيهات التطبيق", notificationsOn) { notificationsOn = it; AssistantPrefs.notificationsOn = it }
                SettingsRow("اللغة", Icons.Outlined.Language, value = "العربية", onClick = { showLanguage = true })
                SettingsRow("المظهر", Icons.Outlined.Brightness6, value = NexisAppearance.mode.label, onClick = { showAppearanceDialog = true })
                SettingsRow("التخزين", Icons.Outlined.Storage, sub = "إدارة البيانات والمحادثات", onClick = onOpenStorage)
            }
        }

        item {
            SettingsSection("الذكاء الاصطناعي") {
                SettingsRow("النموذج الافتراضي", Icons.Outlined.AutoAwesome, sub = "اختيار النماذج والمفاتيح", onClick = onOpenModels)
                SettingsRow(
                    "التعليمات الشخصية", Icons.Outlined.EditNote,
                    value = if (AssistantPrefs.instructions.isNotBlank()) "مفعّلة" else "غير مضبوطة",
                    onClick = { showInstructions = true }
                )
                SettingsRow("النموذج المحلي", Icons.Outlined.Memory, sub = "Qwen GGUF من ذاكرة الهاتف", onClick = onOpenLocalModel)
            }
        }

        item {
            SettingsSection("الخصوصية والأمان") {
                SettingsRow("الأذونات", Icons.Outlined.Security, sub = "إعدادات أذونات التطبيق في النظام", onClick = {
                    ctx.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                })
                SettingsRow("الخصوصية", Icons.Outlined.PrivacyTip, sub = "أين تُحفظ بياناتك ومفاتيحك", onClick = { showPrivacy = true })
            }
        }

        item {
            SettingsSection("للمطورين") {
                SettingsRow("Termux", Icons.Outlined.Terminal, sub = "بيئة التطوير", onClick = { showTermux = true })
                val connected = NexisSessionStore.keyStoreForSettings.hasAnyKey("github")
                SettingsRow(
                    "GitHub", Icons.Outlined.Code,
                    value = if (connected) "متصل" else "غير متصل",
                    valueColor = if (connected) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    onClick = { keysDialogProvider = "github" }
                )
                SettingsRow("النسخ الاحتياطي", Icons.Outlined.CloudUpload, sub = "آخر نسخة: ${BackupState.status}", onClick = onOpenBackup)
            }
        }

        item {
            SettingsSection("التطبيق") {
                SettingsRow("حول Nexis", Icons.Outlined.Info, value = "0.1.0", onClick = { showAbout = true })
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            SettingsRow("تسجيل الخروج", Icons.AutoMirrored.Outlined.Logout, onClick = { showLogoutConfirm = true }, danger = true)
        }
    }

    if (showInstructions) {
        var draft by remember { mutableStateOf(AssistantPrefs.instructions) }
        ModalBottomSheet(onDismissRequest = { showInstructions = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding()) {
                Text("التعليمات الشخصية", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("تُضاف لكل محادثة مع أي نموذج (حتى ${AssistantPrefs.MAX_INSTRUCTIONS} حرفًا)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it.take(AssistantPrefs.MAX_INSTRUCTIONS) },
                    minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                )
                Button(onClick = { AssistantPrefs.instructions = draft; showInstructions = false }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("حفظ") }
            }
        }
    }

    if (showLanguage) {
        ModalBottomSheet(onDismissRequest = { showLanguage = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("اللغة", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                SettingsRow(
                    "العربية", Icons.Outlined.Language, onClick = { showLanguage = false },
                    trailing = { Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = NexisPalette.Accent) }
                )
                SettingsRow("English", Icons.Outlined.Language, value = "قريبًا")
            }
        }
    }

    if (showTermux) InfoSheet(
        "Termux",
        "ربط Termux مخطط له: ينفّذ Nexis داخل بيئتك الأوامر التي يطلبها مساعد البرمجة (مثل git push) مع تأكيد قبل أي إجراء لا يمكن التراجع عنه. الميزة غير متاحة بعد.",
        onDismiss = { showTermux = false }
    )

    if (showPrivacy) InfoSheet(
        "الخصوصية",
        "محادثاتك وملفاتك تُحفظ على هاتفك فقط، ولا تُرفع إلا إذا فعّلت النسخ الاحتياطي إلى مستودع GitHub الخاص بك. مفاتيح API والتوكنات تُخزَّن مشفّرة على الجهاز ولا تدخل في النسخ الاحتياطي. رسائلك تُرسل إلى مزوّد النموذج الذي تختاره فقط، وتبقى محلية بالكامل عند استخدام مساعد ONX.",
        onDismiss = { showPrivacy = false }
    )

    if (showAbout) InfoSheet(
        "حول Nexis",
        "Nexis مركز ذكاء اصطناعي شخصي يجمع عدة نماذج وأدوات في تطبيق واحد. الإصدار 0.1.0",
        onDismiss = { showAbout = false }
    )

    if (showLogoutConfirm) {
        NexisConfirmDialog(
            title = "تسجيل الخروج؟",
            message = "سيتم تسجيل خروجك من هذا الحساب على هذا الجهاز.",
            confirmText = "تسجيل الخروج",
            destructive = true,
            icon = Icons.AutoMirrored.Outlined.Logout,
            onConfirm = { showLogoutConfirm = false; onLogout() },
            onDismiss = { showLogoutConfirm = false }
        )
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NexisPalette.LightMuted) }
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
                Text("الحساب الشخصي", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                SettingsRow("تعديل الملف الشخصي", Icons.Outlined.Edit, value = "الاسم", onClick = { showAccountSheet = false; showProfileEdit = true })
            }
        }
    }

    if (showProfileEdit) {
        var draft by remember { mutableStateOf(profileName) }
        AlertDialog(
            onDismissRequest = { showProfileEdit = false },
            title = { Text("تعديل الملف الشخصي") },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it }, singleLine = true, label = { Text("الاسم") }) },
            confirmButton = {
                TextButton(onClick = { ProfileStore.setName(ctx, draft); profileName = draft.trim(); showProfileEdit = false }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showProfileEdit = false }) { Text("إلغاء") } }
        )
    }

    if (showAppearanceDialog) {
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text("المظهر") },
            text = {
                Column {
                    AppearanceMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = NexisAppearance.mode == mode, onClick = { NexisAppearance.mode = mode; showAppearanceDialog = false })
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = NexisAppearance.mode == mode, onClick = { NexisAppearance.mode = mode; showAppearanceDialog = false })
                            Spacer(Modifier.width(8.dp))
                            Text(mode.label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearanceDialog = false }) { Text("تم") } }
        )
    }

    val provider = keysDialogProvider
    if (provider != null) {
        ApiKeyDialog(providerId = provider, onDismiss = { keysDialogProvider = null })
    }
}

@Composable private fun AccountHeader(name: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).background(NexisPalette.Accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Person, null, Modifier.size(26.dp), tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (name.isBlank()) "الحساب الشخصي" else name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("حساب Nexis", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
