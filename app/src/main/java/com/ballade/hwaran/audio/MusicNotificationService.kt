package com.ballade.hwaran.audio

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * MusicNotificationService
 *
 * A Media3 [MediaSessionService] that hosts the app's single [MediaSession].
 */
class MusicNotificationService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d("MusicService", "onCreate")
        
        // Ensure notification channel is created with the correct name
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channel = android.app.NotificationChannel(
            "hwaran_music",
            getString(com.ballade.hwaran.R.string.app_name),
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)

        val player = HwaranPlayerHolder.getOrCreate(this)

        val sessionActivityIntent = packageManager
            .getLaunchIntentForPackage(packageName)
            ?.let { intent ->
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

        val closeCommand = SessionCommand("ACTION_CLOSE", android.os.Bundle.EMPTY)
        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Close")
            .setCustomIconResId(com.ballade.hwaran.R.drawable.ic_close_premium)
            .setSessionCommand(closeCommand)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setId("Hwaran")
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                        .add(closeCommand)
                        .build()
                    
                    // Explicitly ensure all playback commands are available
                    val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                        .add(Player.COMMAND_PLAY_PAUSE)
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .add(Player.COMMAND_STOP)
                        .build()
                        
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands,
                        availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: android.os.Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == "ACTION_CLOSE") {
                        player.stop()
                        player.clearMediaItems()
                        stopSelf()
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .also { builder ->
                sessionActivityIntent?.let { builder.setSessionActivity(it) }
            }
            .build()
            
        // Setting custom layout ensures the button appears in the notification
        mediaSession?.setCustomLayout(listOf(closeButton))

        // Use a customized DefaultMediaNotificationProvider
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId("hwaran_music")
            .setChannelName(com.ballade.hwaran.R.string.app_name)
            .build()
        notificationProvider.setSmallIcon(com.ballade.hwaran.R.drawable.ic_music_notification)
        setMediaNotificationProvider(notificationProvider)
        
        Log.d("MusicService", "Session built and provider set")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        Log.d("MusicService", "onGetSession from ${controllerInfo.packageName}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d("MusicService", "onDestroy")
        mediaSession?.let {
            it.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
