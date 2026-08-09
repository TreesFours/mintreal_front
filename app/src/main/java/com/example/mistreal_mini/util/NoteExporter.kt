package com.example.mistreal_mini.util

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object NoteExporter {
    fun saveAsTxt(context: Context, content: String): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "Mistreal_Note_$timestamp.txt"
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, filename)
            FileOutputStream(file).use { it.write(content.toByteArray()) }
            file
        } catch (e: Exception) {
            null
        }
    }
}
