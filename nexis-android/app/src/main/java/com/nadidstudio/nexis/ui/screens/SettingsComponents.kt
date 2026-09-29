package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.ui.theme.NexisPalette

/** Shared Nexis design-system pieces for list-style screens (flat rows, no cards). */

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 4.dp)
        )
        content()
    }
}

@Composable
fun SettingsRow(
    title: String,
    icon: ImageVector,
    sub: String = "",
    value: String = "",
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (danger) MaterialTheme.colorScheme.error else muted
    var m = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = muted, maxLines = 1)
        }
        if (value.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(value, fontSize = 13.sp, color = if (valueColor == Color.Unspecified) muted else valueColor, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp)); trailing()
        } else if (onClick != null && !danger) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp), tint = muted)
        }
    }
}

@Composable
fun SettingsSwitchRow(title: String, icon: ImageVector, sub: String = "", checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title, icon = icon, sub = sub,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked, onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedTrackColor = NexisPalette.Accent, checkedThumbColor = Color.White)
            )
        }
    )
}
