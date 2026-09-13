package com.ballade.hwaran.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer

/**
 * HwaranPlayerHolder
 *
 * A process-level singleton that holds the single [ExoPlayer] instance shared
 * between [MusicViewModel] (which drives playback) and [MusicNotificationService]
 * (which attaches a [MediaSession] so the OS can display the media notification).
 *
 * Why a singleton?
 *   - Media3's [MediaSessionService] needs a player reference in [onCreate], which
 *     fires before the ViewModel is necessarily alive (e.g. when the user taps a
 *     notification action from the lock screen).
 *   - Both components calling [ExoPlayer.Builder] independently would create two
 *     audio streams, two audio-focus requests, and two notification channels.
 *
 * Usage:
 *   // In MusicViewModel.init {}
 *   val player = HwaranPlayerHolder.getOrCreate(application)
 *
 *   // In MusicNotificationService.onCreate()
 *   val player = HwaranPlayerHolder.getOrCreate(this)
 */
object HwaranPlayerHolder {

    @Volatile
    private var player: ExoPlayer? = null

    @Volatile
    var activeExternalChapter: com.ballade.hwaran.core.database.entity.ChapterEntity? = null

    @Volatile
    var activeExternalManga: com.ballade.hwaran.core.database.entity.MangaEntity? = null

    /** Returns the existing player, or creates a fresh one with music audio attributes. */
    @Synchronized
    fun getOrCreate(context: Context): ExoPlayer {
        return player ?: ExoPlayer.Builder(context.applicationContext)
            .build()
            .also { newPlayer ->
                val attrs = AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build()
                newPlayer.setAudioAttributes(attrs, /* handleAudioFocus= */ true)
                
                // Set default metadata to identify the app
                val defaultMetadata = MediaMetadata.Builder()
                    .setTitle("Hwaran")
                    .setDisplayTitle("Hwaran")
                    .setArtist("Hwaran")
                    .build()
                newPlayer.setPlaylistMetadata(defaultMetadata)

                player = newPlayer
            }
    }

    /** Call only from [MusicNotificationService.onDestroy] when the process is ending. */
    @Synchronized
    fun release() {
        player?.release()
        player = null
    }
}
