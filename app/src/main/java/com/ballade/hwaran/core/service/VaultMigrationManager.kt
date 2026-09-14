package com.ballade.hwaran.core.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VaultMigrationProgress(
    val mangaId: Long = -1L,
    val title: String = "",
    val isRunning: Boolean = false,
    val progress: Int = 0,
    val status: String = ""
)

object VaultMigrationManager {
    private val _currentMigration = MutableStateFlow<VaultMigrationProgress?>(null)
    val currentMigration: StateFlow<VaultMigrationProgress?> = _currentMigration.asStateFlow()

    fun updateProgress(mangaId: Long, title: String, progress: Int, status: String) {
        _currentMigration.value = VaultMigrationProgress(
            mangaId = mangaId,
            title = title,
            isRunning = true,
            progress = progress.coerceIn(0, 100),
            status = status
        )
    }

    fun finishMigration(mangaId: Long) {
        if (_currentMigration.value?.mangaId == mangaId) {
            _currentMigration.value = null
        }
    }
}
