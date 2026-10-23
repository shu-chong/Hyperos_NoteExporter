package com.shu.notebackupexporter.core

import android.content.Context


object Prefs {

    private const val FILE = "note_exporter"
    private const val KEY_FORMAT = "export_format"
    private const val KEY_FALLBACK = "export_fallback"

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

    
    fun fallbackFormat(ctx: Context): ExportFormat {
        val name = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_FALLBACK, ExportFormat.WORD.name)
        return runCatching { ExportFormat.valueOf(name!!) }.getOrDefault(ExportFormat.WORD)
            .let { if (it == ExportFormat.MARKDOWN) ExportFormat.WORD else it }
    }

    fun setFallbackFormat(ctx: Context, format: ExportFormat) {
        val safe = if (format == ExportFormat.MARKDOWN) ExportFormat.WORD else format
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FALLBACK, safe.name)
            .apply()
    }
}
