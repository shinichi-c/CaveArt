package com.android.CaveArt

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

class CaveArtSettingsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        val prefs = context.getSharedPreferences("cave_art_clock_prefs", Context.MODE_PRIVATE)
        
        val cursor = MatrixCursor(arrayOf("value"))
        when (uri.lastPathSegment) {
            "clock_size" -> cursor.addRow(arrayOf(prefs.getFloat("clock_size", 95f)))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
