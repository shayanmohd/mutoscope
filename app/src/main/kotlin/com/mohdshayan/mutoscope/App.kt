package com.mohdshayan.mutoscope

import android.app.Application
import com.mohdshayan.mutoscope.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Wires the service locator, then tidies up after any export a kill interrupted: pending
 * MediaStore rows and stale files in the export cache.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { ServiceLocator.exports.saver.deleteStalePending() }
        }
    }
}
