package com.shu.notebackupexporter.core

import java.io.File


object NoteRenderer {

    private val IMG_MD = Regex("""!\[[^\]]*]\(\s*https://com\.miui\.notes/image/([^)\s?#]+)[^)]*\)""")
    private val IMG_PLACEHOLDER = Regex("""[^\x00-\x7f]\s*([0-9a-fA-F]{40})(?:\s*<0/></>)?""")
    
    private val AUDIO_MD = Regex("""<??!?\[[^\]]*]\(\s*https://com\.miui\.notes/audio/([0-9A-Za-z._-]+)\s*\)>?""")
    private val ESCAPE = Regex("""\\([!-/:-@\[-`{-~])""")
    private val WS = Regex("""\s+""")

    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val TASK = Regex("""^[-*+]\s+\[([ xX])]\s+(.*)$""")
    private val BULLET = Regex("""^[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\d{1,2})[.)、]\s+(.*)$""")
    private val QUOTE = Regex("""^>\s?(.*)$""")

    
    private val TITLE_PREFIX =
        Regex("""^\s*(?:#{1,6}\s+|[-*+]\s+(?:\[[ xX]]\s+)?|>\s?|\d{1,2}[.)、]\s+)""")

    fun mdUnescape(s: String): String = ESCAPE.replace(s) { it.groupValues[1] }

    
    fun plainText(raw: String): String =
        WS.replace(
            mdUnescape(
                AUDIO_MD.replace(
                    IMG_MD.replace(IMG_PLACEHOLDER.replace(raw, " "), " "),
                    " ",
                )
            ).replace('\u2062', ' '),
            " ",
        ).trim()

    fun imageIds(raw: String): List<String> {
        val md = IMG_MD.findAll(raw).map { it.groupValues[1] }
        val ph = IMG_PLACEHOLDER.findAll(raw).map { it.groupValues[1].lowercase() }
        return (md + ph).distinct().sorted().toList()
    }

    
    fun audioRefs(raw: String): List<String> =
        AUDIO_MD.findAll(raw).map { it.groupValues[1] }.distinct().toList()

    fun titleOf(raw: String): String {
        for (seg in segments(raw)) {
            if (seg is Seg.Text) {
                val line = mdUnescape(seg.text).replace('\u2062', ' ')
                    .lineSequence()
                    .map { TITLE_PREFIX.replace(it.trim(), "").trim() }
                    .firstOrNull { it.isNotEmpty() }
                if (line != null) return line
            }
        }
        val segs = segments(raw)
        return when {
            segs.any { it is Seg.Image } -> "（图片笔记）"
            segs.any { it is Seg.Audio } -> "（语音笔记）"
            else -> "（空笔记）"
        }
    }

    sealed interface Seg {
        data class Text(val text: String) : Seg
        data class Image(val ref: String) : Seg
        data class Audio(val fileName: String) : Seg
    }

    
    fun segments(raw: String): List<Seg> {
        val matches = ArrayList<Pair<IntRange, Seg>>()
        IMG_MD.findAll(raw).forEach { m ->
            matches += m.range to Seg.Image(m.groupValues[1])
        }
        IMG_PLACEHOLDER.findAll(raw).forEach { m ->
            matches += m.range to Seg.Image(m.groupValues[1].lowercase())
        }
        AUDIO_MD.findAll(raw).forEach { m ->
            matches += m.range to Seg.Audio(m.groupValues[1])
        }
        matches.sortBy { it.first.first }
        val out = ArrayList<Seg>()
        var last = 0
        for ((range, seg) in matches) {
            if (range.first < last) continue
            if (range.first > last) out += Seg.Text(raw.substring(last, range.first))
            out += seg
            last = range.last + 1
        }
        if (last < raw.length) out += Seg.Text(raw.substring(last))
        return out
    }

    
    fun render(doc: NoteDoc, raw: String, attDir: File, audioName: (String) -> String = { it }): Int {
        var images = 0
        var wrote = false
        val group = ArrayList<String>()

        
        fun flushGroup() {
            if (group.isEmpty()) return
            doc.addParagraph(group.joinToString("\n"))
            group.clear()
            wrote = true
        }

        for (seg in segments(raw)) {
            when (seg) {
                is Seg.Image -> {
                    flushGroup()
                    val f = File(attDir, seg.ref)
                    if (doc.addImage(f)) {
                        images++
                        wrote = true
                    } else {
                        doc.addParagraph("[图片缺失 ${seg.ref.take(12)}…]")
                        wrote = true
                    }
                }

                is Seg.Audio -> {
                    flushGroup()
                    doc.addAudioRef(audioName(seg.fileName))
                    wrote = true
                }

                is Seg.Text -> {
                    val text = seg.text.replace('\u2062', ' ')
                    for (rawLine in text.split('\n')) {
                        val line = rawLine.trimEnd()
                        if (line.isBlank()) {
                            
                            flushGroup()
                            continue
                        }
                        val mh = HEADING.matchEntire(line)
                        val mt = TASK.matchEntire(line)
                        val mb = BULLET.matchEntire(line)
                        val mo = ORDERED.matchEntire(line)
                        val mq = QUOTE.matchEntire(line)
                        when {
                            mh != null -> {
                                flushGroup()
                                
                                val level = if (mh.groupValues[1].length <= 1) 2 else 3
                                doc.addHeading(mdUnescape(mh.groupValues[2]), level)
                                wrote = true
                            }

                            mt != null -> {
                                flushGroup()
                                doc.addCheckItem(
                                    mdUnescape(mt.groupValues[2]),
                                    mt.groupValues[1].lowercase() == "x"
                                )
                                wrote = true
                            }

                            mb != null -> {
                                flushGroup()
                                doc.addBullet(mdUnescape(mb.groupValues[1]))
                                wrote = true
                            }

                            mo != null -> {
                                flushGroup()
                                doc.addNumbered(
                                    mdUnescape(mo.groupValues[2]),
                                    mo.groupValues[1].toIntOrNull() ?: 1
                                )
                                wrote = true
                            }

                            mq != null -> {
                                flushGroup()
                                doc.addQuote(mdUnescape(mq.groupValues[1]))
                                wrote = true
                            }

                            else -> group += mdUnescape(line)
                        }
                    }
                }
            }
        }
        flushGroup()
        if (!wrote) doc.addParagraph("（此笔记在备份中无正文内容）")
        return images
    }
}
