package com.shu.notebackupexporter.core
import java.io.File
enum class ExportFormat(val label: String, val ext: String) {
    WORD("Word（.docx）", "docx"),
    PDF("PDF（.pdf）", "pdf"),
}
interface NoteDoc {
    fun addTitle(text: String)
    fun addHeading(text: String, level: Int)
    fun addMeta(text: String)
    fun addParagraph(text: String)
    fun addBullet(text: String)
    fun addNumbered(text: String, number: Int)
    fun addCheckItem(text: String, checked: Boolean)
    fun addQuote(text: String)
    fun addIndexEntry(index: Int, date: String, title: String)
    fun addBlankParagraph()
    fun addPageBreak()
    fun addImage(file: File): Boolean
    fun write(title: String)
    companion object {
        fun create(format: ExportFormat, target: File): NoteDoc = when (format) {
            ExportFormat.WORD -> DocxWriter(target)
            ExportFormat.PDF -> PdfWriter(target)
        }
    }
}
