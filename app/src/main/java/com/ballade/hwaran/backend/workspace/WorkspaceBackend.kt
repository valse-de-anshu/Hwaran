package com.ballade.hwaran.backend.workspace

import android.app.Application
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.datastore.GlobalSettings
import com.ballade.hwaran.core.util.HistoryTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class WorkspaceBackend(private val application: Application) {
    private val database = AppDatabase.getDatabase(application)
    private val globalSettings = GlobalSettings(application)
    private val scope = CoroutineScope(Dispatchers.IO)

    val workspacesToonFlow: Flow<List<String>> = globalSettings.workspacesToonFlow
    val workspacesBookFlow: Flow<List<String>> = globalSettings.workspacesBookFlow
    val workspacesVideoFlow: Flow<List<String>> = globalSettings.workspacesVideoFlow

    val activeScreenToonFlow: Flow<String?> = globalSettings.activeScreenToonFlow
    val activeScreenBookFlow: Flow<String?> = globalSettings.activeScreenBookFlow
    val activeScreenVideoFlow: Flow<String?> = globalSettings.activeScreenVideoFlow

    fun getAllDistinctWorkspaces(): Flow<List<String>> {
        return database.mediaDao().getAllDistinctWorkspaces()
    }

    fun setActiveScreen(mediaMode: Int, screenName: String?) {
        scope.launch {
            globalSettings.setActiveScreen(mediaMode, screenName)
        }
    }

    fun createWorkspace(mediaMode: Int, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank() && cleanName != "I Love It") {
            scope.launch {
                globalSettings.addWorkspace(mediaMode, cleanName)
                globalSettings.setActiveScreen(mediaMode, cleanName)
            }
        }
    }

    fun renameWorkspace(mediaMode: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String) {
        val cleanOld = oldWorkspace.trim()
        val cleanNew = newWorkspace.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == "I Love It" || cleanNew == "I Love It") return

        scope.launch {
            database.mediaDao().updateEntireWorkspace(mediaMode, videoLayoutMode, cleanOld, cleanNew)
            globalSettings.addWorkspace(mediaMode, cleanNew)
            globalSettings.removeWorkspace(mediaMode, cleanOld)
            globalSettings.renameWorkspaceIfActive(mediaMode, cleanOld, cleanNew)
            HistoryTracker.logEvent("IMPORT", "Renamed Workspace", "$cleanOld -> $cleanNew")
        }
    }

    fun deleteWorkspace(mediaMode: Int, videoLayoutMode: Int, workspaceName: String) {
        if (workspaceName == "I Love It") return
        scope.launch {
            globalSettings.removeWorkspace(mediaMode, workspaceName)
            val items = database.mediaDao().getMangaListForMove(mediaMode, videoLayoutMode, workspaceName)
            items.forEach { m ->
                HistoryTracker.logEvent("IMPORT", "Merged to I Love It", m.title)
                database.mediaDao().updateWorkspaceForMangaTree(m.id, "I Love It")
            }
        }
    }

    fun moveEntireWorkspace(mediaMode: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String) {
        scope.launch {
            database.mediaDao().updateEntireWorkspace(mediaMode, videoLayoutMode, oldWorkspace, newWorkspace)
            HistoryTracker.logEvent("IMPORT", "Moved Workspace", "$oldWorkspace -> $newWorkspace")
            globalSettings.renameWorkspaceIfActive(mediaMode, oldWorkspace, newWorkspace)
        }
    }

    fun moveItemsToWorkspace(rootIds: List<Long>, oldWorkspace: String, newWorkspace: String) {
        scope.launch {
            for (rootId in rootIds) {
                database.mediaDao().updateWorkspaceForMangaTree(rootId, newWorkspace)
            }
            HistoryTracker.logEvent("IMPORT", "Moved ${rootIds.size} items", "$oldWorkspace -> $newWorkspace")
        }
    }

    fun isProtected(workspaceName: String): Boolean = workspaceName == "I Love It"
}
