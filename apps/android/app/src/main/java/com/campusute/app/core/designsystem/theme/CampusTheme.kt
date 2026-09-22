package com.campusute.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// CampusUTE design tokens (Phase 1). Branding stays configurable — no
// official HCMUTE identity assets are used; replace via theme config later.
private val CampusBlue = Color(0xFF0B5FA5)
private val CampusBlueDark = Color(0xFF9ECBFF)
private val CampusAmber = Color(0xFFF59E0B)

/** Home hero gradient (v1.2 Stitch frame): CampusBlue → brighter sky. Public so
 *  feature screens can brush it without inlining hex literals (design-guard §3). */
val CampusHeroGradientStart = CampusBlue
val CampusHeroGradientEnd = Color(0xFF3B82C4)

/**
 * Every M3 role is declared on purpose. `lightColorScheme` falls back to the Material baseline
 * for any role it is not given, and that baseline is purple — so leaving `primaryContainer` or
 * `surfaceContainer*` unset makes cards, sheets and chips render violet inside a blue app.
 *
 * Only four roles were named at call sites before this file was completed, which invites the
 * conclusion that filling the rest cannot change anything on screen. That is wrong: `Card` takes
 * `surfaceContainer` and `NavigationBarItem` takes `secondaryContainer` without the call site
 * naming either, so completing the scheme retinted the shell from accidental Material purple to
 * the brand hues. Verified on device, and recorded in `docs/ai/app-design.md` §1.
 */
private val LightColors = lightColorScheme(
    primary = CampusBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E8FB),
    onPrimaryContainer = Color(0xFF06283F),
    secondary = CampusAmber,
    onSecondary = Color(0xFF3D2600),
    secondaryContainer = Color(0xFFFFE9C5),
    onSecondaryContainer = Color(0xFF3A2600),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF083344),
    background = Color(0xFFF7FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF7FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE8EFF6),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color(0xFFE8EFF6),
    surfaceContainerHigh = Color(0xFFDCE6F0),
    surfaceContainerHighest = Color(0xFFCFDCE8),
    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFCBD5E1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = CampusBlueDark,
    onPrimary = Color(0xFF06344F),
    primaryContainer = Color(0xFF084C7C),
    onPrimaryContainer = Color(0xFFD3E8FB),
    secondary = Color(0xFFF5B642),
    onSecondary = Color(0xFF3D2600),
    secondaryContainer = Color(0xFF56390A),
    onSecondaryContainer = Color(0xFFFFE9C5),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF083344),
    tertiaryContainer = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFF0F1620),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0F1620),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E2733),
    onSurfaceVariant = Color(0xFFB6C2CF),
    surfaceContainerLowest = Color(0xFF0A1017),
    surfaceContainerLow = Color(0xFF171F2A),
    surfaceContainer = Color(0xFF1E2733),
    surfaceContainerHigh = Color(0xFF29323E),
    surfaceContainerHighest = Color(0xFF344050),
    outline = Color(0xFF8A98A8),
    outlineVariant = Color(0xFF3A4654),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Light / Dark / System theming per the design-system contract. */
@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CampusTypography,
        content = content,
    )
}
