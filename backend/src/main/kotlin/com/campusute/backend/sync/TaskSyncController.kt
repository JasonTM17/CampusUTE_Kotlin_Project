package com.campusute.backend.sync

import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

@Tag(name = "tasks")
@RestController
@RequestMapping("/api/v1/tasks")
class TaskSyncController(private val sync: TaskSyncService) {

    data class TaskDto(
        val id: UUID,
        val title: String,
        val dueDate: String?,
        val done: Boolean,
        val deleted: Boolean,
        val version: Long,
        val updatedAt: String,
    )

    data class ChangesDto(val changes: List<TaskDto>, val serverTime: String)

    data class SyncRequest(val operations: List<TaskSyncService.PushOperation>)

    data class SyncResponseDto(
        val results: List<OpResultDto>,
        val serverTime: String,
    )

    data class OpResultDto(
        val clientOpId: String,
        val status: String, // APPLIED | DUPLICATE | CONFLICT | INVALID
        val task: TaskDto?,
    )

    @Operation(summary = "Delta pull: tasks changed since a timestamp")
    @GetMapping("/changes")
    fun changes(
        @RequestParam("since") since: String,
    ): ApiEnvelope<ChangesDto> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val sinceInstant = try {
            Instant.parse(since)
        } catch (_: DateTimeParseException) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Tham số since phải là ISO-8601 timestamp.")
        }
        val (rows, serverTime) = sync.changes(userId, sinceInstant)
        return ApiEnvelope.ok(ChangesDto(rows.map { it.toDto() }, serverTime.toString()))
    }

    @Operation(summary = "Batch push with clientOpId idempotency and server-win conflicts")
    @PostMapping("/sync")
    fun sync(@RequestBody body: SyncRequest): ApiEnvelope<SyncResponseDto> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        if (body.operations.size > 100) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Tối đa 100 operation mỗi batch.")
        }
        val results = body.operations.map { op ->
            val r = sync.apply(userId, op)
            OpResultDto(r.clientOpId, r.status.name, r.task?.toDto())
        }
        return ApiEnvelope.ok(SyncResponseDto(results, Instant.now().toString()))
    }

    private fun StudyTask.toDto() = TaskDto(
        id = id,
        title = title,
        dueDate = dueDate?.toString(),
        done = done,
        deleted = deleted,
        version = version,
        updatedAt = updatedAt.toString(),
    )
}
