package com.shu.notebackupexporter

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.shu.notebackupexporter.core.BackupLocator
import com.shu.notebackupexporter.core.ExportFormat
import com.shu.notebackupexporter.core.Exporter
import com.shu.notebackupexporter.core.Note
import com.shu.notebackupexporter.core.NoteRenderer
import com.shu.notebackupexporter.core.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private const val OUTPUT_DIR_NAME = "小米笔记"
private const val GITHUB_URL = "https://github.com/shu-chong/Hyperos_NoteExporter"

private const val DEVELOPER = "爱看小说的书虫"


private const val HUGE_BACKUP_BYTES = 300L * 1024 * 1024


private fun isZipFile(f: File): Boolean = runCatching {
    val head = ByteArray(4)
    val n = f.inputStream().use { it.read(head) }
    n == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
        head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
}.getOrDefault(false)


private const val BACKUP_DIR_PATH = "MIUI/backup/AllBackup"


private fun backupDirInitialUri(): Uri? = runCatching {
    DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:$BACKUP_DIR_PATH"
    )
}.getOrNull()


private val AccentBlue = Color(0xFF2A6BFF)
private val AccentBlueLight = Color(0xFF4C8DFF)
private val PillBg = Color(0xFFE4EEFF)
private val GreyText = Color(0xFFB5B5B5)
private val GreyBg = Color(0xFFF0F0F0)

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                AppScreen(resumeTick.value)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.value++
    }
}

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager() else true

private enum class Phase { IDLE, DECRYPTING, DECRYPTED, EXPORTING, EXPORTED, FAILED }


@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp)
    )
}


@Composable
private fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            content()
        }
    }
}


@Composable
private fun PillButton(
    text: String,
    enabled: Boolean = true,
    minWidth: Dp = 76.dp,
    minHeight: Dp = 36.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        cornerRadius = 18.dp,
        minWidth = minWidth,
        minHeight = 36.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            color = PillBg,
            disabledColor = GreyBg,
            contentColor = AccentBlue,
            disabledContentColor = GreyText
        ),
        modifier = modifier
    ) { Text(text) }
}


@Composable
private fun AppIcon(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.23f)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DialogRow(
    title: String,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    titleStyle: TextStyle = MiuixTheme.textStyles.body1,
    indent: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(start = 8.dp + indent, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = AccentBlue, maxLines = 1)
        }
    }
}


private fun openOutputFolder(ctx: Context): Boolean {
    val docUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Download/$OUTPUT_DIR_NAME"
    )
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    val handlers = runCatching {
        ctx.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrDefault(emptyList())

    if (handlers.size > 1 &&
        runCatching { ctx.startActivity(Intent.createChooser(view, "选择文件管理器")) }.isSuccess
    ) {
        return true
    }
    if (runCatching { ctx.startActivity(view) }.isSuccess) return true
    if (runCatching { ctx.startActivity(Intent.createChooser(view, "选择文件管理器")) }.isSuccess) {
        return true
    }

    val fallbacks = listOf(
        ComponentName(
            "com.mi.android.globalFileexplorer",
            "com.mi.android.globalFileexplorer.MainActivity"
        ),
        ComponentName(
            "com.android.fileexplorer",
            "com.android.fileexplorer.FileExplorerTabActivity"
        ),
    )
    for (cn in fallbacks) {
        if (runCatching { ctx.startActivity(Intent().apply { component = cn }) }.isSuccess) {
            return true
        }
    }
    return false
}

@Composable
private fun AppScreen(resumeTick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }

    var granted by remember { mutableStateOf(hasAllFilesAccess()) }
    var showPermission by remember { mutableStateOf(!hasAllFilesAccess()) }
    var backups by remember { mutableStateOf<List<BackupLocator.BackupFile>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var pickedFile by remember { mutableStateOf<File?>(null) }
    
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedIsZip by remember { mutableStateOf(true) }

    var format by remember { mutableStateOf(Prefs.format(ctx)) }
    var fallbackFormat by remember { mutableStateOf(Prefs.fallbackFormat(ctx)) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPickDialog by remember { mutableStateOf(false) }

    var prepared by remember { mutableStateOf<Exporter.Prepared?>(null) }
    var checked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var decryptProgress by remember { mutableStateOf<Exporter.Progress?>(null) }
    var exportProgress by remember { mutableStateOf<Exporter.Progress?>(null) }
    var exportResult by remember { mutableStateOf<Exporter.Result?>(null) }
    var failMessage by remember { mutableStateOf<String?>(null) }

    val versionName = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "—"
    }

    LaunchedEffect(resumeTick, granted) {
        granted = hasAllFilesAccess()
        if (granted) {
            showPermission = false
            scanning = true
            backups = withContext(Dispatchers.IO) { BackupLocator.list() }
            scanning = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { granted = hasAllFilesAccess() }

    
    fun usePickedFile(file: File, displayName: String, isZip: Boolean) {
        pickedFile = file
        pickedName = displayName
        pickedIsZip = isZip
        prepared?.let { runCatching { Exporter(ctx).release(it) } }
        prepared = null
        checked = emptySet()
        decryptProgress = null
        exportProgress = null
        exportResult = null
        failMessage = null
        phase = Phase.IDLE
    }

    
    val pickLauncher = rememberLauncherForActivityResult(
        object : ActivityResultContract<Uri?, Uri?>() {
            override fun createIntent(context: Context, input: Uri?): Intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    if (input != null) putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }

            override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
                if (resultCode == Activity.RESULT_OK) intent?.data else null
        }
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val picked = withContext(Dispatchers.IO) {
                    runCatching {
                        
                        var displayName: String? = null
                        var lastMod = 0L
                        runCatching {
                            ctx.contentResolver.query(
                                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                            )?.use { c -> if (c.moveToFirst()) displayName = c.getString(0) }
                        }
                        runCatching {
                            ctx.contentResolver.query(
                                uri,
                                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                                null, null, null
                            )?.use { c ->
                                if (c.moveToFirst() && !c.isNull(0)) lastMod = c.getLong(0)
                            }
                        }
                        val out = File(ctx.cacheDir, "picked_backup")
                        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
                            out.outputStream().use { ins.copyTo(it) }
                        }
                        if (lastMod > 0) out.setLastModified(lastMod)
                        Triple(out, displayName, isZipFile(out))
                    }.getOrNull()
                }
                if (picked != null) {
                    showPickDialog = false
                    usePickedFile(
                        picked.first,
                        picked.second ?: picked.first.name,
                        picked.third
                    )
                } else {
                    toast(ctx, "无法读取所选文件")
                }
            }
        }
    }

    fun currentTarget(): BackupLocator.BackupFile? =
        pickedFile?.let { BackupLocator.BackupFile(it, pickedIsZip, pickedName) }
            ?: backups.firstOrNull()

    fun requestPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${ctx.packageName}")
        )
        runCatching { permissionLauncher.launch(intent) }
            .onFailure { permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    fun openBackupApp() {
        val intent = Intent().apply {
            component = ComponentName("com.miui.backup", "com.miui.backup.local.LocalHomeActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { toast(ctx, "没有找到备份应用，请到「设置 → 更多设置 → 备份与恢复」") }
    }

    fun startDecrypt() {
        val target = currentTarget()
        if (target == null) {
            toast(ctx, "请先备份笔记，或手动选择备份文件")
            return
        }
        val exporter = Exporter(ctx.applicationContext)
        prepared?.let { runCatching { exporter.release(it) } }
        prepared = null
        checked = emptySet()
        exportResult = null
        failMessage = null
        decryptProgress = Exporter.Progress("准备中…", 0, 0)
        phase = Phase.DECRYPTING

        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    exporter.prepare(target) { p -> main.post { decryptProgress = p } }
                }
            }
            outcome.fold(
                onSuccess = { prep ->
                    prepared = prep
                    checked = emptySet()
                    decryptProgress = Exporter.Progress("解析完成", prep.size, prep.size)
                    phase = Phase.DECRYPTED
                },
                onFailure = { e ->
                    decryptProgress = decryptProgress?.copy(message = "解析失败")
                    failMessage = e.message ?: e.toString()
                    phase = Phase.FAILED
                }
            )
        }
    }

    fun startExport(indices: List<Int>) {
        val prep = prepared ?: return
        if (indices.isEmpty()) {
            toast(ctx, "没有选择要导出的笔记")
            return
        }
        val outRoot = File(BackupLocator.downloadDir, OUTPUT_DIR_NAME)
        exportResult = null
        failMessage = null
        exportProgress = Exporter.Progress("准备中…", 0, 0)
        phase = Phase.EXPORTING

        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    Exporter(ctx.applicationContext).export(
                        prep, indices, outRoot, format, Prefs.fallbackFormat(ctx)
                    ) { p -> main.post { exportProgress = p } }
                }
            }
            outcome.fold(
                onSuccess = { r ->
                    exportResult = r
                    exportProgress = Exporter.Progress("已完成", r.exportedCount, r.exportedCount)
                    phase = Phase.EXPORTED
                },
                onFailure = { e ->
                    exportProgress = exportProgress?.copy(message = "已中断")
                    failMessage = e.message ?: e.toString()
                    phase = Phase.FAILED
                }
            )
        }
    }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val sidePad = if (screenWidthDp > 800) ((screenWidthDp - 760) / 2).dp else 16.dp
    val busy = phase == Phase.DECRYPTING || phase == Phase.EXPORTING
    val selection = checked
    val gated = !granted
    
    val formatTag = when (format) {
        ExportFormat.WORD -> "Word"
        ExportFormat.PDF -> "PDF"
        ExportFormat.MARKDOWN -> "Markdown"
    }

    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior(
        canScroll = { listState.canScrollBackward || listState.canScrollForward }
    )
    
    
    val progressAnchor = 2
    LaunchedEffect(phase) {
        if (phase == Phase.EXPORTING || phase == Phase.EXPORTED || phase == Phase.FAILED) {
            delay(150)
            if (listState.layoutInfo.totalItemsCount > progressAnchor) {
                listState.animateScrollToItem(progressAnchor)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "笔记导出",
                largeTitle = "笔记导出",
                scrollBehavior = scrollBehavior,
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(CircleShape)
                            .clickable { showSettings = true }
                            .padding(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_more_vertical),
                            contentDescription = "更多",
                            colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(horizontal = sidePad, vertical = 4.dp)
            ) {
                
                item {
                    SectionTitle("备份与选择")
                    AppCard {
                        val target = currentTarget()
                        if (target != null) {
                            
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "已选择",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = target.name,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${target.displayTime}　·　${target.displaySize}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (backups.size > 1 && pickedFile == null) {
                                Spacer(Modifier.height(6.dp))
                                Text("该目录下共 ${backups.size} 个备份，默认使用最新的一个。")
                            }
                            if (target.size > HUGE_BACKUP_BYTES) {
                                Spacer(Modifier.height(6.dp))
                                Text("这个备份体积较大，可能是整机备份；解析只会读取其中的笔记数据。")
                            }
                        } else if (scanning) {
                            Text("正在扫描备份目录…")
                        } else {
                            Text("还没有找到备份文件。")
                            Spacer(Modifier.height(4.dp))
                            Text("备份目录：MIUI/backup/AllBackup")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("在系统设置的「本地备份」里只勾选「笔记」后开始备份，完成后回到本应用会自动识别；也可以手动选择备份文件。")
                        Spacer(Modifier.height(12.dp))
                        
                        
                        PillButton(
                            text = "打开备份界面",
                            enabled = !busy,
                            minWidth = 0.dp,
                            minHeight = 44.dp,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { openBackupApp() }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(
                                text = "重新扫描",
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        scanning = true
                                        pickedFile = null
                                        pickedName = null
                                        backups = withContext(Dispatchers.IO) { BackupLocator.list() }
                                        scanning = false
                                    }
                                }
                            )
                            PillButton(
                                text = "手动选择",
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                                onClick = { showPickDialog = true }
                            )
                        }
                    }
                }

                
                item {
                    SectionTitle("解析笔记")
                    AppCard {
                        val prep = prepared
                        if (prep == null) {
                            Text("解析后可以查看全部笔记标题，并逐条或批量导出。")
                        } else {
                            Text("已解析 ${prep.size} 条笔记")
                            if (prep.mergedDuplicates > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text("已合并 ${prep.mergedDuplicates} 条完全重复的记录")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append("图片附件 ${prep.imageCount} 张")
                                    if (prep.audioCount > 0) append("　·　语音 ${prep.audioCount} 条")
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { if (gated) showPermission = true else startDecrypt() },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when {
                                    phase == Phase.DECRYPTING -> "正在解析…"
                                    prep == null -> "开始解析"
                                    else -> "重新解析"
                                }
                            )
                        }
                    }
                }

                
                val activeProgress = when (phase) {
                    Phase.DECRYPTING -> decryptProgress
                    Phase.EXPORTING -> exportProgress
                    else -> null
                }
                val failMsg = failMessage
                val result = exportResult
                if (activeProgress != null || result != null || failMsg != null) {
                    item(key = "progress") {
                        SectionTitle(
                            when {
                                failMsg != null -> "出错了"
                                activeProgress != null -> "进行中"
                                else -> "导出完成"
                            }
                        )
                        AppCard {
                            when {
                                failMsg != null -> Text(failMsg)
                                activeProgress != null -> {
                                    Text(activeProgress.message)
                                    if (activeProgress.total > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("${activeProgress.current} / ${activeProgress.total}")
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = if (activeProgress.total > 0) {
                                            (activeProgress.current.toFloat() / activeProgress.total)
                                                .coerceIn(0f, 1f)
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                result != null -> {
                                    Text(
                                        buildString {
                                            append("已导出 ${result.exportedCount} 条　·　图片 ${result.imageCount} 张")
                                            if (result.audioCount > 0) append("　·　语音 ${result.audioCount} 条")
                                        }
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        buildString {
                                            append("格式：${result.format.label}")
                                            if (result.fallbackCount > 0) {
                                                append("　·　${result.fallbackCount} 条含图片，按 ${result.fallbackFormat?.label ?: "Word"} 导出")
                                            }
                                        }
                                    )
                                    if (result.combinedFile != null) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("汇总文档：${result.combinedFile.name}")
                                    }
                                    if (result.perNoteCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("单条文档：单条笔记/ 共 ${result.perNoteCount} 个文件")
                                    }
                                    if (result.audioCount > 0) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("语音：汇总在 语音附件/，单条与文档同目录")
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text("位置：Download/$OUTPUT_DIR_NAME/${result.outputRoot.name}")
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            if (!openOutputFolder(ctx)) {
                                                toast(ctx, "没有找到可用的文件管理器")
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("去查看") }
                                }
                            }
                        }
                    }
                }

                
                prepared?.let { prep ->
                    item {
                        SectionTitle("笔记列表（${prep.size} 条）")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "已选 ${selection.size} 条",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f)
                            )
                            PillButton(
                                text = if (selection.size == prep.size) "取消全选" else "全选",
                                enabled = !busy,
                                minWidth = 108.dp,
                                onClick = {
                                    checked = if (selection.size == prep.size) {
                                        emptySet()
                                    } else {
                                        prep.notes.indices.toSet()
                                    }
                                }
                            )
                        }
                    }
                    itemsIndexed(prep.notes) { i, note ->
                        NoteRow(
                            index = i,
                            note = note,
                            selected = selection.contains(i),
                            enabled = !busy,
                            onToggle = {
                                checked = if (selection.contains(i)) selection - i else selection + i
                            },
                            onExport = { startExport(listOf(i)) }
                        )
                        if (i != prep.notes.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .padding(start = 8.dp)
                                    .background(MiuixTheme.colorScheme.dividerLine)
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            
            if (prepared != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = sidePad)
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    Button(
                        onClick = {
                            if (gated) showPermission = true else startExport(selection.sorted())
                        },
                        enabled = !busy && (gated || selection.isNotEmpty()),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                phase == Phase.EXPORTING -> "正在导出…"
                                selection.isEmpty() -> "导出选择的笔记（$formatTag）"
                                else -> "导出选择的笔记（$formatTag · ${selection.size} 条）"
                            }
                        )
                    }
                }
            }
        }

        

        
        OverlayDialog(
            show = showPickDialog,
            title = "选择备份文件",
            summary = "内部存储/$BACKUP_DIR_PATH",
            onDismissRequest = { showPickDialog = false }
        ) {
            if (backups.isEmpty()) {
                Text("该目录下还没有备份文件，可以先到系统「本地备份」里只勾选笔记进行备份。")
            } else {
                val currentPath = currentTarget()?.file?.absolutePath
                Column {
                    backups.forEach { b ->
                        val inUse = b.file.absolutePath == currentPath
                        DialogRow(
                            title = b.name,
                            value = if (inUse) "✓ 使用中" else "${b.displaySize} · ${b.displayTime}",
                            onClick = {
                                usePickedFile(b.file, b.name, b.isZip)
                                showPickDialog = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { pickLauncher.launch(backupDirInitialUri()) },
                    modifier = Modifier.weight(1f)
                ) { Text("其他位置…") }
                Button(
                    onClick = { showPickDialog = false },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
            }
        }

        OverlayDialog(
            show = showPermission,
            title = "需要文件访问权限",
            summary = "导出需要读取 MIUI 备份目录，并写入 Download/$OUTPUT_DIR_NAME。" +
                    "请在接下来打开的页面里选择「允许管理所有文件」。",
            onDismissRequest = { showPermission = false }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { showPermission = false },
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = { requestPermission() },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                ) { Text("去授权") }
            }
        }

        OverlayDialog(
            show = showSettings,
            title = "设置",
            onDismissRequest = { showSettings = false }
        ) {
            Text(
                text = "导出格式",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )
            ExportFormat.entries.forEach { f ->
                DialogRow(
                    title = f.label,
                    value = if (f == format) "✓" else null,
                    onClick = {
                        format = f
                        Prefs.setFormat(ctx, f)
                    }
                )
            }
            
            if (format == ExportFormat.MARKDOWN) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Markdown 仅支持纯文本笔记；含图片的笔记将按下方格式导出，" +
                           "语音单独存为 .m4a 放在同目录。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                )
                listOf(ExportFormat.WORD, ExportFormat.PDF).forEach { f ->
                    DialogRow(
                        title = "含图片笔记导出为 ${f.label}",
                        value = if (f == fallbackFormat) "✓" else null,
                        onClick = {
                            fallbackFormat = f
                            Prefs.setFallbackFormat(ctx, f)
                        },
                        
                        titleStyle = MiuixTheme.textStyles.footnote1,
                        indent = 20.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            DialogRow(title = "关于", value = "›", onClick = {
                showSettings = false
                showAbout = true
            })
        }

        OverlayDialog(
            show = showAbout,
            title = "关于",
            onDismissRequest = { showAbout = false }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "笔记导出",
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "版本 $versionName",
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = "把小米笔记的本地备份解析后，导出成 Word、PDF 或 Markdown，" +
                           "保存到 Download/$OUTPUT_DIR_NAME。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(20.dp))
                
                Text(
                    text = "隐私说明",
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本应用完全在本地设备内运行，不请求网络权限，" +
                            "不收集任何个人信息，不会向外部服务器上传任何数据。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "文件解析、文档导出等全部处理流程均在本机完成。" +
                            "应用不会主动复制、外传您的备份文件；" +
                            "导出的文档，仅写入当前设备的指定位置。",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "开发者：$DEVELOPER",
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "源代码：",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Text(
                        text = "Github",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    index: Int,
    note: Note,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            state = if (selected) ToggleableState.On else ToggleableState.Off,
            onClick = onToggle,
            enabled = enabled
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${NoteRenderer.titleOf(note.text)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = fmtDate(note.created),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        PillButton(text = "导出", enabled = enabled, minWidth = 76.dp, onClick = onExport)
    }
}

private fun fmtDate(ms: Long): String =
    if (ms <= 0) "—"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

private fun toast(ctx: Context, msg: String) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
