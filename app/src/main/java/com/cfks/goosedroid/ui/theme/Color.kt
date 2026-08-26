package com.cfks.goosedroid.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// STRICT MONOCHROME PALETTE — Black / Gray / White ONLY.
// Light & Dark schemes are pure inversions of the same gray ramp.
// NEVER introduce chromatic colors anywhere in the app.
// ─────────────────────────────────────────────────────────────────────────────

// ── Dark scheme (default) ───────────────────────────────────────────────────
val TdsmBackground = Color(0xFF000000)      // Pure Black
val TdsmSurface = Color(0xFF121212)         // Very Dark Gray
val TdsmSurfaceElevated = Color(0xFF1E1E1E) // Dark Gray
val TdsmBadgeBg = Color(0xFF262626)         // Badge / Chip surface
val TdsmBorder = Color(0xFF333333)          // Medium Dark Gray
val TdsmBorderLight = Color(0xFF555555)     // Medium Gray
val TdsmMuted = Color(0xFF777777)           // Placeholder Gray
val TdsmTextSecondary = Color(0xFFAAAAAA)   // Light Gray
val TdsmTextPrimary = Color(0xFFFFFFFF)     // Pure White
val TdsmAccent = Color(0xFFFFFFFF)          // Accent = White
val TdsmOnAccent = Color(0xFF000000)        // Content drawn on Accent
val TdsmOverlayDim = Color(0x99000000)      // Dimmed Black (alpha)

// ── Light scheme (inverted ramp — still strictly monochrome) ────────────────
val TdsmLightBackground = Color(0xFFFFFFFF)      // Pure White
val TdsmLightSurface = Color(0xFFF7F7F7)         // Near White
val TdsmLightSurfaceElevated = Color(0xFFEFEFEF) // Light Gray
val TdsmLightBadgeBg = Color(0xFFE3E3E3)         // Badge / Chip surface
val TdsmLightBorder = Color(0xFFDDDDDD)          // Light Border
val TdsmLightBorderLight = Color(0xFFBBBBBB)     // Mid-Light Border
val TdsmLightMuted = Color(0xFF888888)           // Placeholder Gray
val TdsmLightTextSecondary = Color(0xFF555555)   // Dark-Mid Gray
val TdsmLightTextPrimary = Color(0xFF111111)     // Near Black
val TdsmLightAccent = Color(0xFF171717)          // Accent = Near Black
val TdsmLightOnAccent = Color(0xFFFAFAFA)        // Content drawn on Accent

/**
 * @deprecated Legacy direct-color references kept only so unmigrated screens
 * (EditorScreen / PlaygroundScreen / OverlayService) still compile while they
 * are incrementally migrated to [androidx.compose.material3.MaterialTheme]
 * color roles, which is what makes light mode render correctly.
 */
val TdsmTrashActive = TdsmTextPrimary
