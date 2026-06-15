package com.android.CaveArt

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build

class CaveArtSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val baseContext = context ?: return null
        
        val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            baseContext.createDeviceProtectedStorageContext()
        } else {
            baseContext
        }
        
        val prefs = safeContext.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)
        
        val cursor = MatrixCursor(arrayOf("value"))
        when (uri.lastPathSegment) {
            "clock_x" -> cursor.addRow(arrayOf(prefs.getFloat("clock_x", 0f)))
            "clock_y" -> cursor.addRow(arrayOf(prefs.getFloat("clock_y", 110f)))
            "date_x" -> cursor.addRow(arrayOf(prefs.getFloat("date_x", 0f)))
            "date_y" -> cursor.addRow(arrayOf(prefs.getFloat("date_y", 75f)))
            "date_size" -> cursor.addRow(arrayOf(prefs.getFloat("date_size", 20f)))
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
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
