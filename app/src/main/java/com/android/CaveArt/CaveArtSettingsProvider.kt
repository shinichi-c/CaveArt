package com.android.CaveArt

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class CaveArtSettingsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val baseContext = context ?: return null
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseContext.createDeviceProtectedStorageContext()
        } else baseContext
        val prefs = safeContext.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)

        if (method == "updateUdfps" && extras != null) {
            val x = extras.getFloat("udfps_x", -1f)
            val y = extras.getFloat("udfps_y", -1f)
            val r = extras.getFloat("udfps_radius", 0f)
            if (r > 0f) {
                prefs.edit()
                    .putFloat("udfps_x", x)
                    .putFloat("udfps_y", y)
                    .putFloat("udfps_radius", r)
                    .apply()
            }
            return Bundle().apply { putBoolean("success", true) }
        }

        if (method == "updateDepth" && extras != null) {
            val enabled = extras.getBoolean("clock_depth_enabled", true)
            val order = extras.getInt("clock_layer_order", 1)
            val occlusion = extras.getFloat("clock_depth_occlusion", 0.45f)
            val structure = extras.getFloat("layer_structure", 0f)
            val subOpacity = extras.getFloat("layer_subject_opacity", 1.0f)
            val bgOpacity = extras.getFloat("layer_bg_opacity", 1.0f)

            prefs.edit()
                .putBoolean("clock_depth_enabled", enabled)
                .putInt("clock_layer_order", order)
                .putFloat("clock_depth_occlusion", occlusion)
                .putFloat("layer_structure", structure)
                .putFloat("layer_subject_opacity", subOpacity)
                .putFloat("layer_bg_opacity", bgOpacity)
                .apply()
            return Bundle().apply { putBoolean("success", true) }
        }

        return null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val baseContext = context ?: throw FileNotFoundException("Context is null")
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseContext.createDeviceProtectedStorageContext()
        } else baseContext

        val segment = uri.lastPathSegment
        
        if (segment == "depth_mask" || segment == "boot_cutout.png" || segment == "lock_depth_mask.png") {
            val candidateFiles = listOf(
                File(safeContext.filesDir, "lock_depth_mask.png"),
                File(safeContext.noBackupFilesDir, "boot_cutout.png"),
                File(safeContext.filesDir, "boot_cutout.png")
            )
            for (file in candidateFiles) {
                if (file.exists() && file.length() > 0) {
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
        }
        
        if (segment == "raw_depth" || segment == "lock_raw_depth.png") {
            val candidateFiles = listOf(
                File(safeContext.filesDir, "lock_raw_depth.png"),
                File(safeContext.noBackupFilesDir, "lock_raw_depth.png")
            )
            for (file in candidateFiles) {
                if (file.exists() && file.length() > 0) {
                    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                }
            }
        }

        throw FileNotFoundException("File not available for URI: $uri")
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val baseContext = context ?: return null
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseContext.createDeviceProtectedStorageContext()
        } else baseContext

        val prefs = safeContext.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)
        val cursor = MatrixCursor(arrayOf("value"))

        when (uri.lastPathSegment) {
            "clock_x" -> cursor.addRow(arrayOf(prefs.getFloat("clock_x", 0f)))
            "clock_y" -> cursor.addRow(arrayOf(prefs.getFloat("clock_y", 110f)))
            "date_x" -> cursor.addRow(arrayOf(prefs.getFloat("date_x", 0f)))
            "date_y" -> cursor.addRow(arrayOf(prefs.getFloat("date_y", 75f)))
            "date_size" -> cursor.addRow(arrayOf(prefs.getFloat("date_size", 20f)))
            "date_format" -> cursor.addRow(arrayOf(prefs.getInt("date_format", 0)))
            "date_attached" -> cursor.addRow(arrayOf(if (prefs.getBoolean("date_attached", true)) 1 else 0))
            "scroll_style" -> cursor.addRow(arrayOf(prefs.getInt("scroll_style", 0)))
            "clock_hour_size" -> cursor.addRow(arrayOf(prefs.getFloat("clock_hour_size", 100f)))
            "clock_minute_size" -> cursor.addRow(arrayOf(prefs.getFloat("clock_minute_size", 75f)))
            "clock_stroke_width" -> cursor.addRow(arrayOf(prefs.getFloat("clock_stroke_width", 8f)))
            "clock_roundness" -> cursor.addRow(arrayOf(prefs.getFloat("clock_roundness", 30f)))
            "clock_stretch" -> cursor.addRow(arrayOf(if (prefs.getBoolean("clock_stretch", false)) 1 else 0))
            "clock_collision_map" -> cursor.addRow(arrayOf(prefs.getString("clock_collision_map", "")))
            "clock_color" -> cursor.addRow(arrayOf(prefs.getInt("clock_color", android.graphics.Color.WHITE)))
            "clock_font" -> cursor.addRow(arrayOf(prefs.getString("clock_font", "default")))
            "clock_dual_tone" -> cursor.addRow(arrayOf(if (prefs.getBoolean("clock_dual_tone", true)) 1 else 0))
            "clock_layout" -> cursor.addRow(arrayOf(prefs.getInt("clock_layout", 0)))
            "udfps_x" -> cursor.addRow(arrayOf(prefs.getFloat("udfps_x", -1f)))
            "udfps_y" -> cursor.addRow(arrayOf(prefs.getFloat("udfps_y", -1f)))
            "udfps_radius" -> cursor.addRow(arrayOf(prefs.getFloat("udfps_radius", 0f)))

            "clock_depth_enabled" -> cursor.addRow(arrayOf(if (prefs.getBoolean("clock_depth_enabled", true)) 1 else 0))
            "clock_layer_order" -> cursor.addRow(arrayOf(prefs.getInt("clock_layer_order", 1)))
            "clock_depth_occlusion" -> cursor.addRow(arrayOf(prefs.getFloat("clock_depth_occlusion", 0.45f)))
            "depth_mask_path" -> cursor.addRow(arrayOf(prefs.getString("depth_mask_path", "")))
            "layer_structure" -> cursor.addRow(arrayOf(prefs.getFloat("layer_structure", 0f)))
            "layer_subject_opacity" -> cursor.addRow(arrayOf(prefs.getFloat("layer_subject_opacity", 1.0f)))
            "layer_bg_opacity" -> cursor.addRow(arrayOf(prefs.getFloat("layer_bg_opacity", 1.0f)))
            "telemetry_zoom" -> cursor.addRow(arrayOf(prefs.getFloat("telemetry_zoom", 1.10f)))
            
            "clock_gravity" -> cursor.addRow(arrayOf(if (prefs.getBoolean("clock_gravity", false)) 1 else 0))
            "clock_gravity_intensity" -> cursor.addRow(arrayOf(prefs.getFloat("clock_gravity_intensity", 1.0f)))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
