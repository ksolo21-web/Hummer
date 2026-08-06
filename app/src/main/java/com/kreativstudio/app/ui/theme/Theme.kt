package com.kreativstudio.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kreativstudio.app.model.AppSettings
import com.kreativstudio.app.model.StudioThemeId

val LocalKreativTokens = staticCompositionLocalOf { KreativTokens() }

data class KreativTokens(
    val gold: Color = Color(0xFFD6A568),
    val glow: Color = Color(0xFFB77CFF),
    val success: Color = Color(0xFF8FD8B8),
    val warning: Color = Color(0xFFFFC46B),
    val canvasChrome: Color = Color(0xFF100D16),
    val owlSurface: Color = Color(0xFF1B1622),
)

private data class ThemeBundle(val colors: ColorScheme, val tokens: KreativTokens)

private val RoyalOwl = ThemeBundle(
    colors = darkColorScheme(
        primary = Color(0xFFBE86FF),
        onPrimary = Color(0xFF19052B),
        primaryContainer = Color(0xFF43215E),
        onPrimaryContainer = Color(0xFFF6E8FF),
        secondary = Color(0xFFE1B77B),
        onSecondary = Color(0xFF281704),
        secondaryContainer = Color(0xFF49341A),
        onSecondaryContainer = Color(0xFFFFEED2),
        tertiary = Color(0xFFF19BB6),
        onTertiary = Color(0xFF32101B),
        background = Color(0xFF0B0810),
        onBackground = Color(0xFFF8F1FB),
        surface = Color(0xFF14101A),
        onSurface = Color(0xFFF8F1FB),
        surfaceVariant = Color(0xFF241C2D),
        onSurfaceVariant = Color(0xFFE7DDED),
        outline = Color(0xFF8B7896),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    ),
    tokens = KreativTokens(
        gold = Color(0xFFE0B16F),
        glow = Color(0xFFBE86FF),
        canvasChrome = Color(0xFF0C0911),
        owlSurface = Color(0xFF1A1320),
    ),
)

private val MidnightOwl = ThemeBundle(
    colors = darkColorScheme(
        primary = Color(0xFF9F88FF),
        onPrimary = Color(0xFF160B49),
        primaryContainer = Color(0xFF302B66),
        onPrimaryContainer = Color(0xFFEAE6FF),
        secondary = Color(0xFFBAC6E8),
        onSecondary = Color(0xFF172138),
        secondaryContainer = Color(0xFF2E3951),
        onSecondaryContainer = Color(0xFFDDE6FF),
        tertiary = Color(0xFF72D7D1),
        onTertiary = Color(0xFF003735),
        background = Color(0xFF070912),
        onBackground = Color(0xFFF4F2FF),
        surface = Color(0xFF10131D),
        onSurface = Color(0xFFF4F2FF),
        surfaceVariant = Color(0xFF202433),
        onSurfaceVariant = Color(0xFFDDE0F2),
        outline = Color(0xFF85899B),
    ),
    tokens = KreativTokens(
        gold = Color(0xFFC9B27D),
        glow = Color(0xFF9F88FF),
        canvasChrome = Color(0xFF070912),
        owlSurface = Color(0xFF151827),
    ),
)

private val EmberOwl = ThemeBundle(
    colors = darkColorScheme(
        primary = Color(0xFFFFB66E),
        onPrimary = Color(0xFF3D1B00),
        primaryContainer = Color(0xFF5B2C0E),
        onPrimaryContainer = Color(0xFFFFE6CF),
        secondary = Color(0xFFD8B892),
        onSecondary = Color(0xFF2D1C0B),
        secondaryContainer = Color(0xFF46321F),
        onSecondaryContainer = Color(0xFFF5DCBF),
        tertiary = Color(0xFFE19BA0),
        onTertiary = Color(0xFF321013),
        background = Color(0xFF0D0907),
        onBackground = Color(0xFFFFF3E9),
        surface = Color(0xFF17100C),
        onSurface = Color(0xFFFFF3E9),
        surfaceVariant = Color(0xFF2A1D16),
        onSurfaceVariant = Color(0xFFF1DDCF),
        outline = Color(0xFF9C8172),
    ),
    tokens = KreativTokens(
        gold = Color(0xFFFFC47E),
        glow = Color(0xFFFF9D56),
        canvasChrome = Color(0xFF0C0806),
        owlSurface = Color(0xFF21150E),
    ),
)

private val Moonfeather = ThemeBundle(
    colors = darkColorScheme(
        primary = Color(0xFFBCD5FF),
        onPrimary = Color(0xFF0A2A4F),
        primaryContainer = Color(0xFF243E60),
        onPrimaryContainer = Color(0xFFD9E7FF),
        secondary = Color(0xFFC3C6D9),
        onSecondary = Color(0xFF292A36),
        secondaryContainer = Color(0xFF3F414E),
        onSecondaryContainer = Color(0xFFE2E2F1),
        tertiary = Color(0xFFD7B8F5),
        onTertiary = Color(0xFF3A1B50),
        background = Color(0xFF080B11),
        onBackground = Color(0xFFF4F6FF),
        surface = Color(0xFF11151D),
        onSurface = Color(0xFFF4F6FF),
        surfaceVariant = Color(0xFF202630),
        onSurfaceVariant = Color(0xFFDDE3EC),
        outline = Color(0xFF87909C),
    ),
    tokens = KreativTokens(
        gold = Color(0xFFD7BE8A),
        glow = Color(0xFFBCD5FF),
        canvasChrome = Color(0xFF080B11),
        owlSurface = Color(0xFF151B25),
    ),
)

private val ForestNocturne = ThemeBundle(
    colors = darkColorScheme(
        primary = Color(0xFF82D7BE),
        onPrimary = Color(0xFF00382D),
        primaryContainer = Color(0xFF155142),
        onPrimaryContainer = Color(0xFFA1F4DA),
        secondary = Color(0xFFB8CCB8),
        onSecondary = Color(0xFF233427),
        secondaryContainer = Color(0xFF394B3C),
        onSecondaryContainer = Color(0xFFD4E8D3),
        tertiary = Color(0xFFC8B98E),
        onTertiary = Color(0xFF332D13),
        background = Color(0xFF07100D),
        onBackground = Color(0xFFF0F8F3),
        surface = Color(0xFF0F1915),
        onSurface = Color(0xFFF0F8F3),
        surfaceVariant = Color(0xFF1D2A24),
        onSurfaceVariant = Color(0xFFD7E5DC),
        outline = Color(0xFF829089),
    ),
    tokens = KreativTokens(
        gold = Color(0xFFD3B66F),
        glow = Color(0xFF66D7B5),
        canvasChrome = Color(0xFF06100C),
        owlSurface = Color(0xFF13201A),
    ),
)

@Composable
fun KreativTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val bundle = when (settings.themeId) {
        StudioThemeId.MIDNIGHT_OWL -> MidnightOwl
        StudioThemeId.EMBER_OWL -> EmberOwl
        StudioThemeId.MOONFEATHER -> Moonfeather
        StudioThemeId.FOREST_NOCTURNE -> ForestNocturne
        StudioThemeId.ROYAL_OWL -> RoyalOwl
    }
    val colors = if (settings.highContrastText) {
        bundle.colors.copy(
            onBackground = Color(0xFFFFFBFF),
            onSurface = Color(0xFFFFFBFF),
            onSurfaceVariant = Color(0xFFF0EAF3),
            outline = bundle.colors.outline.copy(alpha = 1f),
        )
    } else {
        bundle.colors
    }
    val scale = settings.textScale.coerceIn(.85f, 1.5f)
    val typography = Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 46.sp * scale, lineHeight = 54.sp * scale),
        displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 36.sp * scale, lineHeight = 44.sp * scale),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp * scale, lineHeight = 38.sp * scale),
        headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 25.sp * scale, lineHeight = 32.sp * scale),
        titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp * scale, lineHeight = 29.sp * scale),
        titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp * scale, lineHeight = 24.sp * scale),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp * scale, lineHeight = 24.sp * scale),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp * scale, lineHeight = 21.sp * scale),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp * scale, lineHeight = 20.sp * scale),
        labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp * scale, lineHeight = 18.sp * scale),
    )
    CompositionLocalProvider(LocalKreativTokens provides bundle.tokens) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}

fun Color.contrastText(): Color {
    val luminance = (.299f * red + .587f * green + .114f * blue)
    return if (luminance > .54f) Color(0xFF08080B) else Color.White
}
