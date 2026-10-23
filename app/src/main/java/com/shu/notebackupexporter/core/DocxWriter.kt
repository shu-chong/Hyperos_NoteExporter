package com.shu.notebackupexporter.core

import android.graphics.BitmapFactory
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class DocxWriter(private val target: File) : NoteDoc {

    companion object {
        private const val EMU_PER_PX = 9525L          
        private const val CONTENT_W = 6_120_000L      
        private const val CONTENT_H = 8_140_000L      
        private const val IND_LIST = 420              
        private const val FIRST_LINE = 420            

        private const val NS = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\""

        private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpg" ContentType="image/jpeg"/><Default Extension="jpeg" ContentType="image/jpeg"/><Default Extension="png" ContentType="image/png"/><Default Extension="gif" ContentType="image/gif"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/><Override PartName="/word/theme/theme1.xml" ContentType="application/vnd.openxmlformats-officedocument.theme+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/><Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>"""

        private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>"""

        private const val APP_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>NoteExporter</Application></Properties>"""

        
        private const val THEME = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<a:theme xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" name="Office Theme"><a:themeElements><a:clrScheme name="Office"><a:dk1><a:sysClr val="windowText" lastClr="000000"/></a:dk1><a:lt1><a:sysClr val="window" lastClr="FFFFFF"/></a:lt1><a:dk2><a:srgbClr val="44546A"/></a:dk2><a:lt2><a:srgbClr val="E7E6E6"/></a:lt2><a:accent1><a:srgbClr val="4472C4"/></a:accent1><a:accent2><a:srgbClr val="ED7D31"/></a:accent2><a:accent3><a:srgbClr val="A5A5A5"/></a:accent3><a:accent4><a:srgbClr val="FFC000"/></a:accent4><a:accent5><a:srgbClr val="5B9BD5"/></a:accent5><a:accent6><a:srgbClr val="70AD47"/></a:accent6><a:hlink><a:srgbClr val="0563C1"/></a:hlink><a:folHlink><a:srgbClr val="954F72"/></a:folHlink></a:clrScheme><a:fontScheme name="Office"><a:majorFont><a:latin typeface="Calibri Light"/><a:ea typeface=""/><a:cs typeface=""/></a:majorFont><a:minorFont><a:latin typeface="Calibri"/><a:ea typeface=""/><a:cs typeface=""/></a:minorFont></a:fontScheme><a:fmtScheme name="Office"><a:fillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w="9525" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln><a:ln w="9525" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln><a:ln w="9525" cap="flat" cmpd="sng" algn="ctr"><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:prstDash val="solid"/></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill><a:solidFill><a:schemeClr val="phClr"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements></a:theme>"""

        
        private const val FOOTER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:pPr><w:jc w:val="center"/><w:spacing w:before="0" w:after="0"/></w:pPr><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:t xml:space="preserve">第 </w:t></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:t>1</w:t></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:fldChar w:fldCharType="end"/></w:r><w:r><w:rPr><w:color w:val="808080"/><w:sz w:val="17"/></w:rPr><w:t xml:space="preserve"> 页</w:t></w:r></w:p></w:ftr>"""

        private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles $NS><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial" w:eastAsiaTheme="minorEastAsia" w:cs="Arial"/><w:sz w:val="21"/><w:szCs w:val="21"/><w:lang w:val="en-US" w:eastAsia="zh-CN"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="80" w:line="288" w:lineRule="auto"/><w:jc w:val="both"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/><w:pPr><w:jc w:val="both"/></w:pPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="0"/><w:spacing w:before="240" w:after="120"/></w:pPr><w:rPr><w:b/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="1"/><w:spacing w:before="200" w:after="100"/></w:pPr><w:rPr><w:b/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:outlineLvl w:val="2"/><w:spacing w:before="160" w:after="80"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/><w:szCs w:val="24"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:before="240" w:after="240"/></w:pPr><w:rPr><w:b/><w:sz w:val="48"/><w:szCs w:val="48"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Meta"><w:name w:val="Meta"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="120"/></w:pPr><w:rPr><w:color w:val="595959"/><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr></w:style></w:styles>"""

        private const val SECT_PR = """<w:sectPr><w:footerReference w:type="default" r:id="rId2"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134" w:header="720" w:footer="720" w:gutter="0"/></w:sectPr>"""
    }

    private val body = StringBuilder(1 shl 16)
    private val imageRels = StringBuilder()
    private val images = LinkedHashMap<String, File>()          
    private val mediaBySource = HashMap<String, Pair<String, String>>()  
    private var relSeq = 100
    private var drawSeq = 1
    private var imgSeq = 1

    
    private var pendingPageBreak = false

    var imageCount = 0
        private set

    

    override fun addTitle(text: String) = applyStyle(text, "Title")

    override fun addHeading(text: String, level: Int) =
        applyStyle(text, when (level) {
            1 -> "Heading1"
            2 -> "Heading2"
            else -> "Heading3"
        })

    override fun addMeta(text: String) = applyStyle(text, "Meta")

    override fun addAudioRef(fileName: String) = applyStyle("🎤 语音附件：$fileName", "Meta")

    private fun applyStyle(text: String, style: String) {
        body.append("<w:p><w:pPr><w:pStyle w:val=\"").append(style)
            .append("\"/></w:pPr>").append(breakRun())
            .append("<w:r><w:t xml:space=\"preserve\">")
            .append(escape(text)).append("</w:t></w:r></w:p>")
    }

    
    override fun addParagraph(text: String) = appendRuns(text) { sb ->
        body.append("<w:p><w:pPr><w:spacing w:after=\"120\"/>")
            .append("<w:ind w:firstLineChars=\"200\" w:firstLine=\"").append(FIRST_LINE)
            .append("\"/></w:pPr>").append(breakRun()).append(sb).append("</w:p>")
    }

    override fun addBullet(text: String) = addMarkerItem(text, "• ")

    override fun addNumbered(text: String, number: Int) = addMarkerItem(text, "$number. ")

    override fun addCheckItem(text: String, checked: Boolean) =
        addMarkerItem(text, if (checked) "☑ " else "☐ ")

    
    private fun addMarkerItem(text: String, prefix: String) = appendRuns(text) { sb ->
        body.append("<w:p><w:pPr><w:spacing w:after=\"40\"/>")
            .append("<w:ind w:left=\"").append(IND_LIST)
            .append("\" w:hanging=\"").append(IND_LIST).append("\"/></w:pPr>")
            .append(breakRun())
            .append("<w:r><w:t xml:space=\"preserve\">").append(escape(prefix)).append("</w:t></w:r>")
            .append(sb).append("</w:p>")
    }

    override fun addQuote(text: String) {
        val sb = StringBuilder()
        escape(text).split('\n').forEachIndexed { i, part ->
            if (i > 0) sb.append("<w:r><w:br/></w:r>")
            sb.append("<w:r><w:rPr><w:color w:val=\"595959\"/></w:rPr>")
                .append("<w:t xml:space=\"preserve\">").append(part).append("</w:t></w:r>")
        }
        body.append("<w:p><w:pPr><w:pBdr><w:left w:val=\"single\" w:sz=\"18\" w:space=\"8\" w:color=\"BFBFBF\"/></w:pBdr>")
            .append("<w:spacing w:before=\"80\" w:after=\"80\"/>")
            .append("<w:ind w:left=\"").append(IND_LIST).append("\"/></w:pPr>")
            .append(breakRun()).append(sb).append("</w:p>")
    }

    override fun addIndexEntry(index: Int, date: String, title: String) {
        val rpr = "<w:rPr><w:color w:val=\"404040\"/><w:sz w:val=\"18\"/></w:rPr>"
        body.append("<w:p><w:pPr><w:tabs><w:tab w:val=\"left\" w:pos=\"340\"/></w:tabs>")
            .append("<w:spacing w:after=\"40\"/><w:ind w:left=\"340\" w:hanging=\"340\"/></w:pPr>")
            .append(breakRun())
            .append("<w:r>").append(rpr).append("<w:t xml:space=\"preserve\">").append(index)
            .append(".</w:t></w:r>")
            .append("<w:r>").append(rpr).append("<w:tab/></w:r>")
            .append("<w:r>").append(rpr).append("<w:t xml:space=\"preserve\">")
            .append(escape(date)).append("</w:t></w:r>")
            .append("<w:r>").append(rpr).append("<w:t xml:space=\"preserve\">  ")
            .append(escape(title)).append("</w:t></w:r></w:p>")
    }

    override fun addBlankParagraph() {
        body.append("<w:p><w:pPr><w:spacing w:after=\"0\"/></w:pPr>")
            .append(breakRun()).append("</w:p>")
    }

    
    override fun addPageBreak() {
        pendingPageBreak = true
    }

    private fun breakRun(): String =
        if (pendingPageBreak) {
            pendingPageBreak = false
            "<w:r><w:br w:type=\"page\"/></w:r>"
        } else ""

    
    private inline fun appendRuns(text: String, block: (StringBuilder) -> Unit) {
        val sb = StringBuilder()
        escape(text).split('\n').forEachIndexed { i, part ->
            if (i > 0) sb.append("<w:r><w:br/></w:r>")
            sb.append("<w:r><w:t xml:space=\"preserve\">").append(part).append("</w:t></w:r>")
        }
        block(sb)
    }

    
    override fun addImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false

        val cached = mediaBySource[file.absolutePath]
        val mediaName: String
        val rid: String
        if (cached != null) {
            mediaName = cached.first
            rid = cached.second
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val pw = opts.outWidth
            val ph = opts.outHeight
            if (pw <= 0 || ph <= 0) return false

            val ext = sniff(file)
            mediaName = "image${imgSeq++}.$ext"
            rid = "rId${relSeq++}"
            images[mediaName] = file
            imageRels.append("<Relationship Id=\"").append(rid)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/")
                .append(mediaName).append("\"/>")
            mediaBySource[file.absolutePath] = mediaName to rid
            sizeCache[mediaName] = pw to ph
        }

        val (pw, ph) = sizeCache[mediaName] ?: return false
        var cx = pw.toLong() * EMU_PER_PX
        var cy = ph.toLong() * EMU_PER_PX
        val scale = minOf(1.0, CONTENT_W.toDouble() / cx, CONTENT_H.toDouble() / cy)
        cx = (cx * scale).toLong()
        cy = (cy * scale).toLong()

        val did = drawSeq++
        body.append("<w:p><w:pPr><w:jc w:val=\"center\"/>")
            .append("<w:spacing w:before=\"120\" w:after=\"120\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr>")
            .append(breakRun())
            .append("<w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">")
            .append("<wp:extent cx=\"").append(cx).append("\" cy=\"").append(cy).append("\"/>")
            .append("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>")
            .append("<wp:docPr id=\"").append(did).append("\" name=\"Picture").append(did).append("\"/>")
            .append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
            .append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            .append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"").append(did).append("\" name=\"").append(mediaName)
            .append("\"/><pic:cNvPicPr/></pic:nvPicPr>")
            .append("<pic:blipFill><a:blip r:embed=\"").append(rid).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            .append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(cx).append("\" cy=\"").append(cy)
            .append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>")
            .append("</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
        imageCount++
        return true
    }

    private val sizeCache = HashMap<String, Pair<Int, Int>>()

    

    override fun write(title: String) {
        target.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target), 1 shl 16)).use { zos ->
            zos.setLevel(Deflater.BEST_SPEED)
            putText(zos, "[Content_Types].xml", CONTENT_TYPES)
            putText(zos, "_rels/.rels", ROOT_RELS)
            putText(zos, "word/document.xml", documentXml())
            putText(zos, "word/_rels/document.xml.rels", docRels())
            putText(zos, "word/styles.xml", STYLES)
            putText(zos, "word/footer1.xml", FOOTER)
            putText(zos, "word/theme/theme1.xml", THEME)
            putText(zos, "docProps/core.xml", coreXml(title))
            putText(zos, "docProps/app.xml", APP_XML)

            
            zos.setLevel(Deflater.NO_COMPRESSION)
            for ((name, file) in images) {
                zos.putNextEntry(ZipEntry("word/media/$name"))
                file.inputStream().use { it.copyTo(zos, 1 shl 16) }
                zos.closeEntry()
            }
        }
    }

    private fun documentXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document $NS><w:body>" +
                body + SECT_PR + "</w:body></w:document>"

    private fun docRels(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>" +
                "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"theme/theme1.xml\"/>" +
                imageRels + "</Relationships>"

    private fun coreXml(title: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
                "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
                "<dc:title>" + escape(title) + "</dc:title>" +
                "<dc:creator>笔记导出</dc:creator><cp:lastModifiedBy>笔记导出</cp:lastModifiedBy>" +
                "</cp:coreProperties>"

    private fun putText(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun sniff(file: File): String = runCatching {
        val head = ByteArray(8)
        val n = file.inputStream().use { it.read(head) }
        BackupArchive.sniffExtension(head.copyOf(maxOf(n, 0)))
    }.getOrDefault("jpg")

    

    
    private fun clean(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val ok = cp == 0x09 || cp == 0x0A || cp == 0x0D ||
                    (cp in 0x20..0xD7FF) || (cp in 0xE000..0xFFFD) || (cp in 0x10000..0x10FFFF)
            if (ok) sb.appendCodePoint(cp)
            i += n
        }
        return sb.toString()
    }

    private fun escape(s: String): String {
        val t = clean(s.replace("\r\n", "\n").replace('\r', '\n'))
        val sb = StringBuilder(t.length + 16)
        for (c in t) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
