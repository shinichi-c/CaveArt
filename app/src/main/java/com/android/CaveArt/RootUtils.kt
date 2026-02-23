package com.android.CaveArt

import android.content.ComponentName
import android.content.Context
import java.io.DataOutputStream
import java.io.File

object RootUtils {
	
    const val INJECTION_LOG_PATH = "/sdcard/Download/CaveArt_InjectionLog.txt"
    
    fun bruteForceSetWallpaper(context: Context, serviceClass: Class<*>): String {
        val sb = StringBuilder()
        val componentName = ComponentName(context, serviceClass)
        val pkg = componentName.packageName
        val cls = componentName.className

        sb.append("=== START ROOT INJECTION ===\n")
        sb.append("Target: $pkg/$cls\n")
        sb.append("Strategy: Fast Batch Binder Transaction (IDs 1-100)\n")
        
        val argString = "i32 1 s16 \"$pkg\" s16 \"$cls\""

        try {
            
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            for (i in 1..100) {
                os.writeBytes("service call wallpaper $i $argString\n")
            }
            
            os.writeBytes("exit\n")
            os.flush()
            
            process.waitFor()
            
        } catch (e: Exception) {
            sb.append("Execution error: ${e.message}\n")
        }

        sb.append("Injection loop complete. Verifying...\n")
        
        try { Thread.sleep(500) } catch (e: Exception) {}
        
        val dump = runSimpleCommand("dumpsys wallpaper | grep mWallpaperComponent")
        sb.append("Current Wallpaper: $dump\n")

        if (dump.contains(pkg)) {
            sb.append("SUCCESS: Wallpaper component hooked and applied.\n")
        } else {
            sb.append("FAILED: System did not accept any transaction.\n")
        }

        return sb.toString()
    }

    private fun runSimpleCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            os.writeBytes("$command\n")
            os.writeBytes("exit\n")
            os.flush()
            
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val output = runSimpleCommand("id")
            output.contains("uid=0")
        } catch (e: Exception) { false }
    }
    
    fun saveLogToSdCard(content: String) {
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            
            val safeContent = content.replace("'", "'\\''")
            
            os.writeBytes("echo '$safeContent' > $INJECTION_LOG_PATH\n")
            os.writeBytes("chmod 666 $INJECTION_LOG_PATH\n")
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor()
        } catch (e: Exception) { e.printStackTrace() }
    }
}