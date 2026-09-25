package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.backup.BackupState
import com.nadidstudio.nexis.data.ProfileStore
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.AppearanceMode
import com.nadidstudio.nexis.ui.theme.NexisAppearance
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit, onOpenLocalModel: () -> Unit = {}) {
    var keysDialogProvider by remember { mutableStateOf<String?>(null) }
    var showAccountSheet by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var profileName by remember { mutableStateOf(ProfileStore.name(ctx)) }
    var showProfileEdit by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var notificationsOn by remember { mutableStateOf(true) }

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

        item { ProfileCard(name = profileName, onClick = { showAccountSheet = true }) }

        item { SectionTitle("عام") }
        item {
            SettingGroup {
                SettingSwitchRow("الإشعارات", "تنبيهات التطبيق", Icons.Outlined.Notifications, notificationsOn) { notificationsOn = it }
                GroupDivider()
                SettingRow("اللغة", "العربية", Icons.Outlined.Language)
                GroupDivider()
                SettingRow("المظهر", NexisAppearance.mode.label, Icons.Outlined.Brightness6, onClick = { showAppearanceDialog = true })
                GroupDivider()
                SettingRow("التخزين", "إدارة البيانات والمحادثات", Icons.Outlined.Storage)
            }
        }

        item { SectionTitle("التطوير") }
        item {
            SettingGroup {
                SettingRow("النموذج المحلي", "Qwen GGUF من ذاكرة الهاتف", Icons.Outlined.Memory, onClick = onOpenLocalModel)
                GroupDivider()
                SettingRow("Termux", "ربط بيئة التطوير لاحقًا", Icons.Outlined.Terminal)
                GroupDivider()
                val connected = NexisSessionStore.keyStoreForSettings.hasAnyKey("github")
                SettingRow("GitHub", if (connected) "متصل" else "غير متصل", Icons.Outlined.Code, onClick = { keysDialogProvider = "github" })
                GroupDivider()
                SettingRow("النسخ الاحتياطي", BackupState.status, Icons.Outlined.CloudUpload, onClick = { showBackupDialog = true })
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SettingGroup { SettingRow("تسجيل خروج", "", Icons.AutoMirrored.Outlined.Logout, onClick = onLogout, danger = true) }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Text("الإصدار 0.1.0", color = subtleColor(), fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }

    if (showAccountSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = NexisPalette.LightMuted) }
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
                Text("الحساب الشخصي", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                SettingGroup {
                    SettingRow("تعديل الملف الشخصي", "الاسم", Icons.Outlined.Edit, onClick = { showAccountSheet = false; showProfileEdit = true })
                }
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

    if (showBackupDialog) BackupDialog(onDismiss = { showBackupDialog = false })

    val provider = keysDialogProvider
    if (provider != null) {
        ApiKeyDialog(providerId = provider, onDismiss = { keysDialogProvider = null })
    }
}

@Composable private fun subtleColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)

@Composable private fun SectionTitle(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) { Column(Modifier.fillMaxWidth(), content = content) }
}

@Composable private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 64.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
}

@Composable private fun IconBadge(icon: ImageVector, danger: Boolean = false) {
    val container = if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(Modifier.size(36.dp).background(container, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(19.dp), tint = tint)
    }
}

@Composable private fun ProfileCard(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).background(Brush.linearGradient(listOf(NexisPalette.Accent, NexisPalette.Accent2)), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.Person, null, Modifier.size(28.dp), tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (name.isBlank()) "الحساب الشخصي" else name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("الاسم والصورة والبيانات", fontSize = 12.sp, color = subtleColor())
            }
        }
    }
}

@Composable private fun SettingRow(
    title: String, sub: String, icon: ImageVector,
    onClick: (() -> Unit)? = null, danger: Boolean = false
) {
    var modifier = Modifier.fillMaxWidth() as Modifier
    if (onClick != null) modifier = modifier.clickable(onClick = onClick)
    Row(modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, danger)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = subtleColor(), maxLines = 1)
        }
    }
}

@Composable private fun SettingSwitchRow(title: String, sub: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = subtleColor(), maxLines = 1)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = NexisPalette.Accent))
    }
}
