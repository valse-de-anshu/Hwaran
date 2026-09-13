package com.ballade.hwaran.frontend.description.video

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Classifications for related media within an anime / video franchise.
 * Models standard anime database relations (MyAnimeList / AniList / AniDB).
 */
enum class SeriesRelationType(
    val id: String,
    val displayName: String,
    val color: Color,
    val icon: ImageVector
) {
    SEASON("season", "Season", Color(0xFF9E94AB), Icons.Rounded.Layers),
    SEQUEL("sequel", "Sequel", Color(0xFF8F9BB3), Icons.Rounded.FastForward),
    PREQUEL("prequel", "Prequel", Color(0xFF8895A5), Icons.Rounded.FastRewind),
    MOVIE("movie", "Movie", Color(0xFFA58D9A), Icons.Rounded.Movie),
    OVA("ova", "OVA", Color(0xFF7E9DA3), Icons.Rounded.VideoFile),
    ONA("ona", "ONA", Color(0xFF7C9D97), Icons.Rounded.LiveTv),
    SPECIAL("special", "Special", Color(0xFFAFA384), Icons.Rounded.Stars),
    BLURAY("bluray", "Blu-ray", Color(0xFF839DB5), Icons.Rounded.Album),
    SPINOFF("spinoff", "Spinoff", Color(0xFF8F9E83), Icons.Rounded.Share),
    SUMMARY("summary", "Recap", Color(0xFF848D93), Icons.Rounded.Summarize),
    ALT_VERSION("alt_version", "Alt Version", Color(0xFF998A84), Icons.Rounded.Transform);

    companion object {
        val allOptions = entries.toList()

        fun fromPurpose(purpose: String?): SeriesRelationType {
            val clean = purpose?.lowercase()?.trim() ?: return SEASON
            return entries.firstOrNull { 
                clean == it.id || clean.startsWith(it.id) || it.name.equals(clean, ignoreCase = true) 
            } ?: when {
                clean.contains("movie") || clean.contains("film") -> MOVIE
                clean.contains("ova") -> OVA
                clean.contains("ona") -> ONA
                clean.contains("blu") || clean.contains("bd") -> BLURAY
                clean.contains("special") || clean.contains("side") -> SPECIAL
                clean.contains("prequel") -> PREQUEL
                clean.contains("sequel") -> SEQUEL
                clean.contains("recap") || clean.contains("summary") -> SUMMARY
                clean.contains("alt") -> ALT_VERSION
                clean.contains("spin") -> SPINOFF
                else -> SEASON
            }
        }
    }
}
