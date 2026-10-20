package com.swan.notes

import android.app.Application
import android.content.Context
import com.swan.notes.data.SettingsStore
import com.swan.notes.data.WorkspaceRepository
import com.swan.notes.data.sync.SafSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SwanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settingsStore = SettingsStore(context.filesDir, appScope)
    val repository = WorkspaceRepository(context.filesDir, appScope)
    val safSync = SafSyncRepository(context, context.filesDir, settingsStore)

    init {
        safSync.startAutoMirror(appScope, repository.workspace)
    }
}
