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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.backup.BackupState
import com.nadidstudio.nexis.backup.GitHubBackup
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import kotlinx.coroutines.launch

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf(GitHubBackup.repo(context)) }
    var auto by remember { mutableStateOf(GitHubBackup.autoEnabled(context)) }
    val hasToken = NexisSessionStore.keyStoreForSettings.hasAnyKey("github")
    val repoOk = GitHubBackup.isValidRepo(repo)
    val canRun = hasToken && repoOk && !BackupState.running
    var confirmRestore by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text("النسخ الاحتياطي", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        }

        SettingsSection("الحالة") {
            SettingsRow("آخر نسخة", Icons.Outlined.History, value = BackupState.status)
            SettingsRow("مكان التخزين", Icons.Outlined.Cloud, value = "GitHub")
            SettingsSwitchRow("نسخ تلقائي بعد التغييرات", Icons.Outlined.Sync, checked = auto) { auto = it; GitHubBackup.setAuto(context, it) }
        }

        SettingsSection("المستودع") {
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it; GitHubBackup.setRepo(context, it) },
                label = { Text("المستودع (owner/repo)") },
                singleLine = true,
                isError = repo.isNotEmpty() && !repoOk,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (!hasToken) {
                Text("أضف توكن GitHub من الإعدادات ← للمطورين ← GitHub.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { scope.launch { GitHubBackup.backupNow(context) } },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text(if (BackupState.running) "جارٍ النسخ…" else "إنشاء نسخة الآن") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { confirmRestore = true },
            enabled = canRun,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp)
        ) { Text("استعادة نسخة") }

        Text(
            "استخدم مستودعًا خاصًا: المحادثات والملفات تُرفع كما هي. مفاتيح API لا تُرفع أبدًا. التوكن يحتاج صلاحية Contents: Read and write.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)
        )
    }

    if (confirmRestore) {
        NexisConfirmDialog(
            title = "استعادة من GitHub؟",
            message = "ستُستبدل بياناتك الحالية (المشاريع والمحادثات والملفات) بالنسخة الموجودة في المستودع. تُحفظ نسخة واحدة من بياناتك الحالية محليًا قبل الاستبدال.",
            confirmText = "استعادة",
            destructive = true,
            icon = Icons.Outlined.Restore,
            onConfirm = { confirmRestore = false; scope.launch { GitHubBackup.restore(context) } },
            onDismiss = { confirmRestore = false }
        )
    }
}
