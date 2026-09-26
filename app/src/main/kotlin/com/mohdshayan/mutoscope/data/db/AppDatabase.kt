package com.mohdshayan.mutoscope.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aspect: String,
    val widthPx: Int,
    val heightPx: Int,
    val fps: Int,
    val paperArgb: Int,
    val thumbnailPath: String? = null,
    val isSample: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "reels",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class ReelEntity(
    @PrimaryKey val id: Long,
    val projectId: Long,
    val name: String,
    val zIndex: Int,
    val hold: Int,
    val phaseOffset: Int,
    val opacity: Float,
    val hidden: Boolean,
    val locked: Boolean,
    val kind: String,
    val includeInExport: Boolean,
)

@Entity(
    tableName = "cels",
    foreignKeys = [
        ForeignKey(
            entity = ReelEntity::class,
            parentColumns = ["id"],
            childColumns = ["reelId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("reelId")],
)
data class CelEntity(
    @PrimaryKey val id: Long,
    val reelId: Long,
    val position: Int,
    val filePath: String?,
    val updatedAt: Long,
)

/** One reel's shape for the Projects list: enough to compute the cycle without loading cels. */
data class ReelLengthRow(
    val projectId: Long,
    val hold: Int,
    val hidden: Boolean,
    val kind: String,
    val length: Int,
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query(
        "SELECT r.projectId AS projectId, r.hold AS hold, r.hidden AS hidden, r.kind AS kind, " +
            "(SELECT COUNT(*) FROM cels c WHERE c.reelId = r.id) AS length FROM reels r",
    )
    fun observeReelLengths(): Flow<List<ReelLengthRow>>

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    suspend fun allProjects(): List<ProjectEntity>

    /** The largest reel or cel id stored, so new client-side ids start above it. */
    @Query("SELECT MAX(m) FROM (SELECT MAX(id) AS m FROM reels UNION ALL SELECT MAX(id) AS m FROM cels)")
    suspend fun maxChildId(): Long?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: Long): ProjectEntity?

    @Insert
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("SELECT * FROM reels WHERE projectId = :projectId ORDER BY zIndex")
    suspend fun reels(projectId: Long): List<ReelEntity>

    @Query(
        "SELECT c.* FROM cels c JOIN reels r ON c.reelId = r.id " +
            "WHERE r.projectId = :projectId ORDER BY c.reelId, c.position",
    )
    suspend fun cels(projectId: Long): List<CelEntity>

    @Query("DELETE FROM cels WHERE reelId IN (SELECT id FROM reels WHERE projectId = :projectId)")
    suspend fun deleteCelsOf(projectId: Long)

    @Query("DELETE FROM reels WHERE projectId = :projectId")
    suspend fun deleteReelsOf(projectId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReels(reels: List<ReelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCels(cels: List<CelEntity>)

    @Query("UPDATE projects SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    /** Replaces a project's reels and cels with a snapshot in one transaction. */
    @Transaction
    suspend fun replaceStructure(projectId: Long, reels: List<ReelEntity>, cels: List<CelEntity>, at: Long) {
        deleteCelsOf(projectId)
        deleteReelsOf(projectId)
        insertReels(reels)
        insertCels(cels)
        touch(projectId, at)
    }
}

/**
 * Version 1 until the app ships; schema export is off because there is nothing to migrate from.
 * Turn it on and write a Migration before shipping version 2.
 */
@Database(
    entities = [ProjectEntity::class, ReelEntity::class, CelEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mutoscope.db",
                ).build().also { instance = it }
            }
    }
}
