package com.campusute.app.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

private val StarterPrompts = listOf(
    "Học kỳ này mình còn bao nhiêu tín chỉ?",
    "Tra cứu quy chế đào tạo về điểm rèn luyện",
    "Lịch thi của tuần này",
    "Học phí học kỳ hiện tại là bao nhiêu?",
)

/** AI campus assistant: cited answers, agent attribution, working citation sheet. */
@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    ChatTheme {
        ChatContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatContent(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    var openCitation by remember { mutableStateOf<Citation?>(null) }

    LaunchedEffect(state.messages.size, state.busy) {
        val count = state.messages.size + if (state.busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.messages.isEmpty() && !state.busy) {
                item {
                    FirstRun { prompt ->
                        viewModel.onInputChange(prompt)
                        viewModel.send()
                    }
                }
            }
            items(state.messages, key = { it.id }) { message ->
                when {
                    message.status == ChatStatus.ERROR -> ErrorRow(message, viewModel::retry)
                    message.role == ChatRole.USER -> UserBubble(message.text)
                    else -> AssistantBubble(message) { openCitation = it }
                }
            }
            if (state.busy) {
                item { ThinkingRow() }
            }
        }

        if (state.offline) {
            OfflineBanner()
        }

        if (state.messages.isNotEmpty() && listState.canScrollForward) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    onClick = { scrollScope.launch { listState.animateScrollToItem(state.messages.lastIndex) } },
                    shape = CircleShape,
                    tonalElevation = 3.dp,
                    modifier = Modifier.padding(end = 16.dp, bottom = 4.dp).size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Cuộn xuống tin nhắn mới nhất",
                        )
                    }
                }
            }
        }

        Composer(
            input = state.input,
            busy = state.busy,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::send,
        )
    }

    openCitation?.let { citation ->
        ModalBottomSheet(
            onDismissRequest = { openCitation = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CitationSheet(citation = citation, onClose = { openCitation = null })
        }
    }
}

@Composable
private fun AssistantMark(size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "AI",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun FirstRun(onPrompt: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssistantMark()
            Text(
                "Hỏi về lịch học, điểm, học phí hoặc quy chế",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            "Mỗi câu trả lời kèm trích dẫn theo số trang. Chọn một gợi ý để bắt đầu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StarterPrompts.forEach { prompt ->
            Surface(
                onClick = { onPrompt(prompt) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    prompt,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(PillShape(tailOnEnd = true))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { contentDescription = "Tin nhắn của bạn" },
        )
    }
}

internal val CITATION_MARKER = Regex("""\[\d+]""")

/**
 * The ai-service numbers cited sentences `[1] [2] …` in the same order it emits the citation
 * list, so the nth marker is the nth chip. Markers are styled to read as belonging to the chips
 * below them; they are deliberately NOT click targets — a payload-derived string must never
 * become an interactive element.
 */
@Composable
internal fun citedAnswer(text: String): AnnotatedString {
    val scheme = MaterialTheme.colorScheme
    val span = SpanStyle(
        color = scheme.primary,
        fontWeight = FontWeight.Bold,
    )
    return buildAnnotatedString {
        append(text)
        CITATION_MARKER.findAll(text).forEach { match ->
            addStyle(span, match.range.first, match.range.last + 1)
        }
    }
}

@Composable
private fun AssistantBubble(message: ChatMessage, onCitationClick: (Citation) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text(
                message.agent?.let { "Agent $it" } ?: "Trợ lý Học đường",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .clip(PillShape(tailOnEnd = false))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .accentStripe()
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer {
                    Text(
                        citedAnswer(message.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                message.citations.forEachIndexed { position, citation ->
                    CitationChip(citation, number = position + 1) { onCitationClick(citation) }
                }
            }
        }
    }
}

@Composable
private fun CitationChip(citation: Citation, number: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.height(32.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                citation.page?.let { "[$number] ${citation.document} · tr.$it" }
                    ?: "[$number] ${citation.document}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CitationSheet(citation: Citation, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    citation.document,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    citation.page?.let { "Trang $it" } ?: "Không rõ trang",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Đóng")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            "TRÍCH DẪN TỪ TÀI LIỆU",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .accentStripe()
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        ) {
            SelectionContainer {
                Text(
                    citation.excerpt.ifEmpty { "Trích dẫn không có nội dung." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (!citation.source.isNullOrBlank()) {
            Text(
                "Nguồn: ${citation.source}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Văn bản do trợ lý trích từ tài liệu đã nạp — hãy đối chiếu bản gốc trước khi làm theo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRow(message: ChatMessage, onRetry: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        // A rate-limited reply resends the same fixed window, so it states the wait
        // instead of offering a button that cannot help.
        if (message.retryPrompt != null) {
            TextButton(onClick = { onRetry(message.id) }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Thử lại", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alphas = List(3) { index ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(560, delayMillis = index * 160), RepeatMode.Reverse),
            label = "dot$index",
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AssistantMark(20.dp)
            alphas.forEach { animated ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .alpha(animated.value)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                "Đang suy nghĩ…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "Trợ lý cần kết nối mạng. Lịch học đã tải vẫn dùng được.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun Composer(
    input: String,
    busy: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Hỏi trợ lý AI...") },
                shape = RoundedCornerShape(28.dp),
                maxLines = 4,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )
            if (busy) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            } else {
                Surface(
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Gửi",
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4dp accent stripe down the left edge of an assistant bubble, painted rather than laid out:
 * drawing in the bubble's own backend keeps the schedule-card idiom without a nested layout.
 */
@Composable
private fun Modifier.accentStripe(): Modifier {
    val color = MaterialTheme.colorScheme.primary
    // DrawScope is a Density, so the dp→px conversion happens at draw time.
    return drawBehind {
        drawRect(color = color, size = Size(4.dp.toPx(), size.height))
    }
}

/** Pill with one tightened corner so the bubble reads as spoken from its own side. */
private fun PillShape(tailOnEnd: Boolean): Shape = if (tailOnEnd) {
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
} else {
    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp)
}
