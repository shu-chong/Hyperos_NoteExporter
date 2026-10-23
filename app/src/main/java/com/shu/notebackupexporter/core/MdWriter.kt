package com.shu.notebackupexporter.core

import java.io.File


class MdWriter(private val target: File) : NoteDoc {

    private val sb = StringBuilder()

    
    private fun ensureBlank() {
        when {
            sb.isEmpty() -> {}
            sb.endsWith("\n\n") -> {}
            sb.endsWith("\n") -> sb.append('\n')
            else -> sb.append("\n\n")
        }
    }

    override fun addTitle(text: String) {
        ensureBlank()
        sb.append("# ").append(text).append("\n\n")
    }

    override fun addHeading(text: String, level: Int) {
        ensureBlank()
        val hashes = when (level) {
            1 -> "##"
            2 -> "###"
            else -> "####"
        }
        sb.append(hashes).append(' ').append(text).append("\n\n")
    }

    override fun addMeta(text: String) {
        ensureBlank()
        sb.append(text).append("\n\n")
    }

    override fun addParagraph(text: String) {
        ensureBlank()
        sb.append(text).append("\n\n")
    }

    override fun addBullet(text: String) {
        ensureBlank()
        sb.append("- ").append(text).append('\n')
    }

    override fun addNumbered(text: String, number: Int) {
        ensureBlank()
        sb.append(number).append(". ").append(text).append('\n')
    }

    override fun addCheckItem(text: String, checked: Boolean) {
        ensureBlank()
        sb.append(if (checked) "- [x] " else "- [ ] ").append(text).append('\n')
    }

    override fun addQuote(text: String) {
        ensureBlank()
        text.split('\n').forEach { sb.append("> ").append(it).append('\n') }
        sb.append('\n')
    }

    override fun addAudioRef(fileName: String) {
        ensureBlank()
        sb.append("> 🎤 语音附件：").append(fileName).append("\n\n")
    }

    override fun addIndexEntry(index: Int, date: String, title: String) {
        ensureBlank()
        sb.append(index).append(". ").append(date).append(' ').append(title).append('\n')
    }

    override fun addBlankParagraph() = ensureBlank()

    override fun addPageBreak() {
        ensureBlank()
        sb.append("---\n\n")
    }

    override fun addImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val assetsDir = File(target.parentFile, "assets").apply { mkdirs() }
        val dest = File(assetsDir, file.name)
        runCatching { file.copyTo(dest, overwrite = true) }.getOrElse { return false }
        ensureBlank()
        sb.append("![图片](assets/").append(file.name).append(")\n\n")
        return true
    }

    override fun write(title: String) {
        target.parentFile?.mkdirs()
        target.writeText(sb.toString(), Charsets.UTF_8)
    }
}
