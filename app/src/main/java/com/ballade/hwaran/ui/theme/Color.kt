package com.ballade.hwaran.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Hwaran Obsidian Onyx Design System
// Ultra-luxury, soothing, OLED-friendly dark palette
// ==========================================

// Obsidian Onyx (Default / Flagship Theme)
val ObsidianBackground = Color(0xFF090A0F)
val ObsidianSurface = Color(0xFF111318)
val ObsidianSurfaceVariant = Color(0xFF181B22)
val ObsidianSurfaceElevated = Color(0xFF20232B)
val ObsidianSurfaceGlass = Color(0xD9111318) // Translucent glass (85% alpha)

// Hairlines & Borders (Soft whisper calmness)
val ObsidianBorder = Color(0x10FFFFFF) // 6.5% white
val ObsidianBorderSubtle = Color(0x08FFFFFF) // 3% white
val ObsidianBorderHighlight = Color(0x22FFFFFF) // 13% soft calm glow

// Typography Tokens (Soothing soft moonlight tones)
val TextPrimary = Color(0xFFE6E8EC) // Soft calm light (no eye strain)
val TextSecondary = Color(0xFF8E939D) // Soft graphite
val TextMuted = Color(0xFF555962) // Subdued metadata

// Signature Neutral Accent (Calm diffused light)
val AccentTitanium = Color(0xFFD4D8E0)
val AccentTitaniumDark = Color(0xFF8E95A2)
val AmbientGlowDefault = Color(0x12FFFFFF) // 7% gentle ambient aura

// OLED Pitch Black (Pure contrast)
val PitchBlackBackground = Color(0xFF000000)
val PitchBlackSurface = Color(0xFF0C0C0E)
val PitchBlackSurfaceVariant = Color(0xFF141418)

// Nordic Deep Slate (Architectural)
val NordicSlateBackground = Color(0xFF0A0D12)
val NordicSlateSurface = Color(0xFF10151C)
val NordicSlateSurfaceVariant = Color(0xFF171E28)

// Smoked Charcoal (Analog luxury)
val SmokedCharcoalBackground = Color(0xFF0D0E11)
val SmokedCharcoalSurface = Color(0xFF14161A)
val SmokedCharcoalSurfaceVariant = Color(0xFF1D2026)

// Backward-compatible tokens mapped cleanly to Obsidian Onyx
val PureDarkBackground = ObsidianBackground
val PureDarkSurface = ObsidianSurface
val PureDarkSurfaceVariant = ObsidianSurfaceVariant
val PureDarkPrimary = AccentTitanium
val PureDarkSecondary = AccentTitanium
val PureDarkText = TextPrimary
val PureDarkTextMuted = TextSecondary
val PureDarkBorder = ObsidianBorder

// Deprecated legacy tokens mapped cleanly to Obsidian tokens to ensure zero compilation breaks
val TokyoBackground = ObsidianBackground
val TokyoSurface = ObsidianSurface
val TokyoSurfaceVariant = ObsidianSurfaceVariant
val TokyoPrimary = AccentTitanium
val TokyoSecondary = AccentTitanium
val TokyoText = TextPrimary
val TokyoTextMuted = TextSecondary
val TokyoBorder = ObsidianBorder

val BlueberryBackground = NordicSlateBackground
val BlueberrySurface = NordicSlateSurface
val BlueberrySurfaceVariant = NordicSlateSurfaceVariant
val BlueberryPrimary = AccentTitanium
val BlueberrySecondary = AccentTitanium

val SnowfallBackground = SmokedCharcoalBackground
val SnowfallSurface = SmokedCharcoalSurface
val SnowfallSurfaceVariant = SmokedCharcoalSurfaceVariant
val SnowfallPrimary = AccentTitanium
val SnowfallSecondary = AccentTitanium

val GrapeBackground = ObsidianBackground
val GrapeSurface = ObsidianSurface
val GrapeSurfaceVariant = ObsidianSurfaceVariant
val GrapePrimary = AccentTitanium
val GrapeSecondary = AccentTitanium
