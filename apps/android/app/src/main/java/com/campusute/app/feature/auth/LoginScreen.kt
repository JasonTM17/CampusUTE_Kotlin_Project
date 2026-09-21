package com.campusute.app.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.R
import com.campusute.app.core.data.LoginFailure
import com.campusute.app.core.designsystem.components.CampusButton
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusTextField

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
    /** Set when the network layer dropped a live session underneath the shell. */
    bouncedReason: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) onSignedIn()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(com.campusute.app.R.drawable.ic_launcher_foreground),
                    contentDescription = "CampusUTE logo",
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "AI-Powered Smart Campus",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                modifier = Modifier.padding(top = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                bouncedReason?.let { reason ->
                    // Announced, not just painted: a student bounced out of a timetable mid-week
                    // needs the screen reader to say why the form reappeared.
                    CampusErrorState(reason, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                CampusTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    supportingText = if (com.campusute.app.BuildConfig.DEBUG) "Demo: student@demo.campusute.vn" else null,
                )
                CampusTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Mật khẩu",
                    isPassword = true,
                )
                state.failure?.let { kind ->
                    CampusErrorState(
                        message = recoveryLine(kind, state.message),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        recoveryHint(kind, state.cooldownSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CampusButton(
                    text = when {
                        state.loading -> "Đang đăng nhập..."
                        state.cooldownSeconds > 0 -> "Thử lại sau ${state.cooldownSeconds}s"
                        else -> "Đăng nhập"
                    },
                    onClick = viewModel::login,
                    enabled = state.canSubmit,
                )
            }
        }
    }
}

/** The server's own words when it sent any, otherwise the cause named in the student's language. */
private fun recoveryLine(kind: LoginFailure, stated: String?): String = stated?.takeIf { it.isNotBlank() }
    ?: when (kind) {
        LoginFailure.InvalidCredentials -> "Email hoặc mật khẩu không đúng."
        LoginFailure.RateLimited -> "Quá nhiều lần thử."
        LoginFailure.SessionExpired -> "Phiên đăng nhập đã hết hạn."
        LoginFailure.Offline -> "Không thể kết nối máy chủ."
        LoginFailure.ServerError -> "Máy chủ đang gặp sự cố."
        LoginFailure.Unknown -> "Đã xảy ra lỗi."
    }

/**
 * What to do next differs per cause — that is the whole point of distinguishing them. A locked
 * client is not a typo, and an unreachable campus is not either.
 */
private fun recoveryHint(kind: LoginFailure, cooldownSeconds: Int): String = when (kind) {
    LoginFailure.InvalidCredentials -> "Kiểm tra lại email trường hoặc nhờ phòng đào tạo đặt lại mật khẩu."
    LoginFailure.RateLimited -> if (cooldownSeconds > 0) {
        "Ứng dụng sẽ ngừng tự động thử lại trong $cooldownSeconds giây."
    } else {
        "Bạn có thể thử lại ngay."
    }
    LoginFailure.SessionExpired -> "Đăng nhập lại để tiếp tục — dữ liệu của bạn không bị mất."
    LoginFailure.Offline -> "Kiểm tra Wi-Fi hoặc dữ liệu di động, rồi bấm đăng nhập lại."
    LoginFailure.ServerError -> "Thử lại sau ít phút. Các thiết bị khác vẫn có thể bị ảnh hưởng."
    LoginFailure.Unknown -> "Thử lại. Nếu vẫn hỏng, liên hệ hỗ trợ của trường."
}
