package com.mohdshayan.mutoscope.data

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mohdshayan.mutoscope.core.loop.Aspect
import com.mohdshayan.mutoscope.core.loop.LoopClock
import com.mohdshayan.mutoscope.core.sample.SampleScripts
import com.mohdshayan.mutoscope.data.cels.CelStore
import com.mohdshayan.mutoscope.data.db.AppDatabase
import com.mohdshayan.mutoscope.data.db.CelEntity
import com.mohdshayan.mutoscope.data.db.ProjectEntity
import com.mohdshayan.mutoscope.data.db.ReelEntity
import com.mohdshayan.mutoscope.data.model.CelDoc
import com.mohdshayan.mutoscope.data.model.Ids
import com.mohdshayan.mutoscope.data.model.ProjectDoc
import com.mohdshayan.mutoscope.data.model.ReelDoc
import com.mohdshayan.mutoscope.data.model.ReelKind
import com.mohdshayan.mutoscope.data.prefs.AppPrefs
import com.mohdshayan.mutoscope.ink.Brushes
import com.mohdshayan.mutoscope.ink.CelCompositor
import com.mohdshayan.mutoscope.ink.Scene
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** A project as the Projects grid shows it. */
data class ProjectTile(
    val project: ProjectEntity,
    val cycle: Long,
    val reelCount: Int,
    val lengths: List<Int>,
)

class ProjectRepository(
    private val db: AppDatabase,
    private val store: CelStore,
    private val prefs: AppPrefs,
) {
    private val dao get() = db.projectDao()

    @Volatile
    private var idsSeeded = false

    private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closing = ConcurrentHashMap<Long, Job>()

    /**
     * Saves a loop the editor is leaving, off the editor's own scope so it outlives it. A reopen of
     * the same loop waits for this in [load], so it never reads rows older than the files on disk.
     */
    fun saveOnClose(projectId: Long, reels: List<ReelDoc>) {
        val previous = closing[projectId]
        val job = closeScope.launch {
            previous?.join()
            saveStructure(projectId, reels)
            store.flush()
            loadNow(projectId)?.let { writeThumbnail(it) }
        }
        closing[projectId] = job
        job.invokeOnCompletion { closing.remove(projectId, job) }
    }

    /**
     * Reel and cel ids come from the clock. Once per process they are pushed past every stored id,
     * so a clock set back can never reuse one (a reused id would replace another project's reel).
     */
    suspend fun seedIds() {
        if (idsSeeded) return
        withContext(Dispatchers.IO) { Ids.seed(dao.maxChildId() ?: 0L) }
        idsSeeded = true
    }

    fun observeTiles(): Flow<List<ProjectTile>> =
        combine(dao.observeProjects(), dao.observeReelLengths()) { projects, rows ->
            val byProject = rows.groupBy { it.projectId }
            projects.map { p ->
                val reels = byProject[p.id].orEmpty().filter { it.kind == ReelKind.DRAWN.name }
                val visible = reels.filter { !it.hidden && it.length > 0 }
                ProjectTile(
                    project = p,
                    cycle = LoopClock.cycle(visible.map { LoopClock.period(it.hold, it.length) }),
                    reelCount = reels.size,
                    lengths = reels.map { it.length },
                )
            }
        }

    suspend fun create(name: String, aspect: Aspect, paperArgb: Int, fps: Int): Long = withContext(Dispatchers.IO) {
        seedIds()
        val id = dao.insertProject(
            ProjectEntity(
                name = name.trim().ifEmpty { "Loop" },
                aspect = aspect.name,
                widthPx = aspect.width,
                heightPx = aspect.height,
                fps = fps,
                paperArgb = paperArgb,
            ),
        )
        val reel = ReelDoc(id = Ids.next(), name = "Reel 1", cels = listOf(CelDoc(Ids.next(), null)))
        saveStructure(id, listOf(reel))
        id
    }

    /** Draws the sample loop from its stroke scripts, once per install. */
    suspend fun ensureSample() = withContext(Dispatchers.IO) {
        if (prefs.sampleCreated()) return@withContext
        seedIds()
        val aspect = Aspect.SQUARE
        val id = dao.insertProject(
            ProjectEntity(
                name = SampleScripts.NAME,
                aspect = aspect.name,
                widthPx = aspect.width,
                heightPx = aspect.height,
                fps = SampleScripts.FPS,
                paperArgb = 0xFFFCFCFA.toInt(),
                isSample = true,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val brushes = Brushes()
        val reels = SampleScripts.lampAndBall().map { script ->
            val cels = script.frames.map { strokes ->
                val bmp = Bitmap.createBitmap(aspect.width, aspect.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                strokes.forEach { brushes.drawScript(canvas, it) }
                val celId = Ids.next()
                val name = store.newName(celId)
                store.write(id, name, bmp)
                CelDoc(celId, name)
            }
            ReelDoc(id = Ids.next(), name = script.name, hold = script.hold, cels = cels)
        }
        store.flush()
        saveStructure(id, reels, touch = false)
        load(id)?.let { writeThumbnail(it) }
        prefs.setSampleCreated()
    }

    suspend fun load(id: Long): ProjectDoc? {
        closing[id]?.join()
        return loadNow(id)
    }

    private suspend fun loadNow(id: Long): ProjectDoc? = withContext(Dispatchers.IO) {
        seedIds()
        val project = dao.project(id) ?: return@withContext null
        val cels = dao.cels(id).groupBy { it.reelId }
        val reels = dao.reels(id).map { r ->
            val list = cels[r.id].orEmpty().sortedBy { it.position }.map { CelDoc(it.id, it.filePath) }
            ReelDoc(
                id = r.id,
                name = r.name,
                hold = r.hold.coerceIn(1, 4),
                phaseOffset = r.phaseOffset,
                opacity = r.opacity,
                hidden = r.hidden,
                locked = r.locked,
                kind = runCatching { ReelKind.valueOf(r.kind) }.getOrDefault(ReelKind.DRAWN),
                includeInExport = r.includeInExport,
                cels = list.ifEmpty { listOf(CelDoc(Ids.next(), null)) },
            )
        }
        // A kill between creating the row and writing its reels leaves a loop with nothing to
        // draw on; give it a blank reel so it opens instead of crashing the editor.
        val drawable = if (reels.any { it.kind == ReelKind.DRAWN }) {
            reels
        } else {
            reels + ReelDoc(id = Ids.next(), name = "Reel 1", cels = listOf(CelDoc(Ids.next(), null)))
        }
        ProjectDoc(project, drawable)
    }

    suspend fun saveStructure(projectId: Long, reels: List<ReelDoc>, touch: Boolean = true) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val reelRows = reels.mapIndexed { z, r ->
            ReelEntity(
                id = r.id,
                projectId = projectId,
                name = r.name,
                zIndex = z,
                hold = r.hold,
                phaseOffset = r.phaseOffset,
                opacity = r.opacity,
                hidden = r.hidden,
                locked = r.locked,
                kind = r.kind.name,
                includeInExport = r.includeInExport,
            )
        }
        val celRows = reels.flatMap { r ->
            r.cels.mapIndexed { i, c -> CelEntity(c.id, r.id, i, c.file, now) }
        }
        val project = dao.project(projectId) ?: return@withContext
        dao.replaceStructure(projectId, reelRows, celRows, if (touch) now else project.updatedAt)
    }

    /** Reads the stored row and applies [change], so a stale copy never overwrites newer fields. */
    suspend fun updateProject(id: Long, change: (ProjectEntity) -> ProjectEntity): ProjectEntity? = withContext(Dispatchers.IO) {
        val current = dao.project(id) ?: return@withContext null
        val next = change(current).copy(id = id, updatedAt = System.currentTimeMillis())
        dao.updateProject(next)
        next
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val p = dao.project(id) ?: return@withContext
        dao.updateProject(p.copy(name = name.trim().ifEmpty { p.name }))
    }

    suspend fun duplicate(id: Long): Long? = withContext(Dispatchers.IO) {
        store.flush()
        val doc = load(id) ?: return@withContext null
        val newId = dao.insertProject(
            doc.project.copy(
                id = 0,
                name = "${doc.project.name} copy",
                isSample = false,
                thumbnailPath = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val from = store.celsDir(id)
        val to = store.celsDir(newId)
        val reels = doc.reels.map { r ->
            r.copy(
                id = Ids.next(),
                cels = r.cels.map { c ->
                    c.file?.let { name -> File(from, name).takeIf { it.exists() }?.copyTo(File(to, name), overwrite = true) }
                    CelDoc(Ids.next(), c.file)
                },
            )
        }
        saveStructure(newId, reels)
        load(newId)?.let { writeThumbnail(it) }
        newId
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteProject(id)
        store.forgetProject(id)
        store.projectDir(id).deleteRecursively()
    }

    /** Renders the first frame small and points the project at it. */
    suspend fun writeThumbnail(doc: ProjectDoc) = withContext(Dispatchers.IO) {
        val p = doc.project
        val long = 360
        val scale = long.toFloat() / maxOf(p.widthPx, p.heightPx)
        val bmp = Bitmap.createBitmap((p.widthPx * scale).toInt().coerceAtLeast(1), (p.heightPx * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        runCatching {
            CelCompositor().draw(canvas, Scene(doc, tick = 0L, forExport = false)) { _, cel, _ ->
                cel.file?.let { store.decode(p.id, it, 2) }
            }
        }
        val dir = store.projectDir(p.id).also { it.mkdirs() }
        val file = File(dir, "thumb_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val current = dao.project(p.id) ?: return@withContext
        current.thumbnailPath?.let { old -> if (old != file.absolutePath) File(old).delete() }
        dao.updateProject(current.copy(thumbnailPath = file.absolutePath))
    }

    suspend fun allProjects(): List<ProjectEntity> = withContext(Dispatchers.IO) { dao.allProjects() }

    /** Deletes cel files nothing points at: not the saved project, not any undo step. */
    suspend fun collectGarbage(projectId: Long, keep: Set<String>) = withContext(Dispatchers.IO) {
        store.flush()
        val saved = dao.cels(projectId).mapNotNull { it.filePath }.toSet()
        store.collectGarbage(projectId, saved + keep)
    }
}
