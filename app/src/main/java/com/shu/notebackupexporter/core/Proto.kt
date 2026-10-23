package com.shu.notebackupexporter.core


internal class ProtoReader(private val buf: ByteArray, private var pos: Int = 0) {

    val hasMore: Boolean get() = pos < buf.size

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(pos < buf.size) { "protobuf 数据在 varint 中途结束" }
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            require(shift <= 63) { "protobuf varint 过长" }
        }
    }

    fun readLengthDelimited(): ByteArray {
        val len = readVarint().toInt()
        require(len >= 0 && pos + len <= buf.size) { "protobuf 长度越界: $len" }
        return buf.copyOfRange(pos, pos + len).also { pos += len }
    }

    fun readString(): String = readLengthDelimited().toString(Charsets.UTF_8)

    fun skip(wire: Int) {
        when (wire) {
            0 -> readVarint()
            1 -> { require(pos + 8 <= buf.size); pos += 8 }
            2 -> { val n = readVarint().toInt(); require(pos + n <= buf.size); pos += n }
            5 -> { require(pos + 4 <= buf.size); pos += 4 }
            else -> throw IllegalStateException("protobuf 不支持的 wire type: $wire")
        }
    }
}


internal inline fun forEachField(
    buf: ByteArray,
    action: (field: Int, wire: Int, reader: ProtoReader) -> Unit,
) {
    val r = ProtoReader(buf)
    while (r.hasMore) {
        val tag = r.readVarint().toInt()
        action(tag ushr 3, tag and 7, r)
    }
}
