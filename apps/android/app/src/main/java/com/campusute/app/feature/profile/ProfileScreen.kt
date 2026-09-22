package com.campusute.app.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard
import com.campusute.app.core.designsystem.components.CampusErrorState
import com.campusute.app.core.designsystem.components.CampusHeroCard
import com.campusute.app.core.designsystem.components.CampusIconTile
import com.campusute.app.core.designsystem.components.CampusSectionHeader
import com.campusute.app.core.designsystem.components.CampusSkeleton

/**
 * Hồ sơ & Cài đặt (v1.3, Stitch frame): profile hero, account facts, display
 * toggles (dark mode + notifications, persisted), app info and a confirm-guarded
 * logout. The switch is the app's dark-theme control, not a preview.
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.user != null -> {
                val user = state.user!!
                CampusHeroCard(
                    fullName = user.fullName,
                    profileLine = buildString {
                        append(user.studentCode ?: "-")
                        user.department?.let { append(" · $it") }
                    },
                )
                user.roles.firstOrNull()?.let { role ->
                    Text(
                        role,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            state.loadFailed -> CampusErrorState(
                "Không tải được hồ sơ.",
                onRetry = { viewModel.load() },
            )
            else -> CampusSkeleton(rows = 2)
        }

        CampusSectionHeader("Tài khoản")
        val user = state.user
        CampusCard {
            SettingRow(Icons.Filled.Email, "Email", user?.email ?: "—")
            SettingRow(Icons.Filled.Person, "Mã số sinh viên", user?.studentCode ?: "—")
            SettingRow(Icons.Filled.List, "Khoa / Ngành", user?.department ?: "—")
        }

        CampusSectionHeader("Hiển thị")
        CampusCard {
            SettingRow(
                Icons.Filled.Settings,
                "Chế độ tối",
                null,
                onClick = { viewModel.setDarkMode(!darkMode) },
                trailing = {
                    Switch(
                        checked = darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                        modifier = Modifier.semantics { contentDescription = "toggle-dark" },
                    )
                },
            )
            SettingRow(
                Icons.Filled.Notifications,
                "Nhận thông báo",
                "Trong ứng dụng",
                onClick = { viewModel.setNotifications(!notifications) },
                trailing = {
                    Switch(
                        checked = notifications,
                        onCheckedChange = { viewModel.setNotifications(it) },
                        modifier = Modifier.semantics { contentDescription = "toggle-notifications" },
                    )
                },
            )
        }

        CampusSectionHeader("Ứng dụng")
        CampusCard {
            SettingRow(Icons.Filled.Info, "Phiên bản", "1.2.0")
            SettingRow(Icons.Filled.Done, "Mã nguồn", "GitHub · CampusUTE_Kotlin_Project")
        }

        Button(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Filled.ExitToApp, contentDescription = null)
            Text(
                "Đăng xuất",
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Đăng xuất?") },
            text = { Text("Phiên đăng nhập trên máy này sẽ bị xoá.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        viewModel.logout(onLogout)
                    },
                    modifier = Modifier.semantics { contentDescription = "Xác nhận đăng xuất" },
                ) { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) }
            },

            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Giữ") }
            },
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampusIconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!value.isNullOrBlank()) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}
