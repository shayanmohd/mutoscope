package com.mohdshayan.mutoscope.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finished loops into shared storage. Android 10 and up insert into MediaStore under
 * Pictures/Mutoscope or Movies/Mutoscope with no permission; the row stays pending until the
 * bytes are in, and pending rows left by a crash are removed on the next launch (Android 10 too). Android 8 and 9
 * save through the system file picker instead.
 */
class MediaSaver(private val context: Context) {

    val canSaveToGallery: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun saveToGallery(file: File, isGif: Boolean, displayName: String): String = withContext(Dispatchers.IO) {
        val folder = if (isGif) "Pictures/Mutoscope" else "Movies/Mutoscope"
        val collection = collection(isGif)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isGif) "image/gif" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: throw IllegalStateException("insert refused")
        try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        folder
    }

    suspend fun copyTo(file: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw IllegalStateException("no stream")
    }

    /** Deletes this app's pending rows that a crash or kill left behind. */
    fun deleteStalePending() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            deleteStalePendingOnQ()
            return
        }
        for (isGif in listOf(true, false)) {
            runCatching {
                val args = Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?")
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(context.packageName))
                }
                val collection = collection(isGif)
                context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), args, null)?.use { c ->
                    while (c.moveToNext()) {
                        context.contentResolver.delete(Uri.withAppendedPath(collection, c.getLong(0).toString()), null, null)
                    }
                }
            }
        }
    }

    /** Android 10 has no QUERY_ARG_MATCH_PENDING; pending rows are reached through setIncludePending. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun deleteStalePendingOnQ() {
        for (isGif in listOf(true, false)) {
            runCatching {
                val collection = collection(isGif)
                @Suppress("DEPRECATION")
                val withPending = MediaStore.setIncludePending(collection)
                val selection = "${MediaStore.MediaColumns.IS_PENDING} = 1 AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?"
                context.contentResolver.query(withPending, arrayOf(MediaStore.MediaColumns._ID), selection, arrayOf(context.packageName), null)?.use { c ->
                    while (c.moveToNext()) {
                        @Suppress("DEPRECATION")
                        val row = MediaStore.setIncludePending(Uri.withAppendedPath(collection, c.getLong(0).toString()))
                        context.contentResolver.delete(row, null, null)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection(isGif: Boolean): Uri =
        if (isGif) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
}
