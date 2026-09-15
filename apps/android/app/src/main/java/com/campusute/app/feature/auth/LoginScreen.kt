package com.campusute.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.R
import com.campusute.app.core.designsystem.components.CampusButton
import com.campusute.app.core.designsystem.components.CampusTextField

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
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
                CampusTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = "Email",
                    supportingText = "Demo: student@demo.campusute.vn",
                )
                CampusTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Mật khẩu",
                    isPassword = true,
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                CampusButton(
                    text = if (state.loading) "Đang đăng nhập..." else "Đăng nhập",
                    onClick = viewModel::login,
                    enabled = state.canSubmit,
                )
            }
        }
    }
}
