package com.shu.notebackupexporter.core


data class Note(
    val id: String,
    val created: Long,
    val modified: Long,
    val folder: String?,
    val text: String,
    val legacy: Boolean = false,
)


object NotesParser {

    fun parse(proto: ByteArray): List<Note> {
        parseNewFormat(proto)?.let { if (it.isNotEmpty()) return it }
        return parseLegacyFormat(proto) ?: emptyList()
    }

    private fun parseNewFormat(proto: ByteArray): List<Note>? {
        var payload: ByteArray? = null
        runCatching {
            forEachField(proto) { f, w, r ->
                if (f == 10 && w == 2 && payload == null) payload = r.readLengthDelimited() else r.skip(w)
            }
        }.onFailure { return null }

        val body = payload ?: return null
        val notes = ArrayList<Note>()
        forEachField(body) { f, w, r ->
            if (w != 2 || f == 1) { r.skip(w); return@forEachField }
            val entry = r.readLengthDelimited()
            notes += parseNewNote(entry)
        }
        return notes.filter { it.text.isNotEmpty() || it.created > 0 }
    }

    private fun parseNewNote(entry: ByteArray): Note {
        var created = 0L
        var modified = 0L
        var folder: String? = null
        var text = ""
        var uuid = ""
        forEachField(entry) { f, w, r ->
            when {
                f == 5 && w == 0 -> created = r.readVarint()
                f == 6 && w == 0 -> modified = r.readVarint()
                f == 10 && w == 2 -> folder = r.readString()
                f == 100 && w == 2 -> text = r.readString()
                f == 106 && w == 2 -> uuid = r.readString()
                else -> r.skip(w)
            }
        }
        val id = uuid.take(8).ifEmpty { created.toString() }
        return Note(id, created, modified, folder, text)
    }

    private fun parseLegacyFormat(proto: ByteArray): List<Note>? {
        var container: ByteArray? = null
        runCatching {
            forEachField(proto) { f, w, r ->
                if (f == 6 && w == 2 && container == null) container = r.readLengthDelimited() else r.skip(w)
            }
        }.onFailure { return null }

        val body = container ?: return null
        val notes = ArrayList<Note>()
        forEachField(body) { _, w, r ->
            if (w != 2) { r.skip(w); return@forEachField }
            val entry = r.readLengthDelimited()
            val n = parseLegacyNote(entry)
            if (n != null) notes += n
        }
        return notes.ifEmpty { null }
    }

    private fun parseLegacyNote(entry: ByteArray): Note? {
        var id = ""
        var created = 0L
        var modified = 0L
        var folder: String? = null
        var text = ""
        forEachField(entry) { f, w, r ->
            when {
                f == 2 && w == 2 -> id = r.readString()
                f == 5 && w == 0 -> created = r.readVarint()
                f == 6 && w == 0 -> modified = r.readVarint()
                f == 8 && w == 2 -> if (text.isEmpty()) text = r.readString()
                f == 10 && w == 2 -> folder = r.readString()
                f == 9 && w == 2 -> {
                    val sub = r.readLengthDelimited()
                    if (text.isEmpty()) {
                        forEachField(sub) { f2, w2, r2 ->
                            if (f2 == 4 && w2 == 2) text = r2.readString() else r2.skip(w2)
                        }
                        
                        if (text.isEmpty()) {
                            val s = sub.toString(Charsets.UTF_8)
                            if (s.isNotBlank() && s.none { it.code < 0x20 }) text = s
                        }
                    }
                }
                else -> r.skip(w)
            }
        }
        if (created == 0L && text.isEmpty()) return null
        return Note(id.ifEmpty { created.toString() }, created, modified, folder, text, legacy = true)
    }
}
