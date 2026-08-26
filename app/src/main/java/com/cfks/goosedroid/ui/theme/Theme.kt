package com.cfks.goosedroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Monochrome Dark scheme — maps the Tdsm gray ramp onto Material roles:
 *   background=Black | surface=#121212 | elevated=surfaceVariant=#1E1E1E
 *   accent=primary=White | onAccent=onPrimary=Black
 */
private val DarkMonoScheme = darkColorScheme(
    background = TdsmBackground,
    onBackground = TdsmTextPrimary,
    surface = TdsmSurface,
    onSurface = TdsmTextPrimary,
    surfaceVariant = TdsmSurfaceElevated,
    onSurfaceVariant = TdsmTextSecondary,
    primary = TdsmAccent,
    onPrimary = TdsmOnAccent,
    secondary = TdsmMuted,
    onSecondary = TdsmBackground,
    outline = TdsmBorder,
    outlineVariant = TdsmBorderLight,
    surfaceContainer = TdsmBadgeBg,
    surfaceContainerHigh = TdsmSurfaceElevated,
    surfaceContainerHighest = TdsmSurfaceElevated,
    inverseSurface = TdsmTextPrimary,
    inverseOnSurface = TdsmBackground,
    scrim = TdsmOverlayDim
)

/**
 * Monochrome Light scheme — strict inversion of the dark ramp.
 * No chromatic color is introduced in either mode.
 */
private val LightMonoScheme = lightColorScheme(
    background = TdsmLightBackground,
    onBackground = TdsmLightTextPrimary,
    surface = TdsmLightSurface,
    onSurface = TdsmLightTextPrimary,
    surfaceVariant = TdsmLightSurfaceElevated,
    onSurfaceVariant = TdsmLightTextSecondary,
    primary = TdsmLightAccent,
    onPrimary = TdsmLightOnAccent,
    secondary = TdsmLightMuted,
    onSecondary = TdsmLightBackground,
    outline = TdsmLightBorder,
    outlineVariant = TdsmLightBorderLight,
    surfaceContainer = TdsmLightBadgeBg,
    surfaceContainerHigh = TdsmLightSurfaceElevated,
    surfaceContainerHighest = TdsmLightSurfaceElevated,
    inverseSurface = TdsmLightTextPrimary,
    inverseOnSurface = TdsmLightBackground,
    scrim = TdsmOverlayDim
)

val GooseTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 1.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * App theme — follows the system dark/light preference by default.
 * Strictly monochrome in BOTH modes per design mandate:
 * black, white and grays only — never any chromatic color.
 *
 * @param darkTheme override point (e.g. a future in-app setting toggle);
 * defaults to following the OS setting.
 */
@Composable
fun GooseDesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkMonoScheme else LightMonoScheme,
        typography = GooseTypography,
        content = content
    )
}
