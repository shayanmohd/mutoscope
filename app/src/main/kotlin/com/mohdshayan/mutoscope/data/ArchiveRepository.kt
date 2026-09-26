package com.mohdshayan.mutoscope.data

import android.content.Context
import android.graphics.BitmapFactory
import com.mohdshayan.mutoscope.core.archive.ManifestException
import com.mohdshayan.mutoscope.core.archive.ProjectManifest
import com.mohdshayan.mutoscope.core.archive.ReelManifest
import com.mohdshayan.mutoscope.core.loop.Aspect
import com.mohdshayan.mutoscope.data.cels.CelStore
import com.mohdshayan.mutoscope.data.db.AppDatabase
import com.mohdshayan.mutoscope.data.db.ProjectEntity
import com.mohdshayan.mutoscope.data.model.CelDoc
import com.mohdshayan.mutoscope.data.model.Ids
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.data.model.ReelKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ImportException(message: String) : Exception(message)

/**
 * Projects as files. A project travels as <name>.mutoscope: a zip of project.json plus one PNG
 * per drawn frame. A backup is a zip holding one .mutoscope per project. Import and restore add
 * projects beside the existing ones and never overwrite.
 */
class ArchiveRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val store: CelStore,
    private val projects: ProjectRepository,
) {
    private val dao get() = db.projectDao()

    fun fileNameFor(project: ProjectEntity): String = "${safeName(project.name)}.mutoscope"

    private fun safeName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "loop" }.take(60)

    suspend fun exportProject(id: Long, out: OutputStream) = withContext(Dispatchers.IO) {
        store.flush()
        val doc = projects.load(id) ?: throw ImportException("That project is gone")
        ZipOutputStream(out).use { zip -> writeProject(doc, zip) }
    }

    private fun writeProject(doc: ProjectDoc, zip: ZipOutputStream) {
        val p = doc.project
        val reels = doc.reels.mapIndexed { r, reel ->
            ReelManifest(
                name = reel.name,
                hold = reel.hold,
                phaseOffset = reel.phaseOffset,
                opacity = reel.opacity,
                hidden = reel.hidden,
                locked = reel.locked,
                kind = reel.kind.name,
                includeInExport = reel.includeInExport,
                cels = reel.cels.mapIndexed { f, cel ->
                    if (cel.file != null && store.file(p.id, cel.file).exists()) ProjectManifest.celEntry(r, f) else null
                },
            )
        }
        val manifest = ProjectManifest(
            name = p.name,
            aspect = p.aspect,
            widthPx = p.widthPx,
            heightPx = p.heightPx,
            fps = p.fps,
            paperArgb = p.paperArgb,
            reels = reels,
        )
        zip.putNextEntry(ZipEntry(ProjectManifest.FILE_NAME))
        zip.write(ProjectManifest.encode(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        doc.reels.forEachIndexed { r, reel ->
            reel.cels.forEachIndexed { f, cel ->
                val entry = reels[r].cels[f] ?: return@forEachIndexed
                zip.putNextEntry(ZipEntry(entry))
                store.file(p.id, cel.file!!).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Writes every project into one backup zip. Returns how many went in. */
    suspend fun backupAll(out: OutputStream): Int = withContext(Dispatchers.IO) {
        store.flush()
        val all = projects.allProjects()
        val used = HashSet<String>()
        ZipOutputStream(out).use { zip ->
            for (p in all) {
                val doc = projects.load(p.id) ?: continue
                var name = fileNameFor(p)
                var n = 2
                while (!used.add(name)) name = "${safeName(p.name)} $n.mutoscope".also { n++ }
                val tmp = File.createTempFile("backup", ".mutoscope", context.cacheDir)
                try {
                    ZipOutputStream(FileOutputStream(tmp)).use { inner -> writeProject(doc, inner) }
                    zip.putNextEntry(ZipEntry(name))
                    tmp.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                } finally {
                    tmp.delete()
                }
            }
        }
        all.size
    }

    /**
     * Imports a .mutoscope file or a whole backup, whichever [input] is. Returns the number of
     * projects added. Throws [ImportException] with a reason when nothing could be read.
     */
    suspend fun importAny(input: InputStream): Int = withContext(Dispatchers.IO) {
        projects.seedIds()
        val tmp = File.createTempFile("import", ".zip", context.cacheDir)
        try {
            FileOutputStream(tmp).use { out -> copyLimited(input, out, MAX_ARCHIVE_BYTES) }
            val zip = try {
                ZipFile(tmp)
            } catch (e: Exception) {
                throw ImportException("That file is not a Mutoscope project or backup")
            }
            zip.use {
                if (it.getEntry(ProjectManifest.FILE_NAME) != null) {
                    importProject(it)
                    return@withContext 1
                }
                var count = 0
                val entries = try {
                    it.entries().toList()
                } catch (e: Exception) {
                    throw ImportException("That file is damaged")
                }
                for (entry in entries) {
                    if (entry.isDirectory || !entry.name.endsWith(".mutoscope")) continue
                    val inner = File.createTempFile("inner", ".zip", context.cacheDir)
                    try {
                        it.getInputStream(entry).use { src -> FileOutputStream(inner).use { dst -> copyLimited(src, dst, MAX_ARCHIVE_BYTES) } }
                        ZipFile(inner).use { z -> importProject(z) }
                        count++
                    } catch (e: Exception) {
                        // One unreadable project in a backup does not stop the rest.
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    } finally {
                        inner.delete()
                    }
                }
                if (count == 0) throw ImportException("That file holds no Mutoscope projects")
                count
            }
        } finally {
            tmp.delete()
        }
    }

    private suspend fun importProject(zip: ZipFile) {
        val manifestEntry = zip.getEntry(ProjectManifest.FILE_NAME) ?: throw ImportException("project.json is missing")
        val m = try {
            ProjectManifest.decode(zip.getInputStream(manifestEntry).use { ProjectManifest.readText(it) })
        } catch (e: ManifestException) {
            throw ImportException("That project could not be opened: ${e.message}")
        }
        val aspect = Aspect.parse(m.aspect)
        val id = dao.insertProject(
            ProjectEntity(
                name = m.name.take(80).ifBlank { "Imported loop" },
                aspect = aspect.name,
                // The canvas size always follows the shape, whatever the file claims, so a
                // damaged or hand-edited file cannot ask for a canvas too big to hold in memory.
                widthPx = aspect.width,
                heightPx = aspect.height,
                fps = m.fps,
                paperArgb = m.paperArgb or 0xFF000000.toInt(),
            ),
        )
        try {
            val dir = store.celsDir(id)
            val reels = m.reels.map { r ->
                val cels = r.cels.map { entryName ->
                    val celId = Ids.next()
                    val file = entryName?.let { name ->
                        val e = zip.getEntry(name) ?: return@let null
                        val target = store.newName(celId)
                        val out = File(dir, target)
                        zip.getInputStream(e).use { src -> FileOutputStream(out).use { dst -> copyLimited(src, dst, MAX_CEL_BYTES) } }
                        if (isPng(out)) target else null.also { out.delete() }
                    }
                    CelDoc(celId, file)
                }
                ReelDoc(
                    id = Ids.next(),
                    name = r.name.take(40).ifBlank { "Reel" },
                    hold = r.hold,
                    phaseOffset = r.phaseOffset,
                    opacity = r.opacity.coerceIn(0f, 1f),
                    hidden = r.hidden,
                    locked = r.locked,
                    kind = runCatching { ReelKind.valueOf(r.kind) }.getOrDefault(ReelKind.DRAWN),
                    includeInExport = r.includeInExport,
                    cels = cels,
                )
            }
            projects.saveStructure(id, reels)
            projects.load(id)?.let { projects.writeThumbnail(it) }
        } catch (e: Exception) {
            projects.delete(id)
            throw if (e is ImportException) e else ImportException("That project could not be opened")
        }
    }

    private fun isPng(file: File): Boolean {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth in 1..MAX_CEL_EDGE && o.outHeight in 1..MAX_CEL_EDGE
    }

    private fun copyLimited(input: InputStream, out: OutputStream, limit: Long) {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw ImportException("That file is larger than a Mutoscope project can be")
            out.write(buf, 0, n)
        }
    }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024
        private const val MAX_CEL_BYTES = 64L * 1024 * 1024

        /** Cels are saved at 1080 px on the long edge; anything far larger is not a Mutoscope cel. */
        private const val MAX_CEL_EDGE = 2160
    }
}
