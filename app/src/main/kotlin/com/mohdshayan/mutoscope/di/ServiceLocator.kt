package com.mohdshayan.mutoscope.di

import android.content.Context
import com.mohdshayan.mutoscope.data.ArchiveRepository
import com.mohdshayan.mutoscope.data.ProjectRepository
import com.mohdshayan.mutoscope.data.cels.CelStore
import com.mohdshayan.mutoscope.data.db.AppDatabase
import com.mohdshayan.mutoscope.data.prefs.AppPrefs
import com.mohdshayan.mutoscope.export.ExportManager

/**
 * Manual dependency container. No Hilt: a single-module paid app does not need the method count
 * or the build time. Initialised in App.onCreate, and idempotent.
 */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context =
        appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }
    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val celStore: CelStore by lazy { CelStore(ctx()) }
    val projects: ProjectRepository by lazy { ProjectRepository(database, celStore, appPrefs) }
    val archive: ArchiveRepository by lazy { ArchiveRepository(ctx(), database, celStore, projects) }
    val exports: ExportManager by lazy { ExportManager(ctx(), projects, celStore, appPrefs) }
}
