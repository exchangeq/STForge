package com.wunding.stforge.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wunding.stforge.data.BookEntry
import com.wunding.stforge.data.CardGen
import com.wunding.stforge.data.CardStore
import com.wunding.stforge.data.StCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val steps = listOf("基本", "设定", "世界书", "开场", "导出")

/** 导出前的必备项校验，避免产出无法被 SillyTavern 识别的空卡。 */
private fun cardIssue(c: StCard): String? = when {
    c.name.isBlank() -> "请先在「基本」填写角色名"
    c.firstMes.isBlank() -> "请先在「开场」填写第一条消息（first_mes）"
    c.description.isBlank() -> "请先在「设定」填写角色描述"
    else -> null
}

@Composable
fun StForgeApp(context: Context) {
    var cards by remember { mutableStateOf<List<StCard>>(emptyList()) }
    var screen by rememberSaveable { mutableStateOf("library") }
    var card by remember { mutableStateOf(StCard()) }
    var step by rememberSaveable { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var previewAlt by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()

    fun refresh() { cards = CardStore.list(context) }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(msg) {
        if (msg.isNotBlank()) {
            kotlinx.coroutines.delay(3500)
            msg = ""
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { CardStore.importCard(context, uri) } }
                .onSuccess { c -> card = c; step = 0; screen = "edit"; refresh(); msg = "导入成功：${c.name}" }
                .onFailure { msg = "导入失败：${it.message ?: ""}" }
        }
    }

    when (screen) {
        "library" -> LibraryScreen(
            cards = cards,
            msg = msg,
            onNew = { card = StCard(); step = 0; screen = "edit" },
            onRandom = { card = CardGen.generate(); step = 0; screen = "edit" },
            onOpen = { c -> card = c; step = 0; screen = "edit" },
            onImport = { runCatching { importer.launch(arrayOf("image/png", "application/json", "*/*")) }.onFailure { msg = "没有文件选择器" } },
            onDelete = { c -> scope.launch { withContext(Dispatchers.IO) { CardStore.delete(context, c.id) }; refresh() } },
            onDuplicate = { c -> scope.launch { withContext(Dispatchers.IO) { CardStore.save(context, c.copy(id = "", name = c.name + "（副本）", updatedAt = System.currentTimeMillis())) }; refresh(); msg = "已复制一份卡片" } },
        )
        "edit" -> EditorScreen(
            card = card,
            step = step,
            msg = msg,
            context = context,
            onBack = {
                val draft = card
                if (draft.name.isNotBlank()) {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { CardStore.save(context, draft) }; refresh() }
                            .onFailure { msg = "自动保存失败：${it.message ?: ""}" }
                    }
                }
                screen = "library"
            },
            onChange = { card = it },
            onStep = { step = it },
            onSave = {
                val issue = cardIssue(card)
                if (issue != null) msg = issue
                else scope.launch {
                    card = withContext(Dispatchers.IO) { CardStore.save(context, card) }
                    refresh(); msg = "已保存到卡片库"
                }
            },
            onPreview = { previewAlt = -1; screen = "preview" },
            onExportAist = {
                val issue = cardIssue(card)
                if (issue != null) msg = issue
                else scope.launch {
                    runCatching {
                        val saved = withContext(Dispatchers.IO) { CardStore.save(context, card) }
                        card = saved
                        withContext(Dispatchers.IO) { CardStore.exportAist(context, saved) }
                    }
                        .onSuccess { runCatching { share(context, it) }.onFailure { e -> msg = "分享失败：${e.message ?: ""}" } }
                        .onFailure { msg = "导出失败：${it.message ?: ""}" }
                }
            },
            onCopyIndex = {
                scope.launch {
                    val saved = withContext(Dispatchers.IO) { CardStore.save(context, card) }
                    card = saved
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("index-entry", com.wunding.stforge.data.AistCard.indexEntry(saved)))
                    msg = "仓库 index.json 条目已复制（id=${saved.id}）"
                }
            },
        )
        "preview" -> PreviewScreen(
            card = card,
            altIndex = previewAlt,
            context = context,
            onAlt = { previewAlt = it },
            onBack = { screen = "edit" },
            onEdit = { step = 3; screen = "edit" },
        )
    }
}

@Composable
private fun LibraryScreen(
    cards: List<StCard>, msg: String,
    onNew: () -> Unit, onRandom: () -> Unit, onOpen: (StCard) -> Unit,
    onImport: () -> Unit, onDelete: (StCard) -> Unit, onDuplicate: (StCard) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<StCard?>(null) }
    val visible = cards.filter { query.isBlank() || it.name.contains(query, true) || it.tags.any { t -> t.contains(query, true) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileOpen, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("导入卡片", fontSize = 12.sp)
                }
                Button(onClick = onRandom, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("灵感骰子", fontSize = 12.sp)
                }
                Button(onClick = onNew, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("新建", fontSize = 12.sp)
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("STForge", fontSize = 30.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("角色卡工作台 · 写设定、排世界书、一键出 SillyTavern PNG", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${cards.size} 张", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("搜索名字 / 标签", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                singleLine = true, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            )
            if (msg.isNotBlank()) Text(msg, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 16.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有卡片。点下面「新建」，或「灵感骰子」先生成一张。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { c ->
                        CardTile(c, onOpen = { onOpen(c) }, onDelete = { deleteTarget = c }, onDuplicate = { onDuplicate(c) })
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        CooldownDeleteDialog(
            title = "删除这张卡片？",
            message = "「${target.name.ifBlank { "未命名" }}」会从本地卡片库删除，不可恢复；建议先导出 .aist 备份。",
            onConfirm = { onDelete(target); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun CardTile(c: StCard, onOpen: () -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(104.dp).background(
                    Brush.linearGradient(
                        listOf(
                            Color(android.graphics.Color.HSVToColor(floatArrayOf(((c.name.hashCode() % 360 + 360) % 360).toFloat(), 0.5f, 0.35f))),
                            MaterialTheme.colorScheme.background,
                        )
                    )
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.name.take(1).ifBlank { "?" }, fontSize = 40.sp, fontFamily = FontFamily.Serif, color = Color(0xFFF5EFE2))
                Row(Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.ContentCopy, "复制", Modifier.size(14.dp), tint = Color(0x99FFFFFF))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, "删除", Modifier.size(14.dp), tint = Color(0x99FFFFFF))
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(c.name.ifBlank { "未命名" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(c.tags.take(2).joinToString(" · ").ifBlank { "未加标签" }, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text("${c.entries.size} 条世界书", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    card: StCard, step: Int, msg: String, context: Context,
    onBack: () -> Unit, onChange: (StCard) -> Unit, onStep: (Int) -> Unit,
    onSave: () -> Unit, onPreview: () -> Unit, onExportAist: () -> Unit,
    onCopyIndex: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text(card.name.ifBlank { "新角色卡" }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("第 ${step + 1} 步 / ${steps.size} · ${steps[step]}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onPreview) { Icon(Icons.Default.Preview, "预览") }
                    IconButton(onClick = onSave) { Icon(Icons.Default.Save, "保存") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.indices.forEach { i ->
                        val active = i == step
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f).clickable { onStep(i) },
                        ) {
                            Text(
                                "${i + 1} ${steps[i]}",
                                fontSize = 9.5.sp,
                                maxLines = 1,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (msg.isNotBlank()) Text(msg, fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
            when (step) {
                0 -> StepBasic(card, onChange)
                1 -> StepSetting(card, onChange)
                2 -> StepBook(card, onChange)
                3 -> StepOpening(card, onChange)
                else -> StepExport(card, onExportAist, onCopyIndex)
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, minLines: Int = 2, hint: String = "") {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = value, onValueChange = onChange,
            label = { Text(label, fontSize = 10.5.sp) },
            placeholder = { if (hint.isNotBlank()) Text(hint, fontSize = 10.sp) },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
        )
        Text("${value.length} 字", fontSize = 8.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun StepBasic(card: StCard, onChange: (StCard) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
        item { Field("角色名", card.name, { onChange(card.copy(name = it)) }, 1, "例：沈砚") }
        item { Field("标签（逗号分隔）", card.tags.joinToString("，"), { onChange(card.copy(tags = it.split(",", "，").map { s -> s.trim() }.filter { s -> s.isNotEmpty() })) }, 1, "病娇，日常，原创") }
        item { Field("角色描述（外貌 / 身份 / 世界观）", card.description, { onChange(card.copy(description = it)) }, 6, "写清这个人是谁、在哪、靠什么生活") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                OutlinedButton(onClick = { onChange(CardGen.generate()) }) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("整套随机", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun StepSetting(card: StCard, onChange: (StCard) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
        item { Field("性格", card.personality, { onChange(card.copy(personality = it)) }, 3, "表面 / 里层 / 核心矛盾") }
        item { Field("场景", card.scenario, { onChange(card.copy(scenario = it)) }, 3, "故事开始时，你们在哪、正在做什么") }
        item { Field("系统提示词", card.systemPrompt, { onChange(card.copy(systemPrompt = it)) }, 3) }
        item { Field("历史后指令", card.postHistory, { onChange(card.copy(postHistory = it)) }, 2, "越靠后权重越高，可写硬性规则") }
        item { Field("作者备注", card.creatorNotes, { onChange(card.copy(creatorNotes = it)) }, 2, "给使用者看的说明，不进上下文") }
    }
}

@Composable
private fun StepBook(card: StCard, onChange: (StCard) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("世界书 · ${card.entries.size} 条", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange(card.copy(entries = card.entries + BookEntry(keys = listOf("关键词"), content = "", insertionOrder = card.entries.size + 1))) }) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp)); Text("加一条", fontSize = 10.5.sp)
            }
        }
        Text("命中关键词时注入；勾选「常驻」则每轮都带。建议 2~5 条，短句、具体。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(card.entries.indices.toList(), key = { i -> card.entries[i].hashCode() + i }) { i ->
                val e = card.entries[i]
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("条目 ${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(if (e.enabled) "启用" else "停用", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = e.enabled, onCheckedChange = { v -> onChange(card.copy(entries = card.entries.toMutableList().also { it[i] = e.copy(enabled = v) })) }, modifier = Modifier.height(26.dp))
                            IconButton(onClick = { onChange(card.copy(entries = card.entries.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Delete, "删", Modifier.size(16.dp)) }
                        }
                        OutlinedTextField(value = e.keys.joinToString("，"), onValueChange = { v ->
                            val keys = v.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                            onChange(card.copy(entries = card.entries.toMutableList().also { it[i] = e.copy(keys = keys) }))
                        }, label = { Text("触发关键词", fontSize = 9.5.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = e.content, onValueChange = { v ->
                            onChange(card.copy(entries = card.entries.toMutableList().also { it[i] = e.copy(content = v) }))
                        }, label = { Text("注入内容", fontSize = 9.5.sp) }, minLines = 2, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        OutlinedTextField(value = e.comment, onValueChange = { v ->
                            onChange(card.copy(entries = card.entries.toMutableList().also { it[i] = e.copy(comment = v) }))
                        }, label = { Text("备注名（可空）", fontSize = 9.5.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("常驻", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = e.constant, onCheckedChange = { v -> onChange(card.copy(entries = card.entries.toMutableList().also { it[i] = e.copy(constant = v) })) }, modifier = Modifier.height(26.dp))
                            Spacer(Modifier.weight(1f))
                            Text("顺序 ${e.insertionOrder}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepOpening(card: StCard, onChange: (StCard) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 6.dp)) {
        item { Field("开场白（first_mes）", card.firstMes, { onChange(card.copy(firstMes = it)) }, 6, "用括号写动作，第一句别急着解释设定") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("备用开场 · ${card.alternates.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(card.copy(alternates = card.alternates + "")) }) { Text("加一条", fontSize = 10.5.sp) }
            }
        }
        items(card.alternates.indices.toList(), key = { i -> i }) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = card.alternates[i], onValueChange = { v ->
                        onChange(card.copy(alternates = card.alternates.toMutableList().also { it[i] = v }))
                    }, label = { Text("备用开场 ${i + 1}", fontSize = 10.sp) }, minLines = 2, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                )
                IconButton(onClick = { onChange(card.copy(alternates = card.alternates.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Delete, "删", Modifier.size(16.dp)) }
            }
        }
        item { Field("对话示例（mes_example）", card.mesExample, { onChange(card.copy(mesExample = it)) }, 5, "<START>\n{{user}}: …\n{{char}}: …") }
    }
}

@Composable
private fun StepExport(card: StCard, onAist: () -> Unit, onCopyIndex: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp)) {
                    Text("导出检查", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val checks = listOf(
                        "名字" to card.name.isNotBlank(),
                        "描述 ≥ 100 字" to (card.description.length >= 100),
                        "开场白" to card.firstMes.isNotBlank(),
                        "世界书 ≥ 1 条" to card.entries.isNotEmpty(),
                        "系统提示词" to card.systemPrompt.isNotBlank(),
                    )
                    checks.forEach { (label, ok) ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (ok) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(14.dp), tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text(label, fontSize = 10.5.sp, color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            Text("AI 人设压缩包（.aist）：与 PocketForge-Personas 仓库完全一致的目录结构，可直接上传仓库或导入支持 aist 的 App。", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = onAist, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FileDownload, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("导出 AI 人设包 .aist", fontSize = 13.sp)
            }
        }
        item {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(11.dp)) {
                    Text("包内结构（严格对齐仓库）", fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
                    Text("{id}/AI.md\n{id}/主设定.md\n{id}/扩展包-日常.md\n{id}/扩展包-互动与红线.md\n{id}/扩展包-病娇.md 或 扩展包-囚禁向.md（仅成熟题材）", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
                }
            }
        }
        item {
            TextButton(onClick = onCopyIndex, modifier = Modifier.fillMaxWidth()) { Text("复制仓库 index.json 条目", fontSize = 11.sp) }
        }
        item { Text("导出后走系统分享；上传仓库时用 index.json 条目 + packs/{id}.aist 即可。", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PreviewScreen(card: StCard, altIndex: Int, context: Context, onAlt: (Int) -> Unit, onBack: () -> Unit, onEdit: () -> Unit) {
    val opening = when {
        altIndex >= 0 && altIndex < card.alternates.size -> card.alternates[altIndex]
        card.firstMes.isNotBlank() -> card.firstMes
        else -> "（还没有开场白，去第 4 步写一句）"
    }
    Scaffold(
        containerColor = Color(0xFF070B12),
        topBar = {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text(card.name.ifBlank { "未命名" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("预览 · 与真实开聊一致的气泡排版", fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onEdit) { Text("回去改", fontSize = 10.5.sp) }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = altIndex < 0, onClick = { onAlt(-1) }, label = { Text("主开场", fontSize = 10.sp) })
                card.alternates.indices.forEach { i ->
                    FilterChip(selected = altIndex == i, onClick = { onAlt(i) }, label = { Text("备用 ${i + 1}", fontSize = 10.sp) })
                }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("（${card.name} 已就位。设定已锁定。）", fontSize = 10.sp, color = Color(0xFF7C8AA0))
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(26.dp).background(Color(0xFF28405A), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Text("AI", fontSize = 8.sp, color = Color(0xFFBFE4DA))
                        }
                        Surface(color = Color(0xFF16202E), shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)) {
                            Text(opening, fontSize = 12.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(10.dp), color = Color(0xFFEDE8DC))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(color = Color(0xFF1E3B37), shape = RoundedCornerShape(10.dp)) {
                            Text("（你走进来，抖了抖伞上的水）", fontSize = 12.sp, modifier = Modifier.padding(10.dp), color = Color(0xFFDDE9E2))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = "", onValueChange = {}, enabled = false, placeholder = { Text("说点什么…（预览不发消息）", fontSize = 11.sp) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.Send, null, Modifier.size(20.dp), tint = Color(0xFF6E7E92))
            }
        }
    }
}

private fun share(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val mime = when {
        file.name.endsWith(".png") -> "image/png"
        file.name.endsWith(".aist") -> "application/zip"
        else -> "application/json"
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享人设包").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
