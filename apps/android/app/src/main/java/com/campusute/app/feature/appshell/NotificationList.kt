package com.campusute.app.feature.appshell

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusute.app.core.designsystem.components.CampusCard

/** Notification inbox (student + lecturer + admin): newest first, tap = mark read. */
@Composable
fun NotificationList(viewModel: HomeViewModel = hiltViewModel()) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()

    if (inbox.isEmpty()) {
        Text("Chưa có thông báo.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        inbox.forEach { n ->
            CampusCard {
                Text(
                    (if (n.read) "" else "● ") + n.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (n.body.isNotBlank()) {
                    Text(n.body, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    n.createdAt.substring(0, 16).replace('T', ' '),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
