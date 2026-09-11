package com.ballade.hwaran.frontend.description

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
    SEASON("season", "Season", Color(0xFFAB47BC), Icons.Rounded.Layers),
    SEQUEL("sequel", "Sequel", Color(0xFF7E57C2), Icons.Rounded.FastForward),
    PREQUEL("prequel", "Prequel", Color(0xFF5C6BC0), Icons.Rounded.FastRewind),
    MOVIE("movie", "Movie", Color(0xFFEC407A), Icons.Rounded.Movie),
    OVA("ova", "OVA", Color(0xFF26C6DA), Icons.Rounded.VideoFile),
    ONA("ona", "ONA", Color(0xFF26A69A), Icons.Rounded.LiveTv),
    SPECIAL("special", "Special", Color(0xFFFFA726), Icons.Rounded.Stars),
    BLURAY("bluray", "Blu-ray", Color(0xFF42A5F5), Icons.Rounded.Album),
    SPINOFF("spinoff", "Spinoff", Color(0xFF9CCC65), Icons.Rounded.Share),
    SUMMARY("summary", "Recap", Color(0xFF78909C), Icons.Rounded.Summarize),
    ALT_VERSION("alt_version", "Alt Version", Color(0xFF8D6E63), Icons.Rounded.Transform);

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
