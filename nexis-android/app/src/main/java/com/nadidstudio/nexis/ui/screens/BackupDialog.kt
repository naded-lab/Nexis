package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.backup.BackupState
import com.nadidstudio.nexis.backup.GitHubBackup
import com.nadidstudio.nexis.ui.session.NexisSessionStore
import com.nadidstudio.nexis.ui.theme.NexisPalette
import kotlinx.coroutines.launch

@Composable
fun BackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var repo by remember { mutableStateOf(GitHubBackup.repo(context)) }
    var auto by remember { mutableStateOf(GitHubBackup.autoEnabled(context)) }
    val hasToken = NexisSessionStore.keyStoreForSettings.hasAnyKey("github")
    val repoOk = GitHubBackup.isValidRepo(repo)
    var confirmRestore by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("النسخ الاحتياطي إلى GitHub") },
        text = {
            Column {
                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it; GitHubBackup.setRepo(context, it) },
                    label = { Text("المستودع (owner/repo)") },
                    singleLine = true,
                    isError = repo.isNotEmpty() && !repoOk
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نسخ تلقائي بعد التغييرات", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it; GitHubBackup.setAuto(context, it) })
                }
                Spacer(Modifier.height(8.dp))
                Text(BackupState.status, fontSize = 12.sp, color = NexisPalette.Muted)
                if (!hasToken) {
                    Text("أضف توكن GitHub من صف GitHub في الإعدادات.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                }
                Text(
                    "استخدم مستودعًا خاصًا: المحادثات والملفات تُرفع كما هي. مفاتيح API لا تُرفع أبدًا. التوكن يحتاج صلاحية Contents: Read and write.",
                    fontSize = 11.sp, color = NexisPalette.Muted, modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = hasToken && repoOk && !BackupState.running,
                onClick = { scope.launch { GitHubBackup.backupNow(context) } }
            ) { Text(if (BackupState.running) "جارٍ النسخ…" else "نسخ الآن") }
        },
        dismissButton = {
            Row {
                TextButton(enabled = hasToken && repoOk && !BackupState.running, onClick = { confirmRestore = true }) { Text("استعادة") }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("استعادة من GitHub؟") },
            text = { Text("ستُستبدل بياناتك الحالية (المشاريع والمحادثات والملفات) بالنسخة الموجودة في المستودع. تُحفظ نسخة واحدة من بياناتك الحالية محليًا قبل الاستبدال.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; scope.launch { GitHubBackup.restore(context) } }) { Text("استعادة") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("إلغاء") } }
        )
    }
}
