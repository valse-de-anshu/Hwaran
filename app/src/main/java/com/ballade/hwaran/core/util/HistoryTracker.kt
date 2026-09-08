package com.ballade.hwaran.core.util

import android.content.Context
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.database.entity.HistoryEventEntity
import com.ballade.hwaran.core.datastore.GlobalSettings
import com.ballade.hwaran.core.datastore.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object HistoryTracker {
    private var context: Context? = null
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(ctx: Context, db: AppDatabase) {
        context = ctx.applicationContext
        database = db
    }

    fun logEvent(eventType: String, itemName: String, details: String) {
        val ctx = context ?: return
        val db = database ?: return
        scope.launch {
            try {
                val stopTracking = ctx.dataStore.data.first()[GlobalSettings.STOP_TRACKING] ?: false
                if (stopTracking) return@launch

                db.historyDao().insertHistoryEvent(
                    HistoryEventEntity(
                        timestamp = System.currentTimeMillis(),
                        eventType = eventType,
                        itemName = itemName,
                        details = details
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
