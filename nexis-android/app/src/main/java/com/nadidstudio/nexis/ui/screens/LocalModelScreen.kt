package com.nadidstudio.nexis.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.data.LocalModelStore
import com.nadidstudio.nexis.ui.theme.NexisPalette

@Composable
fun LocalModelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var info by remember { mutableStateOf(LocalModelStore.current(ctx)) }
    var error by remember { mutableStateOf<String?>(null) }
    val readable = info?.let { LocalModelStore.isReadable(ctx, it) } ?: false

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            error = LocalModelStore.save(ctx, uri)
            info = LocalModelStore.current(ctx)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
            Text("النموذج المحلي", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Memory, null, tint = NexisPalette.Accent, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Qwen 0.5B Instruct", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("GGUF ملف · Q4_K_M", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "النموذج لا يدخل داخل التطبيق. اختر الملف الذي حملته على هاتفك، وسيحتفظ Nexis Model بمكانه ويستخدمه لاحقًا.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), lineHeight = 21.sp
                )
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(info!!.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (info!!.size > 0) "%.0f MB · ".format(info!!.size / 1_048_576.0) else "") +
                            if (readable) "مرتبط ✓" else "الملف غير متاح (نُقل أو حُذف)",
                        fontSize = 12.sp,
                        color = if (readable) NexisPalette.Accent else MaterialTheme.colorScheme.error
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NexisPalette.Accent, contentColor = androidx.compose.ui.graphics.Color.White)
                ) {
                    Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (info == null) "إضافة ملف النموذج" else "تغيير ملف النموذج", fontSize = 14.sp)
                }
                if (info != null) {
                    TextButton(
                        onClick = { LocalModelStore.clear(ctx); info = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("إزالة الربط", color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocalModelStore.DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(Icons.Outlined.Download, null, tint = NexisPalette.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("تحميل Qwen Q4_K_M", color = NexisPalette.Accent, fontSize = 14.sp)
        }
        TextButton(
            onClick = {
                val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("url", LocalModelStore.DOWNLOAD_URL))
            },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("نسخ رابط التحميل", color = NexisPalette.Accent, fontSize = 14.sp) }
    }
}
