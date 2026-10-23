package com.shu.notebackupexporter.core

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object BackupLocator {

    data class BackupFile(
        val file: File,
        val isZip: Boolean,
        
        val displayName: String? = null,
    ) {
        val name: String get() = displayName ?: file.name
        val size: Long get() = file.length()
        val modified: Long get() = file.lastModified()
        val displayTime: String
            get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(modified))
        val displaySize: String
            get() = when {
                size >= 1L shl 30 -> String.format(Locale.CHINA, "%.2f GB", size / 1073741824.0)
                size >= 1L shl 20 -> String.format(Locale.CHINA, "%.1f MB", size / 1048576.0)
                else -> String.format(Locale.CHINA, "%.0f KB", size / 1024.0)
            }
    }

    val backupDir: File
        get() = File(Environment.getExternalStorageDirectory(), "MIUI/backup/AllBackup")

    val downloadDir: File
        get() = File(Environment.getExternalStorageDirectory(), "Download")

    
    fun list(): List<BackupFile> {
        val dir = backupDir
        if (!dir.isDirectory) return emptyList()
        val out = ArrayList<BackupFile>()
        dir.listFiles()?.forEach { f ->
            when {
                
                f.isFile && f.name.endsWith(".zip", ignoreCase = true) &&
                        !f.name.startsWith(".") -> out += BackupFile(f, true)
                
                f.isDirectory && (f.listFiles()?.any { it.name.endsWith(".bak") } == true) ->
                    out += BackupFile(f, false)
            }
        }
        return out.sortedByDescending { it.modified }
    }

    fun newest(): BackupFile? = list().firstOrNull()
}
