package com.ballade.hwaran.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ballade.hwaran.MainActivity
import com.ballade.hwaran.R
import com.ballade.hwaran.core.database.AppDatabase
import com.ballade.hwaran.core.util.LocalVaultMigrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VaultMigrationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_MIGRATION = "com.ballade.hwaran.action.START_VAULT_MIGRATION"
        const val EXTRA_MANGA_ID = "extra_manga_id"

        private const val CHANNEL_ONGOING = "hwaran_vault_migration_ongoing"
        private const val CHANNEL_ALERTS = "hwaran_vault_migration_alerts"
        private const val NOTIFICATION_ID_ONGOING = 7001

        fun start(context: Context, mangaId: Long) {
            val intent = Intent(context, VaultMigrationService::class.java).apply {
                action = ACTION_START_MIGRATION
                putExtra(EXTRA_MANGA_ID, mangaId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()

        // Acquire WakeLock with 1 hour safety timeout so large multi-gigabyte files don't freeze if device sleeps
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "hwaran:VaultMigrationWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mangaId = intent?.getLongExtra(EXTRA_MANGA_ID, -1L) ?: -1L
        if (mangaId == -1L) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        // Must call startForeground within system window (Android 8.0+)
        val initialNotification = buildOngoingNotification("Media item", 0, "Preparing migration...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_ONGOING,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_ONGOING, initialNotification)
        }

        serviceScope.launch {
            try {
                val database = AppDatabase.getDatabase(applicationContext)
                val manga = database.libraryDao().getMangaById(mangaId)
                if (manga == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return@launch
                }

                VaultMigrationManager.updateProgress(manga.id, manga.title, 0, "Starting migration...")
                var lastNotifyMs = 0L

                val result = LocalVaultMigrator.moveToVault(
                    context = applicationContext,
                    database = database,
                    manga = manga,
                    onProgress = { progress, status ->
                        VaultMigrationManager.updateProgress(manga.id, manga.title, progress, status)
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyMs > 400 || progress >= 100) {
                            lastNotifyMs = now
                            notificationManager.notify(
                                NOTIFICATION_ID_ONGOING,
                                buildOngoingNotification(manga.title, progress, status)
                            )
                        }
                    }
                )

                stopForeground(STOP_FOREGROUND_REMOVE)

                if (result.isSuccess) {
                    sendCompletionNotification(manga.title, manga.id)
                } else {
                    sendErrorNotification(
                        manga.title,
                        manga.id,
                        result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopForeground(STOP_FOREGROUND_REMOVE)
            } finally {
                VaultMigrationManager.finishMigration(mangaId)
                try {
                    if (wakeLock?.isHeld == true) {
                        wakeLock?.release()
                    }
                } catch (_: Exception) {}
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING,
                "Local Vault Migration (Active)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live progress when transferring media to local vault"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Local Vault Migration (Alerts)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when local vault file transfer completes"
                setShowBadge(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(ongoingChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private fun buildOngoingNotification(title: String, progress: Int, status: String): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification_aesthetic)
            .setContentTitle("Moving to Local Vault: $title")
            .setContentText(if (status.isNotBlank()) "$status ($progress%)" else "Migrating media... $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun sendCompletionNotification(title: String, mangaId: Long) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (mangaId % 1000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_aesthetic)
            .setContentTitle("Shift to Local Vault Complete")
            .setContentText("\"$title\" has been safely shifted to your local vault.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("\"$title\" has been moved to your private local vault and original files removed from external storage."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((20000 + (mangaId % 10000)).toInt(), notification)
    }

    private fun sendErrorNotification(title: String, mangaId: Long, error: String) {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            (mangaId % 1000).toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification_aesthetic)
            .setContentTitle("Vault Migration Failed")
            .setContentText("Could not move \"$title\": $error")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Could not move \"$title\": $error"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((20000 + (mangaId % 10000)).toInt(), notification)
    }
}
