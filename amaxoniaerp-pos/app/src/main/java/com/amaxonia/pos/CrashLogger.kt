package com.amaxonia.pos

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    fun setup(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashLog(context, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashLog(context: Context, throwable: Throwable) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val filename = "crash_log_$timestamp.txt"
            val dir = context.getExternalFilesDir(null)
            
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            
            val file = File(dir, filename)
            val fileWriter = FileWriter(file, true)
            val printWriter = PrintWriter(fileWriter)
            
            printWriter.println("--- CRASH REPORT ---")
            printWriter.println("Time: ${Date()}")
            printWriter.println("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            printWriter.println("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            printWriter.println("--- EXCEPTION ---")
            throwable.printStackTrace(printWriter)
            printWriter.println("--------------------")
            
            printWriter.flush()
            printWriter.close()
            fileWriter.close()
            
            Log.e("CrashLogger", "Crash log saved to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CrashLogger", "Failed to save crash log", e)
        }
    }
}
