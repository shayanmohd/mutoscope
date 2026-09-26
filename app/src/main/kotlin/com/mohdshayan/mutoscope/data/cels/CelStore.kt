package com.mohdshayan.mutoscope.data.cels

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Cels on disk. Every cel image is an immutable PNG named once and never rewritten: an edit
 * writes a new file and the cel points at it. That makes undo a matter of pointing back, lets
 * duplicated frames share a file, and means a half-written file is never one a cel points at.
 *
 * Writes go through one background queue. Until a write lands the bitmap stays in [pending], so
 * readers never see a gap. Decoded bitmaps live in an LruCache sized to a quarter of the app's
 * memory class; playback and ghosts ask for half-size proxies, the still frame for full size.
 */
class CelStore(private val context: Context) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeQueue = Dispatchers.IO.limitedParallelism(1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val decodePool = Dispatchers.IO.limitedParallelism(2)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pending = ConcurrentHashMap<String, Bitmap>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()

    private val cache: LruCache<String, Bitmap>

    private val _loads = MutableStateFlow(0L)

    /** Ticks every time a requested bitmap arrives, so a canvas knows to redraw. */
    val loads: StateFlow<Long> = _loads

    init {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val budgetBytes = am.memoryClass.toLong() * 1024 * 1024 / 4
        cache = object : LruCache<String, Bitmap>(budgetBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }
    }

    fun projectDir(projectId: Long): File = File(context.filesDir, "projects/$projectId")
    fun celsDir(projectId: Long): File = File(projectDir(projectId), "cels").also { it.mkdirs() }
    fun file(projectId: Long, name: String): File = File(celsDir(projectId), name)

    fun newName(celId: Long): String = "c${celId}_${System.nanoTime()}.png"

    private fun key(projectId: Long, name: String, half: Boolean) = "$projectId/$name@${if (half) 2 else 1}"
    private fun pendingKey(projectId: Long, name: String) = "$projectId/$name"

    /**
     * Queues [bitmap] (which the store now owns and never mutates) to be written as [name].
     * Returns at once.
     */
    fun write(projectId: Long, name: String, bitmap: Bitmap) {
        val pk = pendingKey(projectId, name)
        pending[pk] = bitmap
        cache.put(key(projectId, name, false), bitmap)
        scope.launch(writeQueue) {
            try {
                val target = file(projectId, name)
                val tmp = File(target.parentFile, "$name.tmp")
                FileOutputStream(tmp).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!tmp.renameTo(target)) tmp.delete()
            } finally {
                pending.remove(pk)
            }
        }
    }

    /** Suspends until every queued write has landed. */
    suspend fun flush() {
        withContext(writeQueue) { }
    }

    /** Non-blocking: the bitmap if it is at hand, otherwise null and a background decode. */
    fun peek(projectId: Long, name: String, half: Boolean): Bitmap? {
        val k = key(projectId, name, half)
        cache.get(k)?.let { return it }
        if (half) {
            // A full-size copy in memory beats waiting for a proxy decode.
            cache.get(key(projectId, name, false))?.let { return it }
        }
        pending[pendingKey(projectId, name)]?.let { return it }
        if (k in failed || !inFlight.add(k)) return null
        scope.launch(decodePool) {
            try {
                val bmp = decode(projectId, name, if (half) 2 else 1)
                if (bmp != null) cache.put(k, bmp) else failed.add(k)
            } catch (_: Throwable) {
                failed.add(k)
            } finally {
                inFlight.remove(k)
                _loads.value = _loads.value + 1
            }
        }
        return null
    }

    fun hasFailed(projectId: Long, name: String): Boolean =
        key(projectId, name, true) in failed || key(projectId, name, false) in failed

    /** Blocking full-size read for the working cel, as a mutable copy the editor may draw into. */
    fun readMutable(projectId: Long, name: String): Bitmap? {
        pending[pendingKey(projectId, name)]?.let { return it.copy(Bitmap.Config.ARGB_8888, true) }
        cache.get(key(projectId, name, false))?.let { return it.copy(Bitmap.Config.ARGB_8888, true) }
        val opts = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val f = file(projectId, name)
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    /** Blocking read at a sample size, for export and thumbnails. Null when missing or unreadable. */
    fun decode(projectId: Long, name: String, sample: Int): Bitmap? {
        pending[pendingKey(projectId, name)]?.let { return it }
        val f = file(projectId, name)
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }

    /** Deletes files in a project's cels folder that no snapshot points at. */
    fun collectGarbage(projectId: Long, keep: Set<String>) {
        celsDir(projectId).listFiles()?.forEach { f ->
            if (f.name !in keep && pending[pendingKey(projectId, f.name)] == null) f.delete()
        }
    }

    fun forgetProject(projectId: Long) {
        cache.snapshot().keys.filter { it.startsWith("$projectId/") }.forEach { cache.remove(it) }
    }
}
