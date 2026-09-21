package com.campusute.app.core.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CampusUTE design-system components. Screens compose these instead of raw Material calls so
 * tokens restyle in one place.
 *
 * Before this file existed, `SectionHeader`, the error row and the offline banner were each
 * hand-rolled two or three times per feature, and the timetable's banner carried a raw amber hex
 * that duplicated the theme. One offline, one error and one empty shape is
 * what makes "failed to load" and "nothing to show" visually distinct on every tab — the property
 * `docs/ai/app-design.md` §4 asks each screen to preserve.
 */

/** Badge/notice intent. Read the tone, never a hex — palettes own the colour. */
enum class CampusTone { Neutral, Info, Success, Warning, Danger }

@Composable
fun CampusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth(), enabled = enabled) {
        Text(text)
    }
}

@Composable
fun CampusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    errorText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        isError = errorText != null,
        supportingText = (errorText ?: supportingText)?.let { { Text(it) } },
        visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

/**
 * The content card. [onClick] upgrades it to a tappable row; [tonal] is the quieter filled
 * variant used for previews and inset panels, so a screen can show two card weights without
 * inventing a colour.
 */
@Composable
fun CampusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tonal: Boolean = false,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else if (tonal) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
    navigationIcon: @Composable () -> Unit = {},
) {
    TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon, actions = actions)
}

@Composable
fun CampusLoading(message: String = "Đang tải...") {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * A list placeholder. Used instead of a spinner while a section loads, so the screen keeps its
 * shape and the skeleton itself tells the user which section is still arriving.
 */
@Composable
fun CampusSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonPulse",
    )
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) { index ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The lead bar is shorter on odd rows so the block does not read as one rule.
                ColorBar(tint.copy(alpha = pulse), shape, if (index % 2 == 0) 0.55f else 0.4f)
                ColorBar(tint.copy(alpha = pulse * 0.75f), shape, 0.85f)
            }
        }
    }
}

@Composable
private fun ColorBar(color: Color, shape: Shape, widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .background(color, shape),
    )
}

/** Section title with an optional trailing action ("Xem tất cả", "Thêm"). */
@Composable
fun CampusSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

/**
 * A load/action failure with a way out. Deliberately louder and differently shaped than
 * [CampusEmptyState]: a failed request must never look like an honest zero.
 */
@Composable
fun CampusErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Thử lại",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
        }
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(retryLabel, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** Stale-data notice. One shape for every tab so "saved copy" reads the same everywhere. */
@Composable
fun CampusOfflineBanner(
    message: String = "Ngoại tuyến — đang hiển thị dữ liệu đã lưu",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

/** Single-choice filter row. Replaces the per-feature AssistChip strips. */
@Composable
fun <T> CampusFilterChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelFor: (T) -> String = { it.toString() },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelFor(option)) },
                shape = CircleShape,
            )
        }
    }
}

/** Compact status pill: overdue, seats left, unsynced, registered. */
@Composable
fun CampusStatusBadge(
    text: String,
    tone: CampusTone = CampusTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val (container, onContainer) = when (tone) {
        CampusTone.Neutral ->
            MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        CampusTone.Info ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CampusTone.Success ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        // Warning rides the same amber the navigation indicator uses, so one hue means
        // "needs attention" across the shell.
        CampusTone.Warning ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CampusTone.Danger ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = onContainer,
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Lead line for a two-line row (title + supporting text) that never wraps mid-word. */
@Composable
fun CampusLeadLine(
    primary: String,
    secondary: String?,
    modifier: Modifier = Modifier,
    primaryMaxLines: Int = 2,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            primary,
            style = MaterialTheme.typography.titleSmall,
            maxLines = primaryMaxLines,
            textAlign = TextAlign.Start,
        )
        if (!secondary.isNullOrBlank()) {
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * An honest zero. [icon] is what distinguishes it from a blank screen — the sheet requires a
 * glyph so an empty list cannot be mistaken for a failed one at a glance.
 */
@Composable
fun CampusEmptyState(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            SpacerHeight(12.dp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SpacerHeight(height: Dp) {
    Spacer(Modifier.height(height))
}
