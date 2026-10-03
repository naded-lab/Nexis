package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.data.FreeProvider
import com.nadidstudio.nexis.data.FreeProviderCatalog
import com.nadidstudio.nexis.ui.theme.NexisPalette

/**
 * "مزوّدات مجانية": pick a free-tier service, open its key page, add a model in one tap.
 * [onAdded] receives the new provider id (the caller opens the key dialog if it has no key yet).
 */
@Composable
fun FreeProvidersDialog(onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val ctx = LocalContext.current
    val providers = remember { FreeProviderCatalog.load(ctx) }
    var expandedId by remember { mutableStateOf<String?>(providers.firstOrNull()?.id) }
    var added by remember { mutableIntStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("مزوّدات مجانية") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "كل مزوّد له حدوده المستقلة، فيزيد وجودهم معًا فرصة استمرار الرد عند وصول أحدهم للحد.",
                        fontSize = 11.sp, color = NexisPalette.Muted, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (providers.isEmpty()) Text("تعذّر تحميل القائمة", fontSize = 13.sp)
                    refreshKey(added)
                    providers.forEach { p ->
                        ProviderBlock(p, expanded = expandedId == p.id,
                            onToggle = { expandedId = if (expandedId == p.id) null else p.id },
                            onAdd = { m ->
                                val id = FreeProviderCatalog.add(ctx, p, m) { err = it }
                                if (id != null) { added++; err = null; onAdded(id) }
                            })
                    }
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
        )
    }
}

@Suppress("UNUSED_PARAMETER")
private fun refreshKey(v: Int) {}

@Composable
private fun ProviderBlock(p: FreeProvider, expanded: Boolean, onToggle: () -> Unit, onAdd: (com.nadidstudio.nexis.data.FreeModel) -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(20.dp), tint = NexisPalette.Muted)
        }
        if (expanded) {
            Text(p.note, fontSize = 11.sp, color = NexisPalette.Muted)
            if (p.trainsOnData) {
                Text("⚠ قد تُستخدم طلباتك لتدريب نماذجهم — لا ترسل كودًا أو بيانات حساسة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 3.dp))
            }
            TextButton(onClick = {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(p.keyUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("احصل على مفتاح مجاني", fontSize = 12.sp)
            }
            p.models.forEach { m ->
                val has = FreeProviderCatalog.isAdded(ctx, p, m)
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontSize = 13.sp)
                        Text(listOf(m.context, m.limit).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 10.sp, color = NexisPalette.Muted)
                    }
                    if (has) Icon(Icons.Outlined.Check, "مضاف", Modifier.size(19.dp), tint = NexisPalette.Accent)
                    else TextButton(onClick = { onAdd(m) }) { Text("إضافة", fontSize = 12.sp) }
                }
            }
        }
        HorizontalDivider(color = NexisPalette.Muted.copy(alpha = .15f))
    }
}
