package com.shu.notebackupexporter.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream


class PdfWriter(private val target: File) : NoteDoc {

    private companion object {
        const val PAGE_W = 595          
        const val PAGE_H = 842
        const val MARGIN = 56.7f        
        const val CONTENT_W = PAGE_W - MARGIN * 2

        const val TITLE_SIZE = 19f
        const val H1_SIZE = 15f
        const val H2_SIZE = 13f
        const val H3_SIZE = 12f
        const val BODY_SIZE = 10.5f
        const val META_SIZE = 8.5f

        const val IMG_MAX_PX = 1400     
        const val FIRST_LINE_INDENT = BODY_SIZE * 2   
        const val LIST_INDENT = BODY_SIZE * 2         
        const val WORD_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._%+-@~/"
    }

    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = MARGIN
    private var pageNo = 0
    private var imageCount = 0

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = BODY_SIZE
    }
    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x59, 0x59, 0x59)
        textSize = META_SIZE
    }
    private val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x59, 0x59, 0x59)
        textSize = BODY_SIZE
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = TITLE_SIZE
        isFakeBoldText = true
    }
    private val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = H1_SIZE
        isFakeBoldText = true
    }
    private val h2Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = H2_SIZE
        isFakeBoldText = true
    }
    private val h3Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = H3_SIZE
        isFakeBoldText = true
    }
    private val pageNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0x80, 0x80, 0x80)
        textSize = META_SIZE
        textAlign = Paint.Align.CENTER
    }
    private val quoteBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xBF, 0xBF, 0xBF)
    }

    
    private val charW = HashMap<Pair<Char, Float>, Float>(8192)
    private val tokenW = HashMap<Pair<String, Float>, Float>(2048)

    private fun widthOf(ch: Char, paint: Paint): Float =
        charW.getOrPut(ch to paint.textSize) { paint.measureText(ch.toString()) }

    private fun widthOfToken(token: String, paint: Paint): Float =
        if (token.length == 1) widthOf(token[0], paint)
        else tokenW.getOrPut(token to paint.textSize) { paint.measureText(token) }

    
    private fun tokenize(s: String): List<String> {
        val tokens = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            val n = Character.charCount(cp)
            val piece = s.substring(i, i + n)
            if (n == 1 && cp < 128 && WORD_CHARS.indexOf(piece[0]) >= 0) {
                sb.append(piece)
            } else {
                if (sb.isNotEmpty()) {
                    tokens += sb.toString()
                    sb.setLength(0)
                }
                tokens += piece
            }
            i += n
        }
        if (sb.isNotEmpty()) tokens += sb.toString()
        return tokens
    }

    private fun ensurePage() {
        if (page == null) newPage()
    }

    private fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
        page = p
        canvas = p.canvas
        y = MARGIN
        
        canvas?.drawText("第 $pageNo 页", PAGE_W / 2f, PAGE_H - 18f, pageNumPaint)
    }

    
    private fun advance(h: Float) {
        y += h
        if (y > PAGE_H - MARGIN) newPage()
    }

    
    private fun emitLine(tokens: List<String>, paint: Paint, x0: Float, lineH: Float, justify: Boolean) {
        
        if (y + lineH > PAGE_H - MARGIN) newPage()
        var w = 0f
        for (t in tokens) w += widthOfToken(t, paint)
        val avail = CONTENT_W - (x0 - MARGIN)
        if (justify && tokens.size > 1 && w < avail - 0.5f) {
            val gap = (avail - w) / (tokens.size - 1)
            var x = x0
            for (t in tokens) {
                canvas?.drawText(t, x, y + paint.textSize, paint)
                x += widthOfToken(t, paint) + gap
            }
        } else {
            val sb = StringBuilder()
            for (t in tokens) sb.append(t)
            canvas?.drawText(sb.toString(), x0, y + paint.textSize, paint)
        }
        advance(lineH)
    }

    
    private fun writable(
        str: String,
        paint: Paint,
        firstIndent: Float = 0f,
        hangIndent: Float = 0f,
        prefix: String = "",
        justify: Boolean = false,
    ) {
        if (str.isEmpty() && prefix.isEmpty()) {
            advance(paint.textSize * 0.8f)
            return
        }
        ensurePage()
        val lineH = paint.textSize * 1.55f
        val lines = str.split('\n')
        for (li in lines.indices) {
            val firstLine = li == 0
            val lastLine = li == lines.lastIndex
            val x0 = MARGIN + if (firstLine) firstIndent else hangIndent
            val avail = CONTENT_W - (x0 - MARGIN)

            val tokens = ArrayList<String>()
            if (firstLine && prefix.isNotEmpty()) tokens += tokenize(prefix)
            tokens += tokenize(lines[li])

            if (tokens.isEmpty()) {
                
                if (y + lineH > PAGE_H - MARGIN) newPage()
                advance(lineH)
                continue
            }

            val buf = ArrayList<String>()
            var w = 0f
            for (t in tokens) {
                val tw = widthOfToken(t, paint)
                if (w + tw > avail && buf.isNotEmpty()) {
                    
                    emitLine(
                        buf, paint, x0, lineH,
                        justify && !lastLine && buf.size > 1 && !(firstLine && prefix.isNotEmpty())
                    )
                    buf.clear()
                    w = 0f
                }
                buf += t
                w += tw
            }
            if (buf.isNotEmpty()) {
                
                emitLine(buf, paint, x0, lineH, false)
            }
        }
    }

    override fun addTitle(text: String) {
        advance(12f)
        writable(text, titlePaint)
        advance(12f)
    }

    override fun addHeading(text: String, level: Int) {
        val paint = when (level) {
            1 -> h1Paint
            2 -> h2Paint
            else -> h3Paint
        }
        advance(when (level) {
            1 -> 12f
            2 -> 10f
            else -> 8f
        })
        writable(text, paint)
        advance(when (level) {
            1 -> 6f
            2 -> 5f
            else -> 4f
        })
    }

    override fun addMeta(text: String) {
        writable(text, metaPaint)
        advance(6f)
    }

    override fun addAudioRef(fileName: String) {
        writable("🎤 语音附件：$fileName", metaPaint)
        advance(6f)
    }

    override fun addParagraph(text: String) {
        writable(text, bodyPaint, firstIndent = FIRST_LINE_INDENT, justify = true)
        advance(6f)   
    }

    override fun addBullet(text: String) {
        writable(text, bodyPaint, prefix = "• ", hangIndent = LIST_INDENT)
        advance(2f)
    }

    override fun addNumbered(text: String, number: Int) {
        writable(text, bodyPaint, prefix = "$number. ", hangIndent = LIST_INDENT)
        advance(2f)
    }

    override fun addCheckItem(text: String, checked: Boolean) {
        writable(text, bodyPaint, prefix = if (checked) "☑ " else "☐ ", hangIndent = LIST_INDENT)
        advance(2f)
    }

    override fun addQuote(text: String) {
        advance(4f)
        val startY = y
        writable(text, quotePaint, hangIndent = LIST_INDENT)
        
        val top = if (y < startY) MARGIN else startY
        canvas?.drawRect(MARGIN + 2f, top + 2f, MARGIN + 4.5f, y - 2f, quoteBarPaint)
        advance(4f)
    }

    override fun addIndexEntry(index: Int, date: String, title: String) {
        ensurePage()
        canvas?.drawText("$index.", MARGIN, y + metaPaint.textSize, metaPaint)
        writable("$date  $title", metaPaint, firstIndent = 34f, hangIndent = 34f)
        advance(4f)
    }

    override fun addBlankParagraph() = advance(bodyPaint.textSize * 0.8f)

    override fun addPageBreak() = newPage()

    override fun addImage(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= IMG_MAX_PX || bounds.outHeight / (sample * 2) >= IMG_MAX_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp: Bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false

        try {
            var w = CONTENT_W
            var h = bmp.height * (w / bmp.width)
            val maxH = (PAGE_H - MARGIN * 2) * 0.88f
            if (h > maxH) {
                h = maxH
                w = bmp.width * (h / bmp.height)
            }
            ensurePage()
            if (y + h > PAGE_H - MARGIN) newPage()
            advance(6f)
            canvas?.drawBitmap(bmp, MARGIN + (CONTENT_W - w) / 2f, y, null)
            y += h + 8f
            imageCount++
            return true
        } finally {
            bmp.recycle()
        }
    }

    override fun write(title: String) {
        page?.let { doc.finishPage(it) }
        page = null
        canvas = null
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { doc.writeTo(it) }
        doc.close()
    }
}
