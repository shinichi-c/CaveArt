package com.android.CaveArt

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.DataOutputStream
import java.io.File

object RootUtils {

    const val INJECTION_LOG_PATH = "/sdcard/Download/CaveArt_InjectionLog.txt"

    
    fun copyActiveLockscreenWallpaper(context: Context): File? {
        if (!isRootAvailable()) return null
        val destFile = File(context.filesDir, "active_system_lockscreen.png")
        
        val lockWallpaperPath = "/data/system/users/0/wallpaper_lock"
        val fallbackHomePath = "/data/system/users/0/wallpaper"

        val cmd = """
            if [ -f "$lockWallpaperPath" ]; then
                cp -f "$lockWallpaperPath" "${destFile.absolutePath}"
            elif [ -f "$fallbackHomePath" ]; then
                cp -f "$fallbackHomePath" "${destFile.absolutePath}"
            fi
            chmod 644 "${destFile.absolutePath}"
        """.trimIndent()

        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$cmd\nexit\n")
            os.flush()
            process.waitFor()

            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (_: Exception) {
            null
        }
    }

    
    fun detectUdfpsLocation(context: Context): Triple<Float, Float, Float>? {
        
        try {
            val resId = context.resources.getIdentifier("config_udfps_sensor_props", "array", "android")
            if (resId > 0) {
                val arr = context.resources.getIntArray(resId)
                if (arr.size >= 3 && arr[2] > 0) {
                    return Triple(arr[0].toFloat(), arr[1].toFloat(), arr[2].toFloat())
                }
            }
        } catch (_: Exception) {}

        if (!isRootAvailable()) return null
        
        try {
            val prop = runSimpleCommand("getprop persist.sys.udfps.props").trim()
            val parts = prop.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size >= 3 && parts[2] > 0f) {
                return Triple(parts[0], parts[1], parts[2])
            }
        } catch (_: Exception) {}
        
        try {
            val dump = runSimpleCommand("dumpsys fingerprint | grep -iE 'location|radius|sensorlocation'")
            val regex = Regex("""x\s*=\s*(\d+).*?y\s*=\s*(\d+).*?radius\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
            val match = regex.find(dump)
            if (match != null) {
                val (x, y, r) = match.destructured
                return Triple(x.toFloat(), y.toFloat(), r.toFloat())
            }
        } catch (_: Exception) {}

        return null
    }

    fun isRootAvailable(): Boolean {
        return try {
            val output = runSimpleCommand("id")
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun runSimpleCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\nexit\n")
            os.flush()
            process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun bruteForceSetWallpaper(context: Context, serviceClass: Class<*>): String {
        val sb = StringBuilder()
        val componentName = ComponentName(context, serviceClass)
        val pkg = componentName.packageName
        val cls = componentName.className

        sb.append("=== START ROOT INJECTION ===\n")
        sb.append("Target: $pkg/$cls\n")
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

        try { Thread.sleep(500) } catch (_: Exception) {}
        val dump = runSimpleCommand("dumpsys wallpaper | grep mWallpaperComponent")
        sb.append("Current Wallpaper: $dump\n")
        if (dump.contains(pkg)) {
            sb.append("SUCCESS: Wallpaper component hooked and applied.\n")
        } else {
            sb.append("FAILED: System did not accept transaction.\n")
        }
        return sb.toString()
    }
}
