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

private val LightColors = lightColorScheme(
    primary = CampusBlue,
    secondary = CampusAmber,
)

private val DarkColors = darkColorScheme(
    primary = CampusBlueDark,
    secondary = CampusAmber,
)

/** Light / Dark / System theming per the design-system contract. */
@Composable
fun CampusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
