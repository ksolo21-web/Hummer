package com.koenterprises.territorycardstudio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val BrandGreenLight = Color(0xFF146B4C)
private val BrandGreenDark = Color(0xFF69D9A6)
private val LightBackground = Color(0xFFF5F7F6)
private val DarkBackground = Color(0xFF0B1317)
private val DarkSurface = Color(0xFF111B20)
private val DarkSurfaceVariant = Color(0xFF1A272D)

private val LightColors = lightColorScheme(
    primary = BrandGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F4E4),
    onPrimaryContainer = Color(0xFF073B29),
    secondary = Color(0xFF406A5A),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF17201C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17201C),
    surfaceVariant = Color(0xFFE4EAE7),
    onSurfaceVariant = Color(0xFF3F4944),
    outline = Color(0xFF75817B),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenDark,
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF075139),
    onPrimaryContainer = Color(0xFFB9F1D7),
    secondary = Color(0xFFA8CEBA),
    onSecondary = Color(0xFF12372A),
    background = DarkBackground,
    onBackground = Color(0xFFE0E7E3),
    surface = DarkSurface,
    onSurface = Color(0xFFE0E7E3),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBDC9C3),
    outline = Color(0xFF89958F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Immutable
data class TerritoryStatusColors(
    val approvedContainer: Color,
    val approvedContent: Color,
    val needsNewContainer: Color,
    val needsNewContent: Color,
    val reAuditContainer: Color,
    val reAuditContent: Color,
    val conflictContainer: Color,
    val conflictContent: Color,
    val screeningContainer: Color,
    val screeningContent: Color
)

@Immutable
data class TerritoryMapSemanticColors(
    val workInsideOnlyYellow: Color,
    val workBothSidesGreen: Color,
    val doNotWorkRed: Color
)

private val LightStatusColors = TerritoryStatusColors(
    approvedContainer = Color(0xFFD7F4E5),
    approvedContent = Color(0xFF0B5D3D),
    needsNewContainer = Color(0xFFFFE9A8),
    needsNewContent = Color(0xFF624600),
    reAuditContainer = Color(0xFFFFDCC2),
    reAuditContent = Color(0xFF733600),
    conflictContainer = Color(0xFFFFDAD6),
    conflictContent = Color(0xFF93000A),
    screeningContainer = Color(0xFFD9E8FF),
    screeningContent = Color(0xFF17466F)
)

private val DarkStatusColors = TerritoryStatusColors(
    approvedContainer = Color(0xFF123E2E),
    approvedContent = Color(0xFF9BE8C3),
    needsNewContainer = Color(0xFF4D3C05),
    needsNewContent = Color(0xFFFFD965),
    reAuditContainer = Color(0xFF542B0E),
    reAuditContent = Color(0xFFFFBE8A),
    conflictContainer = Color(0xFF5F1D20),
    conflictContent = Color(0xFFFFB4AB),
    screeningContainer = Color(0xFF183852),
    screeningContent = Color(0xFFA8CBF2)
)

private val MapSemanticColors = TerritoryMapSemanticColors(
    workInsideOnlyYellow = Color(0xFFF4C430),
    workBothSidesGreen = Color(0xFF2FA665),
    doNotWorkRed = Color(0xFFD94C4C)
)

val LocalTerritoryStatusColors = staticCompositionLocalOf { LightStatusColors }
val LocalTerritoryMapSemanticColors = staticCompositionLocalOf { MapSemanticColors }

@Composable
fun TerritoryCardStudioTheme(
    appearanceMode: AppearanceMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTerritoryStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
        LocalTerritoryMapSemanticColors provides MapSemanticColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}
