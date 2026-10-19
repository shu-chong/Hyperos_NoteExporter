package com.shu.notebackupexporter.core
import android.content.Context
object Prefs {
    private const val FILE = "note_exporter"
    private const val KEY_FORMAT = "export_format"
    fun format(ctx: Context): ExportFormat {
        val name = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_FORMAT, ExportFormat.WORD.name)
        return runCatching { ExportFormat.valueOf(name!!) }.getOrDefault(ExportFormat.WORD)
    }
    fun setFormat(ctx: Context, format: ExportFormat) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FORMAT, format.name)
            .apply()
    }
}
