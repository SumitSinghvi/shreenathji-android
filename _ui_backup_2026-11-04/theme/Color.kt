package com.shreenathji.sales.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Warm Slate / Charcoal Base (Replacing cold monochrome zinc)
val Slate50 = Color(0xFFF8FAFC)
val Slate100 = Color(0xFFF1F5F9)
val Slate200 = Color(0xFFE2E8F0)
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate600 = Color(0xFF475569)
val Slate700 = Color(0xFF334155)
val Slate800 = Color(0xFF1E293B)
val Slate900 = Color(0xFF0F172A)
val Slate950 = Color(0xFF020617)

// Backward compatible Zinc mappings
val Zinc50 = Slate50
val Zinc100 = Slate100
val Zinc200 = Slate200
val Zinc300 = Slate300
val Zinc400 = Slate400
val Zinc500 = Slate500
val Zinc600 = Slate600
val Zinc700 = Slate700
val Zinc800 = Slate800
val Zinc900 = Slate900
val Zinc950 = Slate950

// Brand Colors: Signature Textile Saffron & Deep Indigo
val BrandOrange = Color(0xFFEA580C)
val BrandOrangeDark = Color(0xFFC2410C)
val BrandOrangeLight = Color(0xFFFFF7ED)
val BrandOrangeBorder = Color(0xFFFFEDD5)
val BrandOrangeGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEA580C), Color(0xFFF97316))
)
val BrandDarkGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
)

// shadcn Accents
val ShadcnBlack = Slate950
val ShadcnWhite = Color(0xFFFFFFFF)
val ShadcnBorder = Slate200
val ShadcnInput = Slate200
val ShadcnMuted = Slate100
val ShadcnMutedForeground = Slate500

// Status Accents (Refined & Vibrant)
val EmeraldSuccess = Color(0xFF10B981)
val EmeraldSuccessBg = Color(0xFFECFDF5)
val EmeraldSuccessBorder = Color(0xFFA7F3D0)
val EmeraldSuccessText = Color(0xFF047857)

val AmberWarning = Color(0xFFF59E0B)
val AmberWarningBg = Color(0xFFFFFBEB)
val AmberWarningBorder = Color(0xFFFDE68A)
val AmberWarningText = Color(0xFFB45309)

val SkyInfo = Color(0xFF0284C7)
val SkyInfoBg = Color(0xFFF0F9FF)
val SkyInfoBorder = Color(0xFFBAE6FD)
val SkyInfoText = Color(0xFF0369A1)

val Orange500 = Color(0xFFF97316)
val Orange600 = Color(0xFFEA580C)

val IndigoAccent = Color(0xFF6366F1)
val IndigoAccentBg = Color(0xFFEEF2FF)
val IndigoAccentBorder = Color(0xFFC7D2FE)
val IndigoAccentText = Color(0xFF4338CA)

val PurpleAccent = Color(0xFF8B5CF6)
val PurpleAccentBg = Color(0xFFF5F3FF)
val PurpleAccentBorder = Color(0xFFDDD6FE)
val PurpleAccentText = Color(0xFF6D28D9)

val RoseDestructive = Color(0xFFEF4444)
val RoseDestructiveBg = Color(0xFFFEF2F2)
val RoseDestructiveBorder = Color(0xFFFECACA)
val RoseDestructiveText = Color(0xFFB91C1C)

// Dynamic Avatar Palette (for visually distinct store initials)
data class AvatarColorScheme(val bg: Color, val text: Color, val border: Color)

val AvatarColorPalettes = listOf(
    AvatarColorScheme(Color(0xFFEEF2FF), Color(0xFF4338CA), Color(0xFFC7D2FE)), // Indigo
    AvatarColorScheme(Color(0xFFECFDF5), Color(0xFF047857), Color(0xFFA7F3D0)), // Emerald
    AvatarColorScheme(Color(0xFFFFF7ED), Color(0xFFC2410C), Color(0xFFFFEDD5)), // Orange
    AvatarColorScheme(Color(0xFFF5F3FF), Color(0xFF6D28D9), Color(0xFFDDD6FE)), // Purple
    AvatarColorScheme(Color(0xFFF0F9FF), Color(0xFF0369A1), Color(0xFFBAE6FD)), // Sky
    AvatarColorScheme(Color(0xFFFFFBEB), Color(0xFFB45309), Color(0xFFFDE68A)), // Amber
    AvatarColorScheme(Color(0xFFFDF2F8), Color(0xFFBE185D), Color(0xFFFBCFE8)), // Pink
    AvatarColorScheme(Color(0xFFF1F5F9), Color(0xFF334155), Color(0xFFCBD5E1))  // Slate
)

fun getAvatarColors(name: String): AvatarColorScheme {
    if (name.isBlank()) return AvatarColorPalettes[0]
    val hash = kotlin.math.abs(name.hashCode())
    return AvatarColorPalettes[hash % AvatarColorPalettes.size]
}
