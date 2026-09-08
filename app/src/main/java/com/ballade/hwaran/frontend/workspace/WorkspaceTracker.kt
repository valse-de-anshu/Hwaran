package com.ballade.hwaran.frontend.workspace

object WorkspaceTracker {
    fun resolveActiveWorkspace(
        mediaMode: Int,
        activeScreenToon: String?,
        activeScreenBook: String?,
        activeScreenVideo: String?
    ): String {
        val raw = when (mediaMode) {
            0 -> activeScreenToon
            1 -> activeScreenBook
            2 -> activeScreenVideo
            else -> "I Love It"
        }
        return if (raw.isNullOrBlank()) "I Love It" else raw
    }

    fun getWorkspacesListForMode(
        mediaMode: Int,
        workspacesToon: List<String>,
        workspacesBook: List<String>,
        workspacesVideo: List<String>
    ): List<String> {
        val rawList = when (mediaMode) {
            0 -> workspacesToon
            1 -> workspacesBook
            2 -> workspacesVideo
            else -> emptyList()
        }
        val list = rawList.toMutableList()
        if (!list.contains("I Love It")) {
            list.add(0, "I Love It")
        }
        return list.filter { it.isNotBlank() }.sorted()
    }

    fun getDisplayListForDialer(
        activeScreensList: List<String>
    ): List<String> {
        return if (activeScreensList.size <= 1 && activeScreensList.contains("I Love It")) {
            emptyList()
        } else {
            activeScreensList
        }
    }
}
