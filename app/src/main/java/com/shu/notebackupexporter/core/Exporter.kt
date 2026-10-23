package com.shu.notebackupexporter.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile


class Exporter(private val context: Context) {

    data class Progress(val message: String, val current: Int = 0, val total: Int = 0)

    
    class Prepared(
        val notes: List<Note>,
        val attDir: File,
        val mergedDuplicates: Int,
        val imageCount: Int,
        val audioCount: Int,
        val backupName: String,
        val backupTime: String,
    ) {
        val size: Int get() = notes.size
    }

    data class Result(
        val exportedCount: Int,
        val imageCount: Int,
        val audioCount: Int,
        val markdownCount: Int,
        val fallbackCount: Int,
        val fallbackFormat: ExportFormat?,
        val combinedFile: File?,
        val perNoteCount: Int,
        val outputRoot: File,
        val format: ExportFormat,
    )

    

    fun prepare(backup: BackupLocator.BackupFile, onProgress: (Progress) -> Unit): Prepared {
        val attDir = File(context.cacheDir, ATT_DIR).apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            onProgress(Progress("正在读取备份文件…"))
            val proto = extractProto(backup, attDir, onProgress)

            onProgress(Progress("正在解析笔记数据…"))
            val parsed = NotesParser.parse(proto)
            require(parsed.isNotEmpty()) {
                "没有在备份里找到笔记。请确认备份时勾选的是「笔记」应用。"
            }

            val (unique, merged) = dedupe(parsed)
            val notes = unique.sortedWith(compareBy({ it.created }, { it.id }))
            return Prepared(
                notes = notes,
                attDir = attDir,
                mergedDuplicates = merged,
                imageCount = notes.flatMap { NoteRenderer.imageIds(it.text) }.distinct().size,
                audioCount = notes.flatMap { NoteRenderer.audioRefs(it.text) }.distinct().size,
                backupName = backup.name,
                backupTime = backup.displayTime,
            )
        } catch (t: Throwable) {
            attDir.deleteRecursively()
            throw t
        }
    }

    fun release(prepared: Prepared) {
        runCatching { prepared.attDir.deleteRecursively() }
    }

    

    
    fun export(
        prepared: Prepared,
        indices: List<Int>,
        outputRoot: File,
        format: ExportFormat,
        fallback: ExportFormat = ExportFormat.WORD,
        onProgress: (Progress) -> Unit,
    ): Result {
        require(indices.isNotEmpty()) { "没有选择要导出的笔记" }
        outputRoot.mkdirs()
        require(outputRoot.isDirectory) { "无法创建输出目录：${outputRoot.absolutePath}" }

        
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(Date())
        val outDir = File(outputRoot, stamp).apply { mkdirs() }

        val exportAll = indices.size == prepared.notes.size
        var perNoteCount = 0

        
        
        val imageCount = indices.asSequence()
            .flatMap { NoteRenderer.imageIds(prepared.notes[it].text).asSequence() }
            .distinct()
            .count()

        val audioCount = indices.asSequence()
            .flatMap { NoteRenderer.audioRefs(prepared.notes[it].text).asSequence() }
            .distinct()
            .count()

        
        
        val anyImage = indices.any { NoteRenderer.imageIds(prepared.notes[it].text).isNotEmpty() }
        val effectiveFormat =
            if (format == ExportFormat.MARKDOWN && anyImage) fallback else format
        var markdownCount = 0
        var fallbackCount = 0

        
        var combined: File? = null
        val audioDir = File(outDir, "语音附件")
        if (audioCount > 0) audioDir.mkdirs()
        if (exportAll) {
            onProgress(Progress("正在生成汇总文档…", 0, prepared.notes.size))
            val f = File(outDir, "小米笔记导出.${effectiveFormat.ext}")
            val doc = NoteDoc.create(effectiveFormat, f)
            writeHeader(doc, prepared, effectiveFormat)
            writeIndex(doc, prepared.notes)
            doc.addPageBreak()
            doc.addHeading("笔记正文", 1)
            prepared.notes.forEachIndexed { i, n ->
                writeNoteInto(doc, n, i + 1, prepared.attDir, copyAudioTo = audioDir)
                if (i != prepared.notes.lastIndex) doc.addPageBreak()
                if ((i + 1) % 10 == 0) onProgress(Progress("正在生成汇总文档…", i + 1, prepared.notes.size))
            }
            doc.write("小米笔记导出")
            combined = f
        }

        
        val dir = File(outDir, "单条笔记").apply { mkdirs() }
        val used = HashSet<String>()
        onProgress(Progress("正在生成单条文档…", 0, indices.size))
        indices.forEachIndexed { k, idx ->
            val n = prepared.notes[idx]
            
            val noteFormat = if (format == ExportFormat.MARKDOWN &&
                NoteRenderer.imageIds(n.text).isNotEmpty()
            ) fallback else format
            if (format == ExportFormat.MARKDOWN) {
                
                if (noteFormat == ExportFormat.MARKDOWN) markdownCount++ else fallbackCount++
            }
            val file = uniqueFile(dir, idx + 1, n, noteFormat, used)
            val doc = NoteDoc.create(noteFormat, file)
            writeNoteInto(doc, n, idx + 1, prepared.attDir, withTitle = true, copyAudioTo = dir)
            doc.write(NoteRenderer.titleOf(n.text).take(200))
            perNoteCount++
            if ((k + 1) % 5 == 0) onProgress(Progress("正在生成单条文档…", k + 1, indices.size))
        }

        return Result(
            exportedCount = indices.size,
            imageCount = imageCount,
            audioCount = audioCount,
            markdownCount = markdownCount,
            fallbackCount = fallbackCount,
            fallbackFormat = if (fallbackCount > 0) fallback else null,
            combinedFile = combined,
            perNoteCount = perNoteCount,
            outputRoot = outDir,
            format = format,
        )
    }

    

    private fun extractProto(
        backup: BackupLocator.BackupFile,
        attDir: File,
        onProgress: (Progress) -> Unit,
    ): ByteArray {
        if (backup.isZip) {
            ZipFile(backup.file).use { zip ->
                val entryName = BackupArchive.findNotesEntry(zip)
                    ?: throw IllegalStateException("这个备份里没有「笔记」应用的数据，请重新备份时勾选笔记。")
                zip.getInputStream(zip.getEntry(entryName)).use { input ->
                    return readTar(input, attDir, onProgress)
                }
            }
        }
        
        val bak = if (backup.file.isFile) {
            backup.file
        } else {
            backup.file.listFiles()
                ?.firstOrNull { it.name.contains("com.miui.notes") && it.name.endsWith(".bak") }
                ?: throw IllegalStateException("备份目录里没有找到笔记的 .bak 文件。")
        }
        bak.inputStream().use { return readTar(it, attDir, onProgress) }
    }

    private fun readTar(
        raw: InputStream,
        attDir: File,
        onProgress: (Progress) -> Unit,
    ): ByteArray {
        val tar = BackupArchive.openTarStream(raw)
        val reader = BackupArchive.TarReader(tar)
        var proto: ByteArray? = null
        var att = 0

        while (true) {
            val e = reader.next() ?: break
            when {
                e.name.endsWith("_tmp_bak") -> {
                    require(e.size in 1..(64L shl 20)) { "笔记数据大小异常：${e.size}" }
                    proto = BackupArchive.readFully(tar, e.size.toInt())
                    BackupArchive.skipFully(tar, e.pad.toLong())
                }

                e.name.contains("/miui_att/") -> {
                    val name = e.name.substringAfterLast('/')
                    if (name.isNotEmpty() && e.size > 0) {
                        copyN(tar, File(attDir, name), e.size)
                        att++
                        if (att % 40 == 0) onProgress(Progress("正在解析图片附件…（$att）"))
                    }
                    BackupArchive.skipFully(tar, e.pad.toLong())
                }

                else -> BackupArchive.skipFully(tar, e.size + e.pad)
            }
        }
        return proto ?: throw IllegalStateException("备份里没有找到笔记数据（_tmp_bak）。")
    }

    private fun copyN(ins: InputStream, out: File, size: Long) {
        out.parentFile?.mkdirs()
        FileOutputStream(out).use { fos ->
            var left = size
            val buf = ByteArray(1 shl 16)
            while (left > 0) {
                val r = ins.read(buf, 0, minOf(left, buf.size.toLong()).toInt())
                if (r < 0) break
                fos.write(buf, 0, r)
                left -= r
            }
        }
    }

    

    
    private fun dedupe(notes: List<Note>): Pair<List<Note>, Int> {
        val groups = LinkedHashMap<String, MutableList<Note>>()
        for (n in notes) {
            val plain = NoteRenderer.plainText(n.text)
            val key = if (plain.length < 30) {
                "U|${n.id}|${n.created}"
            } else {
                "T|$plain|I|" + NoteRenderer.imageIds(n.text).joinToString(",") +
                    "|A|" + NoteRenderer.audioRefs(n.text).joinToString(",")
            }
            groups.getOrPut(key) { ArrayList() } += n
        }
        val out = ArrayList<Note>(groups.size)
        var merged = 0
        for (g in groups.values) {
            if (g.size == 1) {
                out += g[0]
            } else {
                g.sortWith(compareByDescending<Note> { it.text.length }.thenByDescending { it.modified })
                out += g[0]
                merged += g.size - 1
            }
        }
        return out to merged
    }

    

    private fun writeHeader(doc: NoteDoc, prepared: Prepared, format: ExportFormat) {
        doc.addTitle("小米笔记 导出文档")
        doc.addMeta("来源：${prepared.backupName}（${prepared.backupTime}）")
        doc.addMeta(
            "笔记数量：${prepared.size} 条" +
                    if (prepared.mergedDuplicates > 0) "　·　已合并 ${prepared.mergedDuplicates} 条完全重复的记录" else ""
        )
        doc.addMeta("导出格式：${format.label}")
        doc.addMeta("导出时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date()))
        doc.addMeta("说明：正文中的图片已从备份附件还原并嵌入；语音为独立 .m4a 文件，见正文占位标注与「语音附件」目录。")
    }

    private fun writeIndex(doc: NoteDoc, notes: List<Note>) {
        doc.addPageBreak()
        doc.addHeading("目录", 1)
        notes.forEachIndexed { i, n ->
            val title = NoteRenderer.titleOf(n.text)
            val shown = if (title.length > 60) title.take(60) + "…" else title
            doc.addIndexEntry(i + 1, fmtDate(n.created), shown)
        }
    }

    private fun writeNoteInto(
        doc: NoteDoc,
        n: Note,
        index: Int,
        attDir: File,
        withTitle: Boolean = false,
        copyAudioTo: File? = null,
    ): Int {
        val title = NoteRenderer.titleOf(n.text)
        if (withTitle) {
            doc.addTitle(if (title.length <= 80) title else title.take(80) + "…")
        } else {
            doc.addHeading("$index. $title", 1)
        }
        val meta = buildString {
            append("创建：").append(fmtDate(n.created))
            if (n.modified != 0L && n.modified != n.created) append("　|　修改：").append(fmtDate(n.modified))
            if (!n.folder.isNullOrBlank()) append("　|　文件夹：").append(n.folder)
        }
        doc.addMeta(meta)

        
        val audioDisplay = HashMap<String, String>()
        NoteRenderer.audioRefs(n.text).forEachIndexed { k, raw ->
            val display = String.format(
                Locale.CHINA, "%03d_%s_%s_语音%d.m4a",
                index, fmtDate(n.created).substringBefore(' '), safeTitle(n), k + 1,
            )
            audioDisplay[raw] = display
            if (copyAudioTo != null) {
                val src = File(attDir, raw)
                if (src.isFile) {
                    runCatching { src.copyTo(File(copyAudioTo, display), overwrite = true) }
                }
            }
        }
        return NoteRenderer.render(doc, n.text, attDir) { audioDisplay[it] ?: it }
    }

    
    private fun safeTitle(n: Note): String {
        val rawTitle = NoteRenderer.titleOf(n.text)
        return buildString {
            for (c in rawTitle) {
                append(if (c in "\\/:*?\"<>|\r\n\t") '_' else c)
            }
        }.trim(' ', '.').replace(Regex("\\s+"), " ").take(48).ifBlank { "未命名" }
    }

    private fun uniqueFile(
        dir: File,
        index: Int,
        n: Note,
        format: ExportFormat,
        used: MutableSet<String>,
    ): File {
        val date = fmtDate(n.created).substringBefore(' ')
        val safe = safeTitle(n)

        var name = String.format(Locale.CHINA, "%03d_%s_%s", index, date, safe)
        var k = 1
        while (!used.add(name.lowercase(Locale.ROOT))) {
            name = String.format(Locale.CHINA, "%03d_%s_%s_%d", index, date, safe, k)
            k++
        }
        return File(dir, "$name.${format.ext}")
    }

    private fun fmtDate(ms: Long): String =
        if (ms <= 0) "—"
        else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(ms))

    private companion object {
        const val ATT_DIR = "attachments"
    }
}
