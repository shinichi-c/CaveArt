package com.android.CaveArt

import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object FileUtil {
    fun loadMappedFile(context: Context, filePath: String): MappedByteBuffer {
        context.assets.openFd(filePath).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                return inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }
}