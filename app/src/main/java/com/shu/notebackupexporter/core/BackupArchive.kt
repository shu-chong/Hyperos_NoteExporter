package com.shu.notebackupexporter.core

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipFile


object BackupArchive {

    private val MAGIC = "ANDROID BACKUP".toByteArray(Charsets.US_ASCII)
    private const val BLOCK = 512

    
    fun findNotesEntry(zip: ZipFile): String? =
        zip.entries().asSequence()
            .filter { !it.isDirectory }
            .firstOrNull { it.name.contains("com.miui.notes") && it.name.endsWith(".bak") }
            ?.name

    
    fun openTarStream(input: InputStream): InputStream {
        val ins = BufferedInputStream(input, 1 shl 16)
        skipToMagic(ins, MAGIC)
        readLine(ins)                       
        val version = readLine(ins)
        val compressed = readLine(ins)
        val encryption = readLine(ins)
        require(encryption == "none") { "备份已加密（$encryption），无法读取" }
        require(version.toIntOrNull()?.let { it in 1..5 } == true) { "不支持的备份版本: $version" }
        return if (compressed == "1") {
            InflaterInputStream(ins, Inflater(), 1 shl 16)
        } else {
            ins
        }
    }

    private fun skipToMagic(ins: InputStream, magic: ByteArray) {
        var matched = 0
        while (true) {
            val b = ins.read()
            if (b < 0) throw IOException("不是有效的 Android Backup 文件（未找到标识）")
            if (b.toByte() == magic[matched]) {
                matched++
                if (matched == magic.size) return
            } else {
                matched = if (b.toByte() == magic[0]) 1 else 0
            }
        }
    }

    private fun readLine(ins: InputStream): String {
        val sb = StringBuilder(16)
        while (true) {
            val b = ins.read()
            if (b < 0 || b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    
    class Entry(val name: String, val size: Long, val pad: Int)

    
    class TarReader(private val ins: InputStream) {
        private var pendingLongName: String? = null

        fun next(): Entry? {
            while (true) {
                val header = readFully(ins, BLOCK) ?: return null
                if (header.size < BLOCK) return null
                if (header.all { it == 0.toByte() }) return null

                val name0 = readCString(header, 0, 100)
                val size = readOctal(header, 124, 12)
                val type = header[156].toInt().toChar()
                val prefix = readCString(header, 345, 155)
                val fullName = if (prefix.isNotEmpty()) "$prefix/$name0" else name0
                val pad = ((BLOCK - (size % BLOCK)) % BLOCK).toInt()

                when (type) {
                    'L' -> {
                        val data = readFully(ins, size.toInt())
                        skipFully(ins, pad.toLong())
                        pendingLongName = data?.toString(Charsets.UTF_8)?.trimEnd('\u0000', '\n')
                        continue
                    }
                    'x', 'g' -> {
                        skipFully(ins, size + pad)
                        continue
                    }
                    '5' -> {
                        skipFully(ins, pad.toLong())
                        continue
                    }
                }
                val nm = pendingLongName ?: fullName
                pendingLongName = null
                return Entry(nm, size, pad)
            }
        }
    }

    fun readFully(ins: InputStream, n: Int): ByteArray? {
        if (n <= 0) return ByteArray(0)
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(out, off, n - off)
            if (r < 0) return if (off == 0) null else out.copyOf(off)
            off += r
        }
        return out
    }

    fun skipFully(ins: InputStream, n: Long) {
        var left = n
        val tmp = ByteArray(8192)
        while (left > 0) {
            val r = ins.read(tmp, 0, minOf(left, tmp.size.toLong()).toInt())
            if (r < 0) return
            left -= r
        }
    }

    private fun readCString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        val max = minOf(off + len, b.size)
        while (end < max && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.UTF_8)
    }

    private fun readOctal(b: ByteArray, off: Int, len: Int): Long {
        var v = 0L
        var i = off
        val max = minOf(off + len, b.size)
        while (i < max) {
            val c = b[i].toInt()
            if (c == 0 || c == ' '.code) { i++; continue }
            if (c < '0'.code || c > '7'.code) break
            v = v * 8 + (c - '0'.code)
            i++
        }
        return v
    }

    
    fun sniffExtension(head: ByteArray): String = when {
        head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "jpg"
        head.size >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> "png"
        head.size >= 4 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() -> "gif"
        head.size >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> "m4a"
        head.size >= 4 && head[0] == '#'.code.toByte() && head[1] == '!'.code.toByte() -> "amr"
        head.size >= 3 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() &&
            head[2] == '3'.code.toByte() -> "mp3"
        head.size >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFB.toByte() -> "mp3"
        head.size >= 4 && head[0] == 'R'.code.toByte() && head[1] == 'I'.code.toByte() -> "wav"
        head.size >= 4 && head[0] == 'O'.code.toByte() && head[1] == 'g'.code.toByte() -> "ogg"
        else -> "jpg"
    }

    fun isZip(file: File): Boolean = runCatching {
        ZipFile(file).use { it.entries().hasMoreElements() }
    }.getOrDefault(false)
}
