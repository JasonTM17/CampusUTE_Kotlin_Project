package com.campusute.backend.notification

import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "notifications")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val service: NotificationService) {

    data class NotificationDto2(val id: UUID, val type: String, val title: String, val body: String, val read: Boolean, val createdAt: String)
    data class InboxDto(val notifications: List<NotificationDto2>, val unread: Long)

    @Operation(summary = "Caller's in-app notification inbox (newest first)")
    @GetMapping
    fun inbox(): ApiEnvelope<InboxDto> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val rows = service.list(userId)
        return ApiEnvelope.ok(
            InboxDto(
                notifications = rows.map { NotificationDto2(it.id, it.type, it.title, it.bodyText, it.read, it.createdAt.toString()) },
                unread = service.unreadCount(userId),
            ),
        )
    }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ApiEnvelope<Map<String, String>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        service.markRead(id, userId)
        return ApiEnvelope.ok(mapOf("status" to "READ"))
    }
}
