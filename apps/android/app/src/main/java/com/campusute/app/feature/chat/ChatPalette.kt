package com.campusute.app.feature.chat

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Academic Indigo, scoped to the chat subtree only.
 *
 * `CampusTheme` stays CampusBlue for the rest of the app; wrapping chat content in
 * [ChatTheme] lets every chat composable keep reading `MaterialTheme.colorScheme`
 * roles instead of carrying its own hex literals, and makes an eventual app-wide
 * re-brand a single-file change.
 */
internal object ChatPalette {
    // Light — Material 3 tonal tiers over a lavender canvas.
    val Primary = Color(0xFF4338CA)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFE0E7FF)
    val OnPrimaryContainer = Color(0xFF312E81)
    val Secondary = Color(0xFF6366F1)
    val OnSecondary = Color(0xFFFFFFFF)
    val Tertiary = Color(0xFF0284C7)
    val OnTertiary = Color(0xFFFFFFFF)
    val SecondaryContainer = Color(0xFFE0E7FF)
    val OnSecondaryContainer = Color(0xFF1E1B4B)
    val TertiaryContainer = Color(0xFFE0F2FE)
    val OnTertiaryContainer = Color(0xFF082F49)
    val Canvas = Color(0xFFFAF8FF)
    val ContainerLowest = Color(0xFFFFFFFF)
    val ContainerLow = Color(0xFFF5F3FF)
    val Container = Color(0xFFEEF2FF)
    val ContainerHigh = Color(0xFFE2E8F0)
    val OnSurface = Color(0xFF0D1C2E)
    val OnSurfaceVariant = Color(0xFF464554)
    val Outline = Color(0xFFCBD5E1)
    val OutlineVariant = Color(0xFFE2E8F0)
    val Error = Color(0xFFBA1A1A)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    // Dark — the same indigo hue family lifted to fixed-dim tones over tinted neutrals.
    val DarkPrimary = Color(0xFFC3C0FF)
    val DarkOnPrimary = Color(0xFF372ABF)
    val DarkPrimaryContainer = Color(0xFF372ABF)
    val DarkOnPrimaryContainer = Color(0xFFE3DFFF)
    val DarkSecondary = Color(0xFFC0C1FF)
    val DarkOnSecondary = Color(0xFF2F2EBE)
    val DarkTertiary = Color(0xFF93CCFF)
    val DarkOnTertiary = Color(0xFF004B73)
    val DarkSecondaryContainer = Color(0xFF1E1B4B)
    val DarkOnSecondaryContainer = Color(0xFFC7D2FE)
    val DarkTertiaryContainer = Color(0xFF0C4A6E)
    val DarkOnTertiaryContainer = Color(0xFFBAE6FD)
    val DarkCanvas = Color(0xFF121319)
    val DarkContainerLowest = Color(0xFF0D0E13)
    val DarkContainerLow = Color(0xFF1A1B22)
    val DarkContainer = Color(0xFF1E1F27)
    val DarkContainerHigh = Color(0xFF292A33)
    val DarkOnSurface = Color(0xFFE4E2EC)
    val DarkOnSurfaceVariant = Color(0xFFC6C5D6)
    val DarkOutline = Color(0xFF909098)
    val DarkOutlineVariant = Color(0xFF45464E)
    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)
}

private val ChatLightScheme = lightColorScheme(
    primary = ChatPalette.Primary,
    onPrimary = ChatPalette.OnPrimary,
    primaryContainer = ChatPalette.PrimaryContainer,
    onPrimaryContainer = ChatPalette.OnPrimaryContainer,
    secondary = ChatPalette.Secondary,
    onSecondary = ChatPalette.OnSecondary,
    tertiary = ChatPalette.Tertiary,
    onTertiary = ChatPalette.OnTertiary,
    secondaryContainer = ChatPalette.SecondaryContainer,
    onSecondaryContainer = ChatPalette.OnSecondaryContainer,
    tertiaryContainer = ChatPalette.TertiaryContainer,
    onTertiaryContainer = ChatPalette.OnTertiaryContainer,
    background = ChatPalette.Canvas,
    onBackground = ChatPalette.OnSurface,
    surface = ChatPalette.Canvas,
    onSurface = ChatPalette.OnSurface,
    surfaceVariant = ChatPalette.Container,
    onSurfaceVariant = ChatPalette.OnSurfaceVariant,
    surfaceContainerLowest = ChatPalette.ContainerLowest,
    surfaceContainerLow = ChatPalette.ContainerLow,
    surfaceContainer = ChatPalette.Container,
    surfaceContainerHigh = ChatPalette.ContainerHigh,
    surfaceContainerHighest = ChatPalette.OutlineVariant,
    outline = ChatPalette.Outline,
    outlineVariant = ChatPalette.OutlineVariant,
    error = ChatPalette.Error,
    onError = ChatPalette.OnError,
    errorContainer = ChatPalette.ErrorContainer,
    onErrorContainer = ChatPalette.OnErrorContainer,
)

private val ChatDarkScheme = darkColorScheme(
    primary = ChatPalette.DarkPrimary,
    onPrimary = ChatPalette.DarkOnPrimary,
    primaryContainer = ChatPalette.DarkPrimaryContainer,
    onPrimaryContainer = ChatPalette.DarkOnPrimaryContainer,
    secondary = ChatPalette.DarkSecondary,
    onSecondary = ChatPalette.DarkOnSecondary,
    tertiary = ChatPalette.DarkTertiary,
    onTertiary = ChatPalette.DarkOnTertiary,
    secondaryContainer = ChatPalette.DarkSecondaryContainer,
    onSecondaryContainer = ChatPalette.DarkOnSecondaryContainer,
    tertiaryContainer = ChatPalette.DarkTertiaryContainer,
    onTertiaryContainer = ChatPalette.DarkOnTertiaryContainer,
    background = ChatPalette.DarkCanvas,
    onBackground = ChatPalette.DarkOnSurface,
    surface = ChatPalette.DarkCanvas,
    onSurface = ChatPalette.DarkOnSurface,
    surfaceVariant = ChatPalette.DarkContainer,
    onSurfaceVariant = ChatPalette.DarkOnSurfaceVariant,
    surfaceContainerLowest = ChatPalette.DarkContainerLowest,
    surfaceContainerLow = ChatPalette.DarkContainerLow,
    surfaceContainer = ChatPalette.DarkContainer,
    surfaceContainerHigh = ChatPalette.DarkContainerHigh,
    surfaceContainerHighest = ChatPalette.DarkOutlineVariant,
    outline = ChatPalette.DarkOutline,
    outlineVariant = ChatPalette.DarkOutlineVariant,
    error = ChatPalette.DarkError,
    onError = ChatPalette.DarkOnError,
    errorContainer = ChatPalette.DarkErrorContainer,
    onErrorContainer = ChatPalette.DarkOnErrorContainer,
)

/**
 * Chat line metrics. Be Vietnam Pro is not bundled in the APK and adding a font
 * dependency is out of scope, so the typeface stays the platform default while the
 * vertical metrics follow the design system: line height >= 1.5x so stacked Vietnamese
 * tone marks (Ệ Ở Ứ ọ) never clip.
 */
private val ChatTypography = Typography(
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ChatDarkScheme else ChatLightScheme,
        typography = ChatTypography,
        content = content,
    )
}
