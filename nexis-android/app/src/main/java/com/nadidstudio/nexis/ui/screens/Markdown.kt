package com.nadidstudio.nexis.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nadidstudio.nexis.ui.theme.NexisPalette
import kotlinx.coroutines.delay

/** Small dependency-free Markdown renderer for chat replies (headings, lists, quotes, tables, code blocks, inline styles). */
private sealed class Block {
    data class Heading(val level: Int, val text: String) : Block()
    data class Para(val text: String) : Block()
    data class Bullet(val marker: String, val text: String) : Block()
    data class Quote(val text: String) : Block()
    data class Code(val lang: String, val code: String) : Block()
    data class Table(val rows: List<List<String>>) : Block()
    object Rule : Block()
}

private val tableSep = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun parseBlocks(src: String): List<Block> {
    val lines = src.replace("\r\n", "\n").split("\n")
    val out = mutableListOf<Block>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out.add(Block.Para(para.toString().trim())); para.clear() }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val t = line.trim()
        when {
            t.startsWith("```") -> {
                flush()
                val lang = t.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                out.add(Block.Code(lang, code.toString().trimEnd('\n')))
            }
            Regex("^#{1,6}\\s+.+").matches(t) -> {
                flush()
                val lvl = t.takeWhile { it == '#' }.length
                out.add(Block.Heading(lvl, t.drop(lvl).trim()))
            }
            Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t) -> { flush(); out.add(Block.Rule) }
            t.startsWith(">") -> { flush(); out.add(Block.Quote(t.removePrefix(">").trim())) }
            Regex("^[-*+]\\s+.+").matches(t) -> { flush(); out.add(Block.Bullet("•", t.drop(1).trim())) }
            Regex("^\\d+[.)]\\s+.+").matches(t) -> {
                flush()
                val num = t.takeWhile { it.isDigit() }
                out.add(Block.Bullet("$num.", t.substringAfter(' ').trim()))
            }
            t.startsWith("|") && i + 1 < lines.size && tableSep.matches(lines[i + 1]) -> {
                flush()
                val rows = mutableListOf<List<String>>()
                fun cells(s: String) = s.trim().trim('|').split("|").map { it.trim() }
                rows.add(cells(t)); i += 2
                while (i < lines.size && lines[i].trim().startsWith("|")) { rows.add(cells(lines[i])); i++ }
                i--
                out.add(Block.Table(rows))
            }
            t.isEmpty() -> flush()
            else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line.trimEnd()) }
        }
        i++
    }
    flush()
    return out
}

private val inlineRe = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`\\n]+)`)|(~~(.+?)~~)|(\\[([^\\]]+)]\\(([^)\\s]+)\\))|(\\*([^*\\n]+)\\*)|((?<!\\w)_([^_\\n]+)_(?!\\w))")

private fun inline(text: String, codeBg: Color, link: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRe.findAll(text)) {
        append(text.substring(last, m.range.first))
        val g = m.groupValues
        when {
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[2]) }
            g[4].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, fontSize = 13.sp)) { append(" ${g[4]} ") }
            g[6].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[6]) }
            g[8].isNotEmpty() -> withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(g[8]) }
            g[11].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[11]) }
            g[13].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[13]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { parseBlocks(text) }
    val codeBg = color.copy(alpha = 0.09f)
    val link = NexisPalette.Accent2
    val body = TextStyle(color = color, fontSize = 15.sp, lineHeight = 24.sp, textDirection = TextDirection.Content)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Block.Heading -> Text(
                    inline(b.text, codeBg, link),
                    style = body.copy(fontSize = when (b.level) { 1 -> 21.sp; 2 -> 18.sp; else -> 16.sp }, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
                    modifier = Modifier.padding(top = 4.dp)
                )
                is Block.Para -> Text(inline(b.text, codeBg, link), style = body)
                is Block.Bullet -> Row {
                    Text(b.marker, style = body.copy(color = NexisPalette.Accent2, fontWeight = FontWeight.Bold), modifier = Modifier.width(24.dp))
                    Text(inline(b.text, codeBg, link), style = body, modifier = Modifier.weight(1f))
                }
                is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(NexisPalette.Accent2, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(10.dp))
                    Text(inline(b.text, codeBg, link), style = body.copy(color = color.copy(alpha = 0.75f)))
                }
                is Block.Rule -> Divider(color = color.copy(alpha = 0.15f))
                is Block.Code -> CodeBlock(b.lang, b.code)
                is Block.Table -> MarkdownTable(b.rows, body, codeBg, link, color)
            }
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>, body: TextStyle, codeBg: Color, link: Color, color: Color) {
    val cols = rows.maxOf { it.size }
    Column(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .border(1.dp, color.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
    ) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.background(if (r == 0) color.copy(alpha = 0.07f) else Color.Transparent)) {
                for (c in 0 until cols) {
                    Text(
                        inline(row.getOrElse(c) { "" }, codeBg, link),
                        style = body.copy(fontSize = 13.sp, lineHeight = 20.sp, fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal),
                        modifier = Modifier.widthIn(min = 90.dp, max = 220.dp).padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            if (r < rows.lastIndex) Divider(color = color.copy(alpha = 0.1f))
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }
    // Code is always left-to-right, whatever the app's layout direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().background(Color(0xFF0E1513), RoundedCornerShape(14.dp))) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF17211E), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang.ifBlank { "code" }, color = Color(0xFF8FB5A9), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, null, Modifier.size(14.dp), tint = Color(0xFF8FB5A9))
                    Spacer(Modifier.width(5.dp))
                    Text(if (copied) "Copied" else "Copy", color = Color(0xFF8FB5A9), fontSize = 11.sp)
                }
            }
            Text(
                code,
                color = Color(0xFFE3F1EC),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
}
