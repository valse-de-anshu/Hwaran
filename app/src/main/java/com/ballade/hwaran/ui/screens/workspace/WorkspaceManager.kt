package com.ballade.hwaran.ui.screens.workspace

import com.ballade.hwaran.ui.viewmodels.LibraryViewModel
import com.ballade.hwaran.ui.viewmodels.SettingsViewModel

class WorkspaceManager(
    private val libraryViewModel: LibraryViewModel,
    private val settingsViewModel: SettingsViewModel
) {
    fun createWorkspace(mediaMode: Int, name: String) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank() && cleanName != "I Love It") {
            settingsViewModel.addWorkspace(mediaMode, cleanName)
            settingsViewModel.setActiveScreen(mediaMode, cleanName)
        }
    }

    fun renameWorkspace(mediaMode: Int, videoLayoutMode: Int, oldWorkspace: String, newWorkspace: String) {
        val cleanNew = newWorkspace.trim()
        if (cleanNew.isNotBlank() && oldWorkspace != "I Love It" && cleanNew != "I Love It") {
            libraryViewModel.renameWorkspace(mediaMode, videoLayoutMode, oldWorkspace, cleanNew)
        }
    }

    fun deleteWorkspace(mediaMode: Int, videoLayoutMode: Int, workspaceName: String) {
        if (workspaceName != "I Love It") {
            libraryViewModel.deleteWorkspace(mediaMode, videoLayoutMode, workspaceName)
        }
    }

    fun isProtected(workspaceName: String): Boolean {
        return workspaceName == "I Love It"
    }
}
